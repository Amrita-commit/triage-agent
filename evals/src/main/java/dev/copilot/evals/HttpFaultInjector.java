package dev.copilot.evals;

import org.springframework.web.client.RestClient;

/** {@link FaultInjector} that drives the demo app's {@code /faults/*} and business endpoints over HTTP. */
public class HttpFaultInjector implements FaultInjector {

    private final RestClient http;

    public HttpFaultInjector(String demoAppUrl) {
        this.http = RestClient.builder().baseUrl(demoAppUrl).build();
    }

    @Override
    public void reset() {
        http.post().uri("/faults/reset").retrieve().toBodilessEntity();
    }

    @Override
    public void inject(Scenario scenario) {
        if (scenario.endpoint() == null) {
            return;
        }
        http.post().uri(b -> {
            b.path(scenario.endpoint());
            if (scenario.params() != null) {
                scenario.params().forEach(b::queryParam);
            }
            return b.build();
        }).retrieve().toBodilessEntity();
    }

    @Override
    public void drive(Scenario scenario) {
        Scenario.Drive d = scenario.drive();
        if (d == null || d.path() == null) {
            return;
        }
        boolean get = "GET".equalsIgnoreCase(d.methodOrDefault());
        for (int i = 0; i < Math.max(0, d.count()); i++) {
            try {
                if (get) {
                    http.get().uri(d.path()).retrieve().toBodilessEntity();
                } else {
                    http.post().uri(d.path()).retrieve().toBodilessEntity();
                }
            } catch (RuntimeException ignored) {
                // faults can make requests fail (e.g. injected 5xx) — that's expected
            }
        }
    }
}
