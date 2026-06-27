package com.sloopworks.dayfold.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// The committed example feed cards (apps/cli/examples/feed/) must stay valid against
// the generated card schema — they're a ready-to-push reference for the Now feed, and
// one (c2) carries a target deep-link to the example hub (sample-college). Locks both
// the examples and validateCard. Gradle runs CLI tests with workdir = apps/cli.
class ExampleCardsValidateTest {
  private val dir = File("examples/feed")
  private fun read(name: String) = File(dir, name).readText()
  private fun ok(name: String, type: String) {
    val errs = validateCard(type, read(name))
    assertTrue(errs.isEmpty(), "$name failed validateCard: $errs")
  }

  @Test fun `example feed cards validate against the generated schema`() {
    assertTrue(dir.isDirectory, "examples/feed not found at ${dir.absolutePath}")
    ok("c1-rsvp.json", "invite")
    ok("c2-finaid.json", "contact")
  }
}
