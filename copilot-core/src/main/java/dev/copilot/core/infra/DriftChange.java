package dev.copilot.core.infra;

/**
 * A single detected difference between the infrastructure-as-code desired state and the actual
 * running state.
 *
 * @param resource the resource address (e.g. "docker_container.demo_app")
 * @param attribute the attribute that drifted (e.g. "env")
 * @param expected the value recorded in Terraform state / config
 * @param actual the value observed on the live resource
 * @param action terraform's planned action to reconcile ("update", "replace", "create", "delete")
 */
public record DriftChange(
        String resource, String attribute, String expected, String actual, String action) {}
