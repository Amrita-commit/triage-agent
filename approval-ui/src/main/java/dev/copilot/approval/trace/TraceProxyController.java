package dev.copilot.approval.trace;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Same-origin proxy so the trace viewer page can load agent traces without CORS. */
@RestController
public class TraceProxyController {

    private final TraceProxyService service;

    public TraceProxyController(TraceProxyService service) {
        this.service = service;
    }

    @GetMapping("/api/trace/sources")
    public List<String> sources() {
        return List.copyOf(service.sources());
    }

    @GetMapping(value = "/api/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> trace(@RequestParam String source, @RequestParam String id) {
        try {
            return ResponseEntity.ok(service.fetch(source, id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body("{\"error\":\"" + e.getMessage() + "\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body("{\"error\":\"trace source unreachable\"}");
        }
    }
}
