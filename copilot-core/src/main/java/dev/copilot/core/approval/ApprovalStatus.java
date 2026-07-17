package dev.copilot.core.approval;

/** Lifecycle of a proposed action in the human-approval queue. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
