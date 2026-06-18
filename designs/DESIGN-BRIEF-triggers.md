# Design Brief / Prompt — Triggers, Notifications & Time/Location Content

**Hand this whole file to a fresh Claude Code (Claude Design) session.** It is
self-contained. Authoritative source: `../adr/0014-private-trigger-engine.md`,
`../adr/0009-design-system-m3-expressive-adaptive.md`, and the existing system
+ Now/Hubs mockups in `Family AI dashboard design brief/`.

---

## 0. How to run this

> **You are designing the hi-fi UI/UX for the private trigger engine** of
> family-ai-dashboard. Use the `frontend-design` skill. Produce **interactive
> HTML/CSS prototypes** that faithfully emulate **Material 3 Expressive**
> (reuse the tokens/type/shape/motion/components from the existing
> `Design-System` mockup — do NOT invent a new system). Mobile-first
> (~390–430px), **light + dark** for every screen. Map components to M3
> Compose names. Commit to `designs/triggers/`. Visuals only.

## 1. Context (what this feature is)

The app surfaces content based on **device location, date/time, and (later)
activity** — but **privately**: triggers are metadata Claude attaches to
content; the **device matches them on-device**; the **user's live location/
time never leaves the device**. Progressive permission (when-in-use first;
"Always" is an opt-in upgrade for background proximity). Calm: few, timely
notifications that earn the interruption.

**Make the privacy promise visible** — a subtle, trustworthy "matched on your
device · your location never leaves" affordance is a brand differentiator, not
fine print. Honest, never dark-pattern.

## 2. Brand & tone (inherit ADR 0009)

Vibrant, expressive **visuals**; calm **behavior**. Warm, human, not childish.
Provenance on AI content ("added by Claude"). No gamification, no engagement-
bait, no notification spam.

## 3. Screens & states to design

**A. Trigger affordances ON content (the core)**
1. A **briefing card with a time trigger** — countdown / "tomorrow 4pm" / alert
   chip; show the `when` affordance.
2. A **briefing card with a geo trigger** — a "near *Place*" / location chip;
   and its **active state** (you're currently in proximity → highlight/pulse,
   the M3E emphasized treatment).
3. A **Hub block with triggers** — e.g. a checklist item or doc with a
   time/place chip; how triggers read inside the dossier.
4. The **trigger-active highlight** vs **inactive** states, clearly distinct.

**B. Notifications**
5. **System notification** appearance (Android + iOS): a proximity notification
   ("Near the store — party list?") and a time notification ("Soccer 4pm — pack
   jackets"). Calm copy, source attribution.
6. **Tapping a notification → deep-links** into the exact card/Hub block
   (reuse the Now/Hubs deep-link highlight state).
7. **Notification grouping / quiet-hours / daily-cap** treatment — how "few,
   timely" looks; a digest-style group rather than a stream.
8. **In-app surfacing** of a just-fired trigger (a gentle banner/section in
   Now), as an alternative to a system notification.

**C. Permission flows (privacy-forward)**
9. **Location priming** screen (before the OS prompt): why we ask, the
   when-in-use ask, the on-device promise. Then the **"Always" upgrade** prompt
   (explicit opt-in for background proximity) — honest about the tradeoff.
10. **Notification priming** screen.
11. **Permission-limited states:** when-in-use only → an honest "open the app
    to see what's nearby" explainer (no nagging); denied → graceful fallback.

**D. Places management**
12. **Places** screen — define/edit home / school / store (label + map pin +
    radius). Family-scoped. The "your places stay private" affordance.
13. Add-a-place flow (map pin + radius slider).

**E. Cross-cutting**
14. The **"matched on your device"** privacy affordance — design the reusable
    component (a chip / info row / sheet) used across the above.
15. Offline / no-signal states for trigger surfacing.

## 4. Adaptive (specs + one frame each)

Phone gets full hi-fi. For tablet/desktop give a short note + one frame
(places management benefits from a larger map; notifications surface in the
Now pane). **Wear OS:** a proximity/time trigger is the *ideal* glanceable
tile — include one Wear tile concept ("Near store — list"). Activity triggers
are **out of scope** (schema slot only).

## 5. Constraints (honor or call out)

- Calm: notifications are few; design the cap/quiet-hours, not a feed.
- Honest privacy: never imply we track the user; "on-device" must be true in
  the visuals (no server-side-tracking iconography).
- Provenance on AI-authored triggers/content.
- Progressive permission — never request "Always" up front; the upgrade is
  opt-in and reversible.
- Reuse the existing M3E system + Now/Hubs deep-link states; don't fork them.

## 6. Output structure (commit here)

```
designs/triggers/
  index.html              (click-through index — update it)
  content-triggers/       (A: time/geo chips + active-highlight, L+D)
  notifications/          (B: system notifs, grouping, deep-link, in-app)
  permissions/            (C: location/notif priming + limited states)
  places/                 (D: places mgmt + add-place)
  privacy-affordance/     (E: the "matched on your device" component)
  adaptive/               (tablet/desktop frames + a Wear trigger tile)
```

## 7. Definition of done
- All §3 phone screens, light + dark, clickable from `index.html`.
- The privacy affordance designed as a reusable component and shown in context.
- Notification designs for both Android + iOS, with the deep-link-on-tap state.
- Progressive-permission flow (when-in-use → Always opt-in) + limited states.
- Places management + add-place. One Wear trigger tile. Adaptive notes.
- Calm/honest/provenance constraints visibly satisfied; operator can approve.
