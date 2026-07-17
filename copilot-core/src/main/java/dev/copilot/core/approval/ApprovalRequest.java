package dev.copilot.core.approval;

import dev.copilot.core.remediation.ProposedRemediation;
import java.time.Instant;

/**
 * A proposed remediation in the human-approval queue, with its decision state. Immutable: state
 * transitions return a new instance, which the store re-saves under the same id.
 *
 * @param remediation the proposal awaiting a decision
 * @param status PENDING / APPROVED / REJECTED
 * @param createdAt when it was queued
 * @param decidedAt when it was decided (null while PENDING)
 * @param decisionNote the approver's note (null while PENDING)
 * @param resultRef reference to the artifact produced on approval (e.g. branch name / PR URL)
 */
public record ApprovalRequest(
        ProposedRemediation remediation,
        ApprovalStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decisionNote,
        String resultRef) {

    public static ApprovalRequest pending(ProposedRemediation remediation) {
        return new ApprovalRequest(remediation, ApprovalStatus.PENDING, Instant.now(), null, null, null);
    }

    public ApprovalRequest approved(String note, String resultRef) {
        return new ApprovalRequest(remediation, ApprovalStatus.APPROVED, createdAt, Instant.now(), note, resultRef);
    }

    public ApprovalRequest rejected(String note) {
        return new ApprovalRequest(remediation, ApprovalStatus.REJECTED, createdAt, Instant.now(), note, null);
    }

    /** Convenience: the id of the underlying proposal (used as the store key). */
    public String id() {
        return remediation.id();
    }
}
