package dev.copilot.evals;

import java.util.List;

/**
 * Scores a pipeline result against a scenario's expectations. Root-cause accuracy uses keyword
 * matching against the produced hypothesis (an LLM-as-judge could be layered on top later); the
 * cost/latency/tool-call metrics are recorded as-is.
 */
public class Scorer {

    private final double passThreshold;

    public Scorer() {
        this(0.6);
    }

    public Scorer(double passThreshold) {
        this.passThreshold = passThreshold;
    }

    public ScenarioScore score(Scenario scenario, PipelineResult result) {
        String hypothesis = result.hypothesis() == null ? "" : result.hypothesis().toLowerCase();
        List<String> keywords = scenario.keywordsOrEmpty();
        List<String> matched = keywords.stream()
                .filter(k -> hypothesis.contains(k.toLowerCase()))
                .toList();
        double ratio = keywords.isEmpty()
                ? (result.error() ? 0.0 : 1.0)
                : (double) matched.size() / keywords.size();
        boolean pass = !result.error() && ratio >= passThreshold;

        return new ScenarioScore(
                scenario.id(),
                scenario.faultType(),
                scenario.expectedComponent(),
                ratio,
                matched,
                pass,
                result.toolCalls(),
                result.totalTokens(),
                result.costUsd(),
                result.latencyMs(),
                result.error(),
                result.hypothesis());
    }
}
