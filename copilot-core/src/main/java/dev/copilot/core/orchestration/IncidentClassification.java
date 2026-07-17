package dev.copilot.core.orchestration;

/**
 * The orchestrator's classification of an incoming incident, which determines delegation: whether
 * the diagnostics agent (application-level investigation) and/or the infra agent (drift detection)
 * should be engaged.
 *
 * @param category coarse category of the incident
 * @param reasoning the orchestrator's justification for the classification and routing
 * @param needsDiagnostics whether to engage the diagnostics agent
 * @param needsInfra whether to engage the infra/drift agent
 */
public record IncidentClassification(
        Category category, String reasoning, boolean needsDiagnostics, boolean needsInfra) {

    public enum Category {
        /** Application/runtime fault (latency, errors, memory) — diagnostics agent. */
        APPLICATION_FAULT,
        /** Infrastructure/config drift — infra agent. */
        INFRASTRUCTURE_DRIFT,
        /** Symptoms suggest both application and infrastructure involvement. */
        MIXED,
        /** Cannot be confidently classified from the alert alone. */
        UNKNOWN
    }
}
