package dev.copilot.mcp.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.telemetry.TelemetryProvider;
import dev.copilot.core.telemetry.TimeRange;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the three read-only MCP tools the copilot agents call. Each tool serialises its result to
 * JSON text so the agent receives structured, citable evidence.
 */
public class TelemetryTools {

    private static final Logger log = LoggerFactory.getLogger(TelemetryTools.class);

    private final TelemetryProvider provider;
    private final ObjectMapper json;

    public TelemetryTools(TelemetryProvider provider, ObjectMapper json) {
        this.provider = provider;
        this.json = json;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(queryMetrics(), queryLogs(), listAlerts());
    }

    private SyncToolSpecification queryMetrics() {
        Tool tool = Tool.builder()
                .name("query_metrics")
                .description("Run a PromQL query over a recent time window against Prometheus. "
                        + "Returns matching time series with their labels and sample values. Read-only.")
                .inputSchema(objectSchema(Map.of(
                        "query", stringProp("PromQL expression, e.g. "
                                + "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le,uri))"),
                        "rangeMinutes", intProp("Look-back window in minutes (default 15)")),
                        List.of("query")))
                .build();
        return new SyncToolSpecification(tool, handler((exchange, args) -> {
            String query = str(args, "query");
            TimeRange range = TimeRange.lastOf(Duration.ofMinutes(intOr(args, "rangeMinutes", 15)));
            return json.writeValueAsString(provider.queryMetrics(query, range));
        }));
    }

    private SyncToolSpecification queryLogs() {
        Tool tool = Tool.builder()
                .name("query_logs")
                .description("Run a LogQL query over a recent time window against Loki. "
                        + "Returns matching log lines (newest first) with their source labels. Read-only.")
                .inputSchema(objectSchema(Map.of(
                        "filter", stringProp("LogQL selector/filter, e.g. {service=\"demo-app\"} |= \"ERROR\""),
                        "rangeMinutes", intProp("Look-back window in minutes (default 15)"),
                        "limit", intProp("Maximum number of log lines to return (default 100)")),
                        List.of("filter")))
                .build();
        return new SyncToolSpecification(tool, handler((exchange, args) -> {
            String filter = str(args, "filter");
            TimeRange range = TimeRange.lastOf(Duration.ofMinutes(intOr(args, "rangeMinutes", 15)));
            return json.writeValueAsString(provider.queryLogs(filter, range, intOr(args, "limit", 100)));
        }));
    }

    private SyncToolSpecification listAlerts() {
        Tool tool = Tool.builder()
                .name("list_alerts")
                .description("List currently active (firing or pending) alerts from Prometheus. Read-only.")
                .inputSchema(objectSchema(Map.of(), List.of()))
                .build();
        return new SyncToolSpecification(
                tool, handler((exchange, args) -> json.writeValueAsString(provider.listActiveAlerts())));
    }

    // --- handler plumbing -------------------------------------------------------

    /** Wraps a result-producing lambda, converting output to a text {@link CallToolResult}. */
    private BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange, CallToolRequest, CallToolResult> handler(
            ThrowingToolFn fn) {
        return (exchange, request) -> {
            try {
                String result = fn.apply(exchange, request.arguments() == null ? Map.of() : request.arguments());
                return CallToolResult.builder().addTextContent(result).build();
            } catch (Exception e) {
                log.warn("Tool '{}' failed: {}", request.name(), e.toString());
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("Tool error: " + e.getMessage())
                        .build();
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingToolFn {
        String apply(io.modelcontextprotocol.server.McpSyncServerExchange exchange, Map<String, Object> args)
                throws Exception;
    }

    // --- schema + arg helpers ---------------------------------------------------

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> intProp(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return v.toString();
    }

    private static int intOr(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
