package dev.copilot.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the full Spring wiring boots with no ANTHROPIC_API_KEY and no telemetry-mcp running —
 * the Claude models and MCP client are created lazily, so the service starts and its health check
 * passes regardless. Live diagnosis then only needs the key + telemetry-mcp.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiagnosticsAgentApplicationTests {

    @Autowired
    DiagnosticsAgent agent;

    @Test
    void contextLoadsWithoutApiKey() {
        assertThat(agent).isNotNull();
    }
}
