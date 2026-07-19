package dev.copilot.evals;

import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;

/**
 * Eval harness entrypoint. Runs the whole suite unattended with one command:
 * {@code java -jar evals.jar} (or {@code docker compose run --rm evals}). Requires the demo app and
 * diagnostics agent to be running; writes a Markdown scorecard to the results directory.
 */
@SpringBootApplication
public class EvalsApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalsApplication.class);

    @Value("${demo-app.url:http://localhost:8080}")
    private String demoAppUrl;

    @Value("${diagnostics.url:http://localhost:8100}")
    private String diagnosticsUrl;

    @Value("${evals.scenarios-dir:evals/scenarios}")
    private String scenariosDir;

    @Value("${evals.results-dir:evals/results}")
    private String resultsDir;

    @Value("${evals.pass-threshold:0.6}")
    private double passThreshold;

    public static void main(String[] args) {
        SpringApplication.run(EvalsApplication.class, args);
    }

    @Override
    public void run(String... args) {
        List<Scenario> scenarios = new ScenarioLoader().loadDirectory(Path.of(scenariosDir));
        log.info("Loaded {} scenarios from {}", scenarios.size(), scenariosDir);

        EvalRunner runner = new EvalRunner(
                new HttpFaultInjector(demoAppUrl),
                new HttpPipelineClient(diagnosticsUrl),
                new Scorer(passThreshold),
                new ScorecardWriter());

        List<ScenarioScore> scores = runner.run(scenarios, Path.of(resultsDir));
        long passed = scores.stream().filter(ScenarioScore::pass).count();
        log.info("Eval complete: {}/{} passed. Scorecard in {}", passed, scores.size(), resultsDir);
    }
}
