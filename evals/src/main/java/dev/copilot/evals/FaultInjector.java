package dev.copilot.evals;

/** Injects/clears faults in the demo app and drives traffic (in production: HTTP to demo-app). */
public interface FaultInjector {

    void reset();

    void inject(Scenario scenario);

    /** Generates the scenario's traffic so the fault becomes visible in telemetry. */
    void drive(Scenario scenario);
}
