---
title: "feat: Clean Cache Architecture — Single Source of Truth"
type: feat
status: active
date: 2026-03-22
origin: docs/brainstorms/2026-03-22-clean-cache-architecture-brainstorm.md
---

# feat: Clean Cache Architecture — Single Source of Truth

## Enhancement Summary

**Deepened on:** 2026-03-23
**Agents used:** kotlin-coroutines, compose-expert, desktop-expert, kotlin-expert

### Critical Issues Discovered
1. **ViewModel scope leak** — `remember(key)` doesn't call `onCleared()`, leaking coroutines on feedMode switch
2. **Note.flowSet allocation** — `note.flow()` creates `NoteFlowSet` during composition (side effect); needs `remember`
3. **Coordinator needs SupervisorJob** — one failed consume shouldn't kill all subscriptions
4. **@Volatile followedUsers insufficient** — use `MutableStateFlow<Set<HexKey>>` for thread safety + observability
5. **No error handling in consume→emit pipeline** — unhandled exception kills scope

### Key Improvements
- ViewModel lifecycle: `DisposableEffect` cleanup pattern for Desktop
- FeedNoteCard: `NoteInteractionSnapshot` collapses 3 flow observers into recomposition-safe snapshot
- Coordinator: `SupervisorJob` + `ConcurrentHashMap<String, Job>` for subscription tracking
- consume() dispatch: kind-based registry map (O(1)) replaces growing `when` block

---

## Overview

Migrate all desktop feed screens from inline `EventCollectionState` + direct relay subscriptions (System A) to the cache-centric `DesktopFeedViewModel` + `FeedFilter` + `FeedContentState` architecture (System B). Cache becomes the single source of truth. No screen ever reads relay callbacks directly.

```
WebSocket (OkHttp, persistent)
    ↓ push-based
DesktopRelaySubscriptionsCoordinator
    ↓ consume()
DesktopLocalCache (Note model with replies, reactions, zaps)
    ↓ eventStream (SharedFlow<Set<Note>>)
DesktopFeedViewModel (per-screen, with FeedFilter)
    ↓ FeedContentState (Loading/Loaded/Empty/Error)
UI (collects StateFlow, reads Note.flowSet for live counts)
```

(see brainstorm: `docs/brainstorms/2026-03-22-clean-cache-architecture-brainstorm.md`)

## Problem Statement

Current system (System A) causes:
- **State loss on navigation** — `remember`-scoped `EventCollectionState` destroyed when sidebar tab changes
- **Duplicate relay subscriptions** — FeedScreen alone has 8 `rememberSubscription()` calls
- **Fragile count tracking** — zaps/reactions/replies tracked in per-screen `mutableStateOf` maps, lost on recompose
- **Slow tab switching** — 2-5s reload on Home→Profile→Home because data refetched from relays
- **No freshness indicator** — user can't tell if feed is current or stale

System B infrastructure already exists but is not wired to any screen:

| Component | Status | Location |
|-----------|--------|----------|
| `DesktopFeedViewModel` | Built | `desktopApp/.../viewmodels/` |
| 8 `FeedFilter` implementations | Built | `desktopApp/.../feeds/DesktopFeedFilters.kt` |
| `FeedContentState` | Built | `commons/.../ui/feeds/FeedContentState.kt` |
| `DesktopRelaySubscriptionsCoordinator.consumeEvent()` | Built | `desktopApp/.../subscriptions/` |
| `DesktopLocalCache` + `eventStream` | Built | `desktopApp/.../cache/` |

## Proposed Solution

Four-phase migration: FeedScreen first (proves pattern), then expand cache coverage and migrate remaining screens, consolidate subscriptions, add health UI.

### Key Design Decisions (from brainstorm)

1. **Coordinator owns all subscriptions** — each screen has at most ONE subscription request to Coordinator, never multiple `rememberSubscription()` calls
2. **Counts live on Note model** — `note.replies.size`, `note.countReactions()`, `note.zapsAmount` (observed via `Note.flowSet`)
3. **Relay health indicator** — "last event received" per subscription, hidden when <30s, shown as duration when stale
4. **Always-alive core subscriptions** — contact list + home feed stay open even on Settings screen
5. **WebSocket push-based** — "pull-to-refresh" = close + reopen subscription with fresh `since` filter
6. **SearchScreen migrates** — NIP-50 results route through cache, AdvancedSearchBarState stays for query/history management, ViewModel reads cache for results

## Technical Approach

### Architecture

```
┌─────────────────────────────────────────────┐
│                   Relays                     │
│         (persistent WebSocket via OkHttp)    │
└──────────────────┬──────────────────────────┘
                   │ push events
    ┌──────────────▼──────────────┐
    │  DesktopRelaySubscriptions  │
    │       Coordinator           │
    │  (SupervisorJob scope)      │
    │                             │
    │  Always-alive:              │
    │  - contact list (kind 3)    │
    │  - home feed (kind 1)       │
    │  - metadata (rate-limited)  │
    │                             │
    │  Screen-requested:          │
    │  - one sub per screen       │
    │  - interactions (7,9735,6)  │
    │  - reads (kind 30023)       │
    │  - search (NIP-50)          │
    └──────────────┬──────────────┘
                   │ consumeEvent() [try-catch per event]
    ┌──────────────▼──────────────┐
    │     DesktopLocalCache       │
    │  (Single Source of Truth)   │
    │                             │
    │  notes: BoundedLargeCache   │
    │  users: BoundedLargeCache   │
    │  _followedUsers: StateFlow  │
    │  subscriptionHealth: Map    │
    │                             │
    │  consume(): kind registry   │
    │  eventStream:               │
    │    newEventBundles (250ms)  │
    │    deletedEventBundles      │
    └──────────────┬──────────────┘
                   │ SharedFlow<Set<Note>>
    ┌──────────────▼──────────────┐
    │   DesktopFeedViewModel(s)   │
    │  + DisposableEffect cleanup │
    │                             │
    │  One per screen:            │
    │  - HomeFeedVM (Following)   │
    │  - GlobalFeedVM             │
    │  - ProfileFeedVM(pubkey)    │
    │  - ThreadVM(noteId)         │
    │  - BookmarksVM(ids)         │
    │  - ReadsVM                  │
    │  - NotificationsVM(pubkey)  │
    │  - SearchVM(query)          │
    │                             │
    │  init: refreshSuspended()   │
    │  collect: eventStream →     │
    │    FeedFilter.applyFilter() │
    └──────────────┬──────────────┘
                   │ FeedState (Loaded/Loading/Empty/Error)
    ┌──────────────▼──────────────┐
    │           UI Layer          │
    │                             │
    │  NoteInteractionSnapshot    │
    │  (recomposition-safe)       │
    │                             │
    │  DisposableEffect for:      │
    │  - ViewModel cleanup        │
    │  - Note.clearFlow()         │
    │  - Coordinator release      │
    └─────────────────────────────┘
```

### Implementation Phases

---

#### Phase 1: FeedScreen Migration (Foundation)

**Goal:** Prove the pattern on the most complex screen. Remove all inline state management.

##### 1a. Expand `DesktopLocalCache.consume()` coverage

Currently handles: MetadataEvent, TextNoteEvent, ReactionEvent, LnZapRequestEvent, LnZapEvent.

**Add support for:**

| Event Type | Kind | Method | Links |
|------------|------|--------|-------|
| `RepostEvent` | 6 | `consumeRepost()` | `Note.addBoost()` on target |
| `ContactListEvent` | 3 | `consumeContactList()` | Updates `_followedUsers` StateFlow |
| `LongTextNoteEvent` | 30023 | `consumeLongTextNote()` | Creates Note like TextNote |
| `BookmarkListEvent` | 30001 | `consumeBookmarkList()` | Stores on `addressableNotes` or dedicated field |

**File:** `desktopApp/.../cache/DesktopLocalCache.kt`

Each `consume*()` method follows existing pattern:
1. `getOrCreateNote(event.id)` — deduplicate
2. Check `note.event != null` → already seen, return false
3. `getOrCreateUser(event.pubKey)` — ensure author exists
4. `note.loadEvent(event, author, relatedNotes)` — populate
5. Link relationships (`addReply`, `addReaction`, `addZap`, `addBoost`)
6. Return true (new event)

**Research Insight: Use kind-based registry instead of growing `when` block:**

```kotlin
// O(1) dispatch, extensible without editing consume()
private val consumers = mutableMapOf<Int, (Event, NormalizedRelayUrl?) -> Boolean>()

init {
    consumers[MetadataEvent.KIND] = { e, r -> consumeMetadata(e as MetadataEvent); true }
    consumers[TextNoteEvent.KIND] = { e, r -> consumeTextNote(e as TextNoteEvent, r) }
    consumers[ReactionEvent.KIND] = { e, r -> consumeReaction(e as ReactionEvent, r) }
    consumers[RepostEvent.KIND] = { e, r -> consumeRepost(e as RepostEvent, r) }
    consumers[ContactListEvent.KIND] = { e, r -> consumeContactList(e as ContactListEvent); true }
    // ...
}

fun consume(event: Event, relay: NormalizedRelayUrl?): Boolean =
    consumers[event.kind]?.invoke(event, relay) ?: false
```

**Research Insight: Replace `@Volatile followedUsers` with `MutableStateFlow`:**

```kotlin
// Thread-safe atomic updates + Compose-observable
private val _followedUsers = MutableStateFlow<Set<HexKey>>(emptySet())
val followedUsers: StateFlow<Set<HexKey>> = _followedUsers.asStateFlow()

fun updateFollowedUsers(users: Set<HexKey>) {
    _followedUsers.value = users
}
```

`@Volatile` only guarantees reference visibility — read-modify-write races are still possible. `MutableStateFlow` gives atomic `value` swaps plus observability for free (`DesktopFollowingFeedFilter` can react to changes without polling).

##### 1b. Coordinator always-alive subscriptions

Move home feed + contact list subscriptions from FeedScreen into Coordinator.

**File:** `desktopApp/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt`

```kotlin
// New methods on Coordinator
fun subscribeToHomeFeed(relays: Set<NormalizedRelayUrl>, followedUsers: Set<String>)
fun subscribeToContactList(relays: Set<NormalizedRelayUrl>, pubKeyHex: String)
fun requestInteractions(noteIds: List<String>, relays: Set<NormalizedRelayUrl>): String
fun releaseInteractions(subId: String)
```

`requestInteractions()` replaces per-screen zap/reaction/reply/repost subscriptions. Subscribes to kinds 7, 9735, 6, and reply-kind-1 for given note IDs. Results route through `consumeEvent()` → cache → eventStream. Each screen makes at most ONE interaction request.

**Subscription health tracking:**

```kotlin
data class SubscriptionHealth(
    val lastEventReceivedAt: Long?,
    val eoseReceived: Boolean,
)

val subscriptionHealth: StateFlow<Map<String, SubscriptionHealth>>
```

Updated on every event and EOSE callback.

**Research Insight: Coordinator scope MUST use SupervisorJob:**

```kotlin
// In Main.kt where Coordinator is created:
val coordinatorScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
        println("Coordinator error: ${throwable.message}")
    }
)
```

Without `SupervisorJob`, one failed `consume()` call kills ALL subscription processing. Each subscription should be an independent child coroutine.

**Research Insight: Track subscription Jobs for proper cancellation:**

```kotlin
private val screenSubscriptions = ConcurrentHashMap<String, Job>()

fun requestInteractions(noteIds: List<String>, relays: Set<NormalizedRelayUrl>): String {
    val subId = generateSubId("interactions")
    val job = scope.launch {
        // subscription logic
    }
    screenSubscriptions[subId] = job
    return subId
}

fun releaseInteractions(subId: String) {
    screenSubscriptions.remove(subId)?.cancel()
    client.close(subId)
}
```

**Research Insight: Add try-catch in consumeEvent:**

```kotlin
fun consumeEvent(event: Event, relay: NormalizedRelayUrl?) {
    scope.launch(Dispatchers.IO) {
        try {
            val consumed = localCache.consume(event, relay)
            if (consumed) {
                val note = localCache.getNoteIfExists(event.id) ?: return@launch
                eventBundler.invalidateList(note) { batch ->
                    localCache.eventStream.emitNewNotes(batch)
                }
            }
        } catch (e: Exception) {
            // Don't rethrow — other events should continue processing
            println("Failed to consume ${event.kind}: ${e.message}")
        }
    }
}
```

##### 1c. Rewrite FeedScreen to use DesktopFeedViewModel

**File:** `desktopApp/.../ui/FeedScreen.kt`

**Remove:**
- `EventCollectionState<Event>` and all `remember` state maps
- All 8 `rememberSubscription()` blocks
- `zapsByEvent`, `reactionIdsByEvent`, `replyIdsByEvent`, `repostIdsByEvent` maps
- `followedUsers` local state (read from `localCache.followedUsers`)
- `eoseReceivedCount` tracking

**Add:**
- `DesktopFeedViewModel` keyed on `feedMode` with `DisposableEffect` cleanup:

```kotlin
val viewModel = remember(feedMode) {
    val filter = when (feedMode) {
        FeedMode.GLOBAL -> DesktopGlobalFeedFilter(localCache)
        FeedMode.FOLLOWING -> DesktopFollowingFeedFilter(localCache) {
            localCache.followedUsers.value
        }
    }
    DesktopFeedViewModel(filter, localCache)
}

// CRITICAL: Cancel old ViewModel's viewModelScope on recreation
DisposableEffect(viewModel) {
    onDispose { viewModel.clear() }
}

val feedState by viewModel.feedState.feedContent.collectAsState()
```

- Render based on `FeedState`: `Loading`, `Loaded`, `Empty`, `FeedError`
- Relay health indicator from Coordinator

**Research Insight: ViewModel scope leak is the #1 risk.**

`remember(key)` creates a new ViewModel on key change but does NOT call `onCleared()`. On JVM Desktop, there is no `ViewModelStoreOwner`. The old ViewModel's `viewModelScope` coroutines keep running — leaked collectors on `eventStream`. `DisposableEffect(viewModel) { onDispose { viewModel.clear() } }` fixes this.

If `ViewModel.clear()` is not accessible in lifecycle 2.10.0 KMP, add a `fun destroy()` method on `DesktopFeedViewModel` that cancels a custom scope.

**Research Insight: Use `DisposableEffect` (not `LaunchedEffect`) for Coordinator subscriptions:**

```kotlin
// Interaction subscription with proper cleanup
DisposableEffect(loadedNoteIds) {
    val subId = coordinator.requestInteractions(loadedNoteIds, relays)
    onDispose { coordinator.releaseInteractions(subId) }
}
```

`LaunchedEffect` cancels its coroutine on dispose but doesn't call explicit cleanup. `DisposableEffect` guarantees the `onDispose` block runs.

##### 1d. Rewrite FeedNoteCard to accept Note

**File:** `desktopApp/.../ui/FeedScreen.kt` (FeedNoteCard composable)

**Before:**
```kotlin
fun FeedNoteCard(
    event: Event,
    zapReceipts: List<ZapReceipt>,
    reactionCount: Int,
    replyCount: Int,
    repostCount: Int,
    ...
)
```

**After — using NoteInteractionSnapshot for recomposition safety:**

```kotlin
@Immutable
data class NoteInteractionSnapshot(
    val reactionCount: Int,
    val replyCount: Int,
    val boostCount: Int,
    val zapAmount: BigDecimal,
)

@Composable
fun rememberNoteInteractionState(note: Note): NoteInteractionSnapshot {
    // Cache flowSet reference — note.flow() allocates if null (side effect)
    val flowSet = remember(note) { note.flow() }

    val reactionsState by flowSet.reactions.stateFlow.collectAsState()
    val repliesState by flowSet.replies.stateFlow.collectAsState()
    val zapsState by flowSet.zaps.stateFlow.collectAsState()

    // Clean up flowSet when note card leaves composition
    DisposableEffect(note) {
        onDispose { note.clearFlow() }
    }

    return remember(reactionsState, repliesState, zapsState) {
        NoteInteractionSnapshot(
            reactionCount = note.countReactions(),
            replyCount = note.replies.size,
            boostCount = note.boosts.size,
            zapAmount = note.zapsAmount,
        )
    }
}

@Composable
fun FeedNoteCard(
    note: Note,
    localCache: DesktopLocalCache,
    ...
) {
    val interactions = rememberNoteInteractionState(note)

    NoteCard(
        note = note.event!!.toNoteDisplayData(localCache),
        ...
    )
    NoteActionsRow(
        reactionCount = interactions.reactionCount,
        replyCount = interactions.replyCount,
        repostCount = interactions.boostCount,
        zapAmountSats = interactions.zapAmount.toLong(),
        ...
    )
}
```

**Research Insights:**

- **`note.flow()` is a side effect** — it allocates `NoteFlowSet` if null. Wrap in `remember(note)` to avoid repeated allocation during recomposition.
- **`@Immutable NoteInteractionSnapshot`** prevents downstream recomposition when `invalidateData()` fires but counts haven't actually changed. `NoteActionsRow` only recomposes when snapshot content differs.
- **`DisposableEffect` for `clearFlow()`** — without cleanup, every Note that was ever visible retains its `NoteFlowSet` permanently. `clearFlow()` checks `isInUse()` and nullifies when subscriber count = 0.
- **3 StateFlow collectors per card is acceptable** — they're lightweight. With 50 visible cards = 150 collectors. The `NoteInteractionSnapshot` pattern prevents cascading recomposition.

##### 1e. Render FeedState in FeedScreen

```kotlin
when (val state = feedState) {
    is FeedState.Loading -> LoadingState("Loading notes...")
    is FeedState.Empty -> EmptyState(title = "...", onRefresh = { ... })
    is FeedState.FeedError -> ErrorState(message = state.errorMessage)
    is FeedState.Loaded -> {
        val notes by state.feed.collectAsState()
        LazyColumn {
            items(notes.list, key = { it.idHex }) { note ->
                FeedNoteCard(note = note, localCache = localCache, ...)
            }
        }
    }
}
```

**Research Insight: `key = { it.idHex }` is critical.** Since `Note` is `@Stable`, Compose trusts referential equality. The same `Note` object across two list emissions = skipped recomposition for that item. Cache reuses `Note` instances, so this works correctly.

**Research Insight: First frame shows `Loading` briefly.** `refreshSuspended()` runs on `Dispatchers.IO`. The first compose frame sees `FeedState.Loading` until the IO dispatch completes. This is sub-100ms with warm cache but not truly instant. Acceptable for the UX.

##### Phase 1 Acceptance Criteria

- [ ] `DesktopLocalCache.consume()` handles RepostEvent, ContactListEvent, LongTextNoteEvent, BookmarkListEvent
- [ ] consume() uses kind-based registry dispatch (not growing `when` block)
- [ ] `followedUsers` is `MutableStateFlow` (not `@Volatile`)
- [ ] Coordinator scope uses `SupervisorJob` + `CoroutineExceptionHandler`
- [ ] Coordinator manages always-alive home feed + contact list subscriptions
- [ ] Coordinator exposes `requestInteractions()` / `releaseInteractions()` with Job tracking
- [ ] `consumeEvent()` has try-catch per event
- [ ] FeedScreen uses `DesktopFeedViewModel` — no `EventCollectionState`, no `rememberSubscription()`
- [ ] FeedScreen has `DisposableEffect(viewModel) { onDispose { viewModel.clear() } }`
- [ ] FeedScreen uses `DisposableEffect` for Coordinator subscription cleanup
- [ ] FeedNoteCard takes `Note`, uses `rememberNoteInteractionState()` + `NoteInteractionSnapshot`
- [ ] FeedNoteCard has `DisposableEffect` for `note.clearFlow()` cleanup
- [ ] Feed mode switch (Following ↔ Global) works via ViewModel recreation
- [ ] Navigation Home → Profile → Home shows cached data instantly (<100ms)
- [ ] `./gradlew spotlessApply :desktopApp:compileKotlin` passes

---

#### Phase 2: Migrate Remaining Feed Screens

Apply the proven pattern from Phase 1 to all other screens. Each screen gets at most ONE subscription request to Coordinator. Every screen uses `DisposableEffect` for ViewModel + subscription cleanup.

##### 2a. UserProfileScreen

**Dual concern:** Profile header (metadata, follow status) + notes feed.

- Notes feed → `DesktopFeedViewModel(DesktopProfileFeedFilter(cache, pubKeyHex), cache)`
- Profile header → reads from `localCache.users` for metadata, single Coordinator request for fresh metadata
- Tab switching (Notes/Replies/Gallery) → different FeedFilter per tab, keyed on `remember(tab)`
- **DisposableEffect cleanup** for ViewModel on tab switch and screen exit

**File:** `desktopApp/.../ui/UserProfileScreen.kt`

##### 2b. ThreadScreen

**DesktopThreadFilter** extends `FeedFilter` (not `AdditiveFeedFilter`) — does graph walk via `Note.replies`.

- On new event bundle → full `refreshSuspended()` (re-walks graph from cache). Acceptable because thread size is bounded and `BoundedLargeCache` iteration is lock-free.
- Thread level tracking: `DesktopThreadFilter.feed()` returns flattened list with depth info.

**File:** `desktopApp/.../ui/ThreadScreen.kt`

##### 2c. BookmarksScreen

- `DesktopBookmarkFeedFilter(cache) { bookmarkedIds() }` — lambda provides current bookmark IDs
- Bookmark change propagation: After publishing BookmarkListEvent, call `viewModel.feedState.checkKeysInvalidateDataAndSendToTop()` since `feedKey` hash changes with bookmark set.

**File:** `desktopApp/.../ui/BookmarksScreen.kt`

##### 2d. ReadsScreen

- `DesktopFeedViewModel(DesktopReadsFeedFilter(cache), cache)`
- **Card rendering:** ReadsScreen needs `LongFormNoteCard` that extracts title/summary/topics from `Note.event as? LongTextNoteEvent`. Separate composable, not FeedNoteCard.

**File:** `desktopApp/.../ui/ReadsScreen.kt`

##### 2e. NotificationsScreen

- `DesktopNotificationFeedFilter` returns `List<Note>`. NotificationsScreen transforms to typed `NotificationItem` in screen layer:
  ```kotlin
  fun Note.toNotificationItem(): NotificationItem? = when (event) {
      is ReactionEvent -> NotificationItem.Reaction(this)
      is LnZapEvent -> NotificationItem.Zap(this)
      is TextNoteEvent -> if (isReply) NotificationItem.Reply(this) else NotificationItem.Mention(this)
      else -> null
  }
  ```

**File:** `desktopApp/.../ui/NotificationsScreen.kt`

##### 2f. SearchScreen (Cache-Backed + AdvancedSearchBarState)

- **AdvancedSearchBarState stays** — handles query management, history, operator hints
- NIP-50 relay results route through `coordinator.consumeEvent()` → cache
- `DesktopFeedViewModel(DesktopSearchFeedFilter(cache, query), cache)` reads cached results
- AdvancedSearchBarState triggers relay subscription via Coordinator; ViewModel reads cache
- Search results persist across navigation (in cache)

**File:** `desktopApp/.../ui/SearchScreen.kt`

##### Phase 2 Acceptance Criteria

- [ ] All screens use DesktopFeedViewModel (including Search)
- [ ] Each screen has at most ONE subscription request to Coordinator
- [ ] Every screen has `DisposableEffect` cleanup for ViewModel + Coordinator subscription
- [ ] UserProfileScreen: separate header state from feed ViewModel
- [ ] BookmarksScreen: bookmark changes propagate to ViewModel
- [ ] ReadsScreen: uses LongFormNoteCard for kind 30023
- [ ] NotificationsScreen: Note → NotificationItem transformation in screen layer
- [ ] SearchScreen: NIP-50 results go through cache, AdvancedSearchBarState retained
- [ ] All screens show cached data instantly on navigation

---

#### Phase 3: Coordinator Subscription Consolidation

Ensure clean subscription lifecycle. No screen calls `rememberSubscription()`.

##### 3a. Always-alive subscriptions

| Subscription | Kind(s) | Trigger | Lifecycle |
|--------------|---------|---------|-----------|
| Contact list | 3 | App start | Permanent |
| Home feed (following) | 1 | App start | Permanent |
| Metadata (rate-limited) | 0 | On-demand via `loadMetadataForPubkeys()` | Permanent (rate limiter) |

##### 3b. Screen-triggered subscriptions (one per screen)

| Subscription | Kind(s) | Trigger | Lifecycle |
|--------------|---------|---------|-----------|
| Global feed | 1 | Global mode active | Active while mode shown |
| Profile feed + interactions | 1, 7, 9735, 6 | ProfileScreen visible | Active while screen visible |
| Thread + interactions | 1, 7, 9735, 6 | ThreadScreen visible | Active while screen visible |
| Reads | 30023 | ReadsScreen visible | Active while screen visible |
| Notifications | 1, 7, 9735 | NotificationsScreen visible | Active while screen visible |
| Search (NIP-50) | varies | Search query submitted | Active while search active |
| Home interactions | 7, 9735, 6 | FeedScreen visible | Active while screen visible |

Each screen requests ONE consolidated subscription from Coordinator that covers all event types it needs.

##### 3c. Subscription health tracking

```kotlin
// On Coordinator
private val _subscriptionHealth = MutableStateFlow<Map<String, SubscriptionHealth>>(emptyMap())
val subscriptionHealth: StateFlow<Map<String, SubscriptionHealth>>

// Updated in subscription callbacks
onEvent = { event, _, relay, _ ->
    consumeEvent(event, relay)
    updateHealth(subId, lastEventReceivedAt = System.currentTimeMillis())
}
onEose = { _, _ ->
    updateHealth(subId, eoseReceived = true)
}
```

##### Phase 3 Acceptance Criteria

- [ ] No screen calls `rememberSubscription()` directly
- [ ] Each screen has exactly ONE subscription request to Coordinator
- [ ] Coordinator manages all subscription lifecycles via `ConcurrentHashMap<String, Job>`
- [ ] `subscriptionHealth` StateFlow available for UI consumption

---

#### Phase 4: Relay Health UI

##### 4a. Health indicator component

```kotlin
@Composable
fun RelayHealthIndicator(
    lastEventReceivedAt: Long?,
    modifier: Modifier = Modifier,
) {
    val elapsed = /* current time - lastEventReceivedAt */
    if (elapsed > 30_000) {  // only show if > 30s
        Text(
            text = formatElapsed(elapsed),  // "45s ago", "3m ago"
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

**Display rules:**
- `< 30s` → hidden (healthy)
- `30s - 60s` → show seconds
- `> 1 min` → show minutes — indicates relay slowness or offline

**Research Insight: Place health indicator in NavigationRail** (next to existing `BunkerHeartbeatIndicator` at `SinglePaneLayout.kt:148`). This is the established pattern. Also add per-feed indicator in each screen's header.

##### 4b. Pull-to-refresh

Desktop doesn't have swipe gestures. Options:
- Refresh button in feed header (already exists)
- Keyboard shortcut (Cmd+R / Ctrl+R) via `MenuBar`
- Click on health indicator to force refresh

Refresh action: Coordinator closes + reopens relevant subscription with `since` filter.

**Research Insight: Wire Cmd+R from MenuBar to active screen** via `onRefresh` callback hoisted through `SinglePaneLayout` → `RootContent` → active screen (same pattern as existing `onShowComposeDialog`).

##### Phase 4 Acceptance Criteria

- [ ] Relay health indicator shown when last event > 30s ago
- [ ] Health indicator in NavigationRail (global) + per-feed header (screen-specific)
- [ ] Refresh button triggers subscription reopen
- [ ] Cmd+R / Ctrl+R keyboard shortcut works
- [ ] Health indicator clears on new event received

## System-Wide Impact

### Interaction Graph

```
User navigates to screen
  → Screen creates DesktopFeedViewModel(filter, cache)
  → DisposableEffect registers cleanup (viewModel.clear(), coordinator.release())
    → ViewModel.init: refreshSuspended() queries cache via filter
    → ViewModel.init: collects cache.eventStream.newEventBundles
      → FeedContentState.updateFeedWith(newNotes)
        → AdditiveFeedFilter.applyFilter() + sort()
          → FeedState.Loaded emits ImmutableList<Note>

Relay event arrives (WebSocket push)
  → Coordinator.consumeEvent(event, relay) [try-catch]
    → DesktopLocalCache.consume(event, relay) [kind registry dispatch]
      → Note.loadEvent() + Note.addReply/addReaction/addZap
        → Note.flowSet?.reactions?.invalidateData()
    → eventBundler.invalidateList(note)
      → (250ms batch)
      → localCache.eventStream.emitNewNotes(batch)
        → All active FeedViewModels receive bundle
          → Each filter decides if relevant
            → UI updates via NoteInteractionSnapshot

User leaves screen
  → DisposableEffect.onDispose fires
    → viewModel.clear() cancels viewModelScope
    → coordinator.releaseInteractions(subId) closes subscription
    → note.clearFlow() releases NoteFlowSet
```

### Error Propagation

- **WebSocket disconnect:** Coordinator detects → health indicator shows elapsed → reconnect with `since` → events resume → health clears
- **Cache consume fails:** try-catch in `consumeEvent()` logs and continues. SupervisorJob ensures other subscriptions are unaffected.
- **Filter throws:** `FeedContentState` catches → `FeedState.FeedError` → UI shows error state
- **ViewModel scope leak:** Prevented by `DisposableEffect(viewModel) { onDispose { viewModel.clear() } }`

### State Lifecycle Risks

- **Note eviction:** If cache evicts a Note in a ViewModel's feed, `ImmutableList<Note>` still holds a reference. No crash, but Note won't receive flowSet updates. Acceptable at 50k limit.
- **Note.flowSet leak:** Prevented by `DisposableEffect` calling `note.clearFlow()` when FeedNoteCard leaves composition.
- **ViewModel recreation on mode switch:** Old scope cancelled via `DisposableEffect`, new one created. 250ms gap caught on next `refreshSuspended()`.
- **Note mutation thread safety:** `Note.replies`, `Note.reactions` etc. use immutable list swap (reference assignment is atomic on JVM). Last-write-wins race is possible under contention but self-corrects on next event. This matches Android's pattern.

## Acceptance Criteria

### Functional

- [ ] All feed screens render from cache via DesktopFeedViewModel
- [ ] Navigation between screens shows cached data instantly (<100ms)
- [ ] Zap/reaction/reply/repost counts update live via NoteInteractionSnapshot
- [ ] Feed mode switch (Following ↔ Global) works without reload delay
- [ ] New events from relays appear in feeds within 250ms (batch delay)
- [ ] Contact list always cached, Following feed hydrates instantly
- [ ] Search results persist across navigation (cached)

### Non-Functional

- [ ] No `EventCollectionState` usage in any feed screen
- [ ] No `rememberSubscription()` in any feed screen
- [ ] Each screen has at most ONE subscription request to Coordinator
- [ ] Every screen has `DisposableEffect` cleanup for ViewModel + subscription + flowSet
- [ ] Coordinator uses `SupervisorJob` — one failed event doesn't kill all processing
- [ ] `consumeEvent()` has try-catch — no unhandled exceptions
- [ ] `./gradlew spotlessApply :desktopApp:compileKotlin` passes after each phase

## Dependencies & Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| `ViewModel.clear()` not accessible in lifecycle KMP | Medium | High | Add custom `destroy()` method with own `CoroutineScope` |
| Note.flowSet observation doesn't trigger recomposition | Low | High | Prototype `rememberNoteInteractionState()` early in Phase 1d |
| Missing consume() methods cause blank feeds | Low | High | Add all in Phase 1a before screen migration |
| Always-alive subs overwhelm relays | Low | Medium | Start with contact list + home feed only |
| SearchScreen cache filter semantics differ from NIP-50 | Low | Medium | Search filter only matches cached results; NIP-50 provides the actual search |
| BoundedLargeCache eviction is pseudo-random (by hex key order) | Low | Low | At 50k limit, probability of evicting active feed Note is negligible |

## Success Metrics

- Back navigation: 2-5s → <100ms
- Metadata refetch on navigation: always → never (cached)
- Zap/reaction counts on back nav: lost → preserved
- Subscriptions per screen: 4-8 → 1
- Coroutine scope leaks: possible → prevented by DisposableEffect

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-03-22-clean-cache-architecture-brainstorm.md](docs/brainstorms/2026-03-22-clean-cache-architecture-brainstorm.md) — Key decisions: cache as single source of truth, Coordinator owns subscriptions (always alive), relay health indicator, counts on Note model, SearchScreen migrates with AdvancedSearchBarState retained

### Internal References

- Prior cache plan: `docs/plans/2026-03-18-feat-desktop-cache-navigation-persistence-plan.md`
- Relay subscription stability: `docs/brainstorms/2026-03-09-feedscreen-relay-subscription-strategy-brainstorm.md`
- Cache architecture rationale: `docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md`
- FeedContentState: `commons/.../ui/feeds/FeedContentState.kt`
- FeedViewModel: `commons/.../viewmodels/FeedViewModel.kt`
- DesktopFeedFilters: `desktopApp/.../feeds/DesktopFeedFilters.kt`
- DesktopFeedViewModel: `desktopApp/.../viewmodels/DesktopFeedViewModel.kt`
- Note model: `commons/.../model/Note.kt`
- DesktopLocalCache: `desktopApp/.../cache/DesktopLocalCache.kt`
- Coordinator: `desktopApp/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt`
- SinglePaneLayout: `desktopApp/.../ui/deck/SinglePaneLayout.kt` (NavigationRail, BunkerHeartbeatIndicator pattern)

### Research Insights Applied

- **kotlin-coroutines:** SupervisorJob for Coordinator, DisposableEffect for ViewModel lifecycle, try-catch in consume pipeline, Job tracking for screen subscriptions
- **compose-expert:** NoteInteractionSnapshot pattern, `remember(note) { note.flow() }`, DisposableEffect for clearFlow(), key-based LazyColumn skip optimization
- **desktop-expert:** ViewModel.clear() accessibility concern, MenuBar keyboard shortcut wiring, NavigationRail health indicator placement, overlay subscription cleanup
- **kotlin-expert:** Kind-based registry for consume(), MutableStateFlow for followedUsers, Note mutation thread safety analysis, BoundedLargeCache eviction characteristics
