plugins {
  kotlin("jvm") version "2.2.20"
  kotlin("plugin.serialization") version "2.2.20"
}

repositories { mavenCentral() }

dependencies {
  implementation("org.reduxkotlin:redux-kotlin-jvm:0.6.2") // locked: redux-kotlin 0.6.2 stable
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }
