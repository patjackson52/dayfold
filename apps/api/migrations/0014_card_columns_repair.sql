-- 0014_card_columns_repair.sql — repair a partial hand-apply.
-- Prod's briefing_cards was created by hand without the columns later migrations
-- (0006/0007/0009/0013) add, so upsertCard's INSERT 500'd on every card since M0
-- (schema-drift only verifies table presence, not columns, so it read "in sync").
-- Idempotently ensure every column the INSERT references exists. IF NOT EXISTS makes
-- this a no-op wherever the column is already present — a deliberate, one-purpose
-- exception to the "no IF NOT EXISTS" rule (ADR 0033), scoped to this repair.
-- Forward-only, additive, backward-compatible. Safe to run on any briefing_cards.

ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS body_md          text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS target_hub_id    text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS target_section_id text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS target_block_id  text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS provenance       jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS triggers         jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS actions          jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS not_before       timestamptz;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS expires_at       timestamptz;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS type             text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS payload          jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS privacy          jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS hub_ref          text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS related          jsonb;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS related_kicker   text;
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS visibility       text NOT NULL DEFAULT 'family';
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS audience         text[];
ALTER TABLE briefing_cards ADD COLUMN IF NOT EXISTS media            jsonb;
