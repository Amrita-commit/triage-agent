package dev.copilot.mcp.terraform;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.DriftReport;
import dev.copilot.core.infra.InfraStateProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link InfraStateProvider} backed by the Terraform CLI (Docker provider). Read-only: it runs
 * {@code terraform show} and a refresh-only {@code terraform plan}, never {@code apply}.
 */
public class TerraformCliInfraStateProvider implements InfraStateProvider {

    private static final Logger log = LoggerFactory.getLogger(TerraformCliInfraStateProvider.class);

    private final String workingDir;
    private final String binary;
    private final long timeoutSeconds;
    private final TerraformPlanParser parser;

    public TerraformCliInfraStateProvider(
            String workingDir, String binary, long timeoutSeconds, ObjectMapper json) {
        this.workingDir = workingDir;
        this.binary = binary;
        this.timeoutSeconds = timeoutSeconds;
        this.parser = new TerraformPlanParser(json);
    }

    @Override
    public String readState() {
        return run(List.of(binary, "show", "-json", "-no-color"));
    }

    @Override
    public DriftReport planDiff() {
        // -refresh-only surfaces drift between state and the real world without proposing config
        // changes; -json makes the output machine-readable for TerraformPlanParser.
        String out = run(List.of(binary, "plan", "-refresh-only", "-json", "-no-color", "-input=false"));
        return parser.parse(out);
    }

    private String run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(new File(workingDir))
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("terraform command timed out: " + command);
            }
            log.debug("ran {} -> exit {}", command, process.exitValue());
            return output.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run " + command + ": " + e.getMessage(), e);
        }
    }
}
