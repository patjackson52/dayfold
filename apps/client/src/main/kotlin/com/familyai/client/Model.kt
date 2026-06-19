package com.familyai.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire DTOs for the M0 /sync envelope (feed surface). Field names match the API
// (snake_case). NOTE: keep aligned with the SyncResponse contract in
// content.schema.json — a follow-up wires these to the generated Kotlin types.
@Serializable
data class Card(
  val id: String,
  val kind: String = "info",
  val title: String,
  @SerialName("body_md") val bodyMd: String? = null,
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

// Redux state (the whole client state tree at M0 = the briefing feed).
data class AppState(
  val cards: List<Card> = emptyList(),
  val cursor: String? = null,
  val syncing: Boolean = false,
  val error: String? = null,
)

// Actions.
sealed interface Action
data object SyncStarted : Action
data class SyncSucceeded(val resp: SyncResponse) : Action
data class SyncFailed(val message: String) : Action
