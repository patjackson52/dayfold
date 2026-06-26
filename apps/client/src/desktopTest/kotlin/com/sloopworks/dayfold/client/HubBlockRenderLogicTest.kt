package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Pure render-decision logic for hub blocks (no Compose). Locks the live-data bug:
// a typed block (contact/document/checklist…) whose content the author put in
// body_md — with no structured payload — must render its markdown, not an empty
// "Contact"/"document" typed layout.
class HubBlockRenderLogicTest {
  private fun blk(type: String, bodyMd: String? = null, payload: BlockPayload? = null) =
    HubBlock(id = "b", type = type, bodyMd = bodyMd, payload = payload)

  @Test fun `typed block with content only in body_md falls back to markdown`() {
    assertTrue(blockFallsBackToBodyMd(blk("contact", bodyMd = "**Butler offices** — Office of Admissions")))
    assertTrue(blockFallsBackToBodyMd(blk("document", bodyMd = "📄 2026-27 Immunization Requirements")))
    assertTrue(blockFallsBackToBodyMd(blk("checklist", bodyMd = "- [ ] Submit FAFSA")))
    assertTrue(blockFallsBackToBodyMd(blk("link", bodyMd = "see the housing portal")))
  }

  @Test fun `a structured payload renders typed, not the markdown fallback`() {
    assertFalse(blockFallsBackToBodyMd(blk("contact", bodyMd = "x", payload = BlockPayload(name = "Admissions"))))
    assertFalse(blockFallsBackToBodyMd(blk("link", bodyMd = "x", payload = BlockPayload(url = "https://butler.edu"))))
    assertFalse(blockFallsBackToBodyMd(blk("document", bodyMd = "x", payload = BlockPayload(docRef = "ref://imm"))))
    assertFalse(blockFallsBackToBodyMd(blk("checklist", bodyMd = "x", payload = BlockPayload(items = listOf(ChecklistItem(text = "do it"))))))
  }

  @Test fun `markdown + empty blocks do NOT use the typed-fallback path`() {
    assertFalse(blockFallsBackToBodyMd(blk("markdown", bodyMd = "hi")))   // its own branch renders body_md
    assertFalse(blockFallsBackToBodyMd(blk("contact", bodyMd = null)))    // nothing to fall back to → keep typed placeholder
    assertFalse(blockFallsBackToBodyMd(blk("contact", bodyMd = "   ")))   // blank
  }
}
