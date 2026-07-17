package dev.copilot.agent.postmortem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.diagnosis.Evidence;
import dev.copilot.core.infra.DriftChange;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.postmortem.Postmortem;
import dev.copilot.core.postmortem.TimelineEntry;
import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.remediation.RiskLevel;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.copilot.core.trace.LlmCallRecord;
import dev.copilot.core.trace.ToolCallRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostmortemAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PostmortemInput sampleIncident() {
        AgentTrace diag = new AgentTrace("inc-1", "diagnostics-agent", "checkout latency");
        diag.addToolCall(new ToolCallRecord(
                Instant.parse("2026-07-01T10:00:00Z"), "query_metrics", "{\"query\":\"p95\"}", "3.0s", 12, false));
        diag.addLlmCall(new LlmCallRecord(
                Instant.parse("2026-07-01T10:00:01Z"), TaskType.PLANNING, "claude-sonnet-5", 100, 50, 900, 0.001));

        List<TimelineEntry> timeline = TimelineBuilder.fromTraces(List.of(diag));

        Diagnosis diagnosis = new Diagnosis(
                "checkout latency", "artificial latency injected in checkout handler",
                List.of(new Evidence("query_metrics", "p95", "p95 = 3.0s", "far above the 2s SLO")),
                0.9, List.of("remove the injected latency"), false);
        DriftReport drift = new DriftReport(true,
                List.of(new DriftChange("docker_container.demo_app", "env", "INFO", "DEBUG", "update")),
                "Drift detected");
        ProposedRemediation remediation = new ProposedRemediation(
                "rem-1", "inc-1", "Restore LOG_LEVEL", "revert drift", "infra/local/main.tf",
                "env=[INFO]", "--- a\n+++ b\n-DEBUG\n+INFO", "revert commit", RiskLevel.LOW);

        return new PostmortemInput("inc-1", "Checkout latency incident",
                "P95 latency on checkout > 2s", diagnosis, drift, remediation, timeline);
    }

    private ChatModel narrativeModel() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                String json = "{\"executiveSummary\":\"Checkout latency breached SLO due to an injected fault.\","
                        + "\"rootCause\":\"An artificial latency fault in the checkout handler.\","
                        + "\"preventionItems\":[\"Add a P95 latency alert\",\"Guard fault endpoints\"]}";
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(json)).tokenUsage(new TokenUsage(120, 80)).build();
            }
        };
    }

    @Test
    void generatesGroundedMarkdownAndWritesFile(@TempDir Path out) throws Exception {
        PostmortemAgent agent = new PostmortemAgent(
                narrativeModel(), "claude-sonnet-5", new TokenCostEstimator(),
                new InMemoryTraceStore(), JSON, out.toString());

        Postmortem pm = agent.generate(sampleIncident());
        String md = pm.markdown();

        assertThat(md).contains("# Postmortem: Checkout latency incident");
        assertThat(md).contains("Checkout latency breached SLO");          // executive summary
        assertThat(md).contains("## Timeline").contains("tool:query_metrics"); // timeline from traces
        assertThat(md).contains("artificial latency fault in the checkout handler"); // root cause
        assertThat(md).contains("p95 = 3.0s");                              // evidence
        assertThat(md).contains("Infrastructure drift").contains("DEBUG"); // drift
        assertThat(md).contains("```diff").contains("+INFO");              // remediation diff
        assertThat(md).contains("- [ ] Add a P95 latency alert");          // prevention

        // File persisted
        Path file = out.resolve("inc-1.md");
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.readString(file)).isEqualTo(md);
    }

    @Test
    void degradesGracefullyWhenNarrativeModelFails(@TempDir Path out) {
        ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("credit balance too low");
            }
        };
        PostmortemAgent agent = new PostmortemAgent(
                failing, "claude-sonnet-5", new TokenCostEstimator(), new InMemoryTraceStore(), JSON, out.toString());

        Postmortem pm = agent.generate(sampleIncident());
        // Still produced, grounded in the structured facts (the diagnosis hypothesis becomes root cause).
        assertThat(pm.markdown()).contains("# Postmortem")
                .contains("artificial latency injected in checkout handler")
                .contains("## Timeline");
    }
}
