package dev.copilot.agent.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.util.Map;
import java.util.UUID;

/**
 * {@link TelemetryToolbox} backed by the {@code telemetry-mcp} server over MCP (HTTP/SSE). The MCP
 * client is created lazily on first use so the agent app can boot even when telemetry-mcp is not
 * yet up. The agent only ever calls the server's read-only tools.
 */
public class McpTelemetryToolbox implements TelemetryToolbox, AutoCloseable {

    private final String sseUrl;
    private final ObjectMapper json;
    private volatile McpClient client;

    public McpTelemetryToolbox(String sseUrl, ObjectMapper json) {
        this.sseUrl = sseUrl;
        this.json = json;
    }

    private McpClient client() {
        McpClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    HttpMcpTransport transport = new HttpMcpTransport.Builder()
                            .sseUrl(sseUrl)
                            .logRequests(false)
                            .logResponses(false)
                            .build();
                    client = new DefaultMcpClient.Builder()
                            .clientName("diagnostics-agent")
                            .transport(transport)
                            .build();
                }
                c = client;
            }
        }
        return c;
    }

    @Override
    public String queryMetrics(String query, int rangeMinutes) {
        return exec("query_metrics", Map.of("query", query, "rangeMinutes", rangeMinutes));
    }

    @Override
    public String queryLogs(String filter, int rangeMinutes, int limit) {
        return exec("query_logs", Map.of("filter", filter, "rangeMinutes", rangeMinutes, "limit", limit));
    }

    @Override
    public String listAlerts() {
        return exec("list_alerts", Map.of());
    }

    private String exec(String name, Map<String, Object> args) {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .arguments(json.writeValueAsString(args))
                    .build();
            return client().executeTool(request);
        } catch (Exception e) {
            return "Tool transport error calling " + name + ": " + e.getMessage();
        }
    }

    @Override
    public void close() {
        McpClient c = client;
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
