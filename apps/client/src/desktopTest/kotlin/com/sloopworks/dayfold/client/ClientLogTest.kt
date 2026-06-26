package com.sloopworks.dayfold.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientLogTest {
  @AfterTest fun reset() { ClientLog.sink = null }   // it's a global object — don't leak the sink

  @Test fun `log forwards tag and message to the installed host sink`() {
    val seen = mutableListOf<Pair<String, String>>()
    ClientLog.sink = { tag, msg -> seen += tag to msg }
    ClientLog.log("sync", "401 — refreshing access token")
    ClientLog.log("redux", "OpenHub → cards=3 syncing=false error=null")
    assertEquals(
      listOf(
        "sync" to "401 — refreshing access token",
        "redux" to "OpenHub → cards=3 syncing=false error=null",
      ),
      seen,
    )
  }

  @Test fun `log with no sink installed does not throw (stdout-only path)`() {
    ClientLog.sink = null
    ClientLog.log("sync", "no sink here")   // must be a clean no-op for the host sink
  }
}
