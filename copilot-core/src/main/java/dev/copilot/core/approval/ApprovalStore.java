package dev.copilot.core.approval;

import java.util.List;
import java.util.Optional;

/** Stores proposed remediations awaiting (or having received) a human decision. */
public interface ApprovalStore {

    ApprovalRequest save(ApprovalRequest request);

    Optional<ApprovalRequest> findById(String id);

    /** All requests, newest first. */
    List<ApprovalRequest> findAll();

    /** Only the requests still awaiting a decision, newest first. */
    List<ApprovalRequest> findPending();
}
