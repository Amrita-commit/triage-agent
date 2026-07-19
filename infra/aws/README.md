# AWS deployment (deferred — mapping only, not implemented)

This project is **local-first**. Phase 8 is intentionally **not built** — this document only records
how the same code maps onto AWS, to show that "local now, cloud later" is a configuration change
rather than a rewrite. Nothing here provisions real AWS resources.

## Why it's a config change, not a rewrite

The seams are the provider abstractions in `copilot-core`:

- `TelemetryProvider` — implemented locally by `PrometheusLokiTelemetryProvider`.
- `InfraStateProvider` — implemented locally by `TerraformCliInfraStateProvider`.

The agents and MCP servers depend on the **interfaces**, never on Prometheus/Loki/Docker directly.
Swapping the implementations (and the Terraform provider block) is the whole job.

## Component mapping

| Local (now) | AWS (later) |
|---|---|
| Prometheus (metrics) | **CloudWatch Metrics** |
| Loki (logs) | **CloudWatch Logs** |
| Grafana | CloudWatch dashboards (or managed Grafana) |
| `PrometheusLokiTelemetryProvider` | **`CloudWatchTelemetryProvider`** (new impl of `TelemetryProvider`) |
| Docker containers (demo app, agents) | **ECS Fargate** services (or EKS) |
| Terraform `kreuzwerker/docker` provider | Terraform `aws` provider (ECS/ALB/CloudWatch) |
| `TerraformCliInfraStateProvider` | same class, pointed at the AWS-targeted config |
| docker-compose networking | ECS service discovery / ALB + security groups |
| `.env` secrets | AWS Secrets Manager / SSM Parameter Store |
| approval-ui git branch (JGit) | same, plus optional GitHub/CodeCommit PR via API |

## What a real implementation would add

1. **`CloudWatchTelemetryProvider`** in a new module (e.g. `mcp-servers/telemetry-mcp` gains an
   AWS profile): implement `queryMetrics` via the CloudWatch `GetMetricData` API, `queryLogs` via
   CloudWatch Logs Insights, and `listActiveAlerts` via CloudWatch Alarms. Selected by a Spring
   profile / config property — no agent code changes.
2. **Terraform `aws` config** under this directory mirroring `infra/local/` (ECS task defs, ALB,
   CloudWatch log groups/alarms). `terraform-mcp`'s `read_state`/`plan_diff` work unchanged.
3. **Container images** pushed to ECR; the same Dockerfiles build them.
4. **IAM**: least-privilege task roles — read-only CloudWatch/ECS for the diagnostics & infra
   agents (preserving the read-only guardrail), and no permission to mutate infrastructure.
5. **Secrets** via Secrets Manager injected as env vars (the app already reads config from env).

## Explicitly out of scope

Per [`../../plan.md`](../../plan.md): real AWS resources are not created here. This is a mapping to
demonstrate the abstraction, nothing more.
