package dev.copilot.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    @Test
    void loadsAllShippedScenarios() {
        // Surefire's working directory is the module basedir, so scenarios/ resolves.
        List<Scenario> scenarios = new ScenarioLoader().loadDirectory(Path.of("scenarios"));

        assertThat(scenarios).hasSizeGreaterThanOrEqualTo(18);
        assertThat(scenarios).allSatisfy(s -> {
            assertThat(s.id()).isNotBlank();
            assertThat(s.alert()).isNotBlank();
            assertThat(s.faultType()).isNotBlank();
            assertThat(s.keywordsOrEmpty()).isNotEmpty();
        });
        // ids are unique
        assertThat(scenarios.stream().map(Scenario::id).distinct().count())
                .isEqualTo(scenarios.size());
        // a known scenario parsed with its params
        Scenario latency = scenarios.stream().filter(s -> s.id().equals("latency-checkout-3000"))
                .findFirst().orElseThrow();
        assertThat(latency.endpoint()).isEqualTo("/faults/latency");
        assertThat(latency.params()).containsEntry("ms", 3000);
        assertThat(latency.drive().count()).isEqualTo(8);
    }
}
