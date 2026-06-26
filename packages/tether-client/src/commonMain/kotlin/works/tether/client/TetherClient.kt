package works.tether.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Non-2xx from an authed endpoint. `status` lets callers branch — notably 401 → refresh. */
class AuthHttpException(val status: Int, val endpoint: String) :
  Exception("$endpoint HTTP $status")

@Serializable internal data class RefreshReq(val refresh: String)
@Serializable internal data class TokenResp(val access: String, val refresh: String)

/**
 * The reusable client auth core, generified from dayfold's AuthClient + the
 * AuthEngine.callWithRefresh loop. It is the in-app twin of tether-cli: a Ktor
 * call wrapped with transparent refresh-on-401, backed by a platform TokenStore.
 *
 * Crucially DECOUPLED from dayfold's redux store: dayfold dispatched
 * `SessionRotated` on rotation; here the rotation is persisted to the TokenStore
 * and surfaced via an optional [onRotate] callback, so any app (redux, MVVM,
 * plain state) can react without the core depending on a state library.
 *
 * The HttpClient is injected (no default) so commonMain stays engine-free — each
 * platform passes its engine (CIO/OkHttp/Darwin/JS).
 */
class TetherClient(
  private val config: TetherClientConfig,
  private val http: HttpClient,
  private val store: TokenStore,
  private val json: Json = Json { ignoreUnknownKeys = true },
  /** Called after the tokens rotate (already persisted). Hook your state update here. */
  private val onRotate: ((Session) -> Unit)? = null,
) {
  fun session(): Session? = store.load()

  /**
   * Run an access-token call; on 401 refresh once (rotate → persist → notify) and
   * retry. Any other failure (including a failed refresh) propagates. This is the
   * single chokepoint every authed call flows through.
   */
  suspend fun <T> withAuth(block: suspend (access: String) -> T): T {
    val current = store.load() ?: throw IllegalStateException("not signed in")
    return try {
      block(current.access)
    } catch (e: AuthHttpException) {
      if (e.status != 401) throw e
      val rotated = refreshNow(current.refresh)
      block(rotated.access)
    }
  }

  /** Rotate the session via /auth/refresh, persist it, fire [onRotate], return it. */
  suspend fun refreshNow(refreshToken: String? = null): Session {
    val rt = refreshToken ?: store.load()?.refresh ?: throw IllegalStateException("not signed in")
    val resp = http.post("${config.apiBase}${config.endpoints.refresh}") {
      contentType(ContentType.Application.Json)
      setBody(json.encodeToString(RefreshReq.serializer(), RefreshReq(rt)))
    }
    if (resp.status.value != 200) throw AuthHttpException(resp.status.value, "refresh")
    val parsed = json.decodeFromString(TokenResp.serializer(), resp.bodyAsText())
    val rotated = Session(access = parsed.access, refresh = parsed.refresh)
    store.save(rotated)
    onRotate?.invoke(rotated)
    return rotated
  }

  /** Revoke the credential server-side (best-effort) and clear local tokens. */
  suspend fun signout() {
    val s = store.load()
    if (s != null) {
      runCatching {
        http.post("${config.apiBase}${config.endpoints.signout}") { header("authorization", "Bearer ${s.access}") }
      }
    }
    store.clear()
  }

  /** GET /auth/whoami → raw JSON body (caller decodes its own shape). */
  suspend fun whoami(): String = withAuth { access ->
    val resp = http.get("${config.apiBase}${config.endpoints.whoami}") { header("authorization", "Bearer $access") }
    if (resp.status.value != 200) throw AuthHttpException(resp.status.value, "whoami")
    resp.bodyAsText()
  }

  /**
   * Generic authed request with refresh-on-401. `path` is absolute on apiBase,
   * e.g. "/families/$fid/cards/$id". Returns (status, body).
   */
  suspend fun call(method: String, path: String, body: String? = null): Pair<Int, String> = withAuth { access ->
    val resp = request(method, "${config.apiBase}$path", body, access)
    if (resp.status.value == 401) throw AuthHttpException(401, path) // → withAuth refresh-and-retry
    resp.status.value to resp.bodyAsText()
  }

  internal suspend fun request(method: String, url: String, body: String?, access: String?): HttpResponse =
    when (method.uppercase()) {
      "GET" -> http.get(url) { authed(access) }
      "DELETE" -> http.delete(url) { authed(access) }
      "PUT" -> http.put(url) { authed(access); jsonBody(body) }
      else -> http.post(url) { authed(access); jsonBody(body) }
    }

  private fun HttpRequestBuilder.authed(access: String?) {
    if (access != null) header("authorization", "Bearer $access")
  }

  private fun HttpRequestBuilder.jsonBody(body: String?) {
    if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
  }
}
