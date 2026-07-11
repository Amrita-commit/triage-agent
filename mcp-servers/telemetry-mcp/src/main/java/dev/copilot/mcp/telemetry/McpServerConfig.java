package dev.copilot.mcp.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.telemetry.TelemetryProvider;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MCP server: a {@link TelemetryProvider} backed by Prometheus/Loki, the three telemetry
 * tools, and the HTTP/SSE transport exposed as a servlet at {@code /sse} (event stream) and
 * {@code /mcp/message} (client-to-server messages).
 */
@Configuration
public class McpServerConfig {

    static final String SSE_ENDPOINT = "/sse";
    static final String MESSAGE_ENDPOINT = "/mcp/message";

    @Bean
    TelemetryProvider telemetryProvider(TelemetryProperties props) {
        return new PrometheusLokiTelemetryProvider(props.prometheusUrl(), props.lokiUrl());
    }

    @Bean
    McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    HttpServletSseServerTransportProvider mcpTransportProvider(McpJsonMapper mcpJsonMapper) {
        return HttpServletSseServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .messageEndpoint(MESSAGE_ENDPOINT)
                .sseEndpoint(SSE_ENDPOINT)
                .build();
    }

    @Bean
    McpSyncServer mcpSyncServer(
            HttpServletSseServerTransportProvider transport,
            TelemetryProvider provider,
            ObjectMapper objectMapper) {
        TelemetryTools tools = new TelemetryTools(provider, objectMapper);
        return McpServer.sync(transport)
                .serverInfo("telemetry-mcp", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(tools.specifications())
                .build();
    }

    /** Registers the MCP transport servlet at the SSE and message endpoints. */
    @Bean
    ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServletRegistration(
            HttpServletSseServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletSseServerTransportProvider> reg =
                new ServletRegistrationBean<>(transport, SSE_ENDPOINT, MESSAGE_ENDPOINT);
        reg.setName("mcpTransport");
        reg.setAsyncSupported(true);
        reg.setLoadOnStartup(1);
        return reg;
    }
}
