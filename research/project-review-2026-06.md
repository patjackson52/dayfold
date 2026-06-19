# Whole-Project Review — 6-agent fleet (2026-06-18)

Dimensions: cohesiveness · gaps · simplification · security · UI/UX (M3
Expressive) · performance. Each agent read the full corpus (16 ADRs, the
11-part prototype spec suite, the JSON-schema contract, design mockups,
venture governance). GROUND TRUTH: nothing built.

---

## VERDICT

**The corpus is unusually strong and coherent for a pre-build repo** — every
agent led with that. Component-level rigor (security, schema, deep-link, a11y,
adaptive) is genuinely high. The findings cluster into **two operator
decisions that unblock everything**, a set of **cheap correctness fixes**, and
**two deferred tracks** (M1 security hardening, design-system upgrade) that are
*not* M0-blocking. Nothing found is a redesign.

---

## The two decisions that unblock the build (converged across 4 agents)

### D1 — E2E at M0: **decide it now** (cohesiveness P0-1 · simplification P0-2 · gaps P0-1/P1-13 · security)
- **Cohesiveness:** ADR 0015 says "encrypt at M0" but its **own source research
  says "build E2EE at M1, not M0."** "M0" means two things (schema-column-lock
  vs live encryption). This ambiguity is what makes INB-10 un-ratifiable.
- **Simplification:** single-operator-single-key E2E is **theater**; it's the
  *only* thing gating the schema freeze; ruling **plaintext M0** unblocks it
  today and keeps server FTS.
- **Gaps:** accepting E2E silently **deletes search** (no client-search spec)
  and the **recovery posture is values-shaped** (no escrow = lost-keys-lost-
  data) sitting as an inbox default, not an operator decision.
- **Security:** E2E *when built (M1)* has a **P0 key-substitution / fake-key
  hole** (below) — so it needs real hardening, not a rushed M0 bolt-on.
- **→ Recommendation:** **M0 = plaintext** (single private device); **reword
  ADR 0015** to "lock the encrypted-field *column split* at M0; build live
  E2EE at M1" (matches the research); keep **server FTS** for M0; the full
  adopt/defer of live E2EE becomes a clean **M1 decision** with the key-
  authenticity gate attached. Unblocks P0 schema freeze.

### D2 — M0 surface scope: **briefing feed only, or feed + Event Hubs?** (simplification P0-1 vs UI/UX vs ADR 0006)
- **Simplification:** cut **Event Hubs from M0** — feed-only proves the
  push→render→read loop; Hubs (9 block types, sections, archival, deep-link
  resolution, template catalog) is the heaviest P3 work and unneeded to learn
  "does the calm loop feel good."
- **UI/UX + ADR 0006 (operator's "equal peers" decision):** Hubs is the
  *differentiated wedge* (no incumbent ships AI-curated family event dossiers).
- **→ Tension is real and operator-owned.** Recommend **feed-first M0** (ship
  the loop fastest), **Hubs as the very next slice** — but it's your call since
  Hubs is the moat.

---

## Cheap correctness fixes (decision-independent — apply now)

| # | Fix | Source |
|---|---|---|
| C1 | **Postgres pooler** (Neon serverless driver / transaction-mode Supavisor) — serverless connection exhaustion is the #1 backend risk, unspecced | perf P0 |
| C2 | **Sync needs a matching index** `(family_id, updated_at, id)` covering live+tombstoned rows, per synced table — current indexes force a filesort on the hottest path | perf P0 |
| C3 | **Multi-table sync cursor** = a per-table `(updated_at,id)` struct; `has_more` = OR across tables | perf P1 |
| C4 | **`payload` block-types are untyped `{}`** → define a `$def` per block type (link/checklist/contact/location/milestone/budget) before schema freeze, else codegen emits opaque maps | gaps P1 |
| C5 | **`ord` has no assignment/collision rule** → CLI assigns spaced ints (×100) from manifest position; read tiebreak `(ord, id)` | gaps P1 |
| C6 | **Milestone glossary** — bind {G1/G1a/G1b/G2/G3/G4/G-LAUNCH} ↔ {M0/M1} ↔ {prototype/MVP/dogfood/paid-launch}; the corpus uses them loosely | cohesiveness P1-5 |
| C7 | **"render, don't reason" clarifying line** — = no content/LLM reasoning in-app; deterministic on-device trigger *matching* is not reasoning | cohesiveness P2-6 |
| C8 | **ADR 0013 version contradiction** — Accepted ADR pins `alpha1`; plan defaults `0.6.2` stable. Annotate the index (resolved via INB-11): default 0.6.2, alpha01 opt-in | cohesiveness P0-2 |
| C9 | Delete the **stale `07-cli` open-question** (M0 token scope is locked read+write in 04) | cohesiveness P2-8 |
| C10 | **Pick monorepo + migration tool** in P0 (decisions, not research) | gaps P2 |

---

## Product gaps to close before/at dogfood (gaps agent — the pillars)

- **G1 [P0] — The runnable Claude-authoring loop is unspecified.** Secrets +
  the `.claude/skills/familyai/` wrapper are specced, but **what runs, on what
  host, fed by what input** is not. This is the product's pillar. → write a
  `processes/content-authoring-loop.md` runbook (host, trigger, input source,
  prompt, failure handling).
- **G2 [P0] — Usefulness is unmeasurable** (the learning-lab's core KPI), and
  E2E would block server telemetry. → a minimal privacy-safe signal: local-only
  counters surfaced to the operator, or a structured weekly operator journal.
- **G3 [P1] — Action-card render contract** — `kind:"action"` cards have no M0
  semantics (ADR 0016 unbuilt). → M0 = deep-link target only; `actions[]`
  renders disabled until 0016.
- **G4 [P1] — Second-family provisioning** is a hard wall at M0 (one fixed
  family/token). → either external families gate on M1 auth (state it), or a
  parameterized seed/mint script.
- **G5 [P2] — Example content pack** (2-3 manifests) so day-1 dogfood isn't a
  blank page; **M0-normative states** (pull-to-refresh, sync-error, offline)
  lack mockups; **Postgres backup/restore** for real dogfood data (ADR 0012
  auto-rollback doesn't cover data corruption).

---

## Deferred Track 1 — M1 security hardening (security agent; NOT M0-blocking)

M0 is single-operator/single-key/no-distribution, so these bite at **M1**:
- **S1 [P0] — E2E key-substitution / fake-key MITM.** Server-mediated key-wrap-
  on-approve has no *authenticity* binding → a malicious/insider/agent-
  compromised server swaps the pubkey and silently obtains FCK. **Hard gate for
  M1 E2E.** Fix: key-fingerprint/SAS in the approval ceremony + key-in-QR
  (TOFU) + key-change detection.
- **S2 [P0] — ADR 0012 deploy autonomy contradicts ADR 0015's "survives key
  compromise" claim.** A prompt-injection-exposed full-prod deploy agent can
  bind a new signing key, ship a client that exfiltrates FCK, or do the key
  swap. **Trust-root concentration on one host/credential = the #1 systemic
  risk.** Fix: pull signing-key rotation + FCK custody *out* of the deploy role
  (operator-gated, not "cost-bearing config"); tamper-evident out-of-band
  audit; **rewrite the 0015 blast-radius claim** to be accurate (at-rest past
  ciphertext survives; forward content + tenancy do not).
- **S3 [P1] — Supply chain:** pin ≠ verify on the agent-resolved pre-1.0 crypto
  stack (libsodium/SQLCipher). Add SBOM/provenance (sigstore/SLSA) + a skill/
  MCP allowlist gate to the ADR 0012 rails.
- **S4 [P1] — `intents:resolve`** (2-way loop, ADR 0016) is carved out of the
  IDOR matrix while being a member-prompt-injection target holding FCK — model
  it at build; family-scope it; isolate member input.
- **S5 [P1] — Location-privacy posture** is more diffuse than "live position
  never leaves" (place-count oracle + at-rest decrypted cache + OS region
  list). State the whole-system posture honestly; consider padded place rows.
- **S6 [P0-M0] — Harden the M0 household token's *lifetime*:** absolute lifetime
  + mandatory rotation (not just anomaly alert); keep the M0 `FCK` in a keystore
  distinct from the token; out-of-band anomaly alert. (Applies even at M0.)
→ Record as a Proposed **ADR 0017 (E2E key-authenticity + deploy trust-root
boundary)** before M1 build; correct the 0015 claim now.

## Deferred Track 2 — design-system upgrade (UI/UX agent; feeds Claude Design)

Color/surface/adaptive/deep-link/provenance/a11y are **strong and token-true**;
"vibrant visuals / calm behavior" lands. But it's **"M3 2023 wearing an
Expressive label"** — missing the four May-2025 signatures:
- **Physics MotionScheme** (`MotionScheme.expressive()`; spatial vs effects
  springs) — currently old easing tokens.
- **New components:** button group, FAB menu, split button, loading indicator.
- **Shape *morph*** (asserted, never shown — the M3E signature).
- **Emphasized type scale** for hero text (greeting/countdown).
- **[P0] The triggers/notifications/places design set is unbuilt** —
  `DESIGN-BRIEF-triggers.md`'s 15 screens have no mockups (the privacy
  differentiator has no approved look; gates the trigger surface per ADR 0008).
→ Revise the design briefs with the four signatures + execute the triggers
brief (operator hands to Claude Design).

## Deferred Track 3 — process right-sizing (simplification; operator/meta)
For a solo learning-lab build: **suspend the per-iteration planning-loop ritual
+ 30-day viability re-attack** (org-scale for one person who holds all context;
keep spec-gate-before-build + the inbox); **demote 0011/0015/0016 build-detail
to `research/`** with one-line ADR pointers; **phone-only M0** (defer tablet/
foldable/desktop/Wear); let mockups **inform** rather than waterfall-gate.

---

## Net
Two operator decisions (D1 E2E-at-M0, D2 feed-vs-feed+Hubs) unblock the schema
freeze and the build. ~10 cheap fixes (C1-C10) + 2 product-gap docs (G1 loop
runbook, G2 usefulness signal) make M0 truly buildable. The M1 security track
(S1-S6) and the design upgrade are real and important but **deferred, not
blocking**. The project does not need more specs — it needs these decisions +
fixes, then build.
