package dev.copilot.mcp.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import dev.copilot.core.telemetry.Alert;
import dev.copilot.core.telemetry.LogEntry;
import dev.copilot.core.telemetry.LogQueryResult;
import dev.copilot.core.telemetry.MetricQueryResult;
import dev.copilot.core.telemetry.MetricSeries;
import dev.copilot.core.telemetry.TelemetryProvider;
import dev.copilot.core.telemetry.TimeRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link TelemetryProvider} backed by the local stack: Prometheus for metrics and active alerts,
 * Loki for logs. Read-only. A future {@code CloudWatchTelemetryProvider} would implement the same
 * interface against AWS — this class is the only thing that changes between local and cloud.
 */
public class PrometheusLokiTelemetryProvider implements TelemetryProvider {

    private static final Logger log = LoggerFactory.getLogger(PrometheusLokiTelemetryProvider.class);

    private final RestClient prometheus;
    private final RestClient loki;

    public PrometheusLokiTelemetryProvider(String prometheusUrl, String lokiUrl) {
        this.prometheus = RestClient.builder().baseUrl(prometheusUrl).build();
        this.loki = RestClient.builder().baseUrl(lokiUrl).build();
    }

    @Override
    public MetricQueryResult queryMetrics(String query, TimeRange range) {
        String step = chooseStep(range);
        JsonNode root = prometheus.get()
                .uri("/api/v1/query_range?query={q}&start={s}&end={e}&step={st}",
                        query,
                        epochSeconds(range.start()),
                        epochSeconds(range.end()),
                        step)
                .retrieve()
                .body(JsonNode.class);

        List<MetricSeries> series = new ArrayList<>();
        JsonNode results = path(root, "data", "result");
        if (results != null && results.isArray()) {
            for (JsonNode r : results) {
                Map<String, String> labels = readStringMap(r.get("metric"));
                List<MetricSeries.Sample> samples = new ArrayList<>();
                JsonNode values = r.get("values"); // matrix: [[ts, "val"], ...]
                if (values != null && values.isArray()) {
                    for (JsonNode pair : values) {
                        Instant ts = Instant.ofEpochMilli((long) (pair.get(0).asDouble() * 1000));
                        samples.add(new MetricSeries.Sample(ts, parseDouble(pair.get(1).asText())));
                    }
                }
                JsonNode instant = r.get("value"); // vector: [ts, "val"]
                if (instant != null && instant.isArray()) {
                    Instant ts = Instant.ofEpochMilli((long) (instant.get(0).asDouble() * 1000));
                    samples.add(new MetricSeries.Sample(ts, parseDouble(instant.get(1).asText())));
                }
                series.add(new MetricSeries(labels, samples));
            }
        }
        log.debug("queryMetrics '{}' -> {} series", query, series.size());
        return new MetricQueryResult(query, range, series);
    }

    @Override
    public LogQueryResult queryLogs(String filter, TimeRange range, int limit) {
        int effectiveLimit = limit <= 0 ? 100 : Math.min(limit, 5000);
        JsonNode root = loki.get()
                .uri("/loki/api/v1/query_range?query={q}&start={s}&end={e}&limit={l}&direction=backward",
                        filter,
                        epochNanos(range.start()),
                        epochNanos(range.end()),
                        effectiveLimit)
                .retrieve()
                .body(JsonNode.class);

        List<LogEntry> entries = new ArrayList<>();
        JsonNode results = path(root, "data", "result");
        if (results != null && results.isArray()) {
            for (JsonNode stream : results) {
                Map<String, String> labels = readStringMap(stream.get("stream"));
                JsonNode values = stream.get("values"); // [[ "<ns ts>", "line" ], ...]
                if (values != null && values.isArray()) {
                    for (JsonNode pair : values) {
                        long nanos = Long.parseLong(pair.get(0).asText());
                        Instant ts = Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
                        entries.add(new LogEntry(ts, pair.get(1).asText(), labels));
                    }
                }
            }
        }
        entries.sort((a, b) -> b.timestamp().compareTo(a.timestamp())); // newest first
        log.debug("queryLogs '{}' -> {} entries", filter, entries.size());
        return new LogQueryResult(filter, range, entries);
    }

    @Override
    public List<Alert> listActiveAlerts() {
        JsonNode root = prometheus.get().uri("/api/v1/alerts").retrieve().body(JsonNode.class);
        List<Alert> alerts = new ArrayList<>();
        JsonNode arr = path(root, "data", "alerts");
        if (arr != null && arr.isArray()) {
            for (JsonNode a : arr) {
                Map<String, String> labels = readStringMap(a.get("labels"));
                JsonNode annotations = a.get("annotations");
                alerts.add(new Alert(
                        labels.getOrDefault("alertname", "unknown"),
                        text(a, "state"),
                        labels.get("severity"),
                        annotations != null && annotations.hasNonNull("summary")
                                ? annotations.get("summary").asText()
                                : null,
                        labels,
                        parseInstant(text(a, "activeAt"))));
            }
        }
        return alerts;
    }

    // --- helpers ---------------------------------------------------------------

    private static String chooseStep(TimeRange range) {
        long minutes = Math.max(1, range.duration().toMinutes());
        // Keep the number of points bounded (<= ~240) regardless of window size.
        long stepSeconds = Math.max(5, (minutes * 60) / 240);
        return stepSeconds + "s";
    }

    private static double epochSeconds(Instant i) {
        return i.toEpochMilli() / 1000.0;
    }

    private static long epochNanos(Instant i) {
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    private static double parseDouble(String s) {
        return switch (s) {
            case "NaN" -> Double.NaN;
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(s);
        };
    }

    private static Instant parseInstant(String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static JsonNode path(JsonNode root, String... fields) {
        JsonNode n = root;
        for (String f : fields) {
            if (n == null) {
                return null;
            }
            n = n.get(f);
        }
        return n;
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), e.getValue().asText());
            }
        }
        return map;
    }
}
