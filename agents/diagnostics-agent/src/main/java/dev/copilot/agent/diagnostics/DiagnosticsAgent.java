package dev.copilot.agent.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.diagnosis.Diagnosis;
import dev.copilot.core.model.ModelRouter;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.LlmCallRecord;
import dev.copilot.core.trace.ToolCallRecord;
import dev.copilot.core.trace.TraceStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-agent diagnostic loop. Given an alert description it plans, calls the read-only telemetry
 * tools, iterates on the evidence, and emits a structured {@link Diagnosis}.
 *
 * <p>Guardrails enforced <em>in code</em> (not just the prompt):
 * <ul>
 *   <li>Hard cap of {@code maxToolCalls} tool executions per investigation (default 15). On reaching
 *       it the model is forced to answer with tools removed, and the result is marked truncated.</li>
 *   <li>Read-only tools only — the agent cannot mutate anything.</li>
 *   <li>Every LLM call is traced with model, tokens in/out, latency and estimated cost; every tool
 *       call is traced with its arguments and a result preview.</li>
 *   <li>Model routing: the planning/hypothesis loop uses the planning model; bulky log output is
 *       condensed with the cheap model before being fed back.</li>
 * </ul>
 */
public class DiagnosticsAgent {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsAgent.class);

    static final String SYSTEM_PROMPT =
            """
            You are an SRE diagnostics agent. You investigate a single incident using read-only
            telemetry tools and produce a root-cause diagnosis.

            Rules:
            - Use the tools (query_metrics, query_logs, list_alerts) to gather EVIDENCE before concluding.
            - Every hypothesis MUST be backed by concrete observations you actually retrieved from a
              tool. Never invent metric values or log lines.
            - Be economical: prefer the few most informative queries. You have a limited tool budget.
            - When you are confident (or the budget is exhausted), STOP calling tools and reply with
              ONLY a JSON object (no prose, no markdown fences) of exactly this shape:
              {
                "incidentSummary": string,
                "hypothesis": string,
                "evidence": [ {"tool": string, "query": string, "observation": string, "interpretation": string} ],
                "confidence": number,   // 0.0 - 1.0
                "suggestedNextSteps": [ string ]
              }
            """;

    private final ChatModelProvider models;
    private final ModelRouter router;
    private final DiagnosticTools tools;
    private final TraceStore traceStore;
    private final TokenCostEstimator costEstimator;
    private final ObjectMapper json;
    private final int maxToolCalls;
    private final int logSummarizeThreshold;

    public DiagnosticsAgent(
            ChatModelProvider models,
            ModelRouter router,
            DiagnosticTools tools,
            TraceStore traceStore,
            TokenCostEstimator costEstimator,
            ObjectMapper json) {
        this(models, router, tools, traceStore, costEstimator, json, 15, 4000);
    }

    public DiagnosticsAgent(
            ChatModelProvider models,
            ModelRouter router,
            DiagnosticTools tools,
            TraceStore traceStore,
            TokenCostEstimator costEstimator,
            ObjectMapper json,
            int maxToolCalls,
            int logSummarizeThreshold) {
        this.models = models;
        this.router = router;
        this.tools = tools;
        this.traceStore = traceStore;
        this.costEstimator = costEstimator;
        this.json = json;
        this.maxToolCalls = maxToolCalls;
        this.logSummarizeThreshold = logSummarizeThreshold;
    }

    /** Result of one investigation: the diagnosis plus the id of its persisted trace. */
    public record DiagnosisResult(Diagnosis diagnosis, String traceId) {}

    public DiagnosisResult diagnose(String alertDescription) {
        return diagnose(UUID.randomUUID().toString(), alertDescription);
    }

    public DiagnosisResult diagnose(String incidentId, String alertDescription) {
        AgentTrace trace = new AgentTrace(incidentId, "diagnostics-agent", alertDescription);

        String planningModelId = router.modelFor(TaskType.PLANNING);
        ChatModel planningModel = models.forModel(planningModelId);
        var toolSpecs = tools.specifications();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));
        messages.add(UserMessage.from("Incident: " + alertDescription));

        int toolCallsUsed = 0;

        while (true) {
            boolean toolsAllowed = toolCallsUsed < maxToolCalls;
            ChatRequest.Builder reqBuilder = ChatRequest.builder().messages(messages);
            if (toolsAllowed) {
                reqBuilder.toolSpecifications(toolSpecs);
            }
            ChatResponse response = callModel(planningModel, planningModelId, TaskType.PLANNING, reqBuilder.build(), trace);
            AiMessage ai = response.aiMessage();
            messages.add(ai);

            if (toolsAllowed && ai.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                    if (toolCallsUsed >= maxToolCalls) {
                        messages.add(ToolExecutionResultMessage.from(
                                req, "Tool budget exhausted — provide your diagnosis now."));
                        continue;
                    }
                    toolCallsUsed++;
                    String result = runTool(req, trace);
                    messages.add(ToolExecutionResultMessage.from(req, result));
                }
                continue;
            }

            // No (more) tool calls: this is the final answer.
            boolean truncated = !toolsAllowed;
            Diagnosis diagnosis = parseDiagnosis(ai.text(), truncated);
            traceStore.save(trace);
            log.info("Diagnosis for '{}': confidence={}, toolCalls={}, tokens={}, cost=${}",
                    incidentId, diagnosis.confidence(), trace.toolCallCount(), trace.totalTokens(),
                    String.format("%.4f", trace.totalCostUsd()));
            return new DiagnosisResult(diagnosis, trace.traceId());
        }
    }

    private ChatResponse callModel(
            ChatModel model, String modelId, TaskType taskType, ChatRequest request, AgentTrace trace) {
        long t0 = System.nanoTime();
        ChatResponse response = model.chat(request);
        long latencyMs = (System.nanoTime() - t0) / 1_000_000;
        TokenUsage usage = response.tokenUsage();
        int in = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        int out = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        trace.addLlmCall(new LlmCallRecord(
                Instant.now(), taskType, modelId, in, out, latencyMs, costEstimator.estimateUsd(modelId, in, out)));
        return response;
    }

    private String runTool(ToolExecutionRequest req, AgentTrace trace) {
        long t0 = System.nanoTime();
        boolean error = false;
        String result;
        try {
            result = tools.execute(req);
            result = summarizeIfLarge(req.name(), result, trace);
        } catch (Exception e) {
            error = true;
            result = "Tool error: " + e.getMessage();
            log.warn("Tool '{}' failed: {}", req.name(), e.toString());
        }
        long latencyMs = (System.nanoTime() - t0) / 1_000_000;
        trace.addToolCall(new ToolCallRecord(
                Instant.now(), req.name(), req.arguments(), preview(result), latencyMs, error));
        return result;
    }

    /** Condenses bulky log output with the cheap model, demonstrating model routing. */
    private String summarizeIfLarge(String toolName, String result, AgentTrace trace) {
        if (!DiagnosticTools.QUERY_LOGS.equals(toolName) || result.length() <= logSummarizeThreshold) {
            return result;
        }
        String cheapModelId = router.modelFor(TaskType.LOG_SUMMARIZATION);
        ChatModel cheap = models.forModel(cheapModelId);
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from("Summarise these logs for an SRE. Keep exact error messages, "
                                + "counts, and timestamps that indicate a fault. Be terse."),
                        UserMessage.from(result)))
                .build();
        ChatResponse resp = callModel(cheap, cheapModelId, TaskType.LOG_SUMMARIZATION, req, trace);
        return "[log summary — condensed from " + result.length() + " chars]\n" + resp.aiMessage().text();
    }

    private Diagnosis parseDiagnosis(String text, boolean truncated) {
        String jsonText = extractJsonObject(text);
        try {
            ParsedDiagnosis p = json.readValue(jsonText, ParsedDiagnosis.class);
            return new Diagnosis(
                    p.incidentSummary, p.hypothesis, p.evidence, p.confidence, p.suggestedNextSteps, truncated);
        } catch (Exception e) {
            log.warn("Could not parse diagnosis JSON: {}", e.toString());
            return new Diagnosis(
                    "Unparseable diagnosis",
                    text == null ? "" : text.strip(),
                    List.of(),
                    0.0,
                    List.of("Agent output was not valid JSON; manual review needed."),
                    truncated);
        }
    }

    /** Extract the outermost JSON object from a possibly fenced/prose-wrapped model reply. */
    private static String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : "{}";
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }

    /** Jackson binding target for the model's diagnosis JSON (uses core records for the fields). */
    private record ParsedDiagnosis(
            String incidentSummary,
            String hypothesis,
            List<dev.copilot.core.diagnosis.Evidence> evidence,
            double confidence,
            List<String> suggestedNextSteps) {}
}
