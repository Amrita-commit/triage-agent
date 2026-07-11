package dev.copilot.agent.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.model.ModelRouter;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.trace.InMemoryTraceStore;
import dev.copilot.core.trace.TraceStore;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the diagnostics agent and its Claude-backed model router. */
@Configuration
public class AgentConfig {

    @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${copilot.model.cheap:claude-haiku-4-5-20251001}")
    private String cheapModel;

    @Value("${copilot.model.planning:claude-sonnet-5}")
    private String planningModel;

    @Value("${telemetry.mcp.sse-url:http://localhost:8090/sse}")
    private String telemetryMcpSseUrl;

    @Value("${diagnostics.max-tool-calls:15}")
    private int maxToolCalls;

    @Bean
    ModelRouter modelRouter() {
        return new ModelRouter(cheapModel, planningModel);
    }

    @Bean
    TraceStore traceStore() {
        return new InMemoryTraceStore();
    }

    @Bean
    TokenCostEstimator tokenCostEstimator() {
        return new TokenCostEstimator();
    }

    @Bean
    TelemetryToolbox telemetryToolbox(ObjectMapper objectMapper) {
        return new McpTelemetryToolbox(telemetryMcpSseUrl, objectMapper);
    }

    /**
     * Builds one {@link AnthropicChatModel} per distinct model id, on demand. Fails clearly if no
     * API key is configured — the app still boots (so health checks pass) but diagnosis calls
     * report the missing key instead of failing at startup.
     */
    @Bean
    ChatModelProvider chatModelProvider() {
        ConcurrentMap<String, ChatModel> cache = new ConcurrentHashMap<>();
        return modelId -> cache.computeIfAbsent(modelId, id -> {
            if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
                throw new IllegalStateException(
                        "ANTHROPIC_API_KEY is not set — the diagnostics agent cannot call Claude. "
                                + "Set it in your environment or .env and restart.");
            }
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(id)
                    .maxTokens(2048)
                    .temperature(0.0)
                    .build();
        });
    }

    @Bean
    DiagnosticsAgent diagnosticsAgent(
            ChatModelProvider models,
            ModelRouter router,
            TelemetryToolbox toolbox,
            TraceStore traceStore,
            TokenCostEstimator costEstimator,
            ObjectMapper objectMapper) {
        return new DiagnosticsAgent(
                models,
                router,
                new DiagnosticTools(toolbox, objectMapper),
                traceStore,
                costEstimator,
                objectMapper,
                maxToolCalls,
                4000);
    }
}
