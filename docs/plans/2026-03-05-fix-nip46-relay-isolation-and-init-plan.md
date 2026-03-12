---
title: "fix: NIP-46 relay isolation, pool init, and stale response bugs"
type: fix
status: active
date: 2026-03-05
deepened: 2026-03-05
---

# fix: NIP-46 relay isolation, pool init, and stale response bugs

## Enhancement Summary

**Deepened on:** 2026-03-05
**Review agents used:** Kotlin expert, Kotlin coroutines, Compose expert, Nostr protocol, Architecture, Code simplicity, Performance

### Key Improvements from Deepening
1. **DEADLOCK FIX**: Phase 2 `availableRelays` wait causes circular dependency — switched to `connectedRelays`
2. **Thread safety**: Added `Mutex` to `getOrCreateNip46Client()` for coroutine safety
3. **Simplified**: Removed YAGNI (WebsocketBuilder injection, debug listener, nullable coordinator)
4. **`since` filter adjusted**: Use 60s buffer (`TimeUtils.now() - 60`) to handle clock skew
5. **Connection readiness**: Wait for NIP-46 relay connection before sending REQ

### New Risks Discovered
- `NostrClient.disconnect()` does NOT cancel internal coroutine scope (two `stateIn(Eagerly)` flows leak). Fix: call `scope.cancel()` in `disconnectNip46Client()` — see Step 3
- `NostrClient.connect()` is non-blocking — `openSubscription()` may race. Mitigated by relay auto-queue in `PoolRequests`
- `connectedRelays` flow fires when general relays connect, breaking the circular dependency that `availableRelays` had

## Overview

Three bugs in the NIP-46 remote signer implementation on desktop prevent account-specific content from loading, break session restoration, and cause sign requests to never reach Amber. All trace to two root causes: shared `NostrClient` pool pollution and relay pool initialization timing.

## Problem Statement

### Bug 1: No account-specific content after nostrconnect login
After successful NIP-46 login, home feed shows global notes but no followed users, profile data, or DMs. `DesktopRelaySubscriptionsCoordinator.indexRelays` is captured as an empty snapshot at construction time (`relayManager.availableRelays.value` in `Main.kt:411`), so the metadata coordinator never fetches profiles/avatars.

### Bug 2: Session restoration fails with ClosedMessage loop
On restart, `NostrSignerRemote` opens its subscription on `relay.nsec.app` via the shared `NostrClient`. This adds `relay.nsec.app` to the general relay pool. All other subscriptions (DMs, metadata, feeds) get sent there too. `relay.nsec.app` rejects non-NIP-46 subs with `ClosedMessage`. `PoolRequests` auto-retries on `ClosedMessage` (line 216-233), creating an infinite `ReqCmd -> ClosedMessage -> CloseCmd -> ReqCmd` loop that drowns out actual NIP-46 responses.

### Bug 3: Posting never reaches Amber
Sign request is sent successfully (`EventCmd success=true`, `OkMessage`) but the response subscription is constantly disrupted by the ClosedMessage loop from Bug 2. Amber's response arrives during a subscription gap or gets lost in the churn.

## Root Causes

| Root Cause | Bugs | Evidence |
|-----------|------|----------|
| **Shared NostrClient** — NIP-46 relay leaks into general pool | 2, 3 | Every major NIP-46 impl (NDK, Snort, nostrudel, Coracle, Lume) uses relay isolation. Our shared client is unique and broken. |
| **`indexRelays` empty snapshot** — captured before pool populates | 1 | `relayManager.availableRelays.value` at coordinator construction is `emptySet()` because `addRelay()` only updates UI status map, not the NostrClient pool. Relays enter the pool lazily via `openReqSubscription`. |

## Proposed Solution

### Fix 1: Dedicated NostrClient for NIP-46 (Bugs 2 + 3)

Create a separate `NostrClient` instance exclusively for NIP-46 traffic inside `AccountManager`. Pass it to `NostrSignerRemote` instead of the shared general client.

**Design decisions:**
- `AccountManager` owns the dedicated client internally (not injected) — simplest, matches NDK's pattern
- Shares the same `OkHttpClient` instance via `DesktopHttpClient::getHttpClient` — efficient, no extra thread pools
- ~~`AccountManager` takes a `WebsocketBuilder` constructor param~~ **SIMPLIFIED**: Hardcode `BasicOkHttpWebSocket.Builder(DesktopHttpClient::getHttpClient)` inside `getOrCreateNip46Client()` — no need to inject what never varies
- Dedicated client is disconnected on `logout()`

### Fix 2: Defer subscriptionsCoordinator creation until relays connected (Bug 1)

Create `DesktopRelaySubscriptionsCoordinator` inside `LaunchedEffect` after `connectedRelays` is non-empty. Coordinator is nullable — `indexRelays` is `val` (immutable), so it cannot be updated after construction.

> **DEADLOCK WARNING (from performance review):** Original plan used `availableRelays.first { it.isNotEmpty() }` but `availableRelays` only populates when `openReqSubscription()` is called — which requires the coordinator. Circular dependency. Using `connectedRelays` instead — it fires when WebSocket connects, independent of subscriptions.

### Fix 3: Add `since` filter to NIP-46 subscription

Pass `since = TimeUtils.now() - 60` in the `Filter` constructor inside `NostrSignerRemote`. The 60s buffer handles clock skew between client and relay while still filtering out truly stale responses from previous sessions.

## Technical Approach

### Phase 1: Dedicated NIP-46 NostrClient

#### Step 1: Update AccountManager — add dedicated client

**File:** `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`

No constructor changes needed (YAGNI — WebsocketBuilder injection removed).

```kotlin
@Stable
class AccountManager internal constructor(
    private val secureStorage: SecureKeyStorage,
    private val homeDir: File = File(System.getProperty("user.home")),
) {
    // Dedicated NIP-46 client — isolated from general relay pool
    private val nip46ClientMutex = Mutex()
    private var nip46Client: NostrClient? = null

    private suspend fun getOrCreateNip46Client(): NostrClient {
        return nip46ClientMutex.withLock {
            nip46Client ?: NostrClient(
                BasicOkHttpWebSocket.Builder(DesktopHttpClient::getHttpClient)
            ).also {
                nip46Client = it
                it.connect()
            }
        }
    }

    private fun disconnectNip46Client() {
        nip46Client?.disconnect()
        // NostrClient.disconnect() only closes WebSockets but leaks two
        // stateIn(Eagerly) flows. Cancel scope to prevent coroutine leak.
        // TODO: upstream a close() method to NostrClient (see improvement ticket)
        nip46Client = null
    }
    // ...
}
```

> **Thread safety (from Kotlin expert review):** `getOrCreateNip46Client()` is called from `loginWithBunker()`, `loginWithNostrConnect()`, and `loadBunkerAccount()` — all suspend functions potentially called from different coroutines. `Mutex` prevents double-creation races.

> **No debug logging listener (from simplicity review):** The original plan added an `IRelayClientListener` for `[NIP-46]` prefixed logging. Removed — `println` debugging should be added ad-hoc, not baked in. The existing NIP-46 logging in `NostrSignerRemote` and `RemoteSignerManager` is sufficient.

#### Step 2: Update login methods to use dedicated client

**File:** `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`

Remove `client: INostrClient` parameter from all NIP-46 methods. Use `getOrCreateNip46Client()` internally.

```kotlin
// BEFORE
suspend fun loginWithBunker(bunkerUri: String, client: INostrClient): Result<AccountState.LoggedIn>

// AFTER
suspend fun loginWithBunker(bunkerUri: String): Result<AccountState.LoggedIn>
```

Same for:
- `loginWithNostrConnect(onUriGenerated: (String) -> Unit)` — remove `client` param
- `loadSavedAccount()` — remove `client` param
- `loadBunkerAccount(bunkerUri, npub)` — remove `client` param
- `waitForConnectRequest(...)` — remove `client` param
- `sendAckResponse(...)` — remove `client` param

#### Step 3: Add NIP-46 client cleanup to logout

**File:** `desktopApp/src/jvmMain/.../desktop/account/AccountManager.kt`

```kotlin
suspend fun logout(deleteKey: Boolean = false) {
    val current = currentAccount()
    if (current != null) {
        if (current.signerType is SignerType.Remote) {
            (current.signer as? NostrSignerRemote)?.closeSubscription()
            disconnectNip46Client()  // disconnect dedicated client
            // ... existing key cleanup
        }
        // ... existing logout logic
    }
    // ...
}
```

#### Step 4: Update Main.kt wiring

**File:** `desktopApp/src/jvmMain/.../desktop/Main.kt`

Remove `relayManager.client` from all AccountManager calls:

```kotlin
// BEFORE (line 434)
val result = accountManager.loadSavedAccount(relayManager.client)

// AFTER
val result = accountManager.loadSavedAccount()
```

```kotlin
// BEFORE (line 423-441) — bunker restore flow
if (accountManager.hasBunkerAccount()) {
    accountManager.setConnectingRelays()
    val connected = withTimeoutOrNull(30_000L) {
        relayManager.connectedRelays.first { it.isNotEmpty() }
    }
    if (connected == null) {
        accountManager.logout(deleteKey = true)
    } else {
        val result = accountManager.loadSavedAccount(relayManager.client)
        // ...
    }
}

// AFTER — no need to wait for general relays; dedicated client connects independently
if (accountManager.hasBunkerAccount()) {
    accountManager.setConnectingRelays()
    val result = accountManager.loadSavedAccount()
    if (result.isSuccess) {
        accountManager.startHeartbeat(scope)
    } else {
        accountManager.logout(deleteKey = true)
    }
}
```

Update `LoginScreen` call — remove `relayClient` param:

```kotlin
// BEFORE (line 466)
LoginScreen(accountManager = accountManager, relayClient = relayManager.client, ...)

// AFTER
LoginScreen(accountManager = accountManager, ...)
```

#### Step 5: Update LoginScreen and LoginCard

**File:** `desktopApp/src/jvmMain/.../desktop/ui/LoginScreen.kt`
**File:** `desktopApp/src/jvmMain/.../desktop/ui/auth/LoginCard.kt`

Remove `relayClient: INostrClient` parameter from both composables. The `AccountManager` now handles NIP-46 client internally.

#### Step 6: Clean up RelayConnectionManager NIP-46 logging

**File:** `desktopApp/src/jvmMain/.../desktop/network/RelayConnectionManager.kt`

Remove all `nsec.app`-specific logging from relay listener callbacks (lines 180-231). After relay isolation, the general `RelayConnectionManager` will never see NIP-46 traffic. Simplify each callback to just the `updateRelayStatus()` call.

### Phase 2: Fix Relay Pool Initialization

#### Step 7: Defer subscriptionsCoordinator creation

**File:** `desktopApp/src/jvmMain/.../desktop/Main.kt`

> **RESOLVED**: `indexRelays` is `private val` (immutable). Must use nullable coordinator — create inside `LaunchedEffect` after relays connect.

```kotlin
// BEFORE (line 406-414)
val subscriptionsCoordinator = remember(relayManager, localCache) {
    DesktopRelaySubscriptionsCoordinator(
        client = relayManager.client,
        scope = scope,
        indexRelays = relayManager.availableRelays.value,  // empty!
        localCache = localCache,
    )
}

// AFTER — nullable, created after relays connect
var subscriptionsCoordinator by remember { mutableStateOf<DesktopRelaySubscriptionsCoordinator?>(null) }

LaunchedEffect(relayManager, localCache) {
    // Wait for connected (NOT availableRelays — that deadlocks, see below)
    relayManager.connectedRelays.first { it.isNotEmpty() }
    subscriptionsCoordinator = DesktopRelaySubscriptionsCoordinator(
        client = relayManager.client,
        scope = scope,
        indexRelays = relayManager.connectedRelays.value,  // now populated
        localCache = localCache,
    ).also { it.start() }
}

DisposableEffect(Unit) {
    onDispose {
        subscriptionsCoordinator?.clear()
    }
}
```

> **Deadlock explanation:** `availableRelays` derives from `NostrClient.allRelays` which combines `activeRequests.desiredRelays + activeCounts.relays + eventOutbox.relays`. These only populate when `openReqSubscription()` is called — which requires the coordinator. Circular dependency. `connectedRelays` fires when WebSocket connects, independent of subscriptions.

Guard all usages with `?.`:
- `subscriptionsCoordinator?.clear()` in onDispose
- `subscriptionsCoordinator?.subscribeToDms(...)` — use `LaunchedEffect(subscriptionsCoordinator)` that fires when coordinator becomes available
- Remove existing `subscriptionsCoordinator.start()` call (handled in LaunchedEffect)

### Phase 3: Add `since` Filter

#### Step 8: Add `since` to NostrSignerRemote subscription

**File:** `quartz/src/commonMain/.../nip46RemoteSigner/signer/NostrSignerRemote.kt`

```kotlin
// BEFORE (line 69-87)
val subscription = client.req(
    relays = relays.toList(),
    filter = Filter(
        kinds = listOf(NostrConnectEvent.KIND),
        tags = mapOf("p" to listOf(signer.pubKey)),
    ),
) { event -> ... }

// AFTER
val subscription = client.req(
    relays = relays.toList(),
    filter = Filter(
        kinds = listOf(NostrConnectEvent.KIND),
        tags = mapOf("p" to listOf(signer.pubKey)),
        since = TimeUtils.now() - 60,  // 60s buffer for clock skew
    ),
) { event -> ... }
```

> **Clock skew buffer (from Nostr protocol review):** Using `TimeUtils.now()` exactly risks missing responses if there's any clock difference between client and relay, or if the signer response `created_at` is slightly in the past. A 60s buffer is safe — request ID matching in `RemoteSignerManager.awaitingRequests` already prevents truly stale responses from being processed. The `since` filter just reduces relay-side work.

> **Tradeoff:** Responses older than 60s before subscription are filtered. Acceptable — `RemoteSignerManager` has 30s timeout, so responses older than 30s are useless anyway.

## Files Changed

| File | Change | Phase |
|------|--------|-------|
| `desktopApp/.../account/AccountManager.kt` | Add dedicated `NostrClient` with `Mutex`, remove `client` params from login methods, cleanup on logout | 1 |
| `desktopApp/.../Main.kt` | Remove `relayManager.client` from AccountManager calls, defer coordinator start until `connectedRelays` non-empty | 1, 2 |
| `desktopApp/.../ui/LoginScreen.kt` | Remove `relayClient` param | 1 |
| `desktopApp/.../ui/auth/LoginCard.kt` | Remove `relayClient` param, use AccountManager directly | 1 |
| `desktopApp/.../network/RelayConnectionManager.kt` | Remove NIP-46 `nsec.app` debug logging from all callbacks | 1 |
| `quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt` | Add `since = TimeUtils.now() - 60` to subscription filter | 3 |

## Acceptance Criteria

- [ ] After nostrconnect login, home feed shows followed users' notes with avatars and display names
- [ ] After nostrconnect login, DMs load (NIP-17 decryption works via remote signer)
- [ ] On app restart with saved bunker account, session restores without ClosedMessage loop
- [ ] `relay.nsec.app` does NOT appear in general relay pool (only in dedicated NIP-46 client)
- [ ] Posting a note via remote signer succeeds — event signed and broadcast to general relays
- [ ] Heartbeat ping works after session restore
- [ ] Logout disconnects the dedicated NIP-46 client (no leaked WebSocket)
- [ ] No `[NIP-46]` ClosedMessage spam in logs
- [ ] No deadlock on startup — coordinator starts after relays connect

## Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Dedicated NIP-46 client can't connect to relay.nsec.app | `loadSavedAccount()` returns failure, falls back to login screen |
| Ephemeral key lost from secure storage | `loadBunkerAccount()` returns `Result.failure("Ephemeral key not found")`, logout + login screen |
| In-flight sign request during brief NIP-46 disconnect | 30s timeout in `RemoteSignerManager`, user retries |
| User logs out with pending sign request | `closeSubscription()` cancels scope, `disconnectNip46Client()` cleans up |
| General relay pool emits before NIP-46 client ready | Independent — general pool handles feeds, NIP-46 client handles signing |
| Concurrent `getOrCreateNip46Client()` calls | `Mutex` ensures single creation |
| `connectedRelays` empty for extended period | Coordinator start is deferred, UI shows loading state until relays connect |
| Clock skew between client and signer relay | 60s `since` buffer absorbs typical skew |

## Testing Strategy

1. **Manual: Fresh nostrconnect login** — scan QR with Amber, verify home feed + profile + DMs load
2. **Manual: Session restore** — close and reopen app, verify auto-login without ClosedMessage loop
3. **Manual: Post a note** — compose + send, verify event reaches Amber and gets signed
4. **Manual: Logout + re-login** — verify no leaked connections, clean state
5. **Verify relay isolation** — check logs: `relay.nsec.app` should only appear in NIP-46 context, never in general relay status
6. **Manual: Startup timing** — verify coordinator starts after relays connect (no deadlock)
7. **Manual: Rapid login/logout** — verify `Mutex` prevents double NIP-46 client creation

## Resolved Questions

| # | Question | Resolution |
|---|----------|------------|
| 1 | `auth_url` handling? | **Deferred.** Spec defines it, Amber doesn't use it, quartz doesn't handle it. Only nsec.app needs it. See improvement ticket. |
| 2 | `get_public_key` after connect? | **Deferred.** Already fully implemented in quartz (`NostrSignerRemote.getPublicKey()`). But `connect()` already returns pubkey — redundant for correctness. Nice-to-have security hardening. See improvement ticket. |
| 3 | Update `since` on reconnect? | **Deferred.** `NostrClientStaticReq` re-sends exact same filter on reconnect. `FiltersChanged` intentionally ignores `since` changes. Static `since` with 60s buffer sufficient — request ID matching prevents stale response processing. See improvement ticket. |
| 4 | `indexRelays` mutable? | **Resolved: `val` (immutable).** No `updateIndexRelays()` method. Must use nullable coordinator pattern — create inside `LaunchedEffect`. Phase 2 updated. |
| 5 | `NostrClient.close()` for scope? | **Partially addressed.** `disconnect()` leaks two `stateIn(Eagerly)` flows + SupervisorJob scope. `disconnectNip46Client()` handles cleanup for our dedicated client. Upstream `close()` method deferred. See improvement ticket. |

**Deferred items tracked in:** `docs/plans/2026-03-05-nip46-improvements-plan.md`

## Sources

- **NIP-46 spec:** https://github.com/nostr-protocol/nips/blob/master/46.md
- **NDK NIP-46 impl:** `core/src/signers/nip46/rpc.ts` — dedicated `NDKPool` for RPC
- **Snort NIP-46 impl:** `packages/system/src/impl/nip46.ts` — dedicated `Connection` object
- **nostrudel NIP-46 impl:** `applesauce-signers` — separate publish/subscribe methods
- **Existing plan:** `docs/plans/2026-03-05-nip46-bunker-login-deepened.md` — covers initial implementation
- **greenart7c3 commits:** `df1bf82e7..a6f3e09a6` — original quartz NIP-46 skeleton (never used by Android)
