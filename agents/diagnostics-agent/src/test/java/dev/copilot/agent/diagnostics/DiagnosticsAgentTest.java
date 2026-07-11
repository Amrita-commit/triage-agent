package dev.copilot.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.agent.diagnostics.DiagnosticsAgent.DiagnosisResult;
import dev.copilot.core.model.ModelRouter;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.copilot.core.trace.LlmCallRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises the full agentic loop with a scripted fake {@link ChatModel} — no Claude API key
 * required. Verifies structured output, evidence citation, the tool-call cap, tracing/cost, and
 * model routing.
 */
class DiagnosticsAgentTest {

    private static final String CHEAP = "cheap-model";
    private static final String PLANNING = "planning-model";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DIAGNOSIS_JSON =
            """
            {"incidentSummary":"P95 checkout latency above SLO",
             "hypothesis":"artificial latency injected in the checkout handler",
             "evidence":[{"tool":"query_metrics","query":"histogram_quantile(0.95,...)",
                          "observation":"p95 = 3.0s","interpretation":"far above the 2s SLO"}],
             "confidence":0.9,
             "suggestedNextSteps":["remove the injected latency fault"]}""";

    @Test
    void happyPathProducesEvidenceBackedDiagnosis() {
        ChatModel planning = new ScriptedChatModel(List.of(
                toolResponse("query_metrics", "{\"query\":\"histogram_quantile(0.95,...)\"}"),
                textResponse(DIAGNOSIS_JSON)));
        var traceStore = new InMemoryTraceStore();
        DiagnosticsAgent agent = agent(Map.of(PLANNING, planning), traceStore);

        DiagnosisResult result = agent.diagnose("inc-1", "checkout latency alert: P95 > 2s");

        assertThat(result.diagnosis().hypothesis()).contains("latency");
        assertThat(result.diagnosis().isEvidenceBacked()).isTrue();
        assertThat(result.diagnosis().evidence().get(0).observation()).isEqualTo("p95 = 3.0s");
        assertThat(result.diagnosis().confidence()).isEqualTo(0.9);
        assertThat(result.diagnosis().truncated()).isFalse();

        var trace = traceStore.findById(result.traceId()).orElseThrow();
        assertThat(trace.toolCallCount()).isEqualTo(1);
        assertThat(trace.llmCalls()).hasSize(2);
        assertThat(trace.totalTokens()).isGreaterThan(0);
        assertThat(trace.totalCostUsd()).isGreaterThan(0.0);
        assertThat(trace.toolCalls().get(0).tool()).isEqualTo("query_metrics");
    }

    @Test
    void enforcesToolCallCapAndMarksTruncated() {
        // A model that always calls a tool while tools are offered, and only answers once tools are
        // withdrawn — guaranteed to hit the cap.
        ChatModel probing = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                boolean toolsOffered =
                        request.toolSpecifications() != null && !request.toolSpecifications().isEmpty();
                return toolsOffered
                        ? toolResponse("query_metrics", "{\"query\":\"up\"}")
                        : textResponse(DIAGNOSIS_JSON);
            }
        };
        var traceStore = new InMemoryTraceStore();
        DiagnosticsAgent agent = new DiagnosticsAgent(
                modelProvider(Map.of(PLANNING, probing)),
                new ModelRouter(CHEAP, PLANNING),
                new DiagnosticTools(new FakeToolbox(), JSON),
                traceStore,
                new TokenCostEstimator(),
                JSON,
                3, // small cap for the test
                4000);

        DiagnosisResult result = agent.diagnose("inc-2", "some incident");

        assertThat(result.diagnosis().truncated()).isTrue();
        var trace = traceStore.findById(result.traceId()).orElseThrow();
        assertThat(trace.toolCallCount()).isEqualTo(3); // exactly the cap, no more
    }

    @Test
    void routesLogSummarizationToCheapModel() {
        String bigLogs = "ERROR boom\n".repeat(600); // > 4000 chars -> triggers summarization
        TelemetryToolbox toolbox = new TelemetryToolbox() {
            @Override
            public String queryMetrics(String query, int rangeMinutes) {
                return "{}";
            }

            @Override
            public String queryLogs(String filter, int rangeMinutes, int limit) {
                return bigLogs;
            }

            @Override
            public String listAlerts() {
                return "[]";
            }
        };
        ChatModel planning = new ScriptedChatModel(List.of(
                toolResponse("query_logs", "{\"filter\":\"{service=\\\"demo-app\\\"}\"}"),
                textResponse(DIAGNOSIS_JSON)));
        ChatModel cheap = new ScriptedChatModel(List.of(textResponse("600x ERROR boom")));
        var traceStore = new InMemoryTraceStore();
        DiagnosticsAgent agent = new DiagnosticsAgent(
                modelProvider(Map.of(PLANNING, planning, CHEAP, cheap)),
                new ModelRouter(CHEAP, PLANNING),
                new DiagnosticTools(toolbox, JSON),
                traceStore,
                new TokenCostEstimator(),
                JSON);

        DiagnosisResult result = agent.diagnose("inc-3", "log flood");

        var trace = traceStore.findById(result.traceId()).orElseThrow();
        List<LlmCallRecord> summarizationCalls = trace.llmCalls().stream()
                .filter(c -> c.taskType() == TaskType.LOG_SUMMARIZATION)
                .toList();
        assertThat(summarizationCalls).hasSize(1);
        assertThat(summarizationCalls.get(0).model()).isEqualTo(CHEAP);
    }

    // --- fixtures ---------------------------------------------------------------

    private DiagnosticsAgent agent(Map<String, ChatModel> models, InMemoryTraceStore traceStore) {
        return new DiagnosticsAgent(
                modelProvider(models),
                new ModelRouter(CHEAP, PLANNING),
                new DiagnosticTools(new FakeToolbox(), JSON),
                traceStore,
                new TokenCostEstimator(),
                JSON);
    }

    private static ChatModelProvider modelProvider(Map<String, ChatModel> models) {
        return modelId -> {
            ChatModel m = models.get(modelId);
            if (m == null) {
                throw new IllegalArgumentException("No fake model for id: " + modelId);
            }
            return m;
        };
    }

    private static ChatResponse toolResponse(String name, String args) {
        ToolExecutionRequest req =
                ToolExecutionRequest.builder().id("t-" + name).name(name).arguments(args).build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(req))
                .tokenUsage(new TokenUsage(100, 40))
                .build();
    }

    private static ChatResponse textResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .tokenUsage(new TokenUsage(80, 120))
                .build();
    }

    /** Returns scripted responses in order; repeats the last once exhausted. */
    private static final class ScriptedChatModel implements ChatModel {
        private final Deque<ChatResponse> script;
        private ChatResponse last;

        ScriptedChatModel(List<ChatResponse> responses) {
            this.script = new ArrayDeque<>(responses);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            if (!script.isEmpty()) {
                last = script.poll();
            }
            return last;
        }
    }

    /** Minimal toolbox returning canned JSON. */
    private static final class FakeToolbox implements TelemetryToolbox {
        @Override
        public String queryMetrics(String query, int rangeMinutes) {
            return "{\"series\":[{\"labels\":{\"uri\":\"/checkout\"},\"latest\":3.0}]}";
        }

        @Override
        public String queryLogs(String filter, int rangeMinutes, int limit) {
            return "{\"entries\":[]}";
        }

        @Override
        public String listAlerts() {
            return "[]";
        }
    }
}
