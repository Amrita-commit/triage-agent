package dev.copilot.core.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory {@link TraceStore}. Sufficient for the local-first design; swap for a DB later. */
public class InMemoryTraceStore implements TraceStore {

    private final ConcurrentMap<String, AgentTrace> traces = new ConcurrentHashMap<>();

    @Override
    public void save(AgentTrace trace) {
        traces.put(trace.traceId(), trace);
    }

    @Override
    public Optional<AgentTrace> findById(String traceId) {
        return Optional.ofNullable(traces.get(traceId));
    }

    @Override
    public List<AgentTrace> findAll() {
        List<AgentTrace> all = new ArrayList<>(traces.values());
        all.sort(Comparator.comparing(AgentTrace::startedAt).reversed());
        return all;
    }
}
