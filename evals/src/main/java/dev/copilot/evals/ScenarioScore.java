package dev.copilot.evals;

import java.util.List;

/** The scored outcome of one scenario. */
public record ScenarioScore(
        String scenarioId,
        String faultType,
        String expectedComponent,
        double rootCauseMatchRatio,
        List<String> matchedKeywords,
        boolean pass,
        int toolCalls,
        int totalTokens,
        double costUsd,
        long latencyMs,
        boolean error,
        String hypothesis) {}
