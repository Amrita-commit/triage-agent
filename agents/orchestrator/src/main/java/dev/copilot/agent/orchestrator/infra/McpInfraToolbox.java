package dev.copilot.agent.orchestrator.infra;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.util.UUID;

/**
 * {@link InfraToolbox} backed by {@code terraform-mcp} over MCP (HTTP/SSE). The MCP client is
 * created lazily so the orchestrator can boot before terraform-mcp is up.
 */
public class McpInfraToolbox implements InfraToolbox, AutoCloseable {

    private final String sseUrl;
    private volatile McpClient client;

    public McpInfraToolbox(String sseUrl) {
        this.sseUrl = sseUrl;
    }

    private McpClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    HttpMcpTransport transport = new HttpMcpTransport.Builder()
                            .sseUrl(sseUrl)
                            .logRequests(false)
                            .logResponses(false)
                            .build();
                    client = new DefaultMcpClient.Builder()
                            .clientName("orchestrator-infra-agent")
                            .transport(transport)
                            .build();
                }
            }
        }
        return client;
    }

    @Override
    public String readState() {
        return exec("read_state");
    }

    @Override
    public String planDiff() {
        return exec("plan_diff");
    }

    private String exec(String tool) {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(tool)
                    .arguments("{}")
                    .build();
            return client().executeTool(request);
        } catch (Exception e) {
            return "Tool transport error calling " + tool + ": " + e.getMessage();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
