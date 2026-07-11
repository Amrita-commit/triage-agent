package dev.copilot.core.telemetry;

import java.util.List;

/**
 * Result of a metrics query: the original query, the time range, and the matching series.
 * Agents cite {@link #query()} plus concrete sample values as evidence for hypotheses.
 */
public record MetricQueryResult(String query, TimeRange range, List<MetricSeries> series) {

    public boolean isEmpty() {
        return series.isEmpty();
    }
}
