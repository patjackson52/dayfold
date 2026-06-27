package works.tether.client

import android.content.Context
import kotlinx.serialization.json.Json

// Android store. Provided as source, but the `androidTarget()` is NOT enabled in
// this spike's build.gradle.kts because no Android SDK is installed on this build
// host — so this file is dormant here (documented in the README). Enabling it is a
// two-line change (the `com.android.library` plugin + `androidTarget()`) on a host
// with the SDK.
//
// Mirrors dayfold's current impl (private SharedPreferences).
// SECURE-STORAGE FOLLOW: move the refresh token to EncryptedSharedPreferences /
// Jetpack Security (Tink, AES256-GCM) before any non-dogfood build. Private-mode
// prefs are app-sandboxed but not encrypted at rest. See
// kmp-publishing-and-secure-storage.md §android.
class AndroidTokenStore(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenStore {
  private val prefs = context.getSharedPreferences("tether_session", Context.MODE_PRIVATE)
  private val key = "session"

  override fun load(): Session? =
    prefs.getString(key, null)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }

  override fun save(session: Session) {
    prefs.edit().putString(key, json.encodeToString(Session.serializer(), session)).apply()
  }

  override fun clear() {
    prefs.edit().remove(key).apply()
  }
}
