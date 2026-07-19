package dev.copilot.approval.trace;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whitelisted trace sources: a map of short name → agent base URL. The trace proxy only fetches
 * from these (no open proxy / SSRF).
 */
@ConfigurationProperties(prefix = "trace")
public record TraceSourcesProperties(Map<String, String> sources) {

    public TraceSourcesProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }
}
