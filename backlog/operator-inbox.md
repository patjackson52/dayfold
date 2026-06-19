# Operator Inbox

Questions and ratifications awaiting the operator. Swept weekly (per
`context/values-and-direction.md`). Nothing auto-applies; items aging >2
sweeps escalate in the digest. Newest first.

Format: `INB-N · date · urgency(high/med/low) · status(open/answered/stale)`
Each item: question, context link, **proposed default**, urgency.

---

- **INB-15 · 2026-06-19 · med · open — Accept ADR 0022 + the D2 storage fork.**
  The Claude-Design import (`designs/content/*`, `Brand.dc.html`) introduced a
  typed content library (6 types → Now/Hub/Detail), the container-transform
  **fold gesture**, and the **Dayfold** brand — none of which had an ADR (the
  designs mis-cite "ADR 0015", which is actually E2EE). ADR 0022 (Proposed) +
  epic `planning/content-detail-epic.md` capture it. **Decide:** (a) accept ADR
  0022; (b) the **D2 fork** — *extend `briefing_cards` in place* (recommended for
  M0) vs *unify into `content_item`* (cleaner, defer to M1 E2EE migration).
  **Proposed default:** accept + extend-in-place for M0. ADR-class (scope +
  schema). Blocks all build tasks.

- **INB-16 · 2026-06-19 · med · open — Sign off the imported content/detail
  mockups (ADR 0008 design-first gate).** `designs/content/Content-Library`,
  `Detail-Phone`, `Tap-To-Detail`, `Brand` are now in-repo. These are NEW
  surfaces; ADR 0008 needs operator sign-off before build. **Note the design
  GAP:** only phone detail is designed — the **adaptive two-pane detail** needs
  a follow-up Claude-Design pass (CL-10 blocked on it). **Proposed default:**
  sign off the phone surfaces; queue the adaptive-detail design pass. Operator-
  only (taste call).

- **INB-17 · 2026-06-19 · low · open — Confirm the "Dayfold" product name.**
  The designs adopt **Dayfold** ("your day, folded into one place") as the brand;
  repo/product is still "family-ai-dashboard". The *visual* M3 theme (CL-0)
  proceeds regardless; only the **name** needs your call (product naming = ADR-
  class). **Proposed default:** adopt "Dayfold" as the product name; keep the
  repo slug. Confirm or pick another.

- **INB-18 · 2026-06-19 · low · open — Confirm the M0 build slice = 2 types,
  not 6.** The 8-dimension review converged: prove the renderer+detail+fold with
  **`file` + `invite`** first, add the other 4 types in a follow-on slice; defer
  CLI typed-authoring, related-edges, real maps, and adaptive. **Proposed
  default:** yes, 2-type M0 slice (build order in the epic). Agent-recommendable
  but flagged so you can widen to all 6 if you want the full surface sooner.

- **INB-9 · ANSWERED 2026-06-18 → TypeScript on Vercel** (ADR 0018). CLI/client stay Kotlin; types codegen from schema; Postgres via pooler. **Last P0 gate cleared.**

- **INB-9 (orig) · Ratify API host = TypeScript/Vercel.**
  Spec-build loop (architecture review) recommends the backend API in
  **TypeScript on Vercel** (preserves the ADR 0012 preview→promote→rollback
  deploy-autonomy rail; a JVM API needs a container host + standing cost vs
  the <$50/mo cap). **CLI stays Kotlin**; types codegen'd from the JSON
  schema. Platform choice = ADR-class. **Proposed default:** ratify at C3 as
  an ADR. Confirm or pick Kotlin/JVM + Cloud Run instead.

- **INB-11 · SUPERSEDED 2026-06-19 → use `1.0.0-alpha01` (the latest).** The
  operator **owns/maintains reduxkotlin** and will keep it updated, so the
  alpha-churn risk that drove the 0.6.2 default doesn't apply. New directive:
  **leverage the latest APIs** (`concurrentStore`, devtools, the reduxkotlin
  CLI, screenshot tooling) and architect the clients as **`f(store.state) →
  UI`** — the store is the single state source. Verified: both Kotlin modules
  build + test + render on the Pixel on alpha01. (Was: 2026-06-18 → 0.6.2.)
- **INB-10 · ANSWERED 2026-06-18 → M0 = PLAINTEXT** (live E2EE = M1 option, gated by ADR 0017). Schema freeze unblocked.
- *(review decisions 2026-06-18: M0 = briefing-feed-only [Hubs→next slice]; planning-loop ritual suspended for the solo M0 build — keep spec-gate + inbox + multi-agent reviews.)*

- **INB-11 (orig) · redux-kotlin: alpha01 or 0.6.2 stable?**
  You chose `1.0.0-alpha1` (ADR 0013). Verified: it exists (exact
  `1.0.0-alpha01`, all modules, KMP), but it's ~1-day-old (pub 2026-06-17) with
  two alpha01-only modules. **Proposed default:** build P3 on **`0.6.2` stable**
  (hand-written root reducer + `selectorState`/`select{}`) and adopt alpha01
  behind a feature flag once it matures — no hard block either way, and code
  calls `fieldState` (not `fieldStateOf`). Confirm stable-default / insist on
  alpha01.

- **INB-10 · RESOLVED 2026-06-18 → M0 = PLAINTEXT; live E2E = M1 option gated by ADR 0017 (see ANSWERED INB-10 above). Original prompt:**
  Investigation (`research/e2e-encryption-investigation.md`) verdict:
  **CONDITIONAL GO** — the dumb-store architecture makes E2EE nearly free;
  encrypt at M0, distribute keys at M1 (per-member X25519 wrap mapping onto
  owner-approve). **Why high/now:** it changes the **M0 schema** (content
  columns become ciphertext, drop the server FTS index) — cheap to lock before
  real data, expensive to retrofit. Trade-off: loses server-side search;
  **lost keys = lost data** (recovery = OS keychain + owner-mediated re-grant +
  owner recovery phrase, no server escrow — this is **values-shaped, your
  call**). **Proposed default:** accept ADR 0015 → next loop iterations adjust
  02/05/06/08 specs for the encrypted column split. Accept / amend recovery
  posture / defer.

- **INB-15 · 2026-06-19 · med · open — reduxkotlin 1.0 feedback (you maintain it).**
  Findings from wiring `1.0.0-alpha01` into the app →
  `research/reduxkotlin-1.0-feedback.md`. Headline **P0: `redux-kotlin-compose`
  doesn't pull `redux-kotlin-granular` transitively** (GMM variant misses it,
  though the POM declares it) → `FieldStateKt` (selectorState/fieldState) fails
  to load → bare "unresolved reference". Also: compose needs Kotlin ≥2.3.x while
  core/threadsafe read from 2.2.x; selectorState/fieldState are extensions
  (top-level call = "unresolved"); and `concurrentStore`/CLI aren't on Maven Central yet. **DevTools IS published
  (1.0.0-alpha01) — now wired + verified on-device (ADR 0019).** Doc has the
  full list + severities for 1.0.0; `DevTools.md` text predates the publish.

- **INB-14 · DONE 2026-06-19 → Android SDK + Pixel 10 Pro + emulators working; G1a met on-device (feed renders from cloud). iOS shell still needs your Mac. (Was: P3 device-render shell.)**
  The client CORE is built + tested (redux-kotlin 0.6.2 store + /sync reducer,
  5 tests green). What remains to literally "see the feed on your phone" needs
  hardware agents can't supply:
  1. **Android:** the Android SDK + an emulator/device to build & run the
     Compose app (agent can install the SDK; *seeing* it needs a device or your
     OK to run an emulator).
  2. **iOS:** your **Mac + Xcode + Apple Developer** account (P-1) — agents
     can't build/sign iOS.
  The loop will next add a **Compose Desktop** preview of the same feed (builds
  headlessly, no SDK) to prove the render path while the phone targets wait on
  you. Tell me: install the Android SDK + run an emulator now, or hold for your
  device?

- **INB-13 · 2026-06-18 · med · open — Trigger designs need a v2 pass (Claude Design).**
  The new trigger/place/notification mockups are complete (14.5/15) + calm, but
  the 3-agent review found a **P0 honesty bug**: the Places/affordance copy says
  saved place coords "never leave the device" — false per ADR 0014 (they're
  encrypted server-side family content; only *live position* stays local). Fix
  list is in `designs/DESIGN-BRIEF-triggers.md §6b` (privacy copy + the four
  M3-Expressive signatures + offline screen + geo=M1 labeling). **Hand §6b back
  to Claude Design before the trigger surface (M1) gates (ADR 0008).** Schema
  side (`Place.kind`) already fixed.

- **INB-12 · DONE 2026-06-19 → Vercel + Neon created; M0 API deployed live at `family-ai-dashboard.vercel.app` (verified). (Was: P-1 operator bootstrap.)**
  The build loop is running; these account/auth steps only you can do (ADR
  0012 — agents can provision *within* an authed account, but not create the
  account/billing/domain):
  1. **Vercel** — create/auth the project + org (for the MCP deploy rail).
  2. **Postgres** — create a **Neon** (or Supabase) project + billing; put the
     pooled connection string into Vercel's secret store. (Neon serverless
     driver, per ADR 0018.)
  3. **Domain** `api.<host>` DNS (operator-owned) — optional at first (use the
     `*.vercel.app` URL to start).
  4. **Apple** Developer account + a **Mac** (for the iOS client — see INB-14).
  **The deploy config now exists** (`apps/api/vercel.json` + `api/index.ts` Hono
  handler; `pg` talks to Neon's **transaction-pooler** endpoint, no driver
  swap). Once you do the above, deploy is ~3 steps:
  - `npm i -g vercel && vercel link` (in `apps/api`), set env **DATABASE_URL** =
    the Neon **pooled** connection string, **HOUSEHOLD_SECRET** + **HOUSEHOLD_
    CREDENTIAL_ID** (from `node scripts/provision.mjs` run against the Neon DB
    after applying `migrations/0001_m0_init.sql`).
  - `vercel deploy` (preview) → smoke-test `PUT/GET /sync` → `vercel promote`.
  - point the CLI/client `FAMILYAI_API` at the deploy URL.
  *(First-deploy unknown to verify: Vercel bundling the `.ts`-extension imports —
  if it balks, add a tiny build step; the app itself is fully CI-tested.)*
  Reply when Vercel + Neon exist and the loop wires/verifies the live deploy.

- **INB-3 · 2026-06-18 · med · open — Cheapest kill-checks (you, ~2 hrs).**
  Before/while building: (a) run Gemini Daily Brief's school-email→family-
  digest flow yourself; (b) use Maple+ a bit and name what it can't do for a
  niche. These most cheaply move the verdict (KS-6 / OQ-niche). **Operator
  action — cannot be agent-run.** Report findings into A1.

## Answered

- **INB-8 · answered 2026-06-18** — Ratify ADR 0007 + 0006. **→ Accepted
  both** (sweep). Prototype scope locked.
- **INB-7 · answered 2026-06-18** — ADR 0006 (Event Hubs). **→ Accepted.**
  Design cleared to become binding spec at C1b.
- **INB-6 · answered 2026-06-18** — ADR 0005 (14+ minor accounts).
  **→ Direction ratified, ADR stays Proposed pending counsel** on the
  age-gate mechanism + Maryland Kids Code DPIA trigger (legal = never
  agent-decided, guardrail #1). Adults-only remains in force until counsel
  confirms.
- **INB-5 · answered 2026-06-18** — Loop start. **→ Confirmed**, but the
  immediate next item is now **A8 (hi-fi mockups, ADR 0008)**, which gates
  A3. Loop iteration 1 = A8.
- **INB-4 · answered 2026-06-18** — Pricing direction. **→ Acknowledged;
  deferred to B6** (lean annual ~$39-59/yr when set).
- **INB-2 · answered 2026-06-18** — MVP scope guardrails. **→ Ratified**
  (adults-only / no-Gmail-OAuth / plain deep-links; ADR 0004 + 0007).
- **INB-1 · answered 2026-06-18** — Validation verdict & direction.
  **→ Accepted:** proceed building the dogfood prototype as a learning
  artifact; business path stays NO-GO until a flip-condition/niche is
  evidenced.
