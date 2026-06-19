# Design Brief / Prompt — Adaptive (Two-Pane) Content Detail

**Hand this whole file to a fresh Claude Code (Claude Design) session.** It is
self-contained. Authoritative sources: `../adr/0022-typed-content-library-detail-
and-fold-gesture.md`, `../adr/0009-design-system-m3-expressive-adaptive.md`, the
existing phone detail in `Family AI dashboard design brief/designs/content/`
(`Detail-Phone.dc.html`, `Tap-To-Detail.dc.html`, `Content-Library.dc.html`),
and the existing adaptive frames in `designs/Adaptive.dc.html`.

This brief fills **one gap**: the phone content/detail/fold-gesture surfaces are
done and signed off; **the adaptive (tablet / foldable / desktop) two-pane
detail is NOT designed.** It blocks build task **CL-10** (`../planning/content-
detail-epic.md`). Design only this.

---

## 0. How to run this

> **You are designing the hi-fi UI/UX for the adaptive, larger-than-phone
> content experience** of family-ai-dashboard ("**Dayfold**"). Use the
> `frontend-design` skill. Produce **interactive HTML/CSS prototypes** that
> faithfully emulate **Material 3 Expressive** — reuse the existing Dayfold
> tokens / type / shape / **MotionScheme** / components from the
> `Design-System` + `content/` mockups. **Do NOT invent a new system, new
> colors, or new type.** **Light + dark** for every frame. Map components to M3
> Compose names (esp. `ListDetailPaneScaffold`, `NavigationSuiteScaffold`).
> Commit to `designs/content/adaptive/` and link from `designs/content/Index`
> and `designs/Adaptive.dc.html`. Visuals only.

## 1. Context (what already exists — reuse it)

A content item is **data + a type**; six types — **file, link, invite, contact,
geo, email** — each render three ways: a **Now card**, a **Hub block**, and a
**Detail**. On phone, tapping a card runs a **container-transform "fold
gesture"** (the card grows into a full-screen detail; ~460ms open / ~420ms back,
emphasized-decelerate / `MotionScheme.expressive()` spring). Detail anatomy is
constant: **hero (the content) → primary actions → DETAILS metadata → provenance
+ privacy → related**. All of that is designed for phone. **Your job is what
happens when the screen is wider than a phone.**

The brand metaphor *is* the motion: content is *folded away until it matters,
then unfolds*. Preserve that feeling at every size.

## 2. Brand & tone (inherit — do not restyle)

Dayfold: vibrant, calm, honest, family-warm. Warm coral primary (`#C0381E`),
teal secondary, violet tertiary, warm off-white/brown neutral ramp; Outfit
(display/title, tight tracking) + Figtree (body/label); 26dp card radius, 999
pills; warm-tinted elevation. Provenance + a truthful **privacy chip** on
content. No gamification, no engagement-bait.

## 3. The core question to answer (design it, don't dodge it)

**How does the fold gesture behave when a list and a detail are on screen at the
same time?** On phone the card grows to fullscreen. In a two-pane layout the
list stays put and the **detail pane** updates. Decide and show:

- Does selecting a card still **container-transform** *within the detail pane*
  (the card's twin grows to fill the pane), or does the detail pane do a softer
  **shared-axis / fade-through** content swap while the list persists? Pick one,
  justify it in a short note, and make it feel like "unfolding," not a hard cut.
- What's the **selected-card** treatment in the list pane (the source of the
  fold) — a persistent selected/elevated state synced to the detail.
- **Back / dismiss** at width: on compact it collapses the pane (predictive-back
  / iOS interactive-pop); on expanded, "back" clears the selection to the
  empty-pane state. Show both.

## 4. Frames & states to design (light + dark each)

**A. Breakpoints (the spine) — use `ListDetailPaneScaffold` + adaptive nav**
1. **Compact (<600dp) — phone, single pane.** This is the EXISTING design;
   include one reference frame showing the container-transform fold (no
   redesign — just anchor the continuum).
2. **Medium (600–840dp) — tablet portrait / large foldable.** **Navigation
   rail** + list-detail: Now feed (list pane) on the left, content detail
   (detail pane) on the right. Show a **selected** state and the in-pane
   transition.
3. **Expanded (≥840dp) — tablet landscape / desktop.** **Navigation drawer** (or
   rail) + list-detail with comfortable measure; the detail pane reflows the
   hero wider (see §5). Optionally a third **supporting pane** for **related /
   the parent Hub** — design it if it strengthens the dossier feel, else note
   why not.

**B. The six detail types in the detail pane (the payload of this brief)**
4. Each of **file, link, invite, contact, geo, email** in the **detail pane** at
   medium + expanded. The hero must **reflow, not just stretch**: e.g. **geo**
   gets a genuinely larger map; **email** gets a real reading measure for the
   body; **file** shows a larger page preview; **invite** can sit the date-block
   + RSVP beside the metadata; **contact** centers with the reach buttons. Keep
   the constant skeleton; show how each hero uses the extra width well.

**C. Empty / transitional / posture states**
5. **Empty detail pane** — nothing selected yet (expanded layout): a calm
   "Nothing to unfold yet — pick a card" placeholder, on-brand.
6. **Foldable dual-pane across the hinge** — tabletop / book posture: list on
   one half, detail on the other; how the fold gesture maps to the physical
   fold. One frame.
7. **Loading + offline** in the two-pane world — list loaded, detail
   loading/offline (skeleton in the pane, not a full-screen spinner).

**D. Navigation continuity**
8. The **NavigationSuite** transition: bottom bar (compact) → rail (medium) →
   drawer (expanded) hosting Now / Hubs / (Settings). Show the fold gesture's
   `SharedTransitionLayout`/scaffold nesting **above** the nav so it survives
   pane changes. (One annotated frame is enough.)

## 5. Reflow rules (call these out per type)

- The detail **skeleton is constant** across sizes; only the **hero** and the
  **column count** change. Single column on phone → hero + metadata can go
  **two-column** on expanded (hero left, DETAILS/actions/related right) where it
  reads better; specify per type.
- Primary **actions** stay reachable (don't bury them below a long body at
  width). **Related** rows become a sidebar candidate on expanded.
- Never let media (geo map, file preview, link OG) **block** the pane transition
  — placeholder first, fade in (mirror the phone perf rule).

## 6. Constraints (honor or call out)

- **Reuse** the Dayfold M3 Expressive system + the phone detail anatomy + the
  fold motion — extend, don't fork.
- **M3 Expressive specifics:** spring motion (`MotionScheme.expressive()`), not
  bezier; emphasized type for hero titles/countdowns; shape at the corners;
  expressive components (button group / split button for grouped actions, FAB
  menu where a type has multiple primary actions).
- **Honest privacy chip** stays truthful at width (same copy rules as phone —
  "Location never leaves" = *live position* only; saved coords are family
  content).
- **Inline mutations are M0-out:** invite **RSVP Yes/No is display-of-state**,
  not a write, in M0 (ADR 0020/0016) — show it, don't imply a server write;
  Call/Navigate/Reply are OS handoffs.
- **Web is not a build target yet** — design desktop as the "expanded" reference;
  don't design web-chrome-specific affordances.
- **a11y (ADR 0009):** AA contrast (watch coral-on-coral), ≥48dp targets,
  Dynamic Type, a `prefers-reduced-motion` path that swaps the pane transition
  for a plain fade.

## 7. Output structure (commit here)

```
designs/content/adaptive/
  Index.dc.html            (click-through index — link from content/Index + Adaptive)
  Breakpoints.dc.html      (A: compact / medium / expanded continuum, L+D)
  Detail-Pane.dc.html      (B: the 6 types in the detail pane, medium + expanded, L+D)
  States.dc.html           (C: empty pane, foldable dual-pane, loading/offline)
  Nav-Continuity.dc.html   (D: bottom bar → rail → drawer + scaffold nesting note)
```

## 8. Definition of done

- The fold-at-width behavior **decided and shown** (in-pane transform vs
  shared-axis swap), with the selected-card + back/dismiss states.
- All six detail types in the detail pane at medium + expanded, light + dark,
  with per-type reflow that uses the width well.
- Empty detail-pane, foldable dual-pane, and loading/offline pane states.
- Navigation continuity (bottom bar → rail → drawer) with the scaffold nesting
  annotated.
- Everything token/type/motion-consistent with the existing Dayfold system;
  honesty + M0-out-of-scope (RSVP write) constraints visibly satisfied; the
  operator can approve and it unblocks CL-10.
