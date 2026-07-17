package dev.copilot.approval;

import dev.copilot.core.approval.ApprovalStore;
import dev.copilot.core.approval.InMemoryApprovalStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the approval queue and the git-branch publisher used on approval. */
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
}
