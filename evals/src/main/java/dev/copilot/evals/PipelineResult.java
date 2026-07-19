package dev.copilot.evals;

/**
 * The pipeline's output for one scenario: the diagnosis and the cost/latency metrics scored by the
 * harness.
 */
public record PipelineResult(
        String hypothesis,
        double confidence,
        int toolCalls,
        int totalTokens,
        double costUsd,
        long latencyMs,
        boolean error,
        String errorDetail) {

    public static PipelineResult failed(long latencyMs, String detail) {
        return new PipelineResult("", 0.0, 0, 0, 0.0, latencyMs, true, detail);
    }
}
