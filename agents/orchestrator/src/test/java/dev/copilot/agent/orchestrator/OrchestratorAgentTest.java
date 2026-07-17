package dev.copilot.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.agent.orchestrator.infra.InfraAgent;
import dev.copilot.agent.orchestrator.infra.InfraToolbox;
import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.diagnosis.Evidence;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.orchestration.IncidentClassification.Category;
import dev.copilot.core.orchestration.OrchestratedFindings;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises classify -> delegate -> merge with a scripted classifier and fake sub-agents (no API key). */
class OrchestratorAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANNING = "planning-model";

    private ChatModel classifierModel(String classificationJson) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(classificationJson))
                        .tokenUsage(new TokenUsage(60, 30))
                        .build();
            }
        };
    }

    private OrchestratorAgent orchestrator(
            String classificationJson, DiagnosticsClient diag, InfraToolbox infra, InMemoryTraceStore store) {
        var classifier = new IncidentClassifier(
                classifierModel(classificationJson), PLANNING, new TokenCostEstimator(), JSON);
        var infraAgent = new InfraAgent(infra, JSON, store);
        return new OrchestratorAgent(classifier, diag, infraAgent, store);
    }

    @Test
    void classifiesMixedAndMergesBothFindings() {
        String classification =
                "{\"category\":\"MIXED\",\"reasoning\":\"latency plus possible drift\","
                        + "\"needsDiagnostics\":true,\"needsInfra\":true}";
        DiagnosticsClient diag = (id, alert) -> new Diagnosis(
                "checkout latency", "injected latency in checkout",
                List.of(new Evidence("query_metrics", "p95", "3.0s", "above SLO")), 0.9,
                List.of("remove latency"), false);
        InfraToolbox infra = new InfraToolbox() {
            @Override
            public String readState() {
                return "{}";
            }

            @Override
            public String planDiff() {
                return "{\"driftDetected\":true,\"changes\":[{\"resource\":\"docker_container.demo_app\","
                        + "\"attribute\":\"env\",\"expected\":\"INFO\",\"actual\":\"DEBUG\",\"action\":\"update\"}],"
                        + "\"summary\":\"Drift detected\"}";
            }
        };
        var store = new InMemoryTraceStore();

        OrchestratedFindings findings = orchestrator(classification, diag, infra, store).handle("inc-1", "alert");

        assertThat(findings.classification().category()).isEqualTo(Category.MIXED);
        assertThat(findings.diagnosis()).isNotNull();
        assertThat(findings.diagnosis().hypothesis()).contains("latency");
        assertThat(findings.driftReport()).isNotNull();
        assertThat(findings.driftReport().driftDetected()).isTrue();
        assertThat(findings.summary()).contains("MIXED").contains("Drift detected");
    }

    @Test
    void isResilientWhenDiagnosticsDelegationFails() {
        String classification =
                "{\"category\":\"MIXED\",\"reasoning\":\"x\",\"needsDiagnostics\":true,\"needsInfra\":true}";
        DiagnosticsClient failing = (id, alert) -> {
            throw new RuntimeException("credit balance too low");
        };
        InfraToolbox infra = new InfraToolbox() {
            @Override
            public String readState() {
                return "{}";
            }

            @Override
            public String planDiff() {
                return "{\"driftDetected\":false,\"changes\":[],\"summary\":\"No drift\"}";
            }
        };
        var store = new InMemoryTraceStore();

        OrchestratedFindings findings = orchestrator(classification, failing, infra, store).handle("inc-2", "alert");

        assertThat(findings.diagnosis()).isNull(); // diagnostics failed
        assertThat(findings.driftReport()).isNotNull(); // infra still ran
        assertThat(findings.summary()).contains("Diagnostics agent unavailable");
    }

    @Test
    void skipsInfraWhenClassifierSaysApplicationOnly() {
        String classification =
                "{\"category\":\"APPLICATION_FAULT\",\"reasoning\":\"pure app fault\","
                        + "\"needsDiagnostics\":true,\"needsInfra\":false}";
        DiagnosticsClient diag = (id, alert) -> new Diagnosis(
                "s", "h", List.of(new Evidence("t", "q", "o", "i")), 0.8, List.of(), false);
        InfraToolbox infra = new InfraToolbox() {
            @Override
            public String readState() {
                return "{}";
            }

            @Override
            public String planDiff() {
                throw new AssertionError("infra agent must not be called for APPLICATION_FAULT");
            }
        };
        var store = new InMemoryTraceStore();

        OrchestratedFindings findings = orchestrator(classification, diag, infra, store).handle("inc-3", "alert");

        assertThat(findings.classification().category()).isEqualTo(Category.APPLICATION_FAULT);
        assertThat(findings.diagnosis()).isNotNull();
        assertThat(findings.driftReport()).isNull();
    }
}
