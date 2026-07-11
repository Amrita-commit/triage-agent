package dev.copilot.core.trace;

import dev.copilot.core.model.TaskType;
import java.time.Instant;

/**
 * A single traced LLM call: which task type it served, the model used, token usage, latency, and
 * estimated cost. Required by the engineering standard "every LLM call logged with model, tokens
 * in/out, latency, cost estimate".
 */
public record LlmCallRecord(
        Instant timestamp,
        TaskType taskType,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        double costUsd) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
