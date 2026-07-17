package dev.copilot.agent.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.model.TaskType;
import dev.copilot.core.model.TokenCostEstimator;
import dev.copilot.core.orchestration.IncidentClassification;
import dev.copilot.core.orchestration.IncidentClassification.Category;
import dev.copilot.core.trace.AgentTrace;
import dev.copilot.core.trace.LlmCallRecord;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies an incoming incident with a single planning-model call, deciding which agents to
 * engage. This is the orchestrator's one "planning" LLM use; delegation and merging are mechanical.
 */
public class IncidentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IncidentClassifier.class);

    private static final String SYSTEM_PROMPT =
            """
            You are an incident-triage orchestrator. Classify an incident and decide which
            specialist agents to engage:
              - the DIAGNOSTICS agent investigates application/runtime faults (latency, error rates,
                memory) using metrics and logs.
              - the INFRA agent detects infrastructure/configuration drift (a resource changed away
                from its declared Terraform state).
            Reply with ONLY a JSON object (no prose, no markdown) of exactly this shape:
            {
              "category": "APPLICATION_FAULT" | "INFRASTRUCTURE_DRIFT" | "MIXED" | "UNKNOWN",
              "reasoning": string,
              "needsDiagnostics": boolean,
              "needsInfra": boolean
            }
            If unsure, prefer engaging both agents.
            """;

    private final ChatModel model;
    private final String modelId;
    private final TokenCostEstimator costEstimator;
    private final ObjectMapper json;

    public IncidentClassifier(
            ChatModel model, String modelId, TokenCostEstimator costEstimator, ObjectMapper json) {
        this.model = model;
        this.modelId = modelId;
        this.costEstimator = costEstimator;
        this.json = json;
    }

    public IncidentClassification classify(String alert, AgentTrace trace) {
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from("Incident: " + alert))
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

        return parse(response.aiMessage().text());
    }

    private IncidentClassification parse(String text) {
        try {
            String jsonText = extractJson(text);
            Parsed p = json.readValue(jsonText, Parsed.class);
            Category category = toCategory(p.category);
            return new IncidentClassification(category, p.reasoning, p.needsDiagnostics, p.needsInfra);
        } catch (Exception e) {
            log.warn("Could not parse classification, defaulting to MIXED/both: {}", e.toString());
            // Safe default: engage both agents rather than miss a cause.
            return new IncidentClassification(
                    Category.MIXED, "Classification unparseable; engaging both agents as a precaution.",
                    true, true);
        }
    }

    private static Category toCategory(String s) {
        if (s == null) {
            return Category.UNKNOWN;
        }
        try {
            return Category.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Category.UNKNOWN;
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

    private record Parsed(String category, String reasoning, boolean needsDiagnostics, boolean needsInfra) {}
}
