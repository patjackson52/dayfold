# E2EE Feasibility Investigation — family-ai-dashboard

> **Status: Research report (2026-06-18).** Evidence snapshot for an ADR
> decision. Nothing is built. Grounds: `specs/prototype/01-architecture.md`,
> `02-data-model.md`, `specs/event-hubs-design.md`,
> `specs/auth-and-family-design.md`, ADRs 0011 / 0013 / 0014. Claims are
> labeled `[fact:source]` / `[estimate]` / `[assumption]`. Adversarial review
> not yet applied — this feeds, does not gate.

---

```
╔═══════════════════════════════════════════════════════════════════════╗
║ VERDICT: CONDITIONAL GO — write an ADR now, build E2EE at M1, not M0.  ║
║                                                                       ║
║ Structurally feasible. The "dumb store" architecture (CLI encrypts →  ║
║ server stores blind ciphertext → device decrypts) makes E2EE a near-  ║
║ natural fit: the server already processes no content. M0 (single      ║
║ household, operator-only key) is TRIVIAL — one symmetric key in the   ║
║ OS keychain on both ends, no distribution problem. ALL the hard part  ║
║ is M1 multi-member key distribution under owner-approved-join.        ║
║                                                                       ║
║ BIGGEST RISK: key RECOVERY, not key distribution. E2EE = lost keys =  ║
║ permanently unrecoverable family content, and a non-technical family  ║
║ member who loses their phone is the realistic failure, not an         ║
║ attacker. Recovery UX is the make-or-break, and it is where E2EE      ║
║ guarantees are most often silently broken.                            ║
║                                                                       ║
║ SACRIFICE that must be accepted up front: server-side full-text       ║
║ search (tsvector/GIN over body_md, spec'd in event-hubs-design.md)    ║
║ becomes IMPOSSIBLE → client-side search only.                         ║
║                                                                       ║
║ CONFIDENCE: HIGH on feasibility + scope; MEDIUM on the recommended    ║
║ key-distribution scheme (depends on a recovery-floor decision that is ║
║ already an open question: OQ-auth-recovery-floor).                    ║
║                                                                       ║
║ WARRANTS AN ADR NOW: yes. E2EE touches customer-data-handling         ║
║ posture, the FTS scope cut, a recovery/escrow policy (values-shaped), ║
║ and the M0/M1 boundary — all ADR-class per CLAUDE.md. Decide the      ║
║ posture BEFORE the data model freezes, because the cleartext/         ║
║ ciphertext column split is hard to retrofit.                          ║
╚═══════════════════════════════════════════════════════════════════════╝
```

---

## 1. Scope — what gets encrypted, where the line is

The architecture already splits the data into **routing/tenancy metadata**
(the server must read it) and **content** (the server only stores and serves
it). E2EE draws the line exactly on that existing seam.

### MUST stay cleartext (server-readable) — routing, tenancy, concurrency

| Field | Why it cannot be encrypted |
|---|---|
| `family_id`, all `id`s (hub/section/block/card/place) | Tenancy middleware resolves requester→`family_id`→scope on **every** route (`01-architecture.md` §Tenancy invariant). Composite PKs `(family_id, id)` and FKs are the integrity spine. |
| `version`, `updated_at`, `created_at`, `deleted_at` | Optimistic concurrency (`If-Match`), LWW single-writer enforcement, soft-delete sweeps — all server-side (`02-data-model.md`). |
| `ord` | Server returns ordered rows; ordering is structural, not sensitive. |
| `status`, `kind`, `type`, `block_type` | Drive indexes (`hubs(family_id,status)`), archival queries, template-catalog validation. Low-sensitivity enums. |
| `not_before`, `expires_at`, `start_at`, `end_at`, `countdown_to` | Promoted to typed columns specifically so the server can index/sort archival + countdown (`02-data-model.md` lines 44-46). Encrypting them kills that. |
| FK relationships (which block belongs to which section) | Parent-must-exist enforcement, cascade integrity. |
| `provenance.credential_id` | Audit (who pushed). Could be encrypted but no reason to — it is operational metadata, not family content. |

### CAN be E2E-encrypted (server never needs to read) — the content payload

| Field | Notes |
|---|---|
| `blocks.body_md` | The big one. Long-form markdown, design target ~1 MB. **This is what kills server FTS** (§3). |
| `blocks.payload` (jsonb) | Structured block fields (link URLs, checklist text, contact name/phone/email, budget items, location label/address). Encrypt the **value**; the discriminator `type` stays cleartext. |
| `briefing_cards.title`, `briefing_cards.body_md` | Card text. `kind` stays cleartext for routing. |
| `hubs.title`, `sections.title` | The dossier/section names ("Dad's chemo schedule", "Custody handoff plan") are exactly the sensitive part. |
| `blocks.triggers` / `briefing_cards.triggers` (jsonb) | Already matched **only on-device** (ADR 0014) — server never reads them. Free to encrypt; no feature lost. |
| `places.lat`, `places.lng`, `places.label` | Home/school coordinates. `02-data-model.md` line 119 + ADR 0014 already say "encrypted at rest." E2EE upgrades that from server-held-key to client-held-key — strictly better, and the schema already anticipates it. `radius_m` can stay cleartext (not sensitive). |

**The line is clean** because the system was designed render-don't-reason: the
server never inspects content today, so encrypting content removes nothing the
server currently does — *except* the one FTS index in `02-data-model.md` (§3).

### Mechanics

- Encrypt each content field as an independent AEAD ciphertext blob (nonce +
  ciphertext + tag), store in the existing `text`/`jsonb` column as base64 or
  a `bytea`. `body_md` stays a `text`/spill column, now holding ciphertext.
- **AEAD associated data (AAD) = the cleartext routing tuple**
  `(family_id, id, version)`. This binds ciphertext to its row so the server
  cannot transplant block A's ciphertext onto block B's row without the device
  detecting it on decrypt `[assumption: standard AEAD-binding practice]`. This
  is important: it stops a malicious/compromised server from shuffling
  ciphertexts within a tenant.
- The `CHECK (body_md IS NULL OR body_ref IS NULL)` one-of constraint and the
  spill-to-object-storage path (M1) work unchanged — ciphertext spills the
  same as cleartext.

---

## 2. Key management & distribution — THE CRUX

**Goal:** one per-family content key (call it `FCK`) reaches every member
device AND every CLI/device-grant credential, and the server **never** sees it
— across (a) account creation, (b) owner-approved invite, (c) RFC 8628 device
grant.

### The three candidate schemes

**(a) Passphrase-derived family key.**
Owner picks a passphrase; `FCK = Argon2id(passphrase, salt)`; every member
types the same passphrase to derive the same key.
- *Pro:* dead simple, no server-stored key material, no public-key infra.
- *Con (disqualifying):* distributing the passphrase to family members is an
  **out-of-band human problem** the app can't secure ("text Grandma the
  password"). No per-member revocation (remove a member → must rotate the
  passphrase → re-key everything → re-distribute to everyone). Argon2 over a
  human passphrase is brute-forceable if the server is ever breached and the
  passphrase is weak. **Fails owner-approved-join** — the whole point of
  owner-approval is that possession ≠ membership; a shared passphrase makes
  possession = access. **Reject.**

**(b) Per-member asymmetric keypairs + wrap `FCK` to each member's public key.**
Each device/credential generates an X25519 keypair, keeps the private key in
the OS keychain, publishes only the **public** key. The owner (or any member
holding `FCK`) wraps `FCK` to the new member's public key (`crypto_box_seal` /
sealed box); the server stores the **wrapped blob** (ciphertext only). New
member unwraps with their private key.
- *Pro:* server only ever holds public keys + opaque wrapped blobs → **never
  sees `FCK`**. **Per-member revocation is clean**: remove a member → rotate
  `FCK` → re-wrap to remaining members only (the removed device's wrap is just
  not produced). Maps *exactly* onto owner-approved-join: the approval step
  (owner clicks "approve") is the natural moment the owner's device wraps `FCK`
  to the approved member's public key. Same for device-grant: approving the CLI
  in-app is when the approving device wraps `FCK` to the CLI's public key.
- *Con:* the approving device must be **online and hold `FCK`** to mint the
  wrap (acceptable — owner is the human clicking approve). Forward secrecy /
  post-compromise security is weaker than MLS (a leaked `FCK` decrypts past
  content until rotation) — but for **stored family documents** (not a live
  chat stream) this is the right trade: there is no message stream to
  ratchet, content is re-pushed idempotently, and PCS matters far less than
  for messaging.
- **This is the recommendation.**

**(c) Sealed-sender / MLS-style group key (RFC 9420).**
Treat the family as an MLS group; the group ratchets a shared secret with
forward secrecy and post-compromise security; add/remove = MLS Commit.
- *Context:* MLS is now mainstream — Google Messages and Apple Messages began
  MLS-over-RCS rollout May 2026; Discord uses MLS for group call key exchange
  [fact: search synthesis, datatracker.ietf.org/doc/rfc9420, gopher.security].
  It is the right tool for **high-churn real-time group messaging** with
  thousands of members and strong PCS needs.
- *Con (over-engineered here):* a family is 2–6 members of **stored documents**,
  not a message stream. MLS brings epoch/Commit/Welcome state machine
  complexity, a delivery-service assumption, and **no production-grade KMP
  (Android+iOS shared) MLS library exists** as of 2026 `[assumption: none
  surfaced in search; OpenMLS is Rust, would need FFI on both platforms]`. The
  O(log n) tree efficiency MLS buys is irrelevant at n≤6 — scheme (b)'s O(n)
  re-wrap is a handful of sealed-box operations. **Reject for MVP; revisit only
  if the product ever becomes multi-family-at-scale or adds live chat.**

### Recommended distribution flows (scheme b)

```
ACCOUNT CREATION (owner, first family):
  device generates X25519 keypair → private key → OS keychain
  device generates random FCK (32 bytes) → keychain
  device wraps FCK to its OWN public key, uploads:
    - public key (cleartext)
    - self-wrapped FCK blob (so a 2nd device of the same owner can get it)
  server stores public key + wrapped blob per (credential/device).

OWNER-APPROVED INVITE (pending → approve):
  1. invitee authenticates (Firebase), generates own keypair,
     uploads public key, creates PENDING membership (auth-and-family §3a).
     ── invitee has NO FCK yet → cannot read content. Correct: pending = no access.
  2. owner sees approval queue. On APPROVE:
     owner's device fetches invitee public key, wraps FCK to it
     (crypto_box_seal), uploads the wrapped blob, flips membership active.
  3. invitee device downloads its wrapped FCK blob, unwraps with private key.
  ── The "waiting for approval" state is now ALSO "waiting for the key." Clean.

RFC 8628 DEVICE GRANT (CLI):
  1. CLI generates keypair, sends public key with the device-authorization req.
  2. On in-app approve (user_code confirm + origin warning, ADR 0011 §6-7):
     the approving device wraps FCK to the CLI's public key, uploads blob.
  3. CLI polls, receives wrapped FCK, unwraps. Now CLI can ENCRYPT content
     it pushes (and decrypt to re-curate). Content-only scope unchanged.
  ── Revoke CLI credential → its wrap is orphaned; rotate FCK on next sensitive
     change if the credential is believed compromised.
```

**Server's view throughout:** public keys + opaque wrapped blobs + ciphertext
content. Never `FCK`, never a private key, never plaintext. The tenancy
middleware and IDOR matrix are unchanged — wrapped-blob endpoints just join the
per-resource IDOR test matrix (`01-architecture.md` already extends it at M1).

**Rotation:** member removal or suspected device compromise → owner's device
generates `FCK'`, re-wraps to all remaining members, re-encrypts content
lazily (next push) or eagerly. Because content is **idempotently re-pushable
from the operator's upstream source** (event-hubs-design.md §power-user flow),
eager re-encryption is a CLI re-push — cheaper than in a system where content
originates on many devices.

---

## 3. Features sacrificed

| Feature | Impact | Severity |
|---|---|---|
| **Server-side FTS** (`blocks_body_fts` GIN/`tsvector` over `body_md`, `02-data-model.md` lines 242-244; "full-text search ready" in event-hubs-design.md) | **Eliminated.** Server holds only ciphertext; `to_tsvector` over ciphertext is meaningless. → **client-side search only**: decrypt the cache locally, search in SQLDelight (FTS5) or in-memory. | **Medium.** At family scale (a few hundred blocks) client-side FTS5 over the decrypted cache is fast and arguably *better UX* (instant, offline). The loss is real only at large corpora or server-side relevance ranking (Meilisearch path in event-hubs-design.md is also dead). For this product's data volume, **acceptable**. Drop the GIN index from the DDL under E2EE. |
| **Server-side content validation** | Server can't lint markdown, validate payload shapes, or enforce the link-scheme allowlist on `body_md`. | **Low.** Markdown safety is **already client-side by design** (`01-architecture.md` §Markdown safety — renderer is XSS-safe by structure, link allowlist enforced in-app, images off). The CLI (operator-trusted, holds keys) is the validation point. Move schema validation of `payload` to the CLI pre-encrypt; server validates only the cleartext envelope (ids, version, type enum). |
| **Deep-link / trigger resolution** | **No loss.** Deep-links resolve against the **local decrypted cache** (`01-architecture.md` flow 2, ADR 0013 Rule I); triggers match **on-device** (ADR 0014). Both already never touch server-side content. | **None.** |
| **Server-side spill handling** | Object-storage spill (M1) stores ciphertext blobs; signed-URL transport unchanged (it was always transport-only, never embedded in markdown). | **None.** |
| **Provenance display** | `provenance` stays cleartext (operational); but if any provenance field embeds content snippets, encrypt those. | **None** (keep provenance metadata cleartext). |

**Net:** the only meaningful casualty is server FTS, and the product's data
volume makes client-side search a near-equal or better substitute.

---

## 4. Recovery / key loss — THE REAL RISK

E2EE's iron law: **lost keys = permanently unrecoverable content.** The
realistic failure is not an attacker — it is a non-technical family member who
gets a new phone, or the owner losing the only device holding `FCK`. This is
where most "E2EE" products quietly compromise the guarantee.

### Options (combine, don't pick one)

| Option | How | Tradeoff |
|---|---|---|
| **OS keychain sync (default, low-friction)** | Store the private key (and self-wrapped `FCK`) in **iCloud Keychain** (Secure Enclave-bound, escrowed via Apple's multi-party threshold HSM — Apple cannot read it) and Android's **Block Store / Keystore-backed cloud backup**. New device of the same user → key restores automatically. | *Pro:* invisible recovery for same-OS device replacement; Apple's escrow is HSM-threshold, not plaintext-to-Apple [fact: support.apple.com escrow-security-for-icloud-keychain]. *Con:* cross-ecosystem (iPhone→Android) does NOT carry; relies on the user having OS backup enabled. **Not sufficient alone.** |
| **Recovery phrase (BIP39-style, the real backstop)** | At account/family creation, derive or wrap a recovery secret into a 12/24-word phrase [fact: BIP39, cypherock.com]; the user writes it down. Phrase → recovers the private key / `FCK`. | *Pro:* ecosystem-independent, no provider trust, the standard E2EE escape hatch. *Con:* the classic UX wall — non-technical users lose the paper. Mitigate: make it **optional but strongly nudged at owner setup**, allow re-display while a device is still healthy. |
| **Owner-mediated re-grant (family-shaped recovery)** | A member who loses their key is treated like a **new invite**: they re-authenticate, generate a fresh keypair, and the **owner re-wraps `FCK` to them** (§2 invite flow). No content lost — the content was never theirs to hold, only `FCK` access was. | *Pro:* turns individual key loss into a routine re-approval, leverages the existing owner-approved flow, **no separate recovery infra for non-owners**. *Con:* requires the **owner** to still hold `FCK`. So the **owner** is the single point of failure and MUST have a recovery phrase + multi-device. **This is the product's best fit** — recovery becomes "ask the family owner to re-approve you," which families already understand socially. |
| **Optional server escrow (explicit opt-in)** | Wrap `FCK` to a provider-held key (or HSM) so support can recover. | *Pro:* zero-friction recovery. *Con:* **breaks the E2EE guarantee** for anyone who opts in (provider can now decrypt) → subpoena/insider risk returns. Only as a clearly-labeled opt-out of E2EE, never default. **values-shaped → operator decides** (CLAUDE.md confidence protocol). |

### Recommended recovery posture

1. **OS keychain sync** for frictionless same-ecosystem device replacement.
2. **Owner-mediated re-grant** as the primary recovery for non-owner members
   (no extra infra; reuses owner-approval).
3. **Recovery phrase REQUIRED for owners** (the family's key custodian),
   optional-but-nudged for others. This pairs with ADR 0011 §9 ("owner
   accounts require ≥2 linked auth methods") — owners are already the hardened
   tier; make them the key-custody tier too.
4. **No default server escrow.** Offer it only as an explicit, labeled "let
   support recover my data (weakens encryption)" opt-out — operator-gated
   decision. Resolves the standing `OQ-auth-recovery-floor`.

---

## 5. Performance / UX

- **Decrypt-once-into-the-cache, not decrypt-each-time.** On `sync`, the
  middleware/effects layer (ADR 0013 Rule E — effects run off-main) decrypts
  ciphertext and writes **plaintext into the SQLDelight cache**; the Redux
  store + composables read decrypted slices via selectors. Decrypting on every
  render is wasteful and would block the main thread on long markdown.
  `[assumption: standard practice]`
- **Cost is negligible at this scale.** AEAD over ~1 MB markdown is sub-
  millisecond to low-single-digit ms on modern phones `[estimate]`; a family
  cache is a few hundred blocks. Decrypt-on-sync amortizes it to background.
- **Markdown parse stays the bottleneck, not crypto** — event-hubs-design.md
  already mandates off-main-thread `parseMarkdownFlow()` + `LazyMarkdownSuccess`
  (a plain Column is ~12× slower). Crypto rides the same off-main pipeline.
- **Cache-at-rest security (the new exposure).** Decrypting into SQLDelight
  means **plaintext content now lives on the device at rest** — this is the
  honest cost of decrypt-once. Mitigate:
  - Encrypt the SQLDelight DB file with **SQLCipher**, key held in OS keychain
    / Secure Enclave / Keystore (StrongBox where available), released on device
    unlock.
  - Or keep the cache ciphertext-at-rest and decrypt into memory per session.
  - **Recommendation:** SQLCipher-backed cache + keychain-held DB key. This
    keeps the on-device threat surface to "attacker has unlocked device,"
    which E2EE explicitly does **not** defend against (§7) — so it is the right
    boundary, and SQLCipher raises the bar against offline device theft.

---

## 6. KMP/CMP crypto library recommendation (2026)

Requirements: shared Android+iOS Kotlin, AEAD (XChaCha20/ChaCha20-Poly1305 or
AES-GCM), X25519, sealed-box key-wrapping, Argon2 (for recovery-phrase KDF).

| Library | AEAD | X25519 | Sealed box / wrap | Argon2 | Android+iOS shared | Status 2026 |
|---|---|---|---|---|---|---|
| **ionspin `kotlin-multiplatform-libsodium` (bindings)** | XChaCha20/ChaCha20-Poly1305, secretstream | yes (`crypto_kx`, `crypto_box`) | **`crypto_box_seal` (sealed box) + `crypto_kx`** — exactly the wrap primitive scheme (b) needs | yes (Argon2id) | **yes** — directly built libsodium for JVM/native, libsodium.js for web | **v0.9.5** [fact: search/mvnrepository]. Real libsodium under the hood (audited C), thin Kotlin wrapper. Pre-1.0 but the *crypto* is libsodium, not a reimplementation. **Recommended.** |
| **whyoleg `cryptography-kotlin`** | AES-GCM, AES-CCM, ChaCha20-Poly1305 (no XChaCha) | X25519 via XDH | **no key-wrapping / sealed-box primitive** | no | yes — wraps OpenSSL/CryptoKit/WebCrypto/JCA | **v0.6.0**, Apr 2026, active [fact: github.com/whyoleg/cryptography-kotlin]. Clean platform-native design, but **missing sealed-box + Argon2** → you'd hand-build ECIES + a KDF. Good fallback / use for the raw AEAD layer if libsodium binding integration proves painful. |
| **Google Tink** | strong AEAD + key management | (hybrid via HPKE) | yes (keyset/HPKE) | no | **Android-first; iOS via Obj-C/Swift Tink, NOT shared Kotlin** | mature but **not a KMP-shared library** — defeats the ADR 0013 single-codebase directive. Reject for shared layer. |
| **`age` / rage** | ChaCha20-Poly1305, X25519 recipients | yes | file-encryption recipients model | no | Go/Rust — no native KMP; FFI on both platforms | great **CLI-side** option (the Kotlin/JVM CLI could shell to/embed `age` for content encryption with a familiar recipients model), but not the on-device KMP lib. |
| **Signal libs** | — | — | MLS/double-ratchet | — | not KMP-shared, messaging-shaped | over-scoped (§2c). Reject. |

**Recommendation:**
- **On-device (CMP shared) + CLI core: ionspin `kotlin-multiplatform-libsodium`
  bindings (0.9.x).** It is the only maintained true Android+iOS shared library
  that provides **AEAD + X25519 + sealed-box wrap + Argon2** in one — the exact
  primitive set scheme (b) and the recovery phrase need, over audited libsodium.
  Pin the version (pre-1.0); confirm the bindings list exposes `crypto_box_seal`
  + `crypto_kx` + `crypto_pwhash` on Android-release and iosArm64 before
  committing `[assumption: present per supported_bindings_list; verify]`.
- **Fallback:** `whyoleg/cryptography-kotlin` 0.6.0 for the AEAD layer +
  hand-rolled X25519 ECIES if the libsodium native build (iOS/Android `.so`/
  framework packaging) causes friction.
- **CLI may additionally use `age`** for a clean recipients/wrap model if the
  Kotlin libsodium binding on JVM is awkward — same X25519 + ChaCha20-Poly1305
  primitives, interoperable if formats are pinned.
- Pre-1.0 maturity risk mirrors ADR 0013's redux-kotlin `1.0.0-alpha1`
  acceptance — the operator already took an analogous pin-and-watch bet.

---

## 7. Threat model

**E2EE PROTECTS against:**
- **Server breach / database dump** — attacker gets ciphertext + public keys +
  wrapped blobs, no `FCK`, no plaintext. This is the headline win: a Postgres
  leak no longer leaks family content (titles, schedules, places, documents).
- **Subpoena / legal compulsion** — the operator/provider *cannot* produce
  plaintext; there is no key to hand over (absent opt-in escrow). Strong
  posture for a family-data product.
- **Insider / malicious-operator** — agent-operated deploy (ADR 0012) means
  agents touch infra; E2EE means even a fully compromised deploy role or a
  rogue operator-with-DB-access sees only ciphertext. Strong alignment with the
  ADR 0012 "signing-key compromise = crown jewel" concern: under E2EE, even
  signing-key compromise (tenancy bypass) yields ciphertext, not content.
- **Ciphertext transplant within a tenant** — prevented by AAD-binding to
  `(family_id, id, version)` (§1).

**E2EE does NOT protect against:**
- **Device compromise** — malware/forensics on an *unlocked* member device
  reads the decrypted cache (SQLCipher raises the bar for *locked/stolen*
  devices only). This is the inherent E2EE limit; document it.
- **Compromised member** — anyone holding `FCK` (any approved member, any
  approved CLI) sees all family content. E2EE protects against the *server*,
  not against a malicious approved member. Owner-approval is the membership
  gate; E2EE doesn't replace it.
- **Malicious client code** — if the app/CLI is backdoored, it can exfiltrate
  plaintext or `FCK`. E2EE assumes trusted endpoints.

**Metadata leakage (E2EE does NOT hide):**
- **Which family** is active, request timing/frequency, **who is a member**
  (membership rows are cleartext for tenancy), credential/device list.
- **Ciphertext sizes** → approximate content length (a 900 KB block is clearly
  a long document; an empty card is visible). Mitigate with bucketed padding
  if size-correlation is a concern `[estimate: low priority for this product]`.
- **Place COUNT and existence** — number of `places` rows per family is visible
  even though lat/lng are encrypted. The *count* of geofenced locations leaks.
- **Structure** — number of hubs/sections/blocks, their `type`/`status`/dates,
  `not_before`/`expires_at` timing. The *shape* of family life is visible; the
  *content* is not. For this product, content-confidentiality is the promise;
  metadata-confidentiality (à la Session/sealed-sender) is out of scope and
  would conflict with the routing/indexing design. **State this honestly in
  any privacy claim** — "we cannot read your content" is true; "we know
  nothing" is not.

---

## 8. Milestone split — M0 vs M1

**M0 E2EE is trivial; M1 key-distribution is the only hard part.** The
architecture (ADR 0011 §1) already defers all of auth/multi-member to M1 and
keeps M0 a single household with a single household token in the OS keychain.
E2EE lands on the same seam:

- **M0 (single household, operator-only key) — TRIVIAL, recommend doing it:**
  - One symmetric `FCK` generated once, stored in the **OS keychain on the
    operator's CLI host AND in the app's keychain** (the M0 household token is
    already a platform secret — `01-architecture.md` line 108). No distribution
    problem: one human, one (or few self-owned) devices.
  - CLI encrypts content fields before `PUT`; app decrypts on `sync`.
  - Server stores ciphertext from day one. **This is the high-value moment to
    decide** — encrypting at M0 means the cleartext/ciphertext column split is
    correct *before any real data exists*, avoiding a painful re-encrypt
    migration later.
  - Recovery at M0 = a recovery phrase for the single operator key. Done.
  - **Cost:** the FTS index decision (drop it) must be made now; client-side
    search ships from M0.

- **M1 (multi-member) — the hard part, all of it:**
  - Per-member keypairs, public-key publication, **wrap-`FCK`-on-approve** in
    the invite + device-grant flows (§2), rotation on member removal,
    owner-mediated re-grant recovery, SQLCipher cache.
  - This rides the M1 auth build (it literally hooks the "owner approves"
    moment) — so it is **not separable from M1 auth**; spec it together.

**Recommended split:** **encrypt content at M0** (single-key, trivial, locks
the schema split correctly), **build key distribution at M1** alongside auth.
This matches the existing M0-dumb-renderer → M1-auth sequencing perfectly and
front-loads the cheap part while the data model is still soft.

---

## 9. Verdict

**CONDITIONAL GO. Write an ADR now. Encrypt at M0, distribute keys at M1.**

- **Feasibility: HIGH.** The dumb-store, render-don't-reason architecture makes
  E2EE structurally natural — the cleartext/ciphertext line falls on the
  existing routing-vs-content seam, and the server already processes no
  content. Scheme (b) (per-member X25519 + sealed-box wrap of a per-family key)
  maps cleanly onto owner-approved-join and the RFC 8628 device grant.

- **Single biggest risk: RECOVERY, not distribution.** E2EE makes lost keys =
  unrecoverable content, and the realistic failure is a non-technical family
  member losing a phone. The owner-mediated re-grant + required-owner-recovery-
  phrase posture (§4) is the mitigation, but it makes the **family owner a key
  custodian** — a new responsibility that must be designed into onboarding and
  UX, not bolted on. Get this wrong and the product silently either loses
  customer data or quietly escrows to the server (breaking the promise).

- **Mandatory accepted sacrifice:** server-side FTS (`tsvector`/GIN over
  `body_md`) is gone → client-side search only. Acceptable at family data
  volume; must be decided **before the data model freezes** because the index
  and the cleartext-vs-ciphertext column split are entangled.

- **Why an ADR now (ADR-class per CLAUDE.md):** it touches customer-data-
  handling posture, a recovery/escrow policy that is **values-shaped** (operator
  decides — overlaps `OQ-auth-recovery-floor`), the FTS scope cut, and the
  M0/M1 boundary. The schema decisions are cheap to make right *now* and
  expensive to retrofit after real data exists. The ADR should:
  1. Accept content-E2EE with scheme (b);
  2. Lock the cleartext-metadata / ciphertext-content column split (§1);
  3. Drop the FTS GIN index, mandate client-side search;
  4. Decide the recovery posture (owner-custodian + required owner recovery
     phrase; no default server escrow) — **operator-gated**;
  5. Sequence: encrypt at M0, distribute at M1;
  6. Pin ionspin libsodium bindings, note pre-1.0 risk + whyoleg fallback.

---

## Sources

- RFC 9420 (MLS) — https://www.rfc-editor.org/rfc/rfc9420.html ;
  https://datatracker.ietf.org/doc/rfc9750/
- MLS 2026 adoption (Google/Apple Messages over RCS, Discord) — search
  synthesis; https://www.gopher.security/post-quantum/understanding-messaging-layer-security
- Sender Keys vs MLS complexity — https://arxiv.org/pdf/2301.07045
- ionspin Kotlin Multiplatform libsodium (v0.9.5, bindings) —
  https://github.com/ionspin/kotlin-multiplatform-libsodium ;
  https://mvnrepository.com/artifact/com.ionspin.kotlin/multiplatform-crypto-libsodium-bindings-android/0.9.1
- whyoleg cryptography-kotlin (v0.6.0, Apr 2026) —
  https://github.com/whyoleg/cryptography-kotlin
- iCloud Keychain HSM escrow (provider cannot read) —
  https://support.apple.com/guide/security/escrow-security-for-icloud-keychain-secdeb202947/web ;
  https://support.apple.com/guide/security/secure-icloud-keychain-recovery-secdeb202947/web
- BIP39 recovery phrases — https://www.cypherock.com/blogs/bip39-word-list-guide-how-it-secures-your-crypto-recovery-phrase
- E2EE backup / HSM recovery survey — https://arxiv.org/pdf/2406.18226
