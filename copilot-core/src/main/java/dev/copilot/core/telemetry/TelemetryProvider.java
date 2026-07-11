package dev.copilot.core.telemetry;

import java.util.List;

/**
 * The provider-agnostic telemetry abstraction — the seam that makes this project "local-first now,
 * AWS later". The local implementation is backed by Prometheus + Loki; a future
 * {@code CloudWatchTelemetryProvider} implements the same three operations against AWS with only
 * configuration changes.
 *
 * <p>All operations are <strong>read-only</strong>. This is a hard guarantee relied on by the
 * diagnostics and infra agents, which are permitted read-only tools only.
 */
public interface TelemetryProvider {

    /**
     * Runs a metrics query (PromQL for the Prometheus implementation) over a time range.
     *
     * @param query backend-specific metrics query
     * @param range the time window to evaluate
     * @return the matching series (never null; may be empty)
     */
    MetricQueryResult queryMetrics(String query, TimeRange range);

    /**
     * Runs a logs query (LogQL for the Loki implementation) over a time range.
     *
     * @param filter backend-specific log selector/filter
     * @param range the time window to search
     * @param limit maximum number of log lines to return
     * @return the matching entries, newest first (never null; may be empty)
     */
    LogQueryResult queryLogs(String filter, TimeRange range, int limit);

    /**
     * Lists currently active (firing or pending) alerts.
     *
     * @return active alerts (never null; may be empty)
     */
    List<Alert> listActiveAlerts();
}
