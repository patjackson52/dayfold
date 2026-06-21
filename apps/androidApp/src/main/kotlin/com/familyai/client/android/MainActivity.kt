package com.familyai.client.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.familyai.client.AndroidTokenStore
import com.familyai.client.AuthClient
import com.familyai.client.AuthEngine
import com.familyai.client.ContentStore
import com.familyai.client.DriverFactory
import com.familyai.client.FeedApp
import com.familyai.client.SyncClient
import com.familyai.client.SyncEngine
import com.familyai.client.createAppStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.reduxkotlin.devtools.inapp.DevToolsTrigger
import org.reduxkotlin.devtools.inapp.InAppConfig
import org.reduxkotlin.devtools.inapp.ReduxDevToolsHost

// Android shell — owns the store + AuthEngine + SyncEngine. AUTH-S5: the route
// gate drives sign-in/onboarding/feed; repeatOnLifecycle(STARTED) maps the
// Activity foreground/background to engine.resume()/pause().
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val store = createAppStore()
    val cs = ContentStore(DriverFactory(applicationContext).createDriver())
    val tokenStore = AndroidTokenStore(applicationContext)
    val authEngine = AuthEngine(
      store, AuthClient(BuildConfig.FAMILYAI_API), tokenStore,
      devSecret = BuildConfig.DEV_AUTH_SECRET.ifEmpty { null },
    )
    val legacyFam = BuildConfig.FAMILY_ID; val legacySecret = BuildConfig.HOUSEHOLD_SECRET
    val syncEngine = SyncEngine(
      store, cs,
      SyncClient(
        BuildConfig.FAMILYAI_API,
        familyId = { store.state.activeFamilyId ?: legacyFam.ifEmpty { null } },
        token = { store.state.session?.access ?: legacySecret.ifEmpty { null } },
      ),
    )

    syncEngine.start()
    lifecycleScope.launch { authEngine.restore() }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        syncEngine.resume()
        try { awaitCancellation() } finally { syncEngine.pause() }
      }
    }
    setContent {
      ReduxDevToolsHost(InAppConfig(triggers = setOf(DevToolsTrigger.BUBBLE))) {
        FeedApp(
          store,
          onSignIn = { provider -> lifecycleScope.launch { authEngine.signIn(provider); syncEngine.syncNow() } },
          onCreateFamily = { name -> lifecycleScope.launch { authEngine.createFamily(name); syncEngine.syncNow() } },
        )
      }
    }
  }
}
