package works.tether.client

// Web (Kotlin/JS) store. The spike uses an in-memory holder so it compiles with
// zero extra deps; a real app swaps in `localStorage` (add kotlinx-browser and
// use `kotlinx.browser.localStorage`).
//
// SECURITY CAVEAT — the browser is the weakest platform for a refresh token:
// there is no OS keychain equivalent, localStorage is readable by any same-origin
// script (XSS-exposed), and even an httpOnly cookie isn't reachable from JS. The
// recommended posture for web is a SHORT refresh lifetime + server-side rotation
// /reuse-detection, or a backend-for-frontend that keeps the refresh token in an
// httpOnly cookie and never hands it to JS at all. See
// kmp-publishing-and-secure-storage.md §web.
class JsTokenStore(private var session: Session? = null) : TokenStore {
  override fun load(): Session? = session
  override fun save(session: Session) { this.session = session }
  override fun clear() { session = null }
}
