package dev.copilot.mcp.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.telemetry.Alert;
import dev.copilot.core.telemetry.LogEntry;
import dev.copilot.core.telemetry.LogQueryResult;
import dev.copilot.core.telemetry.MetricQueryResult;
import dev.copilot.core.telemetry.MetricSeries;
import dev.copilot.core.telemetry.TelemetryProvider;
import dev.copilot.core.telemetry.TimeRange;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Phase 1 acceptance test: a real MCP client can call all three telemetry tools over HTTP/SSE. The
 * telemetry backend is faked so no live Prometheus/Loki is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TelemetryMcpIntegrationTest {

    @LocalServerPort
    int port;

    @TestConfiguration
    static class FakeTelemetry {
        @Bean
        @Primary
        TelemetryProvider fakeProvider() {
            return new TelemetryProvider() {
                @Override
                public MetricQueryResult queryMetrics(String query, TimeRange range) {
                    var series = new MetricSeries(
                            Map.of("uri", "/checkout"),
                            List.of(new MetricSeries.Sample(Instant.now(), 3.0)));
                    return new MetricQueryResult(query, range, List.of(series));
                }

                @Override
                public LogQueryResult queryLogs(String filter, TimeRange range, int limit) {
                    return new LogQueryResult(
                            filter, range,
                            List.of(new LogEntry(Instant.now(), "ERROR checkout boom", Map.of("service", "demo-app"))));
                }

                @Override
                public List<Alert> listActiveAlerts() {
                    return List.of(new Alert(
                            "HighCheckoutLatency", "firing", "critical", "p95 high",
                            Map.of("severity", "critical"), Instant.now()));
                }
            };
        }
    }

    private McpSyncClient newClient() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/sse")
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
        return client;
    }

    @Test
    void exposesAllThreeTools() {
        try (McpSyncClient client = newClient()) {
            List<String> names = client.listTools().tools().stream()
                    .map(io.modelcontextprotocol.spec.McpSchema.Tool::name)
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("query_metrics", "query_logs", "list_alerts");
        }
    }

    @Test
    void queryMetricsReturnsEvidence() {
        try (McpSyncClient client = newClient()) {
            CallToolResult result = client.callTool(
                    new CallToolRequest("query_metrics", Map.of("query", "up", "rangeMinutes", 15)));
            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(firstText(result)).contains("/checkout").contains("3.0");
        }
    }

    @Test
    void listAlertsReturnsFiringAlert() {
        try (McpSyncClient client = newClient()) {
            CallToolResult result = client.callTool(new CallToolRequest("list_alerts", Map.of()));
            assertThat(firstText(result)).contains("HighCheckoutLatency").contains("firing");
        }
    }

    private static String firstText(CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof TextContent)
                .map(c -> ((TextContent) c).text())
                .findFirst()
                .orElseThrow();
    }
}
