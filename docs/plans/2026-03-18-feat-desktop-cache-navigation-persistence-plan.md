---
title: "feat: Desktop Cache-Centric Architecture for Navigation Persistence"
type: feat
status: active
date: 2026-03-18
origin: docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md
---

# feat: Desktop Cache-Centric Architecture for Navigation Persistence

## Overview

Migrate Amethyst Desktop from per-screen event state (`EventCollectionState` in `remember {}`) to Android's cache-centric pattern where `DesktopLocalCache` is the single source of truth. Feeds query the cache via `FeedFilter`, `FeedViewModel` subscribes to the cache event stream, and data survives navigation.

Three-phase incremental migration. Each phase is a standalone PR that improves UX.

## Problem Statement

| Symptom | Root Cause |
|---------|------------|
| Back navigation shows loading spinners | `EventCollectionState` destroyed when composable leaves composition |
| Metadata re-fetched per screen | Screens call `loadMetadataForPubkeys()` instead of reading cache |
| Zap/reaction counts lost on navigate | Tracked in per-screen mutable state, not in `Note` model |
| Search results vanish on thread → back | Search screen's `EventCollectionState` is gone |
| Wasted network/relay resources | Same events re-fetched on every navigation; duplicate REQ filters |

## Proposed Solution

Mirror Android Amethyst's architecture (see brainstorm: `docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md`):

```
Relays ──→ DesktopLocalCache.consume(event)
               │
               ├──→ Store in maps (users, notes, addressableNotes)
               ├──→ Update Note relationships (replies, reactions, zaps)
               └──→ DesktopCacheEventStream.emitNewNotes(note)
                         │
                         ▼
               FeedViewModel.collect { newNotes →
                   feedState.updateFeedWith(newNotes)
               }
                         │
                         ▼
               Screen observes feedState.feedContent (Loading/Loaded/Empty)
```

## Technical Approach

### Phase 1: Store ALL Events in DesktopLocalCache

**Goal:** Make `DesktopLocalCache` the source of truth.

**Branch:** `feat/desktop-cache-phase1`

#### 1.1 Switch cache backing to `LargeCache` with size enforcement

**File:** `desktopApp/.../cache/DesktopLocalCache.kt`

Replace `ConcurrentHashMap` with `LargeCache` from quartz — same backing store Android uses (`ConcurrentSkipListMap` on JVM), lock-free reads (CAS-based), rich query APIs (`filterIntoSet`, `mapNotNull`, range queries). Desktop keeps strong references (no `WeakReference` wrapper — that's Android-only `LargeSoftCache` for mobile memory pressure).

`LargeCache` chosen over `LruCache` because:
- **Lock-free reads** — `ConcurrentSkipListMap` vs `synchronized` on every `get()`. Critical at 1000 events/sec.
- **Rich query API** — `filterIntoSet`, `mapNotNull` match Android's filter patterns exactly.
- **No snapshot overhead** — `LruCache.snapshot()` copies the entire map; `LargeCache` iterates in-place.

Add size enforcement via a `BoundedLargeCache` wrapper that checks size on `put()` and evicts oldest entries when cap is exceeded:

```kotlin
class BoundedLargeCache<K : Comparable<K>, V>(
    private val maxSize: Int,
    private val evictPercent: Float = 0.1f, // Remove 10% when cap hit
) {
    private val inner = LargeCache<K, V>()

    fun get(key: K): V? = inner.get(key)
    fun put(key: K, value: V) {
        inner.put(key, value)
        enforceSize()
    }
    fun getOrCreate(key: K, builder: (K) -> V): V = inner.getOrCreate(key, builder).also { enforceSize() }
    fun remove(key: K): V? = inner.remove(key)
    fun clear() = inner.clear()
    fun size(): Int = inner.size()
    fun values(): Iterable<V> = inner.values()
    fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> = inner.filterIntoSet(consumer)
    // ... delegate other LargeCache methods as needed

    private fun enforceSize() {
        if (inner.size() > maxSize) {
            val toRemove = (maxSize * evictPercent).toInt().coerceAtLeast(1)
            // ConcurrentSkipListMap keys are sorted — first N keys are "oldest" by insertion order
            val keys = inner.keys().take(toRemove)
            keys.forEach { inner.remove(it) }
        }
    }
}

// Usage:
private val notes = BoundedLargeCache<HexKey, Note>(MAX_NOTES)
private val users = BoundedLargeCache<HexKey, User>(MAX_USERS)
private val addressableNotes = BoundedLargeCache<String, AddressableNote>(MAX_ADDRESSABLE)

companion object {
    const val MAX_NOTES = 50_000    // ~100-150MB at ~2-3KB/note
    const val MAX_USERS = 25_000    // ~25-50MB at ~1-2KB/user
    const val MAX_ADDRESSABLE = 10_000
}
```

Note: `ConcurrentSkipListMap` keys are sorted, so `keys().take(N)` removes the lexicographically smallest hex keys — not strictly "oldest by time." For true time-based eviction, the `enforceSize()` could sort by `note.event?.createdAt` instead, but the simple key-based approach is cheaper and good enough (hex keys from Nostr events are effectively random, so eviction is approximately random).

#### 1.2 Port consume methods from Android LocalCache

**File:** `desktopApp/.../cache/DesktopLocalCache.kt`

Start with 4 new event kinds (kind 9734 required for zap processing). Add remaining kinds per-screen in Phase 3.

| Kind | Event Type | Phase | Notes |
|------|-----------|-------|-------|
| 0 | `MetadataEvent` | Done | Already exists (`consumeMetadata`) |
| 1 | `TextNoteEvent` | 1 | Core feed content |
| 7 | `ReactionEvent` | 1 | Reaction counts on Note |
| 9734 | `LnZapRequestEvent` | 1 | Required before kind 9735 can process |
| 9735 | `LnZapEvent` | 1 | Zap counts on Note |

Each consume method follows Android's pattern. Use `event.tagsWithoutCitations()` for reply parsing (handles both NIP-10 marked and legacy positional tags, excluding inline nostr: citations):

```kotlin
fun consume(event: TextNoteEvent, relay: NormalizedRelayUrl?): Boolean {
    val note = checkGetOrCreateNote(event.id) ?: return false
    if (note.event != null) return false // already have it
    val author = getOrCreateUser(event.pubKey)
    val repliesTo = event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
    note.loadEvent(event, author, repliesTo)
    repliesTo.forEach { it.addReply(note) }
    refreshObservers(note)
    return true
}
```

For reactions, check both `e`-tags and `a`-tags (reactions to addressable events like articles):
```kotlin
fun consume(event: ReactionEvent, relay: NormalizedRelayUrl?): Boolean {
    val note = checkGetOrCreateNote(event.id) ?: return false
    if (note.event != null) return false
    val author = getOrCreateUser(event.pubKey)
    val reactedTo = event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
        event.taggedAddresses().map { getOrCreateAddressableNote(it) }
    note.loadEvent(event, author, reactedTo)
    reactedTo.forEach { it.addReaction(note) }
    refreshObservers(note)
    return true
}
```

#### 1.3 Bridge relay callbacks to cache consumption

**Problem:** Relay `onEvent` callbacks are non-suspend. `emitNewNotes()` is suspend.

**Solution:** Use `BasicBundledInsert` from commons (already exists, battle-tested in Android) with 250ms delay (snappier than Android's 1000ms — desktop has mains power, users expect faster updates).

**File:** `desktopApp/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt`

```kotlin
private val eventBundler = BasicBundledInsert<Note>(
    delay = 250,  // 250ms for desktop (Android uses 1000ms to save battery)
    dispatcher = Dispatchers.IO,
    scope = scope,
)

fun consumeEvent(event: Event, relay: NormalizedRelayUrl?) {
    scope.launch(Dispatchers.IO) {
        val consumed = localCache.consume(event, relay)
        if (consumed) {
            val note = localCache.getNoteIfExists(event.id) as? Note ?: return@launch
            eventBundler.invalidateList(note) { batch ->
                localCache.eventStream.emitNewNotes(batch)
            }
        }
    }
}
```

Keep per-screen `rememberSubscription` — just change the `onEvent` callback to route through cache. Per-screen subscriptions handle lifecycle automatically (auto-cleanup on navigate away).

```kotlin
// Before (per-screen state):
onEvent = { event, _, _, _ -> eventState.addItem(event) }

// After (routes to cache):
onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) }
```

#### 1.4 Fix SharedFlow configuration

**Problem:** `MutableSharedFlow(replay = 0)` with no buffer drops events and blocks emitters on slow collectors. Android uses `extraBufferCapacity=100, DROP_OLDEST`.

**File:** `desktopApp/.../cache/DesktopLocalCache.kt` (DesktopCacheEventStream)

```kotlin
class DesktopCacheEventStream : ICacheEventStream {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _deletedEventBundles = MutableSharedFlow<Set<Note>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    // ...
}
```

Dropped emissions are fine — events are already in the cache. The flow signals "something changed," not "here is the data."

#### 1.5 Set JVM memory limit

**File:** `desktopApp/build.gradle.kts`

```kotlin
compose.desktop.application {
    jvmArgs += "-Xmx2g"
}
```

Size enforcement is handled by `BoundedLargeCache` (section 1.1). `-Xmx2g` is a safety net for the JVM heap overall.

**Eviction safety:** Feed lists hold strong references to `Note` objects. When `BoundedLargeCache` evicts a key, the `Note` object survives in the feed list (GC won't collect it). On next `FeedFilter.feed()` refresh, the evicted note won't appear — acceptable (feed shows most recent N items). If a user clicks an evicted note, `checkGetOrCreateNote(id)` creates a shell Note that triggers a relay re-fetch.

#### 1.6 Add cache clear on logout

**File:** `desktopApp/.../account/AccountManager.kt`

Cancel coordinator scope BEFORE clearing cache to prevent race between in-flight `consume()` calls and `clear()`.

```kotlin
fun logout() {
    coordinator.clear() // Stop subscriptions first
    localCache.clear()  // Then clear cache
}
```

#### Phase 1 Acceptance Criteria

- [ ] `DesktopLocalCache` backed by `BoundedLargeCache` with size caps (50k notes, 25k users, 10k addressable)
- [ ] Consume methods for kinds 1, 7, 9734, 9735
- [ ] Relay `onEvent` callbacks route through `coordinator.consumeEvent()`
- [ ] `BasicBundledInsert(250ms)` batches events before `emitNewNotes()`
- [ ] `DesktopCacheEventStream` has `extraBufferCapacity = 64, DROP_OLDEST`
- [ ] `-Xmx2g` JVM arg set
- [ ] Cache cleared on logout (coordinator first, then cache)
- [ ] `createGlobalFeedSubscription` / `createFollowingFeedSubscription` confirmed routing through `consumeEvent`
- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] `./gradlew spotlessApply` clean

---

### Phase 2: Create Desktop FeedFilters

**Goal:** Query cache instead of holding per-screen event lists.

**Branch:** `feat/desktop-cache-phase2`

#### 2.1 Desktop feed filters

**Directory:** `desktopApp/.../feeds/`

| Filter | Query Strategy | Base Class | Limit |
|--------|---------------|------------|-------|
| `DesktopGlobalFeedFilter` | `notes.filterIntoSet { kind == 1 }` | `AdditiveFeedFilter<Note>` | 2500 |
| `DesktopFollowingFeedFilter` | Same + author in account's follow list | `AdditiveFeedFilter<Note>` | 2500 |
| `DesktopThreadFilter(noteId)` | Root note + `Note.replies` graph walk | `FeedFilter<Note>` | unlimited |
| `DesktopProfileFeedFilter(pubkey)` | `notes.filterIntoSet { author == pubkey }` | `AdditiveFeedFilter<Note>` | 1000 |
| `DesktopBookmarkFeedFilter(ids)` | Direct lookup by ID set | `FeedFilter<Note>` | 2500 |
| `DesktopReadsFeedFilter` | `notes.filterIntoSet { kind == 30023 }` | `AdditiveFeedFilter<Note>` | 500 |
| `DesktopNotificationFeedFilter` | Events tagging logged-in user (reactions, zaps, replies, reposts) | `AdditiveFeedFilter<Note>` | 2500 |
| `DesktopSearchFeedFilter(query)` | Cache search + relay search results stored in cache | `AdditiveFeedFilter<Note>` | 500 |

Limits are ~5x Android's (desktop has more screen space and RAM). Filters use `BoundedLargeCache.filterIntoSet` — same API as Android's `LocalCache`.

The initial `feed()` scan runs only once on first load or `feedKey()` change. After that, `updateListWith()` uses `applyFilter()` + `sort()` incrementally — O(batch_size) not O(cache_size).

Following filter reads followed pubkeys from account state (same pattern as Android's `HomeConversationsFeedFilter` which reads `account.liveHomeFollowLists`).

Notification filter mirrors Android's `NotificationFeedFilter`: filters for events that tag the logged-in user across relevant kinds (kind 1, 6, 7, 9735), with mute list support. Simplified from Android's 20+ kinds to the kinds desktop actually displays.

```kotlin
class DesktopGlobalFeedFilter(
    private val cache: DesktopLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feed(): List<Note> =
        cache.notes.filterIntoSet { _, note -> note.event?.kind == 1 }
            .sortedByDescending { it.event?.createdAt ?: 0 }
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> =
        newItems.filter { it.event?.kind == 1 }.toSet()

    override fun sort(items: Set<Note>): List<Note> =
        items.sortedByDescending { it.event?.createdAt ?: 0 }.take(limit())

    override fun limit(): Int = 2500
}
```

#### Phase 2 Acceptance Criteria

- [ ] All 8 feed filters implemented and compile
- [ ] `applyFilter()` and `sort()` work for incremental updates
- [ ] Notification filter correctly identifies events tagging logged-in user
- [ ] Following filter reads follow list from account state
- [ ] Unit tests for each filter with mock cache data
- [ ] `./gradlew :desktopApp:compileKotlin` passes

---

### Phase 3: Migrate Screens to FeedViewModel

**Goal:** Replace `EventCollectionState` with `FeedViewModel` pattern across all screens.

**Branch:** `feat/desktop-cache-phase3`

#### 3.1 Create DesktopFeedViewModel

`FeedViewModel.init` in commons only sets up stream collectors — it doesn't load existing cache data. Navigation back would show `Loading` forever until a new relay event arrives. Fix by calling `refreshSuspended()` on init. Consider upstreaming to base `FeedViewModel` in commons.

**File:** `desktopApp/.../viewmodels/DesktopFeedViewModel.kt`

```kotlin
class DesktopFeedViewModel(
    filter: FeedFilter<Note>,
    cacheProvider: ICacheProvider,
) : FeedViewModel(filter, cacheProvider) {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            feedState.refreshSuspended()
        }
    }
}
```

#### 3.2 ViewModel lifecycle management

**Singleton feeds** — created in `Main.kt` alongside other app-level state (`relayManager`, `localCache`, `accountState`). Standard Kotlin JVM pattern: create at app startup, pass down as parameters.

**File:** `desktopApp/.../Main.kt`

```kotlin
// App-level state (created once)
val localCache = DesktopLocalCache()
val coordinator = DesktopRelaySubscriptionsCoordinator(localCache, ...)

// Singleton ViewModels (created once, survive navigation)
val globalFeedVM = DesktopFeedViewModel(DesktopGlobalFeedFilter(localCache), localCache)
val followingFeedVM = DesktopFeedViewModel(DesktopFollowingFeedFilter(localCache, account), localCache)
val readsFeedVM = DesktopFeedViewModel(DesktopReadsFeedFilter(localCache), localCache)
val notificationsFeedVM = DesktopFeedViewModel(DesktopNotificationFeedFilter(localCache, account), localCache)
```

Passed to screens as parameters (not CompositionLocal).

**Parameterized feeds** — use `remember(key)` in Compose. Data survives navigation via the cache, not the ViewModel. New VMs query cache on creation via `init { refreshSuspended() }` for instant results.

```kotlin
@Composable
fun ThreadScreen(noteId: String, cache: DesktopLocalCache, ...) {
    val vm = remember(noteId) {
        DesktopFeedViewModel(DesktopThreadFilter(noteId, cache), cache)
    }
    DisposableEffect(vm) {
        onDispose { vm.clear() } // Cancel viewModelScope
    }
    // ...
}
```

#### 3.3 Per-screen subscriptions (unchanged)

Keep `rememberSubscription` in screens — it handles lifecycle automatically. Just change callbacks to route through cache.

```kotlin
rememberSubscription(configuredRelays, feedMode, followedUsers, relayManager = relayManager) {
    when (feedMode) {
        FeedMode.GLOBAL -> createGlobalFeedSubscription(
            relays = configuredRelays,
            onEvent = { event, _, relay, _ ->
                coordinator.consumeEvent(event, relay)
            },
            onEose = { _, _ -> eoseReceivedCount++ },
        )
        // ...
    }
}
```

#### 3.4 Migrate screens with per-screen consume methods

**Migration order and consume methods needed per screen:**

| Screen | VM Type | Consume Kinds to Add | Notes |
|--------|---------|---------------------|-------|
| FeedScreen | Singleton (global + following) | 3 (ContactList), 6 (Repost) | Includes FeedNoteCard rewrite |
| ThreadScreen | Parameterized (noteId) | 5 (Deletion) | Deleted note indicators |
| UserProfileScreen | Parameterized (pubkey) | — | Uses existing kinds |
| SearchResultsList | Parameterized (query) | — | Uses DesktopSearchFeedFilter |
| BookmarksScreen | Singleton | 30078 (BookmarkList) | Bookmark state from events |
| ReadsScreen | Singleton | 30023 (LongTextNote) | Articles feed |
| NotificationsScreen | Singleton | — | Uses DesktopNotificationFeedFilter |

**FeedNoteCard rewrite** — done alongside FeedScreen migration (first screen). All subsequent screen migrations benefit.

Current `FeedNoteCard` takes raw `Event` + per-screen counts:
```kotlin
// CURRENT: 6 per-screen state params
FeedNoteCard(event, ..., zapReceipts, reactionCount, replyCount, repostCount, ...)
```

New `FeedNoteCard` takes `Note` from cache — reads counts directly from model:
```kotlin
// NEW: Note replaces all per-screen count params
FeedNoteCard(note, ...) // inside: note.zaps.size, note.countReactions(), note.replies.size, note.boosts.size
```

**Field mapping (per-screen state → Note model):**

| Per-Screen State Map | Note Model Replacement |
|---------------------|----------------------|
| `zapsByEvent[id]` → `List<ZapReceipt>` | `note.zaps` → `Map<Note, Note?>` |
| `zapReceipts.sumOf { it.amountSats }` | `note.zapsAmount` (BigDecimal) |
| `reactionIdsByEvent[id]` → count | `note.countReactions()` |
| `replyIdsByEvent[id]` → count | `note.replies.size` |
| `repostIdsByEvent[id]` → count | `note.boosts.size` |

**FeedScreen subscriptions removed** (5 subscriptions, ~130 lines):
- `createZapsSubscription` + `zapsByEvent` state map
- `createReactionsSubscription` + `reactionIdsByEvent` state map
- `createRepliesSubscription` + `replyIdsByEvent` state map
- `createRepostsSubscription` + `repostIdsByEvent` state map
- `createBatchMetadataSubscription` for zap senders

These are replaced by `cache.consume()` which populates Note model relationships automatically.

**Per-screen migration removes:**
- `val eventState = remember { EventCollectionState<Event>(...) }`
- Per-screen `zapsByEvent`, `reactionIdsByEvent`, `replyIdsByEvent`, `repostIdsByEvent` mutable state maps
- 5 per-screen subscription handlers (zaps, reactions, replies, reposts, metadata)

**Per-screen migration adds:**
- `val feedState by viewModel.feedState.feedContent.collectAsState()`
- Route `onEvent` to `coordinator.consumeEvent()`
- Always use `key` in `items()`: `items(notes.list, key = { it.idHex })`

```kotlin
when (val state = feedState) {
    is FeedState.Loading -> LoadingState("Loading notes...")
    is FeedState.Empty -> EmptyState(...)
    is FeedState.Loaded -> {
        val notes by state.feed.collectAsState()
        LazyColumn {
            items(notes.list, key = { it.idHex }) { note ->
                FeedNoteCard(note = note, ...)
            }
        }
    }
    is FeedState.FeedError -> ErrorState(state.errorMessage)
}
```

#### Phase 3 Acceptance Criteria

- [ ] `DesktopFeedViewModel` loads cache data on creation (initial `refreshSuspended()`)
- [ ] Singleton ViewModels created in `Main.kt` for global/following/reads/notifications
- [ ] `remember(key)` + `DisposableEffect` for parameterized feeds (thread, profile, search)
- [ ] `FeedNoteCard` rewritten to read from `Note` model (done with FeedScreen migration)
- [ ] Per-screen subscriptions route through `consumeEvent()`
- [ ] Consume methods added for kinds 3, 5, 6, 30023, 30078
- [ ] All 9 screens migrated: Feed, Thread, Profile, Search, Bookmarks, Reads, Notifications
- [ ] Per-screen zap/reaction/reply state removed (stored in Note model)
- [ ] Navigation back shows instant data (no loading spinner)
- [ ] `./gradlew :desktopApp:compileKotlin` passes
- [ ] `./gradlew spotlessApply` clean
- [ ] Manual test: Feed → Thread → Back → data persists
- [ ] Manual test: Search → Thread → Back → search results preserved

---

## System-Wide Impact

### Interaction Graph

```
User navigates to Feed
  → FeedScreen reads globalFeedViewModel.feedState
    → FeedContentState queries DesktopGlobalFeedFilter.feed()
      → Filter calls cache.notes.filterIntoSet { kind==1 }
        → Returns cached Note objects

Relay sends new event
  → OkHttp callback → coordinator.consumeEvent(event, relay)
    → scope.launch(IO) { localCache.consume(event) }
      → Note created/updated in cache
      → BasicBundledInsert(250ms) batches notes
        → eventStream.emitNewNotes(batch)
          → FeedViewModel.collect { feedState.updateFeedWith(notes) }
            → Compose recomposes

User navigates away and back
  → Singleton VM: feedState already Loaded → instant render
  → Parameterized VM: new VM created, init { refreshSuspended() } loads from cache → instant render
```

### Error Propagation

| Error | Source | Handling |
|-------|--------|----------|
| Relay disconnect | OkHttp | Coordinator reconnects, no cache impact |
| consume() throws | Cache | Caught in consumer coroutine, logged, event skipped |
| emitNewNotes() buffer full | SharedFlow | `DROP_OLDEST` — events already in cache |
| Filter query on empty cache | FeedFilter | Returns empty list → `FeedState.Empty` |

### State Lifecycle Risks

| Risk | Mitigation |
|------|-----------|
| Stale data after logout | `coordinator.clear()` then `localCache.clear()` |
| Mixed-account data | ViewModels cleared + cache cleared on account switch |
| Subscription leak on app exit | Coordinator.clear() in shutdown hook |
| Cache memory pressure | `-Xmx2g` + BoundedLargeCache caps (50k notes, 25k users) |

## Dependencies & Prerequisites

| Dependency | Status | Needed For |
|------------|--------|------------|
| `LargeCache` (quartz) | ✅ In quartz jvmAndroid — `ConcurrentSkipListMap`, lock-free | Phase 1 |
| `BasicBundledInsert` (commons) | ✅ In commons `BundledUpdate.kt` | Phase 1 |
| `ICacheProvider` / `ICacheEventStream` | ✅ In commons | Phase 1 |
| `Note.loadEvent()`, `addReply()`, `addReaction()`, `addZap()` | ✅ In commons | Phase 1 |
| `FeedFilter<T>` / `AdditiveFeedFilter<T>` | ✅ In commons | Phase 2 |
| `FeedViewModel` / `FeedContentState` | ✅ In commons | Phase 3 |
| `kotlinx-coroutines-swing` | ✅ In desktopApp deps | Dispatchers.Main |

## Success Metrics

| Metric | Before | After |
|--------|--------|-------|
| Back navigation time | 2-5s (full reload) | <100ms (cache hit) |
| Metadata re-fetch on navigate | Every screen | Never (cached) |
| Zap/reaction counts on back | Lost | Preserved |

## Future Improvements (Deferred)

| Improvement | Trigger |
|-------------|---------|
| Secondary indexes (by kind, by author) | `feed()` scan >50ms |
| Disk persistence (SQLite) | User requests cross-session persistence |
| Centralized subscription coordinator | Multiple screens need same relay filter |

## Sources & References

- **Origin:** [docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md](docs/brainstorms/2026-03-17-desktop-cache-architecture-brainstorm.md)
- `commons/.../ui/feeds/FeedFilter.kt`, `AdditiveFeedFilter.kt`, `FeedContentState.kt`
- `commons/.../viewmodels/FeedViewModel.kt`
- `commons/.../model/cache/ICacheProvider.kt`, `ICacheEventStream.kt`
- `commons/.../model/Note.kt` — `loadEvent()`, `addReply()`, reactions, zaps
- `desktopApp/.../cache/DesktopLocalCache.kt`
- `amethyst/.../model/LocalCache.kt` — Android reference
- `docs/brainstorms/2026-03-09-feedscreen-relay-subscription-strategy-brainstorm.md` — subscription stability
