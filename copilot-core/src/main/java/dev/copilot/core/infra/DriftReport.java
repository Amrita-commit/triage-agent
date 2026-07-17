package dev.copilot.core.infra;

import java.util.List;

/**
 * Structured output of the infra/drift agent: whether the live infrastructure has drifted from its
 * Terraform-declared desired state, and the specific changes. Consumed by the orchestrator and,
 * later, the remediation agent (which turns drift into a proposed Terraform diff).
 *
 * @param driftDetected true if the live state differs from the declared state
 * @param changes the specific drifted attributes (empty when no drift)
 * @param summary human-readable summary of the drift (or confirmation of no drift)
 */
public record DriftReport(boolean driftDetected, List<DriftChange> changes, String summary) {

    public DriftReport {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public static DriftReport noDrift(String summary) {
        return new DriftReport(false, List.of(), summary);
    }
}
