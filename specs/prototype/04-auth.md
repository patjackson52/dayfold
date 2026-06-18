# 04 — Authentication & Token Service

> Status: **draft → in review**. Implementation spec between ADR 0011
> (hardened auth decisions) + the Firebase fact-check
> (`research/design-review-auth-2026-06.md`) and the `03-api.md` endpoints.
> Mostly **[M1]**; the M0 household token is [M0]. Backend = TS/Vercel
> (INB-9); client = GitLive Firebase KMP + native glue.

## Token model

- **Access token** — backend-minted **JWT**, **asymmetric (EdDSA/Ed25519 or
  RS256)**, short TTL (**≤5 min** so revocation bites fast without a per-request
  denylist on the happy path). Claims: `iss`, `aud` (this API), `sub`=user_id,
  `cid`=credential_id, `exp`, `nbf`, `iat`, `jti`. **Carries no
  authorization** — `family_id`/`role`/`scope` are **re-resolved per request**,
  never trusted from the token.
- **Refresh token** — opaque high-entropy, **stored hashed** (`refresh_hash`),
  **rotating**; each rotation supersedes the prior in a **lineage**.
  **Reuse-detection:** presenting a superseded refresh → **revoke the whole
  lineage** + audit (OAuth 2.1 BCP).
- **Signing keys** — per-environment (preview never shares prod's key), in the
  **secret manager** (agent deploy role *binds*, never reads plaintext — ADR
  0012); **rotation** with overlap; API exposes JWKS for its own verification.
  **`alg` allowlist enforced; reject `none` and HS/RS confusion.**

## Auth + authz middleware (every route, fail-closed)

```
1. Extract `Authorization: Bearer <t>`; missing/garbage → 401.
2. Identify credential:
   - access JWT: verify sig+alg-allowlist+iss/aud/exp/nbf; load credential by `cid`.
   - [M0] household token: constant-time compare vs the configured secret →
     its credentials row (kind='cli', user_id NULL, single family_scope).
3. credential.revoked_at IS NOT NULL → 401.  (lookup error/timeout → 401, FAIL CLOSED)
4. Resolve target family from the PATH `{fid}` (or the resource row for
   non-/families routes); cross-tenant → 404 (identical body).
5. Re-resolve membership(user, family) → active? role? scope? (M0 token ⇒
   implicit single family, scope=content:write).
6. Scope/role gate: content routes need content:write; privileged actions
   (member approve/remove, invite mint/revoke, credential revoke, device
   approve) need role='owner'; **content-scoped creds → 403 on every
   non-content route** (M0 token can't reach management).
7. Attach {user_id, family_id, role, scope} to the request context. Default-deny.
```

This middleware is the single tenancy/authz choke point (ADR 0011 §10); every
route inherits it. IDOR test matrix runs against it per resource.

## Firebase integration

- **Client (GitLive `firebase-auth`):** `GoogleAuthProvider`, `OAuthProvider`
  (Apple), `PhoneAuthProvider`. **Native `expect/actual` glue required** for
  Phone-OTP (iOS APNs-silent-push/reCAPTCHA + URL scheme; Android Play
  Integrity) and Apple (nonce/`ASAuthorization`). Spike the hard iOS paths
  first.
- **Server:** verify the Firebase **ID token via Admin SDK**
  (sig + `aud`=project + `iss` + `exp`); **never decode-only**. Firebase
  account **disable/delete does not propagate** to our minted tokens → sync
  via a blocking function or periodic re-validation.

## Flows

**Sign-in (`POST /auth/session`):** verify ID token → look up
`user_identities(provider, provider_uid)` (Apple: `sub`; **never dedupe on
email** — Email Enumeration Protection on, no `fetchSignInMethodsForEmail`).
Found → that user. Not found, no conflict → create user+identity. **Conflict**
(same person, different provider) → `409 {pending_link:{token}}` (see linking).
Mint access+refresh.

**Provider linking (`POST /auth/link`):** **app-driven, proof-of-control** —
the user must complete a **fresh re-auth with the EXISTING provider in the same
request** before the new identity is attached (`linkWithCredential`). No
linking on email-string match.

**Refresh (`POST /auth/refresh`):** validate refresh_hash → rotate (new
refresh, supersede old) → mint access. Reused/superseded → revoke lineage.

**Signout (`POST /auth/signout`):** set `revoked_at` on the presenting
credential lineage; **no grace window** (next request 401s via step 3).

**Account delete + export (`DELETE /auth/account`, `GET /auth/export`):**
export = inline JSON (MVP). Delete = cascade users→identities/memberships/
credentials; **honor last-owner invariant** (transfer/handle owned families);
**call Apple `revokeToken`** for Apple identities (App Store 5.1.1(v)).

## M0 household token [M0]

A `credentials` row (`kind='cli'`, `user_id NULL`, `family_scope`=the one
family, `scopes={content:write}`, `revoked_at` for kill-switch). The **secret
itself lives in the platform secret store** (Vercel encrypted env / secret
manager) — never in repo or client bundle. Validated by **constant-time
compare** in middleware step 2. **No refresh, no management scope.**
**Rotatable** (provision new secret → flip → revoke old); **use is logged**
(ADR 0012 audit). One leaked env secret = content-write to one family only.

## Recovery, SIM-swap, SMS abuse

- **Owner accounts require ≥2 linked methods** (enforced, not nudged) before
  the family holds sensitive content; **step-up** (re-verify) on new-device
  phone-OTP before privileged actions.
- **Manual recovery floor** (all methods lost) = an explicit identity-proofing
  procedure with an **audit trail** (it's a social-engineering surface);
  `OQ-auth-recovery-floor` stays open until the procedure is written.
- **SMS:** Firebase **App Check** + reCAPTCHA SMS defense (Pre-GA) + **region
  allowlist** + per-number-prefix velocity + **daily SMS spend cap + alert**.

## Open questions
- Access-token TTL vs a short revocation denylist — pick ≤5 min TTL (chosen)
  vs jti denylist; confirm under load.
- Blocking-function vs periodic re-validation for Firebase disable/delete sync
  (blocking functions push to metered Identity Platform — cost check).
- Exact recovery-floor procedure (operator + counsel; `OQ-auth-recovery-floor`).
