# 05 — Invite System

> Status: **draft → in review**. Implements ADR 0011 (all invites
> owner-approved, no auto-join) + the invite/member endpoints in `03-api.md`
> and the `invites`/`memberships` tables in `02-data-model.md`. **[M1]** (the
> prototype is single-household; invites arrive with multi-member).

## Invite lifecycle (state machine)

```
            mint
              │
              ▼
        ┌──────────┐  redeem (atomic claim)   ┌───────────────────┐
        │  active  │ ───────────────────────▶ │ membership=pending │
        └──────────┘                          └───────────────────┘
          │   │   │                                    │  owner approve → active
   expire │   │   │ revoke                              │  owner decline → removed
          ▼   │   ▼                                     ▼
     expired  │ revoked                          (notification to owner)
              ▼
          exhausted (used_count == max_uses)
```

Invite `status`: `active | revoked | exhausted | expired`. Membership
`status`: `pending → active | removed`. **No auto-join** — every redeem lands
`pending`; only owner approval activates.

## Mint (`POST /families/{fid}/invites`, owner-only)

- Generate a **≥128-bit** random token; return the **raw token once** (+ QR
  URL); store only `token_hash` (SHA-256).
- Fields: `role` (default `adult`; `teen` deferred ADR 0005), `mode`
  (`qr` TTL ~15 min / `link` TTL ~72 h), `max_uses` (qr=1, link configurable).
- QR encodes a Universal/App-Link `https://<app>/invite/{token}` (link mode
  shares the URL). **Response is NOT gzipped** (BREACH — raw token is a
  one-time secret). Owner-only (middleware role gate).

## Redeem (`POST /invites:redeem`, authenticated invitee)

1. Invitee authenticates as themselves first (invite ≠ identity).
2. Token in **body/header** (not URL path — keeps it out of logs).
3. **Atomic claim** (single transaction):
   ```sql
   UPDATE invites SET used_count = used_count + 1,
          status = CASE WHEN used_count+1 >= max_uses THEN 'exhausted' ELSE status END
    WHERE token_hash = $1 AND status='active' AND used_count < max_uses AND expires_at > now()
   RETURNING family_id, role;
   -- 0 rows ⇒ reject
   INSERT INTO memberships(user_id, family_id, role, status) VALUES ($me, $fam, $role, 'pending')
     ON CONFLICT (user_id, family_id) DO …  -- see edge states
   ```
4. **Uniform `404`** for unknown/revoked/expired/exhausted (no enumeration);
   `200 {family_id, role, status:"pending"}` on success.
5. **Server-set status** = `pending` always (mass-assignment guard — a body
   claiming `active`/`owner` is ignored).

## Approval (`POST …/members/{uid}:approve | :decline`, owner-only)

- Owner sees a **pending-approval queue** (the canonical surface; the invite
  screen + notification link to it) + a push/sheet notification.
- Approve → membership `active`, `joined_at=now()`; **role re-checked at
  approval** (esp. future teen/14+ — owner attests age here per ADR 0005, not
  at mint). Decline → `removed`.
- Idempotent: re-approve of an already-active member → `200` no-op.
- Owner can **revoke an outstanding invite** (`DELETE …/invites/{id}` → status
  `revoked`) and **remove a member** (last-owner guarded).

## Edge states (invitee-facing)

| Case | Behavior |
|---|---|
| Token expired/revoked/exhausted | uniform `404`; UI: "ask for a new invite" |
| Already an **active** member | `409` distinct `problem.type`; route into the family |
| Already **pending** (re-redeem) | `200`/idempotent; show "waiting for approval" |
| Not installed (cold) | re-tap link after install (72 h link TTL); **deferred deep-link cut** |
| Pending, awaiting approval | invitee "waiting for approval" screen until owner acts |

## Security controls (from ADR 0011 + the API review)

- Owner-only mint/approve/decline/revoke (role gate, re-resolved per request).
- ≥128-bit token, hashed at rest, single-use (qr) / capped (link), short TTL.
- Atomic claim prevents double-redeem races; uniform 404 prevents enumeration.
- Rate-limit `:redeem` (per-IP + global); owner notified on every join with a
  **one-tap remove**.
- The invite grants only a **pending** membership → a leaked/forwarded token
  yields no data until the owner approves (the core reason auto-join was
  dropped).

## Forward hook — E2EE (TASK-E2E)

If E2E encryption is adopted, **invite approval is the natural place to hand
off the family content key** to the new member (wrap the family key to the
joiner's public key at approval time). The owner-approved-join model fits a
public-key-wrap distribution well. Re-reconcile this spec when
`research/e2e-encryption-investigation.md` lands.

## Open questions
- Can a non-owner adult invite? Default **owner-only** at MVP (`OQ-invite-roles`).
- Invite-link sharing surface (SMS/AirDrop/copy) — UX in A8b mockups.
- Notification transport for the approval prompt (local vs push) — ties to
  the notification design (ADR 0014 brief).
