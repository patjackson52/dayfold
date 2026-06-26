-- ADR 0033: migration tracking. The runner (scripts/migrate.mjs) records one row per
-- applied migration so it can skip already-applied files and never silently miss one
-- (the 2026-06-25 prod outage: 0002/0003/0008 were never applied, 500'ing sign-in).
-- IF NOT EXISTS because the runner bootstraps this same table before it can query it
-- (chicken-and-egg) — this is the tracking table itself, not tracked content.
CREATE TABLE IF NOT EXISTS schema_migrations (
  version    text PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now(),
  checksum   text
);
