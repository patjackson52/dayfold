// ADR 0033: tracked migration runner. Reads migrations/ in order, skips versions
// already recorded in schema_migrations, applies each pending file in its own
// transaction (DDL is transactional in Postgres), and records it. Re-running is a
// no-op. Built after the 2026-06-25 prod outage where hand-applied migrations
// (0002/0003/0008) were never applied to prod and 500'd sign-in — there was no
// record of what was applied and the first fix hit the wrong Neon branch.
//
// Usage:
//   DATABASE_URL=postgres://... node scripts/migrate.mjs             apply pending
//   DATABASE_URL=postgres://... node scripts/migrate.mjs --dry-run   list pending, apply nothing
//   DATABASE_URL=postgres://... node scripts/migrate.mjs --backfill  mark all pending as applied
//                                                                    WITHOUT running (existing DBs
//                                                                    whose schema was hand-applied)
//   exit 0 = ok · 1 = apply error (rolled back) · 2 = usage/conn error
import { readdirSync, readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

/** Sorted migration filenames — the forward-only apply order. Pure. */
export function migrationFiles(dir) {
  return readdirSync(dir).filter((f) => f.endsWith(".sql")).sort();
}

/** The version key recorded in schema_migrations for a file (filename sans .sql). Pure. */
export function versionOf(file) {
  return file.replace(/\.sql$/, "");
}

/** Stable content checksum (audit / drift). Pure + deterministic. */
export function checksumOf(sql) {
  return createHash("sha256").update(sql).digest("hex");
}

/** Files not yet recorded as applied, in order. Pure. */
export function pendingMigrations(files, appliedVersions) {
  const applied = new Set(appliedVersions);
  return files.filter((f) => !applied.has(versionOf(f)));
}

// The runner bootstraps the tracking table before it can query it (matches 0012).
const BOOTSTRAP =
  "CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, " +
  "applied_at timestamptz NOT NULL DEFAULT now(), checksum text)";

// ── CLI runner (only when invoked directly, not when imported by a test) ──
async function main() {
  const here = dirname(fileURLToPath(import.meta.url));
  const dir = resolve(here, "../migrations");
  if (!process.env.DATABASE_URL) {
    console.error("migrate: set DATABASE_URL to the target DB");
    process.exit(2);
  }
  const dryRun = process.argv.includes("--dry-run");
  const backfill = process.argv.includes("--backfill");
  const files = migrationFiles(dir);

  const pg = (await import("pg")).default;
  const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });
  try {
    await pool.query(BOOTSTRAP); // ensure the tracking table exists (chicken-and-egg)
    const { rows } = await pool.query("SELECT version FROM schema_migrations");
    const pending = pendingMigrations(files, rows.map((r) => r.version));

    if (pending.length === 0) {
      console.log(`migrate: up to date — ${files.length} migration(s) recorded`);
      process.exit(0);
    }
    if (dryRun) {
      console.log(`migrate: ${pending.length} pending (dry-run, nothing applied):`);
      for (const f of pending) console.log(`  - ${versionOf(f)}`);
      process.exit(0);
    }
    if (backfill) {
      // existing DB already carries the schema (hand-applied) — record as applied
      // WITHOUT executing, so the runner won't try to re-create what's already there.
      for (const f of pending) {
        const sql = readFileSync(resolve(dir, f), "utf8");
        await pool.query(
          "INSERT INTO schema_migrations(version, checksum) VALUES ($1,$2) ON CONFLICT DO NOTHING",
          [versionOf(f), checksumOf(sql)],
        );
        console.log(`migrate: backfilled ${versionOf(f)} (not executed)`);
      }
      process.exit(0);
    }
    for (const f of pending) {
      const sql = readFileSync(resolve(dir, f), "utf8");
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        await client.query(sql);
        await client.query(
          "INSERT INTO schema_migrations(version, checksum) VALUES ($1,$2)",
          [versionOf(f), checksumOf(sql)],
        );
        await client.query("COMMIT");
        console.log(`migrate: applied ${versionOf(f)}`);
      } catch (e) {
        await client.query("ROLLBACK").catch(() => {});
        console.error(`migrate: FAILED on ${versionOf(f)} — rolled back: ${e.message}`);
        process.exit(1);
      } finally {
        client.release();
      }
    }
    console.log(`migrate: done — ${pending.length} applied`);
    process.exit(0);
  } finally {
    await pool.end();
  }
}

if (import.meta.url === `file://${process.argv[1]}`) await main();
