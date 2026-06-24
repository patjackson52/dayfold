import { randomBytes, createHash } from "node:crypto";
import { q, pool } from "../db.ts";

export const hashInvite = (t: string) => createHash("sha256").update(t, "utf8").digest("hex");
export const genInviteToken = () => randomBytes(32).toString("base64url");
const id = () => "inv_" + randomBytes(9).toString("hex");

export async function createInvite(familyId: string, createdBy: string, mode: "qr" | "link", role: string, maxUses: number) {
  const token = genInviteToken();
  const inviteId = id();
  const ttl = mode === "qr" ? "15 minutes" : "72 hours";
  await q(
    `INSERT INTO invites(id, family_id, role, token_hash, mode, max_uses, created_by, expires_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7, now() + $8::interval)`,
    [inviteId, familyId, role, hashInvite(token), mode, maxUses, createdBy, ttl],
  );
  return { inviteId, token };
}

const PENDING_CAP = 20;

export async function redeem(token: string, sub: string) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const inv = await client.query(
      `SELECT id, family_id, role, used_count, max_uses FROM invites
       WHERE token_hash=$1 AND status='active' AND used_count<max_uses AND expires_at>now() FOR UPDATE`,
      [hashInvite(token)]);
    if (inv.rowCount !== 1) { await client.query("ROLLBACK"); return { notfound: true as const }; }
    const { id: invId, family_id, role } = inv.rows[0];
    // Serialize redeems PER FAMILY so the pending-cap check+insert is atomic. The
    // invite-row FOR UPDATE above only serializes redeems of the SAME invite — two
    // redeems via DIFFERENT invites of one family would otherwise both read n<CAP
    // (TOCTOU) and overshoot the cap. The advisory lock releases on commit/rollback.
    await client.query(`SELECT pg_advisory_xact_lock(hashtext($1))`, [family_id]);
    const pend = await client.query(
      `SELECT count(*)::int n FROM memberships WHERE family_id=$1 AND status='pending'`,
      [family_id]);
    if (pend.rows[0].n >= PENDING_CAP) { await client.query("ROLLBACK"); return { capfull: true as const }; }
    const ins = await client.query(
      `INSERT INTO memberships(user_id, family_id, role, status, invite_id)
       VALUES ($1,$2,$3,'pending',$4) ON CONFLICT (user_id, family_id) DO NOTHING RETURNING 1`,
      [sub, family_id, role, invId]);
    if (ins.rowCount === 1) {
      await client.query(
        `UPDATE invites SET used_count=used_count+1,
           status = CASE WHEN used_count+1 >= max_uses THEN 'exhausted' ELSE 'active' END
         WHERE id=$1 AND status='active'`,
        [invId]);
      await client.query("COMMIT");
      return { ok: true as const, family_id, role };
    }
    const cur = await client.query(
      `SELECT status FROM memberships WHERE user_id=$1 AND family_id=$2`,
      [sub, family_id]);
    await client.query("COMMIT");
    return { conflict: cur.rows[0].status as "pending" | "active" | "removed" };
  } catch (e) { await client.query("ROLLBACK"); throw e; } finally { client.release(); }
}
