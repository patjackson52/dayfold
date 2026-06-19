package com.familyai.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Desktop shell — lets the operator preview the feed on a laptop (no device).
// The Android/iOS shells reuse FeedScreen + the same store/SyncClient.
fun main() = application {
  val store = remember { createAppStore() }
  var state by remember { mutableStateOf(store.state) }
  DisposableEffect(Unit) {
    val unsub = store.subscribe { state = store.state }
    onDispose { unsub() }
  }
  LaunchedEffect(Unit) {
    val api = System.getenv("FAMILYAI_API")
    val fam = System.getenv("FAMILY_ID")
    val sec = System.getenv("HOUSEHOLD_SECRET")
    if (api != null && fam != null && sec != null) {
      withContext(Dispatchers.IO) { SyncClient(api, fam, sec).sync(store) }
    }
  }
  Window(onCloseRequest = ::exitApplication, title = "family-ai-dashboard") {
    MaterialTheme { FeedScreen(state) }
  }
}
