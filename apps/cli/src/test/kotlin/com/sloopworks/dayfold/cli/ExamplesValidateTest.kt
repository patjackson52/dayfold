package com.sloopworks.dayfold.cli

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// The committed example hub (apps/cli/examples/) must stay valid — it's a
// ready-to-push reference + it exercises the canonical-schema block payloads
// (ADR 0035 Option C). This locks both: a broken example, or a validation
// regression, fails here. Gradle runs CLI tests with workdir = apps/cli.
class ExamplesValidateTest {
  private val dir = File("examples/hub-college")
  private fun read(name: String) = File(dir, name).readText()
  private fun ok(name: String, resource: String) {
    val errs = validateHubTree(resource, read(name))
    assertTrue(errs.isEmpty(), "$name failed validation: $errs")
  }

  @Test fun `every committed example validates (hub, sections, blocks)`() {
    assertTrue(dir.isDirectory, "examples dir not found at ${dir.absolutePath}")
    ok("hub.json", "hubs")
    ok("s1-dates.json", "sections"); ok("s2-money.json", "sections")
    val blocks = dir.listFiles { f -> f.name.startsWith("b") && f.name.endsWith(".json") }!!
    assertTrue(blocks.size >= 5, "expected the example block set")
    for (b in blocks.sortedBy { it.name }) ok(b.name, "blocks")
  }
}
