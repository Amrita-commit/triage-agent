package dev.copilot.agent.postmortem;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Wires the postmortem agent (Claude planning model) and its Markdown output directory. */
@Configuration
public class PostmortemConfig {

    @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${copilot.model.cheap:claude-haiku-4-5-20251001}")
    private String cheapModel;

    @Value("${copilot.model.planning:claude-sonnet-5}")
    private String planningModel;

    @Value("${postmortem.output-dir:docs/incidents}")
    private String outputDir;

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

    @Bean
    ChatModel planningChatModel(ModelRouter router) {
        return AnthropicChatModel.builder()
                .apiKey(anthropicApiKey == null || anthropicApiKey.isBlank() ? "missing-key" : anthropicApiKey)
                .modelName(router.modelFor(TaskType.PLANNING))
                .maxTokens(2048)
                .temperature(0.0)
                .build();
    }

    @Bean
    PostmortemAgent postmortemAgent(
            ChatModel planningChatModel,
            ModelRouter router,
            TokenCostEstimator cost,
            TraceStore traceStore,
            ObjectMapper json) {
        return new PostmortemAgent(
                planningChatModel, router.modelFor(TaskType.PLANNING), cost, traceStore, json, outputDir);
    }
}
