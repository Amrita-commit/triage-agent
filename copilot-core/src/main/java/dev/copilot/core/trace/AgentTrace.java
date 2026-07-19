package dev.copilot.core.trace;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The full trace of one agent investigation: every LLM call and tool call, in order. Accumulated
 * live during a run, then persisted to a {@link TraceStore}. Powers the approval-UI trace viewer
 * and the postmortem agent's timeline. Thread-safe for concurrent appends.
 *
 * <p>{@code @JsonAutoDetect} exposes the private fields to Jackson so the {@code /traces/{id}}
 * endpoints serialize the full trace (its accessors are record-style, not JavaBean getters).
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE)
public class AgentTrace {

    private final String traceId;
    private final String agent;
    private final String incident;
    private final Instant startedAt;
    private final List<LlmCallRecord> llmCalls = new CopyOnWriteArrayList<>();
    private final List<ToolCallRecord> toolCalls = new CopyOnWriteArrayList<>();

    public AgentTrace(String traceId, String agent, String incident) {
        this.traceId = traceId;
        this.agent = agent;
        this.incident = incident;
        this.startedAt = Instant.now();
    }

    public void addLlmCall(LlmCallRecord record) {
        llmCalls.add(record);
    }

    public void addToolCall(ToolCallRecord record) {
        toolCalls.add(record);
    }

    public String traceId() {
        return traceId;
    }

    public String agent() {
        return agent;
    }

    public String incident() {
        return incident;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public List<LlmCallRecord> llmCalls() {
        return List.copyOf(llmCalls);
    }

    public List<ToolCallRecord> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public int toolCallCount() {
        return toolCalls.size();
    }

    public int totalTokens() {
        return llmCalls.stream().mapToInt(LlmCallRecord::totalTokens).sum();
    }

    public double totalCostUsd() {
        return llmCalls.stream().mapToDouble(LlmCallRecord::costUsd).sum();
    }
}
