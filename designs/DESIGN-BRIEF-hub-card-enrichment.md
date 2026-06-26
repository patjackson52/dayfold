# Design Brief / Prompt — Hub & Card Visual Enrichment (hero images, icons, accent color)

**Hand this whole file to a fresh Claude Code (Claude Design) session.** It is
self-contained. Authoritative sources: `../adr/0009-design-system-m3-expressive-adaptive.md`,
`../adr/0006-event-hubs-surface.md`, `../adr/0008` (design-first),
`../specs/event-hubs-design.md`, `../specs/hub-card-visual-enrichment-design.md`,
and the existing system + Now/Hubs mockups in `Family AI dashboard design brief/`.

---

## 0. How to run this

> **You are designing the hi-fi UI/UX for visual enrichment** of Dayfold Hubs
> and briefing cards — hero images, list thumbnails, a small named-icon set, and
> a single accent color per Hub/card. Use the `frontend-design` skill. Produce
> **interactive HTML/CSS prototypes** that faithfully emulate **Material 3
> Expressive** (reuse the tokens/type/shape/motion/components from the existing
> `Design-System` mockup — do NOT invent a new system). Mobile-first
> (~390–430px), **light + dark** for every screen. Map every component to its
> **M3 Compose** name (the build targets Compose Multiplatform). Commit to
> `designs/hub-card-enrichment/`. Visuals only — no app code.

## 1. Context (what this feature is)

Dayfold renders a calm daily **briefing** (cards) + **Hubs** (event dossiers:
vacation, starting-college, move, party, new-baby, medical, school-year). Today
they're visually flat — title, status chips, emoji glyphs, no imagery. This
feature adds **imagery + color identity** so a family grasps a Hub at a glance.

**The headline use case:** a "starting-college" Hub shows the **university
logo/mascot** as a hero image — in the **Hub list row** (small) *and* at the
**top of Hub detail** (large). Cards/blocks get lighter cues (a named icon in an
accent color, an optional thumbnail/avatar).

Enrichment is **author-supplied** (a Claude skill sets explicit image URL / icon
/ color); the app **renders** it. Many Hubs/cards will have **nothing set** —
the unenriched state must look intentional, never broken.

## 2. Brand & tone (inherit ADR 0009)

Vibrant, expressive **visuals**; calm **behavior**. Warm, human, not childish.
Provenance on AI content ("added by Claude"). Imagery should feel curated and
trustworthy, never stock-photo noise. The enrichment must **enhance legibility
and recognition**, not decorate for its own sake.

## 3. The core design problems to solve (call these out explicitly)

These are the hard parts — show your answers as designed states:

1. **Logo vs photo fit.** A wide university wordmark and a square mascot and a
   landscape vacation photo all arrive through one image field. A blind
   cover-crop ruins logos (cuts them off / floats transparent PNGs on nothing).
   Design **two fits**: `cover` (photos, edge-to-edge crop) and `contain` (logos
   — letterboxed on an **accent-tinted background** with padding). Show both in
   list thumb AND hero header.
2. **The fallback ladder, as designed states (not placeholders).**
   `image → icon+accent tile → plain default`. The **icon+accent tile** is a
   deliberate, attractive full-bleed tile (centered icon on the Hub's accent
   color), identical in size/shape to an image thumb so a list of mixed
   enriched/unenriched Hubs keeps a clean rhythm. Loading, error, and
   "nothing set" should be visually consistent — a failure must be invisible.
3. **Accent color, used safely.** One brand hex per Hub/card. At MVP it tints
   **decorative surfaces only** — card border/left-edge, the icon tile, chips,
   the hero scrim — **never body text** (arbitrary brand hex on text fails
   contrast and WCAG 1.4.1). Show how a saturated, a near-white, and a near-black
   accent each look on **light and dark** without breaking. Meaning is always
   carried by icon + text, color only reinforces.
4. **Hero header that stays legible & calm.** Hero image at top of Hub detail:
   height-capped (≈16:9 but not dominating a small phone), **collapsing** to an
   accent-tinted top app bar on scroll, edge-to-edge **under the status bar**
   (light status-bar icons), with a bottom **gradient scrim** strong enough to
   keep a white title readable over a busy or pale image.

## 4. Screens & states to design

**A. Hub list (`HubListScreen` / `HubRow`)**
1. A Hub row **with a photo** thumbnail (e.g. vacation) — fit `cover`.
2. A Hub row **with a logo** thumbnail (e.g. starting-college university logo) —
   fit `contain` on accent tint.
3. A Hub row **with no image** → the designed **icon+accent tile** (e.g. a
   medical Hub with a health icon on its accent).
4. A **full list** mixing all three + status chips + countdown, showing the rhythm
   holds. Show loading (shimmer in the fixed slot) and a failed-image row
   (degrades to the icon tile, indistinguishable from "no image").

**B. Hub detail (`HubDetailScreen`)**
5. **Hero header — photo** (cover), expanded, with scrim + title + back; and its
   **collapsed** state (accent-tinted app bar).
6. **Hero header — logo** (contain on accent bg), expanded + collapsed.
7. **Hero header — none** → an accent/icon header treatment (no broken image).
8. The **scrim stress test**: title legible over a busy photo AND over a pale/white
   logo background.

**C. Cards & blocks (`FeedScreen`, `HubBlockCard`)**
9. A **briefing card** with `media.icon` + accent on the kind chip + optional
   leading thumbnail.
10. A **link / document block** with a preview `thumbnailUrl` (fixed-aspect).
11. A **contact block** with an `avatarUrl` (photo) vs the existing **initials
    avatar** fallback — side by side.

**D. The icon set (curated, small)**
12. Design a **starter set of ~12–20 named icons** that cover the Hub catalog +
    common content (e.g. school, vacation/luggage, medical, move/home, party,
    baby, calendar, location, link, document, contact, budget/dollar). Show them
    as the icon-tile glyph and inline. M3-consistent line weight. (The client
    ships **no icon font** — these will be a small curated/bundled set, so keep
    the set tight and visually unified.)

**E. Cross-cutting**
13. **Light + dark** for every screen above.
14. The **accent-on-surfaces** system: one component sheet showing exactly which
    surfaces take the accent (border/edge, icon tile, chip, scrim) and which
    never do (body text, large fills).
15. **Empty/first-run:** a brand-new Hub with nothing enriched — looks
    deliberate and inviting, not unfinished.

## 5. Adaptive (specs + one frame each)

Phone gets full hi-fi. For tablet/desktop give a short note + one frame (hero
header has more room; list becomes a richer grid where thumbnails shine).
**Wear OS:** a Hub glance tile with the icon+accent tile is the ideal
glanceable — include one concept. iOS parity assumed (Compose Multiplatform);
note any iOS-specific status-bar/edge-to-edge nuance for the hero.

## 6. Constraints (honor or call out)

- **Material 3 Expressive only** — reuse existing Design-System tokens/shape/
  motion; map components to M3 Compose names (`Card`, `LargeTopAppBar`/collapsing,
  `AsyncImage` slot, `Surface`, `AssistChip`, etc.).
- **Accent never on body text.** Decorative surfaces only. Prove contrast on
  light + dark for saturated / near-white / near-black accents.
- **Fixed-aspect image slots** (hero ≈16:9 capped, thumb 1:1) so there is **zero
  layout shift** while images load. Loading = shimmer in the exact slot.
- **a11y:** every meaningful image has visible-intent alt; icons paired with a
  label are decorative. RTL mirrors the leading slot. Respect dynamic type
  (title bounded + ellipsis, scrim band grows) and reduced-motion (no parallax).
- **Calm:** imagery enhances recognition; don't turn the briefing into a media
  feed. One hero per Hub; cards stay compact.
- **No invented data model.** Fields available: Hub `{heroUrl, thumbnailUrl,
  heroFit: cover|contain, imageAlt, icon, accentColor}`; Card `{icon, accentColor,
  thumbnailUrl, imageAlt, imageFit}`; block `thumbnailUrl`/`avatarUrl`. If you
  need another field, flag it — don't assume it.

## 7. Deliverables

- Interactive HTML/CSS prototypes in `designs/hub-card-enrichment/`, light+dark,
  ~390–430px, faithful M3 Expressive.
- A short **component map** (each new component → M3 Compose name + which
  `HubScreens.kt` / `FeedScreen.kt` insertion point it targets).
- The **curated icon set** as an asset sheet.
- A one-paragraph **accent-color usage rule** (the surfaces sheet from §4.14).
- Call out any field you needed that isn't in §6.

---

*When the hi-fi specs are ready, import them back; implementation (schema codegen
→ migration 0012 → Coil render → CLI templates → tests) proceeds against
`../specs/hub-card-visual-enrichment-design.md` + a Proposed ADR on the
image-URL privacy/allowlist posture.*
