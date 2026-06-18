# Prototype Build-Spec Plan (loop state)

Driven by the `/loop` (every 30 min). **Each iteration: read this tracker →
advance the next waterfall step of the current component → update the tracker
→ commit + push.** Goal: enough detail to implement backend, auth, invite,
DB, storage, mobile client, CLI, API.

## Per-component waterfall

`brainstorm → design → multi-agent review → impl-plan → review → implement
(write the spec/DDL/OpenAPI) → verify (review pass)`. Most design decisions
already live in ADRs 0004–0012 + `specs/auth-and-family-design.md` +
`specs/event-hubs-design.md` — those are the brainstorm/design inputs;
the loop produces **implementation-ready specs** and reviews them. Run the
brainstorming step only where a component has open design questions.

## Sequencing (ADR 0011)

Specs are authored for the whole v1 now (design-first). **Build** order
stays: **M0 prototype** (dumb renderer + content API + single household
token, no auth) → **M1** (Firebase auth, invite, CLI device-grant,
Universal Links). The specs note which milestone each part belongs to.

## Component tracker

| # | Component | Spec file | Status | Next step |
|---|---|---|---|---|
| 01 | Architecture overview | `01-architecture.md` | **draft → in review** | apply multi-agent review |
| 02 | Data model & DB schema (Postgres DDL) | `02-data-model.md` | todo | implement DDL from auth+content schemas |
| 03 | API design (OpenAPI) | `03-api.md` | todo | content (tenant-explicit) + auth + device-grant + invite |
| 04 | Authentication & token service | `04-auth.md` | todo | Firebase verify → mint, per-request scope/revoke |
| 05 | Invite system | `05-invite.md` | todo | owner-approved flow, token lifecycle |
| 06 | Storage (object storage, docs/large markdown) | `06-storage.md` | todo | refs, large-body spill, signed URLs |
| 07 | CLI tool | `07-cli.md` | todo | device-grant auth, content push, markdown files |
| 08 | Mobile client (CMP) | `08-mobile-client.md` | todo | modules, render+markdown, local cache, nav/deep-link |
| 09 | Security controls + test/verify plan | `09-security-and-test.md` | todo | from the 5-agent review + ADR 0011/0012 |

## Current

- **Iteration 1:** created this tracker + drafted `01-architecture.md`;
  dispatched a multi-agent review of the architecture. Next: apply review,
  then start component 02 (DB DDL).

## Log

- I1 (2026-06-18): tracker + architecture draft + review dispatched.
