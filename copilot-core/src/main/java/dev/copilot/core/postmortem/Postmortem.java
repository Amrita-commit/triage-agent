package dev.copilot.core.postmortem;

import java.time.Instant;

/**
 * A generated incident postmortem.
 *
 * @param incidentId the incident this documents
 * @param markdown the full postmortem document in Markdown
 * @param path where it was written (e.g. "docs/incidents/inc-1.md"), or null if not persisted
 * @param generatedAt when it was generated
 */
public record Postmortem(String incidentId, String markdown, String path, Instant generatedAt) {}
