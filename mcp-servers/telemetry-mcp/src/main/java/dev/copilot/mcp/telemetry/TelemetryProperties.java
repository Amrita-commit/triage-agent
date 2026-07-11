package dev.copilot.mcp.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endpoints for the telemetry backends. Defaults match the docker-compose service names; override
 * via {@code PROMETHEUS_URL} / {@code LOKI_URL} environment variables.
 */
@ConfigurationProperties(prefix = "telemetry")
public record TelemetryProperties(String prometheusUrl, String lokiUrl) {

    public TelemetryProperties {
        if (prometheusUrl == null || prometheusUrl.isBlank()) {
            prometheusUrl = "http://localhost:9090";
        }
        if (lokiUrl == null || lokiUrl.isBlank()) {
            lokiUrl = "http://localhost:3100";
        }
    }
}
