package dev.copilot.agent.orchestrator;

import dev.copilot.agent.orchestrator.infra.InfraAgent;
import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.orchestration.IncidentClassification;
import dev.copilot.core.orchestration.OrchestratedFindings;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.TraceStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The top-level orchestrator: classifies an incident, delegates to the diagnostics and/or infra
 * agents accordingly, and merges their findings. Delegation is resilient — if one agent fails
 * (e.g. the diagnostics agent has no API credit), the other's findings are still returned.
 */
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final IncidentClassifier classifier;
    private final DiagnosticsClient diagnosticsClient;
    private final InfraAgent infraAgent;
    private final TraceStore traceStore;

    public OrchestratorAgent(
            IncidentClassifier classifier,
            DiagnosticsClient diagnosticsClient,
            InfraAgent infraAgent,
            TraceStore traceStore) {
        this.classifier = classifier;
        this.diagnosticsClient = diagnosticsClient;
        this.infraAgent = infraAgent;
        this.traceStore = traceStore;
    }

    public OrchestratedFindings handle(String alert) {
        return handle(UUID.randomUUID().toString(), alert);
    }

    public OrchestratedFindings handle(String incidentId, String alert) {
        AgentTrace trace = new AgentTrace(incidentId, "orchestrator", alert);
        IncidentClassification classification = classifier.classify(alert, trace);
        log.info("Incident {} classified as {} (diagnostics={}, infra={})",
                incidentId, classification.category(), classification.needsDiagnostics(), classification.needsInfra());

        StringBuilder notes = new StringBuilder();
        Diagnosis diagnosis = null;
        if (classification.needsDiagnostics()) {
            try {
                diagnosis = diagnosticsClient.diagnose(incidentId, alert);
            } catch (RuntimeException e) {
                notes.append("Diagnostics agent unavailable: ").append(e.getMessage()).append(". ");
                log.warn("Diagnostics delegation failed: {}", e.toString());
            }
        }

        DriftReport driftReport = null;
        if (classification.needsInfra()) {
            try {
                driftReport = infraAgent.detectDrift(incidentId, alert);
            } catch (RuntimeException e) {
                notes.append("Infra agent unavailable: ").append(e.getMessage()).append(". ");
                log.warn("Infra delegation failed: {}", e.toString());
            }
        }

        traceStore.save(trace);
        String summary = buildSummary(classification, diagnosis, driftReport, notes.toString());
        return new OrchestratedFindings(incidentId, classification, diagnosis, driftReport, summary);
    }

    private static String buildSummary(
            IncidentClassification c, Diagnosis d, DriftReport drift, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Classified as ").append(c.category()).append(". ");
        if (d != null) {
            sb.append("Diagnosis: ").append(d.hypothesis())
                    .append(" (confidence ").append(String.format("%.2f", d.confidence())).append("). ");
        }
        if (drift != null) {
            sb.append("Infra: ").append(drift.summary()).append(" ");
        }
        if (d == null && drift == null) {
            sb.append("No sub-agent findings were produced. ");
        }
        if (!notes.isBlank()) {
            sb.append("[").append(notes.trim()).append("]");
        }
        return sb.toString().trim();
    }
}
