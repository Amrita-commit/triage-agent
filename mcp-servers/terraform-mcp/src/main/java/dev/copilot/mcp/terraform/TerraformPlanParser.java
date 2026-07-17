package dev.copilot.mcp.terraform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.DriftChange;
import dev.copilot.core.infra.DriftReport;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the newline-delimited JSON stream produced by {@code terraform plan -json} into a
 * structured {@link DriftReport}.
 *
 * <p>Terraform emits a {@code resource_drift} message for each resource whose real-world state has
 * diverged from the prior state (i.e. it was changed outside Terraform), and a {@code change_summary}
 * with counts. We turn those into per-attribute {@link DriftChange}s.
 */
public final class TerraformPlanParser {

    private final ObjectMapper json;

    public TerraformPlanParser(ObjectMapper json) {
        this.json = json;
    }

    public DriftReport parse(String planJsonLines) {
        List<DriftChange> changes = new ArrayList<>();
        int changedCount = 0;

        for (String line : planJsonLines.split("\\R")) {
            line = line.strip();
            if (line.isEmpty() || !line.startsWith("{")) {
                continue;
            }
            JsonNode node;
            try {
                node = json.readTree(line);
            } catch (Exception e) {
                continue; // ignore non-JSON diagnostic lines
            }
            String type = text(node, "type");
            if ("resource_drift".equals(type)) {
                changes.addAll(driftChanges(node.get("change")));
            } else if ("change_summary".equals(type)) {
                JsonNode c = node.path("changes");
                changedCount = c.path("change").asInt(0) + c.path("add").asInt(0) + c.path("remove").asInt(0);
            }
        }

        boolean drift = !changes.isEmpty() || changedCount > 0;
        String summary = drift
                ? "Drift detected: " + changes.size() + " attribute change(s) across "
                        + distinctResources(changes) + " resource(s)."
                : "No drift: live infrastructure matches the Terraform-declared state.";
        return new DriftReport(drift, changes, summary);
    }

    private List<DriftChange> driftChanges(JsonNode change) {
        List<DriftChange> result = new ArrayList<>();
        if (change == null) {
            return result;
        }
        String addr = text(change.path("resource"), "addr");
        String action = actionOf(change.get("actions"), text(change, "action"));
        JsonNode before = change.get("before");
        JsonNode after = change.get("after");

        Set<String> keys = new LinkedHashSet<>();
        collectFieldNames(before, keys);
        collectFieldNames(after, keys);

        boolean anyAttribute = false;
        for (String key : keys) {
            String b = valueText(before, key);
            String a = valueText(after, key);
            if (!java.util.Objects.equals(b, a)) {
                result.add(new DriftChange(addr, key, b, a, action));
                anyAttribute = true;
            }
        }
        if (!anyAttribute) {
            // Drift detected but no attribute-level detail available.
            result.add(new DriftChange(addr, "(resource)", "", "", action));
        }
        return result;
    }

    private static String actionOf(JsonNode actionsArray, String singleAction) {
        if (actionsArray != null && actionsArray.isArray() && !actionsArray.isEmpty()) {
            List<String> acts = new ArrayList<>();
            actionsArray.forEach(a -> acts.add(a.asText()));
            return String.join(",", acts);
        }
        return singleAction == null ? "update" : singleAction;
    }

    private static void collectFieldNames(JsonNode obj, Set<String> into) {
        if (obj != null && obj.isObject()) {
            for (Iterator<String> it = obj.fieldNames(); it.hasNext(); ) {
                into.add(it.next());
            }
        }
    }

    private static String valueText(JsonNode obj, String key) {
        if (obj == null || obj.get(key) == null || obj.get(key).isNull()) {
            return "";
        }
        JsonNode v = obj.get(key);
        return v.isValueNode() ? v.asText() : v.toString();
    }

    private static long distinctResources(List<DriftChange> changes) {
        return changes.stream().map(DriftChange::resource).distinct().count();
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
