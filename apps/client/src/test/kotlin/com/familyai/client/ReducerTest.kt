package com.familyai.client

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReducerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `sync upserts cards and advances cursor`() {
    var s = AppState()
    s = rootReducer(s, SyncSucceeded(SyncResponse(
      changes = Changes(listOf(Card("a", title = "A"), Card("b", title = "B"))), nextCursor = "cur1")))
    assertEquals(listOf("a", "b"), s.cards.map { it.id })
    assertEquals("cur1", s.cursor)
    assertFalse(s.syncing)

    // re-sync: update 'a', add 'c'
    s = rootReducer(s, SyncSucceeded(SyncResponse(
      changes = Changes(listOf(Card("a", title = "A2"), Card("c", title = "C"))), nextCursor = "cur2")))
    assertEquals(listOf("a", "b", "c"), s.cards.map { it.id })
    assertEquals("A2", s.cards.first { it.id == "a" }.title)
    assertEquals("cur2", s.cursor)
  }

  @Test
  fun `tombstone removes a card`() {
    var s = AppState(cards = listOf(Card("a", title = "A"), Card("b", title = "B")))
    s = rootReducer(s, SyncSucceeded(SyncResponse(
      tombstones = listOf(Tombstone("card", "a")), nextCursor = "c3")))
    assertEquals(listOf("b"), s.cards.map { it.id })
  }

  @Test
  fun `sync lifecycle flags`() {
    val started = rootReducer(AppState(), SyncStarted)
    assertTrue(started.syncing); assertNull(started.error)
    val failed = rootReducer(started, SyncFailed("boom"))
    assertFalse(failed.syncing); assertEquals("boom", failed.error)
  }

  @Test
  fun `parses the real API sync envelope`() {
    val body = """{"changes":{"cards":[{"id":"welcome","kind":"info","title":"Hello","body_md":null}]},
      "tombstones":[],"next_cursor":"abc","has_more":false}"""
    val resp = json.decodeFromString(SyncResponse.serializer(), body)
    assertEquals("welcome", resp.changes.cards[0].id)
    assertEquals("abc", resp.nextCursor)
  }

  @Test
  fun `store wires reducer end to end`() {
    val store = createAppStore()
    store.dispatch(SyncSucceeded(SyncResponse(changes = Changes(listOf(Card("x", title = "X"))))))
    assertEquals(1, store.state.cards.size)
  }
}
