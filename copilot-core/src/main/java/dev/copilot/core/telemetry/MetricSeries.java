package dev.copilot.core.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single labelled time series returned from a metrics query, e.g. the P95 latency of
 * {@code /checkout}. Provider-agnostic: the Prometheus implementation maps its result format onto
 * this, and a future CloudWatch implementation would do the same.
 *
 * @param labels the identifying label set (metric name, uri, status, ...)
 * @param samples ordered samples, oldest first
 */
public record MetricSeries(Map<String, String> labels, List<Sample> samples) {

    /** One (timestamp, value) point. */
    public record Sample(Instant timestamp, double value) {}

    /** The most recent sample value, or {@code NaN} if the series is empty. */
    public double latestValue() {
        return samples.isEmpty() ? Double.NaN : samples.get(samples.size() - 1).value();
    }

    /** The maximum value across all samples, or {@code NaN} if empty. */
    public double max() {
        return samples.stream().mapToDouble(Sample::value).max().orElse(Double.NaN);
    }
}
