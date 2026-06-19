import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  kotlin("jvm") version "2.2.20"
  kotlin("plugin.serialization") version "2.2.20"
  kotlin("plugin.compose") version "2.2.20"
  id("org.jetbrains.compose") version "1.8.2"
}

repositories {
  mavenCentral()
  google()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
  implementation("org.reduxkotlin:redux-kotlin-jvm:0.6.2") // locked: redux-kotlin 0.6.2 stable
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  testImplementation(kotlin("test"))
  @OptIn(ExperimentalComposeLibrary::class)
  testImplementation(compose.uiTest)
}

kotlin { jvmToolchain(17) }

compose.desktop { application { mainClass = "com.familyai.client.MainKt" } }

tasks.test { useJUnitPlatform() }
