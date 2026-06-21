package com.familyai.client

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.familyai.client.cards.CardAction
import com.familyai.client.cards.DetailScreen
import com.familyai.client.theme.DayfoldTheme
import org.reduxkotlin.Store
import org.reduxkotlin.compose.selectorState

// Route a card's CardAction: OpenDetail = in-app nav → store; everything else =
// an OS handoff → the shell's PlatformActions. Extracted (non-Composable) so the
// split is unit-testable. Returns Unit (store.dispatch returns the action).
internal fun routeCardAction(store: Store<AppState>, onPlatformAction: (CardAction) -> Unit, action: CardAction) {
  if (action is CardAction.OpenDetail) store.dispatch(NavToDetail(action.cardId))
  else onPlatformAction(action)
}

// f(store.state) -> UI via redux-kotlin-compose `store.selectorState { }` — a
// reactive Compose projection of the single state source (the whole AppState
// here; swap to per-field `fieldState`/narrower selectors to scope recomposition).
// Every shell (desktop, Android, iOS) renders this one connected composable,
// wrapped once in the Dayfold theme (ADR 0022 D5).
@Composable
fun FeedApp(store: Store<AppState>, onPlatformAction: (CardAction) -> Unit = {}) {
  val state by store.selectorState { it }
  // One stable handler (remembered so feed/detail stay skippable): OpenDetail is
  // in-app nav → dispatched to the store; every other CardAction is an OS handoff
  // → the shell's PlatformActions. NOTE: the whole-state `selectorState { it }`
  // subscription is the pre-existing M0 pattern; scoping it (feedCards vs
  // currentDetailCard) to shrink recomposition is a tracked perf follow.
  val handle = remember(store, onPlatformAction) {
    fun(action: CardAction) = routeCardAction(store, onPlatformAction, action)
  }
  DayfoldTheme {
    // CL-7 base transition: animate the feed↔detail swap (keyed on the open card
    // id; null = feed) instead of a hard cut. Open slightly slower than back
    // (asymmetric, per the design). The full shared-element container transform +
    // predictive-back scrub is the CL-7b polish follow.
    val detail = currentDetailCard(state)
    AnimatedContent(
      targetState = detail?.id,
      transitionSpec = {
        val opening = targetState != null
        val dur = if (opening) 320 else 240
        (fadeIn(tween(dur)) + slideInVertically(tween(dur)) { h -> h / 12 }) togetherWith fadeOut(tween(dur))
      },
      label = "feed-detail",
    ) { id ->
      val card = id?.let { cid -> state.cards.find { it.id == cid } }
      if (card != null) DetailScreen(card, onBack = { store.dispatch(NavBack) }, onAction = handle)
      else FeedScreen(state, onAction = handle)
    }
  }
}
