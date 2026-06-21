package com.familyai.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire DTOs for the M0 /sync envelope (feed surface). Field names match the API
// (snake_case). NOTE: keep aligned with the SyncResponse contract in
// content.schema.json — a follow-up wires these to the generated Kotlin types.
@Serializable
data class Provenance(val source: String? = null) // "claude" | "email" | "user" | <url>

@Serializable
data class Card(
  val id: String,
  val kind: String = "info",
  val title: String,
  @SerialName("body_md") val bodyMd: String? = null,
  val provenance: Provenance? = null,
  // [review F2] /sync returns the full row — keep the feed-ordering + deep-link
  // fields, not just the title. not_before drives feed order (F1); target_* is
  // the deep-link the render layer will use when Hubs land.
  @SerialName("not_before") val notBefore: String? = null,
  @SerialName("expires_at") val expiresAt: String? = null,
  @SerialName("target_hub_id") val targetHubId: String? = null,
  @SerialName("target_section_id") val targetSectionId: String? = null,
  @SerialName("target_block_id") val targetBlockId: String? = null,
)

@Serializable data class Changes(val cards: List<Card> = emptyList())
@Serializable data class Tombstone(val type: String, val id: String)

@Serializable
data class SyncResponse(
  val changes: Changes = Changes(),
  val tombstones: List<Tombstone> = emptyList(),
  @SerialName("next_cursor") val nextCursor: String? = null,
  @SerialName("has_more") val hasMore: Boolean = false,
)

// ── AUTH-S5: client identity + session (ADR 0011/0021/0023) ──────────────────
// A backend-minted session (ADR 0011: we mint our own tokens, NOT Firebase's).
// access = short EdDSA JWT (5m); refresh = opaque rotating (45d). userId is the
// `sub`, surfaced for display only — never trusted for authz (re-resolved server
// -side per request).
@Serializable
data class Session(val access: String, val refresh: String, val userId: String? = null)

// One row of the caller's M:N membership (from GET /auth/whoami → families[]).
// status: "active" (approved) | "pending" (owner approval outstanding).
@Serializable
data class FamilyMembership(
  @SerialName("family_id") val familyId: String,
  val name: String = "",
  val role: String = "adult",          // owner | adult (teen 14+ deferred, ADR 0005)
  val status: String = "active",       // active | pending
)

// The app's first navigation surface (ADR 0013: f(state)→UI, no nav library).
// Family-null is a Feed SUBSTATE (the active family has no members yet), not a
// route — keeps the gate minimal.
enum class Route { Loading, SignIn, CreateFamily, Feed }

// Redux state (client state tree). The feed cursor lives in the DB (sync_meta),
// not here — the store is a projection of the DB. The auth fields below are the
// only client-held session state; the access token is attached per request and
// re-validated server-side (never trusted locally for authz).
data class AppState(
  // feed surface
  val cards: List<Card> = emptyList(),
  val syncing: Boolean = false,
  val error: String? = null,
  // auth / session (S5)
  val session: Session? = null,
  val families: List<FamilyMembership> = emptyList(),
  val activeFamilyId: String? = null,
  val route: Route = Route.Loading,
  val authBusy: Boolean = false,
  val authError: String? = null,
)

// Actions. Card data reaches the store ONLY via CardsLoaded (the DB→store bridge);
// SyncStarted/SyncSucceeded/SyncFailed carry sync STATUS only.
sealed interface Action
data object SyncStarted : Action
data object SyncSucceeded : Action
data class SyncFailed(val message: String) : Action
data class CardsLoaded(val cards: List<Card>) : Action

// Auth actions (S5). All I/O lives in AuthEngine (suspend, mutex-guarded like
// SyncEngine); the reducer is pure and derives `route`/`activeFamilyId` from
// (session, families) via routeFor()/activeFamilyIdFor().
data object AuthRestoring : Action                          // cold-start: reading the token store
data class SessionRestored(val session: Session?) : Action // null → SignIn; non-null → Loading (whoami next)
data class SignInRequested(val provider: String) : Action  // "google" | "apple" (dev build → dev-token)
data class SignInSucceeded(val session: Session) : Action  // → Loading until MembershipsLoaded
data class SignInFailed(val message: String) : Action
data class SessionRotated(val session: Session) : Action    // refresh swapped the tokens; no nav change
data class MembershipsLoaded(val families: List<FamilyMembership>) : Action // → Feed | CreateFamily
data class CreateFamilyRequested(val name: String) : Action
data class FamilyCreated(val familyId: String, val name: String) : Action   // → Feed (owner, active)
data class AuthOpFailed(val message: String) : Action
data object SignOutRequested : Action
data object SignedOut : Action                             // clears session + feed → SignIn
