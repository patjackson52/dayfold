// AUTH-S5 live round-trip — exercises the exact endpoints the client AuthEngine +
// SyncClient use, in-process against the real DB + real EdDSA token mint/verify.
// dev-token → whoami(empty) → create family → whoami(active) → push card → sync.
//   DATABASE_URL=postgres:///fad_test node scripts/s5-roundtrip.mjs
import { generateKeyPair, exportJWK } from "jose";

process.env.DATABASE_URL ||= "postgres:///fad_test";
process.env.AUTH_ISS = "https://fad.test/auth";
process.env.AUTH_AUD = "fad-api-test";
process.env.ENABLE_DEV_AUTH = "1";
process.env.DEV_AUTH_SECRET = "dev";
delete process.env.VERCEL_ENV;
const kp = await generateKeyPair("EdDSA", { crv: "Ed25519", extractable: true });
const priv = await exportJWK(kp.privateKey); priv.kid = "k1"; priv.alg = "EdDSA";
process.env.AUTH_SIGNING_KEY = JSON.stringify(priv);

const { app } = await import("../src/app.ts");

let failures = 0;
const check = (label, cond, extra = "") => {
  console.log(`${cond ? "✓" : "✗ FAIL"}  ${label}${extra ? "  — " + extra : ""}`);
  if (!cond) failures++;
};
const call = (method, path, { token, body } = {}) =>
  app.fetch(new Request("http://x" + path, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: "Bearer " + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  }));

// 1) dev-token (stubbed sign-in)
let r = await call("POST", "/auth/dev-token", { token: "dev", body: { provider: "dev", provider_uid: "alice" } });
const tok = await r.json();
check("dev-token mints a session", r.status === 200 && !!tok.access && !!tok.refresh);

// 2) whoami — signed in, no family yet
r = await call("GET", "/auth/whoami", { token: tok.access });
let who = await r.json();
check("whoami before family → no active membership", r.status === 200 && (who.families ?? []).length === 0, JSON.stringify(who));

// 3) create family
r = await call("POST", "/families", { token: tok.access, body: { name: "The Jacksons" } });
const fam = await r.json();
check("create family → returns familyId", (r.status === 200 || r.status === 201) && !!fam.familyId, JSON.stringify(fam));

// 4) whoami — now an active owner of the family
r = await call("GET", "/auth/whoami", { token: tok.access });
who = await r.json();
const m = (who.families ?? []).find((f) => f.family_id === fam.familyId);
check("whoami after create → active owner membership", !!m && m.role === "owner" && m.status === "active", JSON.stringify(who.families));

// 5) push a card (the CLI/authoring path the feed reads)
r = await call("PUT", `/families/${fam.familyId}/cards/card_s5`, {
  token: tok.access, body: { title: "Hello from S5", kind: "info", body_md: "It works." },
});
check("push a card → accepted", r.status === 200 || r.status === 201, "status " + r.status);

// 6) sync — the card comes back on the family-scoped feed
r = await call("GET", `/families/${fam.familyId}/sync`, { token: tok.access });
const page = await r.json();
const card = (page.changes?.cards ?? []).find((c) => c.id === "card_s5");
check("sync returns the pushed card", r.status === 200 && !!card && card.title === "Hello from S5", JSON.stringify(page.changes?.cards?.map((c) => c.id)));

console.log(failures === 0 ? "\nLIVE ROUND-TRIP PASS" : `\nLIVE ROUND-TRIP FAILED (${failures})`);
process.exit(failures === 0 ? 0 : 1);
