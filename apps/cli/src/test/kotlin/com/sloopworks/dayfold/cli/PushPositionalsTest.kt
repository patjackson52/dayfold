package com.sloopworks.dayfold.cli

import kotlin.test.Test
import kotlin.test.assertEquals

// `push <id> <file> [--hub|--section|--block] [--type X]` — the id+file are the positionals.
// Like pushResource (resource), their extraction is flag-position-agnostic: a `--hub` before
// the id must NOT be mistaken for it, and `--type`'s VALUE must not be mistaken for the file.
class PushPositionalsTest {
  @Test fun `id and file are the positionals when flags come last`() {
    assertEquals(listOf("c1", "card.json"), pushPositionals(arrayOf("push", "c1", "card.json")))
    assertEquals(listOf("c1", "card.json"), pushPositionals(arrayOf("push", "c1", "card.json", "--type", "invite")))
    assertEquals(listOf("h1", "hub.json"), pushPositionals(arrayOf("push", "h1", "hub.json", "--hub")))
  }

  @Test fun `flags before or between the positionals are skipped (incl --type's value)`() {
    assertEquals(listOf("h1", "hub.json"), pushPositionals(arrayOf("push", "--hub", "h1", "hub.json")))         // flag-first
    assertEquals(listOf("c1", "card.json"), pushPositionals(arrayOf("push", "c1", "--type", "invite", "card.json"))) // --type interspersed
    assertEquals(listOf("h1", "hub.json"), pushPositionals(arrayOf("push", "h1", "hub.json", "--hub", "--type", "file")))
  }

  @Test fun `missing positionals → fewer entries (caller falls to usage)`() {
    assertEquals(listOf("c1"), pushPositionals(arrayOf("push", "c1")))   // no file
    assertEquals(emptyList(), pushPositionals(arrayOf("push", "--hub")))  // only a flag
  }
}
