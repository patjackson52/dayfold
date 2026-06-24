package com.sloopworks.dayfold.client

import com.sloopworks.dayfold.client.cards.CardAction
import kotlin.test.Test
import kotlin.test.assertEquals

class CardHubNavTest {
  @Test fun `OpenHub routes to the Hubs surface + triggers the hub load`() {
    val store = createAppStore(AppState(route = Route.Feed), debug = false)
    var loaded: String? = null
    routeCardAction(store, onPlatformAction = {}, CardAction.OpenHub("h_party"), onOpenHub = { loaded = it })
    assertEquals(Route.Hubs, store.state.route)   // cross-surface nav (OpenHubs dispatched)
    assertEquals("h_party", loaded)               // engine load triggered with the hub id
  }

  @Test fun `OpenDetail still routes to the card detail stack (unchanged)`() {
    val store = createAppStore(AppState(cards = listOf(Card("c1", title = "X"))), debug = false)
    routeCardAction(store, onPlatformAction = {}, CardAction.OpenDetail("c1"))
    assertEquals(listOf("c1"), store.state.detailStack)
  }
}
