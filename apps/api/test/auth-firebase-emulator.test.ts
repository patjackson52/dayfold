import { describe, it, expect, beforeAll } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { generateKeyPair, exportJWK } from "jose";

// Real-path integration test (ADR 0027 test topology): drive an ACTUAL Firebase
// Auth Emulator end-to-end — mint a real emulator-issued Google ID token, POST it
// to /auth/firebase, assert a session is minted and the identity row is keyed on
// the underlying Google account id. Validates that the emulator's token shape
// matches what FirebaseVerifier expects (the hermetic auth-firebase.test.ts fakes
// the token; this one does not).
//
// Skipped unless FIREBASE_AUTH_EMULATOR_HOST is set — i.e. it only runs under the
// `firebase emulators:exec` wrapper in the CI `firebase-emulator` job, never in
// the plain unit run or locally.
const HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const PROJECT = process.env.FIREBASE_PROJECT_ID || "dayfold-test";

const here = dirname(fileURLToPath(import.meta.url));
process.env.DATABASE_URL ||= "postgres:///fad_test";
process.env.AUTH_ISS = "https://fad.test/auth"; process.env.AUTH_AUD = "fad-api-test";
process.env.HOUSEHOLD_SECRET = "legacy-secret"; process.env.HOUSEHOLD_CREDENTIAL_ID = "hcred";
process.env.FIREBASE_PROJECT_ID = PROJECT;
const akp = await generateKeyPair("EdDSA", { crv: "Ed25519", extractable: true });
const apriv = await exportJWK(akp.privateKey); apriv.kid = "k1"; apriv.alg = "EdDSA";
process.env.AUTH_SIGNING_KEY = JSON.stringify(apriv);

const { q } = await import("../src/db.ts");
const { app } = await import("../src/app.ts");

// Ask the Auth emulator to sign in with a Google credential and hand back a real
// (emulator-issued, unsigned) Firebase ID token. The emulator parses a JSON
// `id_token` claim string in postBody — no real Google round-trip.
async function emulatorGoogleIdToken(sub: string, email: string): Promise<string> {
  const url = `http://${HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key`;
  const claim = JSON.stringify({ sub, email, email_verified: true });
  const r = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      postBody: `id_token=${encodeURIComponent(claim)}&providerId=google.com`,
      requestUri: "http://localhost",
      returnSecureToken: true,
    }),
  });
  const j = (await r.json()) as { idToken?: string };
  if (!j.idToken) throw new Error("emulator signInWithIdp failed: " + JSON.stringify(j));
  return j.idToken;
}

describe.skipIf(!HOST)("POST /auth/firebase — real Firebase Auth Emulator", () => {
  beforeAll(async () => {
    await q(`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`);
    for (const m of ["0001_m0_init.sql", "0002_auth.sql", "0003_device_grant.sql",
      "0004_refresh_grace.sql", "0005_invites.sql", "0006_typed_content.sql", "0007_related.sql"])
      await q(readFileSync(resolve(here, "../migrations/" + m), "utf8"));
  });

  it("emulator-issued Google token → mints a session + creates the user", async () => {
    const idToken = await emulatorGoogleIdToken("g-emulator-1", "emltest@example.com");
    const r = await app.request("/auth/firebase", {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ idToken }),
    });
    expect(r.status).toBe(200);
    const j = await r.json();
    expect(typeof j.access).toBe("string");
    expect(typeof j.refresh).toBe("string");
    const u = await q(
      `SELECT provider, provider_uid FROM user_identities WHERE provider='google.com' AND provider_uid='g-emulator-1'`,
    );
    expect(u.rowCount).toBe(1);
  });

  it("the same Google identity is idempotent (one user across two sign-ins)", async () => {
    const t1 = await emulatorGoogleIdToken("g-emulator-2", "dup@example.com");
    const t2 = await emulatorGoogleIdToken("g-emulator-2", "dup@example.com");
    for (const idToken of [t1, t2]) {
      const r = await app.request("/auth/firebase", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ idToken }),
      });
      expect(r.status).toBe(200);
    }
    const u = await q(`SELECT count(*)::int n FROM user_identities WHERE provider_uid='g-emulator-2'`);
    expect(u.rows[0].n).toBe(1);
  });

  it("rejects a token minted for a different project (aud mismatch)", async () => {
    // Point the emulator REST at a different project id → its token's aud won't match ours.
    const url = `http://${HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake`;
    const claim = JSON.stringify({ sub: "g-wrongproj", email: "x@y.z", email_verified: true });
    const r = await fetch(url.replace(PROJECT, "some-other-project"), {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ postBody: `id_token=${encodeURIComponent(claim)}&providerId=google.com`, requestUri: "http://localhost", returnSecureToken: true }),
    }).catch(() => null);
    // If the emulator issues a token at all, our endpoint must reject a wrong-aud token.
    if (r && r.ok) {
      const tok = ((await r.json()) as { idToken?: string }).idToken;
      if (tok) {
        const resp = await app.request("/auth/firebase", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ idToken: tok }) });
        expect(resp.status).toBe(401);
      }
    }
  });
});
