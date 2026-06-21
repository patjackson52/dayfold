// CL-2 (ADR 0022 D1/D2) — type↔payload cross-validation.
//
// CL-1's BriefingCardSchema validates `type` (enum) and `payload` (a strict
// oneOf of the 6 single-key variants) INDEPENDENTLY — it does NOT enforce that
// the payload's variant key matches `type`. So `{type:"file", payload:{invite:…}}`
// passes zod. The CL-1 commit deferred this cross-check to "CL-2 server
// superRefine". This is it.
//
// Rule (M0, strict for renderer-safety): a card is *typed* iff it carries a
// payload — the two appear together or not at all — and when present the
// payload's single variant key MUST equal `type`. Legacy kind-only cards
// (neither field) stay valid (back-compat). Keeps the client invariant that a
// typed card always has a matching, renderable payload.

export const CONTENT_TYPES = ["file", "link", "invite", "contact", "geo", "email"] as const;
export type ContentType = (typeof CONTENT_TYPES)[number];

export type CrossIssue = { path: (string | number)[]; message: string };

/**
 * Returns [] when the card is consistent, else a zod-issue-shaped list (so the
 * PUT handler can surface it in the same 422 `issues` envelope as zod errors).
 * Operates on the already-zod-parsed card (so `payload`, if present, is a strict
 * single-key object and `type` is a valid enum member or undefined).
 */
export function crossValidateCard(card: { type?: unknown; payload?: unknown }): CrossIssue[] {
  const hasType = card.type != null;
  const hasPayload = card.payload != null;

  if (!hasType && !hasPayload) return []; // legacy kind-only card — valid

  if (hasType !== hasPayload) {
    return [{
      path: [hasType ? "payload" : "type"],
      message: hasType
        ? "a typed card (`type` set) must carry a matching `payload`"
        : "`payload` requires a `type` discriminator",
    }];
  }

  // both present — the payload's single key must equal `type`.
  const keys = Object.keys(card.payload as Record<string, unknown>);
  if (keys.length !== 1 || keys[0] !== card.type) {
    return [{
      path: ["payload"],
      message: `payload variant "${keys[0] ?? "(none)"}" does not match type "${String(card.type)}"`,
    }];
  }
  return [];
}
