// DB access (M0). Local/dev uses node-postgres Pool; on Vercel this swaps to
// the Neon serverless driver (HTTP/WS, no held TCP) per ADR 0018 — same query()
// surface, so callers don't change.
import pg from "pg";
const { Pool, types } = pg;

// [F3] Return timestamptz/timestamp as the EXACT Postgres string (no JS Date
// round-trip) so the sync keyset cursor preserves microsecond precision —
// truncating to JS ms can silently skip a row sharing a millisecond.
types.setTypeParser(1184, (s: string) => s); // timestamptz
types.setTypeParser(1114, (s: string) => s); // timestamp

export const pool = new Pool({ connectionString: process.env.DATABASE_URL });

export function q(text: string, params?: unknown[]) {
  return pool.query(text, params);
}
