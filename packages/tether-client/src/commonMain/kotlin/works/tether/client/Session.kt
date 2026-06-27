package works.tether.client

import kotlinx.serialization.Serializable

// The persisted session: a short-lived access token + a long-lived rotating
// refresh token. The refresh token is THE secret — each platform's TokenStore
// is responsible for storing it as securely as that platform allows.
@Serializable
data class Session(val access: String, val refresh: String)

/**
 * Persistence for the session, generified from dayfold's client `TokenStore`.
 *
 * Deliberately an INTERFACE, not an `expect class`: each platform's impl needs
 * its own construction deps (Android a Context, iOS nothing, desktop a file path)
 * and injecting a concrete impl at the platform entrypoint avoids the
 * expect/actual constructor-shape clash. The shared core only ever sees this
 * interface — it never knows which platform it's on.
 */
interface TokenStore {
  fun load(): Session?
  fun save(session: Session)
  fun clear()
}

/** Volatile store — fine for tests and ephemeral web sessions, never for a real refresh token at rest. */
class InMemoryTokenStore(private var session: Session? = null) : TokenStore {
  override fun load(): Session? = session
  override fun save(session: Session) { this.session = session }
  override fun clear() { session = null }
}
