package dev.copilot.evals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Loads fault scenarios from YAML files in a directory. Each file holds a YAML list of scenarios. */
public class ScenarioLoader {

    private final ObjectMapper yaml = new YAMLMapper();

    public List<Scenario> loadDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a scenarios directory: " + dir);
        }
        List<Scenario> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .sorted()
                    .forEach(p -> all.addAll(loadFile(p)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list scenarios in " + dir + ": " + e.getMessage(), e);
        }
        all.sort(Comparator.comparing(Scenario::id));
        return all;
    }

    public List<Scenario> loadFile(Path file) {
        try {
            return List.of(yaml.readValue(Files.readString(file), Scenario[].class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse scenarios file " + file + ": " + e.getMessage(), e);
        }
    }
}
