package works.tether.client

import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

// iOS store. Mirrors dayfold's current shipping impl (NSUserDefaults), kept so
// the spike stays consistent with the real codebase. Compiles on a macOS host
// only (Kotlin/Native Apple target) — NOT exercised in this Linux spike build.
//
// SECURE-STORAGE FOLLOW: move the refresh token to the iOS Keychain
// (kSecClassGenericPassword via the Security framework) before any non-dogfood
// build. NSUserDefaults is plist-backed, not the secure enclave. This is the same
// tracked follow dayfold carries; see kmp-publishing-and-secure-storage.md.
class IosTokenStore(
  private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenStore {
  private val defaults = NSUserDefaults.standardUserDefaults
  private val key = "tether_session"

  override fun load(): Session? =
    defaults.stringForKey(key)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }

  override fun save(session: Session) {
    defaults.setObject(json.encodeToString(Session.serializer(), session), key)
  }

  override fun clear() {
    defaults.removeObjectForKey(key)
  }
}
