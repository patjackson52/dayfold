package works.tether.client

import io.ktor.http.encodeURLQueryComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The in-app side of the RFC 8628 flow: an owner, signed in on their phone, looks
// up a pending device (the CLI/new app waiting on a user_code) and approves or
// denies it — optionally picking its scopes. This UX primitive is dayfold's
// genuinely novel bit; no off-the-shelf auth product ships an in-app
// owner-approves-with-scopes screen. Generified here, backend-agnostic.
//
// Built on TetherClient.call(), so every verb inherits transparent refresh-on-401.

/** The grant an approve screen renders. Generic, server-driven fields. */
@Serializable
data class PendingDevice(
  @SerialName("user_code") val userCode: String,
  val label: String? = null,
  val scopes: List<String> = emptyList(),
  @SerialName("requested_at") val requestedAt: String? = null,
)

/** GET /device/pending → typed lookup outcome. */
sealed interface DeviceLookupResult {
  data class Found(val device: PendingDevice) : DeviceLookupResult
  data object NotFound : DeviceLookupResult  // 404 — uniform miss/expired
  data object Locked : DeviceLookupResult    // 429 — shared approve lockout
}

/** approve/deny outcome. */
sealed interface DeviceActionResult {
  data object Ok : DeviceActionResult        // 2xx (or 404-on-deny: gone == denied)
  data object Expired : DeviceActionResult   // approve 404 — not pending anymore
  data object Locked : DeviceActionResult    // 429
  data object Forbidden : DeviceActionResult // 403 — caller isn't this tenant's owner
}

@Serializable private data class UserCodeReq(@SerialName("user_code") val userCode: String)

private val approvalJson = Json { ignoreUnknownKeys = true }

/** Look up the pending device for [userCode] so the owner can review it. */
suspend fun TetherClient.devicePending(userCode: String): DeviceLookupResult {
  val q = userCode.encodeURLQueryComponent()
  val (code, body) = call("GET", "/device/pending?user_code=$q")
  return when (code) {
    200 -> DeviceLookupResult.Found(approvalJson.decodeFromString(PendingDevice.serializer(), body))
    429 -> DeviceLookupResult.Locked
    else -> DeviceLookupResult.NotFound // 404 + any other miss
  }
}

/** Owner approves the device → it gets a session. [tenantId] scopes the action. */
suspend fun TetherClient.deviceApprove(config: TetherClientConfig, tenantId: String, userCode: String): DeviceActionResult {
  val path = "/${config.tenantPath}/$tenantId${config.endpoints.deviceApproveSuffix}"
  val (code, _) = call("POST", path, approvalJson.encodeToString(UserCodeReq.serializer(), UserCodeReq(userCode)))
  return when (code) {
    in 200..204 -> DeviceActionResult.Ok
    403 -> DeviceActionResult.Forbidden
    429 -> DeviceActionResult.Locked
    else -> DeviceActionResult.Expired // 404 not-pending/expired race
  }
}

/** Owner denies the device. 2xx/404 both mean "gone" → denied (idempotent). */
suspend fun TetherClient.deviceDeny(config: TetherClientConfig, tenantId: String, userCode: String): DeviceActionResult {
  val path = "/${config.tenantPath}/$tenantId${config.endpoints.deviceDenySuffix}"
  val (code, _) = call("POST", path, approvalJson.encodeToString(UserCodeReq.serializer(), UserCodeReq(userCode)))
  return when (code) {
    in 200..204, 404 -> DeviceActionResult.Ok
    403 -> DeviceActionResult.Forbidden
    429 -> DeviceActionResult.Locked
    else -> DeviceActionResult.Expired
  }
}
