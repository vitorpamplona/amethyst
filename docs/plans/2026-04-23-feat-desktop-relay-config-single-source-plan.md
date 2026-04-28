---
title: "feat: DesktopRelayConfig — Single Source of Truth for All Relay Categories"
type: feat
status: active
date: 2026-04-23
---

# feat: DesktopRelayConfig — Single Source of Truth for All Relay Categories

## Problem

The current relay wiring is broken. Multiple classes (`DesktopRelayCategories`, `DesktopAccountRelays`, `RelayConnectionManager`) each hold parts of relay state, leading to race conditions, empty initial values, and screens using inconsistent relay sources.

**Symptoms:**
- Feed shows "0 feed relays" on startup (race: `allConfiguredRelays` empty at `DesktopRelayCategories` construction)
- NIP-65 relay changes don't propagate to feed subscriptions
- Different screens use different relay sources (`relayStatuses.keys` vs `connectedRelays` vs `feedRelays` vs `allRelayUrls`)
- No screen resubscribes when relay config changes

## Root Cause

| Issue | Why |
|-------|-----|
| `feedRelays` starts empty | `stateIn(Eagerly, allConfiguredRelays.value)` captures empty snapshot at construction time |
| Screens use inconsistent sources | FeedScreen uses `feedRelays`, NotificationsScreen uses `relayStatuses.keys`, ReadsScreen uses `relayStatuses.keys` |
| No reconnection on config change | `rememberSubscription` keys don't include relay category flows |
| Coordinator `indexRelays` is static | Passed at construction, never updated |

## Proposed Solution

**One class: `DesktopRelayConfig`** that:
1. Holds all relay category sets as reactive `StateFlow`s
2. Is initialized from `DefaultRelays.RELAYS` immediately (never empty)
3. Updates when NIP-65/DM/search/blocked events arrive
4. Persists to Preferences
5. Every screen reads from this single source

## Technical Approach

### Delete/Replace

| Remove | Replace With |
|--------|-------------|
| `DesktopRelayCategories.kt` | `DesktopRelayConfig.kt` |
| `DesktopAccountRelays.kt` | Merged into `DesktopRelayConfig` |
| `DesktopDmRelayState.kt` | Merged into `DesktopRelayConfig` |
| `LocalRelayCategories` CompositionLocal | `LocalRelayConfig` |
| `LocalAccountRelays` CompositionLocal | Removed (merged into `LocalRelayConfig`) |

### `DesktopRelayConfig.kt` — The One Class

```kotlin
class DesktopRelayConfig(
    val userPubKeyHex: HexKey,
    private val relayManager: RelayConnectionManager,
    private val nip65State: Nip65RelayListState,
    private val scope: CoroutineScope,
) {
    private val prefs = Preferences.userNodeForPackage(DesktopRelayConfig::class.java)

    // === Raw category state (updated by events + persistence + UI) ===

    private val _dmRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    private val _searchRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(DEFAULT_SEARCH_RELAYS)
    private val _blockedRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    // === Derived relay sets for subscriptions ===

    /** Default relays — always populated from DefaultRelays.RELAYS, never empty */
    private val defaultRelays: Set<NormalizedRelayUrl> =
        DefaultRelays.RELAYS.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    /** Feed relays: NIP-65 outbox → fallback to defaultRelays, minus blocked */
    val feedRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.outboxFlow,
        _blockedRelays,
    ) { outbox, blocked ->
        (outbox.ifEmpty { defaultRelays }) - blocked
    }.distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, defaultRelays) // NEVER empty

    /** Notification relays: NIP-65 inbox → fallback to defaultRelays, minus blocked */
    val notificationRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.inboxFlow,
        _blockedRelays,
    ) { inbox, blocked ->
        (inbox.ifEmpty { defaultRelays }) - blocked
    }.distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, defaultRelays)

    /** Search relays: kind 10007 → fallback to relay.nostr.band, minus blocked */
    val searchRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        _searchRelays,
        _blockedRelays,
    ) { search, blocked ->
        search - blocked
    }.distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SEARCH_RELAYS)

    /** DM relays: kind 10050 → fallback to defaultRelays */
    val dmRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        _dmRelays,
        _blockedRelays,
    ) { dm, blocked ->
        (dm.ifEmpty { defaultRelays }) - blocked
    }.distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, defaultRelays)

    /** Blocked relays (public read-only) */
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>> = _blockedRelays.asStateFlow()

    // Also expose raw lists for editors
    val dmRelayList: StateFlow<Set<NormalizedRelayUrl>> = _dmRelays.asStateFlow()
    val searchRelayList: StateFlow<Set<NormalizedRelayUrl>> = _searchRelays.asStateFlow()
    val blockedRelayList: StateFlow<Set<NormalizedRelayUrl>> = _blockedRelays.asStateFlow()

    init { loadFromPersistence() }

    // === Persistence (relay URLs as CSV) ===

    fun setDmRelays(relays: Set<NormalizedRelayUrl>) { _dmRelays.value = relays; save("dm", relays) }
    fun setSearchRelays(relays: Set<NormalizedRelayUrl>) { _searchRelays.value = relays; save("search", relays) }
    fun setBlockedRelays(relays: Set<NormalizedRelayUrl>) { _blockedRelays.value = relays; save("blocked", relays) }

    // === Event consumption (from bootstrap + relay subscriptions) ===

    fun consumeEvent(event: Event) { ... } // routes by kind, checks created_at

    companion object {
        val DEFAULT_SEARCH_RELAYS = setOfNotNull(RelayUrlNormalizer.normalizeOrNull("wss://relay.nostr.band"))
    }
}
```

**Key design difference from current code:** `defaultRelays` is a `val` computed once from `DefaultRelays.RELAYS` — it's NEVER empty. The `stateIn` initial value is `defaultRelays`, not a snapshot of some flow that might not be populated yet.

### Screen Updates

Every screen uses `LocalRelayConfig.current` to get the right relay set:

| Screen | Current Source | New Source | Change |
|--------|---------------|------------|--------|
| **FeedScreen** (feed sub) | `feedRelays` from DesktopRelayCategories | `relayConfig.feedRelays` | Same concept, but initial value is never empty |
| **FeedScreen** (contact list) | `allRelayUrls = relayStatuses.keys` | `relayConfig.feedRelays` | Unified source |
| **FeedScreen** (metadata) | `allRelayUrls` | `relayConfig.feedRelays` | Unified |
| **FeedScreen** (interactions) | `relayStatuses.value.keys` snapshot | `relayConfig.feedRelays` | Fix snapshot issue |
| **SearchScreen** | `searchRelays` from categories | `relayConfig.searchRelays` | Same concept |
| **NotificationsScreen** | `relayStatuses.keys` | `relayConfig.notificationRelays` | Now uses NIP-65 inbox |
| **ReadsScreen** | `relayStatuses.keys` | `relayConfig.feedRelays` | Unified with feed |
| **BookmarksScreen** | `relayStatuses.keys` | `relayConfig.feedRelays` | Unified |
| **DM subscriptions** | Hardcoded empty | `relayConfig.dmRelays` | Actually works now |

### Subscription Reactivity

`rememberSubscription` already rekeys when its key params change. Each screen uses `relayConfig.feedRelays.collectAsState()` as a key — when the StateFlow emits a new set (e.g., after NIP-65 update), the subscription teardowns and recreates with the new relay set. This is already how it works; the fix is just making the initial value non-empty.

### Coordinator Fix

`DesktopRelaySubscriptionsCoordinator.indexRelays` should use `relayConfig.feedRelays.value` at construction. Or better: make it a `var` so it can be updated:

```kotlin
class DesktopRelaySubscriptionsCoordinator(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    var indexRelays: Set<NormalizedRelayUrl>, // var, not val
    private val localCache: DesktopLocalCache,
)
```

Update it when relay config changes:
```kotlin
LaunchedEffect(relayConfig.feedRelays) {
    relayConfig.feedRelays.collect { relays ->
        subscriptionsCoordinator.indexRelays = relays
    }
}
```

## Implementation Steps

1. Create `DesktopRelayConfig.kt` merging AccountRelays + DmRelayState + RelayCategories
2. Create `LocalRelayConfig` CompositionLocal, remove `LocalRelayCategories` + `LocalAccountRelays`
3. Provide in Main.kt, remove old classes
4. Update every screen to use `LocalRelayConfig.current`
5. Make coordinator `indexRelays` mutable + reactive
6. Test: feed shows 7 relays on startup, search uses configured relays, NIP-65 changes propagate

## Acceptance Criteria

- [ ] Feed shows relay count immediately on startup (never "0 feed relays")
- [ ] All screens use `LocalRelayConfig.current` — no direct `relayStatuses.keys` usage
- [ ] NIP-65 relay changes propagate to feed subscriptions (resubscribes)
- [ ] Search relay changes propagate to search subscriptions
- [ ] DM relay changes propagate to DM subscriptions
- [ ] Relay config persists across restarts
- [ ] Per-screen relay picker dialogs use `DesktopRelayConfig` setters
- [ ] `DesktopRelaySubscriptionsCoordinator.indexRelays` updates reactively

## Unanswered Questions

1. Should `defaultRelays` also include user's connected relays, or strictly `DefaultRelays.RELAYS`?
2. Should we delete `DesktopDmRelayState` or keep it as an internal implementation detail?
