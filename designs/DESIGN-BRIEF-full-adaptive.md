# Design Brief / Prompt — Dayfold Full Adaptive Layouts (Phone → Tablet → Desktop)

**Hand this whole file to a fresh Claude Code (Claude Design) session.** It is
self-contained. Inherits the brand, tone, and the M3-Expressive design system
already established — **read these first and reuse them; do NOT invent a new
system, colors, type, or motion:**

- `adr/0009-design-system-m3-expressive-adaptive.md` — the design system + adaptive intent.
- `adr/0008-design-first.md` — the gate this output satisfies (mockups before build).
- `adr/0013-*` — navigation is `f(state) → UI` (no nav library); design must be expressible as state.
- Existing comps to reuse as the source of truth for tokens/type/shape/motion/components:
  `designs/Family AI dashboard design brief/designs/Design-System.dc.html`,
  `Now-Phone.dc.html`, `Hubs-Phone.dc.html`, `Detail-Phone.dc.html`, `States-Feed.dc.html`,
  `Settings-Phone.dc.html`/`Settings-Adaptive.dc.html`, `Enrichment.dc.html`,
  `designs/content/adaptive/Breakpoints.dc.html` + the two-pane content comps.

This brief **supersedes the piecemeal adaptive work** by asking for ONE coherent,
**every-surface × every-breakpoint** adaptive system — phone, tablet (portrait +
landscape), and **desktop/large-screen** — so the Compose Multiplatform app has a
single signed-off responsive spec it maps to 1:1.

---

## 0. How to run this

> **You are designing the complete adaptive (responsive) UI/UX for Dayfold across
> Compact / Medium / Expanded window classes**, light **and** dark for every
> frame. Use the `frontend-design` skill. Produce **interactive HTML/CSS** that
> faithfully emulates **Material 3 Expressive** (color roles + tonal palettes,
> expressive type scale, shape system, and the **MotionScheme** spring tokens).
> **Reuse the existing Dayfold tokens/type/shape/components** — extend, don't
> replace. **Name every component after its M3 Compose equivalent** so it maps
> 1:1 to Compose Multiplatform (esp. `NavigationSuiteScaffold`,
> `ListDetailPaneScaffold`, `SupportingPaneScaffold`). Visuals only — no app logic.

**Calm = behavioral restraint, vibrant pixels.** Dayfold is a *briefing* surface,
not a dashboard of widgets. Larger screens get **more breathing room and a second
pane of context**, never more density, badges, or engagement bait.

---

## 1. Window-size classes (the spine)

Use the WindowSizeClass breakpoints; design all three, with the listed nav + pane behavior.

| Class | Width | Devices | Navigation (M3) | Panes |
|---|---|---|---|---|
| **Compact** | < 600dp | phone portrait | **Bottom nav bar** (Now / Hubs) | single pane; detail = full-screen push |
| **Medium** | 600–840dp | tablet portrait, large foldable, small desktop window | **Navigation rail** (icons + labels) | `ListDetailPaneScaffold` — list + detail side-by-side when a detail is selected |
| **Expanded** | ≥ 840dp | tablet landscape, **desktop**, large foldable open | **Navigation drawer** (or permanent rail + header) | list + detail **always** two-pane; optional **third supporting pane** (related / audience / enrichment) |

Rules:
- **One nav model expressed three ways** — the same destinations (Now, Hubs, +
  the account entry) reflow between bottom bar → rail → drawer via
  `NavigationSuiteScaffold`. Show the transition, not three unrelated chromes.
- **Content max-width** at Expanded — don't let a briefing card stretch to 1600px;
  cap line length, center the column or use the freed space for the supporting pane.
- **Fold/posture (foldables):** tabletop + book postures — list above/left of the
  hinge, detail below/right; avoid content under the hinge.

## 2. Surfaces to design — EVERY surface at EVERY class (light + dark)

For each, show Compact / Medium / Expanded, and call out what the extra width buys:

1. **Now feed** — the daily briefing list. Expanded: wider gutters + a persistent
   right **"context" pane** (e.g. today's hubs / what's next), NOT more cards.
   Include the **four posture states** (caught-up / first-run / syncing / offline,
   per `States-Feed.dc.html`) at each class.
2. **Hubs** — list → hub detail (hero banner + sections + blocks). Medium/Expanded:
   hub list (left) + hub detail (right) via `ListDetailPaneScaffold`; Expanded adds
   a **supporting pane** for the **per-member audience** (ADR 0030 "who can see this")
   and/or related cards.
3. **Content / card detail** — the typed-card detail + the fold-gesture interplay
   (`Detail-Phone.dc.html`). On phone the card grows to full-screen; at Medium+ it
   opens in the detail pane (list stays).
4. **Visual enrichment** (ADR 0036) — hero banner, accent kind-chips, curated-icon
   fallback tile — show how the hero scales (collapsing-capped) across classes.
5. **Account / Settings** — Account + Family panes (`Settings-Adaptive.dc.html` is
   the seed). At Expanded use a **two-pane settings** (categories left, detail right).
6. **Auth** — sign-in, create-family, join-by-invite, splash. Centered card that
   scales gracefully to a wide window (don't stretch full-bleed).
7. **Empty / loading / offline / error** at each class (not just phone).

## 3. Material 3 Expressive — best practices to apply (and show)

- **Expressive type scale** — the larger emphasized display/headline roles for hero
  moments (hub title, "You're all caught up"); body roles stay calm/legible.
- **Shape system** — expressive corner families; the larger/`*-expressive` shapes on
  hero/primary surfaces, restrained on dense list rows. Show shape morph on selection.
- **Color roles** — primary/secondary/tertiary containers used semantically
  (accent enrichment is decorative-only, harmonized to the theme); AA contrast
  (watch coral-on-coral, ADR 0009 / a11y).
- **MotionScheme (spring tokens)** — use M3 Expressive's spatial vs effects springs;
  specify which token per interaction (don't hand-tune durations).

## 4. Animations & transitions — specify, don't just decorate

For each, name the M3 motion + the Compose API a dev would reach for:
- **Pane enter/exit** — list→detail and the `ListDetailPaneScaffold` pane animations
  (predictive-back aware); the supporting pane sliding in at Expanded.
- **Nav reflow** — bottom-bar ↔ rail ↔ drawer as the window resizes (the
  `NavigationSuiteScaffold` crossfade/reflow) — show an in-between frame.
- **Shared-element / container transform** — card → its detail (a briefing card
  expanding into the detail pane); hub row → hub hero.
- **List item entrance** — staggered reveal on first paint (animation-delay), once,
  calm.
- **State transitions** — syncing-skeleton → loaded feed; caught-up glyph settle.
- **Predictive back** (Compose `PredictiveBackHandler`) at every depth — the back
  gesture must preview the destination at all classes.
- Restraint: **one well-orchestrated load** beats scattered micro-interactions;
  honor reduced-motion.

## 5. KMP / Compose Multiplatform specifics

- **Map to Compose, not CSS.** Name `NavigationSuiteScaffold`,
  `ListDetailPaneScaffold`, `SupportingPaneScaffold`, `WindowSizeClass`,
  `AnimatedPane`, `SharedTransitionLayout`, `MotionScheme`, `PredictiveBackHandler`.
- **`f(state) → UI` (ADR 0013):** the window class + selected list/detail must be
  derivable from redux state (e.g. a `WindowClass` + `selectedId` in `AppState`) —
  design nothing that can't be expressed as state. Note the new state fields a dev
  would add per surface.
- **Per-platform adaptation, one layout:** the same composables run Android /
  desktop (JVM) / iOS / **web (wasmJs)** — see `OQ-web-target`. Flag anything that
  must degrade per platform (e.g. hover affordances only where a pointer exists;
  desktop window-resize is continuous vs. device rotation discrete; **web has no
  bottom-nav convention** — at Compact-on-web prefer a top bar / rail).
- **Bundled fonts** (Outfit/Figtree via Compose Resources) — design only with those.
- **Input model:** show **pointer/hover + keyboard-focus** states for Medium/Expanded
  (desktop/web), in addition to touch — selection, hover elevation, focus ring.

## 6. Future direction — design for **two-way data transfer** (forward-compatible, not built)

Dayfold today is **render-side**: content is authored externally (CLI / Claude
skill) and the app renders it; the app does not yet write back. A near-future
direction is **two-way** — the family acts *in* the app and data flows back to the
server. **Design the layouts so these affordances slot in without a redesign**
(show them as a clearly-labeled "future" layer / variant, not shipped chrome):

- **Lightweight interactions that write back:** tap-to-toggle a checklist item,
  **RSVP** inline on an invite card, dismiss/snooze a briefing card, mark a
  milestone done — show the affordance + its optimistic/pending/confirmed +
  conflict states.
- **In-app authoring/edit (heavier):** an edit affordance on a hub block / card
  (the operator authoring from the app, not only the CLI) — where the entry point
  lives at each class (FAB on Compact? toolbar on Expanded?), and the
  **propose→confirm** posture the curator guardrails imply.
- **Real-time / multi-member sync:** with two-way writes, multiple members edit —
  show how a **presence / "updated by"** indicator and a **sync/offline-write
  queue** state would surface (esp. the Expanded supporting pane).
- **Server round-trip states everywhere a write can happen:** idle → pending →
  saved → error-with-retry (reuse the calm offline/retry language, never alarming).
- Keep these **non-dominant**: the briefing-first calm must survive the addition of
  write affordances — they appear on intent, not as persistent calls-to-action.

## 7. Output & definition of done (ADR 0008 / design-first)

- Commit the comps to `designs/content/adaptive/` (extend the existing set),
  light **+ dark**, every surface × {Compact, Medium, Expanded}, plus the key
  **transition** frames (nav reflow, pane enter, card→detail container transform).
- A short **notes block** per the project convention:
  1. the **M3 → Compose component mapping** (which scaffold/API per region),
  2. the **new `AppState` fields** a dev adds (window class, selected id, pane focus),
  3. the **two-way "future" layer** called out separately from the shipped layer,
  4. any **per-platform degradation** notes (web/desktop pointer + no-bottom-nav).
- **Operator signs off → then build** re-routes the surfaces through
  `NavigationSuiteScaffold` + `ListDetailPaneScaffold` driven by `WindowSizeClass`.
