package dev.copilot.core.trace;

import java.util.List;
import java.util.Optional;

/** Stores completed agent traces so the approval UI and postmortem agent can read them back. */
public interface TraceStore {

    void save(AgentTrace trace);

    Optional<AgentTrace> findById(String traceId);

    /** All stored traces, most recent first. */
    List<AgentTrace> findAll();
}
