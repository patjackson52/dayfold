# ADR 0027: S2 Firebase Verify — Direct JWKS, Emulator-in-CI, Android-First

## Status

Proposed 2026-06-22 (operator-directed; refines ADR 0011, extends ADR 0023).
Records three operator decisions taken while planning the S2 Google-sign-in
build. Does not supersede 0011/0023 — narrows *how* the Firebase ID token is
verified and tested. Immutable once Accepted — supersede, do not edit.

## Context

ADR 0011 specified "Firebase ID-token verify (**Admin SDK**)"; ADR 0023 set the
S2 provider set to **Google + Apple** (free Spark tier). Starting the build
surfaced three implementation forks that the operator resolved:

1. **Verify mechanism** — Admin SDK (heavy, needs a service-account secret) vs.
   verifying the Firebase ID token's RS256 signature directly against Google's
   published keys with the `jose` lib already in the stack.
2. **How to test the real verify path** without a human Google login or a live
   Firebase project — and the fact that Vercel **preview** deploys disable the
   `dev-token` seam (`VERCEL_ENV=preview` → 404).
3. **Which client targets** get real Google sign-in at S2.

The auth architecture already separates *identity verification* from
*token minting* (`/auth/dev-token` → `StubVerifier` → `findOrCreateUser` → mint
OUR EdDSA tokens). S2 changes only the verification half; everything downstream
(EdDSA access + rotating refresh, whoami, membership routing, per-device revoke)
is unchanged.

## Decision

**1. Verify by direct JWKS, not the Admin SDK.** A `FirebaseVerifier` verifies
the token's RS256 signature against Google's securetoken JWKS with `jose`, and
checks `iss = https://securetoken.google.com/<projectId>`, `aud = <projectId>`,
`exp`. **No service-account key** to store or rotate; fits Vercel serverless.

**2. Identity binds to the underlying provider uid, not the Firebase uid**
(ADR 0011: never rely on Firebase auto-link). `provider =
firebase.sign_in_provider` (`google.com` / `apple.com`); `provider_uid =
firebase.identities[provider][0]` (the Google account id / Apple `sub`), falling
back to `sub` only if absent. Keyed via the existing unique
`(provider, provider_uid)`.

**3. New endpoint `POST /auth/firebase {idToken}`** — always-on (production
included); the identity proof is the Firebase signature, so there is **no dev
gate**. It mints OUR tokens via the same `findOrCreateUser → mintCredentialFor →
mintAccess + issueRefresh` path. `/auth/dev-token` is **retained** as the
hermetic test/local seam (still gated off prod/preview).

**4. Test topology:**
- **Hermetic unit** — sign Firebase-shaped tokens with a test RS256 key, inject
  the JWKS (DI), assert valid/forged/expired/bad-aud/bad-iss/anonymous. Runs in
  plain CI, no network.
- **Real-path integration** — the **Firebase Auth Emulator** (CI). Emulator
  tokens are **unsigned** (`alg: "none"`), so `FirebaseVerifier` has an
  **emulator mode** (`FIREBASE_AUTH_EMULATOR_HOST` set) that decode-and-validates
  claims without a signature check — mirroring the Admin SDK. This exercises the
  real endpoint + DB without a Google account.
- **Preview = manual QA** with real Google test users (dev-token is 404 there).

**5. Targets: Android only at S2.** Android via Credential Manager + Google ID →
Firebase ID token → `POST /auth/firebase`. Desktop/iOS keep `dev-token`; iOS
real sign-in is deferred until its app project exists (bundle id + its own OAuth
client + `GoogleService-Info.plist`).

## Security control (load-bearing)

Emulator mode bypasses signature verification → a forgeable identity if ever
enabled in production. **Mitigation:** the `/auth/firebase` handler honors
emulator mode only when `VERCEL_ENV ∉ {production, preview}` — even if the host
env var leaks in. The emulator host var must never be set on prod/preview.

## Consequences

Positive:
- No service-account secret to manage/rotate; one less prod secret + lighter
  cold start.
- CI exercises the real verify code path hermetically (emulator), not just mocks.
- Rename (ADR 0026) already landed the new `com.sloopworks.dayfold` id → the
  Firebase Android app + OAuth client get registered **once** under the final id.

Negative / deferred:
- Emulator mode is a verification bypass that must stay env-gated (control above).
- **Provider linking** (ADR 0011 `account-exists-with-different-credential`,
  owner ≥2 methods) is **out of the Google-only S2 slice** — one provider, no
  linking yet. Returns with Apple / the second-method requirement.
- JWKS endpoint URL + token claim shape must be confirmed against the live
  Firebase project at integration (defaulted + `FIREBASE_JWKS_URI`-overridable).

Operator-gated (ADR 0012 console login; guardrail #3 free-tier):
- Create the Firebase project + enable Google; register the Android app under
  `com.sloopworks.dayfold` + signing **SHA-1** (debug + Play App Signing); add
  `google-services.json`; set `FIREBASE_PROJECT_ID` in Vercel env.

## Revisit Trigger

Adding Apple sign-in (needs its identity binding + `revokeToken`, already in
ADR 0011/0023); enabling provider linking; adding Phone-OTP (ADR 0023); or a
Firebase change to the ID-token signing/JWKS scheme.
