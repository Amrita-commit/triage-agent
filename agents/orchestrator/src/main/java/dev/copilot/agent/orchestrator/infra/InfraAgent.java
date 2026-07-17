package dev.copilot.agent.orchestrator.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.ToolCallRecord;
import dev.copilot.core.trace.TraceStore;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The infra/drift agent. It calls the read-only {@code plan_diff} tool on {@code terraform-mcp} and
 * returns a structured {@link DriftReport}. Drift detection is deterministic (Terraform decides what
 * has drifted), so this agent needs no LLM — keeping it cheap and reliable. It is traced like every
 * other agent step.
 */
public class InfraAgent {

    private static final Logger log = LoggerFactory.getLogger(InfraAgent.class);

    private final InfraToolbox toolbox;
    private final ObjectMapper json;
    private final TraceStore traceStore;

    public InfraAgent(InfraToolbox toolbox, ObjectMapper json, TraceStore traceStore) {
        this.toolbox = toolbox;
        this.json = json;
        this.traceStore = traceStore;
    }

    public DriftReport detectDrift(String incidentId, String alert) {
        AgentTrace trace = new AgentTrace(incidentId, "infra-agent", alert);
        long t0 = System.nanoTime();
        String result = toolbox.planDiff();
        long latencyMs = (System.nanoTime() - t0) / 1_000_000;

        DriftReport report;
        boolean error = false;
        try {
            report = json.readValue(result, DriftReport.class);
        } catch (Exception e) {
            error = true;
            report = new DriftReport(false, List.of(),
                    "Drift check could not be completed: " + firstLine(result));
            log.warn("plan_diff result was not a DriftReport: {}", e.toString());
        }
        trace.addToolCall(new ToolCallRecord(
                Instant.now(), "plan_diff", "{}", preview(result), latencyMs, error));
        traceStore.save(trace);
        return report;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }
}
