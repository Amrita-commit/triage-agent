package dev.copilot.approval;

import dev.copilot.core.approval.ApprovalRequest;
import dev.copilot.core.approval.ApprovalStatus;
import dev.copilot.core.approval.ApprovalStore;
import dev.copilot.core.remediation.ProposedRemediation;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The human-approval gate. A proposed remediation enters as PENDING; a human decision either rejects
 * it or approves it — and approval is the <em>only</em> path that invokes the publisher to create a
 * branch/PR. There is deliberately no "apply" operation anywhere in this service.
 */
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalStore store;
    private final RemediationPublisher publisher;

    public ApprovalService(ApprovalStore store, RemediationPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public ApprovalRequest submit(ProposedRemediation remediation) {
        log.info("Queued remediation {} for approval (incident {})",
                remediation.id(), remediation.sourceIncidentId());
        return store.save(ApprovalRequest.pending(remediation));
    }

    public ApprovalRequest approve(String id, String note) {
        ApprovalRequest request = require(id);
        if (request.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request " + id + " is already " + request.status());
        }
        // The change becomes real only here, and only as a branch/PR for review — never an apply.
        String ref = publisher.publish(request.remediation());
        ApprovalRequest approved = request.approved(note, ref);
        store.save(approved);
        log.info("Approved remediation {} -> {}", id, ref);
        return approved;
    }

    public ApprovalRequest reject(String id, String note) {
        ApprovalRequest request = require(id);
        if (request.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request " + id + " is already " + request.status());
        }
        ApprovalRequest rejected = request.rejected(note);
        store.save(rejected);
        return rejected;
    }

    public List<ApprovalRequest> all() {
        return store.findAll();
    }

    public List<ApprovalRequest> pending() {
        return store.findPending();
    }

    public ApprovalRequest get(String id) {
        return require(id);
    }

    private ApprovalRequest require(String id) {
        return store.findById(id).orElseThrow(() -> new NoSuchElementException("No approval request: " + id));
    }
}
