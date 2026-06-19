# 05 — Invite System

> Status: **reviewed (2 agents) → fixes applied**. Implements ADR 0011 (all invites
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
2. Token (base64url of **32 CSPRNG bytes**) in **body/header** (not URL path).
   The `/invite/{token}` landing page reads the path param and **POSTs it in
   the body** (`Referrer-Policy: no-referrer`, no third-party assets before
   consuming, strip token from history) — the path form is transport-only.
3. **Resolve the invite** by `token_hash` (guard `status='active' AND
   used_count<max_uses AND expires_at>now()`); 0 rows ⇒ **uniform `404`**.
4. **INSERT-first, in ONE transaction (use is consumed ONLY on a net-new
   pending membership — a no-op conflict must NOT burn a `max_uses` slot):**
   ```sql
   INSERT INTO memberships(user_id, family_id, role, status, invite_id)
     VALUES ($me, $fam, $invrole, 'pending', $invid)
     ON CONFLICT (user_id, family_id) DO NOTHING
   RETURNING status;            -- row ⇒ net-new
   -- net-new → bump the invite atomically (token_hash-keyed guard, exhaust on last use);
   -- no row inserted → SELECT memberships.status and branch (no use consumed):
   --   pending  → 200 idempotent ("waiting for approval")
   --   active   → 409 (distinct problem.type) → route into the family
   --   removed  → 409 "previously removed — ask the owner" (NO auto-resurrect)
   ```
5. **Server-set `status='pending'`** always (mass-assignment guard); role
   comes from the invite, never the body. `200 {family_id, role,
   status:"pending"}` on net-new.

## Approval (`POST …/members/{uid}:approve | :decline`, owner-only)

**Identity-bound, not presence-bound** (the security boundary — a generic
"someone wants to join" one-tap re-opens the device-grant phishing class):
- The approval prompt **MUST show the invitee's verified identity** (name/
  email/phone from their authenticated account), **which invite + role**
  (via `memberships.invite_id`), and **mint provenance** ("you created this
  invite N min ago"). **Decline is the low-friction default;** approve
  requires the identity to match.
- Approve in a tx that **`SELECT … FOR UPDATE` the pending row** and asserts
  current `status='pending'` (reject otherwise) + approver is an `active
  owner` re-resolved now → `active`, `joined_at=now()`. **Role re-checked at
  approval** (teen/14+ owner-attested per ADR 0005 — attestation, not
  verification; not a hard minor-data boundary). Decline → `removed`.
- Idempotent: re-approve already-active → `200` no-op; approve a `removed`
  row → `409` (must re-invite).
- **Remove is NOT retroactive:** approval == full read (the new member's
  device can immediately `sync` the whole dossier incl. minor data). One-tap-
  remove revokes *future* access only. So approval is the boundary — weight it
  (identity-bound, above). *Optional:* a short post-approval sync cooldown so a
  mistaken remove lands before bulk read.
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
- **Mint role allowlist:** `{adult, teen-when-shipped}` — **never `owner`**
  via invite (ownership is transfer-only, last-owner-guarded, ADR 0011 §11).
- **Cap concurrent pending** memberships per family AND per invite; **rate-
  limit mint** per owner; coalesce/throttle approval notifications; **auto-
  expire stale pending** rows — stops a forwarded link-mode invite flooding
  the approval queue into a rubber-stamp (mirrors device-grant pending caps).
- **Per-authenticated-account redeem rate-limit + lockout** (redeem is
  authed) on top of per-IP/global — stops a signed-in user brute-forcing
  tokens behind the uniform-404.
- **Entropy is the load-bearing control** (not the hash): enforce CSPRNG
  ≥128-bit (32-byte) token in code; reject short codes. SHA-256-at-rest is
  fine over that space.
- **Build-time deps (M1, `OQ-deeplink-domain`):** Universal/App-Link QR needs
  `apple-app-site-association` + `assetlinks.json` hosted on the verified
  `<app>` domain — resolve before this ships (deferred from the prototype per
  ADR 0011 §1).
- **State notes:** expiry is **sweep-driven** (`UPDATE … WHERE status='active'
  AND expires_at<now() → 'expired'`), never written on the redeem hot path;
  `revoked` is sticky; `removed` never auto-resurrects.

## Forward hook — E2EE (TASK-E2E)

If E2E encryption is adopted, **invite approval is the natural place to hand
off the family content key** to the new member (wrap the family key to the
joiner's public key at approval time). The owner-approved-join model fits a
public-key-wrap distribution well. Re-reconcile this spec when
`research/e2e-encryption-investigation.md` lands.

## Open questions
- Can a non-owner adult invite? Default **owner-only** at MVP (`OQ-invite-roles`).
- Invite-link sharing surface (SMS/AirDrop/copy) — UX in A8b mockups.
- **Notification = non-blocking enhancement;** the pending-approval **queue is
  the source of truth** (degrades gracefully without push). Transport (local
  vs push) ties to the ADR 0014 brief.
- **Family-name-before-auth tension:** redeem is auth-first and returns
  `family_id` only on success, so the invitee can't see the family name
  pre-auth without an unauthenticated lookup (reintroduces enumeration).
  Resolution: **no family name before auth** — show it post-redeem on the
  "waiting for approval" screen.
