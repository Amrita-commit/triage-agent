package dev.copilot.evals;

/** Runs the agent pipeline for a scenario (in production: calls the diagnostics agent over HTTP). */
public interface PipelineClient {
    PipelineResult run(String incidentId, String alert);
}
