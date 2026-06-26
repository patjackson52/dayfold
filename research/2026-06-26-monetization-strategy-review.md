# Monetization Strategy Review — Dayfold (2026-06-26)

**Class:** strategy / pricing-adjacent. **Authority:** evidence (dated
snapshot). **Decision status:** *all pricing & billing-mechanic choices here
are operator-gated and ADR-class* (hard guardrail #2 — agents draft, operator
decides). Nothing in this doc auto-applies. Feeds **A5 (margin model)** and
**B6 (pricing ADR)**; routed to the operator via **INB-24**.

Citations labeled `[fact:source]` / `[estimate]` / `[assumption]` per process.

---

## TL;DR / recommendation

1. **Subscription, not paid-up-front.** A one-time price cannot fund recurring
   LLM + infra + a multi-member service; the category is subscription-priced;
   subscription enables trials and annual-first billing. Paid-up-front rejected.
2. **One flat *per-family* price — not per-seat.** Dayfold's tenant *is* the
   family (one account, per-member logins — ADR 0004/0011). The family is the
   natural billing unit; per-seat pricing would penalize the exact multi-member
   differentiator the product is built on. **Who pays = the family owner**
   (the inviter/owner in the ADR 0011 model); all adult members unlock.
3. **Make the entitlement server-side and family-keyed — decouple it from any
   one store account.** This is the single most important architectural call,
   it's cheap to honor now, and it keeps every billing rail open. A member on
   Android in an iPhone-owner's family must still unlock; only a server-side,
   tenant-scoped entitlement does that cleanly.
4. **Primary billing rail at launch ≈ web/direct (Stripe or a
   merchant-of-record), with store IAP as an optional convenience rail.** For a
   *multi-member, cross-platform, server-entitled* product, web-direct is both
   the lowest-fee and the most natural fit, and it's now clearly policy-permitted
   in the US (see §4). **Yes — payment can be taken out of the app store**, and
   for this product it is arguably the *better* architecture, not a hack.
5. **Tiering: a single paid family tier at launch.** No Good/Better/Best at MVP
   — it adds ops load (guardrail: <2 hr/wk steady state) without enough surface
   to differentiate. Free *concierge/beta* now (we're pre-revenue anyway), one
   paid tier at G-LAUNCH, optional "founding-family" annual. Reserve a future
   "plus" lane for integrations/extra hubs but **defer it**.
6. **None of this bites until G-LAUNCH** (a post-build, far-off milestone). The
   only thing needed *now* is to not bake store-IAP assumptions into the data
   model. Decide the rail at launch with real numbers.

---

## 1. Why Dayfold's monetization is not the generic app case

Three repo facts make the standard "just use IAP" advice wrong here:

- **The tenant is the family, not the user** (ADR 0004 §3, ADR 0011). One
  household, multiple adult logins, cross-platform (Android/iOS/web). The thing
  being sold is *one family's dashboard*, consumed on many devices/OSes.
- **Solo operator, <2 hr/wk steady-state, <$50/mo infra**
  (`goals-and-constraints.md`). Ops simplicity and tax/compliance offload have
  real monetary value here — this changes the store-fee calculus (§4).
- **Constitution constraints are binding:** no dark patterns, clean cancel +
  export, never spam, family owns its relationship
  (`business-constitution.md`). This rules out forced-annual, cancellation
  friction, and engagement-bait freemium funnels.
- **Open-source posture (ADR 0032):** monetize via **hosted SaaS only**;
  self-host doesn't overlap the convenience ICP. Reinforces a server-side,
  account-based entitlement model.

**Anchors (already established):** category ceiling ~$39–79/yr
(Cozi Gold $39/yr, Maple+ $40/yr, Skylight Plus $79/yr hardware-bundled)
`[fact:vendor, via pricing-structure.md]`; LLM cost trivial (~$0.006/briefing,
Haiku 4.5) `[fact:platform.claude.com/pricing]`; contribution margin 78–91% at
$5–15/mo `[estimate, pricing-structure.md]`; **store commission is the real
margin threat, not cost** `[estimate, pricing-structure.md]`; price band
$5–10/family/mo, annual-first ~$39–59/yr `[estimate, INB-4 → B6]`.

---

## 2. Paid app vs. subscription

| Model | Fit for Dayfold | Verdict |
|---|---|---|
| **Paid up-front (one-time)** | Cannot fund recurring LLM/infra/service; no trial; no annual-churn amortization; off-category | **Reject** |
| **Subscription (recurring)** | Matches recurring cost + category norm; enables free trial + annual-first; aligns with hosted-SaaS posture | **Recommend** |
| **Freemium (free tier + paid)** | Funnel benefit, but raises infra/support load on a 2–4% converting free base, and the constitution forbids engagement-bait | **Defer / narrow** |

**Recommended shape:** free **concierge/beta** during dogfood + friendly-family
phase (we are pre-revenue by design until G1b/G-LAUNCH), converting to a single
paid subscription at launch. Annual-first billing recommended — amortizes the
Stripe $0.30/charge and cuts effective churn `[estimate, pricing-structure.md]`.
Keep a monthly option (no dark-pattern lock-in — constitution).

---

## 3. Tiered approach

**Recommendation: one paid family tier at launch.** Rationale:

- The product surface (one briefing + a few hubs) doesn't yet have enough
  feature axes to split a credible Good/Better/Best without inventing
  paywalled friction — which the "calm, no dark patterns" identity forbids.
- Every extra tier is recurring ops + support + billing-edge-case load against
  a hard <2 hr/wk budget.
- A single clear per-family price is the easiest thing to message and the
  easiest to reason about for WTP conversations (A4).

**Future tier lane (defer, don't build):** if/when integrations land
(Calendar auto-ingest, more hubs, geo triggers — all post-MVP), a "Dayfold
Plus" upsell becomes legible. Note it in B-phase strategy; do not pre-commit
schema or pricing now.

**Rejected:** per-seat / per-member pricing. It directly taxes the multi-member
differentiator and complicates billing across a family whose members span OSes.

---

## 4. App store policies — and "can payment be taken out of the store?"

**Short answer: yes, and in the US (mid-2026) the door is wider than it has
ever been.** The 2025–26 antitrust rulings reshaped this; the numbers below are
current as of 2026-06-26 and partly *in active litigation*, so treat them as a
moving snapshot, not a constant.

### 4a. The store-fee landscape (US, 2026)

- **Apple — in-app purchase (IAP):** standard 30% (15% on subscriptions after
  12 paid months); **15% flat under the Small Business Program** for developers
  with <$1M proceeds in the prior year `[fact:developer.apple.com/app-store/small-business-program]`.
  Dayfold qualifies for 15%.
- **Apple — external-link purchases (US only):** following the April 2025
  injunction Apple was **barred from commission on purchases made via external
  links**; its attempted 27% fee was found to be contempt
  `[fact:macrumors.com 2026-04-29]`. In **April 2026 the Ninth Circuit reversed
  the stay**, remanding to the district court to determine what fee (if any)
  Apple may charge on link-outs; Apple is seeking Supreme Court review
  `[fact:techcrunch.com 2026-04-29; macrumors.com 2026-05-21]`. **Net today:
  US external-link purchases carry ~0% Apple commission, but this is unsettled
  and could change.** Plan for the architecture, not the current 0%.
- **Google Play (from June 30, 2026, US/UK/EEA):** restructured into a **10%
  service fee on the first $1M/yr regardless of billing method** + a separate
  **5% billing fee that applies only when using Google Play Billing**.
  Alternative billing or external web links are **not** subject to the 5%
  `[fact:support.google.com/googleplay/.../16954621; android-developers.googleblog.com 2026-06]`.
  So for a sub-$1M developer: ~**15% via Google Play Billing, ~10% via
  alt/external billing** in the US.
- **Web / direct (Stripe):** 2.9% + $0.30 `[fact:stripe.com/pricing]`. **But**
  web-direct makes *you* the merchant of record → you own sales-tax/VAT
  remittance, fraud, and chargebacks. A merchant-of-record (Paddle,
  Lemon Squeezy) offloads that for ~5–8% all-in `[estimate]`.

### 4b. The solo-operator nuance the raw % hides

The store's 15% is not pure rent: **Apple/Google handle global tax remittance,
fraud, chargebacks, dunning, and billing infra.** For a <2 hr/wk solo operator
that offload has real value. The honest comparison is:

- **Store IAP (15%):** zero tax/fraud/billing ops. Highest fee.
- **Web + merchant-of-record (~5–8%):** near-zero tax/ops, mid fee.
- **Web + raw Stripe (~3% + your tax/ops burden):** lowest fee, **highest
  ongoing ops** (you file taxes, handle chargebacks) — in tension with the
  <2 hr/wk constraint.

At the realistic low scale (~100–200 payers → ~$300–1,500/mo
`[estimate, pricing-structure.md]`), the *absolute dollar* difference between
15% and 5% is ~$30–150/mo — often **not worth the tax/ops burden** until volume
grows. This argues for a pragmatic sequence (§5), not fee-minimization at
launch.

### 4c. The policy lane Dayfold fits: multiplatform services

Dayfold is genuinely multiplatform with server-authored content, which maps
onto **App Review Guideline 3.1.3(b) (multiplatform services):** apps may let
users access subscriptions/content **acquired outside the app** (e.g. on the
web), provided the item is also offered as IAP and the app doesn't steer to
non-IAP methods inside the app `[fact:developer.apple.com/app-store/review/guidelines]`.
With the post-2025 US anti-steering changes, in-app link-out is additionally
permitted in the US. **This is the standard "sign up on the web, just log in on
the app" pattern** — fully viable for Dayfold and the cleanest way to keep the
family-tenant entitlement store-agnostic.

> ⚠ **Guardrail #3/legal:** the exact 3.1.3(b) compliance posture, App Review
> risk, and merchant-of-record/tax choice are **legal/operator decisions, never
> agent-decided.** This memo scopes options; it does not select a compliance
> posture.

---

## 5. Family plan: who pays, and how the money flows

**Billing unit = the family tenant. Payer = the family owner. Entitlement =
server-side, tenant-scoped.** That trio is the whole design. Two delivery
options for the family-plan billing, plus the recommended hybrid:

### Option A — Store IAP with Apple "Share with Family"
Enable the **"Share with Family" toggle** on the subscription SKU so an
organizer's purchase shares to their Apple Family group (up to 5 members; each
keeps a personalized account; **the family organizer pays for everyone**)
`[fact:support.apple.com/en-us/108107; developer.apple.com]`.
- **Pro:** one-tap purchase; Apple handles tax/fraud; familiar.
- **Con:** **Apple's family group ≠ Dayfold's family tenant.** The people in an
  Apple Family Sharing group may not be the same people in the Dayfold
  owner-approved membership, and it **doesn't span to Android/web members.**
  Google Play has an analogous-but-separate family library. Relying on
  store-native family sharing fractures a cross-platform tenant.

### Option B — Web/direct billing, account-based entitlement (recommended primary)
Owner subscribes on the web (Stripe or MoR); the server marks the **family
tenant** paid; **every adult member unlocks by logging in, on any OS/device.**
- **Pro:** matches the tenant model exactly; one payer unlocks the whole
  cross-platform family; lowest store fee; aligns with hosted-SaaS posture
  (ADR 0032); fits 3.1.3(b) multiplatform.
- **Con:** purchase friction (drive to web, no one-tap in-app); you own
  tax/ops unless using a MoR; App Review nuance to respect.

### Recommended: hybrid, server-entitled
- **Entitlement lives server-side, keyed to the family tenant** (extends the
  ADR 0011 membership + ADR 0029/0030 scope model — a tenant-level
  `subscription_status`). Store receipts *and* web payments both resolve to the
  same tenant entitlement.
- **Launch primary = web/direct** (Option B) for the cross-platform family
  fit + low fee; **add store IAP as a convenience rail** later if measured
  in-app conversion materially beats web checkout.
- **Who pays:** family owner only; members never see a paywall, they just get
  access — preserves the calm, member-personalized experience.

---

## 6. Recommended sequence (what to do, when)

| When | Action |
|---|---|
| **Now (pre-revenue)** | Free dogfood + concierge beta. **Do NOT bake store-IAP assumptions into the schema.** Reserve a tenant-level entitlement field as the single source of truth. No pricing constant set. |
| **A4/A5/A7 (business path)** | Use WTP conversations to test the flat per-family price + annual-first; finish the margin model with store-fee + tax/MoR sensitivity (§4b). |
| **B6 (pricing ADR)** | Operator sets the price constant + tier shape + billing rail in an ADR (operator-gated). |
| **G-LAUNCH (paid)** | Stand up the chosen rail (web/MoR primary; store IAP optional). Requires a legal entity (A6) + tax posture + the Apple dev account ($99/yr) / Play account (already in ADR 0034). |

---

## 7. Operator decisions needed (gated — routed via INB-24)

1. **Billing unit & payer model:** confirm flat **per-family**, owner-pays,
   server-side entitlement (recommended) vs per-seat.
2. **Primary billing rail at launch:** web/direct-MoR (recommended) vs store
   IAP vs hybrid — *and* accept the tax/ops tradeoff (§4b).
3. **Tier shape:** single family tier (recommended) vs freemium vs multi-tier.
4. **Price constant + cadence:** the actual number + annual-first
   (deferred to B6; do not set here).
5. **Legal/compliance posture** (never agent-decided): 3.1.3(b) multiplatform
   compliance + App Review risk + merchant-of-record/tax-remittance choice +
   entity timing (A6).

These are **B-phase / G-LAUNCH** decisions — none blocks current build. The one
*now* action is architectural: keep the entitlement store-agnostic.

---

## Sources

- Apple App Store Small Business Program — developer.apple.com/app-store/small-business-program `[fact]`
- Apple/Epic US external-link commission status (Apr–May 2026) — macrumors.com/2026/04/29, techcrunch.com/2026/04/29, macrumors.com/2026/05/21 `[fact]`
- Google Play billing-choice restructure (eff. 2026-06-30; 10% service + 5% billing) — support.google.com/googleplay/android-developer/answer/16954621, android-developers.googleblog.com/2026/06 `[fact]`
- Apple Family Sharing for subscriptions (organizer pays; up to 5; "Share with Family") — support.apple.com/en-us/108107, developer.apple.com/app-store/subscriptions `[fact]`
- App Review Guideline 3.1.3(b) multiplatform services — developer.apple.com/app-store/review/guidelines `[fact]`
- Stripe pricing 2.9% + $0.30 — stripe.com/pricing `[fact]`
- Internal: `research/validation-round1-agent-outputs/pricing-structure.md`, ADR 0004, ADR 0032, `context/goals-and-constraints.md`, `context/business-constitution.md`
</content>
</invoke>
