package dev.copilot.mcp.terraform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.DriftChange;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.infra.InfraStateProvider;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Phase 3 acceptance (MCP layer): a real MCP client can call read_state and plan_diff. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TerraformMcpIntegrationTest {

    @LocalServerPort
    int port;

    @TestConfiguration
    static class FakeInfra {
        @Bean
        @Primary
        InfraStateProvider fakeProvider() {
            return new InfraStateProvider() {
                @Override
                public String readState() {
                    return "{\"resources\":[{\"address\":\"docker_container.demo_app\"}]}";
                }

                @Override
                public DriftReport planDiff() {
                    return new DriftReport(
                            true,
                            List.of(new DriftChange(
                                    "docker_container.demo_app", "env", "LOG_LEVEL=INFO", "LOG_LEVEL=DEBUG", "update")),
                            "Drift detected: 1 attribute change(s) across 1 resource(s).");
                }
            };
        }
    }

    private McpSyncClient newClient() {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .sseEndpoint("/sse")
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
        return client;
    }

    @Test
    void exposesReadStateAndPlanDiff() {
        try (McpSyncClient client = newClient()) {
            List<String> names = client.listTools().tools().stream()
                    .map(io.modelcontextprotocol.spec.McpSchema.Tool::name)
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("read_state", "plan_diff");
        }
    }

    @Test
    void planDiffReturnsStructuredDrift() {
        try (McpSyncClient client = newClient()) {
            CallToolResult result = client.callTool(new CallToolRequest("plan_diff", Map.of()));
            String text = result.content().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElseThrow();
            assertThat(text).contains("docker_container.demo_app").contains("env").contains("DEBUG");
        }
    }
}
