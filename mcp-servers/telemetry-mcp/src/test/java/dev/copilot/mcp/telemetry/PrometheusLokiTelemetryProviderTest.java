package dev.copilot.mcp.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.copilot.core.telemetry.Alert;
import dev.copilot.core.telemetry.LogQueryResult;
import dev.copilot.core.telemetry.MetricQueryResult;
import dev.copilot.core.telemetry.TimeRange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Prometheus/Loki response parsing against a stubbed HTTP backend — no live Prometheus
 * or Loki required.
 */
class PrometheusLokiTelemetryProviderTest {

    private HttpServer server;
    private PrometheusLokiTelemetryProvider provider;

    private static final String QUERY_RANGE_JSON =
            """
            {"status":"success","data":{"resultType":"matrix","result":[
              {"metric":{"__name__":"http_server_requests_seconds","uri":"/checkout"},
               "values":[[1700000000,"0.5"],[1700000015,"3.1"]]}
            ]}}""";

    private static final String LOKI_JSON =
            """
            {"status":"success","data":{"resultType":"streams","result":[
              {"stream":{"service":"demo-app"},
               "values":[["1700000000000000000","ERROR checkout boom"],
                         ["1700000005000000000","INFO ok"]]}
            ]}}""";

    private static final String ALERTS_JSON =
            """
            {"status":"success","data":{"alerts":[
              {"labels":{"alertname":"HighCheckoutLatency","severity":"critical"},
               "annotations":{"summary":"p95 latency high"},
               "state":"firing","activeAt":"2026-07-05T10:00:00Z"}
            ]}}""";

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/query_range", ex -> respond(ex, QUERY_RANGE_JSON));
        server.createContext("/loki/api/v1/query_range", ex -> respond(ex, LOKI_JSON));
        server.createContext("/api/v1/alerts", ex -> respond(ex, ALERTS_JSON));
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        provider = new PrometheusLokiTelemetryProvider(base, base);
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void parsesMetricSeriesWithLatestAndMax() {
        MetricQueryResult result = provider.queryMetrics(
                "histogram_quantile(0.95, ...)", TimeRange.lastOf(Duration.ofMinutes(15)));
        assertThat(result.series()).hasSize(1);
        var series = result.series().get(0);
        assertThat(series.labels()).containsEntry("uri", "/checkout");
        assertThat(series.samples()).hasSize(2);
        assertThat(series.latestValue()).isEqualTo(3.1);
        assertThat(series.max()).isEqualTo(3.1);
    }

    @Test
    void parsesLogsNewestFirst() {
        LogQueryResult result =
                provider.queryLogs("{service=\"demo-app\"}", TimeRange.lastOf(Duration.ofMinutes(15)), 100);
        assertThat(result.entries()).hasSize(2);
        // newest first: the INFO line at t+5s precedes the ERROR line at t+0s
        assertThat(result.entries().get(0).line()).isEqualTo("INFO ok");
        assertThat(result.entries().get(1).line()).contains("ERROR checkout boom");
    }

    @Test
    void parsesActiveAlerts() {
        List<Alert> alerts = provider.listActiveAlerts();
        assertThat(alerts).hasSize(1);
        Alert a = alerts.get(0);
        assertThat(a.name()).isEqualTo("HighCheckoutLatency");
        assertThat(a.state()).isEqualTo("firing");
        assertThat(a.severity()).isEqualTo("critical");
        assertThat(a.summary()).isEqualTo("p95 latency high");
        assertThat(a.labels()).containsEntry("severity", "critical");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
