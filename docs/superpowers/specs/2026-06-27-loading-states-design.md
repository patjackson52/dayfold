# Loading States — Design

**Date:** 2026-06-27
**Branch:** `worktree-loading-states` (from `main`)
**Surface:** Dayfold Compose Multiplatform client (`apps/client`, `apps/androidApp`)
**Status:** Approved (design) — pending multi-agent review → implementation plan

## Problem

Several user-triggered async actions show no feedback; the app appears frozen.
Named by operator: **sign-in** ("Continue with Google", etc.) and **sign-out**.
A full audit found more.

### Audit findings

State pattern today: each Redux slice has its own `*Busy: Boolean` / `*Error: String?`
flags (`authBusy`, `syncing`, `hubsBusy`, `deviceBusy`, `approvalsBusy`, …) in
`Model.kt` (`AppState`, ~L283). No shared `Loadable`/`AsyncState` wrapper. Per-action
loading (which member row is approving) is not tracked. Only two real spinners exist
(`SplashScreen` cold-start, `DeviceFinishingScreen`). No skeletons/shimmer. Theme is
Material 3 (`theme/Theme.kt`), custom Box-based `AuthButton` (not M3 `Button`).

| Action | Composable | Current | Gap |
|---|---|---|---|
| Sign in (provider) | `SignInScreen` (AuthScreens.kt:185) | buttons disabled on `authBusy`, no spinner | silent-ish |
| Sign out | `AccountScreen` (AccountScreen.kt:151) | clickable Box, nothing | **silent** |
| Create family | `CreateFamilyScreen` (AuthScreens.kt:246) | text "Creating…" | weak |
| Join invite | `JoinInviteScreen` (:90) | text "Joining…" | weak |
| Load approvals/roster | `MembersScreen` (:38) | nothing | **silent** |
| Approve/Decline/Remove member | `MembersScreen` rows (:91,:116) | nothing, no per-row flag | **silent** |
| Load devices | `DevicesScreen` (:35) | nothing, no flag | **silent** |
| Revoke device | `DevicesScreen` row (:86) | nothing, no per-row flag | **silent** |
| Enter code / approve device | `DeviceApprovalScreens` (:151,:209) | text "Checking…/Working…" | weak |
| Feed initial sync | `FeedScreen` (:44) | text "Syncing…" | weak |
| Feed pull-to-refresh | `FeedScreen` | not implemented | **missing** |
| Feed error retry | `FeedScreen` (:125) | no busy on retry | weak |
| Load hubs / hub detail | `HubScreens` (:71,:200) | text "Loading hubs…/Loading…" | weak |
| Load hub audience | `WhoCanSeeSheet` (:229) | nothing | **silent** |
| Cold-start restore | `SplashScreen` | CircularProgressIndicator | ✅ ok |
| Device resume (deep link) | `DeviceFinishingScreen` | CircularProgressIndicator | ✅ ok |

## Decisions (operator-approved)

1. **Scope:** full coherent pass — fix silent actions, upgrade text-only states, add
   list/content skeletons + pull-to-refresh; one consistent loading vocabulary.
2. **Vocabulary:** full Material 3 kit — button-busy, skeleton/shimmer, `PullToRefreshBox`,
   full-screen splash, reusable components.
3. **State model:** keep the `*Busy/*Error` boolean pattern (low blast radius, matches
   codebase); add in-flight **id-set** tracking where per-row busy is needed. No
   `Loadable<T>` refactor.

## Principles (Material 3 + mobile UX)

- Acknowledge every tap in <100ms — action buttons flip to busy instantly.
- Skeletons where layout is known (lists, feed cards); spinners where it isn't
  (button actions, blocking restores). Skeletons improve perceived speed.
- Inline over full-screen. Block the whole screen only when the screen can't exist
  yet (cold-start restore, sign-out teardown).
- No flash: screen-level skeletons/spinners gated by a min-duration helper so fast
  responses don't flicker. Button-busy is exempt (it is the press acknowledgement).
- Accessible: indicators carry semantics ("Loading"); busy buttons announce
  disabled+busy. All states are network → indeterminate indicators.

## Component kit — new package `client/.../ui/loading/`

All in `commonMain` (no platform deps; Compose-MP `rememberInfiniteTransition` for
shimmer). Reusable, unit/snapshot-testable.

| Component | Responsibility | Notes |
|---|---|---|
| `AuthButton(busy: Boolean)` (extend existing) | tapped provider/action button | label stays, leading glyph → 18dp `CircularProgressIndicator` in `content` color, `enabled=false` while busy |
| `RowBusy()` | 16dp inline spinner replacing a list-row's trailing affordance | approve/decline/remove/revoke rows |
| `Modifier.shimmer()` | animated shimmer brush sweep | foundation for skeletons |
| `SkeletonBox` + `FeedSkeleton`, `HubListSkeleton`, `MemberListSkeleton`, `DeviceListSkeleton` | placeholder layouts mirroring real content shape | use `shimmer()` |
| `FullScreenLoading` | center mark + spinner | reuse/share `SplashScreen` pattern |
| `LoadingScrim` | dim scrim + centered spinner overlay | sign-out teardown |
| `rememberStableLoading(flag): Boolean` | anti-flash: delay-show (~120ms) + min-visible (~400ms) | screen-level states only |

M3 `PullToRefreshBox` (compose-material3 in CMP 1.9.3) wraps the FeedScreen list.

## State changes (`Model.kt` + `Reducer.kt`)

Additive only; existing flags untouched.

- `pendingProvider: String?` — which sign-in provider button spins.
- `signOutBusy: Boolean` — drives `LoadingScrim` + sign-out button busy.
- `devicesBusy: Boolean` — device list load (no flag existed).
- `audienceBusy: Boolean` — WhoCanSee audience load.
- Members: `approvingIds: Set<String>`, `removingIds: Set<String>`.
- Devices: `revokingIds: Set<String>`.

Reducer: on `*Requested` insert the id into the set (or set the boolean); on the
matching success/fail action remove the id (or clear the boolean). Decline shares the
members in-flight treatment.

## Screen application

- **Sign-in** (`SignInScreen`): set `pendingProvider` on tap → that `AuthButton`
  shows busy spinner, others `enabled=false`.
- **Sign-out** (`AccountScreen`): confirm dialog → on confirm set `signOutBusy` →
  sign-out button busy + `LoadingScrim` over the screen until session clears/route
  changes.
- **Create-family / join / enter-code / approve-device**: replace text-morph with
  `AuthButton(busy=…)` (label stays).
- **Feed** (`FeedScreen`): initial load → `FeedSkeleton` (gated by
  `rememberStableLoading(syncing)`); wrap list in `PullToRefreshBox` driving the
  existing sync; error-retry button → button-busy.
- **Hubs** (`HubListScreen` / `HubDetailScreen`): replace "Loading…" text with
  `HubListSkeleton` / detail skeleton.
- **Members / Devices**: list load → `MemberListSkeleton` / `DeviceListSkeleton`;
  per-row `RowBusy` via the id sets.
- **WhoCanSeeSheet**: small spinner while `audienceBusy`.

## Verification

- **Fake backend** (`client/.../fake/FakeBackend.kt`): add an artificial-latency knob
  so loading states are observable; reuse scenarios `sync-error`, `owner-approvals`,
  `busy-family`, `empty-new`. Desktop `DAYFOLD_API=fake://…`; Android debug drawer.
- **`rk` snapshot PNGs** + `apps/scripts/ondevice-demo.sh` for on-device visual check.
- **Tests:** reducer tests for the id-set / new-boolean transitions; Compose snapshot
  tests for each `Skeleton*` component (repo already snapshot-tests cards, ADR 0036).

## Out of scope

- No `Loadable<T>`/sealed `AsyncState` refactor (explicitly deferred).
- No new network/optimistic-update behavior — purely feedback/presentation.
- No changes to non-client surfaces (api, cli).

## Review plan

Three parallel review agents before the implementation plan: (1) mobile-UX / M3
critique, (2) Compose + redux-kotlin correctness, (3) simplification / YAGNI. Fold
feedback, then `writing-plans`.
