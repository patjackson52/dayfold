# TASK-SYNC design — offline-first DB-as-source-of-truth (ADR 0020)

**Date:** 2026-06-19 · **Branch:** `task-sync` · **ADR:** 0020 (+ spec
`specs/prototype/08-mobile-client.md` §"Data freshness & offline-first sync").

## Goal & scope

Wire the existing-but-unwired `ContentStore` (SQLDelight) as the client's single
source of truth, replacing the M0 in-memory path (network → store). Land:

- **R1** — instant offline cold-start (render last-cached content, zero network).
- **R2** — foreground freshness (sync on resume + poll loop ~45 s).
- **R4** — DB is truth; strictly unidirectional `network → DB → store → UI`.

**Out of scope (deferred):** **R3 background sync** (Android `WorkManager` + iOS
`BGTaskScheduler` — iOS background needs the not-yet-built Xcode shell); push
(FCM/APNs/SSE); E2EE (ADR 0017, M1); 2-way/outbox (ADR 0016); the `payload`/`$defs`
richer card fields; iOS sync-config plumbing (iOS renders DB-only this slice).

## Dataflow

```
SyncClient ──applyDelta (1 txn)──▶ ContentStore (SQLDelight = truth)
                                        │ activeCardsFlow (reactive)
                                        ▼
                              dispatch CardsLoaded ──▶ redux store ──selectorState──▶ UI
```

The UI never reads the network. The network's only job is to write the DB. The
store is a reactive projection of the DB.

## Components (all `commonMain` unless noted)

### 1. ContentStore
Add `activeCardsFlow(): Flow<List<Card>>` via SQLDelight `coroutines-extensions`
(`activeCards().asFlow().mapToList(Dispatchers.Default)`) — **[H]** `Dispatchers.Default`
(native-safe; not `IO`). `applyDelta` / `cursor()` already exist + tested
(`ContentStoreTest`). **[D]** Enable `PRAGMA journal_mode=WAL` at driver creation
(in each `DriverFactory` actual).

### 2. SyncClient (rewrite → pure transport)
Reduce to a thin page fetcher (no store, no DB, no drain loop — that moves to the
engine, keeping transport trivially testable and orchestration in one place):

```
suspend fun fetchPage(since: String?): SyncResponse   // ktor GET; throws on non-200 / network error
```

Constructed with `(api, familyId, secret, http = HttpClient(), json)`.

### 3. SyncEngine (new) — orchestrator (owns the drain loop, DB writes, status)
Owns `ContentStore` + `SyncClient` + a `CoroutineScope(SupervisorJob() +
Dispatchers.Default)` + a **`Mutex`** **[B]**.

```
class SyncEngine(store, contentStore, syncClient, pollIntervalMs = 45_000, scope = …) {
  fun start()         // [C] bridge ONLY: collect activeCardsFlow → dispatch CardsLoaded.
                      //     This IS cold-start hydration — first emission = cached rows, no network.
  fun resume()        // [C] syncNow() + (re)start poll loop
  fun pause()         // stop poll loop (bridge keeps running)
  suspend fun syncNow() // [B][I] mutex-guarded full sync; public so future push can call it
  fun stop()          // cancel scope
}
```

`syncNow()` body (the drain loop): `mutex.withLock { dispatch(SyncStarted); try {
do { cursor = contentStore.cursor(); resp = syncClient.fetchPage(cursor);
contentStore.applyDelta(resp.changes.cards, resp.tombstones.filter{type=="card"}.map{id},
resp.nextCursor, nowIso) } while (resp.hasMore); dispatch(SyncSucceeded) } catch (e)
{ dispatch(SyncFailed(e.message)) } }`. `nowIso` via **`kotlinx-datetime`**
(`Clock.System.now().toString()`). Cursor is read from the **DB** each page (never
the store).

- **[A]** `SyncSucceeded` (status-only) clears `syncing`/`error`; cards reach the
  store only through the bridge.
- **[C]** `start()` never syncs (avoids the double-initial-sync). `resume()` owns
  the first + ongoing syncs.
- **[B]** `syncNow()` holds the `Mutex` so poll / resume / future-push never
  overlap and race the cursor.
- Poll loop = `while(active){ syncNow(); delay(pollIntervalMs) }` in a job
  cancelled by `pause()`. Fixed interval, no backoff (M0, YAGNI).

### 4. Model / Reducer
- **Add** `CardsLoaded(val cards: List<Card>)` → `state.copy(cards = action.cards)`
  (full replace; DB is truth).
- **`SyncSucceeded` becomes status-only** (no payload) → `syncing=false, error=null`
  **[A]**. Its old `LinkedHashMap` merge is **deleted** (that logic now lives in
  `ContentStore.applyDelta`, covered by `ContentStoreTest`).
- **Remove `cursor` from `AppState`** (lives in `sync_meta` now) + drop the
  `cursor` reference in the action-log middleware.
- `AppState` = `(cards, syncing, error)`. Keep `SyncStarted` / `SyncFailed`.

### 5. Shells
Each constructs `ContentStore.create(DriverFactory(…).createDriver())` +
`SyncEngine(store, contentStore, SyncClient(api, fam, sec))`, replacing the
ad-hoc `LaunchedEffect { SyncClient().sync(store) }`:
- **Desktop** (`Main.kt`): `DisposableEffect` → `engine.start(); engine.resume()`;
  dispose → `engine.stop()`. **[E]** desktop `DriverFactory` → **file DB** in an
  app-data dir (so desktop cold-start persists; was `IN_MEMORY`).
- **Android** (`MainActivity`): `engine.start()` once; **[F]**
  `repeatOnLifecycle(STARTED){ engine.resume(); awaitCancellation()… }` pattern
  (add `androidx.lifecycle:lifecycle-runtime-compose`) → `resume` on
  foreground, `pause` on background; `stop` on destroy. `DriverFactory(context)`.
- **iOS** (`MainViewController`): **[G]** `engine.start()` **only** (bridge →
  renders cached DB); no `resume`/sync until config plumbing lands (deferred).

## Dependencies
- `commonMain`: `app.cash.sqldelight:coroutines-extensions:2.3.2`,
  `org.jetbrains.kotlinx:kotlinx-datetime:0.6.1`.
- `androidApp`: `androidx.lifecycle:lifecycle-runtime-compose` (BOM-managed).
- `desktopTest`: `io.ktor:ktor-client-mock:3.1.1` (+ optional
  `app.cash.turbine:turbine` for Flow assertions).

All new commonMain deps publish KMP variants incl. iOS (datetime,
coroutines-extensions). ktor-mock is test/desktop only.

## Tests (desktopTest, TDD, ktor `MockEngine`)
1. **Offline cold-start** — pre-seed DB, `engine.start()` with a MockEngine that
   is never hit → store receives `CardsLoaded(cachedCards)`, zero network.
2. **sync → DB → UI** — MockEngine returns a page → `syncNow()` → cards in DB
   **and** in `store.cards` (via the bridge).
3. **Pagination drain + cursor** — `has_more` then final page → all cards land;
   `sync_meta.cursor` advanced; **page-2 request carries `since=<cursor>`**.
4. **Cursor survives restart** — sync, dispose, reopen `ContentStore` on the
   **same file DB** → `cursor()` persisted; next sync sends `since`.
5. **Tombstone** — delete page removes the card from DB **and** store.
6. **Status** — `SyncStarted`→syncing=true; success→`SyncSucceeded` clears;
   failure→`SyncFailed` sets error.
- **Rewrite `ReducerTest`** for `CardsLoaded` replace + status actions (the merge
  logic it used to test now belongs to `ContentStoreTest`).
- Keep `CardRenderTest` / `FeedScreenTest` / `FeedSnapshotTest` green (adjust for
  the `AppState` shape change only).

## Tradeoffs accepted (M0)
- **Broad recomposition:** full-list `CardsLoaded` + whole-`AppState`
  `selectorState` re-renders the feed each sync. Fine at M0 size; `LazyColumn`
  keyed by `card.id` mitigates. Future: narrower selectors / `fieldState`.
- **1-frame empty flash** on cold start (Flow is async). Optional later polish: a
  synchronous initial `activeCards()` read dispatched pre-composition.
- **Fixed-interval poll, no backoff** when offline.

## Future-feature fit (the unidirectional shape supports these)
- **Push** → calls `syncNow()` (the hook exists). **2-way/outbox** → outbox table
  feeds the same flow; engine gains a drain step. **E2EE** → decrypt in the bridge
  mapper. **Hubs / more types** → more tables + flows + `HubsLoaded`; `AppState`
  grows. `sync_meta.last_synced_at` already available for a future "updated Xm ago".
- **⚠ Multi-member family tenant (the defensible wedge):** cursor is currently
  per-family/household-token. Per-member views/cursor is a later milestone (ADR
  0011 auth/members) — keep `sync_meta` keyed so a member dimension can be added
  without a painful migration. **Do not** bake a single-viewer assumption into the
  cursor/keyset semantics.

## Definition of Done
Opens instantly offline from cache (R1); a foreground push reflects within one
poll interval (R2); `network → DB → store → UI` holds with cursor crash-safe
(R4); all 6 new tests + the adjusted existing tests green on desktop; Android APK
assembles + iOS framework links (no regression). R3 background explicitly out.
