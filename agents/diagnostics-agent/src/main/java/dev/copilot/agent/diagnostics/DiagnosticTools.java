package dev.copilot.agent.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;

/**
 * Bridges the read-only {@link TelemetryToolbox} to LangChain4j: the {@link #specifications()} the
 * model sees, and {@link #execute(ToolExecutionRequest)} which parses the model's JSON arguments
 * and dispatches to the toolbox.
 */
public class DiagnosticTools {

    static final String QUERY_METRICS = "query_metrics";
    static final String QUERY_LOGS = "query_logs";
    static final String LIST_ALERTS = "list_alerts";

    private final TelemetryToolbox toolbox;
    private final ObjectMapper json;

    public DiagnosticTools(TelemetryToolbox toolbox, ObjectMapper json) {
        this.toolbox = toolbox;
        this.json = json;
    }

    public List<ToolSpecification> specifications() {
        ToolSpecification metrics = ToolSpecification.builder()
                .name(QUERY_METRICS)
                .description("Run a PromQL query over a recent window against Prometheus. Returns time "
                        + "series with labels and sample values. Read-only.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "PromQL expression")
                        .addIntegerProperty("rangeMinutes", "Look-back window in minutes (default 15)")
                        .required("query")
                        .build())
                .build();

        ToolSpecification logs = ToolSpecification.builder()
                .name(QUERY_LOGS)
                .description("Run a LogQL query over a recent window against Loki. Returns matching log "
                        + "lines (newest first) with labels. Read-only.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("filter", "LogQL selector/filter")
                        .addIntegerProperty("rangeMinutes", "Look-back window in minutes (default 15)")
                        .addIntegerProperty("limit", "Max log lines (default 100)")
                        .required("filter")
                        .build())
                .build();

        ToolSpecification alerts = ToolSpecification.builder()
                .name(LIST_ALERTS)
                .description("List currently active (firing/pending) alerts from Prometheus. Read-only.")
                .parameters(JsonObjectSchema.builder().build())
                .build();

        return List.of(metrics, logs, alerts);
    }

    /** Executes a tool call, returning its JSON-text result. Throws for unknown tools. */
    public String execute(ToolExecutionRequest request) throws Exception {
        JsonNode args = parseArgs(request.arguments());
        return switch (request.name()) {
            case QUERY_METRICS -> toolbox.queryMetrics(
                    text(args, "query"), intOr(args, "rangeMinutes", 15));
            case QUERY_LOGS -> toolbox.queryLogs(
                    text(args, "filter"), intOr(args, "rangeMinutes", 15), intOr(args, "limit", 100));
            case LIST_ALERTS -> toolbox.listAlerts();
            default -> throw new IllegalArgumentException("Unknown tool: " + request.name());
        };
    }

    private JsonNode parseArgs(String arguments) throws Exception {
        if (arguments == null || arguments.isBlank()) {
            return json.createObjectNode();
        }
        return json.readTree(arguments);
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args.get(field);
        if (n == null || n.isNull()) {
            throw new IllegalArgumentException("Missing required argument: " + field);
        }
        return n.asText();
    }

    private static int intOr(JsonNode args, String field, int def) {
        JsonNode n = args.get(field);
        return n == null || n.isNull() ? def : n.asInt(def);
    }
}
