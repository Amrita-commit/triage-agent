package dev.copilot.approval;

import dev.copilot.core.approval.ApprovalRequest;
import dev.copilot.core.remediation.ProposedRemediation;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the approval queue. The UI (static page) calls these endpoints. */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    public record Decision(String note) {}

    @GetMapping
    public List<ApprovalRequest> all() {
        return service.all();
    }

    @GetMapping("/pending")
    public List<ApprovalRequest> pending() {
        return service.pending();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApprovalRequest> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.get(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Submit a proposed remediation to the queue (called by the remediation agent). */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody ProposedRemediation remediation) {
        if (remediation == null || remediation.id() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "a remediation with an id is required"));
        }
        ApprovalRequest request = service.submit(remediation);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestBody(required = false) Decision decision) {
        return decide(() -> service.approve(id, note(decision)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, @RequestBody(required = false) Decision decision) {
        return decide(() -> service.reject(id, note(decision)));
    }

    private ResponseEntity<?> decide(java.util.function.Supplier<ApprovalRequest> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // e.g. publishing to a branch failed
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static String note(Decision d) {
        return d == null ? "" : (d.note() == null ? "" : d.note());
    }
}
