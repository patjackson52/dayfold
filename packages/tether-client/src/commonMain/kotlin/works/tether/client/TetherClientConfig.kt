package works.tether.client

/**
 * Client-side mirror of tether-cli's TetherConfig — the one thing an app edits
 * to point the shared auth core at a backend. Same RFC 8628 + bearer model, same
 * tenant-path convention, so the CLI and the in-app SDK speak to the same server
 * with identical assumptions.
 *
 * Backend-agnostic: dayfold's own API, Better Auth's device plugin, FusionAuth,
 * Zitadel, WorkOS — only the endpoint paths differ, and those are config.
 */
data class TetherClientConfig(
  /** API origin, e.g. "https://api.example.com" (no trailing slash). */
  val apiBase: String,
  /**
   * Tenant noun used to build owner-scoped paths: `/<tenantPath>/<id>/device/approve`.
   * dayfold uses "families"; a workspaces app would say "workspaces".
   */
  val tenantPath: String = "families",
  val endpoints: Endpoints = Endpoints(),
) {
  data class Endpoints(
    val refresh: String = "/auth/refresh",
    val whoami: String = "/auth/whoami",
    val signout: String = "/auth/signout",
    /** GET, takes ?user_code= — the grant the in-app approve screen renders. */
    val devicePending: String = "/device/pending",
    /** Suffix appended to `/<tenantPath>/<id>` for the owner approve action. */
    val deviceApproveSuffix: String = "/device/approve",
    val deviceDenySuffix: String = "/device/deny",
  )
}
