package dev.copilot.mcp.terraform;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.copilot.core.infra.InfraStateProvider;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read-only MCP tools the infra/drift agent calls: {@code read_state} (the declared desired
 * state) and {@code plan_diff} (structured drift between declared and live state).
 */
public class InfraTools {

    private static final Logger log = LoggerFactory.getLogger(InfraTools.class);

    private final InfraStateProvider provider;
    private final ObjectMapper json;

    public InfraTools(InfraStateProvider provider, ObjectMapper json) {
        this.provider = provider;
        this.json = json;
    }

    public List<SyncToolSpecification> specifications() {
        Tool readState = Tool.builder()
                .name("read_state")
                .description("Read the Terraform-declared infrastructure state (terraform show -json). "
                        + "Shows what the infrastructure-as-code tracks. Read-only.")
                .inputSchema(emptyObjectSchema())
                .build();

        Tool planDiff = Tool.builder()
                .name("plan_diff")
                .description("Detect drift between the declared Terraform state and the live "
                        + "infrastructure (refresh-only terraform plan). Returns structured drift with "
                        + "the resources and attributes that changed. Read-only — never applies changes.")
                .inputSchema(emptyObjectSchema())
                .build();

        return List.of(
                new SyncToolSpecification(readState, handler(args -> provider.readState())),
                new SyncToolSpecification(planDiff, handler(args -> json.writeValueAsString(provider.planDiff()))));
    }

    private BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler(ToolFn fn) {
        return (exchange, request) -> {
            try {
                String result = fn.apply(request.arguments() == null ? Map.of() : request.arguments());
                return CallToolResult.builder().addTextContent(result).build();
            } catch (Exception e) {
                log.warn("Tool '{}' failed: {}", request.name(), e.toString());
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("Tool error: " + e.getMessage())
                        .build();
            }
        };
    }

    @FunctionalInterface
    private interface ToolFn {
        String apply(Map<String, Object> args) throws Exception;
    }

    private static Map<String, Object> emptyObjectSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }
}
