package dev.copilot.demo.web;

import dev.copilot.demo.fault.FaultState;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fault-injection control plane. These endpoints deliberately degrade the demo app so the copilot
 * agents have real faults to diagnose. They are separate from the business endpoints and clearly
 * namespaced under {@code /faults}.
 */
@RestController
@RequestMapping("/faults")
public class FaultController {

    private static final Logger log = LoggerFactory.getLogger(FaultController.class);

    private final FaultState faults;

    public FaultController(FaultState faults) {
        this.faults = faults;
    }

    @PostMapping("/latency")
    public Map<String, Object> latency(@RequestParam int ms) {
        int applied = faults.setLatency(ms);
        return Map.of("fault", "latency", "injectedLatencyMs", applied);
    }

    @PostMapping("/error-rate")
    public Map<String, Object> errorRate(@RequestParam int pct) {
        int applied = faults.setErrorRate(pct);
        return Map.of("fault", "error-rate", "errorRatePct", applied);
    }

    @PostMapping("/memory-leak")
    public Map<String, Object> memoryLeak(@RequestParam(defaultValue = "128") int mb) {
        long retained = faults.leakMemory(mb);
        return Map.of("fault", "memory-leak", "retainedBytes", retained);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        faults.reset();
        return Map.of("fault", "reset", "status", "cleared");
    }

    /** Simulates a hard process crash after a short delay so the HTTP response can still be sent. */
    @PostMapping("/crash")
    public Map<String, Object> crash() {
        log.error("FAULT INJECTED: process will crash in 500ms");
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(1);
        });
        return Map.of("fault", "crash", "status", "process exiting");
    }
}
