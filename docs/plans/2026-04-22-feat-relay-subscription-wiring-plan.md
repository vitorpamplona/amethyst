---
title: "feat: Wire Relay Config Categories into Desktop Subscriptions"
type: feat
status: active
date: 2026-04-22
origin: docs/plans/2026-04-21-feat-relay-config-parity-plan.md
deepened: 2026-04-22
---

# feat: Wire Relay Config Categories into Desktop Subscriptions

## Enhancement Summary

**Deepened on:** 2026-04-22
**Review agents used:** nostr-expert, kotlin-coroutines, desktop-expert, performance-oracle, architecture-strategist, code-simplicity-reviewer

### Key Changes from Review
1. **2 phases, not 4** — merged aggregator into Phase 1, cut persistence (YAGNI)
2. **No new files for BootstrapSubscription or minusBlocked** — inline everything
3. **Keep DesktopRelayCategories** but provide via `LocalRelayCategories` CompositionLocal (matches `LocalTorState` pattern)
4. **Bake blocked subtraction + `debounce(1s)` into aggregator** — centralized, not per-call-site
5. **Route bootstrap through localCache** — keeps Nip65RelayListState in sync
6. **Kind 10006/10007 need NIP-51 decryption** — use `privateTags(signer)`
7. **`connectedRelays.first { isNotEmpty }` needs timeout** — `withTimeoutOrNull(30s)`
8. **Make FeedMetadataCoordinator.indexRelays mutable** — avoid recreation, preserve dedupe state
9. **Add `created_at` checking** in consumeIfRelevant to prevent stale overwrites

## Problem Statement

| Feature | Currently Uses | Should Use |
|---------|---------------|------------|
| Home feed | all connected relays | NIP-65 outbox (kind 10002 write) |
| Notifications | all connected relays | NIP-65 inbox (kind 10002 read) |
| Search | all connected relays | Search relays (kind 10007) |
| DMs | `emptySet()` fallback → connected | DM relays (kind 10050) |
| All features | no filtering | Minus blocked relays (kind 10006) |

## Technical Approach

### Phase 1: Wire State + Bootstrap (single PR)

**1a. Expand `DesktopAccountRelays.kt`**

Add search/blocked StateFlows + `consumeIfRelevant()` dispatcher with `created_at` dedup:

```kotlin
private val _searchRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
val searchRelayList: StateFlow<Set<NormalizedRelayUrl>> = _searchRelayList.asStateFlow()

private val _blockedRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
val blockedRelayList: StateFlow<Set<NormalizedRelayUrl>> = _blockedRelayList.asStateFlow()

// Track created_at to prevent stale overwrites
private var lastSearchCreatedAt = 0L
private var lastBlockedCreatedAt = 0L
private var lastDmCreatedAt = 0L

fun consumeIfRelevant(event: Event): Boolean {
    return when (event.kind) {
        ChatMessageRelayListEvent.KIND -> {
            if (event.createdAt > lastDmCreatedAt) {
                lastDmCreatedAt = event.createdAt
                consumeDmRelayList(event as ChatMessageRelayListEvent)
            }
            true
        }
        SearchRelayListEvent.KIND -> {
            if (event.createdAt > lastSearchCreatedAt) {
                lastSearchCreatedAt = event.createdAt
                // NIP-51: try public tags first, then decrypt private
                val relays = (event as? SearchRelayListEvent)?.publicRelays()?.toSet() ?: emptySet()
                _searchRelayList.value = relays
            }
            true
        }
        BlockedRelayListEvent.KIND -> {
            if (event.createdAt > lastBlockedCreatedAt) {
                lastBlockedCreatedAt = event.createdAt
                val relays = (event as? BlockedRelayListEvent)?.publicRelays()?.toSet() ?: emptySet()
                _blockedRelayList.value = relays
            }
            true
        }
        else -> false
    }
}
```

**1b. Create `DesktopRelayCategories.kt`** — aggregator with fallback + blocked subtraction + debounce

```kotlin
class DesktopRelayCategories(
    nip65State: Nip65RelayListState,
    accountRelays: DesktopAccountRelays,
    connectedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    scope: CoroutineScope,
) {
    val feedRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.outboxFlow, connectedRelays, accountRelays.blockedRelayList,
    ) { outbox, connected, blocked ->
        (outbox.ifEmpty { connected }) - blocked
    }.debounce(1.seconds).distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    val notificationRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.inboxFlow, connectedRelays, accountRelays.blockedRelayList,
    ) { inbox, connected, blocked ->
        (inbox.ifEmpty { connected }) - blocked
    }.debounce(1.seconds).distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    val searchRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        accountRelays.searchRelayList, accountRelays.blockedRelayList,
    ) { search, blocked ->
        (search.ifEmpty { DEFAULT_SEARCH_RELAYS }) - blocked
    }.debounce(1.seconds).distinctUntilChanged()
     .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SEARCH_RELAYS)

    val dmRelays: StateFlow<Set<NormalizedRelayUrl>> = accountRelays.dmRelays.flow

    companion object {
        val DEFAULT_SEARCH_RELAYS = setOf(NormalizedRelayUrl("wss://relay.nostr.band/"))
    }
}
```

**1c. Create `LocalRelayCategories.kt`** — CompositionLocal (matches `LocalTorState` pattern)

```kotlin
val LocalRelayCategories = compositionLocalOf<DesktopRelayCategories> {
    error("No DesktopRelayCategories provided")
}
```

**1d. Wire in Main.kt**

- Instantiate single `DesktopAccountRelays` in logged-in section
- Create `DesktopRelayCategories` combining nip65State + accountRelays + connectedRelays
- Inline bootstrap subscription in `LaunchedEffect` (~10 lines)
- Route bootstrap events through `localCache` for NIP-65 sync
- Provide `LocalRelayCategories` via `CompositionLocalProvider`
- Use `withTimeoutOrNull(30.seconds)` on `connectedRelays.first { isNotEmpty }`
- Remove hardcoded `DesktopDmRelayState(emptySet())` and per-column `DesktopAccountRelays`

### Phase 2: Screen Wiring

Each screen collects from `LocalRelayCategories.current`:

**SearchScreen.kt:** `val searchRelays by LocalRelayCategories.current.searchRelays.collectAsState()` → use in `rememberSubscription`

**FeedScreen.kt:** `val feedRelays by LocalRelayCategories.current.feedRelays.collectAsState()` → replace `allRelayUrls`

**NotificationsScreen.kt:** `val notificationRelays by LocalRelayCategories.current.notificationRelays.collectAsState()`

**DM subscriptions:** Make reactive via `debounce(500).collect { resubscribe }` with Job tracking

## Files Modified

| File | Change |
|------|--------|
| `DesktopAccountRelays.kt` | Add search/blocked flows, consumeIfRelevant, created_at dedup |
| `DesktopRelayCategories.kt` | NEW: aggregator with debounce + blocked subtraction |
| `LocalRelayCategories.kt` | NEW: CompositionLocal (3 lines) |
| `Main.kt` | Wire accountRelays, relayCategories, inline bootstrap, CompositionLocalProvider |
| `DeckColumnContainer.kt` | Remove per-column DesktopAccountRelays creation |
| `FeedScreen.kt` | Use feedRelays from LocalRelayCategories |
| `SearchScreen.kt` | Use searchRelays from LocalRelayCategories |
| `NotificationsScreen.kt` | Use notificationRelays from LocalRelayCategories |

## Acceptance Criteria

- [ ] Bootstrap subscription fetches kinds 10002/10050/10007/10006 on login
- [ ] Events routed through localCache (NIP-65 stays in sync)
- [ ] SearchScreen uses search relays (falls back to relay.nostr.band)
- [ ] FeedScreen uses NIP-65 outbox relays (falls back to connected)
- [ ] Blocked relays subtracted from all category relay sets
- [ ] DM subscriptions reactive to relay changes
- [ ] No subscription thrashing at startup (debounce 1s)
- [ ] `connectedRelays.first` has 30s timeout

## Unanswered Questions

1. NIP-51 private tag decryption for kind 10007/10006 — defer to v2 or attempt now? (Public tags work for `create()`, but `updateRelayList()` moves to private)
