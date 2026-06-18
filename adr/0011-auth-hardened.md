# ADR 0011: Auth & Family-Tenancy Architecture (Hardened) — supersedes ADR 0010

## Status

**Accepted** 2026-06-18 (operator approved in-session, after the 5-agent
design review `research/design-review-auth-2026-06.md`). Immutable —
supersede, do not edit. **Supersedes ADR 0010** (which tripped its own
revisit trigger: the security review found exploitable flaws in the
invite/device-grant flows). Detailed design: `specs/auth-and-family-design.md`.

## Context

ADR 0010 chose the right standards (Firebase Auth, RFC 8628 device grant,
backend-minted scoped/revocable tokens, M:N default-deny tenancy) but stated
key controls as assertions rather than enforced mechanics. A 5-agent review
found three independent account/tenant takeover paths (all mapping to live
2026 attacks), one sequencing error, and a factual Firebase correction. This
ADR keeps ADR 0010's architecture and binds the hardening.

## Decision (changes vs ADR 0010 are marked ⚠)

### Sequencing
1. ⚠ **Auth is a distinct milestone AFTER the ADR 0007 prototype.** The
   prototype keeps its **single household token** for the CLI — **no RFC
   8628, no Universal/App Links** in the prototype. The CLI device-grant is
   the critical path for the **product**, not the prototype. The content-API
   path is **tenant-explicit** (`/families/{fid}/hubs/{id}`) from day one so
   auth grows without changing the endpoint contract.

### Identity (⚠ corrected to Firebase reality)
2. ⚠ **No dedupe on email/phone strings.** Email Enumeration Protection is
   on by default; Firebase never auto-links; phone & Apple private-relay
   have no stable dedupe email. Use **app-driven linking** triggered by
   `account-exists-with-different-credential` (carry the pending credential,
   user picks the provider), **join on provider UID** (Apple `sub`), and
   treat **phone as its own identity**.
3. ⚠ **Never auto-link providers.** Require **proof-of-control of the
   existing account** (sign in with the original provider) before attaching
   a new one. Match only on `email_verified==true` / OTP-proven phone.
4. **Apple account-deletion + token revocation** (`revokeToken`) is required
   (App Store 5.1.1(v)) and part of the account-deletion flow.

### Invites (⚠ policy reversed)
5. ⚠ **All invites are owner-approved (pending → approve). Auto-join is
   removed** — possession of a QR is not proof of presence; a forwarded QR
   would otherwise grant full family-data read. Invite tokens stay hashed,
   short-TTL, capped, single-use, revocable; invitee authenticates as self
   before entering the approval queue.

### CLI / device grant (⚠ hardened, fallback cut)
6. ⚠ **Email→push fallback is removed for MVP.** Approval is QR scan-to-
   approve + in-app `user_code` entry only.
7. **Anti-phishing controls (RFC 8628 §5.4):** the approval screen requires
   **on-phone `user_code` confirmation** (QR fills it; the human still
   confirms it matches), shows the **request origin geo/ASN**, and warns/
   blocks datacenter-ASN origins. `user_code` ≥8 chars from an unambiguous
   ~20-symbol alphabet, **rate-limited + lockout**; `device_code` one-time
   (invalidated on issue/deny/expiry); server enforces `interval`/`slow_down`.

### Tokens & revocation
8. **Backend-minted tokens** remain (Firebase revocation is global-per-UID
   only — per-device revoke + "Connected devices" is impossible natively, so
   this layer is required). Rules: asymmetric signing (EdDSA/RS256), strict
   `alg` allowlist, full `iss/aud/exp/nbf`, key rotation; **never trust
   `family_id`/`scope`/`role` from the token** — re-resolve membership +
   credential-not-revoked **per request**. Refresh **reuse-detection →
   revoke the token family**. Revocation effective within one request.
9. **Owner accounts** require ≥2 linked auth methods (not just a nudge) +
   step-up on new-device phone-OTP, to dampen SIM-swap takeover.

### Tenancy & integrity
10. **Default-deny enforced as one mandatory membership/scope middleware**
    every route inherits, with per-resource IDOR tests (family A → 403/404 on
    family B) covering invite/device/credential/membership endpoints too.
11. **Schema invariants:** unique `(provider, provider_uid)` and membership
    `(user_id, family_id)`; **≥1 active owner per family** (transfer before
    last-owner leave/delete); defined **account-deletion + data-export**
    flow; content stored **relationally with `family_id`** scoping.

### Abuse/cost
12. **SMS:** App Check + reCAPTCHA SMS defense (Pre-GA) + **region
    allowlist** + per-number-prefix velocity + daily spend cap. Avoid MFA/
    blocking-functions at MVP to stay off metered Identity Platform.

## Consequences

Positive: closes the three takeover P0s; matches Firebase reality; phasing
keeps the prototype cheap; tenant-explicit path future-proofs the endpoint.
Negative: owner-approval adds one tap to every join; removing email→push and
auto-join trims convenience the operator originally wanted (accepted for
security); the bespoke token + device-grant server remains the densest
surface — use a vetted OAuth-server lib where possible; CMP Firebase is a
community SDK (GitLive) needing native `expect/actual` glue for Phone-OTP +
Apple.

## Revisit Trigger

A re-review after the spec hardens still finds an exploitable flow; Firebase
ships an official KMP SDK or RFC 8628 support; the multi-family UI or teen
(14+, ADR 0005) memberships ship; or auth moves from "later milestone" into
active build (re-confirm the sequencing boundary then).
