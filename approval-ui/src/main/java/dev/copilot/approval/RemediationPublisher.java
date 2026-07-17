package dev.copilot.approval;

import dev.copilot.core.remediation.ProposedRemediation;

/**
 * Turns an <em>approved</em> remediation into a reviewable artifact (a Git branch, and optionally a
 * PR). This runs only after a human approves — it is the sole path by which a proposal becomes a
 * concrete change, and even then it produces a branch/PR for review, never a live apply.
 */
public interface RemediationPublisher {

    /** Publishes the remediation and returns a reference to it (e.g. the branch name or PR URL). */
    String publish(ProposedRemediation remediation);
}
