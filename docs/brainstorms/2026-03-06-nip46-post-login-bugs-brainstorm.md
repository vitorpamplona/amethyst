---
date: 2026-03-06
topic: nip46-post-login-bugs
---

# NIP-46 Post-Login Bugs: Content Not Loading + Bunker Timeout

## What's Happening

Two bugs on branch `feat/nip46-bunker-login` after the NIP-46 relay isolation work:

### Bug 1: `followedUsers=0` — Feed Never Populates After Login

**Symptoms:**
- Contact list subscription opens/closes rapidly (subscription churn)
- No `[DEBUG-FEED] contactList event:` log — no kind-3 event ever arrives
- Feed stays on "Loading followed users..." forever

**Logs:**
```
[DEBUG-FEED] contactList sub: relays=5, account=0b24845a, mode=FOLLOWING
[DEBUG-FEED] feedSub: mode=FOLLOWING, relays=5, followedUsers=0
[DEBUG-SUB] OPEN contacts-0b24845a-1772773857361 relays=5
[DEBUG-SUB] CLOSE contacts-0b24845a-1772773857361
[DEBUG-SUB] OPEN contacts-0b24845a-1772773867946 relays=5
[DEBUG-SUB] CLOSE contacts-0b24845a-1772773867946
```

### Bug 2: `bunker://` Login Times Out (30s)

**Symptoms:**
- Pasting bunker:// URI → "Connection timed out" after 30s
- `RemoteSignerManager.timeout = 30000` controls this

## Root Cause Analysis

### Bug 1: Subscription Churn from `relayStatuses.keys`

**The pattern** (`FeedScreen.kt:193`):
```kotlin
rememberSubscription(relayStatuses.keys, account, feedMode, ...) { ... }
```

**The problem:**
1. `relayStatuses` is a `StateFlow<Map<NormalizedRelayUrl, RelayStatus>>`
2. Every relay status change (connect, disconnect, ping) emits a new map
3. `.keys` produces a structurally-equal but reference-different set
4. `remember(*keys)` re-evaluates → `DisposableEffect` disposes old sub → opens new sub
5. New sub gets a fresh `subId` (uses `System.currentTimeMillis()`)
6. Relay's response to old REQ arrives → client drops it (unknown subId)
7. New REQ sent → relay starts processing → another status change → cycle repeats

**Timeline:**
```
T+0s:  Relay A connects → relayStatuses emits → sub opens (subId=1001)
T+1s:  Relay B connects → relayStatuses emits → sub CLOSES (subId=1001), opens (subId=1002)
T+2s:  Relay C connects → same cycle → CLOSE 1002, OPEN 1003
T+3s:  Relay responds to subId=1001 → DROPPED (sub already closed)
T+4s:  Relay responds to subId=1003 → maybe received, maybe closed again
```

**Contributing factor:** `relayStatuses.keys` includes configured-but-not-connected relays. REQs to unconnected relays are queued but may never be sent.

### Bug 2: NIP-46 Relay Connection Race

**The flow** (`AccountManager.loginWithBunker()`):
```kotlin
val nip46Client = getOrCreateNip46Client()  // creates NostrClient, calls connect() (no-op: no relays)
val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, signer, nip46Client)
remoteSigner.openSubscription()  // calls client.req(relays=[relay.nsec.app]) → lazy connect
val remotePubkey = remoteSigner.connect()  // sends connect request, waits 30s for response
```

**The problem:**
- `openSubscription()` triggers `sendOrConnectAndSync(relay.nsec.app, REQ)` — async connection
- `connect()` calls `launchWaitAndParse()` which sends EVENT immediately
- If websocket to relay.nsec.app isn't established yet, EVENT send fails/queues
- Even if EVENT sends, relay may not have processed REQ yet → response has no matching sub
- 30s timeout expires

**Also:** Only 1 relay (`wss://relay.nsec.app`) — single point of failure.

## Approaches

### Bug 1 Fix: Stabilize Subscription Keys

#### Approach A: Use `connectedRelays` + Debounce (Recommended)

Replace `relayStatuses.keys` with a debounced/stable relay set.

```kotlin
// Stable relay set that only updates when relay URLs actually change
val stableRelays by remember {
    snapshotFlow { connectedRelays }
        .distinctUntilChanged()
        .debounce(2000) // wait for relays to stabilize
}.collectAsState(initial = emptySet())

rememberSubscription(stableRelays, account, feedMode, ...) { ... }
```

**Pros:** Eliminates churn, only resubscribes when relay set actually changes
**Cons:** 2s delay before first subscription opens

#### Approach B: Separate Subscription Lifecycle from Relay Set

Don't use relay set as a recomposition key at all. Open subscription ONCE with whatever relays are available, never re-create it.

```kotlin
// Only recompose on account/feedMode changes, not relay changes
rememberSubscription(account, feedMode, relayManager = relayManager) {
    val relays = connectedRelays  // snapshot, not reactive
    if (relays.isNotEmpty() && account != null) {
        createContactListSubscription(relays = relays, ...)
    } else null
}
```

**Pros:** Zero churn, simplest fix
**Cons:** If initial relay set is empty, subscription never opens (need LaunchedEffect to wait for relays)

#### Approach C: LaunchedEffect + One-Shot Subscription

Don't use `rememberSubscription` for contact list at all. Use a `LaunchedEffect` that waits for relays then subscribes once.

```kotlin
LaunchedEffect(account) {
    val relays = relayManager.connectedRelays.first { it.isNotEmpty() }
    relayManager.subscribe("contacts-${account.pubKeyHex.take(8)}",
        listOf(FilterBuilders.contactList(account.pubKeyHex)), relays,
        listener = object : IRequestListener { ... })
}
```

**Pros:** No churn, explicit lifecycle, clear intent
**Cons:** Manual cleanup needed, doesn't auto-update if relay set changes later

### Bug 2 Fix: Wait for NIP-46 Relay Connection

#### Approach A: Wait for Connected Relay Before connect() (Recommended)

```kotlin
val nip46Client = getOrCreateNip46Client()
val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, signer, nip46Client)
remoteSigner.openSubscription()

// Wait for NIP-46 relay to actually connect before sending connect request
nip46Client.connectedRelaysFlow().first { relays ->
    remoteSigner.relays.any { it in relays }
}

val remotePubkey = remoteSigner.connect()
```

**Pros:** Guarantees relay is connected before handshake
**Cons:** Adds wait time; if relay never connects, blocks indefinitely (need timeout)

#### Approach B: Add Retry Logic to connect()

Wrap `connect()` in retry with backoff:
```kotlin
val remotePubkey = retry(maxAttempts = 3, delayMs = 5000) {
    remoteSigner.connect()
}
```

**Pros:** Handles transient failures
**Cons:** Doesn't fix root cause, just masks it; up to 90s total wait

#### Approach C: Increase Timeout + Add Debug Logging

Bump `RemoteSignerManager.timeout` to 60s, add logging to track relay connection state.

**Pros:** Quick, low-risk
**Cons:** Doesn't fix the race; just makes timeout less likely

## Key Decisions

- Bug 1 is the higher priority — it affects ALL logins, not just bunker
- Bug 1 Approach B or C preferred — simplest, eliminates churn entirely
- Bug 2 Approach A preferred — fixes the actual race condition
- Both fixes are independent and can be done in parallel

## Open Questions

1. Does the contact list fail for nsec/key login too, or only bunker? (Would confirm if it's relay churn vs NIP-46 specific)
2. Is `relay.nsec.app` reachable from this machine? (`websocat wss://relay.nsec.app` test)
3. Should the bunker URI's relays be used for content subscriptions too? (User's relays vs default relays)
4. Should we add a fallback relay for NIP-46? (e.g., `wss://relay.damus.io` alongside `relay.nsec.app`)
