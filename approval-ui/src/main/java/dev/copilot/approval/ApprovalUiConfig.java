package dev.copilot.approval;

import dev.copilot.approval.trace.TraceProxyService;
import dev.copilot.approval.trace.TraceSourcesProperties;
import dev.copilot.core.approval.ApprovalStore;
import dev.copilot.core.approval.InMemoryApprovalStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the approval queue, the git-branch publisher used on approval, and the trace viewer proxy. */
@Configuration
public class ApprovalUiConfig {

    /** Path to the Git repository where approved remediations are committed to a branch. */
    @Value("${approval.repo-path:.}")
    private String repoPath;

    @Bean
    ApprovalStore approvalStore() {
        return new InMemoryApprovalStore();
    }

    @Bean
    RemediationPublisher remediationPublisher() {
        return new GitBranchPublisher(repoPath);
    }

    @Bean
    ApprovalService approvalService(ApprovalStore store, RemediationPublisher publisher) {
        return new ApprovalService(store, publisher);
    }

    @Bean
    TraceProxyService traceProxyService(TraceSourcesProperties props) {
        return new TraceProxyService(props.sources());
    }
}
