# SloopWorks Debug Drawer — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a standalone, reusable, **debug-only** Compose-Multiplatform debug-drawer library (host + pluggable panels + SloopWorks-themed chrome) as new Gradle modules with zero Dayfold dependencies, producing a working empty drawer that opens from a draggable bubble and renders registered panels — plus the no-op artifact that strips it entirely from release builds.

**Architecture:** A `:debugdrawer` (real) module and an API-identical `:debugdrawer-noop` module. The app wraps its root in `DebugDrawerHost(config) { App() }` and reads its backend via `DebugDrawer.backendUrl(default)`. Apps wire `debugImplementation(:debugdrawer)` + `releaseImplementation(:debugdrawer-noop)` so release links only the no-op (everything stripped). Panels are `DebugPlugin`s the host lists and navigates list→detail. This foundation plan delivers the module wiring, theme system, plugin API, persistence seam, and host/bubble/nav shell. The four panels (AppInfo, Backend-switch, Logs, Redux adapter) are **separate follow-on plans** (§Follow-on), each producing working software on its own.

**Tech Stack:** Kotlin Multiplatform 2.3.20, Compose Multiplatform (`org.jetbrains.compose`, matching the repo's pinned dev version), `kotlin("plugin.compose")`, `com.android.library`, kotlinx-coroutines 1.9.0. **No** redux/sqldelight/ktor/Dayfold deps.

## Global Constraints

- **JDK 17** (`jvmToolchain(17)`); Kotlin **2.3.20**; Compose-MP via `id("org.jetbrains.compose")` + `kotlin("plugin.compose")` (match the version already resolved in `apps/client/build.gradle.kts`).
- **Targets:** `androidTarget()`, `jvm("desktop")`, `iosArm64()`, `iosSimulatorArm64()` — mirror `apps/client`.
- **Gradle build:** modules live in the `apps/` build (`rootProject.name = "dayfold-apps"`), added to `apps/settings.gradle.kts`. Build via `cd apps && ./gradlew …`.
- **Zero Dayfold dependencies.** No `:client`, no app coords. Group **`com.sloopworks.debugdrawer`**, namespace `com.sloopworks.debugdrawer`. Publish-ready (own coordinates), extractable later.
- **No release footprint.** All drawer code is consumed via `debugImplementation`; `releaseImplementation` gets `:debugdrawer-noop`. The no-op exposes the **identical public API** with inert bodies.
- **Theming is token-level only** (`DebugDrawerTheme`); layout/density/shapes are fixed (spec §3). Default skin = SloopWorks (`designs/sloopworks/BRAND.md`); status + log-level colors are fixed cross-app.
- **Accessibility:** WCAG-AA (ADR 0009) — ≥48dp targets, contentDescription on icon-only controls, AA contrast both themes, color never the only signal.
- **Source of visual truth:** `designs/debug-drawer/spec.md` (component inventory C1–C16, interaction, adaptive, a11y) + `designs/debug-drawer/Debug Drawer.dc.html` (exact pixels/tokens). Implement visuals to match; this plan fixes structure, interfaces, and test cycles.

---

## File Structure

```
apps/settings.gradle.kts                       # +include(":debugdrawer", ":debugdrawer-noop")
apps/debugdrawer/
  build.gradle.kts                             # KMP + compose + android.library, no app deps
  src/commonMain/kotlin/com/sloopworks/debugdrawer/
    DebugDrawerHost.kt        # @Composable entry: bubble + drawer + nav; passthrough wrapper
    DebugDrawerConfig.kt      # config: buildInfo, backends, plugins, trigger, theme override
    DebugDrawer.kt            # top-level seams: backendUrl(default), selectedBackend, install hooks
    DebugPlugin.kt            # plugin interface + DebugScope (services handed to panels)
    theme/
      DebugDrawerTheme.kt     # overridable token object + LocalDebugDrawerTheme
      DebugSkins.kt           # SloopWorks default skin (light/dark) + fixed status/log colors
      Marks.kt                # sail brandMark (default), drawn as Compose Path
    host/
      Bubble.kt               # C1 draggable, edge-snapping bubble
      DrawerScaffold.kt       # C2/C3 adaptive container (bottom sheet / side sheet) + header
      PanelNav.kt             # list→detail nav state + transitions
      DrawerChrome.kt         # C4 panel-list rows; shared row/kv/chip primitives reused by panels
    persistence/
      DebugStore.kt           # expect KV (get/put string); used for backend override + bubble corner
  src/androidMain/kotlin/.../persistence/DebugStore.android.kt   # SharedPreferences
  src/desktopMain/kotlin/.../persistence/DebugStore.desktop.kt   # java.util.prefs.Preferences
  src/iosMain/kotlin/.../persistence/DebugStore.ios.kt           # NSUserDefaults
  src/commonTest/kotlin/com/sloopworks/debugdrawer/...           # tests
apps/debugdrawer-noop/
  build.gradle.kts
  src/commonMain/kotlin/com/sloopworks/debugdrawer/             # SAME packages/signatures, inert
    DebugDrawerHost.kt  DebugDrawerConfig.kt  DebugDrawer.kt  DebugPlugin.kt  theme/DebugDrawerTheme.kt
```

Each file has one responsibility; panels (follow-on plans) add `panels/<name>/…` without touching host internals.

---

### Task 1: Scaffold `:debugdrawer` module + minimal compiling public API

**Files:**
- Modify: `apps/settings.gradle.kts` (add includes)
- Create: `apps/debugdrawer/build.gradle.kts`
- Create: `apps/debugdrawer/src/androidMain/AndroidManifest.xml` (empty `<manifest>` with the namespace)
- Create: `apps/debugdrawer/src/commonMain/kotlin/com/sloopworks/debugdrawer/DebugDrawer.kt`
- Test: `apps/debugdrawer/src/commonTest/kotlin/com/sloopworks/debugdrawer/SmokeTest.kt`

**Interfaces:**
- Produces: `object DebugDrawer { fun backendUrl(default: String): String }` (stub returns `default` for now — real override logic in Task 6).

- [ ] **Step 1: Write the failing test**
```kotlin
// SmokeTest.kt
import kotlin.test.Test
import kotlin.test.assertEquals
class SmokeTest {
  @Test fun backendUrl_returns_default_when_no_override() {
    assertEquals("https://api.example", DebugDrawer.backendUrl("https://api.example"))
  }
}
```

- [ ] **Step 2: Wire the module.** Add to `apps/settings.gradle.kts`: `include(":client", ":androidApp", ":debugdrawer", ":debugdrawer-noop")`. Create `apps/debugdrawer/build.gradle.kts` mirroring `apps/client`'s plugin block (`kotlin("multiplatform")`, `kotlin("plugin.compose")`, `id("org.jetbrains.compose")`, `com.android.library`) with `jvmToolchain(17)`, the four targets, `namespace = "com.sloopworks.debugdrawer"`, `compileSdk`/`minSdk` copied from `apps/client`, and **only** these deps in `commonMain`: `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui`, `kotlinx-coroutines-core:1.9.0`; `commonTest`: `kotlin("test")`. No redux/sqldelight/ktor.

- [ ] **Step 3: Minimal implementation**
```kotlin
// DebugDrawer.kt
package com.sloopworks.debugdrawer
object DebugDrawer {
  fun backendUrl(default: String): String = default
}
```

- [ ] **Step 4: Run** `cd apps && ./gradlew :debugdrawer:compileKotlinDesktop :debugdrawer:desktopTest` — Expected: PASS. Also `:debugdrawer:compileDebugKotlinAndroid` succeeds.

- [ ] **Step 5: Commit** — `feat(debugdrawer): scaffold KMP module + backendUrl seam`

---

### Task 2: Theme token system + SloopWorks default skin

**Files:**
- Create: `…/theme/DebugDrawerTheme.kt`, `…/theme/DebugSkins.kt`, `…/theme/Marks.kt`
- Test: `…/commonTest/kotlin/com/sloopworks/debugdrawer/theme/ThemeTest.kt`

**Interfaces:**
- Produces: `data class DebugDrawerTheme(brandName, accent, onAccent, accentSoft, colorScheme: DrawerColorScheme, bubblePosition: Corner, bubbleEdgeSnap: Boolean, brandMark: DrawerMark, bubbleIcon: DrawerMark?)`; `enum class DrawerColorScheme { DARK, LIGHT, SYSTEM }`; `enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }`; `val LocalDebugDrawerColors: ProvidableCompositionLocal<DrawerColors>`; `data class DrawerColors(...)` (resolved surface/border/text/muted/accent/ok/warn/err/log-levels per the active light|dark token set); `object DebugSkins { fun sloopworks(): DebugDrawerTheme; fun colors(theme, dark: Boolean): DrawerColors }`; `DrawerMark` = a drawable abstraction rendered via Compose `Path` (default = sail `M18 7 L18 38 L40 38 Z` scaled).
- Consumes: theme tokens are the **exact** hex from `designs/sloopworks/BRAND.md` / spec §3 (light `accent #2A53F0`, dark `#86A1FF`, surfaces, fixed `ok #46C97E/#1A9E55`, `warn #E0A33A/#B5740C`, `err #F2685E/#C5392B`, log levels V=muted D=accent I=ok W=warn E=err).

- [ ] **Step 1: Failing test** — assert `DebugSkins.colors(sloopworks(), dark=true).accent == Color(0xFF86A1FF)` and `dark=false` accent `== 0xFF2A53F0`; assert status colors are identical regardless of `theme.accent` override (cross-app fixedness).

- [ ] **Step 2: Run** `:debugdrawer:desktopTest` — Expected: FAIL (unresolved).

- [ ] **Step 3: Implement** the data classes + `DebugSkins` with the verbatim token tables (light/dark) from BRAND.md; status/log colors are constants not derived from `accent`. `Marks.sailPath()` returns a `Path` from the sail coordinates normalized to the draw size.

- [ ] **Step 4: Run** `:debugdrawer:desktopTest` — Expected: PASS.

- [ ] **Step 5: Commit** — `feat(debugdrawer): theme tokens + SloopWorks default skin`

---

### Task 3: Plugin API + DebugScope

**Files:** Create `…/DebugPlugin.kt`; Test `…/commonTest/.../PluginRegistryTest.kt`

**Interfaces:**
- Produces:
```kotlin
interface DebugPlugin { val id: String; val title: String; @Composable fun Content(scope: DebugScope) }
interface DebugScope {
  val store: DebugStore                 // persistence (Task 4)
  val backends: List<Backend>           // from config
  fun activeBackendId(): String         // resolves override or default
  fun stageBackend(id: String)          // staged selection (Backend panel uses it)
  fun requestRestart()                  // host action; default per-platform impl
  val logs: LogBuffer                   // ring buffer (Logs panel uses it)
  fun copy(text: String)                // clipboard + toast
}
data class Backend(val id: String, val label: String, val url: String)
```
- Consumes: `DebugStore` (Task 4), `LogBuffer` (defined here as an empty interface + in-memory impl; Logs follow-on plan fills capture).

- [ ] **Step 1: Failing test** — a registry holding plugins returns them in insertion order; `activeBackendId()` falls back to `backends.first().id` when the store has no override.

- [ ] **Step 2–4:** Run→fail; implement the interfaces + an internal `PluginRegistry`, `LogBuffer` in-memory ring (capacity 1000), and a default `DebugScopeImpl` wired to `DebugStore`; run→pass.

- [ ] **Step 5: Commit** — `feat(debugdrawer): plugin API + DebugScope`

---

### Task 4: Persistence seam (expect/actual KV)

**Files:** Create `…/persistence/DebugStore.kt` (expect) + `DebugStore.android.kt` / `.desktop.kt` / `.ios.kt`; Test `…/commonTest/.../DebugStoreTest.kt` (desktop actual).

**Interfaces:**
- Produces: `expect class DebugStore { fun get(key: String): String?; fun put(key: String, value: String); fun remove(key: String) }` + a common `DebugKeys` object (`BACKEND_OVERRIDE`, `BUBBLE_CORNER`, `COLOR_SCHEME`).

- [ ] **Step 1: Failing test (desktop):** put→get round-trips; remove clears; missing key → null. Use a temp-namespaced `Preferences` node so the test is isolated.
- [ ] **Step 2–4:** Run→fail; implement android `SharedPreferences` (context via an `initDebugStore(context)` called from the Android host), desktop `java.util.prefs.Preferences.userRoot().node("com.sloopworks.debugdrawer")`, iOS `NSUserDefaults.standardUserDefaults`; run→pass.
- [ ] **Step 5: Commit** — `feat(debugdrawer): expect/actual persistence (backend override, bubble corner)`

---

### Task 5: Host shell — bubble + adaptive drawer + list→detail nav

**Files:** Create `…/host/Bubble.kt`, `…/host/DrawerScaffold.kt`, `…/host/PanelNav.kt`, `…/host/DrawerChrome.kt`; Test `…/commonTest/.../NavTest.kt` (nav state logic, headless).

**Interfaces:**
- Produces: `@Composable fun DebugBubble(corner: Corner, onOpen: ()->Unit, badge: Int, theme)`; `@Composable fun DrawerScaffold(widthClass: DrawerWidth, header, content)`; `class PanelNavState { val current: String?; fun open(id); fun back() }`; `enum class DrawerWidth { COMPACT, MEDIUM, EXPANDED }`; reusable `@Composable fun PanelListRow(...)`, `KeyValueRow(...)`, `MonoChip(...)`, `StatusDot(...)` in `DrawerChrome.kt`.
- Consumes: theme (Task 2), `DebugPlugin`/`DebugScope` (Task 3), `DebugStore` (Task 4).

- [ ] **Step 1: Failing test** — `PanelNavState`: starts at `current == null` (list home); `open("appinfo")` → current == "appinfo"; `back()` → null; nav survives recomposition (plain state-holder test).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.**
  - `PanelNavState` (the test target) — plain class, no Compose.
  - `DebugBubble`: 56dp circle, `pointerInput` drag, snaps to nearest horizontal edge on release, persists `Corner` via `DebugStore`; `badge` count; contentDescription "Open debug drawer, N unread". Visuals per spec C1.
  - `DrawerScaffold`: COMPACT → bottom `ModalBottomSheet`-style (~92% height, drag handle, scrim); EXPANDED → right side sheet (~420dp, non-modal, Esc/✕); MEDIUM → capped bottom sheet. Header (C3): brandMark + brandName + build-type chip + ✕; detail mode shows back ‹ + panel title. Use `compose.material3`.
  - `DrawerChrome` primitives styled to the theme tokens (hairline borders, 4dp scale, Geist-ish fallback to default font family for now — real Geist bundling is a follow-up).
- [ ] **Step 4: Run** `:debugdrawer:desktopTest` (nav logic) → PASS. Also `:debugdrawer:compileDebugKotlinAndroid` green.
- [ ] **Step 5: Commit** — `feat(debugdrawer): bubble + adaptive drawer scaffold + nav`

---

### Task 6: `DebugDrawerHost` entry + config + backend seam wiring

**Files:** Create `…/DebugDrawerHost.kt`, `…/DebugDrawerConfig.kt`; modify `…/DebugDrawer.kt`; Test `…/commonTest/.../BackendSeamTest.kt`.

**Interfaces:**
- Produces:
```kotlin
data class BuildInfo(val version: String, val build: String, val commit: String? = null,
                     val buildType: String = "debug", val flavor: String? = null, val extras: Map<String,String> = emptyMap())
data class DebugDrawerConfig(
  val buildInfo: BuildInfo,
  val backends: List<Backend> = emptyList(),
  val plugins: List<DebugPlugin> = emptyList(),     // built-ins auto-prepended unless disabled
  val theme: DebugDrawerTheme = DebugSkins.sloopworks(),
  val includeBuiltins: Boolean = true,
)
@Composable fun DebugDrawerHost(config: DebugDrawerConfig, content: @Composable () -> Unit)
```
- Modify `DebugDrawer`: `backendUrl(default)` now returns `store.get(BACKEND_OVERRIDE)?.let { id -> backends.firstOrNull{it.id==id}?.url } ?: default`, reading the config registered by the host. `fun selectedBackendId(): String?`.
- Consumes: everything from Tasks 2–5.

- [ ] **Step 1: Failing test** — register a config with backends `[prod→A, local→B]`; with no override `backendUrl("A")=="A"`; after `store.put(BACKEND_OVERRIDE,"local")`, `backendUrl("A")=="B"`; unknown override id falls back to the passed default.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `DebugDrawerHost` (Box{ content(); DebugBubble; if open → DrawerScaffold{ nav==null ? plugin list : plugin.Content(scope) } }); built-ins prepended when `includeBuiltins` (empty for now — panels are follow-on; the host renders an empty list + an "add panels" empty-state C14). Register config into a module-internal holder that `DebugDrawer` reads.
- [ ] **Step 4: Run** `:debugdrawer:desktopTest` → PASS.
- [ ] **Step 5: Commit** — `feat(debugdrawer): DebugDrawerHost + config + backend override seam`

---

### Task 7: `:debugdrawer-noop` — API-parity passthrough + parity guard

**Files:** Create `apps/debugdrawer-noop/build.gradle.kts` + `src/commonMain/.../{DebugDrawerHost,DebugDrawerConfig,DebugDrawer,DebugPlugin}.kt` and `theme/DebugDrawerTheme.kt`; Test `…/commonTest/.../NoopParityTest.kt`.

**Interfaces:** Same public symbols as the real module, inert:
- `@Composable fun DebugDrawerHost(config, content) { content() }` (passthrough only).
- `object DebugDrawer { fun backendUrl(default) = default; fun selectedBackendId() = null }`.
- `DebugDrawerConfig`, `BuildInfo`, `Backend`, `DebugPlugin`, `DebugScope`, `DebugDrawerTheme`, `DebugSkins.sloopworks()` present with minimal bodies so consumer code compiles unchanged against either artifact.

- [ ] **Step 1: Failing test** — in the noop module: `DebugDrawer.backendUrl("X")=="X"`; `selectedBackendId()==null`; constructing a `DebugDrawerConfig(BuildInfo("1","1"))` succeeds.
- [ ] **Step 2: Run** `:debugdrawer-noop:desktopTest` → FAIL.
- [ ] **Step 3: Implement** the parity surface. Keep it dependency-light (compose.runtime only — `@Composable` passthrough). Mirror every PUBLIC signature from the real module's Tasks 2,3,6 (copy signatures, empty/inert bodies).
- [ ] **Step 4: Run** `:debugdrawer-noop:desktopTest` → PASS. Then a **parity check**: a small script/test that lists public declarations of both modules' shared files and asserts the names match (or a documented manual checklist in the PR). Minimum bar: both modules compile against a shared `consumer-smoke` source that calls every public entry.
- [ ] **Step 5: Commit** — `feat(debugdrawer): no-op artifact (release-stripping) + parity guard`

---

## Self-Review (foundation)

- **Spec coverage (foundation slice):** C1 bubble (Task 5) · C2/C3 drawer+header (Task 5) · C4 list rows + C5 kv + chips (Task 5 `DrawerChrome`) · theming contract §3 (Task 2) · adaptive §5 (Task 5 `DrawerWidth`) · backend-override seam §4-hero plumbing (Task 6) · release-strip §4 (Task 7). Panels C6–C12, C16 + the Apply&Restart/log-filter interactions = **follow-on plans** (below) — intentionally out of this plan.
- **No placeholders:** UI tasks reference `spec.md`/`Debug Drawer.dc.html` for exact pixels by design (the visual truth is the imported mockup); interfaces, structure, and test cycles are concrete here.
- **Type consistency:** `Backend(id,label,url)`, `DebugScope`, `DebugStore`, `DebugDrawerConfig`, `BuildInfo`, `Corner`, `DrawerWidth` are used identically across Tasks 3–7.

---

## Follow-on plans (each its own spec→plan→build cycle; each ships working software)

**Plan B — Panels: AppInfo+Build (C5) & Backend/Env switch (C7/C8/C9/C10).** Two built-in `DebugPlugin`s. AppInfo renders `BuildInfo` + per-platform device/OS as copyable kv rows. Backend panel: selectable list (radio), staged selection, sticky "Apply & Restart" (C8) → confirm sheet (C9) → blocking overlay (C10) → `scope.requestRestart()`; per-platform restart actual (Android: relaunch launcher intent; desktop: relaunch process; iOS: exit-and-prompt or no-op+message). Tests: staging logic, override persistence round-trip, restart hook invoked.

**Plan C — Panel: Logs (C6/C11/C12/C13/C14).** `LogBuffer` capture adapter the app installs in one line (e.g. a Napier antilog / a simple `DebugLog.record(level,tag,msg)`); segmented V/D/I/W/E filter; virtualized list, auto-follow with "jump to latest"; log detail + share/export (per-platform share actual). Tests: filter logic, ring-buffer cap, level→color mapping fixed.

**Plan D — Panel: Redux DevTools adapter (`:debugdrawer-redux`, C16).** Optional module depending on `org.reduxkotlin:redux-kotlin-devtools-inapp` + `:debugdrawer`. A `DebugPlugin` whose `Content` mounts the existing inspector in a full-height detail pane. App adds it `debugImplementation` only. Proves the plugin API + unifies the two drawers (replaces the standalone redux bubble from ADR 0019).

**Plan E — Dayfold integration + polish.** Wire `DebugDrawerHost` into `apps/androidApp` (+ desktop `Main.kt`) behind `debugImplementation`/`releaseImplementation`; route `AuthClient`/`SyncClient` base URL through `DebugDrawer.backendUrl(BuildConfig.DAYFOLD_API)`; provide Dayfold's `DebugDrawerTheme` override (Dayfold accent `#C75C3C`/`#E89070`) as the consumer-skin proof; Compose snapshot tests (per ADR 0019 policy) of the drawer + panels light/dark; CI assertion that release links the noop (akin to the ADR 0026 package guard); optional Geist/Geist-Mono bundling.

---

## Risks / notes for the executor

- **Compose-MP version drift on a new module:** copy the resolved compose plugin/version from `apps/client` exactly; the JetBrains compose-dev maven repo is already in `apps/settings.gradle.kts`.
- **Android namespace + manifest:** a library module needs `namespace` in `build.gradle.kts` and a minimal `AndroidManifest.xml`. Watch the duplicate-class pattern the redux noop hit (`apps/androidApp/build.gradle.kts` `exclude(...)`) — apply the same technique if the noop and real artifacts ever collide in a consuming variant.
- **iOS targets** compile-only here; no device run needed for the foundation.
- **Bundling Geist fonts** is deferred (Plan E); use the platform default font family until then — do not block the foundation on font assets.
