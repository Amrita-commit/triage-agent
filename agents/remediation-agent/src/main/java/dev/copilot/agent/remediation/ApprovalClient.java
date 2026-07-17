package dev.copilot.agent.remediation;

import dev.copilot.core.remediation.ProposedRemediation;
import java.util.Optional;

/** Submits a proposed remediation to the approval queue (the human gate). */
public interface ApprovalClient {
    /** Returns the created approval id, or empty if submission is disabled/failed. */
    Optional<String> submit(ProposedRemediation remediation);
}
