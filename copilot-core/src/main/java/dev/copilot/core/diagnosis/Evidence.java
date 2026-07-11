package dev.copilot.core.diagnosis;

/**
 * A single piece of evidence backing a hypothesis. Every hypothesis in a {@link Diagnosis} must be
 * supported by concrete evidence — an actual tool call and its real result — never the model's
 * unsupported assertion.
 *
 * @param tool the tool that produced the evidence (e.g. "query_metrics")
 * @param query the exact query/argument used (e.g. the PromQL expression)
 * @param observation the concrete result observed (a real value/log line from the tool)
 * @param interpretation what this observation implies for the incident
 */
public record Evidence(String tool, String query, String observation, String interpretation) {}
