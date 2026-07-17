package dev.copilot.core.postmortem;

import java.time.Instant;

/**
 * One entry in an incident timeline, derived from an agent trace step (an LLM call or a tool call).
 * Every timeline row carries the id of the trace it came from so postmortem claims are auditable
 * back to real recorded steps.
 *
 * @param at when the step happened
 * @param source which agent produced it (e.g. "diagnostics-agent")
 * @param event short event label (e.g. "tool:query_metrics", "llm:PLANNING")
 * @param detail concrete detail (arguments/result preview, or model + token/cost)
 * @param traceId the id of the {@code AgentTrace} this entry was extracted from
 */
public record TimelineEntry(Instant at, String source, String event, String detail, String traceId) {}
