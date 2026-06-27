// SPIKE — extraction PoC. Proves dayfold's in-app client auth (AuthClient +
// AuthEngine.callWithRefresh + TokenStore) lifts into a standalone, backend-
// agnostic Kotlin Multiplatform library, decoupled from redux.
//
// Targets here: desktop (JVM), iosArm64, iosSimulatorArm64, js(IR). androidTarget
// is intentionally OMITTED because this build host has no Android SDK — the
// AndroidTokenStore source ships under src/androidMain and is enabled with a
// two-line change (com.android.library + androidTarget()) on an SDK host. See
// README. iOS targets configure on Linux but their final binaries compile only on
// a macOS host (Kotlin/Native Apple constraint — see the publishing research doc).

plugins {
  kotlin("multiplatform") version "2.3.20"
  kotlin("plugin.serialization") version "2.3.20"
}

repositories { mavenCentral() }

kotlin {
  // dayfold's client pins jvmToolchain(17); this spike targets 21 to build on the
  // host JDK without toolchain auto-provisioning. Adoption matches the consuming app.
  jvmToolchain(21)

  jvm("desktop")
  iosArm64()
  iosSimulatorArm64()
  js(IR) { nodejs() }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("io.ktor:ktor-client-core:3.5.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
      }
    }
    val desktopMain by getting {
      dependencies { implementation("io.ktor:ktor-client-cio:3.5.0") }
    }
    val desktopTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("io.ktor:ktor-client-mock:3.5.0")
      }
    }
    iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.5.0") }
    val jsMain by getting {
      dependencies { implementation("io.ktor:ktor-client-js:3.5.0") }
    }
  }
}

tasks.named<Test>("desktopTest") { useJUnitPlatform() }

// ── Publishing (recommended setup, INACTIVE in the spike) ──
// Per the KMP publishing research (kmp-publishing-and-secure-storage.md): the
// lowest-maintenance path is the vanniktech plugin → Sonatype Central Portal,
// publishing ALL targets from one macOS CI job (the only host that can build final
// Apple binaries; single-host avoids Central's duplicate-publication failure).
// Uncomment + add coordinates/POM/signing to activate:
//
// plugins { id("com.vanniktech.maven.publish") version "0.37.0" }
// mavenPublishing {
//   publishToMavenCentral()      // Central Portal is the default
//   signAllPublications()
//   coordinates("works.tether", "tether-client", "0.1.0")
//   pom { /* name, description, url, licenses, developers, scm */ }
// }
