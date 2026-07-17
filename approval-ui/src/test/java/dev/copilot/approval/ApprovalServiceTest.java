package dev.copilot.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.copilot.core.approval.ApprovalRequest;
import dev.copilot.core.approval.ApprovalStatus;
import dev.copilot.core.approval.InMemoryApprovalStore;
import dev.copilot.core.remediation.ProposedRemediation;
import dev.copilot.core.remediation.RiskLevel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies the approval gate: approval (and only approval) publishes; state transitions are one-way. */
class ApprovalServiceTest {

    private ProposedRemediation remediation(String id) {
        return new ProposedRemediation(
                id, "inc-1", "title", "rationale", "infra/local/main.tf", "new", "diff", "rollback", RiskLevel.LOW);
    }

    @Test
    void approvePublishesAndRecordsBranch() {
        AtomicInteger published = new AtomicInteger();
        RemediationPublisher publisher = r -> {
            published.incrementAndGet();
            return "remediation/inc-1-abc";
        };
        var service = new ApprovalService(new InMemoryApprovalStore(), publisher);

        ApprovalRequest submitted = service.submit(remediation("r1"));
        assertThat(submitted.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(published).hasValue(0); // nothing published on submit

        ApprovalRequest approved = service.approve("r1", "looks good");
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.resultRef()).isEqualTo("remediation/inc-1-abc");
        assertThat(approved.decisionNote()).isEqualTo("looks good");
        assertThat(published).hasValue(1); // published exactly once, on approval
    }

    @Test
    void rejectDoesNotPublish() {
        AtomicInteger published = new AtomicInteger();
        var service = new ApprovalService(new InMemoryApprovalStore(), r -> {
            published.incrementAndGet();
            return "should-not-happen";
        });
        service.submit(remediation("r2"));

        ApprovalRequest rejected = service.reject("r2", "too risky");
        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(published).hasValue(0);
    }

    @Test
    void cannotDecideTwice() {
        var service = new ApprovalService(new InMemoryApprovalStore(), r -> "branch");
        service.submit(remediation("r3"));
        service.approve("r3", "ok");
        assertThatThrownBy(() -> service.approve("r3", "again")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.reject("r3", "no")).isInstanceOf(IllegalStateException.class);
    }
}
