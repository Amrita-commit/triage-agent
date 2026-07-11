package dev.copilot.core.diagnosis;

import java.util.List;

/**
 * The structured output of the diagnostics agent. Emitted as JSON by the agent and deserialized
 * into this record — never free-text parsed by downstream agents (remediation, postmortem).
 *
 * @param incidentSummary one-line restatement of the incident under investigation
 * @param hypothesis the agent's best explanation of the root cause
 * @param evidence concrete evidence supporting the hypothesis (must be non-empty for a confident result)
 * @param confidence 0.0–1.0 confidence in the hypothesis
 * @param suggestedNextSteps recommended follow-up actions (diagnostic or remediation directions)
 * @param truncated true if the investigation hit the tool-call budget before concluding
 */
public record Diagnosis(
        String incidentSummary,
        String hypothesis,
        List<Evidence> evidence,
        double confidence,
        List<String> suggestedNextSteps,
        boolean truncated) {

    public Diagnosis {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        suggestedNextSteps = suggestedNextSteps == null ? List.of() : List.copyOf(suggestedNextSteps);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    /** A diagnosis is only trustworthy if it cites at least one concrete piece of evidence. */
    public boolean isEvidenceBacked() {
        return !evidence.isEmpty();
    }
}
