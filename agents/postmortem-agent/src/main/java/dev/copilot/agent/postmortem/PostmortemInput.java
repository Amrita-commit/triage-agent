package dev.copilot.agent.postmortem;

import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.postmortem.TimelineEntry;
import dev.copilot.core.remediation.ProposedRemediation;
import java.util.List;

/**
 * Everything the postmortem agent needs about a resolved incident. The caller assembles this from
 * the orchestrated findings and the agents' traces (see {@link TimelineBuilder}).
 */
public record PostmortemInput(
        String incidentId,
        String title,
        String alert,
        Diagnosis diagnosis,
        DriftReport driftReport,
        ProposedRemediation remediation,
        List<TimelineEntry> timeline) {

    public PostmortemInput {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
}
