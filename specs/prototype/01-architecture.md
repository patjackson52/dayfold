# 01 — Architecture Overview (Prototype / v1)

> Status: **draft → in review** (iteration 1). Synthesizes ADRs 0004–0012 +
> `specs/auth-and-family-design.md` + `specs/event-hubs-design.md` into one
> system view. Build sequencing per ADR 0011 (M0 dumb renderer → M1 auth).

## Purpose & scope

A calm family dashboard that **renders content authored externally** (by the
operator via Claude Code) and pushed through a content API. Two surfaces:
**Now** (briefing cards) and **Hubs** (event dossiers). The app reasons about
nothing; intelligence is upstream.

## System context

```
            ┌─────────────────────────── Clients ───────────────────────────┐
  operator → CLI / Claude Code ──push──▶                                      │
                                        │   HTTPS / JSON (+gzip)              │
  family   → Mobile app (CMP:           │                                     │
            Android + iOS) ──read/auth──▶                                     │
            └────────────────────────────┼─────────────────────────────────┘ │
                                          ▼
                              ┌────────────────────────┐
                              │  API service (stateless)│
                              │  content · auth · invite│
                              │  · device-grant         │
                              └───┬─────────┬──────────┬┘
                                  ▼         ▼          ▼
                          Postgres   Object storage   Firebase Auth (M1)
                       (families,    (docs, large     (Google/Apple/
                        content,      markdown spill)   phone-OTP)
                        auth, creds)
```

## Components

| Component | Responsibility | Milestone |
|---|---|---|
| **Mobile client** (CMP, Android+iOS) | Render Now + Hubs from local cache; markdown render (mikepenz); deep-link nav; auth UI (M1) | M0 render · M1 auth UI |
| **CLI / Claude Code** | Author + push content (cards, hubs, markdown files) via the content API; device-grant auth (M1) | M0 push (single token) · M1 device-grant |
| **API service** (stateless HTTP) | Content upsert/read (tenant-explicit), auth (verify Firebase → mint tokens), invite, device-grant; per-request membership+scope+revocation check | M0 content · M1 auth/invite/device |
| **Postgres** | families, memberships, users, identities, invites, device_authorizations, credentials, hubs/sections/blocks | M0 content tables · M1 auth tables |
| **Object storage** | document refs + large-markdown spill (>~1–few MB); signed URLs | M0 (basic) · M1 (full) |
| **Firebase Auth** | Google/Apple/phone-OTP authentication (GitLive KMP + native glue) | M1 |

## Data flows

1. **Content push (M0):** CLI → `PUT /families/{fid}/hubs/{id}` (idempotent,
   stable IDs, gzip) → validate → upsert Postgres (+ spill large `body_md` to
   object storage) → 200. Auth = single household token at M0; minted CLI
   credential at M1.
2. **Render (M0):** client background-pulls family content → local cache
   (SQLDelight) → UI always renders from cache; deep-link target resolves
   against the local cache (nearest-ancestor fallback).
3. **Auth (M1):** client → Firebase (Google/Apple/phone) → ID token → API
   verifies (Admin SDK) → mints access+refresh → app-driven provider linking.
4. **Invite (M1):** owner mints invite → Universal-Link QR → invitee
   authenticates → **pending membership → owner approves** → active.
5. **Device-grant (M1):** CLI runs RFC 8628 → QR scan-to-approve in app
   (user_code confirm + origin warning) → CLI gets scoped, revocable,
   content-only family credential.

## Cross-cutting

- **Tenancy:** every request resolves requester → active membership in the
  path's `family_id` → role/scope; **default-deny via one middleware**;
  IDOR tests per resource (ADR 0011).
- **Tokens:** backend-minted, asymmetric-signed; never trust claims —
  re-resolve per request; refresh reuse-detection (ADR 0011).
- **Markdown:** CommonMark+GFM; sanitized (no raw HTML; link-scheme
  allowlist; images off at MVP); lazy render (`specs/event-hubs-design.md`).
- **Idempotency/versioning:** client-supplied stable IDs; single-writer LWW
  at M0; `version`/`If-Match` carried for later multi-writer.

## Environments & deploy (ADR 0012)

- **Local:** Firebase Emulator + local Postgres — agents configure + verify
  with no prod/cost.
- **Preview:** per-change deploy + smoke/browser verify (gate before prod).
- **Prod:** agents may promote behind the safety rails (test-green-before,
  verify-and-rollback-after, log).

## Tech lean (agent-buildability, C3 — not yet locked)

API host = **Vercel** (MCP) · DB = **Neon/Supabase Postgres** · object
storage = Vercel Blob / S3-compatible · auth = **Firebase** · client = **CMP**
· CLI = Kotlin/JVM (or thin script) hitting the same API. Final at C3.

## Open questions

- API host language/runtime (Kotlin/JVM vs TS) — interacts with sharing
  models/validation with the CMP client; decide at 03/C3.
- Single-household token shape at M0 (OQ-proto-auth).
- Object-storage provider + signed-URL model (component 06).
