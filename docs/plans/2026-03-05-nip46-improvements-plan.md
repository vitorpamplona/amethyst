---
title: "feat: NIP-46 remote signer improvements (post-MVP)"
type: feat
status: backlog
date: 2026-03-05
origin: docs/plans/2026-03-05-fix-nip46-relay-isolation-and-init-plan.md
---

# feat: NIP-46 remote signer improvements (post-MVP)

Deferred items from the NIP-46 relay isolation fix. All are nice-to-haves discovered during deepening/review — none block the initial fix.

## 1. `auth_url` handling (nsec.app compatibility)

**Priority:** Medium — only affects nsec.app users, not Amber

NIP-46 spec defines `auth_url` for signers needing out-of-band user confirmation:
```json
{"id": "<req_id>", "result": "auth_url", "error": "<URL_to_display>"}
```

Client should open the URL, then resubscribe for the actual result using the same request ID.

**Current behavior:** Treated as `ReceivedButCouldNotPerform` error → times out.

**To implement:**
- New `BunkerResponseAuthUrl` class + parser
- New `SignerResult` state for pending auth
- Desktop UI to open URL in browser + resubscribe
- Timeout management for multi-step flow

**Files:** `BunkerResponse.kt`, `SignerResult.kt`, response parsers, `RemoteSignerManager.kt`, desktop `LoginCard.kt`

## 2. `get_public_key` validation after connect

**Priority:** Low — security hardening, not correctness

Infrastructure already exists: `NostrSignerRemote.getPublicKey()` is fully implemented and production-ready. `connect()` already returns the user pubkey, so this is redundant for normal operation.

**Value:** Belt-and-suspenders against MITM on the connect response. Low risk since NIP-44 encryption protects the channel.

**To implement:**
```kotlin
// In AccountManager.loadBunkerAccount() after remoteSigner.openSubscription()
val confirmedPubkey = remoteSigner.getPublicKey()
if (confirmedPubkey != expectedPubkey) {
    return Result.failure(Exception("Pubkey mismatch: signer returned $confirmedPubkey"))
}
```

**Cost:** 1 extra RPC round-trip + potential 30s timeout on failure.

## 3. Dynamic `since` filter on reconnect

**Priority:** Low — optimization, no correctness issue

`NostrClientStaticReq` re-sends the exact same filter on WebSocket reconnect. With `since = now() - 60`, this means after a reconnect the relay replays events from the original subscription time — wasteful but harmless (request ID matching discards stale responses).

**To implement:** Switch `NostrSignerRemote` from `NostrClientStaticReq` to `NostrClientDynamicReq`:
```kotlin
// Lambda recomputes since on each reconnect
val subscription = client.dynamicReq(
    relays = relays.toList(),
    filter = {
        Filter(
            kinds = listOf(NostrConnectEvent.KIND),
            tags = mapOf("p" to listOf(signer.pubKey)),
            since = TimeUtils.now() - 60,
        )
    },
) { event -> ... }
```

**Tradeoff:** More complexity. Only matters if relay operators complain about repeated full-history queries or reconnects are frequent.

## 4. `NostrClient.close()` method (upstream)

**Priority:** Medium — affects any code creating temporary `NostrClient` instances

`NostrClient.disconnect()` only closes WebSockets but leaks:
- `allRelays` combine flow (`stateIn(Eagerly)` on `scope`)
- `debouncingConnection` debounce flow (`stateIn(Eagerly)` on `scope`)
- The `SupervisorJob` scope itself

**To implement:**
```kotlin
// In NostrClient.kt
fun close() {
    disconnect()
    scope.cancel()
}
```

**Current workaround:** `disconnectNip46Client()` in AccountManager handles our specific case. But any future code creating temporary `NostrClient` instances will hit the same leak.

**Recommendation:** PR upstream to quartz adding `close()` to `NostrClient`.

## 5. `FeedMetadataCoordinator` reactive relay sets

**Priority:** Low — deferred construction in the fix plan solves the immediate bug

Currently `indexRelays` is `private val` — immutable after construction. If relay sets change at runtime (user adds/removes relays), the coordinator won't pick up changes.

**To implement:** Accept `StateFlow<Set<NormalizedRelayUrl>>` instead of `Set<NormalizedRelayUrl>`, collect in coordinator's scope.

**Current workaround:** Nullable coordinator pattern with lazy construction after relays connect.
