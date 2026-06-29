# Changelog

Product, API, and feature changes — used as release notes for the app and CLI.
Format: newest first. Dates are ISO-8601.

## Unreleased (main / edge)

### Added
- **Predictive back** gesture on Android — swipe-to-dismiss card↔detail + history
  navigation goes up one level (#237)
- **Loading states** throughout the app — skeleton screens on cold start; skeleton
  dismisses only once the first sync completes; anti-flash guard (no skeleton on
  fast syncs < 200ms) (#223)
- **Feed posture states** — four distinct "Now" feed states: loading, loaded,
  caught-up (feed is clear + a "Your hubs are here" pill), and error (#209)
- **Author-side linkifier** — `dayfold push` auto-links bare phone numbers and
  email addresses in every `body_md` to tappable `tel:`/`mailto:` links; prints
  a diff; `--no-linkify` opts out (#196, `packages/linkrules`)
- **Hub/card visual enrichment** (ADR 0036) — `media` field on hubs, briefing
  cards, and hub blocks: `icon`, `accentColor`, `heroUrl`, `thumbnailUrl`,
  `avatarUrl`, `imageAlt`, `heroFit`; Wikimedia-only image allowlist (phase 1);
  hardened validator shared across API + CLI + client (#177)
- **Scope-gated credential grants** (ADR 0029) — `credential_grants` table;
  `requireScope` enforces content:read/write + per-hub `hub:<id>:read/write`
  on every API route; `dayfold whoami` prints resolved scope
- **Per-member hub & card visibility** (ADR 0030) — hubs can be
  `family` (default) or `restricted` (explicit allow-list); cards carry an
  author-stamped `audience[]`; sync emits tombstones for newly-restricted rows
- **`dayfold update`** — delegates to `brew upgrade dayfold`; also shows the
  latest stable version vs the running version; throttled once/24h update nudge
  after interactive `push`/`pull` (ADR 0037)
- **CLI Homebrew distribution pipeline** (ADR 0031/0037) — `release-cli.yml`
  builds `distTar` on every tag (`cli-v*`) and bumps the tap formula; `release-
  cli-edge.yml` pushes a rolling `cli-edge` pre-release on every `main` push
  touching `apps/cli/**`
- **Database migration tracker** (ADR 0033) — `schema_migrations` table (migration
  `0012`); `npm run db:migrate` applies pending migrations in order, one txn each,
  skips applied, re-run-safe; `npm run db:check` guards schema drift in CI
- **Android release pipeline** (ADR 0034) — `release-android.yml`; 3-track train
  (internal → beta → production draft); monotonic `versionCode`; upload-key signing
- **CI iOS actual guard** — `check-expect-actual.sh` catches a missing `iosMain`
  actual for any `commonMain expect` before the Gradle build (#226)
- **Vitest upgraded** 2 → 4 (clears critical + high dependency advisories) (#217)

### Changed
- **Block markdown rendering** memoized in composables — no re-parse per
  recomposition (#242)
- **Accent harmonization** memoized — `rememberAccentRoles` now actually
  remembers across recompositions (#193)

### Fixed
- Double-submit guards on sign-in, create-family, join-invite, device-authorize —
  busy state disables all action buttons to prevent concurrent racing ops (#229–#233)
- Inline link taps route through `PlatformActions` + `percentEncode` injection
  resistance; evasion-tested (#194, #205)

---

## 0.1.0-alpha — M0 Prototype Complete (2026-06-26)

First end-to-end working prototype. Google sign-in → CLI device login → author a
hub → renders on-device. Running on Vercel + Neon; Android renders on the Pixel 10.

### Content API (`apps/api`)

- **Content routes** — `PUT`/`GET` `/families/:fid/cards/:id` (briefing cards),
  `/hubs/:id`, `/sections/:id`, `/blocks/:id`; keyset sync at `/families/:fid/sync`
  (merged cards+hubs+sections+blocks cursor, tombstones for deletes)
- **Auth** (ADR 0011/0021) — EdDSA/RS256 tokens (backend-minted), rotating refresh
  with reuse-detection, revocation effective within one request; tenant-explicit
  middleware on every route; credential not-revoked checked per request
- **Firebase Google sign-in** (ADR 0023/0027) — `POST /auth/firebase` verifies
  Firebase ID tokens via direct JWKS (`jose`, no Admin SDK); Firebase Auth Emulator
  in CI
- **RFC 8628 device grant** (ADR 0021 S3) — `/device/authorize` + `/device/token` +
  owner approve/deny flow; QR code in CLI; `dayfold login` + `dayfold logout`
- **Invite flow** (S4) — owner mints QR/link invites; invitee redeems; owner
  approves/declines pending members; rate-limited (ADR 0025)
- **Account management** (S5/S6) — sign-in/out, profile display name, data export
  (`/auth/me/export`), connected devices (`/auth/me/credentials`), account
  soft-delete with last-owner invariant check, member removal
- **Typed content validation** — Zod schema (codegen from `content.schema.json`);
  cross-validates `type`↔`payload`; block payload validation (ADR 0035)
- **Hub tree** — 3-level tree (hub → section → block); structural pre-check in CLI;
  sections/blocks inherit hub visibility
- **14 SQL migrations** tracked by `schema_migrations` (ADR 0033); `npm run db:migrate`

### CLI (`dayfold` binary — `apps/cli`)

- **`dayfold login`** — RFC 8628 device grant with QR code; refresh token in OS
  keychain; `--allow-env-key` for headless/CI; auto-fetches family ID from
  `/auth/whoami`
- **`dayfold logout`** — revokes the session server-side; clears the keychain
- **`dayfold whoami`** — shows family, API, credential type, and resolved scope
- **`dayfold push <id> <file.json>`** — PUTs a briefing card (default) or hub tree
  node (`--hub`/`--section`/`--block`); `--type` runs local typed validation
- **`dayfold pull [--hub <id>]`** — reads cards+hubs, or one hub's full tree
- **`dayfold template <type>`** — prints a starter JSON body (card types:
  `file link invite contact geo email`; hub tree: `hub section block`)
- **`dayfold delete <id> [--card]`** — removes a hub (cascades sections+blocks) or
  a card (`--card`)
- **`dayfold update`** — `brew upgrade dayfold` or prints update instructions
- **`dayfold version`** — prints the embedded build version

### Client (`apps/client` — Compose Multiplatform)

- **Offline-first** (ADR 0020) — SQLDelight DB as source of truth;
  network→DB→store→UI unidirectional; instant cold-start from cache; foreground
  poll ~45s + sync-on-resume; crash-safe cursor in `sync_meta`
- **Feed** — typed card rendering (all 6 content types); lazy list; pull-to-refresh
- **Hub detail** — hub → section → block tree; 9 block types rendered; deep-link nav
  from cards (`target.hubId/sectionId/blockId`); fold gesture (container transform)
- **Auth UI** — sign-in (Google), create family, join invite (QR/link), member
  roster, connected devices, account settings, sign-out
- **Debug drawer** — floating bubble (debug builds only); redux-kotlin devtools with
  actions/state/diff/pipeline panels; fake backend for UI testing without server
- **KMP module** (TASK-KMP) — single Gradle root at `apps/`; `commonMain` holds all
  shared logic+UI+SQLDelight+ktor; Android + desktop + iOS targets

### Schema (`packages/schema`)

- `content.schema.json` — single source of truth for BriefingCard, Hub, Section,
  Block, and all payload types; codegen produces Zod (TS) + Kotlin data classes
- 6 card types: `file · link · invite · contact · geo · email`
- 9 block types: `text · markdown · checklist · link · document · contact ·
  location · milestone · budget`
- `media` fields (ADR 0036): `heroUrl · thumbnailUrl · avatarUrl · icon ·
  accentColor · heroFit · imageAlt`

### Infrastructure

- **Vercel + Neon Postgres** — API deployed to `family-ai-dashboard.vercel.app`;
  Vercel Cron for auth sweep
- **CI** — 6 jobs: API (vitest + Postgres), CLI (Kotlin), client (Compose headless),
  debug drawer, Android (assembleDebug smoke), naming-guard (ADR 0026), Firebase
  Auth Emulator integration test

---

## Planning & Architecture (2026-06-18 — Bootstrap)

*Not a software release — records the decisions that shaped the build.*

- **ADR 0004** — Product framing: calm family briefing surface, content-API-fed,
  adults-only MVP. Not a chatbot, not a chore app.
- **ADR 0006** — Event Hubs: co-equal curated-dossier surface; hub → section → block.
- **ADR 0009** — Design system: Material 3 Expressive, adaptive, Compose.
- **ADR 0011** — Auth & family-tenancy architecture (hardened): EdDSA tokens, RFC
  8628 device grant, per-request revocation, Firebase deduplication.
- **ADR 0013** — Client architecture: KMP/CMP + redux-kotlin 1.0.0-alpha01.
- **ADR 0018** — API host: TypeScript on Vercel; types codegen from JSON schema.
- **ADR 0022** — Typed content library: 6 card types, container-transform fold gesture.
- **Validation verdict** (round 1) — CONDITIONAL: learning-lab GO, standalone-
  business NO-GO; defensible wedge is multi-member family-tenant briefing.
