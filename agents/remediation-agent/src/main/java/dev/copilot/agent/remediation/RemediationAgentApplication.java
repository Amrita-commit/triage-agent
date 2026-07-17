package dev.copilot.agent.remediation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Remediation agent service: proposes fixes as reviewable diffs. Never applies. */
@SpringBootApplication
public class RemediationAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(RemediationAgentApplication.class, args);
    }
}
