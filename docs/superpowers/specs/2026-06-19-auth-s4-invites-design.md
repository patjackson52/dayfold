# Auth S4 — Owner-Approved Invites + multi-family credential fix (design)

**Date:** 2026-06-19 · **Branch:** `auth-s4` · **ADR:** 0021 (S4 slice) + ADR 0011 §5/§11.
Implements `05-invite.md` + `auth-and-family-design §Flow 3a`, on the AUTH-S1
backbone (credentials, memberships, `authorizeTenant`, dev-token) + the S3
owner-gate pattern.

## Goal & scope

Owner-approved family invites (no auto-join) **and** the family-agnostic
credential fix that lets a user belong to >1 family (clears the documented S1
two-family debt). Backend-only.

**In scope:** `invites` table + `memberships.invite_id`; the credential-model
change (app creds family-agnostic); endpoints mint / redeem / approve / decline /
revoke-invite / remove-member / list; security controls; the multi-family test
that un-skips the S1 limit.

**Out of scope (deferred):** QR rendering, the approval-queue / invitee-identity-
display / "waiting for approval" **screens** (S6, design-gated, needs A8b);
Universal/App-Link domain (`OQ-deeplink-domain`); teen role (ADR 0005); push
notifications (the pending queue is the source of truth); the expiry **sweep**
mechanism (same deferred-sweep follow as S3 m-2 — the redeem-time guard still
blocks stale invites); Firebase identity (S2); E2EE key-handoff (M1).

## Decisions (brainstorm)

- **Credential model [clears S1 debt]:** `kind='app'` (interactive) credentials
  are **family-agnostic** — `family_scope = NULL`. Access is governed purely by
  per-request `membership(user=sub, family=path-fid)` in `authorizeTenant` (its
  `cred.family_scope && cred.family_scope !== fid` check short-circuits when null,
  so a null-scope app cred is allowed on any family the user is an active member
  of). **Drop the `POST /families` binding UPDATE**; mint app creds null-scope
  (dev-token + the `/families` flow + future Firebase session). **CLI (`kind='cli'`)
  creds stay family-scoped** (one device = one family) — satisfies the existing
  `CHECK (kind <> 'cli' OR family_scope IS NOT NULL)`. No data migration (S1/S3 are
  not deployed → no live app creds bound).
- **Invitee authenticates via access-JWT** (dev-token now, Firebase S2) — invite ≠
  identity. **Owner-only** mint/approve/decline/revoke/remove. **Role allowlist
  `{adult}`** (teen deferred; **never `owner`** via invite — ownership is
  transfer-only, ADR 0011 §11).
- **Backend-only;** all UI → S6.

## Data model (`apps/api/migrations/0005_invites.sql`)

```sql
CREATE TABLE invites (
  id          text PRIMARY KEY,
  family_id   text NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  role        text NOT NULL DEFAULT 'adult'
                CONSTRAINT invites_role_allowlist CHECK (role IN ('adult')),  -- [M4] named for the teen-widening ALTER; never owner
  token_hash  text NOT NULL UNIQUE,            -- SHA-256 of a ≥128-bit CSPRNG token
  mode        text NOT NULL CHECK (mode IN ('qr','link')),
  max_uses    int  NOT NULL DEFAULT 1 CHECK (max_uses >= 1 AND max_uses <= 10),  -- [I2] hard cap
  used_count  int  NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= max_uses),  -- [M2] defense-in-depth
  status      text NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','exhausted','expired')),
  created_by  text NOT NULL REFERENCES users(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL
);
CREATE INDEX ON invites (family_id, status);

-- approval provenance + a queue timestamp ([M4]: memberships has joined_at/updated_at
-- but no request time, and joined_at is set at approve — the pending queue needs its own).
ALTER TABLE memberships ADD COLUMN invite_id text REFERENCES invites(id);  -- ON DELETE NO ACTION: invites are revoked, never deleted (documented)
ALTER TABLE memberships ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();  -- request/redeem time for the queue
```
`token_hash UNIQUE` is the redeem lookup key. Enums as CHECK (consistent with
0002's auth tables; 0001's `CREATE TYPE` enums predate that convention).
memberships PK `(user_id, family_id)` (0002) + index `(family_id, status)` (0002)
already exist — the redeem INSERT + the queue read rely on them.

## Credential change (S1 core, `apps/api/src/app.ts` + `identity.ts`)
- `POST /families`: **remove** `UPDATE credentials SET family_scope=$1 WHERE
  user_id=$2 AND family_scope IS NULL`. App creds stay null-scope.
- `identity.ts mintCredentialFor(userId)` and the dev-token credential INSERT:
  mint `kind='app'` with **`family_scope = NULL`** (drop the family arg / column).
- `authorizeTenant` unchanged — already allows null-scope app creds
  (`middleware.ts` `cred.family_scope && cred.family_scope !== fid` short-circuits
  on null; **membership is the gate**). Verify with the multi-family test.
- **[C2] `GET /auth/whoami` MUST change too** — it currently returns
  `cred.family_scope` (app.ts), which becomes `null` for every app token after this
  change (and is wrong by construction once a user is in >1 family). The existing
  `auth-e2e.test.ts` whoami assertion (`family_id === fam.familyId`) **will fail**
  otherwise. Redefine whoami to return the caller's memberships derived from `sub`:
  `{ families: [{ family_id, name, role, status }] }` (active + pending), via a
  `memberships JOIN families` on `user_id=sub`. Update the whoami test accordingly.

## Transaction discipline (applies to redeem / approve / decline / revoke / remove) [C1]
On Vercel the pg pool is **max:1** (`db.ts`) → a transaction holds the only
connection. **Every multi-statement endpoint runs on ONE `pool.connect()` client
with explicit `BEGIN`/`COMMIT`/`ROLLBACK`, all statements via `client.query`** —
**never the module-level `q()` inside an open txn** (it grabs a second pooled
connection → self-deadlock + breaks atomicity). `audit()` (uses `q()`) is called
**after COMMIT** (mirrors `device.ts`). State transitions use the proven
**guarded `UPDATE … WHERE status=$expected RETURNING`** idiom (`rowCount===1` →
success; `0` → re-read in-txn to disambiguate) rather than SELECT-then-UPDATE.

## Endpoints (owner ops via a generalized `ownerGate` = `authorizeTenant` + `role==='owner'` + `cred.kind==='app'`)

### `POST /families/:fid/invites` — owner mint
ownerGate. Per-owner mint rate-limit (`owner:mint:<sub>`, reuse S3 helpers) +
**pending-cap** — reject if the family already has ≥N (e.g. 20) `pending`
memberships **or** ≥M (e.g. 10) **live** invites
(`status='active' AND expires_at>now()` — **[I3] expires_at-filtered** so dead rows
don't inflate the cap). Generate token = `randomBytes(32).base64url` (≥128-bit;
reject in code if a supplied value is short — never accept a client token). Body
`{mode, role?, max_uses?}`: `role` allowlist `{adult}` (default adult; reject
`owner`/`teen` → 400); **`max_uses` validated server-side ≤10 [I2]** (else 400);
`mode='qr'`→`max_uses=1`, TTL 15 min; `mode='link'`→`max_uses` as given (default 1),
TTL 72 h. Insert `active`, store `token_hash` only. Return
`201 {invite_id, token, url, role, mode, expires_at}` — **raw token ONCE**,
**response not gzipped** (BREACH; raw token is a one-time secret). `url =
"<verification_uri>/invite/" + token` (the deep-link domain is `OQ-deeplink-domain`;
S4 uses a documented constant). Audit `invite.mint`.

### `POST /invites:redeem` — authenticated invitee
Authenticate the invitee's access-JWT (their own identity). `isLocked(account:redeem:<sub>)`
→ 429 before lookup. Body `{token}` (never the URL path on the server). **All of
the below runs on ONE `pool.connect()` client in a single BEGIN/COMMIT** (C1 — the
`FOR UPDATE` lock MUST hold across the claim + bump or a concurrent redeemer
double-claims a `max_uses=1` invite):
1. Lock the invite: `SELECT id, family_id, role, used_count, max_uses FROM invites
   WHERE token_hash=$1 AND status='active' AND used_count<max_uses AND expires_at>now() FOR UPDATE`.
   0 rows → ROLLBACK, `recordFailure(account:redeem:<sub>)`, **uniform 404**.
2. **[I2] per-family pending-cap inside the txn:** `SELECT count(*) FROM memberships
   WHERE family_id=$fid AND status='pending'` ≥ cap (named const, e.g. 20) → ROLLBACK
   + 429 (queue full). (Counting distinct attacker accounts a leaked link can park.)
3. INSERT-first claim:
   `INSERT INTO memberships(user_id, family_id, role, status, invite_id)
    VALUES ($sub, $fid, $invrole, 'pending', $invid) ON CONFLICT (user_id, family_id) DO NOTHING RETURNING 1`.
   - **net-new (rowCount 1):** **[I1] guarded bump** —
     `UPDATE invites SET used_count=used_count+1,
        status = CASE WHEN used_count+1 >= max_uses THEN 'exhausted' ELSE 'active' END
      WHERE id=$invid AND status='active'` (the `AND status='active'` guard prevents
     resurrecting a concurrently-revoked invite; CASE only `active→exhausted`).
     COMMIT → **[M1] `resetFailures(account:redeem:<sub>)`** → `200 {family_id,
     family_name, role, status:'pending'}` ([M4] family name = 05-invite's post-auth
     disclosure point; auth-gated, no enumeration). Audit `invite.redeem` (after COMMIT).
   - **conflict (rowCount 0, no use consumed):** read the existing membership status
     **in the same txn** → COMMIT, then branch: `pending`→`200` idempotent +
     `resetFailures` · `active`→`409` (route in) · `removed`→`409` ("ask owner", no
     resurrect). (The GET queue, not this 200/409, is the source of truth.)
- `status='pending'` and `role` are **server-set** (mass-assignment guard; role from
  the invite, never the body).

### `POST /families/:fid/members/:uid:approve` — owner
ownerGate. **[I1] guarded transition** (one statement, no SELECT-then-UPDATE TOCTOU):
`UPDATE memberships SET status='active', joined_at=now() WHERE user_id=$uid AND
family_id=$fid AND status='pending' RETURNING role`. `rowCount===1` → approved
(role re-checked against the allowlist before/at write). `0` → re-read the row:
`active`→`200` idempotent no-op · `removed`→`409` · none→`404`. (ownerGate already
re-resolved the approver as active owner per request.) Audit `invite.approve`
(approver, invitee uid, invite_id, family).

### `POST /families/:fid/members/:uid:decline` — owner
ownerGate. **[I1] guarded:** `UPDATE memberships SET status='removed' WHERE
user_id=$uid AND family_id=$fid AND status='pending' RETURNING 1` → `204`; `0` →
re-read → already-active→`409` (use remove instead) · already-removed→`204` no-op ·
none→`404`. Audit `invite.decline`.

### `DELETE /families/:fid/invites/:id` — owner
ownerGate. **[I1] guarded + sticky:** `UPDATE invites SET status='revoked' WHERE
id=$id AND family_id=$fid AND status='active' RETURNING 1` → `204`; `0` →
already-revoked/exhausted/expired→`204` no-op (sticky), wrong family→`404`. Audit
`invite.revoke`.

### `DELETE /families/:fid/members/:uid` — owner, **≥1-owner guarded** [C3]
ownerGate. **One `pool.connect()` txn.** `SELECT user_id, role FROM memberships
WHERE family_id=$fid AND role='owner' AND status='active' FOR UPDATE` — this **locks
the active-owner rows** (NOT `count(*) FOR UPDATE`, which Postgres rejects with
aggregates, and which would lose the concurrent-double-remove race). If the target
`$uid` is an active owner AND the locked set has **< 2** rows → ROLLBACK + `409`
(last-owner). Else `UPDATE memberships SET status='removed' WHERE user_id=$uid AND
family_id=$fid AND status IN ('active','pending') RETURNING 1` → COMMIT → `204`;
`0`→`404`. The row-lock serializes two concurrent removes (the second sees count=1
→ rejected). **Not retroactive** (revokes future access only; ADR 0011 — the
member's already-synced data isn't recalled). Audit `member.remove` (after COMMIT).

### `GET /families/:fid/invites` + pending members — owner
ownerGate. Returns: outstanding **live** invites (`status='active' AND
expires_at>now()` — **[I3]**; no token/hash) + the **pending-approval queue** (the
source of truth, no push dependency). **[I4] the pending payload MUST carry the
invitee's verified identity** (05-invite's identity-binding is a hard anti-phishing
control the approver acts on): JOIN `memberships → users → user_identities`:
`{ uid, display_name, provider, provider_uid, email_verified, role, invite_id,
requested_at: memberships.created_at, invite: {created_by, created_at} }`. (At dev
`display_name` is null — StubVerifier sets none — so identity reduces to
`provider_uid` + `email_verified`; Firebase S2 fills name/email. The backend
exposing this data is the S4 obligation; the S6 screen renders it.)

## Security (ADR 0011 + 05-invite — all load-bearing)
- **Owner-only** mint/approve/decline/revoke/remove (role + kind gate, re-resolved
  per request; legacy household token has `role=null` → 403, S3 pattern).
- **Entropy is the control:** ≥128-bit (32-byte) CSPRNG token; SHA-256 at rest;
  reject short tokens in code.
- **Atomic INSERT-first claim** → no double-redeem race, a no-op conflict never
  burns a `max_uses` slot.
- **Uniform 404** on bad/expired/exhausted/revoked → no invite enumeration.
- **Leaked/forwarded token yields only a `pending` membership** → zero family data
  until the owner approves (the reason auto-join was removed).
- **Never `owner` via invite**; ≥1-owner invariant on remove/decline.
- **Redeem rate-limit + lockout** per-account (authed) — stops a signed-in user
  brute-forcing tokens behind the uniform 404.
- **Pending caps** (per family + per invite) + mint rate-limit per owner → a
  forwarded link-mode invite can't flood the approval queue into a rubber-stamp.
- Mass-assignment guard: `status`/`role` server-set, `family_id` from path/invite.

## Forward-compat
- **Firebase (S2):** the invitee/approver authenticate via access-JWT exactly as
  today; at S2 those JWTs come from Firebase verify instead of dev-token — no S4
  change. Approval needs the invitee's display identity (name/email) — available
  from `user_identities` (dev provider now, Firebase later); the **display** is S6.
- **E2EE (M1):** approval is the natural family-key handoff point (wrap the FCK to
  the joiner's pubkey at approve) — note only, no S4 code.
- **S6 UI:** every endpoint returns the data the screens need (queue, statuses,
  invitee identity); QR/Universal-Link rendering is S6.

## Testing (vitest vs live PG)
- **Cred fix / multi-family:** a user creates family A AND joins family B (invite
  → approve); ONE app token works on both; cross-family still 404 — **un-skip the
  S1 two-family test.**
- **Mint:** owner-only (non-owner 403, kind='cli' 403); token ≥128-bit + hashed
  (raw returned once, only hash stored); role allowlist rejects `owner`/`teen`;
  qr→max_uses=1/15m, link→max_uses/72h; per-owner rate-limit; pending-cap reject.
- **Redeem:** net-new→pending + used_count bumped (exhaust on last); **double
  redeem of a max_uses=1 invite by two users → one pending + one 404, never 2
  uses**; same-user re-redeem→200 idempotent (no extra use); active→409; removed→409;
  uniform 404 (not-found/expired/revoked/exhausted); role from invite not body;
  per-account lockout after N.
- **Approve:** owner-only; pending→active+joined_at; re-approve active→200; removed
  →409; role re-checked; concurrent approve (FOR UPDATE) single-activates.
- **Decline/revoke/remove:** decline→removed; revoke→then redeem 404; remove member;
  **≥1-owner: removing/declining the last owner → rejected**; **[C3] concurrent
  double-remove of two owners → at most one succeeds, ≥1 owner remains.**
- **[C2] whoami:** returns the caller's memberships (family_id + role + status) from
  `sub`, not `cred.family_scope`; update the existing `auth-e2e.test.ts` whoami
  assertion; a user in 2 families sees both.
- **[I2] redeem pending-cap:** with the family at the cap, a fresh redeem → 429
  (queue full). **[M1] lockout reset:** a failed-then-successful redeem clears the
  account's redeem counter.
- **[M3] device-grant interaction:** remove a member who holds a `kind='cli'` device
  token → that token now 403s (membership re-resolution; behavior exists via
  `middleware.ts`, add the regression).
- **[I5] schema harness:** extend the schema test `beforeAll` to load the FULL
  ordered migration chain **0001→0005** (it currently stops at 0002). Add 0005
  constraint tests: role allowlist rejects `owner`/`teen`; `token_hash` UNIQUE;
  `max_uses` 1–10; `used_count ≤ max_uses`; `memberships.invite_id` FK.
- Whole API suite + S1/S3 regression green.

## Implementation notes (folded from review; not blockers)
- **[BREACH] mint response:** set `Cache-Control: no-store, no-transform`, never
  gzip the raw token; if a global compression middleware is added later, exempt this
  route. Test asserts no `content-encoding`.
- **[I6] perf (impl-time):** mint's two cap counts → one round-trip via scalar
  subqueries; mint itself is a single INSERT → plain `q()`, no `pool.connect()`.

## Definition of Done
`0005` migrated (`invites` + `memberships.invite_id` + `memberships.created_at`);
app creds family-agnostic (S1 binding removed) **and `/auth/whoami` redefined to
memberships** with the existing whoami test updated + the two-family test un-skipped
+ green; mint/redeem/approve/decline/revoke/remove/list endpoints implemented +
fully tested (state machine via guarded `UPDATE…RETURNING`, single-txn atomic claim,
uniform 404, owner+kind gate, corrected ≥1-owner row-lock, rate-limit/lockout,
pending caps, identity-JOIN queue payload); **schema harness loads 0001→0005**;
whole suite + household regression green; Vercel bundle regenerated. UI, QR,
deep-link domain, teen, the expiry **sweep**, Firebase, E2EE handoff explicitly
deferred.
