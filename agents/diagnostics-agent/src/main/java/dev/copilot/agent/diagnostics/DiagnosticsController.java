package dev.copilot.agent.diagnostics;

import dev.copilot.agent.diagnostics.DiagnosticsAgent.DiagnosisResult;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.TraceStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entrypoint for running a diagnosis and reading back its trace. */
@RestController
public class DiagnosticsController {

    private final DiagnosticsAgent agent;
    private final TraceStore traceStore;

    public DiagnosticsController(DiagnosticsAgent agent, TraceStore traceStore) {
        this.agent = agent;
        this.traceStore = traceStore;
    }

    public record DiagnoseRequest(String incidentId, String alert) {}

    @PostMapping("/diagnose")
    public ResponseEntity<?> diagnose(@RequestBody DiagnoseRequest request) {
        if (request.alert() == null || request.alert().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "'alert' is required"));
        }
        try {
            DiagnosisResult result = request.incidentId() == null || request.incidentId().isBlank()
                    ? agent.diagnose(request.alert())
                    : agent.diagnose(request.incidentId(), request.alert());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            // e.g. ANTHROPIC_API_KEY missing
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // Upstream failures (LLM API billing/rate-limit/model errors, MCP transport, ...).
            // Surface a clean message instead of an opaque 500 stack trace.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of(
                            "error", "Diagnosis failed calling an upstream service",
                            "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<AgentTrace> trace(@PathVariable String traceId) {
        return traceStore.findById(traceId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
