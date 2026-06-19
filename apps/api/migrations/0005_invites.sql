CREATE TABLE invites (
  id          text PRIMARY KEY,
  family_id   text NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  role        text NOT NULL DEFAULT 'adult' CONSTRAINT invites_role_allowlist CHECK (role IN ('adult')),
  token_hash  text NOT NULL UNIQUE,
  mode        text NOT NULL CHECK (mode IN ('qr','link')),
  max_uses    int  NOT NULL DEFAULT 1 CHECK (max_uses >= 1 AND max_uses <= 10),
  used_count  int  NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= max_uses),
  status      text NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','exhausted','expired')),
  created_by  text NOT NULL REFERENCES users(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL
);
CREATE INDEX ON invites (family_id, status);
ALTER TABLE memberships ADD COLUMN invite_id text REFERENCES invites(id);   -- revoked-not-deleted → NO ACTION
ALTER TABLE memberships ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
