# Failure analysis

An honest account of where this system breaks, is weak, or is deliberately simplified. Written to be
useful, not flattering.

## Diagnosis quality

- **Keyword-match scoring is coarse.** The eval scorer marks a diagnosis "correct" if the hypothesis
  contains the expected keywords. A hypothesis can contain "latency" for the *wrong* reason and still
  pass; conversely, a correct hypothesis phrased differently can fail. It is a cheap proxy for
  accuracy — an LLM-as-judge (planned hook in `Scorer`) would be more faithful but costs tokens.
- **LLM hallucination is possible.** The evidence requirement (`isEvidenceBacked`) and the "never
  invent values" prompt reduce, but do not eliminate, unsupported hypotheses. The agent can still
  misinterpret real evidence.
- **Scrape-timing races.** The runner injects a fault, drives traffic, then queries — but Prometheus
  scrapes every 5s. If the agent queries before the fault registers, it sees stale/empty metrics and
  produces a low-confidence or wrong diagnosis. Mitigated by driving enough traffic and a short
  settle, not eliminated.

## Availability & cost

- **No LLM credits → no diagnosis.** The agent phases require Anthropic API credits. Without them,
  `/diagnose` and `/incident` return an upstream error (handled cleanly, but no result). The
  non-LLM paths (approval gate, drift detection, telemetry queries, scorecard rendering) still work.
- **Model choice drives cost and quality.** `ModelRouter` sends planning to Sonnet and cheap steps to
  Haiku; costs in the trace are estimates from approximate list prices, not billed amounts.

## State & scale

- **In-memory stores.** Traces and the approval queue live in memory and are lost on restart. Fine for
  local-first; a real deployment needs a database.
- **Single-incident, no cross-service trace aggregation.** The trace viewer fetches one trace from one
  service by id. It works because the diagnostics trace id equals the incident id — a convention, not
  a guarantee. There is no unified, cross-agent trace store yet.
- **Concurrency.** Fault injection is global to the demo app; running scenarios in parallel would
  interfere. The eval runner is intentionally sequential.

## Infrastructure / drift

- **Drift parsing is partial.** `TerraformPlanParser` reads `resource_drift` and the change summary
  from `terraform plan -json`. Deeply nested attribute diffs are flattened to top-level keys; some
  drift shapes may be summarized rather than shown attribute-by-attribute.
- **Requires an initialized workspace.** `plan_diff` needs `terraform init` + `apply` to have run in
  `infra/local`; on a fresh checkout it errors until the demo container is created.
- **The crash fault is excluded from the eval suite** because it hard-kills the demo app and is not
  recoverable by a `/faults/reset` between scenarios.

## Security posture (local demo)

- **No authentication anywhere.** Every service port is open on localhost. This is a local demo, not
  a hardened deployment.
- **The approval publisher writes to a mounted repo.** Approving a remediation creates a branch in
  whatever git repo is mounted at `APPROVAL_REPO_PATH`. Point it at a throwaway/IaC repo, not
  something precious.
- **The trace proxy is whitelisted** (name → URL map) specifically to avoid an open proxy / SSRF; only
  configured sources can be fetched.

## What would harden this for production

Persistent trace/approval stores; auth + RBAC on every service and the approval gate; an
LLM-as-judge eval track; a unified trace store keyed by a real correlation id; ret/rate-limit
handling and budgets on LLM spend; and the `CloudWatchTelemetryProvider` / ECS deployment sketched in
[`infra/aws/README.md`](../infra/aws/README.md).
