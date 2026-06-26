package works.tether.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Exercises the extracted common core through the JVM (desktop) target with a
// ktor MockEngine — no network. Proves the refresh-on-401 chokepoint, the
// device-approval verbs, and the desktop token store, all from commonMain code.
class TetherClientTest {
  private val config = TetherClientConfig(apiBase = "https://api.test", tenantPath = "families")
  private val json = jsonHeaders()

  private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

  private fun client(store: TokenStore, onRotate: ((Session) -> Unit)? = null, engine: MockEngine) =
    TetherClient(config, HttpClient(engine), store, onRotate = onRotate)

  @Test fun refreshesOnceOn401ThenRetries() = runBlocking {
    val store = InMemoryTokenStore(Session(access = "a1", refresh = "r1"))
    var rotatedTo: Session? = null
    val engine = MockEngine { req ->
      val auth = req.headers[HttpHeaders.Authorization]
      when (req.url.encodedPath) {
        "/auth/refresh" -> respond("""{"access":"a2","refresh":"r2"}""", HttpStatusCode.OK, json)
        "/families/f1/cards/c1" ->
          if (auth == "Bearer a1") respond("", HttpStatusCode.Unauthorized)
          else respond("ok", HttpStatusCode.OK)
        else -> respond("", HttpStatusCode.NotFound)
      }
    }
    val c = client(store, onRotate = { rotatedTo = it }, engine = engine)

    val (code, body) = c.call("PUT", "/families/f1/cards/c1", "{}")

    assertEquals(200, code)
    assertEquals("ok", body)
    assertEquals("a2", store.load()!!.access)   // rotated tokens persisted
    assertEquals("r2", store.load()!!.refresh)
    assertEquals("r2", rotatedTo!!.refresh)     // onRotate fired
  }

  @Test fun nonAuthErrorIsNotRetried() = runBlocking {
    var calls = 0
    val engine = MockEngine { _ -> calls++; respond("nope", HttpStatusCode.InternalServerError) }
    val c = client(InMemoryTokenStore(Session("a1", "r1")), engine = engine)

    val (code, _) = c.call("GET", "/families/f1/cards")
    assertEquals(500, code)
    assertEquals(1, calls) // no refresh, no retry on a non-401
  }

  @Test fun deviceApproveMapsStatusToResult() = runBlocking {
    val engine = MockEngine { req ->
      if (req.url.encodedPath == "/families/f1/device/approve") respond("", HttpStatusCode.NoContent)
      else respond("", HttpStatusCode.NotFound)
    }
    val c = client(InMemoryTokenStore(Session("a1", "r1")), engine = engine)
    assertEquals(DeviceActionResult.Ok, c.deviceApprove(config, "f1", "WXYZ-1234"))
  }

  @Test fun devicePendingParsesGrant() = runBlocking {
    val engine = MockEngine { req ->
      if (req.url.encodedPath == "/device/pending")
        respond("""{"user_code":"WXYZ","label":"tether CLI","scopes":["read","write"]}""", HttpStatusCode.OK, json)
      else respond("", HttpStatusCode.NotFound)
    }
    val c = client(InMemoryTokenStore(Session("a1", "r1")), engine = engine)
    val r = c.devicePending("WXYZ")
    assertTrue(r is DeviceLookupResult.Found)
    assertEquals(listOf("read", "write"), r.device.scopes)
  }

  @Test fun fileTokenStoreRoundTrips() {
    val f = Files.createTempDirectory("tether").resolve("session.json").toFile()
    val store = FileTokenStore(f)
    assertNull(store.load())
    store.save(Session("acc", "ref"))
    assertEquals("ref", store.load()!!.refresh)
    // best-effort 0600
    val perms = Files.getPosixFilePermissions(f.toPath()).map { it.toString() }
    assertTrue(perms.none { it.startsWith("GROUP") || it.startsWith("OTHERS") })
    store.clear()
    assertNull(store.load())
  }
}
