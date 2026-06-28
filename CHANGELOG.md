# Changelog

All notable product, API, and feature changes. Intended for release notes.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Feed posture states** — four distinct "Now" feed states: loading, caught-up
  (no new cards), offline/error, and loaded. The "Your hubs are here" caught-up
  pill navigates to the Hubs tab.
- **Author-side auto-linkify** — `dayfold push` auto-links bare phone numbers and
  email addresses in every `body_md` field to tappable `tel:`/`mailto:` links before
  storing. A diff of changed fields is printed to stderr. `--no-linkify` opts out.
  Shared linkrules package (`packages/linkrules`) keeps CLI + client in sync.
- **Visual enrichment** (ADR 0036) — hub and briefing card `media` fields: `heroUrl`,
  `thumbnailUrl`, `heroFit`, `imageAlt`, `icon`, and `accentColor`. Block `link`/`document`
  may carry `thumbnailUrl`; block `contact` may carry `avatarUrl`. Images rendered via Coil3
  with accent harmonization. Icon glyph shown as tile fallback when no image loads.
  Image URLs restricted to an allowlist (currently `upload.wikimedia.org`).
- **`dayfold update`** — self-update command (ADR 0037); prompts to `brew upgrade dayfold`
  on stable installs. Throttled once-per-day nudge on `push`/`pull`.
- **Continuous edge channel** (ADR 0037) — every `main` merge produces a rolling
  `cli-edge` pre-release (`0.0.0-edge.<sha>`) installable alongside the stable channel.
- **CLI `delete` / `rm`** — remove a hub (cascades sections + blocks) or a card
  (`--card` flag). Flag order is position-agnostic.
- **CI bundle drift guard** — CI now rebuilds the committed Vercel bundle
  (`apps/api/api/index.js`) and fails if it's stale, preventing the silent stale-code
  prod outage pattern.
- **Schema-drift detector column-aware** — `db:check` now catches column-level
  drift (field renames, type changes), not just missing tables.
- **Card chevron + back-navigation polish** — `chevron_right` on card-detail actions;
  `arrow_back` unified across auth + settings screens.

### Fixed

- Android: `DAYFOLD_API` now defaults to prod so plain `assembleDebug` works on-device
  without extra env.
- Client: Wikimedia images load correctly (descriptive `User-Agent` per Wikimedia policy).
- Client: inline link taps route through `PlatformActions` (defense-in-depth, iOS compile fix).
- Client: accent harmonization memoized — `rememberAccentRoles` now actually caches.
- CLI: `push` id + file resolved flag-position-agnostically (flags may appear before or
  after the positional arguments).
- CLI: `delete` id resolved flag-position-agnostically.

### Security

- API: authz gate boundary hardened; legacy "god-mode" path removed.
- API: `classifyOrigin` CIDR boundaries locked (datacenter anti-phishing heuristic).
- API: `/sync` cursor validated against malformed + injection-shaped input.
- API: "restricted to nobody" hub visibility invariant enforced fail-closed (ADR 0030).
- Client: markdown link-scheme normalization guards XSS evasion patterns.
- Client: `vettedOpenUri` evasion-resistance (inline-tap defense-in-depth).
- Client: geo `percentEncode` injection-resistance for the Navigate action.
- Client: image URL allowlist tested against parser-differential bypass attempts.

---

## [0.0.0-M0] — 2026-06-19

First end-to-end working slice. Google sign-in → CLI device login → author a hub → it
renders on Android. Validation round 1 verdict: **CONDITIONAL — learning-lab GO,
standalone-business NO-GO**.

### Added

**Content API** (`apps/api` — TypeScript, Hono, Postgres on Vercel + Neon)
- Hub + briefing card CRUD via `PUT /families/:fid/hubs/:id` and `PUT /families/:fid/cards/:id`
- Keyset sync (`GET /sync/keyset`) for offline-first client pull
- Per-member hub + card visibility (ADR 0030): `family` or `restricted` hubs;
  `audience[]` on cards; omit-not-403 read filter
- Resource-scoped credential grants per hub (ADR 0029)
- Body-size cap (1 MB) as DoS floor
- `/health` liveness endpoint (no auth, no DB)
- Vercel cron sweep (`/cron/sweep`) to clean expired auth ephemera

**Auth epic** (S1–S6, ADR 0021)
- S1: EdDSA tokens (Ed25519, `jose` library), per-request tenancy enforcement, refresh rotation
- S2: Firebase ID-token verify via JWKS (no Admin SDK); Google + Apple sign-in only (ADR 0023)
- S3: CLI device grant RFC 8628 — user_code + QR display + polling + owner approve
- S4: Owner-approved family invites
- S5: Sign-in / sign-out / account flows
- S6: Member roster, connected devices, profile export, account soft-delete

**CLI** (`apps/cli` — Kotlin, JDK-only HTTP)
- `dayfold login` — RFC 8628 device grant with QR code (macOS Keychain storage)
- `dayfold logout` — revoke credential + clear token
- `dayfold push` — PUT hub/card/section/block with auto-linkify + pre-validation
- `dayfold pull` — GET full content or single hub tree
- `dayfold template` — starter JSON for any content type
- `dayfold validate` — local schema + media validation (no network)
- `dayfold whoami` — show identity + resolved scope grants
- Distribution via Homebrew tap `sloopworks/tap/dayfold` (ADR 0031)

**Android client** (`apps/client` + `apps/androidApp` — Compose Multiplatform)
- True KMP module (`commonMain` shared UI + logic; `androidMain` / `desktopMain` actuals)
- Redux-kotlin store (one immutable `AppState` tree)
- SQLDelight offline cache — network → DB → store → UI unidirectional (ADR 0020)
- Feed screen: briefing card list with icon, accent chip, thumbnail fallback ladder
- Hub detail: adaptive layout (phone single-pane, tablet two-pane); block rendering
  (text, markdown, link, checklist, document, milestone, contact, location, budget)
- Auth screens: Google sign-in, device-grant QR scan, family invite, member roster,
  connected devices + revocation
- Material 3 Expressive color system with adaptive light/dark + accent harmonization
- Debug drawer: redux devtools (actions, state diff, time-travel) + fake backend for
  UI scenario testing without a live server

**Content schema** (`packages/schema`)
- `content.schema.json` → generated TypeScript (Zod) + Kotlin types via `npm run codegen`
- Hub types: `vacation | starting-college | move | party-event | new-baby | medical | school-year`
- Card types: `file | link | invite | contact | geo | email`
- Block types: `text | markdown | link | checklist | document | milestone | contact | location | budget`

**CI** (`.github/workflows/`)
- `ci.yml`: API (vitest + live Postgres), CLI (Gradle), client (headless Compose desktop tests),
  debug drawer, Android `assembleDebug` smoke, codegen drift guard, naming guard (ADR 0026)
- `firebase-emulator` job: real Firebase Auth Emulator for S2 integration tests (ADR 0027)
- `release-cli.yml`: tag-driven stable release + Homebrew formula bump
- `release-android.yml`: Play Store pipeline (stable / beta / alpha internal tracks)
- `migrate.yml`: manual database migration runner

**Database migrations** (0001–0013)
- M0 schema init → auth tables → visual enrichment columns
- Tracked migration runner with schema-drift detector (ADR 0033)
