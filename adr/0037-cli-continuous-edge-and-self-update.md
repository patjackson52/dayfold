# ADR 0037: CLI Continuous "Edge" Channel + `dayfold update`

## Status

**Accepted** 2026-06-26 (operator-directed in-session: "Rolling edge on every main
push" + "`dayfold update` → brew + version nudge"). Extends **ADR 0031** (CLI
Homebrew distribution) — it does NOT supersede it; the tag-driven stable channel is
unchanged. Composes 0031/0018/0012. Runbook: `processes/cli-release.md`.

## Context

ADR 0031 ships the CLI via a first-party Homebrew tap, released on a manual
`cli-v<semver>` tag. Two gaps for a solo-operator + Claude-Code authoring loop:

1. **No continuous build.** Every CLI change needs a manual semver tag to become
   installable, so "latest `main`" is never trivially runnable for dogfooding.
2. **No in-CLI update path.** Brew users have `brew upgrade dayfold`, but there is
   no `dayfold update`, no "an update is available" signal, and nothing for a
   non-brew install.

These are process/maintenance decisions (ADR-class), so they are recorded here.
They do not widen any external-action/legal/spend guardrail; the public Homebrew
*install/upgrade* path still depends on ADR 0031's open operator gates (tap repo,
`HOMEBREW_TAP_TOKEN`, license) — this ADR adds channels that work without them.

## Decision

1. **Continuous "edge" channel** (`.github/workflows/release-cli-edge.yml`). Every
   push to `main` that touches `apps/cli/**` or `packages/schema/**` builds the dist
   (version `0.0.0-edge.<shortsha>`) and refreshes a single **`cli-edge` GitHub
   pre-release** with a **stable asset name** (`dayfold-edge.tar` → stable download
   URL). Only the built-in `github.token` (`contents: write`) is needed, so it runs
   immediately. **PRs do not publish** (never ship unreviewed code). `cli-edge` is a
   *pre-release*, so the GitHub `releases/latest` API ignores it.

2. **Stable channel unchanged.** `cli-v<semver>` tags remain the only thing that
   cuts a real GitHub Release and bumps the Homebrew tap formula (ADR 0031). The
   tap formula tracks **stable only**; edge is a testing artifact, never the tap.

3. **`dayfold update`.** Delegates to the ADR 0031 distribution: when the running
   CLI is **brew-managed** (its keg path is under a Homebrew Cellar/prefix), it runs
   `brew upgrade dayfold`; otherwise it prints the Homebrew install/upgrade
   instructions + the releases URL. It first reports the latest **stable** semver
   (from `releases/latest`) vs the running version. Dev/edge builds report as such
   and are never "downgraded".

4. **Throttled update nudge.** After an interactive `push`/`pull`, a fail-silent,
   **once-per-24h** check (TTY-only, skipped under `CI` or `DAYFOLD_NO_UPDATE_CHECK`)
   compares the running **stable** version to the latest release and prints a single
   `→ dayfold X is available … run \`dayfold update\`` line to stderr. Dev/edge
   builds are never nagged. The timestamp is written *before* the network call, so a
   slow/offline check never re-fires that day.

## Rationale

- **Edge as a moving pre-release** is the standard "nightly/latest" pattern: one
  stable URL, no release spam, no version churn in the tap, and `releases/latest`
  (stable) stays clean for the update check. Rejected: auto-cutting a semver patch
  tag per merge (version churn + needs the tap live to help anyone); per-commit full
  releases (spam).
- **`update` delegates to brew** rather than self-replacing because Homebrew IS the
  chosen distribution (ADR 0031) — a self-download/replace would fight a
  brew-managed keg. Rejected: full self-update (download+replace) and a
  detect-and-do-both mode — more code + foot-guns for negligible benefit while brew
  is the only live channel. The door to self-update stays open if a non-brew channel
  ever ships.
- **Nudge is opt-out, throttled, TTY-only, stable-only, fail-silent** so it never
  adds latency in scripts/CI or nags dogfood/edge builds.

## Consequences

Positive:
- Latest `main` is always installable for testing the moment it merges (no manual tag).
- One obvious update path (`dayfold update`) + passive awareness, matching brew.
- Zero new secrets/gates: edge uses only `github.token`; the update check is read-only
  against public release metadata.

Negative / accepted:
- A second release workflow to keep green. Mitigated: paths-filtered, concurrency-capped,
  same hardened conventions as `release-cli.yml`.
- `brew install/upgrade` (and therefore the most ergonomic `dayfold update`) stays
  inert until ADR 0031's operator gates close (tap repo + `HOMEBREW_TAP_TOKEN` +
  license). Until then `update` still reports versions + prints instructions, and edge
  tarballs are downloadable from the `cli-edge` pre-release.
- The brew-managed heuristic (keg-path match) could miss an exotic prefix → it then
  prints instructions instead of auto-running brew (safe degradation).

## Revisit Trigger

- A non-Homebrew distribution channel ships → reconsider a real self-update path.
- ADR 0031's tap gates close → confirm the `dayfold update` brew path end-to-end and
  consider dropping the manual instructions.
- Edge build volume or release-asset retention becomes a problem → add pruning / move
  edge to a CI artifact instead of a release.
