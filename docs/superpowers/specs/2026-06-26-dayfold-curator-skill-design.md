# Dayfold Curator Skill — Design

**Date:** 2026-06-26
**Status:** Design (approved in brainstorming; pending spec review)
**Type:** Claude Code skill — the MVP authoring wedge (CLAUDE.md "content API + CLI + Claude skill")

## Purpose

A Claude Code skill, `dayfold-curator`, that turns a person's scattered context
(email, calendar, files, notes/second-brain) into **dayfold content** — Hubs and
BriefingCards — authored through the `dayfold` CLI.

The guiding test for every piece of content authored:

> Imagine yourself in the user's position going about their day. What content,
> surfaced in dayfold, would stop them from digging through multiple apps or
> searching their notes/second brain? Link directly to the app/content when
> possible; otherwise embed a snippet or the info itself.

The dashboard **renders** intelligence; this skill **produces** it. It is not a
chatbot.

## Confirmed decisions

| Decision | Choice |
|---|---|
| Source of truth location | dayfold repo, `.claude/skills/dayfold-curator/` |
| Context sources | Active read of connected MCPs (Gmail/Calendar/Drive) + pasted material |
| Authoring autonomy | Propose → confirm each → push (never push before approval) |
| Phase C (enrich) cadence | On-demand only (scheduled-task wiring deferred) |
| CLI drive | Assume `dayfold` on PATH, brew-installed + logged in; verify with `dayfold whoami` first |

## Background — the content model the skill authors

(Source of truth: `specs/domain-model/schemas/content.schema.json`.)

- **BriefingCard** — the "Now" feed surface. `type` ∈ `file·link·invite·contact·geo·email`.
  Carries `title`, `body_md`, `target` (deep-link `{hubId,sectionId,blockId}`),
  `triggers` (when/geo, matched on-device), `provenance`, `hubRef`, `related`,
  `not_before`/`expires_at`, `privacy`.
- **Hub → Section → Block** — project/event containers. Hub `type` ∈ bounded
  catalog `vacation·starting-college·move·party-event·new-baby·medical·school-year`.
  Block `type` ∈ `text·markdown·link·checklist·document·milestone·contact·location·budget`.
- The CLI is the transport: `template` (starter JSON), `push` (PUT card or hub
  tree, with opt-in local `--type` validation), `pull` (read current state back).
  The **server stays the authority**; the CLI validator is a fast structural
  pre-check only.

## Architecture — three phases, one skill

### Phase A — Onboard (first run per family)

1. **Verify prereqs.** `dayfold whoami` — confirm logged in + a family resolved.
   If not, stop and tell the operator to `dayfold login`.
2. **Ingest context.** Read what the operator pastes/points at, plus actively
   read connected MCPs:
   - Gmail MCP = the operator's **own** mail (satisfies Guardrail 3 — no
     server-side restricted-scope Gmail read; reasoning happens here).
   - Calendar MCP = events, recurring commitments.
   - Drive MCP = documents/links the operator already keeps.
3. **Deep-analyze → cluster** signals into candidate Hubs from the bounded
   catalog. Each candidate names: the life-thread, the signals feeding it, why it
   matters now.
4. **Onboarding questionnaire** — one question at a time: confirm family members
   (adults only as account holders), which threads matter, hub priority order,
   privacy comfort (what may be read, what stays on-device).
5. **Output:** an agreed **hub map**. No `push` in this phase.

### Phase B — Author (propose → confirm → push)

For each agreed hub:
- Build the Section→Block tree starting from `dayfold template hub|section|block`,
  fill real fields, validate locally, **show the operator the JSON**, and push on
  approval: `dayfold push <id> <file.json> --hub|--section|--block`.

For each signal worth surfacing **now**:
- Author a **BriefingCard** of the right `type`, with `target` deep-linking to its
  hub, `triggers` for time/place relevance, honest `privacy.storage` chip, and
  `provenance.source = claude`. Validate with `--type`, show JSON, push on approval:
  `dayfold push <cardId> card.json --type <type>`.

Batching: propose a hub's whole tree (or a batch of cards) for one approval rather
than one push at a time, but never push an un-approved batch.

### Phase C — Enrich (on-demand, over existing state)

1. `dayfold pull` (and `dayfold pull --hub <id>`) to read current hubs + cards.
2. **Empathy pass.** Walk the user's day hour by hour against what exists. For each
   moment, ask: *would they have to open another app or search notes to handle
   this?* Each "yes" is a content gap.
3. For each gap, choose the surfacing form, in priority order:
   - **Link directly** — deep link / `location.mapUrl` / source email thread URL /
     document ref. Always preferred.
   - **Embed** — `body_md` snippet, `contact` payload, `checklist`, `milestone`,
     `budget` — when a direct link doesn't exist or the info is small and the point
     is to avoid the click entirely.
4. Propose the new cards/blocks → confirm → push (same flow as Phase B).

## Guardrails (baked into the skill instructions)

- **Propose-confirm before every push.** External-action / operator-gated posture
  (CLAUDE.md). The skill drafts; the operator approves the push.
- **Email content over own mail only** (Guardrail 3). Never imply a server-side
  restricted-scope Gmail read.
- **Honest privacy chips.** `privacy.storage` must name a boundary the schema/code
  enforces — `on_device` for a cached copy, `location_local` only for live position
  (ADR 0014). Never overclaim.
- **Adults-only account holders** (Guardrail 1 / COPPA). Children appear only as
  subjects in a parent's own data.
- **Provenance.** Everything authored carries `provenance.source = claude` and
  `at`. Author from `dayfold template` (starters include `kind` + `provenance.at`)
  rather than bare stubs, per the validator's known asymmetries.
- **Server is the authority.** Local `--type` validation is a courtesy pre-check;
  a 422 from the server is the real gate — surface it, fix, re-push.

## Skill file layout

```
.claude/skills/dayfold-curator/
  SKILL.md            # name, description (triggering), the 3-phase workflow
  references/
    content-model.md  # condensed card/hub/block field reference for authoring
    cli.md            # dayfold command cheatsheet (whoami/template/push/pull)
    guardrails.md     # the privacy/consent/provenance rules, copied close to use
  install.sh          # symlink into ~/.claude/skills/ (global) — documented
```

`SKILL.md` frontmatter `description` triggers on intent like "set up my dayfold,"
"author dayfold content," "what should be on my dashboard," "enrich my hubs."

## Installation (public-repo friendly)

Canonical source committed in the dayfold repo. Three documented install paths:

1. **Global** — `install.sh` symlinks the skill dir into `~/.claude/skills/`
   (available in every project).
2. **Per-project** — copy the dir into a target repo's `.claude/skills/`.
3. **Plugin** (deferred) — add `.claude-plugin/plugin.json` + a marketplace entry
   so others `/plugin install` from the public repo, versioned.

No secrets in the skill — credentials live in the OS keychain / env, never the repo.

## Testing / verification

- **Dry-run authoring:** run Phase A→B against a sample pasted context, confirm the
  proposed JSON validates with `dayfold push --type` against a local/dev API, and
  that nothing is pushed without an explicit approval step.
- **Guardrail checks:** assert the skill refuses to author an email card from
  anything other than the operator's own mail, and that every authored card carries
  a `privacy.storage` chip the schema permits.
- **Phase C loop:** push a small hub, run enrich, confirm it `pull`s current state
  and proposes only net-new, non-duplicate content.

## Out of scope (YAGNI for v1)

- Scheduled / autonomous enrichment runs (Phase C is on-demand).
- Auto-push without confirmation.
- Plugin packaging + marketplace (documented as a later path).
- Any new CLI commands — the skill uses the existing `template/push/pull/whoami`.
- Hub-tree local validation beyond what the CLI already does (server is authority).
