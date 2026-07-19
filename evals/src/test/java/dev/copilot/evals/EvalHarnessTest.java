package dev.copilot.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises scoring, scorecard rendering, and the runner end-to-end with fakes (no services needed). */
class EvalHarnessTest {

    private Scenario latencyScenario() {
        return new Scenario("latency-checkout-3000", "latency", "/faults/latency", Map.of("ms", 3000),
                new Scenario.Drive("/checkout", "POST", 3), "P95 latency on checkout > 2s",
                "artificial latency in checkout", "demo-app", List.of("latency", "checkout"));
    }

    @Test
    void scorerPassesOnKeywordMatchAndFailsOtherwise() {
        Scorer scorer = new Scorer();
        Scenario s = latencyScenario();

        ScenarioScore good = scorer.score(s, new PipelineResult(
                "The checkout handler has injected latency causing high P95", 0.9, 2, 300, 0.002, 1200, false, null));
        assertThat(good.pass()).isTrue();
        assertThat(good.rootCauseMatchRatio()).isEqualTo(1.0);
        assertThat(good.matchedKeywords()).contains("latency", "checkout");

        ScenarioScore bad = scorer.score(s, new PipelineResult(
                "The database is overloaded", 0.5, 1, 100, 0.001, 900, false, null));
        assertThat(bad.pass()).isFalse();

        ScenarioScore errored = scorer.score(s, PipelineResult.failed(50, "credit balance too low"));
        assertThat(errored.pass()).isFalse();
        assertThat(errored.error()).isTrue();
    }

    @Test
    void scorecardRendersSummaryAndRows() {
        String md = new ScorecardWriter().render(List.of(
                new ScenarioScore("s1", "latency", "demo-app", 1.0, List.of("latency"), true, 2, 300, 0.002, 1200, false, "h"),
                new ScenarioScore("s2", "error-rate", "demo-app", 0.0, List.of(), false, 1, 120, 0.001, 800, false, "h")));
        assertThat(md).contains("# Eval Scorecard");
        assertThat(md).contains("1/2 passed (50%)");
        assertThat(md).contains("`s1`").contains("✅ PASS");
        assertThat(md).contains("`s2`").contains("❌ FAIL");
    }

    @Test
    void runnerInjectsScoresAndWritesScorecard(@TempDir Path results) {
        List<String> injected = new ArrayList<>();
        FaultInjector injector = new FaultInjector() {
            public void reset() { injected.add("reset"); }
            public void inject(Scenario s) { injected.add("inject:" + s.id()); }
            public void drive(Scenario s) { injected.add("drive:" + s.id()); }
        };
        // Fake pipeline: correct hypothesis for the latency scenario, wrong for the error one.
        PipelineClient pipeline = (id, alert) -> id.startsWith("latency")
                ? new PipelineResult("injected latency in checkout", 0.9, 2, 300, 0.002, 1000, false, null)
                : new PipelineResult("unrelated cause", 0.3, 1, 100, 0.001, 800, false, null);

        List<Scenario> scenarios = List.of(
                latencyScenario(),
                new Scenario("errors-checkout-100", "error-rate", "/faults/error-rate", Map.of("pct", 100),
                        new Scenario.Drive("/checkout", "POST", 3), "checkout 500s", "errors", "demo-app",
                        List.of("error", "checkout")));

        List<ScenarioScore> scores = new EvalRunner(injector, pipeline, new Scorer(), new ScorecardWriter())
                .run(scenarios, results);

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0).pass()).isTrue();   // latency matched
        assertThat(scores.get(1).pass()).isFalse();  // error not matched
        assertThat(injected).contains("inject:latency-checkout-3000", "drive:errors-checkout-100");
        assertThat(Files.exists(results.resolve("scorecard-latest.md"))).isTrue();
    }
}
