# Designs

Hi-fi UI/UX mockups. **Design-first gate (ADR 0008):** every surface gets a
high-fidelity mockup here, authored with Claude Code (`frontend-design`
skill), **before** deep planning or build of that surface — operator-approved.

**Design system: Material 3 Expressive, adaptive** (ADR 0009) — vibrant /
full-expressive visuals, calm behavior. Phone gets full hi-fi; tablet /
foldable / desktop / Wear OS get adaptive specs + one frame each.

➡ **The brief / prompt to produce these: [`DESIGN-BRIEF.md`](DESIGN-BRIEF.md)**
— hand it to a fresh Claude Code session.

Layout: one subfolder per area, holding viewable artifacts (HTML/CSS
prototypes; the brief maps components to M3 Compose names).

| Area | Folder | Status |
|---|---|---|
| Design system (tokens, type, shape, motion, components) | `design-system/` | not started — **build first** |
| Now (briefing cards) — light + dark | `now/` | not started |
| Hubs (list, detail w/ all blocks, deep-link state, fallback) — L+D | `hubs/` | not started |
| Adaptive (tablet / foldable / desktop specs + 1 frame each) | `adaptive/` | not started |
| Wear OS (tile + complication) | `wear/` | not started |

Mockups are living references, not contracts — once a surface is built, the
Compose code is the source of truth for its look.
