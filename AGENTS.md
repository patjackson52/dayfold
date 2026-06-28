# AGENTS.md — AI Agent Quick Reference

Compact orientation for AI agents. For deep work, follow the full start-of-session
routine in `CLAUDE.md`. This file is self-contained enough for most tasks.

## What this project is

**Dayfold** — a calm, AI-powered household dashboard. One account per family.
It renders a daily briefing + smart actions authored by external AI loops (like
Claude Code). It is **not** a chatbot, chore app, or calendar/email replacement.

**Current state:** M0 prototype built + live (Vercel + Neon; Android on-device).
Validation result: CONDITIONAL — learning-lab GO, standalone-business NO-GO.
Phase A (validation follow-through) is running. Build only starts after planning gates.

## Hard constraints (never decide alone)

1. No children's personal data (COPPA). Adults-only accounts at MVP.
2. No pricing constants or billing mechanics changes.
3. No Gmail restricted-scope reads server-side (triggers CASA audit).
4. No external messages/sign-ups/payments — agents draft, operator sends.
5. No spend above agreed thresholds; no legal entities; no signing.
6. ADR-class decisions (scope, pricing, compliance, vendor, data handling) →
   write a Proposed ADR and wait for operator acceptance before proceeding.
7. Design-first (ADR 0008): no deep planning or build without hi-fi mockups in
   `designs/` and operator sign-off.

## Repository layout

```
apps/api/        Content API — TypeScript, Hono, Postgres; deployed on Vercel
apps/cli/        dayfold CLI — Kotlin; login/push/pull/template/validate/delete
apps/client/     Compose Multiplatform (KMP) — the Android/iOS/desktop UI
apps/androidApp/ Android host (thin wrapper on :client)
packages/schema/ content.schema.json → generated Kotlin + TypeScript types
.claude/skills/  dayfold-curator skill (authors Hubs + BriefingCards via CLI)
adr/             Architecture Decision Records (immutable once Accepted)
specs/           PRD, architecture, domain model, design specs
processes/       Planning loop, agent routing, dev loop, release, fleet patterns
backlog/         now.md / next.md / later.md / operator-inbox.md
context/         values-and-direction.md, business-constitution.md (operator-owned)
research/        Validation reports, market research (dated evidence)
designs/         Hi-fi UI mockups (Claude.design HTML files + Figma)
planning/        Waterfall workstream board (workstreams.md)
```

## Key process files

| Need | File |
|---|---|
| Session context load order | `CLAUDE.md` § Required start-of-session routine |
| Task routing | `processes/agent-routing.md` |
| Build + test loop | `processes/agent-dev-loop.md` |
| Planning loop protocol | `processes/planning-loop.md` |
| Confidence protocol (what agent can decide alone) | `processes/planning-loop.md` §3 |
| ADR index | `adr/decisions-index.md` |
| Current work | `backlog/now.md` |
| Operator decisions pending | `backlog/operator-inbox.md` |
| Agentic build safety rails | `processes/agent-build-automation.md` |

## Building + testing

**Toolchain:** JDK 17, Kotlin 2.3.20, Gradle 9.4.1, AGP 9.2.1, Node 24.
Single Gradle root at `apps/` (not per-module).

```bash
# API (TypeScript)
cd apps/api && npm ci && npx vitest run

# CLI (Kotlin)
cd apps/cli && ./gradlew --no-daemon build

# Client desktop tests (Compose)
cd apps && ./gradlew --no-daemon :client:desktopTest

# Android compile smoke
cd apps && ./gradlew --no-daemon :androidApp:assembleDebug

# Codegen (schema → types)
npm run codegen        # from repo root

# Full CI equivalent
cd apps/api && npm run build:fn   # rebuild Vercel bundle (must match committed api/index.js)
```

## Content authoring (CLI)

```bash
dayfold whoami                          # verify login (family=<id> api=<url>)
dayfold pull                            # read current hubs + cards
dayfold pull --hub <hubId>              # read one hub's full tree
dayfold template <type>                 # starter JSON (card types or hub/section/block)
dayfold push <id> <file.json> --hub     # push a hub (--section, --block for children)
dayfold push <id> <file.json> --type <type>  # push a typed card (local validation)
dayfold validate <file.json>            # validate locally without pushing
dayfold delete <id>                     # delete hub (cascades); --card for cards
dayfold login [--allow-env-key]         # RFC 8628 device grant + keychain storage
dayfold logout                          # revoke + clear saved token
```

See `.claude/skills/dayfold-curator/references/` for the full content model,
CLI cheatsheet, and guardrails (binding on the curator skill).

## Confidence protocol (what agents may decide alone)

| Level | When | Action |
|---|---|---|
| HIGH | Desk-proven, no values/legal touch | Decide + record |
| MEDIUM | Plausible, but operator call preferred | Mark `[pending-ratify]` + add to operator-inbox.md |
| LOW / values-shaped | Affects direction, legal, scope | Ask operator directly |

**Never agent-decided:** legal, pricing constants, scope changes, kill/pivot, spend,
external actions (emails, sign-ups, payments, anything a third party sees).

## Agentic build safety rails (ADR 0012)

Before any prod deploy or cost action: run `apps/api npm run preflight`, confirm
tests green, log the action. If a deploy changes auth or migrations, verify on prod
after. Roll back on non-200 health check. Never skip hooks or force-push main.

## Schema conventions

- Content ids: 26-char Crockford base32 ULIDs
- Timestamps: ISO-8601
- Image URLs: `https` only, allowlisted hosts (currently `upload.wikimedia.org`)
- `provenance.source = "claude"` + `at` ISO-8601 on every authored card/block

## Where NOT to make changes without an ADR

Scope, pricing, platform/vendor choices, customer-data handling, COPPA boundary,
automation-autonomy boundaries, maintenance-burden changes. Write a Proposed ADR
in `adr/` first; accepted ADRs are immutable (supersede, don't edit).
