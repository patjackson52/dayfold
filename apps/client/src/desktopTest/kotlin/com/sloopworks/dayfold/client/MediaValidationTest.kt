package com.sloopworks.dayfold.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

// ADR 0036: the image-URL allowlist is mirrored across server (WHATWG URL), CLI
// (java.net), and THIS client (a hand-rolled commonMain parser — no java.net on
// iOS/JS). "A parser differential IS the vulnerability." The server + CLI mirrors
// have evasion-corpus tests; this client mirror (the renderer that actually feeds
// Coil) had none. This pins it against the SAME corpus so the three stay lock-step,
// plus a few vectors the hand-rolled parser is uniquely exposed to.
class MediaValidationTest {
  private val HERO = "https://upload.wikimedia.org/wikipedia/commons/0/0c/Logo.png"

  @Test fun acceptsAllowlistedHttps() = assertNull(MediaValidation.imageUrlError(HERO))
  @Test fun acceptsCaseInsensitiveHost() = assertNull(MediaValidation.imageUrlError("https://UPLOAD.WIKIMEDIA.ORG/x.png"))
  @Test fun acceptsExplicit443() = assertNull(MediaValidation.imageUrlError("https://upload.wikimedia.org:443/x.png"))
  @Test fun acceptsTrailingDot() = assertNull(MediaValidation.imageUrlError("https://upload.wikimedia.org./x.jpg"))
  @Test fun acceptsNonSvgExtensions() {
    assertNull(MediaValidation.imageUrlError("https://upload.wikimedia.org/x.jpeg"))
    assertNull(MediaValidation.imageUrlError("https://upload.wikimedia.org/x.webp"))
  }

  @Test fun rejectsEvasionVectors() {
    val bad = listOf(
      // ── the server/CLI corpus (must stay aligned) ──
      "http://upload.wikimedia.org/x.png",                 // not https
      "data:image/png;base64,iVBORw0KGgo=",                // data:
      "javascript:alert(1)",                               // javascript:
      "https://upload.wikimedia.org@evil.com/x.png",       // userinfo smuggling → real host evil.com
      "https://evil.com/upload.wikimedia.org/x.png",       // path-as-host
      "https://upload.wikimedia.org.evil.com/x.png",       // suffix evasion
      "https://notupload.wikimedia.org/x.png",             // prefix evasion
      "https://commons.wikimedia.org/x.png",               // sibling subdomain (exact-host, not suffix)
      "https://upload.wikimedia.org:8443/x.png",           // alternate explicit port
      "https://upload.wikіmedia.org/x.png",           // cyrillic-i homograph → illegal (non-ASCII) host
      "https://upload.wikimedia.org/logo.svg",             // SVG (XSS surface)
      "https://upload.wikimedia.org/LOGO.SVG",             // SVG, uppercase
      "https://upload.wikimedia.org/a b.png",              // whitespace smuggling
      "https://upload.wikimedia.org/a\tb.png",             // tab (control) smuggling
      "https://upload.wikimedia.org/" + "a".repeat(2100),  // over-long
      // ── vectors the hand-rolled parser is uniquely exposed to ──
      "blob:https://upload.wikimedia.org/abc",             // blob: — scheme test must see "blob:https", not "https"
      "https://[::1]/x.png",                               // ip-literal host
      "https://[fd00::1]/x.png",                           // ip-literal host (ULA)
      "https://upload.wikimedia.org\\@evil.com/x.png",     // backslash smuggling
      "https:///x.png",                                    // empty authority
      "https://up\tload.wikimedia.org/x.png",              // control char inside host
      "https://upload.wikimedia.org%2eevil.com/x.png",     // percent-encoded dot in host
      "",                                                  // empty
    )
    for (u in bad) assertNotNull(MediaValidation.imageUrlError(u), "client MUST reject (parser differential = vuln): $u")
    assertNotNull(MediaValidation.imageUrlError(null))     // null → error
  }

  @Test fun safeImageUrlGatesCoil() {
    assertEquals(HERO, MediaValidation.safeImageUrl(HERO))            // allowlisted passes through
    assertNull(MediaValidation.safeImageUrl("https://evil.com/x.png")) // disallowed → null (Coil never sees it)
    assertNull(MediaValidation.safeImageUrl(null))
  }

  @Test fun iconSetAcceptReject() {
    for (n in MediaValidation.CURATED_ICONS) assertNull(MediaValidation.iconError(n))
    assertEquals(18, MediaValidation.CURATED_ICONS.size)
    assertNotNull(MediaValidation.iconError("medical_services"))   // a glyph, not a curated NAME
    assertNotNull(MediaValidation.iconError("nuke"))
  }

  @Test fun accentHexAcceptReject() {
    assertNull(MediaValidation.accentHexError("#1c6e8c"))
    assertNull(MediaValidation.accentHexError("#1C6E8C"))
    for (h in listOf("1c6e8c", "#1c6e8", "#1c6e8cc", "#zzzzzz", "red", "#fff")) assertNotNull(MediaValidation.accentHexError(h), h)
  }
}
