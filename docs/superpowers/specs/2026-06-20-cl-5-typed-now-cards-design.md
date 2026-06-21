# CL-5 — Client UI: 6 typed Now cards (design)

**Epic:** `planning/content-detail-epic.md` · **Design:** `designs/content/
Content-Library.dc.html` (signed off, INB-16) · **ADR:** 0008/0009 (M3E, WCAG-AA)
/ 0020 (read-only M0) / 0022 (typed content) · **Depends:** CL-0 theme (done),
CL-4 typed `Card`/`Payload` (done) — both on `cl-next`.

## Goal

Render the six Now-card layouts in-brand, light + dark, dispatched by `card.type`,
emitting inline-action + open-detail **intents** via a callback. Snapshot-verify.

## Design source (per-type, from the signed-off mockup)

Shared card chrome (all types): **accent tile** · **kicker chip** (type/urgency)
· **title** · **body** · **primary action pill** · **provenance chip** ·
**privacy chip** where present. Per-type (icon/accent/kicker/primary from the
mockup's `data-dc-script`):

| type | accent (→role) | kicker | primary action | distinct |
|---|---|---|---|---|
| file | teal → secondary | FILE | Open → `OpenUrl(docRef)` | — |
| link | violet → tertiary | LINK/FORM | Open form → `OpenUrl(url)` | — |
| invite | coral → primary | INVITATION · RSVP | Details → `OpenDetail` | `primaryContainer` bg + **Yes/No display row** |
| contact | teal → secondary | CONTACT | Call → `Call(phone)` | avatar monogram + inline **Call/Text** buttons |
| geo | teal → secondary | OUTING | Navigate → `Navigate(addr)` | **map strip** (stylized placeholder) |
| email | violet → tertiary | EMAIL | Reply → `Email(mailto)` | — |

Accents map to **MaterialTheme color roles** (not raw light hex) so dark mode is
correct: teal=`secondary`, violet=`tertiary`, coral=`primary` (+ their
containers). Honors ADR 0009 (theme is the single source; CL-0 already encodes
the Dayfold hex + dark equivalents).

## Decisions / scope cuts (M0)

- **No icon dependency.** CL-0 deferred the Material-Symbols-vs-`compose.material.
  icons` choice; pulling icons mid-slice risks APK bloat / build churn. Use
  **accent tiles + text labels** (Call/Text pills, monogram tiles) — theme-driven,
  snapshot-stable, **accessible** (text > icon-only). Material-Symbols fidelity =
  a noted follow, folded into the queued **CL-0b** font work.
- **Kicker is derived, not stored.** CL-1 did not add a `kicker` field. Derive a
  simple type/urgency label from `type` (+ a payload hint like `link.kind`). Rich
  date-relative kickers ("DUE THU") wait for a `kicker` field or date formatting —
  noted follow.
- **RSVP Yes/No is DISPLAY-ONLY** (M0 unidirectional, ADR 0020/0016): render the
  authored `payload.invite.rsvpState` as static highlighted chips. **No write
  path, no server-mutating control** (binding review finding). Primary action =
  `OpenDetail` (Reply handoff lands with detail in CL-6).
- **Actions emit intents, effects are next.** Cards build a `CardAction` and call
  `onAction(it)`. The **platform effect layer** (`expect/actual PlatformActions`:
  openUrl/call/message/navigate/share/copy) is the epic's separate cross-cutting
  shim — shared with CL-6's tap-to-detail wiring; **deferred to a CL-6
  prerequisite** so each shell wires it once. CL-5 ships the callback + the typed
  `CardAction` model (so "inline actions dispatch" holds at the commonMain layer).
- **Map = stylized placeholder** (CL-9 posture): a themed strip
  (`DayfoldExtendedColors.map*`), no SDK/key/position leak. Navigate handoff only.
- **tap → detail** = `onAction(OpenDetail(id))`; the detail screen + nav are CL-6.

## Files

- `cards/CardAction.kt` (new) — `sealed interface CardAction` (OpenDetail/OpenUrl/
  Call/Message/Email/Navigate/Copy/Share).
- `cards/TypedCards.kt` (new) — the 6 `@Composable` per-type cards + shared chrome
  (AccentTile, KickerChip, PrimaryActionPill, ProvenanceChip, PrivacyChip) + a
  `TypedCardItem(card, onAction)` dispatcher on `card.type`.
- `FeedScreen.kt` — dispatch: `card.type != null` → `TypedCardItem`; else the
  existing generic `CardItem` (kind-only/back-compat). Thread
  `onAction: (CardAction)->Unit = {}` (default no-op; snapshots use default).
- `FeedApp.kt` — pass `onAction = {}` for now (TODO: wire PlatformActions in CL-6).
- `FeedSnapshotTest.kt` — add a typed-feed snapshot (all 6 types) light + dark.

## Security / privacy / a11y

- Read-only (ADR 0020): no write path; RSVP display-only.
- Actions are a **closed typed union** (`CardAction`) — no freeform URLs in the UI
  layer; scheme vetting stays in the effect layer (CardRender `ALLOWED_SCHEMES`
  reused in CL-6). Card composables never construct arbitrary intents.
- **No OG/favicon fetch** (link card): show domain text + accent tile only.
- **Privacy chip honesty:** render `payload`-adjacent `privacy.storage` verbatim
  via the privacy extended color; no claim the schema/boundary doesn't back.
- **WCAG-AA:** ≥48dp touch targets (Call/Text/primary pills), text labels
  (TalkBack/VoiceOver legible), AA contrast (esp. coral-on-coral invite — use
  `onPrimaryContainer`). reduced-motion N/A (no animation at CL-5; CL-7 owns it).

## Test plan (`desktopTest`)

1. Snapshot a feed with one of **each of the 6 types**, light + dark (PNG to
   `build/snapshots/` for manual review; golden-diff is ADR 0019-deferred).
2. Snapshot the **invite** card showing each `rsvpState` (none/yes/no) renders the
   right highlighted chip (display-only).
3. A pure unit test on the dispatcher/derivation helpers (kicker text per type,
   accent per type, primary `CardAction` per type) — these are pure, fast,
   golden-stable (don't rely on PNG diffing).
4. Existing snapshots + `CardRenderTest`/`FeedScreenTest` stay green (kind-only
   cards still use the legacy `CardItem`).

## DoD

All 6 cards render in-brand light+dark matching the mockup layout; inline actions
emit the right `CardAction`; invite RSVP is display-only; tap emits
`OpenDetail`; `:client:desktopTest` green; Android + iOS-sim compile.

## Review-driven decisions (folded from the pre-impl adversarial review)

- **No write surface.** `CardAction` has no `add_to_hub`/RSVP/Save mutation; Now
  cards emit only the per-type primary + contact Call/Text handoffs + invite
  display-only RSVP. Confirmed compliant.
- **Forward-compat:** unknown non-null `type` → the dispatcher's safe generic
  `StandardCard` (null-safe payload reads via `?.`); `type==null` → legacy
  `CardItem`. Never crashes on a newer server type.
- **States CUT at M0 (explicit):** per-card **loading skeleton** (no async media
  on a Now card — payload is pre-decoded by CL-4; skeletons land with CL-6
  detail / CL-9 map), **urgent** variant (needs date logic — folds in with the
  derived-kicker date follow), **dismissed-on-answer** (a *server/feed* behavior —
  the author removes/updates the card; the client only renders current
  `rsvpState`). Empty/loading/error stay feed-level in `FeedScreen`.
- **Contact primary = Details** (`OpenDetail`), not a second "Call" — the inline
  Call/Text row already covers calling (avoids the duplicate-Call seen in the
  first snapshot).
- **a11y:** accent tiles + map strip are decorative (`clearAndSetSemantics{}`) —
  the kicker chip states the type in text; RSVP row carries a `contentDescription`.
- **i18n:** English-only at M0; derived kicker/label strings are centralized in
  `TypedCardLogic.kt`/the chrome composables (not hardcoded-by-accident).
- **onAction seam:** a `(CardAction)->Unit` callback for M0; CL-6 routes it
  through middleware (ADR 0013 Rule E), not a second event bus.

## Risks

- Monogram tiles / text labels diverge visually from the Material-Symbols mockup
  — accepted M0 (noted CL-0b follow); layout/accent/light-dark fidelity is the
  CL-5 bar, glyph fidelity is the follow.
- Derived kicker is coarser than the authored mockup strings — accepted (noted).
