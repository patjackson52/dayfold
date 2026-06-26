# tether-client — KMP extraction spike (PoC)

**Status: spike / proof-of-concept.** Not wired into dayfold's build. Sibling to
`packages/tether-cli`: that one extracted the *CLI* half of dayfold's auth; this
extracts the *in-app client* half into a standalone, backend-agnostic **Kotlin
Multiplatform** library.

## What this is

dayfold's `apps/client` already has working client-side auth — `AuthClient`
(Ktor calls), `AuthEngine.callWithRefresh` (transparent refresh-on-401), a
`TokenStore` interface with per-platform impls, and the in-app **device-approval**
screens (owner approves a CLI/new device by `user_code`). The Tether hypothesis is
that this is the genuinely reusable, *nobody-sells-it* part. This module proves it
lifts out cleanly.

Two things were generified out of dayfold:

1. **Backend specifics → `TetherClientConfig`** (API base, tenant noun, endpoint
   paths) — same shape as `tether-cli`'s `TetherConfig`, so the CLI and the app
   speak to the same server identically. Works against any RFC 8628 + bearer
   backend (dayfold API, Better Auth, FusionAuth, Zitadel, WorkOS).
2. **The redux coupling → an `onRotate` callback.** dayfold dispatched
   `SessionRotated` into its store; the core now just persists the rotation and
   fires an optional callback, so any app (redux / MVVM / plain state) can adopt
   it without pulling in a state library.

## The core (all in `commonMain`, platform-neutral)

| File | Role |
|---|---|
| `TetherClientConfig.kt` | the one thing an app edits |
| `Session.kt` | `Session` + the `TokenStore` interface (+ in-memory impl) |
| `TetherClient.kt` | `withAuth` / `call` / `refreshNow` / `signout` — the refresh-on-401 chokepoint |
| `DeviceApproval.kt` | `devicePending` / `deviceApprove` / `deviceDeny` — the in-app owner-approves-with-scopes primitive |

`TokenStore` is an **interface, not `expect/actual`** (dayfold's deliberate
choice): each platform impl takes its own ctor deps (Android a `Context`, desktop
a file path) and is injected at the entrypoint, avoiding the expect/actual
constructor clash. The shared core never knows which platform it's on.

Per-platform token stores: `FileTokenStore` (desktop, 0600), `AndroidTokenStore`
(SharedPreferences), `IosTokenStore` (NSUserDefaults), `JsTokenStore` (in-memory).
Their **secure-storage upgrades** (EncryptedSharedPreferences / Keychain / BFF
cookie) are documented in `kmp-publishing-and-secure-storage.md` §Part 2.

## What compiles where (and what this spike actually built)

This build host is **Linux with JDK 21, Node 22, no Android SDK, no macOS**. So:

| Target | In this spike | Notes |
|---|---|---|
| **desktop (JVM)** | ✅ compiled + tested (`./gradlew desktopTest`) | the verified proof — exercises all `commonMain` logic via MockEngine |
| **js (IR)** | declared, compiles here | Node present |
| **iosArm64 / iosSimulatorArm64** | declared; **final binary needs macOS** | Kotlin/Native Apple constraint — see research doc §5 |
| **android** | source shipped, **target omitted** | no Android SDK on this host; re-enable with `com.android.library` + `androidTarget()` |

The verified part is the **common core compiling + the test suite green on the JVM
target** — which is exactly what proves the logic is platform-neutral (commonMain
can't reach JVM-only APIs). The platform stores are thin and host-specific.

## Tests (`src/desktopTest`)

MockEngine-driven, no network: refresh-on-401-then-retry (+ rotation persisted +
`onRotate` fired), non-401 is *not* retried, `deviceApprove`/`devicePending`
status mapping, and the `FileTokenStore` 0600 round-trip.

## Publishing

See `kmp-publishing-and-secure-storage.md` (Part 1). TL;DR: **vanniktech
maven-publish → Sonatype Central Portal, all targets from one `macos-latest` CI
job** (only host that builds Apple binaries; single-host avoids Central's
duplicate-publication failure). A ready-to-uncomment block is at the bottom of
`build.gradle.kts`.

## What this spike does NOT decide

The backend foundation (extract dayfold's TS vs Better Auth vs hybrid) is still
ADR-class and open. Like `tether-cli`, this is the no-regrets client half: worth
packaging regardless of backend, because device-grant + bearer is identical across
all of them.
