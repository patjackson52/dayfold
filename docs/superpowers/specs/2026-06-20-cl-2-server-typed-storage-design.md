# CL-2 — Server: typed content storage, validation, sync (design)

**Epic:** `planning/content-detail-epic.md` · **ADR:** 0022 (D2 = *extend
`briefing_cards` in place*; unify→M1) · **Depends:** CL-1 (typed schema + zod
`BriefingCardSchema` with `type`/`payload`/`privacy`/`hubRef`) — DONE on `cl-next`.

## Goal

Persist + serve the 6 typed content payloads end-to-end on the existing M0 card
surface, keeping the keyset-sync + tombstone invariants intact. CL-1 made the
wire schema typed; the **server still drops** `type`/`payload`/`privacy`/`hubRef`
(repo.ts never reads them). CL-2 closes that, adds the **type↔payload-key
cross-validation** the CL-1 commit deferred to "CL-2 server superRefine", and
proves it vs live PG.

## Scope (what changes)

1. **Migration `0005_typed_content.sql`** — extend `briefing_cards` in place
   (D2). Add nullable cols: `type text`, `payload jsonb`, `privacy jsonb`,
   `hub_ref text`. DB-level `CHECK` that `type` ∈ the 6 enum values (defense in
   depth — **`type` only**; the payload-key↔type tie is NOT a DB constraint, it
   lives in `crossValidateCard`. CHECK not a PG enum — enums are painful to
   extend, M0 disposable data).
   No new index (payload is never a query predicate; keyset index unchanged).
   `[E2E-ciphertext @ M1]` comment on `payload` so ADR 0015/0017 lands cleanly.
2. **`repo.ts` upsert** — carry `type`, `payload` (jsonb), `privacy` (jsonb),
   `hub_ref` (= wire `hubRef`) through INSERT + ON CONFLICT update. `SELECT *`
   means list/sync/return serialize them automatically (pg auto-parses jsonb).
3. **Cross-validation** — new `src/content-validation.ts :: crossValidateCard`,
   called in the PUT handler on **`parsed.data`** (post-zod), after
   `BriefingCardSchema.safeParse`. **Load-bearing:** zod validates `type` and
   `payload` *independently* — `{type:"file", payload:{invite:…}}` PASSES
   `safeParse` (a valid invite payload), so the key↔type tie exists ONLY in
   crossValidate, never in the schema. Rule (M0,
   strict for renderer-safety): a card is typed **iff** it carries a payload —
   `(type==null) === (payload==null)`, and when present the payload's single key
   **must equal** `type`. Legacy kind-only cards (neither) stay valid
   (back-compat). Violations → **422** with `issues`, same envelope as zod.
4. **No new sync/serialization code** — wire stays DB-shaped (snake; matches the
   existing `target_hub_id` precedent). CL-4 client maps. `payload`/`privacy`
   ride as parsed JSON objects; `hub_ref` snake on the wire.

## Out of scope (deferred, per epic)

CL-3 CLI typed authoring · CL-8 related-edges / cross-family-ref IDOR on
`linkedEventId` (no resolution at M0; it's an opaque string) · CL-9 real maps ·
the unified `content_item` migration (M1 E2EE debt) · camelCasing the wire.

## Security / privacy (review-binding, from the epic)

- **Mass-assignment:** `payload`/`type`/`privacy`/`hubRef` are *client content*
  (not server-managed) → they pass `stripServerManaged` untouched, then re-parse
  through the **strict** `BriefingCardSchema` (`.strict()` rejects unknown keys;
  each payload variant is `.strict()` single-key). No `z.any()` for payload.
- **Tenancy:** new fields ride the existing `authorizeTenant` path; add an IDOR
  invariant test (family-A typed card never appears in family-B list/sync).
- **OG / SSRF:** schema already forbids server URL fetch (CL-1 `ogDesc`
  author-stamped); CL-2 validates URL *syntax* only via the existing zod. No
  fetch added.
- **Privacy chip honesty:** `privacy.storage` is stored verbatim; no claim is
  *asserted* by the server (CL-6 audits copy). The enum is the only allowed set.
- **Guardrail 3:** `email.bodyExcerpt` is authored over the operator's OWN mail
  (CLI/Claude) — CL-2 adds **no** server-side mail read. Unchanged.

## Test plan (vs live PG, new `test/typed-content.test.ts`)

`beforeAll` applies `0001` + `0005` (no auth migrations; household-token path).

1. Each of the 6 types upserts → 200; `payload` + `type` + `privacy` + `hub_ref`
   round-trip via GET cards **and** GET sync (parsed objects, not strings).
2. Type↔payload mismatch (`type:"file"`, `payload:{invite:…}`) → **422**.
3. `payload` without `type` → 422; `type` without `payload` → 422.
4. Legacy kind-only card (neither type nor payload) → **200** (back-compat).
5. Typed card soft-delete → tombstone surfaces in sync (trigger bumps
   `updated_at`).
6. Cross-tenant: family-A typed card absent from family-B list + sync.
7. Cursor stability: re-sync from `next_cursor` returns no dupes.

Also: **update `api.test.ts` `beforeAll` to apply `0005`** (its `put()` path now
hits the new columns) — keep the existing 10 green.

## DoD

All 6 types upsert+sync+soft-delete green vs live PG; invalid/mismatched
payloads rejected 422; keyset/tombstone/cursor invariants intact; api.test +
content-schema suites still green; `npm run codegen` unaffected (no schema edit).

## Risks

- pg jsonb auto-parse: confirmed — `provenance`/`triggers`/`actions` already
  jsonb and return as objects today; `payload`/`privacy` inherit that.
- Strict typed-iff-payload rule could reject a future "type tag, no payload"
  card — acceptable at M0 (renderer needs the payload); revisit if a use case
  appears.
