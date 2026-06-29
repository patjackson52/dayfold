# dayfold CLI — command reference

The skill drives ONLY these commands. Assume `dayfold` is on PATH and logged in.

## Auth

```
dayfold login [--allow-env-key]
```
RFC 8628 device grant — prints a user code + QR for an owner to approve in the
Dayfold app. Stores the refresh token in the OS keychain. `--allow-env-key`
permits a plaintext 0600-file fallback on hosts without a keychain (headless/CI).

```
dayfold logout
```
Revokes the current session server-side and clears the keychain.

```
dayfold whoami
```
Shows `family=<id> api=<url> (device|legacy)` and the credential's resolved
scope (`scope=content:read,content:write,...`).
If it shows `not signed in` → operator must `dayfold login`.

## Read current state (Phase C, and to get ids before push)

```
dayfold pull                 # {"cards":[...],"hubs":[...]}
dayfold pull --hub <hubId>   # that hub's full section/block tree
```

## Get a starter body

```
dayfold template <type>      # prints starter JSON to stdout
```
`<type>` ∈ card types `file link invite contact geo email`
        + hub-tree bodies `hub section block`.
Redirect to a file to edit: `dayfold template invite > card.json`.

## Push (PUT) — card by default, hub tree with a flag

```
dayfold push <cardId> card.json [--type <type>]     # briefing card
dayfold push <hubId> hub.json --hub                 # hub
dayfold push <sectionId> section.json --section     # section (body carries hubId)
dayfold push <blockId> block.json --block           # block (body carries sectionId)
```
- `--type` runs local structural validation against the generated schema BEFORE
  the network — catches wrong payload variant / unknown field / type mismatch.
  Without `--type`, a card is sent unchanged (no local validation).
- Hub/section/block pushes run an always-on structural pre-check (no flag needed).
- By default `push` auto-links bare phone/email in every `body_md` to tappable
  `tel:`/`mailto:` links and prints a diff — author plain text, not hand-rolled
  markdown links. `--no-linkify` stores the body verbatim.
- The path `<id>` overwrites the body `id` server-side — the body `id` can stay
  `REPLACE_WITH_CARD_ID`.
- Output: `push <resource>/<id> -> <httpStatus>`. Non-200 prints the server body
  to stderr and exits 1 — the server is the authority; fix and re-push.

## Delete

```
dayfold delete <id>          # remove a hub (cascades its sections + blocks)
dayfold delete <id> --card   # remove a briefing card
dayfold rm <id>              # alias for delete (hub by default)
```
There is no section/block-level delete at MVP. To remove a stray block, delete
its hub and re-push the tree.

## Other

```
dayfold update               # brew upgrade dayfold (or prints install instructions)
dayfold version              # print the running CLI version
dayfold help                 # print full usage
```

## Notes

- Generate stable ULIDs for new ids client-side (26-char Crockford base32). Reuse
  an existing id (from `dayfold pull`) to update rather than create.
- `dayfold pull` with no `--hub` returns all cards + all hub summaries; use it to
  enumerate existing content before authoring.
