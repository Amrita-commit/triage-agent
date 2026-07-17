package dev.copilot.agent.orchestrator;

import dev.copilot.core.diagnosis.Diagnosis;
import java.util.Map;
import org.springframework.web.client.RestClient;

/**
 * Calls the diagnostics-agent service's {@code POST /diagnose} endpoint and returns its
 * {@link Diagnosis}. The diagnostics agent runs as a separate service (loose coupling via HTTP).
 */
public class HttpDiagnosticsClient implements DiagnosticsClient {

    private final RestClient http;

    public HttpDiagnosticsClient(String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Diagnosis diagnose(String incidentId, String alert) {
        DiagnoseResponse response = http.post()
                .uri("/diagnose")
                .body(Map.of("incidentId", incidentId, "alert", alert))
                .retrieve()
                .body(DiagnoseResponse.class);
        return response == null ? null : response.diagnosis();
    }

    /** Mirrors the diagnostics-agent DiagnosisResult envelope. */
    private record DiagnoseResponse(Diagnosis diagnosis, String traceId) {}
}
