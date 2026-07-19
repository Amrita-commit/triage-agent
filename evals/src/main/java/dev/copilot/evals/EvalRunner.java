package dev.copilot.evals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the full eval suite: for each scenario, reset the environment, inject the fault, drive
 * traffic, run the agent pipeline, and score the result — then write a Markdown scorecard.
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final FaultInjector injector;
    private final PipelineClient pipeline;
    private final Scorer scorer;
    private final ScorecardWriter writer;

    public EvalRunner(FaultInjector injector, PipelineClient pipeline, Scorer scorer, ScorecardWriter writer) {
        this.injector = injector;
        this.pipeline = pipeline;
        this.scorer = scorer;
        this.writer = writer;
    }

    /** Runs all scenarios, writes the scorecard to {@code resultsDir}, and returns the scores. */
    public List<ScenarioScore> run(List<Scenario> scenarios, Path resultsDir) {
        List<ScenarioScore> scores = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            log.info("Running scenario {} ({})", scenario.id(), scenario.faultType());
            try {
                injector.reset();
                injector.inject(scenario);
                injector.drive(scenario);
            } catch (RuntimeException e) {
                log.warn("Fault setup failed for {}: {}", scenario.id(), e.toString());
            }
            PipelineResult result;
            try {
                result = pipeline.run(scenario.id(), scenario.alert());
            } catch (RuntimeException e) {
                result = PipelineResult.failed(0, e.getMessage());
            }
            ScenarioScore score = scorer.score(scenario, result);
            scores.add(score);
            String status = score.pass() ? "PASS" : (score.error() ? "ERROR" : "FAIL");
            log.info("  -> {} (root-cause {}%, {} tool calls, ${})",
                    status, String.format("%.0f", score.rootCauseMatchRatio() * 100),
                    score.toolCalls(), String.format("%.4f", score.costUsd()));
        }
        try {
            injector.reset();
        } catch (RuntimeException ignored) {
            // best effort cleanup
        }
        writer.write(scores, resultsDir);
        return scores;
    }
}
