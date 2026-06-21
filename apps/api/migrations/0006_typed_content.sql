-- CL-2 (ADR 0022 D2) — typed content on the M0 card surface. Extend
-- briefing_cards IN PLACE (the unified content_item table is deferred to M1,
-- where it migrates with E2EE anyway). All columns NULLABLE so kind-only M0
-- cards stay valid (back-compat). Numbering: 0001 m0, 0002-0004 auth, this=0005.

BEGIN;

ALTER TABLE briefing_cards
  ADD COLUMN type    text,   -- file|link|invite|contact|geo|email (ADR 0022 D1); NULL = legacy kind-only card
  ADD COLUMN payload jsonb,  -- typed payload, variant by `type`. [E2E-ciphertext @ M1, ADR 0015/0017]
  ADD COLUMN privacy jsonb,  -- honesty chip {storage} (ADR 0014/0015) — verbatim, server asserts nothing
  ADD COLUMN hub_ref text;   -- parent Hub id (adaptive supporting pane, ADR 0022; CL-10). wire = hubRef

-- DB-level defense-in-depth (zod is the primary gate). CHECK not a PG enum:
-- enums are painful to extend and M0 data is disposable; the 6-value list is
-- the discriminator set in content.schema.json.
ALTER TABLE briefing_cards
  ADD CONSTRAINT briefing_cards_type_chk
  CHECK (type IS NULL OR type IN ('file','link','invite','contact','geo','email'));

-- No new index: payload/type are never query predicates at M0; the keyset sync
-- index (family_id, updated_at, id) and the touch_updated_at trigger are
-- unchanged, so tombstone/cursor invariants are preserved.

COMMIT;
