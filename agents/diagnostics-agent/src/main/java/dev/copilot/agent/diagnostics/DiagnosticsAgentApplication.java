package dev.copilot.agent.diagnostics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Diagnostics agent service. Exposes {@code POST /diagnose} and reads telemetry via telemetry-mcp. */
@SpringBootApplication
public class DiagnosticsAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiagnosticsAgentApplication.class, args);
    }
}
