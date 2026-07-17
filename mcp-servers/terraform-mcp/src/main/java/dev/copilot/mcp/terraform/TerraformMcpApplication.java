package dev.copilot.mcp.terraform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * MCP server exposing read-only Terraform tools ({@code read_state}, {@code plan_diff}) for the
 * infra/drift agent, served over MCP HTTP/SSE.
 */
@SpringBootApplication
@EnableConfigurationProperties(TerraformProperties.class)
public class TerraformMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(TerraformMcpApplication.class, args);
    }
}
