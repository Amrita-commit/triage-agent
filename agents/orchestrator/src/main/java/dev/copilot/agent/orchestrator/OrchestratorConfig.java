package dev.copilot.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.agent.orchestrator.infra.InfraAgent;
import dev.copilot.agent.orchestrator.infra.InfraToolbox;
import dev.copilot.agent.orchestrator.infra.McpInfraToolbox;
import dev.copilot.core.model.ModelRouter;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.copilot.core.trace.TraceStore;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the orchestrator: the classifier (Claude), the infra agent (terraform-mcp), and the diagnostics client. */
@Configuration
public class OrchestratorConfig {

    @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${copilot.model.cheap:claude-haiku-4-5-20251001}")
    private String cheapModel;

    @Value("${copilot.model.planning:claude-sonnet-5}")
    private String planningModel;

    @Value("${terraform.mcp.sse-url:http://localhost:8091/sse}")
    private String terraformMcpSseUrl;

    @Value("${diagnostics.agent.url:http://localhost:8100}")
    private String diagnosticsAgentUrl;

    @Bean
    ModelRouter modelRouter() {
        return new ModelRouter(cheapModel, planningModel);
    }

    @Bean
    TokenCostEstimator tokenCostEstimator() {
        return new TokenCostEstimator();
    }

    @Bean
    TraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    /** The planning model used by the classifier. Built even without a key (calls fail clearly at runtime). */
    @Bean
    ChatModel planningChatModel(ModelRouter router) {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey == null || anthropicApiKey.isBlank() ? "missing-key" : anthropicApiKey)
                .modelName(router.modelFor(TaskType.PLANNING))
                .maxTokens(1024)
                .temperature(0.0)
                .build();
    }

    @Bean
    IncidentClassifier incidentClassifier(
            ChatModel planningChatModel, ModelRouter router, TokenCostEstimator cost, ObjectMapper json) {
        return new IncidentClassifier(planningChatModel, router.modelFor(TaskType.PLANNING), cost, json);
    }

    @Bean
    InfraToolbox infraToolbox() {
        return new McpInfraToolbox(terraformMcpSseUrl);
    }

    @Bean
    InfraAgent infraAgent(InfraToolbox toolbox, ObjectMapper json, TraceStore traceStore) {
        return new InfraAgent(toolbox, json, traceStore);
    }

    @Bean
    DiagnosticsClient diagnosticsClient() {
        return new HttpDiagnosticsClient(diagnosticsAgentUrl);
    }

    @Bean
    OrchestratorAgent orchestratorAgent(
            IncidentClassifier classifier,
            DiagnosticsClient diagnosticsClient,
            InfraAgent infraAgent,
            TraceStore traceStore) {
        return new OrchestratorAgent(classifier, diagnosticsClient, infraAgent, traceStore);
    }
}
