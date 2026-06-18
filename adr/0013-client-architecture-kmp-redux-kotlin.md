# ADR 0013: Client Architecture — KMP/CMP Shared UI + redux-kotlin

## Status

**Accepted** 2026-06-18 (operator directive, in-session). Immutable —
supersede, do not edit. Client architecture decision; extends ADR 0004
(Compose Multiplatform) and ADR 0009 (M3 Expressive); composes with ADR 0012
(agent-operated build). Feeds component 08 (`specs/prototype/08-mobile-client.md`).
Reference: <https://reduxkotlin.org/ai-agents/building-with-ai-agents>.

## Context

The product needs clients on phone (Android+iOS) now and tablet/foldable/
desktop/web later (ADR 0009). The operator directs: **maximize KMP code AND
UI sharing across all frontend clients**, and use **redux-kotlin
`1.0.0-alpha1`** as the state architecture. redux-kotlin is a Kotlin
Multiplatform Redux port that is **explicitly optimized for AI-agent
development** (ships an `AGENTS.md`, per-concern guides, a `.claude/skills/
redux-kotlin/`, and a `./gradlew build` verify loop) — directly amplifying
ADR 0012's agent-buildability goal.

## Decision

1. **One KMP/Compose-Multiplatform codebase — shared business logic AND
   UI** across **Android, iOS, Web (Wasm/JS), Desktop**. Per-platform code is
   confined to thin `expect/actual` shims (platform integration: secure
   storage, deep-link glue, push later). Build sequencing per ADR 0007: the
   prototype ships **Android + iOS** first; web/desktop are compile-targets
   off the same code, enabled later — no re-foundation.
2. **State management = redux-kotlin `1.0.0-alpha1`** (pin exact coordinates
   at setup; the AI-agents page shows the `0.6.x` line — confirm the alpha1
   module set). Module set: core + `threadsafe`/`concurrent` (atomic/lock-free
   store) + `granular` (field subscriptions) + `compose` (`State<T>`
   bindings) + `compose-saveable` (process-death persistence) + `multimodel`
   if heterogeneous state is needed.
3. **Adopt redux-kotlin's design rules**, notably:
   - **Render isolation (Rule C):** composables bind the **narrowest slice**
     via `selectorState`/`fieldStateOf` — never read state wholesale.
   - **Off-main effects (Rule E):** all side effects (API sync, content pull,
     auth, storage) originate **only in middleware**, run off-main, and
     marshal back via `NotificationContext`. Reducers stay pure.
   - **Mint at edge (Rule G):** IDs/timestamps from generators at dispatch
     sites, never in reducers (matches our client-supplied stable IDs).
   - **State-keyed lifecycle (Rule I):** data loads key on **state**
     (navigation-derived slices), not events — which gives **deep-link +
     time-travel support natively**. This is how the **card→block deep-link**
     (ADR 0006) and the "that item moved" fallback are implemented.
4. **Package-by-feature layout:** `feature/<name>/{model, actions, reducer,
   effects, screen, selectors, tests}` + `core`, `infra`, `app`, `ui`.
   Surfaces (Now, Hubs, Auth) are features.
5. **Seams to prior specs:** the content-API client + **SQLDelight** local
   cache (03-api `sync`) are wired as **effects/middleware**; the store's
   state is cache-backed; the **M3 Expressive** UI (ADR 0009) and the
   **mikepenz markdown** renderer live in `ui`/`screen`, reading via
   selectors. Persistence/process-death via `compose-saveable` +
   `SaveableStateRegistry`.
6. **Agent-buildability (composes ADR 0012):** drop **`AGENTS.md`** at the
   client repo/module root; install **`.claude/skills/redux-kotlin/`**; the
   client's **test-green-before gate = `./gradlew build`** (compile + test +
   detekt + apiCheck) + `detektAll`; enforce `explicitApi()` + KDoc on public
   declarations. Agents follow the per-concern guides (feature-slice,
   store-setup, compose-binding, effects-sync, testing, platform-shims).

## Rationale

One solo maintainer ships every surface from a single codebase — the maximal-
leverage choice, and the operator's explicit aim. Redux's unidirectional flow
with middleware-isolated effects is deterministic, testable, and **structured
exactly the way agents build well** (pure reducers, explicit actions, slice
selectors, a gradle verify loop). State-keyed lifecycle aligns the framework
with our **deep-link wedge** (ADR 0006) for free. And redux-kotlin being
*agent-first* (AGENTS.md/skills/verify) compounds ADR 0012 — the client
becomes one of the most agent-operable parts of the system.

**Rejected:** per-platform native UI (SwiftUI + Compose + React separately) —
defeats the shared-UI directive and the solo-dev leverage; ad-hoc/MVI state
without redux — loses the agent-friendly conventions, devtools, and
time-travel/deep-link alignment.

## Consequences

Positive: one codebase for all surfaces; deterministic, testable, highly
agent-buildable; deep-link/time-travel native; clean seams to cache, API,
M3E, markdown.
Negative: **`1.0.0-alpha1` maturity risk** — pin the version, watch for
breaking changes, keep a fallback (the `0.6.x` stable line) noted; CMP Web is
Beta + iOS needs native glue (prior research) — web/desktop stay "enabled,
not prototype-blocking"; redux boilerplate (mitigated by the skill/codegen and
package-by-feature); Wasm/web bundle size to monitor.

## Revisit Trigger

`1.0.0-alpha1` proves unstable (pin to last-good or fall back to `0.6.x`); a
platform target (esp. Web/Wasm) underperforms for the UX; the maintainer base
grows enough that per-platform teams change the sharing calculus; or
redux-kotlin's agent tooling diverges from how we build.
