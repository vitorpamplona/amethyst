# Brainstorm: FeedScreen Relay Subscription Strategy

**Date:** 2026-03-09
**Status:** Investigation complete, solution needed
**Branch:** `feat/nip46-bunker-login`

## Problem Statement

Follows don't load for nostrconnect:// login. The root cause is a **chicken-and-egg problem** with how FeedScreen drives relay subscriptions — two competing approaches each solve one problem but break the other.

## What We Found

### The Two Approaches

| Approach | Drives relay connections? | Avoids subscription churn? |
|----------|--------------------------|---------------------------|
| `configuredRelays` (relayStatuses.keys) | YES — openReqSubscription connects on demand | NO — status map emits on every ping/connect/disconnect |
| `connectedRelays` (connectedRelaysFlow) | NO — subscriptions never open if nothing is connected | YES — only changes when relay actually connects/disconnects |

### How `configuredRelays` Works (Current Working Dir)

```kotlin
val relayStatusMap by relayManager.relayStatuses.collectAsState()
val configuredRelays = relayStatusMap.keys  // all relay URLs, connected or not

rememberSubscription(configuredRelays, account, feedMode, ...) {
    createContactListSubscription(relays = configuredRelays, ...)
}
```

- `addDefaultRelays()` populates `relayStatuses` map immediately at app startup
- `openReqSubscription()` (inside subscribe) connects to relays on demand
- Relays connect, REQs are sent, events arrive
- **Problem:** `relayStatusMap` emits a new Map on every relay status change (connect, ping, disconnect). Each emission triggers recomposition. `relayStatusMap.keys` creates a new Set instance. Even though `Set.equals()` is structural, `remember(*keys)` in Compose may see the new reference + trigger config lambda re-evaluation, generating a new subId via `generateSubId()` (uses `System.currentTimeMillis()`). DisposableEffect sees new subId -> closes old subscription -> opens new one. Contact list responses to old subId are dropped.

### How `connectedRelays` Works (Committed in e58aaf411)

```kotlin
val connectedRelays by relayManager.connectedRelays.collectAsState()

rememberSubscription(connectedRelays, account, feedMode, ...) {
    createContactListSubscription(relays = connectedRelays, ...)
}
```

- Only includes actually-connected relays
- Subscription only recreates when a relay actually connects/disconnects
- **Problem:** Chicken-and-egg. Nothing triggers relay connections in the first place. `addDefaultRelays()` only adds to the status map, NOT to the NostrClient. `relayManager.connect()` is a no-op when NostrClient has no registered relays. Relays only get registered when `openReqSubscription()` is called. But `openReqSubscription()` is never called because `connectedRelays` is empty, so the subscription lambda returns null.

### Why nsec/bunker Login Appeared to Work with `connectedRelays`

The `subscriptionsCoordinator.start()` in Main.kt kicks off a `rateLimiter.start` that calls `client.openReqSubscription()` for metadata. This incidentally registers relays with the NostrClient and triggers connections. Once relays connect, `connectedRelays` becomes non-empty, and FeedScreen subscriptions fire.

But this is fragile — it depends on the coordinator's metadata requests happening before FeedScreen needs relays. For nostrconnect (where login takes longer), the timing may differ.

## Architecture Context

### Relay Registration Flow
```
addDefaultRelays() -> relayStatuses map only (local state)
connect()          -> NostrClient.connect() (no-op if no relays registered)
subscribe()        -> openReqSubscription() -> registers relays + connects + sends REQ
```

Key insight: **Subscriptions are what trigger relay connections**, not `addDefaultRelays()` or `connect()`.

### Key Files
- `FeedScreen.kt` — all feed subscriptions, follows loading
- `SubscriptionUtils.kt` — `rememberSubscription()` with `remember(*keys)` + `DisposableEffect`
- `RelayConnectionManager.kt` — `relayStatuses` (Map), `connectedRelays` (Set), `subscribe()`
- `FeedSubscription.kt` — `createContactListSubscription()`, `generateSubId()` (uses timestamp)

### The Churn Mechanism (Detail)
```
1. relayStatusMap emits (relay A pings)
2. FeedScreen recomposes
3. configuredRelays = relayStatusMap.keys  (new Set instance)
4. remember(*keys) — keys include configuredRelays
5. IF keys changed: config() runs -> generateSubId("contacts-...") -> new subId
6. DisposableEffect sees new subId -> onDispose (close old sub) -> open new sub
7. Relay was responding to old subId -> response dropped
8. New sub sent -> relay starts processing -> another status change -> goto 1
```

The critical question: does `remember(*keys)` trigger re-evaluation when `configuredRelays` has same content but different reference? In Compose, `remember` uses `==` (structural equality). `Set.equals()` compares contents. So **if relay URLs haven't changed, remember should NOT recompute**.

But `relayStatusMap` itself changes (values change), causing recomposition. During recomposition, `configuredRelays = relayStatusMap.keys` runs. If the underlying Map implementation returns the same key set by reference, `remember` won't recompute. But if it returns a new Set (likely, since we do `.toMutableMap().apply{...}` in `updateRelayStatus`), then... `Set.equals()` should still return true.

**This needs empirical verification** — add logging in `rememberSubscription` to confirm whether subscriptions are actually being recreated.

## Candidate Solutions

### Approach A: Stabilize configuredRelays Key

Keep using `configuredRelays` to drive subscriptions (solves chicken-and-egg), but prevent churn by stabilizing the key:

```kotlin
// Derive relay URLs once, only update when URLs actually change
val configuredRelayUrls by remember {
    derivedStateOf {
        relayManager.relayStatuses.value.keys
    }
}
```

Or use `distinctUntilChanged()` on the Flow before collecting:

```kotlin
val configuredRelays by relayManager.relayStatuses
    .map { it.keys }
    .distinctUntilChanged()
    .collectAsState(emptySet())
```

**Pro:** Subscriptions drive relay connections AND keys are stable.
**Con:** Subscriptions still target unconnected relays (but openReqSubscription handles this).

### Approach B: Use configuredRelays for initial, connectedRelays after

Two-phase approach:
1. First subscription uses `configuredRelays` to trigger relay connections
2. Once relays connect, switch to `connectedRelays` for stability

```kotlin
val relaySet = if (connectedRelays.isEmpty()) configuredRelays else connectedRelays
```

**Pro:** Gets the best of both worlds.
**Con:** Subscription recreates once during the transition. More complex logic.

### Approach C: Separate relay connection from subscriptions

Add relay registration to `addDefaultRelays()` so `connect()` actually works:

```kotlin
fun addRelay(url: String): NormalizedRelayUrl? {
    val normalized = RelayUrlNormalizer.normalizeOrNull(url) ?: return null
    updateRelayStatus(normalized) { it.copy(connected = false) }
    _client.registerRelay(normalized)  // NEW: register with NostrClient
    return normalized
}
```

Then FeedScreen can safely use `connectedRelays` because relays will connect via `connect()` independently of subscriptions.

**Pro:** Clean separation. `connectedRelays` works correctly everywhere.
**Con:** Requires changes to NostrClient API (may not have `registerRelay`). Bigger change.

### Approach D: Empirically verify churn isn't happening

Add debug logging to `rememberSubscription` to confirm whether the contactList subscription is actually being recreated. If `Set.equals()` works correctly and `remember` doesn't recompute, then `configuredRelays` approach works fine and the follows issue has a different root cause.

```kotlin
@Composable
fun rememberSubscription(vararg keys: Any?, ...) {
    val subscription = remember(*keys) {
        DebugConfig.log("SUB RECOMPUTE keys=${keys.contentToString()}")
        config()
    }
    // ...
}
```

**Pro:** Cheapest path. May reveal the actual bug.
**Con:** Doesn't fix anything by itself.

## Recommendation

**Start with Approach D** (verify), then **Approach A** (stabilize) if churn is confirmed.

Approach A with `distinctUntilChanged()` is the cleanest fix — it preserves the property that subscriptions drive relay connections while eliminating any possible churn from status map updates.

## Decision: D then A

### D: Empirical Verification (implemented)

Added `SUB RECOMPUTE` log inside `remember(*keys)` in `rememberSubscription()`. Fires only when Compose re-evaluates the config lambda (i.e., keys actually changed). Compare frequency against existing `SUB OPEN`/`SUB CLOSE` logs.

**File:** `SubscriptionUtils.kt:73-78`

Run with `AMETHYST_DEBUG=true ./gradlew :desktopApp:run` and watch for:
- `SUB RECOMPUTE` spam = churn confirmed, A is needed
- `SUB RECOMPUTE` fires once per subscription = no churn, root cause is elsewhere

### A: Stabilize configuredRelays (implemented)

Replaced raw `relayStatusMap.keys` with a `distinctUntilChanged()` flow:

```kotlin
val configuredRelays by remember {
    relayManager.relayStatuses
        .map { it.keys }
        .distinctUntilChanged()
}.collectAsState(emptySet())
```

**File:** `FeedScreen.kt:163-167`

This ensures `configuredRelays` only emits when the set of relay URLs actually changes, not on every ping/status update. `openReqSubscription` still drives relay connections on demand.

## Open Questions

1. **If churn isn't the issue, what is?** Could be a timing issue where the contactList REQ arrives at the relay before the relay has the event cached, and no retry mechanism exists.

2. **Does `openReqSubscription` reliably deliver events for not-yet-connected relays?** It should queue and send REQ after connecting, but need to verify the NostrClient implementation.

3. **Should we add a retry/resubscribe mechanism?** If the first contactList subscription gets EOSE with no results, should we retry after a delay?

4. **Is the `since` filter on contactList needed?** ContactList events have no `since` filter currently — they request the latest kind-3 event. But if the relay is slow to index, the response might be empty.
