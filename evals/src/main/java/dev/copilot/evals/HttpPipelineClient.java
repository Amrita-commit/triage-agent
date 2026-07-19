package dev.copilot.evals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * {@link PipelineClient} that runs the diagnostics agent over HTTP and reads the resulting trace for
 * tool-call / token / cost metrics.
 */
public class HttpPipelineClient implements PipelineClient {

    private final RestClient http;

    public HttpPipelineClient(String diagnosticsAgentUrl) {
        this.http = RestClient.builder().baseUrl(diagnosticsAgentUrl).build();
    }

    @Override
    public PipelineResult run(String incidentId, String alert) {
        long t0 = System.nanoTime();
        try {
            JsonNode resp = http.post()
                    .uri("/diagnose")
                    .body(Map.of("incidentId", incidentId, "alert", alert))
                    .retrieve()
                    .body(JsonNode.class);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000;

            JsonNode diagnosis = resp.path("diagnosis");
            String hypothesis = diagnosis.path("hypothesis").asText("");
            double confidence = diagnosis.path("confidence").asDouble(0);

            int toolCalls = 0;
            int totalTokens = 0;
            double cost = 0.0;
            String traceId = resp.path("traceId").asText(null);
            if (traceId != null) {
                try {
                    JsonNode trace = http.get().uri("/traces/{id}", traceId).retrieve().body(JsonNode.class);
                    toolCalls = trace.path("toolCalls").size();
                    for (JsonNode c : trace.path("llmCalls")) {
                        totalTokens += c.path("inputTokens").asInt() + c.path("outputTokens").asInt();
                        cost += c.path("costUsd").asDouble();
                    }
                } catch (RuntimeException ignored) {
                    // metrics are best-effort
                }
            }
            return new PipelineResult(hypothesis, confidence, toolCalls, totalTokens, cost, latencyMs, false, null);
        } catch (RuntimeException e) {
            long latencyMs = (System.nanoTime() - t0) / 1_000_000;
            return PipelineResult.failed(latencyMs, e.getMessage());
        }
    }
}
