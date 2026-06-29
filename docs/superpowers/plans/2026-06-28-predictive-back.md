# Predictive Back (Core P0+P1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make dayfold's back navigation predictive-back-correct on Android 17/SDK 37 — fix the bug where system back exits the app on most screens (P0), and make the card→detail container transform gesture-driven while keeping its emphasized-decelerate Material motion (P1).

**Architecture:** Two cooperating back handlers (innermost-enabled wins): a shell `BackHandler` in `FeedApp` that maps system back to "up one level" for every non-detail screen via a pure `Back` action; and a `PredictiveBackHandler` in `ContentHost` that scrubs a `SeekableTransitionState` for the feed↔detail morph (commit → `NavBack`, cancel → animate back). Redux stays the single source of truth for *which* screen; the seekable state is the single *animator*.

**Tech Stack:** Kotlin, Compose Multiplatform 1.11.1 (commonMain), redux-kotlin, kotlin.test (desktopTest). Spec: `docs/superpowers/specs/2026-06-27-predictive-back-design.md`.

> **Revised after 3-agent review (2026-06-28).** Fixes folded into Tasks 2/4/5: nested-stack pop-target (was a feed-flash on detail→detail back); `onCloseHub()` side-effect on hub-detail back (was a DB-subscription leak); overlay-first back so the audience sheet closes before navigating (was over-navigate / app-exit); clamp the scrub <1f; keep the explicit `transitionSpec` (kills a default `scaleIn` wobble); `deviceResuming` back dead-zone; swipeEdge-independence documented. Google-conformance review: **PASS** for Core scope.

## Global Constraints

- All UI code in `commonMain` (no Android-only fork). Only the manifest line is Android-specific.
- Use the Compose-Multiplatform common back APIs: `androidx.compose.ui.backhandler.{BackHandler, PredictiveBackHandler}`.
- Experimental APIs require opt-in: `@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalTransitionApi::class)` where used.
- Reuse the existing `com.sloopworks.dayfold.client.ui.loading.rememberReduceMotion(): Boolean` for the reduced-motion guard. Do NOT add a new `expect/actual` (CI #226 enforces iOS-actual parity).
- Emphasized-decelerate easing = `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` (ADR 0022). Gesture progress runs through a decelerate curve, never raw-linear (Google guidance).
- Tests: `kotlin.test`, in `apps/client/src/desktopTest/...`. Run from `apps/`. Commit per repo convention (`feat(client):`/`test(client):`/`fix(client):`), end body with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Branch: `feat/predictive-back` (already created off `origin/main`).

---

### Task 1: Manifest — opt into the back-invoked callback

**Files:**
- Modify: `apps/androidApp/src/main/AndroidManifest.xml` (the `<application>` tag)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing (OS configuration).

- [ ] **Step 1: Add the attribute**

In `apps/androidApp/src/main/AndroidManifest.xml`, add `android:enableOnBackInvokedCallback="true"` to the `<application ...>` opening tag (alongside the existing attributes). Explicit opt-in (default-on at SDK 37, but states intent and is harmless on older installs).

- [ ] **Step 2: Verify it's present and the app still assembles**

Run: `cd apps && DAYFOLD_API="fake://busy-family" ./gradlew :androidApp:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. Then `grep enableOnBackInvokedCallback apps/androidApp/src/main/AndroidManifest.xml` → one match.

- [ ] **Step 3: Commit**

```bash
git add apps/androidApp/src/main/AndroidManifest.xml
git commit -m "feat(client): opt into enableOnBackInvokedCallback (predictive back)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Back-nav model — the `Back` action + pure resolution

A single `Back` action whose meaning ("up one level") is a pure function of `AppState`, so it's fully unit-testable. The shell handler (Task 4) dispatches it; the reducer delegates to the existing per-screen action.

**Files:**
- Create: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/BackNav.kt`
- Modify: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/Model.kt` (add the `Back` action object near the other nav actions, ~line 360)
- Modify: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/Reducer.kt` (add the `is Back ->` case in `rootReducer`'s `when`, near `is NavBack ->` at line 47)
- Test: `apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/BackNavTest.kt`

**Interfaces:**
- Consumes: `AppState` (fields `route: Route`, `detailStack: List<String>`, `currentHubId: String?`); existing actions `NavBack, CloseHub, CloseAccount, OpenAccount, CloseDeviceFlow, JoinDismissed`; `rootReducer(state: AppState, action: Any): AppState`.
- Produces:
  - `data object Back : Action`
  - `fun backAction(state: AppState): Action?` — the existing action a system-back resolves to, or `null` when the app should not consume back (→ system exits).
  - `fun appHandlesBack(state: AppState): Boolean` = `backAction(state) != null`.

- [ ] **Step 1: Write the failing test**

Create `apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/BackNavTest.kt`:

```kotlin
package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Pure back-resolution: what does system-back do from each state?
class BackNavTest {
  private fun st(route: Route, detail: List<String> = emptyList(), hub: String? = null,
                 sheet: Boolean = false, resuming: Boolean = false) =
    AppState(route = route, detailStack = detail, currentHubId = hub,
             audienceSheetOpen = sheet, deviceResuming = resuming)

  @Test fun `feed with a detail open resolves to NavBack`() {
    assertEquals(NavBack, backAction(st(Route.Feed, detail = listOf("c1"))))
  }

  @Test fun `an open audience sheet closes FIRST, before any nav`() {
    // even on a hub detail (where back would otherwise CloseHub) the overlay wins
    assertEquals(CloseAudienceSheet, backAction(st(Route.Hubs, hub = "h1", sheet = true)))
    // and on the hub LIST (where back would otherwise exit) the sheet still closes
    assertEquals(CloseAudienceSheet, backAction(st(Route.Hubs, sheet = true)))
    assertTrue(appHandlesBack(st(Route.Hubs, sheet = true)))
  }

  @Test fun `the device-resume beat lets the OS handle back`() {
    assertNull(backAction(st(Route.Feed, detail = listOf("c1"), resuming = true)))
    assertFalse(appHandlesBack(st(Route.Account, resuming = true)))
  }

  @Test fun `appHandlesBack is true for a handled route`() {
    assertTrue(appHandlesBack(st(Route.Account)))
  }

  @Test fun `feed with no detail is not handled (system exits)`() {
    assertNull(backAction(st(Route.Feed)))
    assertFalse(appHandlesBack(st(Route.Feed)))
  }

  @Test fun `hub detail resolves to CloseHub, hub list does not`() {
    assertEquals(CloseHub, backAction(st(Route.Hubs, hub = "h1")))
    assertNull(backAction(st(Route.Hubs)))
  }

  @Test fun `account resolves to CloseAccount`() {
    assertEquals(CloseAccount, backAction(st(Route.Account)))
  }

  @Test fun `members and devices resolve to OpenAccount`() {
    assertEquals(OpenAccount, backAction(st(Route.Members)))
    assertEquals(OpenAccount, backAction(st(Route.Devices)))
  }

  @Test fun `every device-flow screen resolves to CloseDeviceFlow`() {
    for (r in listOf(Route.AuthorizeDevice, Route.EnterCode, Route.ScanPrimer, Route.ScanDevice, Route.ScanDenied))
      assertEquals(CloseDeviceFlow, backAction(st(r)), "back from $r")
  }

  @Test fun `join invite resolves to JoinDismissed`() {
    assertEquals(JoinDismissed, backAction(st(Route.JoinInvite)))
  }

  @Test fun `auth gate routes are not handled`() {
    for (r in listOf(Route.SignIn, Route.Loading, Route.CreateFamily, Route.AuthError)) {
      assertNull(backAction(st(r)), "back from $r")
      assertFalse(appHandlesBack(st(r)))
    }
  }

  @Test fun `Back action delegates through the reducer to the resolved action`() {
    val s = st(Route.Hubs, hub = "h1")
    assertEquals(rootReducer(s, CloseHub), rootReducer(s, Back))
  }

  @Test fun `Back is a no-op where the app does not handle it`() {
    val s = st(Route.SignIn)
    assertEquals(s, rootReducer(s, Back))
  }
}
```

- [ ] **Step 2: Run it to verify it fails (unresolved `Back`/`backAction`)**

Run: `cd apps && ./gradlew :client:desktopTest --tests "com.sloopworks.dayfold.client.BackNavTest" --console=plain`
Expected: FAIL — compilation error, `Unresolved reference 'Back'` / `'backAction'`.

- [ ] **Step 3: Add the `Back` action**

In `Model.kt`, next to `data object NavBack : Action` (line ~360), add:

```kotlin
data object Back : Action                                     // system back → up one level (resolved by backAction)
```

- [ ] **Step 4: Create `BackNav.kt`**

```kotlin
package com.sloopworks.dayfold.client

// Pure back-navigation model (predictive back, Core P0). System back ("up one
// level") is a function of state: an OPEN OVERLAY closes first; otherwise it
// resolves to an existing nav action, or null when the app should NOT consume back
// (an auth-gate / top-level screen → the OS handles it, e.g. back-to-home). The
// reducer's `is Back ->` delegates here; the shell BackHandler enables itself via
// appHandlesBack(). One source of truth.
//
// NOTE: this is reducer-pure. One route has an extra side effect on back that the
// reducer cannot express — Route.Hubs detail also cancels the HubEngine DB tree
// subscription via onCloseHub() (FeedApp HubsHost). The shell BackHandler special-
// cases CloseHub to run that closure; see Task 4. Everything else is dispatch-only.
fun backAction(state: AppState): Action? {
  if (state.deviceResuming) return null                       // "Finishing…" resume beat → let the OS handle back
  if (state.audienceSheetOpen) return CloseAudienceSheet      // an open overlay closes FIRST (before any nav)
  return when (state.route) {
    Route.Feed -> if (state.detailStack.isNotEmpty()) NavBack else null
    Route.Hubs -> if (state.currentHubId != null) CloseHub else null
    Route.Account -> CloseAccount
    Route.Members, Route.Devices -> OpenAccount
    Route.AuthorizeDevice, Route.EnterCode, Route.ScanPrimer, Route.ScanDevice, Route.ScanDenied -> CloseDeviceFlow
    Route.JoinInvite -> JoinDismissed
    Route.SignIn, Route.Loading, Route.CreateFamily, Route.AuthError -> null
  }
}

fun appHandlesBack(state: AppState): Boolean = backAction(state) != null
```

- [ ] **Step 5: Add the reducer case**

In `Reducer.kt`, inside `rootReducer`'s `when (action)`, next to `is NavBack ->` (line ~47), add:

```kotlin
  is Back -> backAction(state)?.let { rootReducer(state, it) } ?: state
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd apps && ./gradlew :client:desktopTest --tests "com.sloopworks.dayfold.client.BackNavTest" --console=plain`
Expected: PASS (all BackNavTest cases green).

- [ ] **Step 7: Commit**

```bash
git add apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/BackNav.kt \
        apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/Model.kt \
        apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/Reducer.kt \
        apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/BackNavTest.kt
git commit -m "feat(client): pure Back-nav model (Back action + backAction resolution)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Motion helpers — easing + decelerate progress

Pure, `commonMain`, no platform code. Used by Task 5's gesture scrub and settle.

**Files:**
- Create: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/PredictiveBackMotion.kt`
- Test: `apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/PredictiveBackMotionTest.kt`

**Interfaces:**
- Consumes: `androidx.compose.animation.core.{CubicBezierEasing, Easing}`.
- Produces:
  - `val EmphasizedDecelerate: Easing` (= `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)`) — the settle curve on commit/cancel.
  - `fun decelerateProgress(raw: Float): Float` — clamps `raw` to `0f..1f` and applies a standard-decelerate curve (more motion early), for the gesture scrub fraction.

- [ ] **Step 1: Write the failing test**

Create `apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/PredictiveBackMotionTest.kt`:

```kotlin
package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictiveBackMotionTest {
  @Test fun `decelerateProgress pins the endpoints`() {
    assertEquals(0f, decelerateProgress(0f), 0.0001f)
    assertEquals(1f, decelerateProgress(1f), 0.0001f)
  }

  @Test fun `decelerateProgress clamps out-of-range input`() {
    assertEquals(0f, decelerateProgress(-0.5f), 0.0001f)
    assertEquals(1f, decelerateProgress(1.7f), 0.0001f)
  }

  @Test fun `decelerateProgress is monotonic non-decreasing`() {
    var prev = -1f
    var x = 0f
    while (x <= 1f) {
      val y = decelerateProgress(x)
      assertTrue(y >= prev - 0.0001f, "non-monotonic at x=$x (y=$y, prev=$prev)")
      prev = y; x += 0.05f
    }
  }

  @Test fun `decelerate front-loads motion (faster near the start than linear)`() {
    // a decelerate curve is above the y=x line in the first half
    assertTrue(decelerateProgress(0.25f) > 0.25f, "expected front-loaded motion at 0.25")
  }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps && ./gradlew :client:desktopTest --tests "com.sloopworks.dayfold.client.PredictiveBackMotionTest" --console=plain`
Expected: FAIL — `Unresolved reference 'decelerateProgress'`.

- [ ] **Step 3: Create `PredictiveBackMotion.kt`**

```kotlin
package com.sloopworks.dayfold.client

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

// ADR 0022 emphasized-decelerate — the settle curve played on commit/cancel (the
// tail animation; the drag itself tracks the finger via SeekableTransitionState).
val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// Standard decelerate (≈ PathInterpolator(0,0,0,1)). Google: feed the predictive-back
// preview a decelerate curve, never raw-linear progress, so motion is more apparent
// at gesture start.
private val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

fun decelerateProgress(raw: Float): Float = Decelerate.transform(raw.coerceIn(0f, 1f))
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd apps && ./gradlew :client:desktopTest --tests "com.sloopworks.dayfold.client.PredictiveBackMotionTest" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/PredictiveBackMotion.kt \
        apps/client/src/desktopTest/kotlin/com/sloopworks/dayfold/client/PredictiveBackMotionTest.kt
git commit -m "feat(client): predictive-back motion helpers (emphasized-decelerate + decelerate progress)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Shell back handler — fix the exits-app bug (P0)

One `BackHandler` at the `FeedApp` top level dispatches `Back` for every non-detail screen. Disabled when a feed detail is open (Task 5's `PredictiveBackHandler` owns that, and wins by nesting anyway — the guard is belt-and-suspenders).

**Files:**
- Modify: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/FeedApp.kt` (inside `DayfoldTheme { ... }`, before the `if (state.deviceResuming)` line ~104)

**Interfaces:**
- Consumes: `appHandlesBack(state)`, `backAction(state)`, `Back` (Task 2); the existing `CloseHub` action and the `onCloseHub: () -> Unit` parameter already on `FeedApp` (declared ~line 81); `currentDetailCard(state): Card?` (Selectors.kt); `androidx.compose.ui.backhandler.BackHandler`.
- Produces: nothing (wiring only).

- [ ] **Step 1: Add the import**

In `FeedApp.kt`, add to the imports:
```kotlin
import androidx.compose.ui.backhandler.BackHandler
```

- [ ] **Step 2: Add the shell handler**

In `FeedApp.kt`, inside `DayfoldTheme {`, immediately before the `if (state.deviceResuming) { ... }` line, add:

```kotlin
    // Predictive-back P0: every non-detail screen routes system back to "up one
    // level" (without this, system back exits the app at targetSdk >= 36). Disabled
    // when a feed detail is open — ContentHost's PredictiveBackHandler owns that.
    // Hub-detail back is special-cased: it must ALSO run onCloseHub() to cancel the
    // HubEngine DB tree subscription (mirrors HubsHost's on-screen arrow); every other
    // route — incl. closing the audience overlay — is pure and goes through `Back`.
    BackHandler(enabled = appHandlesBack(state) && currentDetailCard(state) == null) {
      if (backAction(state) == CloseHub) { onCloseHub(); store.dispatch(CloseHub) }
      else store.dispatch(Back)
    }
```

- [ ] **Step 3: Verify it compiles and existing tests stay green**

Run: `cd apps && ./gradlew :client:compileKotlinDesktop --console=plain` → `BUILD SUCCESSFUL`.
Run: `cd apps && ./gradlew :client:desktopTest --console=plain` → `BUILD SUCCESSFUL` (all existing snapshot/reducer tests still pass).

- [ ] **Step 4: Commit**

```bash
git add apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/FeedApp.kt
git commit -m "feat(client): shell back handler — system back goes up one level (P0)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Gesture-driven card↔detail container transform (P1)

Convert `ContentHost`'s `AnimatedContent` to a `SeekableTransitionState` and add a `PredictiveBackHandler` that scrubs it with the gesture; commit dispatches `NavBack`, cancel animates back. Remove `DetailScreen`'s now-redundant plain `BackHandler` (the gesture handler supersedes it; the in-hero arrow still dispatches `NavBack`).

**Files:**
- Modify: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/FeedApp.kt` (`ContentHost`, lines ~200-224; imports)
- Modify: `apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/cards/DetailScreen.kt` (remove the `BackHandler` at line ~63)

**Interfaces:**
- Consumes: `SeekableTransitionState`, `rememberTransition`, `Transition.AnimatedContent` (`androidx.compose.animation.core.*` / `androidx.compose.animation.*`); `PredictiveBackHandler` (`androidx.compose.ui.backhandler`); `EmphasizedDecelerate`, `decelerateProgress` (Task 3); `rememberReduceMotion()` (`ui.loading`); `currentDetailCard`, `NavBack`, `OpenAccount`, the existing `LocalSharedTransitionScope`/`LocalAnimatedVisibilityScope` providers and `cardSharedBounds`.
- Produces: nothing new (behavioral change to `ContentHost`).

- [ ] **Step 1: Update imports in `FeedApp.kt`**

Ensure these imports are present (add any missing):
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.PredictiveBackHandler
import com.sloopworks.dayfold.client.ui.loading.rememberReduceMotion
import kotlin.coroutines.cancellation.CancellationException
```
**KEEP** the existing `fadeIn` / `slideInVertically` / `fadeOut` / `togetherWith` imports — the explicit `transitionSpec` in Step 2 still uses them (the default `Transition.AnimatedContent` spec adds an unwanted `scaleIn` that wobbles against the shared-bounds morph, so we keep the original asymmetric fade+slide).

- [ ] **Step 2: Replace the `ContentHost` body**

Replace the whole `ContentHost` function (currently lines ~200-224) with:

```kotlin
// CL-7b container transform, gesture-driven (predictive back, P1). The card morphs
// into the detail via SharedTransitionLayout (key "card-$id"); a SeekableTransitionState
// is the single animator. Non-gesture transitions (open detail, hero-arrow back, deep
// pops) are driven by the redux→seekable LaunchedEffect; the back GESTURE scrubs the
// seekable with the finger and commits (NavBack) or cancels (animate back).
// The morph is edge-INDEPENDENT by design: it targets the card's fixed feed position,
// so BackEventCompat.swipeEdge / RTL do not change it (no edge-signed shift to invert).
// Threading swipeEdge is a P2 prerequisite only for the deferred full-screen route anim.
@Suppress("DEPRECATION")   // CMP 1.11.1: PredictiveBackHandler/BackEventCompat are @Deprecated (→ NavigationEvent); intentional per design D2
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class, ExperimentalTransitionApi::class)
@Composable
private fun ContentHost(store: Store<AppState>, state: AppState, handle: (CardAction) -> Unit, onConnectDevice: () -> Unit = {}, onNavHubs: () -> Unit = {}, onRefresh: () -> Unit = {}) {
  val detail = currentDetailCard(state)
  val targetKey: String? = detail?.id            // top of the detail stack (null = feed)
  // Where a back POP lands: the card UNDERNEATH the top (null = feed for a 1-deep stack).
  // Hardcoding null here would flash the feed on a nested detail→detail back.
  val popTarget: String? = state.detailStack.getOrNull(state.detailStack.lastIndex - 1)
  val seekable = remember { SeekableTransitionState<String?>(targetKey) }
  val reduceMotion = rememberReduceMotion()

  // Redux -> animation sync for NON-gesture transitions. The gesture path drives the
  // seekable directly and dispatches NavBack on commit; afterwards targetKey == the
  // settled state, so this no-ops.
  LaunchedEffect(targetKey) {
    if (seekable.currentState != targetKey) {
      if (reduceMotion) seekable.snapTo(targetKey)
      else seekable.animateTo(targetKey, animationSpec = tween(if (targetKey != null) 360 else 280, easing = EmphasizedDecelerate))
    }
  }

  // Back GESTURE: only when a detail is open. Scrub toward popTarget with the finger;
  // commit -> NavBack, cancel -> animate back to the current detail. Reduced-motion:
  // skip the scrub, jump to commit. The scrub is clamped < 1f so only the explicit
  // commit animateTo finishes the morph (a front-loaded decelerate can hit 1f while the
  // finger is still down — without the clamp the detail would visually vanish pre-commit).
  PredictiveBackHandler(enabled = detail != null) { progress ->
    try {
      progress.collect { e -> if (!reduceMotion) seekable.seekTo(decelerateProgress(e.progress).coerceAtMost(0.999f), targetState = popTarget) }
      if (!reduceMotion) seekable.animateTo(popTarget, animationSpec = tween(280, easing = EmphasizedDecelerate))
      store.dispatch(NavBack)                    // COMMIT
    } catch (e: CancellationException) {
      val back = detail?.id
      if (!reduceMotion && back != null) seekable.animateTo(back, animationSpec = tween(280, easing = EmphasizedDecelerate))
      throw e                                    // CANCEL — must rethrow
    }
  }

  val transition = rememberTransition(seekable, label = "feed-detail")
  SharedTransitionLayout {
    transition.AnimatedContent(
      contentKey = { it },
      transitionSpec = {
        // keep the original asymmetric fade+slide (360 open / 280 close); the default
        // Transition.AnimatedContent spec adds a scaleIn that fights the shared morph.
        val opening = targetState != null
        val dur = if (opening) 360 else 280
        (fadeIn(tween(dur)) + slideInVertically(tween(dur)) { h -> h / 16 }) togetherWith fadeOut(tween(dur))
      },
    ) { id ->
      CompositionLocalProvider(
        LocalSharedTransitionScope provides this@SharedTransitionLayout,
        LocalAnimatedVisibilityScope provides this@AnimatedContent,
      ) {
        val card = id?.let { cid -> state.cards.find { it.id == cid } }
        if (card != null) DetailScreen(card, onBack = { store.dispatch(NavBack) }, onAction = handle)
        else FeedScreen(state, onAction = handle, onOpenAccount = { store.dispatch(OpenAccount) }, onConnectDevice = onConnectDevice, onNavHubs = onNavHubs, onRefresh = onRefresh)
      }
    }
  }
}
```

- [ ] **Step 3: Remove the redundant `BackHandler` in `DetailScreen.kt`**

In `cards/DetailScreen.kt`, delete the line (~63):
```kotlin
  androidx.compose.ui.backhandler.BackHandler(enabled = true) { onBack() }
```
and update the preceding comment block to note the gesture handler now lives in `ContentHost`:
```kotlin
  // CL-7b: the back GESTURE (Android predictive back / iOS interactive-pop) is owned by
  // ContentHost's PredictiveBackHandler (it scrubs the container-transform). The in-hero
  // arrow below still dispatches onBack() -> NavBack for the explicit tap path.
```
Keep the `onBack` parameter (still used by `HeroHeader`). The `@OptIn(ExperimentalComposeUiApi::class)` on `DetailScreen` (line ~57) existed ONLY for the removed `BackHandler` — `grep ExperimentalComposeUiApi cards/DetailScreen.kt`; if nothing else in the file uses it, delete the annotation (and its import) to avoid an "unnecessary opt-in" warning.

- [ ] **Step 4: Compile and run the full client test suite**

Run: `cd apps && ./gradlew :client:compileKotlinDesktop --console=plain` → `BUILD SUCCESSFUL`.
Run: `cd apps && ./gradlew :client:desktopTest --console=plain` → `BUILD SUCCESSFUL` (existing card-detail snapshot/render tests still pass — the morph structure and `cardSharedBounds` are unchanged; only the animator driver changed).

> **Note on gesture testing:** the back-gesture scrub (PredictiveBackHandler + SeekableTransitionState) is a coroutine/Compose-runtime path not meaningfully unit-testable; its commit/cancel *logic* (NavBack dispatch, animate-back) is exercised by the BackNav reducer tests (Task 2) plus the **Verify phase** on the emulator (drive the system back gesture via adb, screenshot the morph mid-gesture and after commit/cancel). Do NOT fabricate a UI gesture unit test.

- [ ] **Step 5: Build the Android APK (the real predictive-back target)**

Run: `cd apps && DAYFOLD_API="fake://busy-family" ./gradlew :androidApp:assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/FeedApp.kt \
        apps/client/src/commonMain/kotlin/com/sloopworks/dayfold/client/cards/DetailScreen.kt
git commit -m "feat(client): gesture-driven card<->detail container transform (predictive back, P1)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Verify (after all tasks)

Driven separately (the `/verify` / emulator phase), not a code task:
1. Build the fake-backend APK (`DAYFOLD_API=fake://busy-family`), install on emulator, dev-sign-in, open a card detail.
2. Drive the system back gesture (`adb shell input ...` swipe from the left edge) partway and screenshot — expect the detail morphing back toward its feed card (not a hard cut); release before threshold → snaps back to detail; full swipe → commits to feed. Confirm the morph does **not** double-scale (no extra shrink fighting the shared-element morph — Agent 3 review).
3. **Nested back (Agent 1 BLOCKER 1):** open a card detail, tap a RELATED row to push a second detail, then back-gesture — expect a morph to the **previous detail**, NOT a flash of the feed list.
4. From a **hub detail**, Account, Members, Devices, and a scan screen: system back goes **up one level**, not app-exit (the P0 fix). On a hub detail, also confirm via logs that the tree subscription is cancelled (Agent 2 BLOCKER 1 — `onCloseHub()` ran; look for the HubEngine close log).
5. **Overlay-first (Agent 2 BLOCKER 2):** open the "Who can see" sheet on a hub, press back → the **sheet closes**, the hub stays open (back does NOT navigate away or exit).
6. Toggle "Remove animations" (ANIMATOR_DURATION_SCALE 0) → back still works, no scrub preview.
