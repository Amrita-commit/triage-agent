package dev.copilot.mcp.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MCP server exposing read-only telemetry tools against the local Prometheus + Loki stack.
 *
 * <p>Tools: {@code query_metrics}, {@code query_logs}, {@code list_alerts}. Served over MCP's
 * HTTP/SSE transport so the agent services can reach it as a normal docker-compose service.
 */
@SpringBootApplication
@EnableConfigurationProperties(TelemetryProperties.class)
public class TelemetryMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelemetryMcpApplication.class, args);
    }
}
