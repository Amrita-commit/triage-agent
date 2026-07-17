package dev.copilot.agent.remediation;

import dev.copilot.core.remediation.ProposedRemediation;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Submits proposed remediations to the approval-ui service over HTTP. If no approval-ui URL is
 * configured, {@link #submit} is a no-op (the proposal is simply returned to the caller).
 */
public class HttpApprovalClient implements ApprovalClient {

    private static final Logger log = LoggerFactory.getLogger(HttpApprovalClient.class);

    private final RestClient http;
    private final boolean enabled;

    public HttpApprovalClient(String approvalUiUrl) {
        this.enabled = approvalUiUrl != null && !approvalUiUrl.isBlank();
        this.http = enabled ? RestClient.builder().baseUrl(approvalUiUrl).build() : null;
    }

    @Override
    public Optional<String> submit(ProposedRemediation remediation) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.post()
                    .uri("/api/approvals")
                    .body(remediation)
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("id");
            return Optional.ofNullable(id).map(Object::toString);
        } catch (RuntimeException e) {
            log.warn("Failed to submit remediation to approval-ui: {}", e.toString());
            return Optional.empty();
        }
    }
}
