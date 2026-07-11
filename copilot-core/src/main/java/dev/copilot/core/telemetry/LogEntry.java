package dev.copilot.core.telemetry;

import java.time.Instant;
import java.util.Map;

/**
 * A single log line with its source labels (service, container, ...). Provider-agnostic mapping of
 * a Loki stream entry (or a future CloudWatch Logs event).
 */
public record LogEntry(Instant timestamp, String line, Map<String, String> labels) {}
