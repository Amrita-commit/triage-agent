package dev.copilot.agent.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Orchestrator service: classifies incidents and delegates to the diagnostics + infra agents. */
@SpringBootApplication
public class OrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
