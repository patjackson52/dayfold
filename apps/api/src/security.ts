// Security-critical pure logic for the content API (M0). Unit-tested.
// Specs: 03-api §Validation (mass-assignment), 04-auth §M0 household token.
import { createHash, timingSafeEqual } from "node:crypto";

/**
 * Constant-time comparison of the presented household token against the
 * configured secret. Hash both to equal-length digests so timingSafeEqual
 * never throws on length mismatch and leaks no length via timing.
 */
export function constantTimeEqual(presented: string, secret: string): boolean {
  const a = createHash("sha256").update(presented, "utf8").digest();
  const b = createHash("sha256").update(secret, "utf8").digest();
  return timingSafeEqual(a, b);
}

/**
 * Mass-assignment allowlist for CONTENT writes (M0). These fields are
 * server-owned and must be IGNORED if present in a client body — `family_id`
 * comes only from the path, `version` is server-bumped (M0), timestamps are
 * DB-managed. (Auth-resource fields like role/scope/used_count are M1.)
 */
export const SERVER_MANAGED_CONTENT_FIELDS = [
  "family_id",
  "version",
  "created_at",
  "updated_at",
  "deleted_at",
  "last_used_at",
] as const;

export function stripServerManaged<T extends Record<string, unknown>>(body: T): T {
  const out: Record<string, unknown> = { ...body };
  for (const k of SERVER_MANAGED_CONTENT_FIELDS) delete out[k];
  return out as T;
}

/**
 * The server stamps provenance.credential_id from the authenticated credential
 * (audit). The client may set provenance.source/at; it can NEVER forge
 * credential_id.
 */
export function stampProvenance(
  body: Record<string, unknown>,
  credentialId: string,
): Record<string, unknown> {
  const prov = (body.provenance && typeof body.provenance === "object")
    ? { ...(body.provenance as Record<string, unknown>) }
    : {};
  prov.credential_id = credentialId; // server-owned
  return { ...body, provenance: prov };
}
