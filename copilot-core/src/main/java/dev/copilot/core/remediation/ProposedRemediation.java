package dev.copilot.core.remediation;

/**
 * A remediation proposed by the remediation agent. It is a <strong>proposal only</strong>: a concrete
 * file change plus rollback notes. It is never applied to running infrastructure — on human approval
 * it becomes a branch/PR for review. This separation is the core guardrail of the system.
 *
 * @param id stable id for this proposal
 * @param sourceIncidentId the incident this remediates
 * @param title short human-readable title
 * @param rationale why this change addresses the incident (cites the diagnosis/drift)
 * @param targetPath the repo-relative file to change (e.g. "infra/local/main.tf")
 * @param newContent the full proposed new content of {@code targetPath}
 * @param unifiedDiff a human-readable unified diff (old vs new) for display in the approval UI
 * @param rollbackNotes how to revert if the change causes problems
 * @param risk blast-radius classification
 */
public record ProposedRemediation(
        String id,
        String sourceIncidentId,
        String title,
        String rationale,
        String targetPath,
        String newContent,
        String unifiedDiff,
        String rollbackNotes,
        RiskLevel risk) {}
