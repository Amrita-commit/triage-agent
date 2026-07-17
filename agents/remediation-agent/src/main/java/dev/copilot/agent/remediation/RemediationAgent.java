package dev.copilot.agent.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.remediation.RiskLevel;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.LlmCallRecord;
import dev.copilot.core.trace.TraceStore;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes a diagnosis (or drift report) plus the current content of a config/IaC file and proposes
 * a minimal fix as a concrete new file version, with a computed unified diff and rollback notes.
 *
 * <p><strong>It never applies anything.</strong> It only produces a {@link ProposedRemediation}; the
 * change reaches infrastructure only after human approval turns it into a branch/PR. This is enforced
 * structurally — this class has no code path that writes to or mutates live infrastructure.
 */
public class RemediationAgent {

    private static final Logger log = LoggerFactory.getLogger(RemediationAgent.class);

    private static final String SYSTEM_PROMPT =
            """
            You are a remediation agent. Given a diagnosed problem and the current content of a
            configuration or infrastructure-as-code file, propose the MINIMAL change that fixes it.

            Rules:
            - Output ONLY the full proposed new content of the file — do not apply anything.
            - Prefer the smallest change that addresses the root cause; preserve everything else.
            - Provide clear rollback notes.
            Reply with ONLY a JSON object (no prose, no markdown fences) of exactly this shape:
            {
              "title": string,
              "rationale": string,        // why this fixes the diagnosed problem
              "newContent": string,       // the FULL proposed new file content
              "rollbackNotes": string,
              "risk": "LOW" | "MEDIUM" | "HIGH"
            }
            """;

    private final ChatModel model;
    private final String modelId;
    private final TokenCostEstimator costEstimator;
    private final TraceStore traceStore;
    private final ObjectMapper json;

    public RemediationAgent(
            ChatModel model,
            String modelId,
            TokenCostEstimator costEstimator,
            TraceStore traceStore,
            ObjectMapper json) {
        this.model = model;
        this.modelId = modelId;
        this.costEstimator = costEstimator;
        this.traceStore = traceStore;
        this.json = json;
    }

    public ProposedRemediation propose(
            String incidentId, String problem, String targetPath, String currentContent) {
        AgentTrace trace = new AgentTrace(incidentId, "remediation-agent", problem);

        String userMessage = "Diagnosed problem:\n" + problem
                + "\n\nTarget file: " + targetPath
                + "\n\nCurrent content of " + targetPath + ":\n" + currentContent;
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userMessage))
                .build();

        long t0 = System.nanoTime();
        ChatResponse response = model.chat(request);
        long latencyMs = (System.nanoTime() - t0) / 1_000_000;
        TokenUsage usage = response.tokenUsage();
        int in = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        int out = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        trace.addLlmCall(new LlmCallRecord(
                Instant.now(), TaskType.PLANNING, modelId, in, out, latencyMs,
                costEstimator.estimateUsd(modelId, in, out)));
        traceStore.save(trace);

        Parsed p = parse(response.aiMessage().text());
        String unifiedDiff = unifiedDiff(targetPath, currentContent, p.newContent);
        return new ProposedRemediation(
                UUID.randomUUID().toString(),
                incidentId,
                p.title == null ? "Proposed remediation" : p.title,
                p.rationale == null ? "" : p.rationale,
                targetPath,
                p.newContent == null ? currentContent : p.newContent,
                unifiedDiff,
                p.rollbackNotes == null ? "" : p.rollbackNotes,
                toRisk(p.risk));
    }

    /** Computes a real unified diff between the current and proposed content for display. */
    static String unifiedDiff(String path, String currentContent, String newContent) {
        List<String> original = Arrays.asList(currentContent.split("\n", -1));
        List<String> revised = Arrays.asList((newContent == null ? currentContent : newContent).split("\n", -1));
        Patch<String> patch = DiffUtils.diff(original, revised);
        if (patch.getDeltas().isEmpty()) {
            return "(no change)";
        }
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff("a/" + path, "b/" + path, original, patch, 3);
        return String.join("\n", diff);
    }

    private Parsed parse(String text) {
        try {
            return json.readValue(extractJson(text), Parsed.class);
        } catch (Exception e) {
            log.warn("Could not parse remediation JSON: {}", e.toString());
            return new Parsed("Unparseable remediation", "Agent output was not valid JSON.", null, "", "HIGH");
        }
    }

    private static RiskLevel toRisk(String s) {
        if (s == null) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RiskLevel.MEDIUM;
        }
    }

    private static String extractJson(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : "{}";
    }

    private record Parsed(
            String title, String rationale, String newContent, String rollbackNotes, String risk) {}
}
