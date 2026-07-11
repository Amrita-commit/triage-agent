# Project Plan: Agentic Cloud Incident-Response Copilot (Java)

> **Instructions for Claude Code:** Implement this project phase by phase, in order. Do not skip phases. After each phase, run the verification steps listed under "Acceptance criteria" before moving on. Ask me before making architectural decisions not covered in this document. All infrastructure must run **locally via Docker Compose** — no real AWS account is used during development. AWS deployment is Phase 8 and is OPTIONAL/deferred.

---

## 1. Project Overview

A multi-agent AI system that monitors a demo microservice environment, diagnoses infrastructure/application faults, proposes remediations as reviewable pull requests (never direct execution), and drafts postmortem reports.

**Core principle:** agents are read-only by default; any mutating action goes through a human-approval gate.

**Why local-first:** the entire system runs on a laptop with Docker Compose. A cloud-provider abstraction layer means the same code later points at real AWS (CloudWatch, ECS) with only configuration changes.

---

## 2. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Java 21 | Use records, virtual threads where sensible |
| Framework | Spring Boot 3.3+ | |
| AI orchestration | Spring AI (preferred) or LangChain4j | Claude API via Anthropic provider |
| LLM | Claude API — Haiku for cheap steps, Sonnet for planning | Model routing is a first-class feature |
| Tool protocol | MCP (Model Context Protocol) | Build our own MCP servers in Java for telemetry tools |
| Telemetry (local) | Prometheus (metrics) + Loki (logs) + Grafana (dashboards) | Stand-in for CloudWatch; abstracted behind interfaces |
| Demo app | Small Spring Boot "shop" service with deliberate fault injection endpoints | |
| IaC | Terraform (targeting the local Docker provider now; AWS provider later) | Same module structure reused for AWS |
| Messaging/approval | Local web UI approval queue (simple) — Slack webhook optional later | |
| Containerization | Docker Compose for everything | |
| Build | Maven multi-module | |

---

## 3. Repository Structure

```
agentic-sre-copilot/
├── plan.md
├── README.md
├── docker-compose.yml              # full local stack
├── infra/
│   ├── local/                      # Terraform, docker provider (dev)
│   └── aws/                        # Terraform, AWS provider (Phase 8, stubs only for now)
├── demo-app/                       # the "victim" microservice
│   └── src/main/java/...           # /checkout, /inventory endpoints + fault injection
├── copilot-core/                   # shared domain model, provider abstraction
│   └── src/main/java/.../telemetry # TelemetryProvider interface (Prometheus impl now, CloudWatch later)
├── agents/
│   ├── orchestrator/               # planning agent + delegation logic
│   ├── diagnostics-agent/          # queries metrics/logs, forms hypotheses
│   ├── infra-agent/                # reads Terraform state, detects drift
│   ├── remediation-agent/          # generates Terraform/config diffs as PRs
│   └── postmortem-agent/           # incident report generation
├── mcp-servers/
│   ├── telemetry-mcp/              # tools: query_metrics, query_logs, list_alerts
│   ├── terraform-mcp/              # tools: read_state, plan_diff, generate_pr
│   └── runtime-mcp/                # tools: list_containers, container_status (read-only)
├── approval-ui/                    # minimal web UI: pending actions, approve/reject
├── evals/
│   ├── scenarios/                  # 20-30 YAML fault scenarios
│   ├── runner/                     # injects fault, runs agent, scores result
│   └── results/                    # generated scorecards (markdown tables)
└── docs/
    ├── architecture.md             # with mermaid diagrams
    ├── guardrails.md               # blast-radius control, permission model
    └── failure-analysis.md         # where the agent breaks and why
```

---

## 4. Architecture

```
                        ┌──────────────────┐
   alert / user query → │   Orchestrator   │ ← model: Sonnet (planning)
                        └───────┬──────────┘
              ┌─────────────────┼──────────────────┬────────────────┐
              ▼                 ▼                  ▼                ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
      │ Diagnostics  │  │ Infra/Drift  │  │ Remediation  │  │ Postmortem   │
      │ agent        │  │ agent        │  │ agent        │  │ agent        │
      └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────────────┘
             │ MCP             │ MCP             │ generates diff only
             ▼                 ▼                 ▼
      telemetry-mcp      terraform-mcp     Approval queue (human)
      (Prometheus/Loki)  (state, plan)     → then PR created
```

Rules encoded in the orchestrator:
1. Diagnostics and infra agents have **read-only** tools.
2. Remediation agent can only **generate diffs**; it cannot apply anything.
3. Every agent step is traced (input, tool calls, output, model used, token cost) to a local trace store viewable in the approval UI.
4. Model routing: log summarization / data extraction → Haiku; hypothesis formation and planning → Sonnet. Route via a `ModelRouter` component with per-task-type config.

---

## 5. Implementation Phases

### Phase 0 — Skeleton & local stack
- Maven multi-module skeleton, Spring Boot apps boot, Docker Compose brings up: demo-app, Prometheus, Loki, Grafana, Promtail.
- Demo app exposes `/checkout`, `/inventory`, plus **fault-injection endpoints**: `/faults/latency?ms=`, `/faults/memory-leak`, `/faults/error-rate?pct=`, `/faults/crash`.
- **Acceptance criteria:** `docker compose up` works; Grafana shows demo-app metrics; injecting latency is visible in Prometheus.

### Phase 1 — Telemetry abstraction + MCP telemetry server
- `TelemetryProvider` interface in copilot-core: `queryMetrics(query, range)`, `queryLogs(filter, range)`, `listActiveAlerts()`.
- Prometheus/Loki implementation.
- Wrap as `telemetry-mcp` MCP server exposing those three tools.
- **Acceptance criteria:** MCP inspector (or a test client) can call all three tools against live local telemetry.

### Phase 2 — Diagnostics agent (single-agent loop)
- Spring AI agent loop: receives an alert description → plans → calls telemetry tools → iterates → outputs structured `Diagnosis {hypothesis, evidence[], confidence, suggestedNextSteps}`.
- Hard cap: max 15 tool calls per investigation; must cite evidence (actual query results) for every hypothesis.
- **Acceptance criteria:** inject `/faults/latency?ms=3000`, feed the agent "checkout latency alert", agent correctly identifies the latency source with evidence.

### Phase 3 — Orchestrator + infra agent
- Orchestrator: classifies incoming incident, delegates to diagnostics and/or infra agent, merges findings.
- Infra agent + `terraform-mcp`: `read_state`, `plan_diff` (runs `terraform plan` against local infra), reports drift.
- **Acceptance criteria:** manually edit a container's env outside Terraform; agent detects and reports the drift.

### Phase 4 — Remediation agent + approval gate
- Remediation agent consumes a Diagnosis → proposes a fix as a concrete diff (Terraform change or config change) with rollback notes.
- Approval UI: queue of proposed actions, human approves/rejects. On approval → open a PR (local git repo; GitHub API optional).
- **Never** auto-apply. Enforce in code, not just prompt.
- **Acceptance criteria:** end-to-end: inject fault → diagnosis → proposed diff appears in approval UI → approve → PR/branch created with the diff.

### Phase 5 — Postmortem agent
- After an incident is marked resolved, generate a postmortem: timeline (from traces), root cause, evidence, remediation, prevention items. Output as markdown in `docs/incidents/`.
- **Acceptance criteria:** postmortem for the Phase 4 incident reads coherently and every claim links to a trace entry.

### Phase 6 — Eval harness (THE differentiator — do not cut corners here)
- 20–30 YAML scenarios in `evals/scenarios/`, e.g.:
  ```yaml
  id: latency-checkout-01
  inject: { endpoint: /faults/latency, params: { ms: 3000 } }
  alert: "P95 latency on checkout > 2s"
  expected_root_cause: "artificial latency in checkout handler"
  expected_component: demo-app
  ```
- Runner: resets environment, injects fault, runs full agent pipeline, scores: root-cause accuracy (LLM-as-judge + keyword match), time-to-diagnosis, total token cost, tool-call count.
- Output: markdown scorecard table committed to `evals/results/`.
- **Acceptance criteria:** full eval suite runs unattended with one command; scorecard generated.

### Phase 7 — Observability, docs, polish
- Trace viewer in approval UI (every agent step, cost per incident).
- `docs/architecture.md` (mermaid), `docs/guardrails.md`, `docs/failure-analysis.md` (honest section on failure modes observed in evals).
- README with quickstart, demo GIF/video script, eval scorecard embedded.
- **Acceptance criteria:** a stranger can clone, `docker compose up`, run one eval scenario, and understand the system from README alone.

### Phase 8 — AWS deployment (DEFERRED — stub only)
- Do NOT implement now. Create `infra/aws/README.md` describing the mapping: Prometheus/Loki → CloudWatch Metrics/Logs, Docker → ECS Fargate, TelemetryProvider → CloudWatchTelemetryProvider.
- The provider abstraction from Phase 1 is what makes this a config change, not a rewrite.

---

## 6. Non-negotiable Engineering Standards

- Structured outputs from every agent (Java records deserialized from JSON), never free-text parsing between agents.
- Every LLM call logged with: model, tokens in/out, latency, cost estimate.
- All secrets via environment variables; `.env.example` provided; never commit keys.
- Unit tests for tool implementations and the model router; integration test for one full happy path.
- Conventional commits; one phase per branch.

## 7. Out of Scope (do not build)

- Real AWS resources, auth/multi-user, Kubernetes, fine-tuning, any auto-apply of remediations.
