package com.familyai.client

import kotlinx.serialization.json.Json
import org.reduxkotlin.Store
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Pulls /sync (M0 household token) and dispatches the delta into the store.
// JDK-only HTTP so the core stays dependency-light; the same logic ports to
// ktor in the KMP/Compose shell later.
class SyncClient(
  private val api: String,
  private val familyId: String,
  private val secret: String,
  private val http: HttpClient = HttpClient.newHttpClient(),
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  fun sync(store: Store<AppState>) {
    store.dispatch(SyncStarted)
    try {
      val cursor = store.state.cursor
      val qs = cursor?.let { "?since=" + URLEncoder.encode(it, "UTF-8") } ?: ""
      val req = HttpRequest.newBuilder(URI.create("$api/families/$familyId/sync$qs"))
        .header("authorization", "Bearer $secret").GET().build()
      val res = http.send(req, HttpResponse.BodyHandlers.ofString())
      if (res.statusCode() != 200) {
        store.dispatch(SyncFailed("HTTP ${res.statusCode()}")); return
      }
      store.dispatch(SyncSucceeded(json.decodeFromString(SyncResponse.serializer(), res.body())))
    } catch (e: Exception) {
      store.dispatch(SyncFailed(e.message ?: "sync error"))
    }
  }
}
