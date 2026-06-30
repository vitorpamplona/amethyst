# Auth identity is connection scope, not policy state

> **Status:** shipped — `AuthScopedPolicy` is gone, `onAuthenticated` returns `Boolean`, and `onConnect`/policies read an injected connection scope exactly as the target model prescribes.
> _Audited 2026-06-30._

**Date:** 2026-06-04
**Module:** `quartz` — `nip01Core/relay/server`
**Status:** Proposed (review before implementing)

## Problem

`IRelayPolicy` is a *decision* interface (`accept(...)` → allow/reject/rewrite).
But we attached `authenticatedUsers` (the set of pubkeys logged in on a
connection) to the policy world via the `AuthScopedPolicy` mixin. That is
connection *state*, not a decision. Storing it in the policy forced three
artifacts whose only job is to route the state back out as scope:

- `AuthScopedPolicy` — marker to locate the state,
- `PolicyStack.authenticatedUsers` — union to forward it through the composition
  wrapper (the session's real policy is usually `PolicyStack(LimitsPolicy, …)`),
- `RequestContext`'s `as? AuthScopedPolicy` downcast — to read it back.

`RequestContext` is *already* the connection-scope object (`connectionId`,
`authenticatedUsers`). It should **own** the authenticated identity instead of
reaching into the policy for it.

## Why it got merged

The gating policies need the state to decide: `FullAuthPolicy.accept(ReqCmd)`
returns `auth-required` when no one is authenticated. Storing the set in the
policy kept `accept()` self-contained. The fix is to let the policy *read* a
scope it doesn't *own*.

## Target model

- **Connection scope** (engine-owned, per `RelaySession`) owns the mutable
  `authenticatedUsers` set + `connectionId`. `RequestContext` is its read-only
  view (what sources already receive).
- **Policy stays pure decision.** `FullAuthPolicy` keeps `accept(AuthCmd)`
  (validate the proof) and gating `accept(ReqCmd/CountCmd/EventCmd)`, but
  *reads* the scope to gate instead of owning a set.
- **The engine performs the single commit.** On a fully-approved AUTH, the
  engine records the verified pubkey into the scope — one commit point, not a
  field buried in a policy.

Deleted by this change: `AuthScopedPolicy`, `PolicyStack.authenticatedUsers`,
the `RequestContext` downcast, and `FullAuthPolicy`'s `authenticatedUsers` field
/ `isAuthenticated()` / the `.add()` in `onAuthenticated`.

## The two correctness invariants (must be preserved)

1. **Only a *verified* identity may enter the scope.** A blind-accept policy
   (`PassThroughPolicy`/`EmptyPolicy`, which accept `AUTH` without checking a
   challenge or signature) must record *nothing*. Today this holds because only
   `FullAuthPolicy.onAuthenticated` commits. We must not regress to "commit
   whenever `accept` passed."
2. **No partial/rolled-back auth.** A thrown `authorize`, or a downstream policy
   rejecting the AUTH, must leave the connection unauthenticated. Guarded by
   `authorizeThrowTurnsAuthIntoFailingOk`,
   `policyRejectingAuthAfterFullAuthLeavesConnectionUnauthenticated`,
   `failedReAuthKeepsPreviousValidAuthentication`,
   `commandsRejectedAfterFailedAuthHook`.

**Hard constraint:** `VerifyPolicy`/`VerifyAuthOnlyPolicy`/`EmptyPolicy` are
shared `object` singletons. They must remain stateless — a per-connection scope
ref may only be retained by a per-connection policy (built fresh by
`policyBuilder`, i.e. `FullAuthPolicy`).

## Mechanism

Two small signature changes carry it:

### A. Read side — inject the scope at connect

`IRelayPolicy.onConnect(send)` → `onConnect(scope: RequestContext, send)`.

- `PassThroughPolicy`, `VerifyEventsAndAuthPolicy` (singletons): ignore `scope`
  (no storage — stays stateless).
- `PolicyStack.onConnect`: forward `scope` to each child.
- `FullAuthPolicy`: store the read-only `scope` (per-connection, safe) and read
  `scope.authenticatedUsers` in its gating `accept(...)`.

### B. Write side — `onAuthenticated` returns the commit decision

`IRelayPolicy.onAuthenticated(pubKey, event)` → returns `Boolean`
(default `false` = "I did not authenticate anyone; record nothing").

- `FullAuthPolicy`: runs `authorize(...)` (may throw → engine catches → `OK
  false`), then `return true`. No `.add()`.
- `PolicyStack`: run **every** child (side effects) and OR the results, so a
  chain authenticates iff some verifying member claims it:
  `policies.fold(false) { acc, p -> p.onAuthenticated(pubKey, event) || acc }`
  (left operand always evaluated → every hook runs).
- Engine (`RelaySession.handleAuth`):
  ```
  val result = policy.accept(cmd)            // proof check across chain
  if (rejected) { OK false; return }
  val record = try { policy.onAuthenticated(cmd.event.pubKey, cmd.event) }
               catch (e) { OK false; return }  // authorize threw → nothing recorded
  if (record) scope.add(cmd.event.pubKey)      // single engine-side commit
  OK true
  ```

This keeps the write strictly engine-side (invariant 1: only `true`-returning
verifying policies cause a commit; PassThrough returns `false`) and the commit
after a no-throw `onAuthenticated` (invariant 2).

## File-by-file

| File | Change |
|------|--------|
| `backend/RequestContext.kt` | Drop the `as? AuthScopedPolicy` getter; `authenticatedUsers` becomes a plain backed property. Keep `policy` (for app-state downcast). |
| `policies/AuthScopedPolicy.kt` | **Delete.** |
| `policies/IRelayPolicy.kt` | `onConnect` gains `scope: RequestContext`; `onAuthenticated` returns `Boolean` (default `false`). |
| `policies/FullAuthPolicy.kt` | Remove `authenticatedUsers` field, `isAuthenticated()`, the marker. Store injected `scope`; gate on `scope.authenticatedUsers`; `onAuthenticated` runs `authorize` then `return true`. |
| `policies/PolicyStack.kt` | Drop `authenticatedUsers`/marker; forward `scope` in `onConnect`; OR-fold `onAuthenticated`. |
| `policies/PassThroughPolicy.kt`, `VerifyPolicy.kt` | `onConnect(scope, send)` — ignore `scope` (stay stateless). |
| `server/RelaySession.kt` | Own the mutable `authenticatedUsers` set behind `requestContext`; expose `requestContext` (read view) for observability/tests; pass it to `policy.onConnect`; perform the commit in `handleAuth`. |

No change to `EventSource`/`SessionBackend`/`EventSourceBackend`/`LiveEventStore`
— sources still get `RequestContext` and read `authenticatedUsers` exactly as
now; only the *backing* moved.

## Test mapping (assertions retarget; guarantees unchanged)

`NostrServerAuthTest` currently inspects `(session.policy as FullAuthPolicy)
.authenticatedUsers / .isAuthenticated()`. Retarget to the scope, e.g.
`session.requestContext.authenticatedUsers` (expose `requestContext` on
`RelaySession`).

| Test | New path / why it still holds |
|------|-------------------------------|
| `authSucceedsWithValidEvent` | accept ✓ → `onAuthenticated`→true → engine commits → `requestContext.authenticatedUsers` has pubkey. |
| `multipleUsersCanAuthenticate` | two successful AUTHs → engine commits each → set has both. |
| `authorizeThrowTurnsAuthIntoFailingOk` | `authorize` throws → engine catches before commit → set empty. |
| `policyRejectingAuthAfterFullAuthLeavesConnectionUnauthenticated` | chain `accept` rejected → `onAuthenticated` never called → no commit. |
| `failedReAuthKeepsPreviousValidAuthentication` | 2nd accept rejected → no commit → set keeps 1st pubkey. |
| `commandsRejectedAfterFailedAuthHook` | unchanged (gating reads empty scope). |
| `EventSourceServerTest.sourceSeesAuthenticatedUserInContext` | unchanged — already reads `ctx.authenticatedUsers`. |

## Alternatives considered

- **Scope exposes `add()`, verifying policy writes it** (no `onAuthenticated`
  signature change). Rejected: gives policies write access to scope and splits
  the commit across N policies; the engine-side single commit is easier to audit
  against invariant 2.
- **Pass `scope` into every `accept(cmd, scope)`** instead of injecting at
  `onConnect`. Rejected: ~4 methods × ~9 policies of churn for a dependency only
  `FullAuthPolicy` uses.
- **Generic `EventSource<C : RequestContext>`** for a typed `AuthRequestContext`
  (prior discussion). Out of scope; cascades type params through the shared
  `SessionBackend`/storage path.

## Risk

Low surface, high-sensitivity (signed-in correctness). The four bypass tests are
the spec; they pass unchanged in *behavior*, only their assertion target moves.
The `onConnect`/`onAuthenticated` signature changes are mechanical but touch
every policy + any external `IRelayPolicy` impl (source-breaking — acceptable
for this in-tree 2025 SPI, same call as the `EventSource` ctx change).
