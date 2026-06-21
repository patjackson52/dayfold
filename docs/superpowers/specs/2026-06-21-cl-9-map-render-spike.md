# CL-9 — Map render strategy (spike + decision)

**Date:** 2026-06-21 · **Epic:** `planning/content-detail-epic.md` §CL-9 ·
**ADRs:** 0014 (private trigger engine / location posture), 0015 (E2EE),
0006 (render-don't-reason), 0020 (read-only M0). **Guardrail 3** (restricted
data / third-party data handling).

## Decision (TL;DR)

**M0 = keep the stylized `MapStrip()` placeholder + one-tap Navigate handoff.
Already shipped — no key, no cost, no third-party coordinate leak, honest
privacy chip. No code change.** Static map *images* are the M1 path, but only
behind a new ADR (third-party map-provider disclosure) and author-time
stamping. Embedded maps SDK is rejected for the foreseeable future. Self-host
(Protomaps) is parked as an M2+ escape hatch if third-party disclosure is
ever judged unacceptable.

This records the CL-9 decision and closes the spike. The DoD ("geo card +
detail render a map affordance + working Navigate handoff within the ADR 0014
privacy posture; decision recorded") is **met**: the affordance + handoff
already exist (`MapStrip`, `CardAction.Navigate`), and this note is the
record.

## What exists today (verified in code)

- `cards/TypedCards.kt :: MapStrip()` — a 92dp stylized surface (two
  decorative "roads" from `DayfoldExtendedColors.mapBackground/mapLine/
  mapRoad`, light+dark). `clearAndSetSemantics {}` → it is correctly
  invisible to a11y (decorative, not a real map).
- Used by `GeoCard` (Now strip) and `DetailScreen :: HeroMedia` (detail hero).
- Primary action: `TypedCardLogic` → `"Navigate" → CardAction.Navigate(address|label)`.
- Handoff vetting (CL-PLAT): `cardActionUri(Navigate)` →
  `geo:0,0?q=<percent-encoded place query>` — **never coordinates**, per ADR
  0014. OS maps app resolves the query. No key, no embedded view, no position.
- **Structural invariant (document where it would be broken):** `GeoPayload`
  *does* carry `lat`/`lng` (`Model.kt`), but the Navigate URI builder
  **deliberately ignores them** and uses `address ?: label` text only. Using
  lat/lng for "precision" would be an **ADR-0014 regression** (it would put a
  real coordinate into an outbound URI). A geo card with only lat/lng and no
  address/label yields no handoff (`null`) — fail-closed, not a coord leak.

So the *renderer* question ("CSS placeholder") is already answered in M0; the
open question CL-9 actually had to settle is **whether to upgrade the
placeholder to a real map, and at what privacy/cost price.**

## The three options

### A. Stylized placeholder (current M0) — CHOSEN for M0

- **Privacy:** zero third-party calls; the place coordinate never leaves our
  stack at render. Navigate sends a *text place query* (not lat/lng) to the
  OS maps app only on explicit tap.
- **Cost:** $0. No API key, no provider account, no per-request billing.
- **Cohesion:** matches the M0 "dumb renderer" posture (ADR 0020) and the
  OG-unfurl rule (CL-2: no server fetch / no client re-fetch). Themed via the
  existing `DayfoldExtendedColors` map tokens.
- **Honesty (ADR 0014/0015):** a placeholder makes no map-data claim, so no
  honesty risk. A privacy chip can truthfully say location data stays local.
- **Cost of NOT upgrading:** lower visual richness — a generic strip instead
  of the real street layout. Acceptable for a learning-lab M0; the Navigate
  handoff carries the actual utility.

### B. Static map image (third-party provider) — M1, ADR-gated

Provider landscape (June 2026, researched):

| Provider | Free tier | $/1k static | Caching allowed? | Attribution |
|---|---|---|---|---|
| Google Maps Static | 10k/mo | **$2.00** | **NO** (image caching prohibited) | "Google" baked in, non-removable |
| Mapbox Static Images | 50k/mo | $1.00 | Yes, ≤30 days | Mapbox + OSM |
| MapTiler | 100k/mo | ~$1.50 | Paid plans | OSM/MapTiler |
| **Geoapify** | 3k/day | **~$0.06–0.10/1k** | **Yes — store/redistribute freely** | "Powered by Geoapify" + OSM (white-label on paid) |
| **Stadia Maps** | 200k cred/mo (~10k img) | $0.30–0.60/1k | **Yes — special cacheable endpoint (paid)** | OSM + Stadia |

**Provenance (checked 2026-06-21):** free-tier sizes + the caching/attribution
policy columns are `[fact:<provider pricing+ToS pages>]`; all `$/1k` figures
are `[estimate]` — Google/Mapbox/MapTiler are list prices, Geoapify/Stadia are
inferred from plan-price ÷ included credits and vary with image size/markers.
The **Google "image caching prohibited"** claim is load-bearing (it eliminates
Google from the M1 shortlist, since author-time stamping *requires* caching the
image) → `[fact:cloud.google.com/maps-platform/terms + Maps Static caching
guidance, checked 2026-06-21]`. Provider ToS/pricing drift — **re-verify at
CL-9b time**, do not treat these as durable.

- **The privacy crux (why this is ADR-class, not a task tweak):** ADR 0014
  draws a hard line — the *device's live position* never leaves, but
  *authored place coordinates* ARE family-scoped content that goes to **our
  server** (encrypted at rest). A static-map call adds a **new actor**: the
  coordinate must be transmitted to the **provider's** servers to render the
  image. This is intrinsic to *every* hosted provider above — there is no
  keyless/no-call static endpoint. That is a **third-party data flow ADR 0014
  did not authorize** and touches Guardrail 3 (third-party data handling) +
  the "privacy by architecture" brand promise. It must be an explicit ADR
  with disclosure, not a silent dependency.
- **The clean M1 design (mirrors CL-2 OG-unfurl):** stamp the static image
  **at author time** in the authoring loop (over the operator's OWN data),
  store it immutable; the **server never fetches at render (no SSRF)** and the
  **client never calls a map provider at render (no key on device, no
  render-time leak/timing oracle).** Under that model the coordinate leaves to
  the provider **once, at author time, over the operator's own data** — the
  same posture that keeps email authoring clear of CASA (Guardrail 3 analog).
  Pick a **caching-allowed** provider (Geoapify or Stadia; Google is out — it
  forbids caching the image, which breaks author-time stamping).
- **Provider-logging exposure (the actual new exposure — do not under-state):**
  the OG-unfurl analogy is imperfect. OG-unfurl fetches a URL the operator is
  *already linking to publicly* (non-sensitive by nature); a static-map stamp
  transmits a **home/school place coordinate**, which ADR 0014 flags as
  sensitive family content that must be **encrypted at rest, family-scoped, and
  never logged**. Even "once, at author time, over the operator's own data,"
  that coordinate **appears in the map provider's request logs** — every
  provider logs request params. That, not chip wording, is the real new
  exposure, and it must be assessed against ADR 0014's "never logged" property
  before CL-9b ships.
- **Honesty follow:** any privacy chip on a geo card with a real map must NOT
  imply the location stayed local — it left to a map provider at author time.
  Chip copy gets a CL-6-style audit when this lands.
- **E2EE follow (ADR 0015) — *speculative M1 detail, not M0 scope*:** the
  stamped image URL/bytes are a ciphertext-candidate field (it encodes a
  place) — mark `"x-e2e":"ciphertext"` in schema so codegen preserves it,
  consistent with `body`/place `label/lat/lng`. (Belongs in the CL-9b
  ADR/spec; recorded here so it is not forgotten.)

### C. Embedded maps SDK (Google Maps / MapLibre native) — REJECTED

- Heavy per-platform dependency (Android Maps SDK, MapKit/MapLibre iOS, a web
  map lib) — large binary, licensing, per-platform parity work; directly
  fights the "smallest correct design" and the single-`:client` KMP goal.
- A live interactive map invites render-time provider calls and can leak the
  device viewport/position — the exact thing ADR 0014 forecloses.
- No M0/M1 product need: the OS maps app (via Navigate) already gives real
  interactive maps + routing for free, on the user's own map app + account.
- **Rejected** unless a future surface genuinely needs an *in-app* interactive
  map (none planned); even then, prefer MapLibre + self-host tiles over a
  proprietary SDK.

## Q: Why not an interactive 3P map (Google/Mapbox SDK) for the strip — wouldn't it enable a fluid morph into detail + an interactive detail map?

Investigated directly (operator question, 2026-06-21). Answer: **no — an
interactive map *fights* the fluid transition; the morph is achieved better by
a static image.** Three independent blockers, then it also loses on
privacy/cost:

1. **Interactive map cannot participate in the shared-element morph.** A native
   map is a platform view punched into Compose via `AndroidView` (Android) /
   `UIKitView` (iOS). Android's shared-elements docs name this a flat
   limitation: *"No interoperability between Views and Compose is supported …
   including any composable that wraps `AndroidView`."*
   `[fact:developer.android.com/develop/ui/compose/animation/shared-elements]`
   → snap/flicker/clip + GL-surface artifacts mid-transition. A plain `Image`
   is a first-class `sharedElement()` citizen and morphs cleanly. **The
   animation you want *requires* the static path.**
2. **Maps-in-a-list is a Google-named anti-pattern.** Google's own fix is
   **Lite Mode** = *"a bitmap image of a map … cannot zoom or pan,"* recommended
   *"when you want a number of maps in a stream, or a map too small for
   meaningful interaction."* `[fact:developers.google.com/maps/documentation/android-sdk/lite]`
   N live GL map surfaces in a scrolling feed = memory/recompose blowup (hits
   our perf review dimension).
3. **No cross-platform live-map widget for us.** No unified interactive-map
   composable spanning Android + iOS + **Wasm**. Best option (MapLibre Compose)
   is Web ~20% complete, **Wasm unsupported** `[fact:github.com/maplibre/maplibre-compose]`
   → our Web target would have no interactive map at all; even the "unified"
   API resolves to per-platform native views.

Cost, for completeness: Google **Dynamic Maps = $7/1k map loads** (10k free/mo)
`[fact:woosmap.com pricing breakdown, checked 2026-06-21]`; a pan/zoom session
is one load, but each card instance = one load → scales with feed size. Static
is the cheaper SKU.

**Where an interactive map *could* live (if ever):** a single **dedicated
full-screen surface** (one instance, Android/iOS only), never the strip — and
still behind the disclosure ADR below. For M0/M1 the **Navigate handoff to the
OS maps app** already delivers full interactivity (pan/zoom/route) on the
user's own map app + account, for free, with no key and no render-time leak.

## Privacy — data-flow analysis (what is shared, with whom, alternatives)

The operator asked to examine this precisely. The sensitive payload is the
**authored place coordinate** (lat/lng of a venue/school/store) — this is
*content* (ADR 0014 lets it reach our server, encrypted at rest, family-
scoped). It is **not** the device's live position, which ADR 0014 keeps on the
device forever. The privacy question for maps is: *does rendering a map cause
that place coordinate (and surrounding metadata) to reach a **new** third
party, and under whose identity?*

### What is transmitted, to whom, and when — per option

| Option | Place coord leaves to a 3rd party? | When / how often | Whose identity is attached | Other metadata sent |
|---|---|---|---|---|
| **A. Placeholder + Navigate** *(M0, current)* | **No** at render. On Navigate tap, only a **text place query** (e.g. "Riverside Park"), never lat/lng | only on explicit tap | the **user's own** maps app/account (Google/Apple Maps) — their existing relationship, not ours | whatever the user's maps app collects in its own session (out of our hands) |
| **B-render. Static image fetched at render** *(NOT recommended)* | **Yes** | every card render, per device | the **family member's device** (their IP) + our app's embedded key | device IP, API key, User-Agent, timestamp, referer |
| **B-author. Static image stamped at author time** *(CL-9b path)* | **Yes, once** | once, at author time, in the authoring loop | our **author/server** identity (server IP + author-side key) — **not** the family member's device | server IP, key, timestamp at author time only; render serves bytes from *our* server |
| **C. Interactive SDK map** | **Yes, continuously** | every pan/zoom tile fetch, per device, per session | the **family member's device** (IP) + embedded key; if Google + signed-in, correlatable to their Google identity | viewport bbox stream, device IP, key, UA, session — continuous telemetry |

**The exact data items a 3rd-party map provider receives** (for B/C — A sends
none): (1) the **place coordinate** or a viewport derived from it; (2) the
**requester's IP** — at *author* time that is our server's IP (B-author), at
*render* time it is the family member's device IP (B-render/C), revealing their
network/rough location; (3) the **API key** identifying our app/billing
account; (4) **User-Agent / timestamp / referer**; and for **C**, a continuous
**interaction stream** (every pan/zoom) that profiles the session.

**Who the "who" is matters.** Candidate providers and their posture:
- **Google** — also the family's **auth IdP** (Firebase Google sign-in, ADR
  0023) and the likely Calendar/email source. The correlation risk has **two
  strengths** (don't conflate): at **render / interactive (B-render/C)** with
  the device signed in, the request carries the family member's IP and can be
  tied to their **known Google identity** — the strong case. At **author time
  (B-author)** it instead ties the place graph to **our** Google billing
  account — weaker, but it still concentrates the family's data in the same
  ad-tech vendor that is already their IdP/Calendar source, counter to "privacy
  by architecture." **Avoid Google for maps either way.** Google also forbids
  caching the static image (breaks author-time stamping). `[fact:cloud.google.com/maps-platform/terms]`
- **OSM-based, non-ad-tech providers (Geoapify, MapTiler, Stadia Maps)** —
  OpenStreetMap data; business model is **API fees, not ad profiling** (the
  load-bearing point); caching-allowed; clearer retention terms. Jurisdictions
  vary — Geoapify EU (Cyprus), MapTiler Switzerland, **Stadia Maps
  US-headquartered** `[fact:provider company/imprint pages, checked 2026-06-21]`
  — so "EU data residency" is **not** uniform; the privacy plus here is the
  non-ad-tech model, not a blanket EU claim. **Preferred** over Google if a 3rd
  party is used at all.

### Alternatives — least to most provider exposure

1. **Placeholder + Navigate handoff (A) — ZERO provider exposure from our app**
   *(current M0)*. We send no coordinate, no key, no device IP. The user's
   chosen maps app sees only a text query, only on tap, under the user's own
   account. **This is the privacy-optimal real option** and already shipped.
2. **Self-host tiles / render (Protomaps + MapLibre on *our* infra) — ZERO
   third-party exposure even for a real map.** Render the static map image from
   OSM data on our own servers; the coordinate never leaves our stack. Cost =
   ops (run a renderer/tile store). The only way to get *real map imagery* with
   no third party in the loop. Parked as M2+ (over-built for solo M0/M1).
3. **Author-time static stamp via an OSM/non-ad provider (B-author, CL-9b).**
   Provider sees the coordinate **once**, tied to **our author identity** (not
   the family device), at author time; render serves bytes from our server (no
   key on device, no render-time leak, no SSRF). Residual exposure = that one
   author-time request sits in the provider's logs. Mitigations: pick a
   non-ad-tech provider (Geoapify/MapTiler), **coarsen the coordinate** (snap to
   a grid / show approximate area) so the logged point is fuzzed, and disclose.
   **Caveat — coarsening fuzzes the *coordinate*, not the *place*:** if a place
   label or address rides the request (a marker label) or sits on the card
   itself ("Lincoln Elementary"), the venue is still identifiable regardless of
   grid-snapping. Coarsening lowers point precision; it does not anonymize the
   disclosure of *which place* — only authoring without a label would do that.
4. **Render-time static (B-render) — avoid.** Adds the family device IP + a
   client key to every render for no UX gain over B-author.
5. **Interactive SDK (C) — worst.** Continuous per-device telemetry, key on
   device, ad-tech correlation if Google. Rejected.

**Defense-in-depth knobs for B-author (the only 3rd-party option we'd
consider):** (a) coordinate coarsening before the stamp request; (b) non-ad
provider; (c) author-side fetch only (device never calls a map host); (d)
immutable stored image marked `"x-e2e":"ciphertext"` (ADR 0015); (e) honest
privacy-chip copy — *not* "location never leaves" (false for a real map), but
e.g. "map image generated when this card was created."

**Bottom line on privacy:** today's placeholder + Navigate (A) leaks **nothing**
to a map vendor and is also the better morph engine. Any real in-app map adds a
third party; if that's ever wanted, **self-host (2)** removes the third party
entirely, and **author-time stamp via a non-Google OSM provider with coordinate
coarsening (3)** is the acceptable middle — both **ADR-gated**, never agent-
decided.

## Self-host escape hatch (Protomaps / PMTiles) — parked, M2+

The only path where the coordinate never leaves our infrastructure even at
author time: render from OSM data locally (Protomaps PMTiles single-file +
MapLibre/tileserver-gl rasterize to PNG). Cost ≈ storage/bandwidth
("practically free" for one region) but real ops: own the renderer, style,
and a render service. Over-built for a solo learning-lab M0/M1. Hold as the
fallback if third-party disclosure (option B) is ever judged unacceptable.

## Recommendation & follow-ups

1. **M0: ship as-is** (placeholder + Navigate). No code, no spend, no new
   permission. ✅ already satisfied.
2. **M1 (deferred, ADR-gated):** `CL-9b` — author-time-stamped static map
   image. Prereqs, in order: (a) **new ADR** for third-party map-provider
   disclosure (extends ADR 0014's place-coord boundary to "+ one map
   provider, at author time"); (b) provider pick (Geoapify or Stadia —
   caching-allowed, cheap, OSM-attributed); (c) author-loop stamps the image,
   stores immutable, server/client never fetch at render (CL-2 OG-unfurl
   pattern); (d) schema `"x-e2e":"ciphertext"` on the image field (ADR 0015);
   (e) privacy-chip honesty audit (ADR 0014/0015); (f) **accept + assess the
   provider-logging exposure** — the place coordinate lands in the map
   provider's request logs at author time, which collides with ADR 0014's
   "place coords never logged" property; this is the genuine new exposure the
   ADR must weigh (beyond chip copy). **Not agent-decidable** — the ADR is
   operator-gated.
3. **No change to ADR 0014.** This spike operationalizes it; it does not move
   the line.

## Review & verification

- Doc-only; no app code changed. The load-bearing handoff invariant was
  **exercised, not asserted**: `./gradlew :client:desktopTest --tests
  PlatformActionsTest --tests TypedCardLogicTest` → **BUILD SUCCESSFUL**
  (2026-06-21), including `navigate is a percent-encoded geo query, never
  coordinates`. Other geo coverage (`FeedSnapshotTest` geo card + `detail-geo`,
  `ContentStoreTest` geo round-trip) is unaffected by a doc-only change.
- Pre-impl + final adversarial review of this note's reasoning (privacy crux,
  provider facts, option scoring) per the build loop.
