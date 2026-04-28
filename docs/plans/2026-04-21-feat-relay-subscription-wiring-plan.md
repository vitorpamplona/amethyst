---
title: "feat: Wire Relay Config Categories into Desktop Subscriptions"
type: feat
status: active
date: 2026-04-21
origin: docs/plans/2026-04-21-feat-relay-config-parity-plan.md
---

# feat: Wire Relay Config Categories into Desktop Subscriptions

## Overview

Desktop's relay config UI publishes relay list events (kinds 10002, 10050, 10007, 10006) but the app itself uses hardcoded defaults or all connected relays for every subscription. This plan wires each relay category into the features that consume it.

## Problem Statement

| Feature | Currently Uses | Should Use |
|---------|---------------|------------|
| Home feed | all connected relays | NIP-65 outbox (kind 10002 write) |
| Notifications | all connected relays | NIP-65 inbox (kind 10002 read) |
| Search | all connected relays | Search relays (kind 10007) |
| DMs | `emptySet()` fallback → connected | DM relays (kind 10050) |
| All features | no filtering | Minus blocked relays (kind 10006) |

**Root causes:**
1. `DesktopAccountRelays` is dead code — never instantiated in Main.kt
2. `DesktopDmRelayState` in Main.kt uses hardcoded `MutableStateFlow(emptySet())`
3. No desktop state holders for search or blocked relay lists
4. No bootstrap subscription fetches user's own relay config events at login
5. Screens pass `allRelayUrls` to all `rememberSubscription` calls
6. `DesktopRelaySubscriptionsCoordinator.indexRelays` is static

## Proposed Solution

4 phases, each independently shippable:
1. Bootstrap subscription + wire existing state objects
2. Create missing state holders + aggregate relay categories
3. Update screens to consume category relay sets
4. Persist relay lists to Preferences

## Technical Approach

### Architecture

```
New/Modified:
├── Main.kt                           # MODIFY: instantiate DesktopAccountRelays, bootstrap sub
├── model/
│   ├── DesktopAccountRelays.kt       # MODIFY: add search + blocked relay tracking
│   ├── DesktopRelayCategories.kt     # NEW: aggregator with fallback logic
│   └── DesktopDmRelayState.kt        # UNCHANGED (already correct)
├── subscriptions/
│   ├── DesktopRelaySubscriptionsCoordinator.kt  # MODIFY: accept reactive relay sets
│   ├── BootstrapSubscription.kt      # NEW: fetch user's kind 10002/10050/10007/10006
│   └── SubscriptionUtils.kt          # MODIFY: rememberSubscription keys
├── ui/
│   ├── FeedScreen.kt                 # MODIFY: use NIP-65 outbox relays
│   ├── SearchScreen.kt               # MODIFY: use search relays
│   └── NotificationsScreen.kt        # MODIFY: use NIP-65 inbox relays
```

### Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Outbox model | v1: all filters to all outbox relays | True per-relay filter maps requires SubscriptionConfig redesign — defer |
| Search fallback | Hardcoded NIP-50 relays (relay.nostr.band) | Most connected relays don't support NIP-50 |
| DM fallback | Connected relays (existing behavior) | Blocking send is too aggressive for desktop UX |
| Blocked subtraction | Utility function at call site | Centralized in `RelayConnectionManager.subscribe` is opaque and affects all callers |
| Relay set change → resub | Debounce 1s via `distinctUntilChanged()` on StateFlows | Prevents thrashing during startup when multiple kind events arrive |
| Auto-connect category relays | Yes, add to relay pool if not present | DM/search relays from events may not be in connected set |
| Persist relay lists | java.util.prefs.Preferences | Consistent with existing DesktopPreferences pattern |
| FeedMetadataCoordinator.indexRelays | Keep static, recreate coordinator when relays change | Avoids commons API change |

### Phase 1: Bootstrap + Wire Existing State

**Goal:** Fetch user's relay config on login. Wire `DesktopAccountRelays` into Main.kt so kind 10050 events actually update DM relay state.

**1a. Create `BootstrapSubscription.kt`**

Fetches user's own replaceable events (kinds 10002, 10050, 10007, 10006) from connected relays on login.

```kotlin
class BootstrapSubscription(
    private val relayManager: RelayConnectionManager,
    private val scope: CoroutineScope,
) {
    fun subscribe(
        userPubKeyHex: HexKey,
        onEvent: (Event) -> Unit,
    ) {
        val filter = Filter(
            kinds = listOf(10002, 10050, 10007, 10006),
            authors = listOf(userPubKeyHex),
            limit = 4,
        )
        relayManager.subscribe(
            subId = "bootstrap-relay-config",
            filters = listOf(filter),
            listener = object : SubscriptionListener {
                override fun onEvent(event: Event, isLive: Boolean, relay: NormalizedRelayUrl, forFilters: List<Filter>?) {
                    onEvent(event)
                }
            },
        )
    }
}
```

**1b. Instantiate `DesktopAccountRelays` in Main.kt**

Replace the standalone `DesktopDmRelayState(MutableStateFlow(emptySet()), ...)` with `DesktopAccountRelays`:

```kotlin
// In Main.kt logged-in section (replace lines 924-929)
val accountRelays = remember(account, relayManager, scope) {
    DesktopAccountRelays(account.pubKeyHex, relayManager, scope)
}

// Bootstrap: fetch user's relay config events
LaunchedEffect(accountRelays) {
    relayManager.connectedRelays.first { it.isNotEmpty() }
    BootstrapSubscription(relayManager, scope).subscribe(account.pubKeyHex) { event ->
        accountRelays.consumeIfRelevant(event) // routes to appropriate handler
    }
}
```

**1c. Expand `DesktopAccountRelays` to route all relay config events**

Add methods to consume kinds 10002, 10007, 10006 (currently only handles 10050):

```kotlin
// In DesktopAccountRelays
private val _searchRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
val searchRelayList: StateFlow<Set<NormalizedRelayUrl>> = _searchRelayList.asStateFlow()

private val _blockedRelayList = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
val blockedRelayList: StateFlow<Set<NormalizedRelayUrl>> = _blockedRelayList.asStateFlow()

fun consumeIfRelevant(event: Event): Boolean {
    return when (event.kind) {
        ChatMessageRelayListEvent.KIND -> { consumeDmRelayList(event as ChatMessageRelayListEvent); true }
        SearchRelayListEvent.KIND -> { consumeSearchRelayList(event); true }
        BlockedRelayListEvent.KIND -> { consumeBlockedRelayList(event); true }
        else -> false
    }
}
```

**1d. Thread `accountRelays` to MainContent and layouts**

Single instance created in Main.kt, passed through composable tree. Replace the per-column `DesktopAccountRelays` created in `DeckColumnContainer`.

### Phase 2: Relay Category Aggregator

**Goal:** Single source of truth for "which relays should feature X use?" with fallback logic.

**Create `DesktopRelayCategories.kt`**

```kotlin
class DesktopRelayCategories(
    private val nip65State: Nip65RelayListState,
    private val accountRelays: DesktopAccountRelays,
    private val connectedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    scope: CoroutineScope,
) {
    /** Relays for home feed / publishing. NIP-65 write → fallback to connected */
    val feedRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.outboxFlow,
        connectedRelays,
    ) { outbox, connected -> outbox.ifEmpty { connected } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    /** Relays for receiving notifications. NIP-65 read → fallback to connected */
    val notificationRelays: StateFlow<Set<NormalizedRelayUrl>> = combine(
        nip65State.inboxFlow,
        connectedRelays,
    ) { inbox, connected -> inbox.ifEmpty { connected } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, connectedRelays.value)

    /** Relays for NIP-50 search. Search list → fallback to DEFAULT_SEARCH_RELAYS */
    val searchRelays: StateFlow<Set<NormalizedRelayUrl>> = accountRelays.searchRelayList
        .map { it.ifEmpty { DEFAULT_SEARCH_RELAYS } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SEARCH_RELAYS)

    /** DM relays — already handled by DesktopDmRelayState with fallback */
    val dmRelays: StateFlow<Set<NormalizedRelayUrl>> = accountRelays.dmRelays.flow

    /** Blocked relays to exclude from all subscriptions */
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>> = accountRelays.blockedRelayList

    companion object {
        val DEFAULT_SEARCH_RELAYS = setOf(
            NormalizedRelayUrl("wss://relay.nostr.band/"),
        )
    }
}
```

### Phase 3: Update Screens

**3a. SearchScreen.kt**

Replace `allRelayUrls` with `relayCategories.searchRelays`:

```kotlin
// Current (line 175):
rememberSubscription(connectedRelays, debouncedQuery, relayManager = relayManager) {
    // relays = allRelayUrls

// Target:
val searchRelays by relayCategories.searchRelays.collectAsState()
rememberSubscription(searchRelays, debouncedQuery, relayManager = relayManager) {
    // relays = searchRelays
```

Thread `relayCategories` to SearchScreen via RootContent params.

**3b. FeedScreen.kt**

Replace `allRelayUrls` with `relayCategories.feedRelays`:

```kotlin
val feedRelays by relayCategories.feedRelays.collectAsState()
rememberSubscription(feedRelays, ..., relayManager = relayManager) {
    // relays = feedRelays
```

**3c. NotificationsScreen.kt**

Use `relayCategories.notificationRelays` for incoming notification subscriptions.

**3d. Blocked relay subtraction**

Add utility:

```kotlin
// In RelayValidation.kt or new file
fun Set<NormalizedRelayUrl>.minusBlocked(
    blocked: Set<NormalizedRelayUrl>
): Set<NormalizedRelayUrl> = this - blocked
```

Apply at each subscription site:

```kotlin
val effectiveRelays = feedRelays.minusBlocked(blockedRelays)
```

**3e. Make DM subscriptions reactive**

In Main.kt, replace one-shot `subscribeToDms()` with a collector:

```kotlin
LaunchedEffect(accountRelays) {
    accountRelays.dmRelays.flow.collect { dmRelaySet ->
        subscriptionsCoordinator.resubscribeToDms(account.pubKeyHex, accountRelays.dmRelays, onDmEvent)
    }
}
```

### Phase 4: Persistence

**Goal:** Relay lists survive app restart without waiting for bootstrap fetch.

Save last-known relay lists to `DesktopPreferences`:

```kotlin
// On every relay list update
DesktopPreferences.nip65RelayList = mapper.writeValueAsString(outboxRelays)
DesktopPreferences.dmRelayList = mapper.writeValueAsString(dmRelays)
DesktopPreferences.searchRelayList = mapper.writeValueAsString(searchRelays)
DesktopPreferences.blockedRelayList = mapper.writeValueAsString(blockedRelays)

// On startup, before bootstrap fetch completes
val savedDmRelays = mapper.readValue<Set<String>>(DesktopPreferences.dmRelayList)
    .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
accountRelays.setDmRelays(savedDmRelays)
```

## Acceptance Criteria

### Phase 1: Bootstrap + Wire
- [ ] `DesktopAccountRelays` instantiated once in Main.kt, threaded to all screens
- [ ] Bootstrap subscription fetches kinds 10002, 10050, 10007, 10006 on login
- [ ] Kind 10050 events update `accountRelays.dmRelayList` (no longer hardcoded empty)
- [ ] Kind 10007 events populate `accountRelays.searchRelayList`
- [ ] Kind 10006 events populate `accountRelays.blockedRelayList`
- [ ] Remove per-column `DesktopAccountRelays` from DeckColumnContainer

### Phase 2: Aggregator
- [ ] `DesktopRelayCategories` exposes `feedRelays`, `notificationRelays`, `searchRelays`, `dmRelays`, `blockedRelays`
- [ ] Each StateFlow has appropriate fallback (connected, default search relays)
- [ ] `distinctUntilChanged()` on all flows to prevent subscription thrashing

### Phase 3: Screen Wiring
- [ ] SearchScreen uses `searchRelays` for NIP-50 subscriptions
- [ ] FeedScreen uses `feedRelays` for feed subscriptions
- [ ] NotificationsScreen uses `notificationRelays`
- [ ] Blocked relays subtracted from all subscription relay sets
- [ ] DM subscriptions reactive — resubscribe when dmRelays flow changes
- [ ] `rememberSubscription` keys on category relay sets

### Phase 4: Persistence
- [ ] Relay lists saved to Preferences on every update
- [ ] Loaded from Preferences on startup before bootstrap completes
- [ ] Bootstrap fetch overwrites saved data with fresh data from relays

## Dependencies & Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Subscription thrashing during startup | Medium | Medium | `distinctUntilChanged()` + 1s debounce on relay set changes |
| Bootstrap subscription returns stale events | Low | Low | Replaceable events — latest `created_at` wins |
| DM relays not in connected set | Medium | Medium | Auto-add to relay pool on relay set change |
| Blocked relay decryption fails (NIP-44) | Low | Low | Graceful fallback — empty blocked set |
| Coordinator recreation on relay change | Medium | Low | Debounce, only recreate when relay set structurally changes |

## Unanswered Questions

1. Should bootstrap subscription unsubscribe after receiving all 4 kinds, or stay open for live updates?
2. Should `DesktopRelayCategories` auto-add category relays to the relay pool (so they connect)?
3. For blocked relay decryption — can we use `signer.decrypt()` directly or need async handling for NIP-46?
4. Should we show a UI indicator when operating on fallback relays ("No NIP-65 published — using defaults")?
5. True outbox model (per-relay filter maps) — defer to separate plan or include as Phase 5?
