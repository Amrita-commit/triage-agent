package dev.copilot.agent.postmortem;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.diagnosis.Evidence;
import dev.copilot.core.infra.DriftChange;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.postmortem.Postmortem;
import dev.copilot.core.postmortem.TimelineEntry;
import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.LlmCallRecord;
import dev.copilot.core.trace.TraceStore;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates an incident postmortem as Markdown: a deterministic timeline and evidence/remediation
 * sections (grounded in real traces and structured findings), plus LLM-written narrative
 * (executive summary, root cause, prevention). Written to {@code <outputDir>/<incidentId>.md}.
 *
 * <p>The narrative gracefully degrades: if the LLM call fails (e.g. no API credit), the postmortem
 * is still produced from the structured facts, so the timeline/evidence are never lost.
 */
public class PostmortemAgent {

    private static final Logger log = LoggerFactory.getLogger(PostmortemAgent.class);

    private static final String SYSTEM_PROMPT =
            """
            You are an SRE writing an incident postmortem. Using ONLY the structured facts provided
            (diagnosis, evidence, drift, remediation), write a blameless, factual narrative.
            Reply with ONLY a JSON object (no markdown fences) of exactly this shape:
            {
              "executiveSummary": string,       // 2-3 sentences
              "rootCause": string,              // grounded in the evidence
              "preventionItems": [ string ]     // concrete follow-ups
            }
            """;

    private final ChatModel model;
    private final String modelId;
    private final TokenCostEstimator costEstimator;
    private final TraceStore traceStore;
    private final ObjectMapper json;
    private final String outputDir;

    public PostmortemAgent(
            ChatModel model,
            String modelId,
            TokenCostEstimator costEstimator,
            TraceStore traceStore,
            ObjectMapper json,
            String outputDir) {
        this.model = model;
        this.modelId = modelId;
        this.costEstimator = costEstimator;
        this.traceStore = traceStore;
        this.json = json;
        this.outputDir = outputDir;
    }

    public Postmortem generate(PostmortemInput input) {
        Narrative narrative = writeNarrative(input);
        String markdown = render(input, narrative);
        String path = persist(input.incidentId(), markdown);
        return new Postmortem(input.incidentId(), markdown, path, Instant.now());
    }

    // --- narrative (LLM, with graceful fallback) --------------------------------

    private record Narrative(String executiveSummary, String rootCause, List<String> preventionItems) {}

    private Narrative writeNarrative(PostmortemInput input) {
        AgentTrace trace = new AgentTrace(input.incidentId(), "postmortem-agent", input.alert());
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(facts(input)))
                    .build();
            long t0 = System.nanoTime();
            ChatResponse response = model.chat(request);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000;
            TokenUsage u = response.tokenUsage();
            int in = u != null && u.inputTokenCount() != null ? u.inputTokenCount() : 0;
            int out = u != null && u.outputTokenCount() != null ? u.outputTokenCount() : 0;
            trace.addLlmCall(new LlmCallRecord(Instant.now(), TaskType.PLANNING, modelId, in, out, latencyMs,
                    costEstimator.estimateUsd(modelId, in, out)));
            traceStore.save(trace);

            Narrative n = json.readValue(extractJson(response.aiMessage().text()), Narrative.class);
            if (n.executiveSummary() == null) {
                return fallbackNarrative(input);
            }
            return n;
        } catch (Exception e) {
            log.warn("Narrative generation failed ({}); using deterministic fallback", e.toString());
            traceStore.save(trace);
            return fallbackNarrative(input);
        }
    }

    private Narrative fallbackNarrative(PostmortemInput input) {
        String hypothesis = input.diagnosis() != null ? input.diagnosis().hypothesis() : "See findings below.";
        String drift = input.driftReport() != null && input.driftReport().driftDetected()
                ? " Infrastructure drift was also detected." : "";
        return new Narrative(
                "Incident " + input.incidentId() + ": " + input.title() + "." + drift,
                hypothesis,
                List.of("Add/adjust alerting for this failure mode.",
                        "Review guardrails so a fix can be proposed and approved faster."));
    }

    private String facts(PostmortemInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Incident: ").append(input.title()).append(" (").append(input.incidentId()).append(")\n");
        sb.append("Alert: ").append(input.alert()).append("\n");
        if (input.diagnosis() != null) {
            sb.append("Diagnosis hypothesis: ").append(input.diagnosis().hypothesis()).append("\n");
            input.diagnosis().evidence().forEach(e -> sb.append("Evidence: ")
                    .append(e.observation()).append(" (").append(e.interpretation()).append(")\n"));
        }
        if (input.driftReport() != null && input.driftReport().driftDetected()) {
            sb.append("Drift: ").append(input.driftReport().summary()).append("\n");
        }
        if (input.remediation() != null) {
            sb.append("Proposed remediation: ").append(input.remediation().title()).append("\n");
        }
        return sb.toString();
    }

    // --- markdown rendering (deterministic) -------------------------------------

    private String render(PostmortemInput input, Narrative n) {
        StringBuilder md = new StringBuilder();
        md.append("# Postmortem: ").append(input.title()).append("\n\n");
        md.append("- **Incident:** `").append(input.incidentId()).append("`\n");
        md.append("- **Generated:** ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n");
        md.append("- **Alert:** ").append(input.alert()).append("\n\n");

        md.append("## Summary\n\n").append(n.executiveSummary()).append("\n\n");

        md.append("## Timeline\n\n");
        if (input.timeline().isEmpty()) {
            md.append("_No trace steps recorded._\n\n");
        } else {
            md.append("| Time | Agent | Step | Detail | Trace |\n|---|---|---|---|---|\n");
            for (TimelineEntry e : input.timeline()) {
                md.append("| ").append(e.at()).append(" | ").append(e.source()).append(" | ")
                        .append(e.event()).append(" | ").append(escapeCell(e.detail()))
                        .append(" | `").append(shortId(e.traceId())).append("` |\n");
            }
            md.append("\n");
        }

        md.append("## Root cause\n\n").append(n.rootCause()).append("\n\n");

        if (input.diagnosis() != null && !input.diagnosis().evidence().isEmpty()) {
            md.append("## Evidence\n\n");
            for (Evidence e : input.diagnosis().evidence()) {
                md.append("- **").append(e.tool()).append("** `").append(escapeCell(e.query())).append("` → ")
                        .append(escapeCell(e.observation())).append(" — ").append(e.interpretation()).append("\n");
            }
            md.append("\n");
        }

        DriftReport drift = input.driftReport();
        if (drift != null && drift.driftDetected()) {
            md.append("## Infrastructure drift\n\n").append(drift.summary()).append("\n\n");
            for (DriftChange c : drift.changes()) {
                md.append("- `").append(c.resource()).append("` — `").append(c.attribute()).append("`: ")
                        .append(escapeCell(c.expected())).append(" → ").append(escapeCell(c.actual())).append("\n");
            }
            md.append("\n");
        }

        ProposedRemediation r = input.remediation();
        if (r != null) {
            md.append("## Remediation\n\n**").append(r.title()).append("** (risk: ").append(r.risk())
                    .append(") — proposed, human-approved, applied as a reviewed branch.\n\n");
            md.append("```diff\n").append(r.unifiedDiff()).append("\n```\n\n");
            md.append("**Rollback:** ").append(r.rollbackNotes()).append("\n\n");
        }

        md.append("## Prevention\n\n");
        for (String item : n.preventionItems()) {
            md.append("- [ ] ").append(item).append("\n");
        }
        md.append("\n_Generated by the SRE Copilot postmortem agent. Every timeline row references a recorded trace._\n");
        return md.toString();
    }

    private String persist(String incidentId, String markdown) {
        try {
            Path dir = Path.of(outputDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitize(incidentId) + ".md");
            Files.writeString(file, markdown);
            log.info("Wrote postmortem to {}", file);
            return file.toString();
        } catch (Exception e) {
            log.warn("Could not persist postmortem: {}", e.toString());
            return null;
        }
    }

    private static String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        return s >= 0 && e > s ? text.substring(s, e + 1) : "{}";
    }

    private static String escapeCell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    private static String sanitize(String s) {
        return s == null ? "incident" : s.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String shortId(String id) {
        return id == null ? "" : id.substring(0, Math.min(8, id.length()));
    }
}
