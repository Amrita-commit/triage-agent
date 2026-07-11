package dev.copilot.agent.diagnostics;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Resolves a concrete {@link ChatModel} for a given model id. Because LangChain4j binds the model
 * id at construction time, model routing is implemented by holding one pre-built {@link ChatModel}
 * per configured id (cheap + planning) and selecting between them here.
 */
@FunctionalInterface
public interface ChatModelProvider {
    ChatModel forModel(String modelId);
}
