package dev.copilot.core.telemetry;

import java.time.Instant;
import java.util.Map;

/**
 * An active alert as reported by the telemetry backend (Prometheus alerting rules today; a future
 * CloudWatch Alarm later).
 *
 * @param name alert name, e.g. "HighCheckoutLatency"
 * @param state "firing" or "pending"
 * @param severity best-effort severity label (e.g. "critical"), may be null
 * @param summary human-readable annotation summarising the alert, may be null
 * @param labels the alert's label set
 * @param activeSince when the alert began firing, may be null
 */
public record Alert(
        String name,
        String state,
        String severity,
        String summary,
        Map<String, String> labels,
        Instant activeSince) {}
