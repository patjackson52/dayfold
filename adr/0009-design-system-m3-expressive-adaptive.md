# ADR 0009: Design System — Material 3 Expressive, Adaptive, Compose

## Status

**Accepted** 2026-06-18 (operator instruction, in-session). Immutable —
supersede, do not edit. Platform/design-system decision.

## Context

ADR 0008 mandates hi-fi mockups before build. The operator chose the design
language: **Material 3 Expressive (M3E)** — its component set, expressive
motion, shape scale, and tonal color system — with **adaptive** layouts
across phone, tablet, foldable, desktop, and Wear OS. This fits the chosen
stack (Compose Multiplatform + Wear Compose), where M3E ships as first-party
components, so the design language and the implementation language are the
same system end-to-end.

## Decision

1. **Design system = Material 3 Expressive.** Use M3E tokens (color roles,
   tonal palettes), the expressive type scale, shape scale, elevation, the
   M3E component set, and expressive/spring motion + emphasized easing.
2. **Brand expression = vibrant / full-expressive visuals, calm behavior.**
   Lean into M3E's bolder color and motion. The constitution's "calm" is a
   **behavioral** promise (no engagement-bait, no notification spam, doesn't
   compete for attention) — NOT a muted palette. Vibrancy lives in color,
   shape, and motion; restraint lives in interaction and notification
   frequency. A vibrant brand seed generates the tonal palette; **honor
   Android dynamic color** where present.
3. **Adaptive across window size classes** per Material adaptive guidance:
   phone → tablet → foldable (hinge-aware dual-pane) → desktop, using
   canonical layouts (list-detail / supporting-pane / feed) and adaptive
   navigation (bottom bar → rail → drawer). **Wear OS** is designed in its
   own paradigm (Wear Compose: tiles, complications, glanceable), not a
   shrunk phone UI.
4. **Mockup coverage now:** **phone full hi-fi**; tablet/foldable/desktop/
   Wear as adaptive layout specs + one representative frame each (matches the
   phone-first prototype, ADR 0007). Build remains phone-first; the system is
   defined adaptively so later form factors need no re-foundation.
5. **Accessibility is non-negotiable:** WCAG-AA contrast, ≥48dp touch
   targets, dynamic type, motion-reduction support.

## Rationale

M3E gives a complete, opinionated, accessible, well-documented system the
solo dev gets largely for free in Compose — minimal bespoke design debt, and
the mockups map directly to real components. Adaptive-by-design future-proofs
the form-factor expansion the operator wants without committing build effort
now. "Vibrant visuals / calm behavior" resolves the apparent tension with the
constitution.

**Rejected:** a fully bespoke design language (rejected — design debt, worse
component coverage, slower for a solo dev); muting M3E to "quiet" (rejected —
operator chose vibrant; calm is behavioral, not chromatic).

## Consequences

Positive: fast, consistent, accessible UI; mockups ≈ real components; adaptive
foundation; expressive brand within a trusted system.
Negative: M3E reads "Google/Android-native" — on iOS it will look Material,
not Cupertino (accepted: one cohesive cross-platform brand, per ADR 0004);
must actively police "vibrant ≠ loud/engagement-baity" against the
constitution; Wear OS is a genuinely separate design effort when built.

## Revisit Trigger

iOS users reject the Material look badly enough to warrant per-platform
chrome; or M3E's expressiveness is found to erode the calm promise in
practice (dial back via the brand-expression knobs).
