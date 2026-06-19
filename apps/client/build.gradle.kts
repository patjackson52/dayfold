import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  kotlin("jvm") version "2.3.20"
  kotlin("plugin.serialization") version "2.3.20"
  kotlin("plugin.compose") version "2.3.20"
  id("org.jetbrains.compose") version "1.9.3"
  id("app.cash.sqldelight") version "2.3.2"
}

sqldelight {
  databases {
    create("ContentDb") {
      packageName.set("com.familyai.client.db")
      dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.3.2") // UPSERT (ON CONFLICT DO UPDATE)
    }
  }
}

repositories {
  mavenCentral()
  google()
  maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
  implementation("org.reduxkotlin:redux-kotlin-threadsafe-jvm:1.0.0-alpha01") // latest (operator owns reduxkotlin); [F5] threadsafe
  implementation("org.reduxkotlin:redux-kotlin-compose-jvm:1.0.0-alpha01")     // selectorState/fieldState → f(store.state)→UI (needs Kotlin 2.3+)
  implementation("org.reduxkotlin:redux-kotlin-granular-jvm:1.0.0-alpha01")     // FieldStateKt depends on it; not pulled transitively by the compose .module
  implementation("org.reduxkotlin:redux-kotlin-devtools-core-jvm:1.0.0-alpha01") // devTools() store enhancer (records to DevToolsHub)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("app.cash.sqldelight:sqlite-driver:2.3.2") // JdbcSqliteDriver (desktop/test) + runtime
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  testImplementation(kotlin("test"))
  @OptIn(ExperimentalComposeLibrary::class)
  testImplementation(compose.uiTest)
}

kotlin { jvmToolchain(17) }

compose.desktop { application { mainClass = "com.familyai.client.MainKt" } }

tasks.test { useJUnitPlatform() }
