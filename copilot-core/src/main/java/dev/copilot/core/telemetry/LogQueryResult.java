package dev.copilot.core.telemetry;

import java.util.List;

/** Result of a logs query: the original filter, the time range, and matching entries (newest first). */
public record LogQueryResult(String filter, TimeRange range, List<LogEntry> entries) {

    public int count() {
        return entries.size();
    }
}
