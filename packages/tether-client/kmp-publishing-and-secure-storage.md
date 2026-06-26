# Publishing a KMP library to all platforms + secure token storage

Research backing the `tether-client` spike. Dated snapshot — **2026-06-26**,
Kotlin 2.3.20. Claims tagged `[fact:url]`, `[estimate]`, `[assumption]`.
Re-verify version-sensitive items (flagged) before acting.

---

## Part 1 — Publishing KMP to Android / iOS / JVM-Desktop / Web

### 1. Maven Central is the default path; use the vanniktech plugin

- A KMP library publishes **one Maven publication per target** (`-jvm`, `-android`/`androidRelease`, `-js`, `-wasm-js`, `-iosarm64`, `-iosx64`, `-iossimulatorarm64`) **plus an umbrella `kotlinMultiplatform` root publication** under the bare coordinate. Consumers declare the single root coordinate; **Gradle Module Metadata** (`.module` file) routes each consumer to the right variant automatically. Module Metadata is therefore mandatory for KMP consumption — a POM-only Maven client can't resolve variants. Don't disable it. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html]
- JetBrains' own publishing tutorial recommends the **`com.vanniktech.maven.publish`** plugin over hand-wiring `maven-publish`: it auto-configures the per-target publications, sources + Dokka jars, POM, GPG signing, and the Central upload. Latest stable **0.37.0** (~2026-06-21); 0.36+ needs JDK 17, Gradle ≥ 9, Kotlin ≥ 2.2.0, Dokka v2. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html] [fact:https://vanniktech.github.io/gradle-maven-publish-plugin/central/]

### 2. Sonatype: Central Portal only (OSSRH is dead)

- **OSSRH shut down 2025-06-30**; the **Central Publisher Portal** (`central.sonatype.com`) is the only path. vanniktech dropped OSSRH in 0.34.0 — modern DSL is `publishToMavenCentral()` (+ `signAllPublications()`). [fact:https://central.sonatype.org/pages/ossrh-eol/] [fact:https://vanniktech.github.io/gradle-maven-publish-plugin/central/]
- One-time **namespace verification**: `io.github.<user>` via a GitHub repo named with the key, or a reverse-DNS domain via a TXT record. Credentials are a **user token** (`central.sonatype.com/usertoken`), not your login. **GPG signing is mandatory** — on CI use an in-memory ASCII-armored key (`ORG_GRADLE_PROJECT_signingInMemoryKey` + KeyId + KeyPassword). [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html]
- Release: `publishToMavenCentral` + manual confirm, or `publishAndReleaseToMavenCentral` for hands-off. **Snapshots** (`-SNAPSHOT`) appear immediately at `https://central.sonatype.com/repository/maven-snapshots/`, auto-purge ~90 days. [fact:https://central.sonatype.org/publish/publish-portal-snapshots/]

### 3. iOS consumption — three options

- **KMP/Compose iOS consumer:** nothing extra — adds the one Maven coordinate, Gradle resolves the Apple `.klib`s. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html]
- **Native Swift/Xcode consumer:** needs an **XCFramework** (a `.klib` can't be consumed by Xcode). Build via the `XCFramework(...)` DSL + `assembleXCFramework` (bundles device + simulator slices). Distribute via:
  - **Swift Package Manager** (the direction JetBrains is steering toward): `.binaryTarget(url:, checksum:)` in `Package.swift`, zip the xcframework → attach to a GitHub Release → reference URL + `swift package compute-checksum`. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-spm-export.html]
  - **CocoaPods** (legacy fallback): the `kotlin("native.cocoapods")` plugin + `podPublishXCFramework`. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-cocoapods-overview.html]
  - **Touchlab KMMBridge** automates the build-on-macOS → publish-binary flow. [fact:https://kmmbridge.touchlab.co/docs/]
- Swift export (idiomatic Swift, no ObjC headers) is **Alpha in Kotlin 2.4.0**, *not available at 2.3.20*. On 2.3.20 the native-Swift path is XCFramework → SPM/CocoaPods. [fact:https://kotlinlang.org/docs/whatsnew24.html]

### 4. Web (JS + Wasm) consumption

- KMP-to-KMP is Maven-first: `js(IR)` and `wasmJs` are separate variants resolved by Module Metadata when the consumer enables the matching target. [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html]
- **npm** publishing exists (`Kotlin/npm-publish` plugin, `@JsExport` → `.d.ts`) for exposing Kotlin to a JS/TS frontend, but it's the secondary path and lightly documented. [fact:https://github.com/Kotlin/npm-publish]
- Maturity at 2.3.x: **Kotlin/Wasm is Beta**, Compose-for-Web (wasmJs) is Beta, and **wasmJs↔JS interop is not yet stable**. Kotlin/JS (IR) is the established, broader-compat web backend. [fact:https://kotlinlang.org/docs/wasm-overview.html] [fact:https://kotlinlang.org/docs/whatsnew2320.html]

### 5. Build-host constraints (the decisive one)

- **macOS host** builds final binaries + klibs for **any** target. **Linux/Windows** build everything **except final Apple binaries** (they *can* cross-compile Apple `.klib` metadata when there are no cinterop/CocoaPods deps, but **not** final binaries / XCFrameworks). [fact:https://kotlinlang.org/docs/native-target-support.html]
- **Single-host publishing rule:** publish all artifacts from one host — "Maven Central explicitly forbids duplicate publications and fails the process if they are created." [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html]
- ⇒ **This spike on Linux**: desktop (JVM) + js compile and the common metadata compiles; **iOS final binaries cannot be built here** — they need macOS. That's expected, not a defect.

### 6. Recommendation (solo author, lowest maintenance)

1. **vanniktech `com.vanniktech.maven.publish` 0.37.0** → `publishToMavenCentral()` + `signAllPublications()`.
2. Publish **everything from one `macos-latest` GitHub Actions job** (only host that builds Apple binaries; satisfies single-host) with `--no-configuration-cache` (publishing breaks under the config cache). [fact:https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html]
3. KMP consumers get one coordinate; add an **XCFramework + SPM-from-GitHub-Release** only when a native-Swift consumer appears (KMMBridge automates).
4. Web = Maven variants; npm only if a JS/TS app must consume it.
5. Pin the vanniktech version and re-check the changelog — it's the most volatile piece. [fact:https://vanniktech.github.io/gradle-maven-publish-plugin/changelog/]

### Version-sensitive caveats
- vanniktech version churns (0.36→0.37 mid-2026); the no-arg `publishToMavenCentral()` + snapshot support are recent.
- Publishing + Gradle configuration cache → keep `--no-configuration-cache`.
- Swift export / Swift package import are **2.4.0**, not 2.3.20.
- Kotlin/Wasm + Compose Web are **Beta**; wasmJs↔JS interop unstable.
- The Native host×target matrix is Kotlin-version-specific — re-check per upgrade.

---

## Part 2 — Secure token storage per platform

The refresh token is the long-lived secret; store it as securely as each platform
allows. Best-practice posture (`[assumption]`/`[estimate]` from platform docs;
verify against current guidance before shipping):

- **§android** — use **EncryptedSharedPreferences** (Jetpack Security / Tink,
  AES-256-GCM, key in the Android Keystore / StrongBox where available) rather
  than plain `MODE_PRIVATE` prefs. Plain prefs are app-sandboxed but unencrypted
  at rest (readable on a rooted/backed-up device). The spike's `AndroidTokenStore`
  ships the plain-prefs version (matching dayfold's current slice) with this as
  the tracked upgrade. [assumption: Jetpack Security docs]
- **§ios** — use the **Keychain** (`kSecClassGenericPassword`, with
  `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` so the secret never leaves the
  device via backup/iCloud). `NSUserDefaults` (the spike's current `IosTokenStore`,
  matching dayfold) is plist-backed plaintext — fine for dogfood, not for ship.
  [assumption: Apple Keychain Services docs]
- **§web** — the weakest platform: **no OS keychain equivalent**. `localStorage`
  is readable by any same-origin script (XSS-exposed); `sessionStorage` is no
  better and dies on tab close. The robust posture is a **backend-for-frontend
  that keeps the refresh token in an httpOnly, Secure, SameSite cookie** (never
  handed to JS), or a **short refresh lifetime + server-side rotation/reuse-
  detection** so a stolen token's blast radius is small. The spike's `JsTokenStore`
  is in-memory (cleared on reload) precisely to avoid implying localStorage is
  safe. [assumption: OWASP token-storage guidance]

The common core never sees any of this — it depends only on the `TokenStore`
interface, so the security posture is a per-platform implementation detail that
can harden over time without touching shared logic.
