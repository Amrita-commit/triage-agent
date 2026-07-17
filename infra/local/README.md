# Local infrastructure (Terraform, Docker provider)

This module declares a Terraform-managed copy of the demo app (`demo-app-managed`). It exists so the
**infra/drift agent** has real infrastructure-as-code to reason about: change the running container
out-of-band and a refresh-only `terraform plan` reports the drift, which `terraform-mcp`'s
`plan_diff` tool surfaces.

The `terraform-mcp` service runs Terraform against this directory inside the container (it's mounted
at `/infra`, with the Docker socket, so the Docker provider can manage containers). You can also run
it from a host that has Terraform + Docker.

## One-time setup

```bash
# build the demo-app image the Terraform config references
docker compose build demo-app

cd infra/local
terraform init
terraform apply        # creates the demo-app-managed container (port 8081)
```

## Demonstrate drift (Phase 3 acceptance)

1. Change the container **outside Terraform** — e.g. recreate it with a different log level:
   ```bash
   docker rm -f demo-app-managed
   docker run -d --name demo-app-managed -e LOG_LEVEL=DEBUG -e MANAGED_BY=terraform \
     -l managed-by=terraform -p 8081:8080 agentic-sre/demo-app:latest
   ```
2. Detect the drift:
   ```bash
   terraform plan -refresh-only        # shows LOG_LEVEL INFO -> DEBUG drift
   ```
   Or via the agent path: `terraform-mcp`'s `plan_diff` returns the same drift as structured JSON,
   and the infra agent explains it in a `DriftReport`.

## Reconcile

`terraform apply` would put it back to the declared state — but in this project **the agent never
applies**. Remediation (Phase 4) turns drift into a *proposed* Terraform diff that a human approves.

## AWS later (Phase 8)

The same module structure is reused against the AWS provider (ECS/CloudWatch) by swapping the
provider block — see [`infra/aws/README.md`](../aws/README.md).
