package dev.copilot.core.telemetry;

import java.time.Duration;
import java.time.Instant;

/**
 * A closed time interval used when querying telemetry backends.
 *
 * <p>Part of the provider-agnostic domain model in {@code copilot-core}. The same value object is
 * consumed by the local Prometheus/Loki implementation today and by a future
 * {@code CloudWatchTelemetryProvider} without change.
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must be non-null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    /** A range covering the last {@code duration} up to now. */
    public static TimeRange lastOf(Duration duration) {
        Instant now = Instant.now();
        return new TimeRange(now.minus(duration), now);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
