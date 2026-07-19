# Architecture

The copilot is a set of small Spring Boot services that cooperate to turn an incident alert into a
diagnosis, a reviewable fix, and a postmortem — all read-only by default, with a human gate before
anything can change.

## Components

| Service | Port | Role |
|---|---|---|
| `demo-app` | 8080 | The "victim" microservice with fault-injection endpoints |
| `telemetry-mcp` | 8090 | MCP server: `query_metrics`, `query_logs`, `list_alerts` (Prometheus/Loki) |
| `terraform-mcp` | 8091 | MCP server: `read_state`, `plan_diff` (Terraform drift, read-only) |
| `diagnostics-agent` | 8100 | Agentic loop → evidence-cited `Diagnosis` |
| `orchestrator` | 8110 | Classifies an incident, delegates, merges findings |
| `remediation-agent` | 8120 | Proposes a fix as a diff (never applies) |
| `approval-ui` | 8130 | Human approval queue + trace viewer; approve → branch/PR |
| `postmortem-agent` | 8140 | Timeline-from-traces Markdown postmortem |
| `evals` | — | On-demand harness: inject faults, score, scorecard |
| Prometheus / Loki / Grafana / Promtail | 9090 / 3100 / 3000 | Local observability stack (stands in for CloudWatch) |

`copilot-core` is a framework-neutral library shared by all services: the domain model
(`Diagnosis`, `DriftReport`, `ProposedRemediation`, …), the provider abstractions
(`TelemetryProvider`, `InfraStateProvider`), model routing (`ModelRouter`), and tracing
(`AgentTrace`, `TraceStore`).

## System diagram

```mermaid
flowchart TB
    alert([alert / user query]) --> orch[orchestrator<br/>classify · delegate · merge]

    orch -->|application fault| diag[diagnostics-agent]
    orch -->|infra drift| infra[infra agent<br/>in orchestrator]
    diag -->|MCP| tmcp[telemetry-mcp]
    infra -->|MCP| tfmcp[terraform-mcp]
    tmcp --> prom[(Prometheus)]
    tmcp --> loki[(Loki)]
    tfmcp --> tf[(Terraform state<br/>Docker provider)]

    diag --> findings[Diagnosis + DriftReport]
    infra --> findings
    findings --> rem[remediation-agent<br/>propose diff]
    rem --> gate{{approval-ui<br/>HUMAN GATE}}
    gate -->|approve| branch[[git branch / PR]]
    gate -->|reject| drop[discarded]
    findings --> pm[postmortem-agent] --> md[[docs/incidents/*.md]]

    classDef gate fill:#7a1c2f,stroke:#ff8098,color:#fff;
    class gate gate;
```

## Request flow (happy path)

```mermaid
sequenceDiagram
    participant U as Alert
    participant O as orchestrator
    participant D as diagnostics-agent
    participant T as telemetry-mcp
    participant R as remediation-agent
    participant A as approval-ui
    U->>O: POST /incident {alert}
    O->>O: classify (Claude, planning)
    O->>D: POST /diagnose {alert}
    loop up to 15 tool calls
        D->>T: query_metrics / query_logs (MCP)
        T-->>D: evidence
    end
    D-->>O: Diagnosis {hypothesis, evidence, confidence}
    O-->>U: OrchestratedFindings
    Note over R,A: later, on a proposed fix
    R->>A: POST /api/approvals {diff}
    A->>A: human approves
    A->>A: create remediation/* branch (JGit)
```

## Model routing

A `ModelRouter` maps task types to models: planning/hypothesis → Sonnet, cheap/high-volume steps
(log summarization) → Haiku. Every LLM call is traced with model, tokens, latency, and an estimated
cost, viewable per incident in the approval UI's trace viewer.

## Provider abstraction (local now, AWS later)

`TelemetryProvider` and `InfraStateProvider` are the seams that make this "local-first now, AWS
later." The Prometheus/Loki and Terraform-Docker implementations live in the MCP servers; a future
`CloudWatchTelemetryProvider` implements the same interface — a configuration change, not a rewrite.
See [`infra/aws/README.md`](../infra/aws/README.md).

See also [guardrails.md](guardrails.md) and [failure-analysis.md](failure-analysis.md).
