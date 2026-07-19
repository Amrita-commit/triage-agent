package dev.copilot.approval;

import dev.copilot.approval.trace.TraceSourcesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Approval UI service: review proposed remediations and approve (→ branch/PR) or reject. */
@SpringBootApplication
@EnableConfigurationProperties(TraceSourcesProperties.class)
public class ApprovalUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalUiApplication.class, args);
    }
}
