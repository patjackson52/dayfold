package com.familyai.client

import org.reduxkotlin.Store
import org.reduxkotlin.applyMiddleware
import org.reduxkotlin.compose
import org.reduxkotlin.middleware
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.threadsafe.createThreadSafeStore

// Hand-written root reducer (locked decision: no combineReducers). Card data
// arrives only via CardsLoaded (DB→store bridge); sync actions carry status only.
fun rootReducer(state: AppState, action: Any): AppState = when (action) {
  is SyncStarted -> state.copy(syncing = true, error = null)
  is SyncSucceeded -> state.copy(syncing = false, error = null)
  is SyncFailed -> state.copy(syncing = false, error = action.message)
  is CardsLoaded -> state.copy(cards = action.cards)   // DB is truth → full replace
  else -> state
}

// AGENT-readable text action log → stdout (desktop) / logcat tag System.out
// (Android: `adb logcat -s System.out`). Cheap text feedback on the redux loop
// for future sessions — no screenshot/vision needed. Pairs with the on-screen
// devtools drawer (ADR 0019).
private val actionLog = middleware<AppState> { store, next, action ->
  val r = next(action)
  val s = store.state
  println("[redux] ${action::class.simpleName} → cards=${s.cards.size} syncing=${s.syncing} error=${s.error}")
  r
}

// [F5] thread-safe store: the SyncClient effect dispatches from Dispatchers.IO
// while the Compose UI reads on main — needs synchronized dispatch.
// `debug=true` composes the redux-kotlin-devtools `devTools()` enhancer (records
// to DevToolsHub → in-app drawer) WITH the text action-log middleware. Release
// passes debug=false (neither).
fun createAppStore(initial: AppState = AppState(), debug: Boolean = true): Store<AppState> =
  if (debug) createThreadSafeStore(
    ::rootReducer, initial,
    compose(devTools(DevToolsConfig(instanceId = "family-ai", name = "Family AI")), applyMiddleware(actionLog)),
  )
  else createThreadSafeStore(::rootReducer, initial)
