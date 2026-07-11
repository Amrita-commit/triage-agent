package dev.copilot.core.trace;

import java.time.Instant;

/**
 * A single traced tool call made by an agent, including a preview of the result. Used by the
 * approval UI's trace viewer to show exactly what the agent did and saw.
 *
 * @param resultPreview truncated result text (full results can be large log/metric dumps)
 */
public record ToolCallRecord(
        Instant timestamp,
        String tool,
        String arguments,
        String resultPreview,
        long latencyMs,
        boolean error) {}
