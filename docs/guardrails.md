# Guardrails

The core principle: **agents are read-only by default, and every mutating action passes through a
human-approval gate.** These guardrails are enforced in code, not just in prompts.

## 1. Read-only tools

The diagnostics and infra agents are given only read-only MCP tools:

- `telemetry-mcp` — `query_metrics`, `query_logs`, `list_alerts` (queries only).
- `terraform-mcp` — `read_state`, `plan_diff` (a **refresh-only** `terraform plan`; never `apply`).

There is no tool that mutates infrastructure. An agent physically cannot change anything it observes.

## 2. The remediation agent proposes; it never applies

`RemediationAgent` produces a `ProposedRemediation` — a target file, the proposed new content, a
computed unified diff, rollback notes, and a risk level. It has **no code path** that writes to a
live system. The proposal is inert until a human acts on it.

## 3. The human-approval gate

`approval-ui` holds proposals as `PENDING`. A human decision is the **only** way a proposal advances:

- **Approve** → the change is written to a **new git branch** (`remediation/*`) via JGit and left for
  review as a PR. Even approval produces a *branch*, never a live apply.
- **Reject** → nothing happens.

Enforced and tested (`ApprovalServiceTest`): approval invokes the publisher **exactly once**,
rejection **never** invokes it, and a decision is **one-way** (you cannot approve an already-decided
request). The publisher writes only to a branch and restores the working tree to `main`
(`GitBranchPublisherTest`).

## 4. Blast-radius controls

- **Tool-call budget:** the diagnostics loop is hard-capped at 15 tool calls per investigation
  (enforced in `DiagnosticsAgent`, tested). On reaching it the model is forced to conclude, and the
  result is marked `truncated`.
- **Evidence requirement:** a `Diagnosis` is only considered trustworthy if it cites concrete
  evidence (`isEvidenceBacked()`); the prompt forbids inventing metric values or log lines.
- **Resilient delegation:** if one sub-agent fails (e.g. the LLM API is unavailable), the orchestrator
  still returns the others' findings rather than failing the whole incident.

## 5. Cost & auditability

Every LLM call is traced with model, tokens in/out, latency, and an estimated USD cost
(`TokenCostEstimator`); every tool call is traced with its arguments and a result preview. The full
trace per incident is viewable in the approval UI's **trace viewer**, and the postmortem timeline is
built from these traces — so every claim is auditable back to a recorded step.

## 6. Secrets

All secrets come from environment variables; `.env` is git-ignored and never committed. The services
boot without a key (LLM clients are created lazily) so health checks and the non-LLM paths work
offline.

## Out of scope (deliberately not built)

Real AWS resources, auth/multi-user, Kubernetes, and **any auto-apply of remediations**.
