package dev.copilot.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Approval UI service: review proposed remediations and approve (→ branch/PR) or reject. */
@SpringBootApplication
public class ApprovalUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApprovalUiApplication.class, args);
    }
}
