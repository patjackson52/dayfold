import { describe, it, expect } from "vitest";
import { constantTimeEqual, stripServerManaged, stampProvenance } from "../src/security.ts";
import { BriefingCardSchema } from "../src/generated/content.ts";

describe("household-token compare", () => {
  it("accepts the matching secret", () => {
    expect(constantTimeEqual("s3cr3t-token", "s3cr3t-token")).toBe(true);
  });
  it("rejects a wrong/short secret (no length leak)", () => {
    expect(constantTimeEqual("s3cr3t-token", "wrong")).toBe(false);
    expect(constantTimeEqual("", "s3cr3t-token")).toBe(false);
  });
});

describe("mass-assignment strip (content writes)", () => {
  it("drops server-managed fields, keeps content", () => {
    const out = stripServerManaged({ title: "Party", family_id: "ATTACKER", version: 999, created_at: "x" });
    expect(out).toEqual({ title: "Party" });
  });
  it("stamps credential_id, can't be forged by the client", () => {
    const out = stampProvenance({ provenance: { source: "claude", credential_id: "FORGED" } }, "cred-real");
    expect((out.provenance as any).credential_id).toBe("cred-real");
    expect((out.provenance as any).source).toBe("claude");
  });
});

describe("schema validation (generated zod from single source)", () => {
  it("accepts a valid briefing card", () => {
    const r = BriefingCardSchema.safeParse({
      id: "01J0000000000000000000000A", kind: "info", title: "Party Sat",
      provenance: { source: "claude", at: "2026-06-18T10:00:00Z" },
    });
    expect(r.success).toBe(true);
  });
  it("rejects an invalid card (bad kind / missing title)", () => {
    const r = BriefingCardSchema.safeParse({ id: "01J0000000000000000000000A", kind: "nope" });
    expect(r.success).toBe(false);
  });
});
