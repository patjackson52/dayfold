package com.sloopworks.dayfold.client

import com.sloopworks.dayfold.client.cards.CardAction
import kotlin.test.Test
import kotlin.test.assertEquals

class CardHubNavTest {
  @Test fun `OpenHub routes to the Hubs surface + triggers the hub load with the focus block`() {
    val store = createAppStore(AppState(route = Route.Feed), debug = false)
    var loadedHub: String? = null; var loadedFocus: String? = "UNSET"
    routeCardAction(store, onPlatformAction = {}, CardAction.OpenHub("h_party", "blk_chk"),
      onOpenHub = { id, focus -> loadedHub = id; loadedFocus = focus })
    assertEquals(Route.Hubs, store.state.route)   // cross-surface nav (OpenHubs dispatched)
    assertEquals("h_party", loadedHub)            // engine load triggered with the hub id
    assertEquals("blk_chk", loadedFocus)          // + the deep-link focus block (arrival highlight)
  }

  @Test fun `OpenDetail still routes to the card detail stack (unchanged)`() {
    val store = createAppStore(AppState(cards = listOf(Card("c1", title = "X"))), debug = false)
    routeCardAction(store, onPlatformAction = {}, CardAction.OpenDetail("c1"))
    assertEquals(listOf("c1"), store.state.detailStack)
  }
}
