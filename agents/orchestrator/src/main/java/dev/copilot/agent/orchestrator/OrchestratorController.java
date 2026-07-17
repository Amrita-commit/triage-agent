package dev.copilot.agent.orchestrator;

import dev.copilot.core.orchestration.OrchestratedFindings;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.TraceStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entrypoint for the orchestrator. */
@RestController
public class OrchestratorController {

    private final OrchestratorAgent orchestrator;
    private final TraceStore traceStore;

    public OrchestratorController(OrchestratorAgent orchestrator, TraceStore traceStore) {
        this.orchestrator = orchestrator;
        this.traceStore = traceStore;
    }

    public record IncidentRequest(String incidentId, String alert) {}

    @PostMapping("/incident")
    public ResponseEntity<?> handle(@RequestBody IncidentRequest request) {
        if (request.alert() == null || request.alert().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "'alert' is required"));
        }
        try {
            OrchestratedFindings findings = request.incidentId() == null || request.incidentId().isBlank()
                    ? orchestrator.handle(request.alert())
                    : orchestrator.handle(request.incidentId(), request.alert());
            return ResponseEntity.ok(findings);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of(
                            "error", "Orchestration failed",
                            "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<AgentTrace> trace(@PathVariable String traceId) {
        return traceStore.findById(traceId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
