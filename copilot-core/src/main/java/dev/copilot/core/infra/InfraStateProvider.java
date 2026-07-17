package dev.copilot.core.infra;

/**
 * Provider-agnostic view of infrastructure-as-code state and drift. The local implementation is
 * backed by Terraform (Docker provider); the same interface could later back onto Terraform Cloud
 * or an AWS-targeted configuration. All operations are <strong>read-only</strong> — the infra agent
 * observes and reports drift but never applies changes.
 */
public interface InfraStateProvider {

    /**
     * Returns the recorded desired state (what IaC declares/tracks), as JSON text. Backed by
     * {@code terraform show -json}.
     */
    String readState();

    /**
     * Detects drift between the declared state and the live infrastructure and returns it in
     * structured form. Backed by a refresh-only {@code terraform plan}.
     */
    DriftReport planDiff();
}
