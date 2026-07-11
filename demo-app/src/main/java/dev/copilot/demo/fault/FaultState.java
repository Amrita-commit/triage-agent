package dev.copilot.demo.fault;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the currently injected fault configuration for the demo app. Business endpoints consult
 * this component on every request so injected faults become visible in latency/error metrics and
 * logs, which is exactly what the diagnostics agent must reason over.
 *
 * <p>The current fault values are also exported as Prometheus gauges so that an observer (human or
 * agent) can see ground truth for what was injected.
 */
@Component
public class FaultState {

    private static final Logger log = LoggerFactory.getLogger(FaultState.class);

    private final MeterRegistry registry;

    /** Extra latency (ms) added to each business request. */
    private final AtomicInteger injectedLatencyMs = new AtomicInteger(0);

    /** Probability (0-100) that a business request fails with 500. */
    private final AtomicInteger errorRatePct = new AtomicInteger(0);

    /** Intentionally retained references to simulate a memory leak. */
    private final List<byte[]> leaked = new CopyOnWriteArrayList<>();

    public FaultState(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("demo.fault.injected_latency_ms", injectedLatencyMs, AtomicInteger::get)
                .description("Currently injected artificial latency in milliseconds")
                .register(registry);
        Gauge.builder("demo.fault.error_rate_pct", errorRatePct, AtomicInteger::get)
                .description("Currently injected error rate as a percentage")
                .register(registry);
        Gauge.builder("demo.fault.leaked_bytes", leaked, l -> l.size() * 1_048_576.0)
                .description("Approximate bytes retained by the injected memory leak")
                .register(registry);
    }

    /** Applies the currently injected latency by sleeping, then decides whether to fail. */
    public void applyFaults() {
        int latency = injectedLatencyMs.get();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int errPct = errorRatePct.get();
        if (errPct > 0 && ThreadLocalRandom.current().nextInt(100) < errPct) {
            throw new InjectedFaultException("Injected error (rate=" + errPct + "%)");
        }
    }

    public int setLatency(int ms) {
        int clamped = Math.max(0, ms);
        injectedLatencyMs.set(clamped);
        log.warn("FAULT INJECTED: latency set to {}ms", clamped);
        return clamped;
    }

    public int setErrorRate(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        errorRatePct.set(clamped);
        log.warn("FAULT INJECTED: error rate set to {}%", clamped);
        return clamped;
    }

    /** Leaks {@code megabytes} MiB of heap that will never be released until restart. */
    public long leakMemory(int megabytes) {
        for (int i = 0; i < megabytes; i++) {
            leaked.add(new byte[1_048_576]);
        }
        log.warn("FAULT INJECTED: leaked {}MiB (total retained ~{}MiB)", megabytes, leaked.size());
        return (long) leaked.size() * 1_048_576L;
    }

    /** Clears all injected faults (used by the eval harness between scenarios). */
    public void reset() {
        injectedLatencyMs.set(0);
        errorRatePct.set(0);
        leaked.clear();
        log.warn("FAULTS RESET");
    }

    public int injectedLatencyMs() {
        return injectedLatencyMs.get();
    }

    public int errorRatePct() {
        return errorRatePct.get();
    }
}
