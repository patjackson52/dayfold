# ADR 0018: API Host — TypeScript on Vercel

## Status

**Accepted** 2026-06-18 (operator decision, INB-9). Immutable — supersede, do
not edit. Platform choice (ADR-class). Composes with ADR 0012 (agent build/
deploy), 0013 (Kotlin client/CLI). Recommended by the architecture + perf
reviews.

## Context

The content API host language/runtime was the last undecided P0 gate (blocks
the P0 scaffold + codegen + deploy pipeline). Options weighed: TypeScript/Node
on Vercel vs Kotlin/JVM on a container host (Cloud Run).

## Decision

1. **The content API is TypeScript/Node on Vercel.** Serverless functions
   behind the tenant-explicit content API (03), the M0 household-token
   middleware, and the sync endpoint.
2. **The CLI stays Kotlin/JVM** (ADR 0013); the **client stays CMP/Kotlin**.
   Both the TS (zod) and Kotlin types are **codegen'd from the JSON-schema
   contract** (`specs/domain-model/schemas/content.schema.json`) — one source,
   no hand-kept duplication.
3. **Postgres via a pooler** (perf review): **Neon serverless driver** (HTTP/
   WS, no held TCP) or a transaction-mode pooler (Supavisor/PgBouncer) — never
   a long-lived `pg.Pool` per function. Provider final at C3 (Neon/Supabase).

## Rationale

ADR 0012's deploy-autonomy rail (preview → promote → rollback via the Vercel
MCP, agent-operated) is **first-class only on TS/Vercel**. A Kotlin/JVM API
needs a container host (Cloud Run) — adding standing cost against the <$50/mo
ceiling and breaking the cheap preview/rollback loop the whole agent-build
model depends on. The one Kotlin edge (shared types server↔client) is recovered
by **codegen from the JSON schema**, so the cross-language cost is near zero.

**Rejected:** Kotlin/JVM API on Cloud Run — one language end-to-end, but loses
the Vercel deploy rails + adds standing container cost; only revisit if a hard
need to share *live* Kotlin types server↔client emerges (re-examine with the
codegen path first).

## Consequences

Positive: the agent-build/deploy rails (ADR 0012) work as designed; cheap idle;
scale-to-zero fits the cost cap; the richest TS ecosystem for the API.
Negative: two languages (TS API + Kotlin client/CLI) — mitigated by schema
codegen; the serverless-Postgres pooler is **mandatory** (not optional); a
Vercel/Node dependency.

## Revisit Trigger

Vercel cost/limits become binding at scale; or a hard requirement to share live
Kotlin types server↔client that codegen can't satisfy.
