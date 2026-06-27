package com.sloopworks.dayfold.cli

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ADR 0037 — the semver-compare that drives the update nudge + `dayfold update`.
class UpdateTest {
  @Test fun `older detects a strictly-older clean semver`() {
    assertTrue(isOlder("0.1.0", "0.1.1"))
    assertTrue(isOlder("0.1.0", "0.2.0"))
    assertTrue(isOlder("0.9.9", "1.0.0"))
    assertTrue(isOlder("1.0.0", "1.0.10"))   // numeric, not lexical
  }

  @Test fun `equal or newer is not older`() {
    assertFalse(isOlder("0.1.0", "0.1.0"))
    assertFalse(isOlder("1.2.0", "1.1.9"))
    assertFalse(isOlder("2.0.0", "1.9.9"))
  }

  @Test fun `dev or edge builds are never older (never nagged)`() {
    assertFalse(isOlder("0.0.0-dev", "9.9.9"))
    assertFalse(isOlder("0.0.0-edge.abc1234", "9.9.9"))
    assertFalse(isOlder("unknown", "1.0.0"))
    assertFalse(isOlder("0.1.0", "not-a-version"))
  }

  @Test fun `usage advertises the update command`() {
    assertTrue(USAGE.contains("update"), "USAGE must list `update`")
  }
}
