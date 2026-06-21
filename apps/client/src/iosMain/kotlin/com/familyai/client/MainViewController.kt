package com.familyai.client

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

// iOS entry — the SHARED FeedApp with the AUTH-S5 route gate. Session persists via
// NSUserDefaults (IosTokenStore). The dev-token sign-in secret + a real API base
// stay unset here (iOS run config is operator-gated on Mac/Xcode), so sign-in is
// inert on-device this slice; the gate + onboarding UI + restore are all wired.
fun MainViewController(): UIViewController = ComposeUIViewController {
  val store = remember { createAppStore() }
  val tokenStore = remember { IosTokenStore() }
  val authEngine = remember { AuthEngine(store, AuthClient(""), tokenStore, devSecret = null) }
  val syncEngine = remember {
    SyncEngine(
      store, ContentStore(DriverFactory().createDriver()),
      SyncClient("", familyId = { store.state.activeFamilyId }, token = { store.state.session?.access }),
    )
  }
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) {
    syncEngine.start()
    authEngine.restore()
    syncEngine.resume()
  }
  FeedApp(
    store,
    onSignIn = { provider -> scope.launch { authEngine.signIn(provider); syncEngine.syncNow() } },
    onCreateFamily = { name -> scope.launch { authEngine.createFamily(name); syncEngine.syncNow() } },
  )
}
