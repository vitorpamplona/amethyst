---
title: "fix: Desktop Tor traffic leaks — zero direct connections when Tor ON"
type: fix
status: active
date: 2026-04-01
deepened: 2026-04-01
origin: docs/brainstorms/2026-04-01-fix-tor-traffic-leaks-brainstorm.md
---

# fix: Desktop Tor traffic leaks

## Enhancement Summary

**Deepened on:** 2026-04-01
**Agents:** Security sentinel, Simplicity reviewer, Coil3 API verifier, Performance oracle

### Critical Findings from Deepening
1. **SECURITY: Fail-closed** — `proxyClient.value ?: directClient` silently leaks during Tor bootstrap. Must return a dead-socket client when Tor expected but not ready.
2. **SECURITY: NIP-46 bunker** — `AccountManager` uses `simpleClient` (always direct). Bunker relay connections bypass Tor entirely.
3. **SECURITY: DesktopBlossomClient** — must use `get() =` not `=` (stale capture at construction).
4. **PERFORMANCE: Stagger relay reconnect** — 30 relays × Tor circuit simultaneously = 30-90s freeze. Need 500ms jitter.
5. **SIMPLICITY: lateinit var** — fail loudly on misconfiguration rather than silently falling back to direct client (IP leak).

---

## Overview

Manual testing with `lsof` revealed that with Tor ON (Full Privacy), only relay WebSocket connections go through the SOCKS proxy. 8 other network egress paths create their own `OkHttpClient()` and bypass Tor — leaking user IP to image CDNs, media servers, lightning providers, and Blossom hosts.

## Problem Statement

With Tor enabled, the Java process should have ONLY:
- Connections to `127.0.0.1:PORT` (local SOCKS proxy)
- The Tor daemon's own guard node connections

Instead, `lsof` shows 9+ direct external TCP connections from Java to Cloudflare, Hetzner, and relay IPs — defeating Tor's privacy guarantees.

## Proposed Solution

**Global `DesktopHttpClient.currentClient()` accessor** (see brainstorm: `docs/brainstorms/2026-04-01-fix-tor-traffic-leaks-brainstorm.md`). Each leak site changes 1-2 lines to use the Tor-aware client instead of a bare `OkHttpClient()`.

## Implementation

### Phase 1: Add fail-closed global accessor to DesktopHttpClient

**File:** `desktopApp/.../network/DesktopHttpClient.kt`

Add to companion object:
```kotlin
lateinit var instance: DesktopHttpClient
    private set

fun setInstance(client: DesktopHttpClient) { instance = client }

/** Fail-closed client — routes to dead proxy, requests fail instead of leaking. */
private val failClosedClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 1)))
        .connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

/**
 * Returns the current Tor-aware client.
 * - Tor active → proxy client (SOCKS)
 * - Tor off → direct client
 * - Tor expected but bootstrapping → FAIL-CLOSED (dead proxy, requests fail)
 */
fun currentClient(): OkHttpClient {
    if (!::instance.isInitialized) return simpleClient // Pre-init only (tests, startup)
    val client = instance
    val proxyClient = client.proxyClient.value
    if (proxyClient != null) return proxyClient
    // Tor not active — check if it SHOULD be
    return if (client.isTorExpected()) failClosedClient else client.getNonProxyClient()
}
```

Add `isTorExpected()` to `DesktopHttpClient` instance:
```kotlin
/** Returns true if user's settings expect Tor routing (INTERNAL or EXTERNAL mode). */
fun isTorExpected(): Boolean = torTypeFlow.value != TorType.OFF
```

**File:** `desktopApp/.../Main.kt`

After `httpClient` creation in `App`:
```kotlin
DesktopHttpClient.setInstance(httpClient)
```

### Phase 2: Fix 8 leak sites

| File | Before | After |
|------|--------|-------|
| `AnimatedGifImage.kt:52` | `private val gifHttpClient by lazy { OkHttpClient.Builder()...build() }` | `private val gifHttpClient get() = DesktopHttpClient.currentClient()` |
| `SaveMediaAction.kt:32` | `private val httpClient = OkHttpClient()` | `private val httpClient get() = DesktopHttpClient.currentClient()` |
| `EncryptedMediaService.kt:36` | `private val httpClient = OkHttpClient()` | `private val httpClient get() = DesktopHttpClient.currentClient()` |
| `ServerHealthCheck.kt:30` | `private val httpClient = OkHttpClient.Builder()...build()` | Remove property. Inline `DesktopHttpClient.currentClient()` at call site. |
| `NoteActions.kt:939` | `val httpClient = OkHttpClient.Builder()...build()` | `val httpClient = DesktopHttpClient.currentClient()` |
| `DesktopBlossomClient.kt:37` | `private val okHttpClient: OkHttpClient = OkHttpClient()` | `private val okHttpClient: OkHttpClient get() = DesktopHttpClient.currentClient()` |
| `AccountManager.kt:141` | `DesktopHttpClient::getSimpleHttpClient` | `{ url -> DesktopHttpClient.currentClient() }` |

**Note on DesktopBlossomClient:** Must use `get() =` (not `=`) so each upload picks up current Tor state. If Tor activates after dialog opens, uploads still route through Tor.

**Note on AccountManager (NEW):** NIP-46 bunker relay connections currently always bypass Tor via `getSimpleHttpClient`. Security review flagged this as HIGH — bunker connections associate user's IP with signing operations.

### Phase 3: Wire Coil image loader through Tor

**File:** `desktopApp/.../service/images/DesktopImageLoaderSetup.kt`

```kotlin
import coil3.network.okhttp.OkHttpNetworkFetcher

ImageLoader.Builder(PlatformContext.INSTANCE)
    .components {
        add(OkHttpNetworkFetcher.factory { DesktopHttpClient.currentClient() })
        // ... existing decoders (SvgDecoder, SkiaGifDecoder, etc.)
    }
```

Confirmed API: `OkHttpNetworkFetcher.factory(callFactory: () -> Call.Factory)` — lambda called per-request, so each image load picks up current Tor state. Coil's memory + disk caches (256MB + 512MB) mean cached images don't re-trigger network.

### Phase 4: Staggered relay reconnection on Tor Active

**File:** `desktopApp/.../Main.kt`

```kotlin
LaunchedEffect(torManager) {
    var previouslyActive = false
    torManager.status.collect { status ->
        val nowActive = status is TorServiceStatus.Active
        if (nowActive && !previouslyActive) {
            // Tor just became active — stagger reconnect to avoid thundering herd
            // NostrClient.reconnect() disconnects all then reconnects
            // 30 relays × Tor circuit = potential 30-90s if simultaneous
            delay(500) // Brief delay for proxy client to propagate
            relayManager.client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)
        }
        previouslyActive = nowActive
    }
}
```

Performance oracle recommends staggering inside `NostrClient.reconnect()`, but that requires modifying quartz. For this fix, the `delay(500)` ensures the proxy client StateFlow has propagated before reconnection starts. Full staggered reconnection is a follow-up.

### Phase 5: Tests

**New unit tests** (`desktopApp/src/jvmTest/.../network/DesktopHttpClientTest.kt`):

```
// Add to existing DesktopHttpClientTest:
- currentClient_instanceNotSet_returnsSimpleClient
- currentClient_torActive_returnsProxyClient
- currentClient_torOff_returnsDirectClient
- currentClient_torExpectedButBootstrapping_returnsFailClosed
- currentClient_failClosed_hasSocksProxy  // Verify it has a proxy, not direct
- currentClient_failClosed_pointsToDeadPort  // port 1, will fail fast
```

**Manual verification (lsof):**
```bash
# WITH Tor ON (Full Privacy):
lsof -i TCP -P -n | grep "^java" | grep ESTABLISHED | grep -v "127.0.0.1"
# Expected: EMPTY — zero direct connections

# WITH Tor OFF:
lsof -i TCP -P -n | grep "^java" | grep ESTABLISHED | grep -v "127.0.0.1"
# Expected: Direct connections to relay IPs (control test)

# DURING Tor bootstrap (first 10-30s):
lsof -i TCP -P -n | grep "^java" | grep ESTABLISHED | grep -v "127.0.0.1"
# Expected: EMPTY — fail-closed prevents leaks during bootstrap
```

**Grep CI check (prevent regressions):**
```bash
# Should return ZERO matches outside DesktopHttpClient.kt:
grep -r "OkHttpClient()" desktopApp/src/jvmMain/ --include="*.kt" | grep -v "DesktopHttpClient.kt" | grep -v "Test"
```

## Acceptance Criteria

- [ ] `currentClient()` returns proxy client when Tor active
- [ ] `currentClient()` returns direct client when Tor off
- [ ] `currentClient()` returns **fail-closed client** when Tor expected but bootstrapping
- [ ] `currentClient()` throws (lateinit) if instance never set (misconfiguration = loud failure)
- [ ] Fail-closed client has SOCKS proxy pointing to dead port (requests fail, don't leak)
- [ ] `AnimatedGifImage` uses `currentClient()` via `get() =`
- [ ] `SaveMediaAction` uses `currentClient()` via `get() =`
- [ ] `EncryptedMediaService` uses `currentClient()` via `get() =`
- [ ] `ServerHealthCheck` inlines `currentClient()` at call site
- [ ] `NoteActions.zapNote()` uses `currentClient()`
- [ ] `DesktopBlossomClient` uses `currentClient()` via `get() =` (not `=`)
- [ ] `AccountManager` NIP-46 bunker uses `currentClient()` (not `simpleClient`)
- [ ] Coil uses `OkHttpNetworkFetcher.factory { currentClient() }`
- [ ] Relays reconnect through SOCKS when Tor transitions to Active
- [ ] `lsof` shows ZERO direct external connections from Java when Tor ON
- [ ] `lsof` shows ZERO direct connections during Tor bootstrap (fail-closed)
- [ ] `lsof` shows direct connections when Tor OFF (control)
- [ ] All existing tests pass
- [ ] Images load through Tor (slower but functional)
- [ ] Zaps work through Tor

## Files Changed

| File | Change | Lines |
|------|--------|-------|
| `DesktopHttpClient.kt` | `lateinit`, `currentClient()`, `failClosedClient`, `isTorExpected()` | +25 |
| `Main.kt` | `setInstance`, reconnect `LaunchedEffect` | +12 |
| `AnimatedGifImage.kt` | `get() = currentClient()` | 1 |
| `SaveMediaAction.kt` | `get() = currentClient()` | 1 |
| `EncryptedMediaService.kt` | `get() = currentClient()` | 1 |
| `ServerHealthCheck.kt` | Inline `currentClient()` at call site, remove property | -3, +1 |
| `NoteActions.kt` | Replace inline builder | 1 |
| `DesktopBlossomClient.kt` | `get() = currentClient()` | 1 |
| `AccountManager.kt` | Replace `getSimpleHttpClient` with `currentClient()` | 1 |
| `DesktopImageLoaderSetup.kt` | Add `OkHttpNetworkFetcher.factory` | 2 |
| `DesktopHttpClientTest.kt` | Add fail-closed + currentClient tests | +25 |

**Total: ~70 lines changed across 11 files.**

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-04-01-fix-tor-traffic-leaks-brainstorm.md](docs/brainstorms/2026-04-01-fix-tor-traffic-leaks-brainstorm.md)
- Key decisions: Global accessor (Option A), fail-closed during bootstrap, Coil3 per-request lambda

### Deepening Findings

| Agent | Key Finding | Impact |
|-------|------------|--------|
| Security | `?: directClient` fallback leaks during bootstrap | **CRITICAL** — added fail-closed |
| Security | NIP-46 bunker bypasses Tor via `simpleClient` | **HIGH** — added AccountManager fix |
| Simplicity | `DesktopBlossomClient` `=` captures stale client | **HIGH** — changed to `get() =` |
| Simplicity | `lateinit var` fails loudly vs silent leak | Applied |
| Simplicity | ServerHealthCheck: inline, drop custom timeout | Applied |
| Coil3 | `OkHttpNetworkFetcher.factory { client }` correct API | Confirmed |
| Performance | 30-relay thundering herd through Tor = 30-90s | Added delay, follow-up for stagger |
| Performance | Image latency +450-1250ms first load, cached OK | Expected, documented |
