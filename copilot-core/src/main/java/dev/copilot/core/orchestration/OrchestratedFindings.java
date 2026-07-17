package dev.copilot.core.orchestration;

import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.infra.DriftReport;

/**
 * The orchestrator's merged result for one incident: the classification plus whichever sub-agent
 * findings were produced. Either finding may be null if that agent was not engaged.
 *
 * @param incidentId the incident identifier
 * @param classification how the incident was classified/routed
 * @param diagnosis the diagnostics-agent result, or null if not engaged
 * @param driftReport the infra-agent result, or null if not engaged
 * @param summary a combined, human-readable summary across the engaged agents
 */
public record OrchestratedFindings(
        String incidentId,
        IncidentClassification classification,
        Diagnosis diagnosis,
        DriftReport driftReport,
        String summary) {}
