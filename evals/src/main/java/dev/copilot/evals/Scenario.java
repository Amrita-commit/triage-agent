package dev.copilot.evals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * A fault scenario: how to inject the fault, the alert to feed the agent, and what the correct
 * diagnosis should look like (for scoring).
 *
 * @param id unique scenario id
 * @param faultType e.g. "latency", "error-rate", "memory-leak"
 * @param endpoint demo-app fault endpoint to POST (e.g. "/faults/latency")
 * @param params query params for the fault endpoint (e.g. {ms: 3000})
 * @param drive traffic to generate after injecting, so the fault shows up in telemetry
 * @param alert the alert text handed to the agent
 * @param expectedRootCause a human-readable expected root cause (for reference / LLM judge)
 * @param expectedComponent the component expected to be blamed (e.g. "demo-app")
 * @param keywords keywords that a correct hypothesis should contain (keyword-match scoring)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(
        String id,
        String faultType,
        String endpoint,
        Map<String, Object> params,
        Drive drive,
        String alert,
        String expectedRootCause,
        String expectedComponent,
        List<String> keywords) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Drive(String path, String method, int count) {
        public String methodOrDefault() {
            return method == null || method.isBlank() ? "POST" : method;
        }
    }

    public List<String> keywordsOrEmpty() {
        return keywords == null ? List.of() : keywords;
    }
}
