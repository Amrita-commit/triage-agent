package dev.copilot.evals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renders scenario scores into a Markdown scorecard and writes it to the results directory. */
public class ScorecardWriter {

    private static final Logger log = LoggerFactory.getLogger(ScorecardWriter.class);

    /** Writes {@code scorecard-latest.md} (and a timestamped copy). Returns the latest path. */
    public Path write(List<ScenarioScore> scores, Path resultsDir) {
        String markdown = render(scores);
        try {
            Files.createDirectories(resultsDir);
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
            Files.writeString(resultsDir.resolve("scorecard-" + stamp + ".md"), markdown);
            Path latest = resultsDir.resolve("scorecard-latest.md");
            Files.writeString(latest, markdown);
            log.info("Wrote scorecard to {}", latest);
            return latest;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write scorecard: " + e.getMessage(), e);
        }
    }

    public String render(List<ScenarioScore> scores) {
        int total = scores.size();
        long passed = scores.stream().filter(ScenarioScore::pass).count();
        long errored = scores.stream().filter(ScenarioScore::error).count();
        int totalTokens = scores.stream().mapToInt(ScenarioScore::totalTokens).sum();
        double totalCost = scores.stream().mapToDouble(ScenarioScore::costUsd).sum();
        double avgLatency = scores.stream().mapToLong(ScenarioScore::latencyMs).average().orElse(0);
        double passRate = total == 0 ? 0 : 100.0 * passed / total;

        StringBuilder md = new StringBuilder();
        md.append("# Eval Scorecard\n\n");
        md.append("- **Generated:** ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n");
        md.append(String.format("- **Root-cause accuracy:** %d/%d passed (%.0f%%)%n", passed, total, passRate));
        md.append("- **Errors:** ").append(errored).append("\n");
        md.append(String.format("- **Total tokens:** %,d · **Total cost:** $%.4f · **Avg time:** %.0f ms%n%n",
                totalTokens, totalCost, avgLatency));

        md.append("| Scenario | Fault | Root cause | Tool calls | Tokens | Cost (USD) | Time (ms) | Result |\n");
        md.append("|---|---|---:|---:|---:|---:|---:|:--:|\n");
        for (ScenarioScore s : scores) {
            String result = s.error() ? "⚠️ ERROR" : (s.pass() ? "✅ PASS" : "❌ FAIL");
            md.append(String.format("| `%s` | %s | %.0f%% | %d | %d | %.4f | %d | %s |%n",
                    s.scenarioId(), s.faultType(), s.rootCauseMatchRatio() * 100,
                    s.toolCalls(), s.totalTokens(), s.costUsd(), s.latencyMs(), result));
        }
        md.append("\n_Root-cause accuracy is keyword-match against the agent's hypothesis. "
                + "Errors usually mean the pipeline was unreachable or the LLM API was unavailable._\n");
        return md.toString();
    }
}
