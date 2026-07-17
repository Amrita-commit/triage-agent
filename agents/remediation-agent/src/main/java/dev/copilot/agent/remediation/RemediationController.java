package dev.copilot.agent.remediation;

import dev.copilot.core.remediation.ProposedRemediation;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entrypoint: propose a remediation and (optionally) enqueue it for human approval. */
@RestController
public class RemediationController {

    private final RemediationAgent agent;
    private final ApprovalClient approvalClient;

    public RemediationController(RemediationAgent agent, ApprovalClient approvalClient) {
        this.agent = agent;
        this.approvalClient = approvalClient;
    }

    public record ProposeRequest(String incidentId, String problem, String targetPath, String currentContent) {}

    @PostMapping("/propose")
    public ResponseEntity<?> propose(@RequestBody ProposeRequest request) {
        if (request.problem() == null || request.problem().isBlank()
                || request.targetPath() == null || request.targetPath().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "'problem' and 'targetPath' are required"));
        }
        try {
            ProposedRemediation remediation = agent.propose(
                    request.incidentId() == null ? "adhoc" : request.incidentId(),
                    request.problem(),
                    request.targetPath(),
                    request.currentContent() == null ? "" : request.currentContent());

            Map<String, Object> body = new HashMap<>();
            body.put("remediation", remediation);
            approvalClient.submit(remediation).ifPresent(id -> body.put("approvalId", id));
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Remediation failed",
                            "detail", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
