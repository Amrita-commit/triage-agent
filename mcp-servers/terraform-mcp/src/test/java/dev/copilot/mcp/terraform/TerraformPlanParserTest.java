package dev.copilot.mcp.terraform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.DriftChange;
import dev.copilot.core.infra.DriftReport;
import org.junit.jupiter.api.Test;

/** Verifies drift extraction from {@code terraform plan -json} output without running Terraform. */
class TerraformPlanParserTest {

    private final TerraformPlanParser parser = new TerraformPlanParser(new ObjectMapper());

    @Test
    void detectsContainerEnvDrift() {
        String planJson =
                """
                {"@level":"info","@message":"Terraform 1.9.0","type":"version"}
                {"@level":"info","type":"resource_drift","change":{"resource":{"addr":"docker_container.demo_app","resource_type":"docker_container"},"action":"update","before":{"env":["LOG_LEVEL=INFO"]},"after":{"env":["LOG_LEVEL=DEBUG"]}}}
                {"@level":"info","type":"change_summary","changes":{"add":0,"change":1,"remove":0,"operation":"plan"}}
                """;

        DriftReport report = parser.parse(planJson);

        assertThat(report.driftDetected()).isTrue();
        assertThat(report.changes()).isNotEmpty();
        DriftChange env = report.changes().stream()
                .filter(c -> c.attribute().equals("env"))
                .findFirst()
                .orElseThrow();
        assertThat(env.resource()).isEqualTo("docker_container.demo_app");
        assertThat(env.expected()).contains("INFO");
        assertThat(env.actual()).contains("DEBUG");
        assertThat(env.action()).isEqualTo("update");
        assertThat(report.summary()).contains("Drift detected");
    }

    @Test
    void reportsNoDriftWhenStateMatches() {
        String planJson =
                """
                {"@level":"info","type":"version"}
                {"@level":"info","type":"change_summary","changes":{"add":0,"change":0,"remove":0,"operation":"plan"}}
                """;

        DriftReport report = parser.parse(planJson);

        assertThat(report.driftDetected()).isFalse();
        assertThat(report.changes()).isEmpty();
        assertThat(report.summary()).contains("No drift");
    }
}
