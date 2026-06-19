# ADR 0015: End-to-End Encryption

## Status

**Proposed** (2026-06-18). Operator-gated — touches customer-data posture, a
**values-shaped recovery policy**, a server-feature cut (FTS), and the **M0
schema column-split** (cheap pre-data, expensive to retrofit). Decide soon:
the report recommends locking the cleartext/ciphertext split **before any real
data exists**. Investigation: `research/e2e-encryption-investigation.md`.
Composes with the constitution ("privacy by architecture"), ADR 0004/0007
(dumb store), ADR 0011 (auth), ADR 0013 (KMP client), ADR 0014 (private
triggers). Resolves `OQ-e2e-encryption`; folds in `OQ-auth-recovery-floor`.

## Context

The server is a dumb store that never processes content, so **E2EE removes
nothing the server does** (except one search index). Verdict from the
investigation: **CONDITIONAL GO — adopt; encrypt at M0; distribute keys at M1.**

## Decision (proposed)

1. **Encrypt content; keep routing cleartext.** Ciphertext (server never
   reads): `body_md`, block `payload`, titles, `triggers`, `places.lat/lng/
   label`. Cleartext (server needs to route/scope): `family_id`, ids,
   `version`, timestamps, `ord`, `status`, enums, dates. **AEAD with
   AAD=`(family_id, id, version)`** to prevent ciphertext transplant.
2. **Per-family content key `FCK`; distribution = per-member X25519
   sealed-box wrap (scheme b).** The server holds only public keys + opaque
   wrapped-`FCK` blobs + ciphertext — never `FCK`. **Owner "approve" = the
   moment `FCK` is wrapped to the new member's public key**; RFC 8628 device-
   approve = wrap `FCK` to the CLI's public key. Clean per-member revocation.
   (Rejected: passphrase — possession=access, breaks owner-approval; MLS —
   over-engineered at n≤6, no KMP lib.)
3. **Sacrifice: server-side FTS** (`tsvector`/GIN over `body_md`) → **client-
   side search only** (acceptable at family-data volume).
4. **Recovery (values-shaped — the biggest risk, not distribution):** OS
   keychain sync + **owner-mediated re-grant** (reuses owner-approval) +
   **required recovery phrase for owners** (owner = key custodian) + **no
   default server escrow**. This resolves the auth recovery floor.
5. **Perf:** **decrypt-once-into-cache** off-main (ADR 0013 Rule E), not per-
   render; the SQLDelight cache becomes **SQLCipher** with a keychain-held DB
   key (new at-rest exposure addressed).
6. **Library:** **ionspin `kotlin-multiplatform-libsodium`** (audited
   libsodium; AEAD + X25519 + `crypto_box_seal` + Argon2; pre-1.0 — pin, like
   the redux-kotlin alpha bet). Fallback: whyoleg `cryptography-kotlin`; CLI
   may use `age`.
7. **Milestone:** **M0 = encrypt** (single `FCK` in keychain on the operator's
   CLI + devices, no distribution — and the moment to **lock the column
   split**); **M1 = multi-member key distribution** (inseparable from the auth
   build).

## Honest claim

"**We can't read your content**" — NOT "we know nothing." Metadata leaks:
which-family, structure, dates, **place count**, ciphertext sizes. State this
truthfully in marketing/UX.

## Consequences

Positive: privacy becomes structural (survives server breach, subpoena,
insider, even ADR 0012 deploy/signing-key compromise → only ciphertext); a
real, honest differentiator; key handoff reuses owner-approval + device-grant.
Negative: loses server FTS; **lost keys = unrecoverable content** (recovery
posture is the load-bearing UX risk); SQLCipher + decrypt pipeline + per-
member wrapping is real client work (M1); a pre-1.0 crypto lib dependency;
does NOT protect device compromise or a malicious approved member.

## Revisit Trigger

If accepted: the column split is locked at M0 (this changes 02-DDL's FTS index,
06-storage, 08-client). If the recovery UX proves too lossy for non-technical
members, revisit escrow. If a KMP MLS lib matures and families exceed ~6,
reconsider scheme (c).

## If accepted, downstream spec changes
- **02-data-model:** content columns become ciphertext; **drop the `tsvector`
  GIN index**; add `family_keys`/`member_pubkeys`/wrapped-`FCK` tables (M1).
- **06-storage:** spilled bodies are already-encrypted blobs.
- **08-client:** SQLCipher cache + decrypt-once pipeline + key store.
- **05-invite / device-grant:** approval wraps `FCK` to the joiner's pubkey.
