package dev.copilot.agent.diagnostics;

/**
 * The read-only telemetry tools available to the diagnostics agent. Implementations back these
 * with the {@code telemetry-mcp} server (over MCP) in production, or with a fake in tests. The
 * agent is deliberately given <em>only</em> read-only tools — it can observe, never mutate.
 */
public interface TelemetryToolbox {

    /** Run a PromQL query over the last {@code rangeMinutes}; returns JSON text. */
    String queryMetrics(String query, int rangeMinutes);

    /** Run a LogQL query over the last {@code rangeMinutes}, up to {@code limit} lines; returns JSON text. */
    String queryLogs(String filter, int rangeMinutes, int limit);

    /** List currently active alerts; returns JSON text. */
    String listAlerts();
}
