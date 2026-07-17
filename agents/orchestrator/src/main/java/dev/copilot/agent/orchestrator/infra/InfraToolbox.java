package dev.copilot.agent.orchestrator.infra;

/**
 * Read-only infra tools the infra/drift agent calls, backed by the {@code terraform-mcp} server.
 * Implementations return the server's JSON-text tool results.
 */
public interface InfraToolbox {

    /** {@code read_state} — the Terraform-declared desired state, as JSON text. */
    String readState();

    /** {@code plan_diff} — structured drift (a serialized {@code DriftReport}), as JSON text. */
    String planDiff();
}
