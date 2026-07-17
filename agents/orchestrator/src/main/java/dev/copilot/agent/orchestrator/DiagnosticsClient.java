package dev.copilot.agent.orchestrator;

import dev.copilot.core.diagnosis.Diagnosis;

/** Delegates an investigation to the diagnostics agent. Implemented over HTTP in production. */
public interface DiagnosticsClient {
    Diagnosis diagnose(String incidentId, String alert);
}
