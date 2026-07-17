package dev.copilot.mcp.terraform;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for invoking Terraform: the working directory that holds the {@code .tf} config and
 * state, and the terraform binary to run.
 */
@ConfigurationProperties(prefix = "terraform")
public record TerraformProperties(String workingDir, String binary, long timeoutSeconds) {

    public TerraformProperties {
        if (workingDir == null || workingDir.isBlank()) {
            workingDir = "/infra";
        }
        if (binary == null || binary.isBlank()) {
            binary = "terraform";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 120;
        }
    }
}
