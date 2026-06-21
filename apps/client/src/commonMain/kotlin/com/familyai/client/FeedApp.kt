package com.familyai.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.familyai.client.theme.DayfoldTheme
import org.reduxkotlin.Store
import org.reduxkotlin.compose.selectorState

// f(store.state) -> UI via redux-kotlin-compose `store.selectorState { }` — a
// reactive Compose projection of the single state source (the whole AppState
// here; swap to per-field `fieldState`/narrower selectors to scope recomposition).
// Every shell (desktop, Android, iOS) renders this one connected composable,
// wrapped once in the Dayfold theme (ADR 0022 D5).
//
// AUTH-S5: the app's first navigation — a pure when(route) gate (no nav library,
// ADR 0013). Effect callbacks (sign-in / create-family) are injected by the
// platform entrypoint (T6), which launches the suspend AuthEngine calls; they
// default to no-ops so the screens stay snapshot-testable in isolation.
@Composable
fun FeedApp(
  store: Store<AppState>,
  onSignIn: (String) -> Unit = {},
  onCreateFamily: (String) -> Unit = {},
) {
  val state by store.selectorState { it }
  DayfoldTheme {
    when (state.route) {
      Route.Loading -> SplashScreen()
      Route.SignIn -> SignInScreen(busy = state.authBusy, error = state.authError, onProvider = onSignIn)
      Route.CreateFamily -> CreateFamilyScreen(busy = state.authBusy, error = state.authError, onCreate = onCreateFamily)
      Route.Feed -> FeedScreen(state)
    }
  }
}
