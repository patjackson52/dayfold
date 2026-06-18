# Design Brief / Prompt — family-ai-dashboard

**Hand this whole file to a fresh Claude Code session to produce the hi-fi
mockups + design system.** It is self-contained. Authoritative decisions
live in `../adr/` (esp. 0004, 0006, 0007, 0008, 0009) and
`../specs/event-hubs-design.md`; this brief operationalizes them for design.

---

## 0. How to run this

> **You are designing the hi-fi UI/UX for family-ai-dashboard.** Use the
> `frontend-design` skill. Produce **interactive HTML/CSS prototypes** that
> faithfully emulate **Material 3 Expressive** (color roles, tonal palettes,
> expressive type scale, shape scale, elevation, expressive/spring motion).
> The build target is Compose Multiplatform, so treat these mockups as a
> visual spec: name things after M3 components (e.g. `FilledCard`,
> `NavigationBar`, `ExtendedFAB`, `AssistChip`) so they map 1:1 to Compose.
> Commit outputs to the folders in §7. Do not build app logic — visuals only.

## 1. Product (context)

A calm, AI-powered household dashboard. Adults log in (prototype is
adults-only). It renders a daily **briefing** and persistent **Event Hubs**
(rich event dossiers) — content is authored/curated externally (by the
operator via Claude Code) and pushed to the app; **the app is a dumb
renderer**. Two co-equal surfaces:

- **Now** — today's briefing: a short feed of cards (next actions, logistics).
- **Hubs** ("Projects") — persistent dossiers per family event (vacation,
  starting college, move, party, medical, school year).

Tapping a briefing card **deep-links into the exact Hub content** it refers
to (a section/block), which scrolls into view and is briefly highlighted.

## 2. Brand & tone

- **Vibrant, full-expressive visuals; calm behavior.** Lean into M3
  Expressive's bolder color, shape, and motion. "Calm" is behavioral (no
  engagement-bait, no notification spam, doesn't fight for attention) — NOT
  muted. Energy in pixels, restraint in interaction.
- **Honest & trustworthy.** Every AI-curated block shows **provenance**
  ("added by Claude", "from your email", "link you saved") — design a small,
  consistent provenance treatment (e.g. an `AssistChip` or caption row).
- **Family-warm, not childish.** Adults are the users; warm and human, never
  a kids' app, never gamified (no points/streaks/badges).
- Working **name/wordmark:** use a tasteful placeholder (e.g. "Hearth" or a
  simple wordmark) — easily swapped; product name is still TBD.

## 3. Design system (Material 3 Expressive) — deliver first

Produce a **design-system page** (`design-system/index.html`) before screens:

- **Color:** choose a **vibrant brand seed** (proposed: an energetic
  coral/persimmon ≈ `#FF5436` with a contrasting vibrant secondary, e.g.
  teal `#11B5A4`, and an expressive tertiary, e.g. violet — adjust freely).
  Generate full M3 tonal palettes + all color roles (primary/secondary/
  tertiary/surface/surface-container tiers/outline/error) for **light AND
  dark** (light is the hero). Note where Android **dynamic color** would
  override.
- **Typography:** M3 Expressive type scale (display/headline/title/body/
  label), an expressive display face + clean body face; show the ramp.
- **Shape:** M3 shape scale (none→extra-large), lean to the expressive/
  rounder end; show shape morph on key components.
- **Elevation & surface tiers:** surface-container tones (not just shadows).
- **Motion:** expressive spring + emphasized easing; specify durations/
  curves for card entry, deep-link highlight pulse, FAB, nav transitions.
- **Components inventory** (M3E): top app bar (incl. large/collapsing),
  navigation bar / rail / drawer (adaptive), `FilledCard`/`ElevatedCard`,
  list items, checkboxes, chips (assist/filter), buttons + button groups,
  FAB / Extended FAB, segmented buttons, snackbars, dialogs/bottom sheets,
  search, loading skeletons, empty states. Show each in light + dark.
- **Iconography:** Material Symbols; note weight/fill.
- **Accessibility:** WCAG-AA contrast on every role pairing; ≥48dp targets;
  dynamic-type behavior; reduced-motion variant.

## 4. Screens — FULL hi-fi for PHONE (light + dark each)

Mobile viewport (~390–430px). Deliver, at minimum:

**Now (briefing) — `now/`**
1. Briefing feed: greeting/header, then cards of varied types — an **action
   card with a deep-link** ("Party Sat — ordered groceries? [open list]"),
   an info/logistics card, a weather/context card, an event **countdown**
   card. Provenance on AI cards. Calm density, generous spacing.
2. Empty state ("nothing needs you right now").
3. Loading/skeleton state.

**Hubs — `hubs/`**
4. Hub list ("Projects"): cards per Event Hub with type icon, title,
   countdown, status (planning/active/archived). Include an empty state.
5. **Hub detail (the dossier)** — the centerpiece. A collapsing header with
   title + countdown, then sections containing **every block type** so the
   system is fully exercised: `text`, `link`, `checklist` (with due/assignee),
   `document` (link/small-file ref — show the "tap to open" affordance, NO
   in-app preview), `milestone`/timeline, `contact`, `location` (map
   thumbnail), `budget`. Provenance per block.
6. **Deep-link arrival state:** the Hub detail scrolled to a specific block
   with the **highlight/pulse** treatment active (this is what a tapped Now
   card lands on). Show the highlight clearly.
7. **Graceful fallback state:** target missing → landed on nearest ancestor
   with a quiet "that item moved" note.

**Chrome & states**
8. Adaptive **navigation**: phone = bottom `NavigationBar` (Now / Hubs).
9. A representative dialog/bottom sheet (e.g. a checklist item detail) to
   show overlay treatment.

## 5. Adaptive — SPECS + one frame each (not full sets)

Per Material adaptive guidance, show how the two surfaces reflow across
window size classes. For each, give a short spec + **one** representative
frame:

- **Tablet / medium width:** list-detail (Hubs list + detail side-by-side);
  navigation **rail**.
- **Foldable:** hinge-aware dual-pane; describe folded vs unfolded.
- **Desktop / expanded:** navigation **drawer** + multi-column; max content
  width.
- **Wear OS:** its own paradigm — a glanceable "next thing" **tile** + a
  complication; do NOT shrink the phone screen. One tile mock + notes.

## 6. Constraints (honor or call out)

- Two surfaces only (Now, Hubs). No settings/auth/onboarding screens beyond a
  minimal placeholder — prototype is a dumb renderer (ADR 0007).
- No kids UI, no gamification, no ad surfaces, no engagement-bait patterns.
- Don't invent data sources/integrations in the UI (no "connect Gmail"
  flows) — content "just appears" because it's pushed.
- Provenance is mandatory on AI-curated content.
- Light + dark for every phone screen.

## 7. Output structure (commit here)

```
designs/
  DESIGN-BRIEF.md            (this file)
  design-system/index.html   (tokens, type, shape, motion, components — L+D)
  now/                       (briefing feed, empty, loading — L+D)
  hubs/                      (list, detail w/ all blocks, deep-link state, fallback — L+D)
  adaptive/                  (tablet, foldable, desktop specs + 1 frame each)
  wear/                      (tile mock + notes)
  README.md                  (index of what's here, update it)
```

Add a short `designs/index.html` or update `README.md` linking every screen
so the operator can click through. Keep mockups as living references — once a
surface is built, the Compose code becomes the source of truth.

## 8. Definition of done (gates board item A8 / ADR 0008)

- Design-system page complete (color L+D, type, shape, motion, components).
- All §4 phone screens in light + dark, clickable from the index.
- §5 adaptive specs + one frame each (incl. a Wear tile).
- Provenance + accessibility treatments visible and consistent.
- Operator can open the index and approve the look-and-feel.
