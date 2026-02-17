# Chess Engine Refactoring

## Context

Chess feature had inconsistent state reconstruction:
- **Android**: Replays moves from `LocalCache.addressables` (can get stale/desync)
- **Desktop**: Buffers `pendingMoves` and syncs via `forceResync(fen)` (fragile)

Both ViewModels (~1200 + ~956 lines) duplicate logic that now exists in shared components.

## Completed Work

### Shared Components (Done)

| File | Location | Purpose |
|------|----------|---------|
| `ChessStateReconstructor` | `quartz/commonMain/` | Deterministic event→state reconstruction |
| `ChessStateReconstructorTest` | `quartz/jvmAndroidTest/` | 22 integration tests |
| `ChessEventCollector` | `commons/commonMain/` | Thread-safe event aggregation with dedup |
| `ChessGameLoader` | `commons/commonMain/` | Converts ReconstructionResult → LiveChessGameState |
| `ChessLobbyLogic` | `commons/commonMain/` | Shared business logic (challenges, moves, polling) |
| `ChessLobbyState` | `commons/commonMain/` | Shared UI state (StateFlows for games, challenges, status) |
| `ChessBroadcastStatus` | `commons/commonMain/` | Shared broadcast status sealed class |
| `ChessPollingDelegate` | `commons/commonMain/` | Configurable periodic refresh |
| `ChessFilterBuilder` | `commons/commonMain/subscription/` | Shared relay filter construction |
| `ChessSubscriptionController` | `commons/commonMain/subscription/` | Platform subscription interface |
| `ChessStatusBanner` | `amethyst/` (Android only) | Android broadcast status UI |

### Test Coverage

- Game lifecycle, move ordering/dedup, game end conditions
- Viewer perspectives, draw offers, castling, determinism

---

## Target Architecture

```
BOTH PLATFORMS IDENTICAL:

VM (~150 lines) → ChessLobbyLogic
                      │
                      ├─ publish: ChessEventPublisher (platform impl)
                      │
                      └─ refresh (periodic):
                            one-shot REQ to relays
                                    ↓
                            transient ChessEventCollector (use-once)
                                    ↓
                            ChessStateReconstructor.reconstruct()
                                    ↓
                            LiveChessGameState → UI
                            (collector discarded, no cache)
```

**Key principle**: No cache. Relays are the ONLY source of truth.

Every refresh cycle:
1. One-shot REQ to relays for all game events
2. Transient collector → reconstruct → get state
3. Diff against UI state, update if changed
4. Discard collector

Real-time subscription events apply moves **optimistically** to `LiveChessGameState`. Periodic full-reconstruction corrects any drift.

---

## Resolved Decisions

### 1. No LocalCache for Chess

**Decision**: Chess events must NOT enter LocalCache. Chess is inherently remote-only.

**How to stop writes**: Remove chess event handlers from `LocalCache.justConsumeInnerInner()` (lines 2956-2960 in `LocalCache.kt`). These are:
```kotlin
is LiveChessGameChallengeEvent -> consume(event, relay, wasVerified)  // REMOVE
is LiveChessGameAcceptEvent -> consume(event, relay, wasVerified)     // REMOVE
is LiveChessMoveEvent -> consume(event, relay, wasVerified)           // REMOVE
is LiveChessGameEndEvent -> consume(event, relay, wasVerified)        // REMOVE
```

Also remove chess DataSource from `RelaySubscriptionsCoordinator` (line 83: `val chess = ChessFilterAssembler(client)`). Chess manages its own relay subscriptions independently.

### 2. One-Shot Relay Fetch (in commons)

**Decision**: Create a shared one-shot fetch helper in commons using existing `IRequestListener` + `Channel` pattern.

**Existing pattern** (proven in production):
- `quartz/.../accessories/NostrClientSingleDownloadExt.kt` — single event download
- `quartz/.../accessories/NostrClientSendAndWaitExt.kt` — multi-relay wait

**Design**: The fetcher takes an `INostrClient` (available on both platforms) and uses the existing `IRequestListener` callback → `Channel` → `withTimeoutOrNull` pattern:

```kotlin
// commons/commonMain - shared one-shot fetch
class ChessRelayFetchHelper(private val client: INostrClient) {

    suspend fun fetchEvents(
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        timeoutMs: Long = 30_000,
    ): List<Event> {
        val events = mutableListOf<Event>()
        val eoseReceived = CompletableDeferred<Unit>()
        val subId = UUID.randomUUID().toString().take(8)

        val listener = object : IRequestListener {
            override fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                events.add(event)
            }
            override fun onEose(relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                eoseReceived.complete(Unit)
            }
        }

        client.openReqSubscription(subId, filters, listener)
        withTimeoutOrNull(timeoutMs) { eoseReceived.await() }
        client.close(subId)

        return events
    }
}
```

Platform adapters inject their `INostrClient`:
- **Android**: `account.client` (or equivalent from relay pool)
- **Desktop**: `relayManager.client`

### 3. Metadata Provider (interface in commons)

**Decision**: Shared `IUserMetadataProvider` interface in commons. Platform-specific implementations.

Android uses `LocalCache.users[pubkey].info`, Desktop uses `UserMetadataCache`. Both implement:

```kotlin
// commons/commonMain
interface IUserMetadataProvider {
    fun getDisplayName(pubkey: String): String
    fun getPictureUrl(pubkey: String): String?
}
```

`ChessChallenge` gets enriched with display fields by calling provider at construction time.

### 4. Challenge Type Migration

**Decision**: Enrich `ChessChallenge` with display fields (displayName, avatarUrl) so it replaces both `Note` (Android) and `LiveChessGameChallengeEvent` (Desktop) in UI code.

---

## Implementation Steps

### Step 1: Stop chess events entering LocalCache

**Files:**
- `amethyst/.../model/LocalCache.kt` — remove chess `when` branches (lines ~2956-2960)
- `amethyst/.../service/relayClient/RelaySubscriptionsCoordinator.kt` — remove `val chess` DataSource

### Step 2: Create one-shot relay fetch helper in commons

**File:** `commons/src/commonMain/.../chess/ChessRelayFetchHelper.kt` (new)

Uses `INostrClient` + `IRequestListener` + `Channel` pattern. Shared by both platforms.

### Step 3: Create IUserMetadataProvider in commons

**File:** `commons/src/commonMain/.../chess/IUserMetadataProvider.kt` (new)

### Step 4: Rewrite ChessLobbyLogic (relay-first)

**File:** `commons/src/commonMain/.../chess/ChessLobbyLogic.kt`

Replace `ChessEventFetcher` with `ChessRelayFetcher` (wraps `ChessRelayFetchHelper`):

```kotlin
interface ChessRelayFetcher {
    suspend fun fetchGameEvents(gameId: String): ChessGameEvents
    suspend fun fetchChallenges(): List<LiveChessGameChallengeEvent>
    suspend fun fetchRecentGames(): List<RelayGameSummary>
}
```

Rewrite refresh cycle:
```kotlin
private suspend fun refreshGame(gameId: String) {
    val events = relayFetcher.fetchGameEvents(gameId)  // one-shot from relays
    val result = ChessStateReconstructor.reconstruct(events, userPubkey)
    when (result) {
        is ReconstructionResult.Success ->
            state.replaceGameState(gameId, ChessGameLoader.toLiveGameState(result, userPubkey))
        is ReconstructionResult.Error ->
            state.setError("Game $gameId: ${result.message}")
    }
}
```

Add:
- `handleIncomingEvent(event)` — optimistic real-time event routing
- `handleGameAccepted()` + `startGameFromAcceptance()`
- `acceptDraw()`, `declineDraw()`, `claimAbandonmentVictory()`
- `retryWithBackoff()` for publish operations
- Subscription controller integration

### Step 5: Enhance ChessLobbyState

**File:** `commons/src/commonMain/.../chess/ChessLobbyState.kt`

Add:
- `replaceGameState(gameId, newState)` — for full reconstruction updates
- `completedGames: StateFlow<List<CompletedGame>>`
- Enrich `ChessChallenge` with `displayName`, `avatarUrl`

### Step 6: Platform adapters

**Android:** `amethyst/.../chess/AndroidChessAdapter.kt` (new)
- `AndroidChessPublisher` — wraps `account.signAndComputeBroadcast()`
- `AndroidRelayFetcher` — wraps `ChessRelayFetchHelper(account.client)`
- `AndroidMetadataProvider` — wraps `LocalCache.users[pubkey].info`

**Desktop:** `desktopApp/.../chess/DesktopChessAdapter.kt` (new)
- `DesktopChessPublisher` — wraps `account.signer.sign()` + `relayManager.broadcastToAll()`
- `DesktopRelayFetcher` — wraps `ChessRelayFetchHelper(relayManager.client)`
- `DesktopMetadataProvider` — wraps `UserMetadataCache`

### Step 7: Rewrite Android ChessViewModel

**File:** `amethyst/.../chess/ChessViewModel.kt` (1220 → ~150 lines)

Thin wrapper: delegates to `ChessLobbyLogic`, exposes state, routes `LocalCache.live.newEventBundles` for real-time events.

Delete: `RetryOperation`, `PublicGameInfo`, all LocalCache queries, all handle* methods, `ChessStatus`.

### Step 8: Rewrite Desktop DesktopChessViewModel

**File:** `desktopApp/.../chess/DesktopChessViewModel.kt` (956 → ~150 lines)

Thin wrapper: delegates to `ChessLobbyLogic`, keeps `UserMetadataCache` platform-specific.

Delete: `pendingMoves`, `pendingAccepts`, `challengesByGameId`, `processedEventIds`, `gamesBeingCreated`, all handle* methods, `applyPendingMoves`, `CompletedGame`.

### Step 9: Create shared BroadcastBanner

**File:** `commons/src/commonMain/.../chess/ChessBroadcastBanner.kt` (new)

Extract from Android's `ChessStatusBanner.kt`, use `ChessBroadcastStatus` from commons.

### Step 10: Update UI consumers

**Android:**
- `ChessGameScreen.kt` / `ChessLobbyScreen.kt`: `ChessChallenge` instead of `Note`
- Remove `ChessStatusBanner.kt`, use shared `ChessBroadcastBanner`

**Desktop:**
- `ChessScreen.kt`: `ChessChallenge` instead of `LiveChessGameChallengeEvent`
- Add `ChessBroadcastBanner`

---

## Files Modified

| File | Action | Notes |
|------|--------|-------|
| `LocalCache.kt` | Edit | Remove chess event handlers |
| `RelaySubscriptionsCoordinator.kt` | Edit | Remove chess DataSource |
| `commons/.../ChessRelayFetchHelper.kt` | **New** | One-shot relay query helper |
| `commons/.../IUserMetadataProvider.kt` | **New** | Metadata interface |
| `commons/.../ChessLobbyLogic.kt` | Rewrite | Relay-first, event routing, reconstructor |
| `commons/.../ChessLobbyState.kt` | Enhance | replaceGameState, completedGames, enriched ChessChallenge |
| `commons/.../ChessBroadcastBanner.kt` | **New** | Shared broadcast status UI |
| `amethyst/.../AndroidChessAdapter.kt` | **New** | Publisher + fetcher + metadata |
| `amethyst/.../ChessViewModel.kt` | Rewrite | 1220→150 lines |
| `amethyst/.../ChessStatusBanner.kt` | Delete | Replaced by shared |
| `amethyst/.../ChessGameScreen.kt` | Update | Challenge type + banner |
| `amethyst/.../ChessLobbyScreen.kt` | Update | Challenge type |
| `desktopApp/.../DesktopChessAdapter.kt` | **New** | Publisher + fetcher + metadata |
| `desktopApp/.../DesktopChessViewModel.kt` | Rewrite | 956→150 lines |
| `desktopApp/.../ChessScreen.kt` | Update | Challenge type + banner |

---

## Verification

1. `./gradlew :quartz:build`
2. `./gradlew :commons:build`
3. `./gradlew :amethyst:compileDebugKotlin`
4. `./gradlew :desktopApp:compileKotlin`
5. `./gradlew :quartz:jvmAndroidTest` — ChessStateReconstructor tests
6. `./gradlew :desktopApp:run` — manual test: create challenge, play moves
