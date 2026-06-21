# AUTH-S5 — Client Identity + Onboarding UI (design)

**Status:** Draft 2026-06-20. Gated by ADR 0008 (A8b mockups merged → cleared),
ADR 0011/0021 (architecture + decomposition), **ADR 0023** (Google + Apple,
Phone-OTP deferred). Build posture: **Firebase-stubbed via the dev-token path**
(operator-chosen 2026-06-20) — the client establishes a real backend session
without Firebase; the Firebase provider SDK glue lands at S2 behind the same UI.

Design source: `designs/Family AI dashboard design brief/designs/Auth-Phone.dc.html`
(views: `signin`, `createfamily`, `familynull`; later `invited`/`waiting`/…).
Hardened flow spec: `specs/auth-and-family-design.md` §Flow 1, §Flow 2.

## Scope

**S5 slice 1 (this build) = authenticated session + onboarding gate.** The app
goes from "baked household secret, single feed screen" to "sign in → reach (or
create) a family → feed", with a real backend session.

In:
- **Sign-in screen** — Dayfold mockup `signin`, **Google + Apple only** (ADR 0023;
  no phone button). Buttons drive a **dev-token sign-in** (stubbed identity) until
  S2. A debug-only provider picker (`dev:alice` etc.) is acceptable behind the
  same buttons.
- **Create-family screen** — mockup `createfamily`. `POST /families {name}`.
- **Family null state** — mockup `familynull` (owner, 0 other members; CTAs are
  visual only at S5 slice 1 — Invite/Connect wire up in S6/slice 2).
- **Route gate** — first nav in the app: `Loading → SignIn → (CreateFamily |
  Feed)` derived from session + memberships.
- **Session layer** — `AuthClient` (dev-token / whoami / families / refresh /
  signout), an **expect/actual token store** (persist access+refresh), and
  `SyncClient`/`SyncEngine` rewired to use the session access token + the selected
  `family_id` instead of `BuildConfig.HOUSEHOLD_SECRET`.
- **Sign-out** — clears the token store → back to SignIn.

Out (later slices, tracked):
- **S5 slice 2:** invitee-join (`invited`/`waiting`/`inviteerror`/`alreadymember`),
  link-a-2nd-method (Google↔Apple), provider-link-conflict.
- **S2:** real Firebase Google/Apple SDK glue (replaces the dev-token call behind
  the same buttons); ID-token verify already exists server-side.
- **S6:** invite generation, authorize-device, members+approvals, connected
  devices, account export/delete.
- **Deferred (ADR 0023):** phone button, OTP entry, OTP error/resend screens.
- Secure-storage hardening (Keychain/EncryptedSharedPrefs/0600) may land as a
  slice-1 follow if the first cut uses a plain persisted file — call it out.

## State design (new in `AppState`)

```kotlin
@Serializable data class Session(val access: String, val refresh: String, val userId: String? = null)
@Serializable data class FamilyMembership(val familyId: String, val name: String, val role: String, val status: String) // status: active|pending
enum class Route { Loading, SignIn, CreateFamily, Feed }   // FamilyNull folded into Feed-with-no-cards? NO — see below

data class AppState(
  // existing feed surface
  val cards: List<Card> = emptyList(),
  val syncing: Boolean = false,
  val error: String? = null,
  // NEW — auth/session
  val session: Session? = null,
  val families: List<FamilyMembership> = emptyList(),
  val activeFamilyId: String? = null,
  val route: Route = Route.Loading,
  val authBusy: Boolean = false,
  val authError: String? = null,
)
```

**Route derivation (the gate, pure):**
- no `session` → `SignIn`
- `session` + **no active membership** → `CreateFamily` (slice-1; the only way in
  is to create one — invitee-join is slice 2)
- `session` + an **active** membership → `Feed` (with `activeFamilyId` = first
  active). A `Feed` with zero cards renders the **family-null** content when the
  active family has no members beyond the owner — i.e. **family-null is a Feed
  substate**, not a separate route. *(Decision: keep Route small; null-state is a
  property of the feed surface, gated on `families`/membership, not a route.)*
- `route = Loading` only during cold-start token-restore + first whoami.

## Actions (new)

```kotlin
data object AuthRestoring : Action                         // cold-start: reading token store
data class SessionRestored(val session: Session?) : Action // null → SignIn
data class SignInRequested(val provider: String) : Action  // "google"|"apple" (dev: maps to dev-token)
data class SignInSucceeded(val session: Session) : Action
data class SignInFailed(val message: String) : Action
data class MembershipsLoaded(val families: List<FamilyMembership>, val activeFamilyId: String?) : Action
data class CreateFamilyRequested(val name: String) : Action
data class FamilyCreated(val familyId: String, val name: String) : Action
data class AuthOpFailed(val message: String) : Action
data object SignOutRequested : Action
data object SignedOut : Action
```

Reducer is pure; all I/O (HTTP, token store) lives in an **`AuthEngine`**
(commonMain, suspend, mutex-guarded like `SyncEngine`) that dispatches these.

## AuthClient (commonMain, ktor suspend)

```kotlin
class AuthClient(api: String, http: HttpClient = …, json: Json = …) {
  suspend fun devToken(provider: String, providerUid: String): Session   // POST /auth/dev-token (Bearer DEV_AUTH_SECRET)
  suspend fun whoami(access: String): WhoamiResponse                      // GET /auth/whoami → {family_id, families[]}
  suspend fun createFamily(access: String, name: String): String         // POST /families → {familyId}
  suspend fun refresh(refresh: String): Session                          // POST /auth/refresh
  suspend fun signout(access: String)                                    // POST /auth/signout
}
```
- Dev-token needs the `DEV_AUTH_SECRET` (local/test only; **never shipped** — the
  endpoint hard-refuses in prod/preview per ADR 0021 §4). At S2 the Google/Apple
  buttons call Firebase → ID token → a new `POST /auth/session` verify path; the
  dev-token call is debug-only.
- Access token is short (5 min) → `SyncClient`/`AuthClient` calls that 401 trigger
  one `refresh()` + retry (the refresh-grace already exists server-side, S3).

## Token store (expect/actual)

```kotlin
expect class TokenStore { fun load(): Session?; fun save(s: Session); fun clear() }
```
- desktop: file under `~/.family-ai-dashboard/session.json`, **0600**.
- android: `EncryptedSharedPreferences` (or DataStore + Tink) — slice-1 may start
  with plain prefs + a follow ticket.
- ios: Keychain (slice-1 may start with a file + a follow ticket).

## Wiring (`FeedApp` becomes a router)

```kotlin
@Composable fun FeedApp(store: Store<AppState>) {
  val s by store.selectorState { it }
  DayfoldTheme {
    when (s.route) {
      Loading     -> SplashScreen()
      SignIn      -> SignInScreen(busy = s.authBusy, error = s.authError, onProvider = { store.dispatch(SignInRequested(it)) })
      CreateFamily-> CreateFamilyScreen(busy = s.authBusy, error = s.authError, onCreate = { store.dispatch(CreateFamilyRequested(it)) })
      Feed        -> FeedScreen(s, onSignOut = { store.dispatch(SignOutRequested) })
    }
  }
}
```
- App entry (Android/desktop/iOS) constructs `AuthEngine` + `SyncEngine`; on
  start, `AuthEngine.restore()` (token store → whoami → memberships) sets the
  route; `SyncEngine` only starts once `activeFamilyId` is set.
- `SyncClient` constructor changes from `(api, familyId, secret)` to taking a
  **token provider** + `activeFamilyId` (so a refresh swaps the access token
  without rebuilding the client). Legacy `HOUSEHOLD_SECRET` env path stays as a
  dev fallback until the S3 cutover lands.

## Verification

- **Reducer tests** (pure, fast): every route transition — restore(null)→SignIn,
  SignInSucceeded→ (memberships gate) →CreateFamily/Feed, FamilyCreated→Feed,
  SignedOut→SignIn; authBusy/authError set+cleared.
- **AuthClient tests** against a fake ktor `MockEngine`: dev-token, whoami parse
  ({family_id,families}), createFamily, refresh, signout; 401→refresh→retry.
- **TokenStore** desktop test: save→load→clear round-trip; 0600 perms.
- **Snapshot tests** (`rk snapshot` / FeedSnapshotTest pattern): `SignInScreen`
  (light+dark, idle + busy + error), `CreateFamilyScreen`, family-null feed
  substate — `Read` the PNGs, compare to the `signin`/`createfamily`/`familynull`
  mockups for cohesiveness.
- **Live round-trip** (desktop, `ENABLE_DEV_AUTH=1` local API): launch → dev
  sign-in → create family → feed syncs a card pushed via CLI. Confirm via the
  `[redux]` action log + `rk devtools`.
- DoD: app cold-starts to SignIn with no session; dev sign-in + create-family
  reaches a syncing Feed; sign-out returns to SignIn; tokens survive restart;
  desktopTest green (count-checked) + snapshots match mockups; no
  `HOUSEHOLD_SECRET` needed for the JWT path.

## Risks / notes

- **First navigation in the app** — keep it a pure `when(route)`; no nav library
  (matches the redux `f(state)→UI` posture, ADR 0013).
- redux-kotlin alpha01 gotchas (extensions, granular dep) already solved — see
  `processes/agent-dev-loop.md`.
- Fonts still `FontFamily.Default` (CL-0b follow) — auth screens inherit that;
  not an S5 blocker.
- The dev-token secret must never reach a shipped build — gate it to debug/CLI
  env exactly as the server gates the endpoint.
