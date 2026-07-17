package dev.copilot.mcp.terraform;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.InfraStateProvider;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the terraform-mcp server: Terraform-backed provider, infra tools, HTTP/SSE transport. */
@Configuration
public class McpServerConfig {

    static final String SSE_ENDPOINT = "/sse";
    static final String MESSAGE_ENDPOINT = "/mcp/message";

    @Bean
    InfraStateProvider infraStateProvider(TerraformProperties props, ObjectMapper objectMapper) {
        return new TerraformCliInfraStateProvider(
                props.workingDir(), props.binary(), props.timeoutSeconds(), objectMapper);
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
            InfraStateProvider provider,
            ObjectMapper objectMapper) {
        InfraTools tools = new InfraTools(provider, objectMapper);
        return McpServer.sync(transport)
                .serverInfo("terraform-mcp", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(tools.specifications())
                .build();
    }

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
