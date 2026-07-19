package dev.copilot.approval.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.web.client.RestClient;

/**
 * Fetches an agent trace from one of the whitelisted trace sources, server-side, so the browser can
 * view traces from the approval UI's own origin (no CORS). Read-only.
 */
public class TraceProxyService {

    private final Map<String, RestClient> clients = new LinkedHashMap<>();

    public TraceProxyService(Map<String, String> sources) {
        sources.forEach((name, url) -> clients.put(name, RestClient.builder().baseUrl(url).build()));
    }

    public Set<String> sources() {
        return clients.keySet();
    }

    /** Returns the raw trace JSON from {@code <source>/traces/<id>}. */
    public String fetch(String source, String traceId) {
        RestClient client = clients.get(source);
        if (client == null) {
            throw new NoSuchElementException("Unknown trace source: " + source);
        }
        return client.get().uri("/traces/{id}", traceId).retrieve().body(String.class);
    }
}
