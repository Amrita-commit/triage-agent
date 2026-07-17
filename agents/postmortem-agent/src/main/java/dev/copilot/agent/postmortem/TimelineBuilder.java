package dev.copilot.agent.postmortem;

import dev.copilot.core.postmortem.TimelineEntry;
import dev.copilot.core.trace.AgentTrace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flattens agent traces into a chronological incident timeline. Each LLM call and tool call becomes
 * a {@link TimelineEntry} carrying its originating trace id, so the postmortem's timeline is fully
 * grounded in recorded steps.
 */
public final class TimelineBuilder {

    private TimelineBuilder() {}

    public static List<TimelineEntry> fromTraces(List<AgentTrace> traces) {
        List<TimelineEntry> entries = new ArrayList<>();
        for (AgentTrace trace : traces) {
            trace.llmCalls().forEach(c -> entries.add(new TimelineEntry(
                    c.timestamp(),
                    trace.agent(),
                    "llm:" + c.taskType(),
                    String.format("%s — %d tokens, $%.4f, %dms", c.model(), c.totalTokens(), c.costUsd(), c.latencyMs()),
                    trace.traceId())));
            trace.toolCalls().forEach(t -> entries.add(new TimelineEntry(
                    t.timestamp(),
                    trace.agent(),
                    "tool:" + t.tool(),
                    (t.error() ? "[error] " : "") + t.arguments() + " → " + t.resultPreview(),
                    trace.traceId())));
        }
        entries.sort(Comparator.comparing(TimelineEntry::at));
        return entries;
    }
}
