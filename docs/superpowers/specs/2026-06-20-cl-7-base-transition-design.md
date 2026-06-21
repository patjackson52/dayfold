# CL-7 — Fold gesture (M0 = base transition + hardware back) (design)

**Epic:** `planning/content-detail-epic.md` (CL-7) · **Decision:** INB-18 / review =
**"base transition first"** · **ADR:** 0008/0009 / 0020 · **Depends:** CL-6
(DetailScreen + `detailStack` nav) — on `cl-next`.

## Spike result (done 2026-06-20)

At Compose-MP **1.9.3**, verified in the resolved jars:
- `SharedTransitionLayout` / `ExperimentalSharedTransitionApi` / `SharedTransitionScope`
  → present in the **animation** module (already a dep).
- `BackHandler` / `PredictiveBackHandler` → present in **`org.jetbrains.compose.ui:
  ui-backhandler`** (package `androidx.compose.ui.backhandler`), a **separate
  artifact NOT currently on the classpath** — which is why CL-6's `BackHandler`
  reference didn't resolve. Adding the dep enables both.

## Scope — M0 = the BASE transition (+ fix CL-6 hardware-back)

Per the operator's "base transition first" decision, M0 ships:
1. **`ui-backhandler` dependency** → enables back APIs.
2. **Hardware/gesture back → NavBack** in DetailScreen via `BackHandler` (fixes the
   CL-6 wart where Android hardware-back exited the app from detail). Plain
   `BackHandler` (not predictive) is the base; predictive-back *scrub* is polish.
3. **Base feed↔detail transition**: wrap the host switch in `AnimatedContent`
   keyed on the open card id (null = feed). A tasteful **fade + slight
   slide/scale** enter/exit (emphasized easing), open slightly slower than back
   (asymmetric, per the design's intent), so the swap reads as motion not a cut.
   Rapid-tap safe: `AnimatedContent` manages in-flight content; nav reducer
   already dedups the top.

## Deferred to CL-7b (polish follow — needs on-device iteration)

The **full SharedTransitionLayout container transform** (shared `card-$id`
bounds card→full, corner morph 26→0, content fade-after-grow, scrim 0→0.18) and
**predictive-back scrub** (`PredictiveBackHandler` driving the shared bounds).
**Why deferred:** shared-element animation correctness (no snap-back, no flicker,
media-not-gating) cannot be verified headlessly — it needs device iteration; the
spike confirms the APIs are available so CL-7b is unblocked. The base
`AnimatedContent` is the documented fallback the epic already sanctions.

## Files

- `apps/client/build.gradle.kts` — add `org.jetbrains.compose.ui:ui-backhandler`
  to commonMain.
- `FeedApp.kt` — wrap the feed↔detail switch in `AnimatedContent`.
- `cards/DetailScreen.kt` — `BackHandler(enabled = true) { onBack() }` at the top.

## Security / a11y / perf

- No behavior change to data/actions (read-only, ADR 0020 intact).
- Back via hardware/gesture is the expected platform affordance (a11y +
  navigation correctness).
- Transition is cheap (fade/slide of two subtrees); no media gating (cards/detail
  already render synchronously from the decoded store — CL-4/5/6).

## Test plan (`desktopTest`)

1. Existing 69 tests stay green (nav state machine already covers push/pop/dedup/
   prune; the transition wraps the same host).
2. Snapshots: feed + detail still render under the `AnimatedContent` host (the
   existing feed + detail snapshots already assert this — re-run, eyeball).
3. Animation smoothness = **manual on-device/desktop** (epic-sanctioned; can't
   snapshot motion). Note it.
4. Compile: desktop + Android + iOS-sim (the `ui-backhandler` dep must resolve on
   all 3).

## DoD

`ui-backhandler` resolves on all targets; hardware/gesture back returns to feed
(no app-exit from detail); feed↔detail animates (base transition) instead of a
hard cut; `:client:desktopTest` green; 3 targets compile. Full container-transform
+ predictive-back filed as CL-7b with the spike findings.

## Risks

- `ui-backhandler` artifact resolution across iOS/Android variants — verify the
  3-target compile (the spike found the desktop jar; KMP should resolve the rest).
- `AnimatedContent` size-transform jank between a list and a full screen — keep
  the spec simple (fade + small slide), no `SizeTransform` heroics at M0.
