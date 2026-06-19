package com.familyai.client

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReducerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test fun `CardsLoaded replaces the card list (DB is truth)`() {
    var s = AppState(cards = listOf(Card("old", title = "Old")))
    s = rootReducer(s, CardsLoaded(listOf(Card("a", title = "A"), Card("b", title = "B"))))
    assertEquals(listOf("a", "b"), s.cards.map { it.id })
  }

  @Test fun `sync status lifecycle`() {
    val started = rootReducer(AppState(), SyncStarted)
    assertTrue(started.syncing); assertNull(started.error)
    val ok = rootReducer(started.copy(error = "stale"), SyncSucceeded)
    assertFalse(ok.syncing); assertNull(ok.error)
    val failed = rootReducer(started, SyncFailed("boom"))
    assertFalse(failed.syncing); assertEquals("boom", failed.error)
  }

  @Test fun `parses the real API sync envelope`() {
    val body = """{"changes":{"cards":[{"id":"welcome","kind":"info","title":"Hello","body_md":null}]},
      "tombstones":[],"next_cursor":"abc","has_more":false}"""
    val resp = json.decodeFromString(SyncResponse.serializer(), body)
    assertEquals("welcome", resp.changes.cards[0].id)
    assertEquals("abc", resp.nextCursor)
  }

  @Test fun `decodes a full SELECT-star sync row without losing feed fields (F3 contract)`() {
    val body = """{"changes":{"cards":[{"id":"c1","family_id":"fam1","kind":"info","title":"T",
      "body_md":null,"target_hub_id":"h1","target_section_id":null,"target_block_id":null,
      "provenance":{"credential_id":"hc","source":"claude"},"triggers":null,"actions":null,
      "not_before":"2026-06-18T16:00:00Z","expires_at":null,"version":"1",
      "created_at":"2026-06-18T10:00:00Z","updated_at":"2026-06-18T10:00:00Z","deleted_at":null}]},
      "tombstones":[{"type":"card","id":"old"}],"next_cursor":"abc","has_more":false}"""
    val resp = json.decodeFromString(SyncResponse.serializer(), body)
    val c = resp.changes.cards[0]
    assertEquals("c1", c.id)
    assertEquals("h1", c.targetHubId)
    assertEquals("2026-06-18T16:00:00Z", c.notBefore)
    assertEquals("old", resp.tombstones[0].id)
  }

  @Test fun `store wires reducer end to end`() {
    val store = createAppStore()
    store.dispatch(CardsLoaded(listOf(Card("x", title = "X"))))
    assertEquals(1, store.state.cards.size)
  }
}
