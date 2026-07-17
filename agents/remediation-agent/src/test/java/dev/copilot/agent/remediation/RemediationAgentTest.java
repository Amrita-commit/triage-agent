package dev.copilot.agent.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.remediation.RiskLevel;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

/** Verifies the remediation agent produces a structured proposal with a correct unified diff. */
class RemediationAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChatModel modelReturning(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(text))
                        .tokenUsage(new TokenUsage(200, 150))
                        .build();
            }
        };
    }

    @Test
    void proposesFixWithComputedUnifiedDiffAndTrace() {
        String current = "resource \"docker_container\" \"demo\" {\n  env = [\"LOG_LEVEL=DEBUG\"]\n}\n";
        String proposedNew = "resource \"docker_container\" \"demo\" {\n  env = [\"LOG_LEVEL=INFO\"]\n}\n";
        String modelJson =
                "{\"title\":\"Restore LOG_LEVEL to INFO\","
                        + "\"rationale\":\"drift set it to DEBUG; declared state is INFO\","
                        + "\"newContent\":" + JSON.valueToTree(proposedNew) + ","
                        + "\"rollbackNotes\":\"re-apply previous value\",\"risk\":\"LOW\"}";

        var store = new InMemoryTraceStore();
        RemediationAgent agent = new RemediationAgent(
                modelReturning(modelJson), "planning-model", new TokenCostEstimator(), store, JSON);

        ProposedRemediation r = agent.propose(
                "inc-1", "Drift: LOG_LEVEL changed to DEBUG", "infra/local/main.tf", current);

        assertThat(r.targetPath()).isEqualTo("infra/local/main.tf");
        assertThat(r.sourceIncidentId()).isEqualTo("inc-1");
        assertThat(r.newContent()).contains("LOG_LEVEL=INFO");
        assertThat(r.risk()).isEqualTo(RiskLevel.LOW);
        // The unified diff is computed from current vs proposed content.
        assertThat(r.unifiedDiff()).contains("-  env = [\"LOG_LEVEL=DEBUG\"]");
        assertThat(r.unifiedDiff()).contains("+  env = [\"LOG_LEVEL=INFO\"]");
        assertThat(store.findAll()).hasSize(1);
        assertThat(store.findAll().get(0).llmCalls()).hasSize(1);
    }

    @Test
    void reportsNoChangeWhenContentIdentical() {
        String same = "unchanged\n";
        assertThat(RemediationAgent.unifiedDiff("f", same, same)).isEqualTo("(no change)");
    }
}
