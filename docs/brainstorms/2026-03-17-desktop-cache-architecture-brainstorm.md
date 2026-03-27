# Desktop Cache Architecture — Navigation Persistence

**Date:** 2026-03-17
**Status:** Brainstorm
**Branch:** `feat/desktop-media` (current), will need dedicated branch

## What We're Building

A cache-centric data architecture for Amethyst Desktop that mirrors Android Amethyst's pattern: `DesktopLocalCache` as the single source of truth, `FeedFilter` query objects, and `FeedViewModel` for reactive UI state. This ensures loaded data (notes, metadata, reactions, zaps) survives navigation between screens.

### The Problem

- Feed events live in per-screen `EventCollectionState` inside `remember {}` — destroyed on navigation
- Navigating search → thread → back loses all loaded notes and search results
- Metadata is re-fetched per-screen via `loadMetadataForPubkeys()` even though `DesktopLocalCache` already holds it
- Zaps, reactions, reply counts are tracked per-screen in mutable state — lost on navigation
- UX feels broken: back navigation shows loading spinners for already-seen data

### The Goal

| Before | After |
|--------|-------|
| Screen creates EventCollectionState in `remember` | Screen observes FeedViewModel backed by cache |
| Events stored per-screen, lost on navigation | Events stored in DesktopLocalCache singleton |
| Metadata re-fetched per screen | Metadata cached, available immediately |
| Back navigation = full reload | Back navigation = instant (data in cache) |

## Why This Approach

**Mirror Android Amethyst's cache-centric design** rather than inventing a new repository pattern:

1. **Proven pattern** — Android Amethyst handles millions of events this way
2. **Shared code** — `FeedFilter`, `FeedViewModel`, `FeedContentState` already exist in `commons/`
3. **Future merge safety** — staying aligned with upstream means less divergence
4. **Natural fit** — `DesktopLocalCache` already implements `ICacheProvider` and `ICacheEventStream`

### Android's Architecture (what we're mirroring)

```
Relays → LocalCache (stores ALL events) → ICacheEventStream
                                              ↓
                                    FeedViewModel subscribes
                                              ↓
                                    FeedFilter.feed() queries cache
                                              ↓
                                    FeedContentState (Loading/Loaded/Empty)
                                              ↓
                                    UI collects StateFlow
```

### Desktop's Current Architecture (broken)

```
Relays → Screen composable (EventCollectionState in remember)
              ↓
         UI renders from local state
              ↓
         [navigation] → state destroyed → reload from scratch
```

### Desktop's Target Architecture

```
Relays → DesktopLocalCache (stores ALL events) → DesktopCacheEventStream
                                                      ↓
                                            FeedViewModel subscribes
                                                      ↓
                                            FeedFilter queries cache
                                                      ↓
                                            Screen observes FeedContentState
                                                      ↓
                                            [navigation] → cache persists → instant back
```

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Cache-centric (mirror Android) | Proven, shared code, merge-safe |
| Migration | Incremental (3 phases) | Each phase is a standalone UX improvement |
| Storage | In-memory only (no disk) | Matches Android, sufficient for navigation persistence |
| Feed queries | FeedFilter pattern from commons | Already exists, well-tested |
| State management | FeedViewModel + FeedContentState | Already in commons, handles Loading/Loaded/Empty |
| Thumbnails | Out of scope | Already survive navigation (singleton cache) |

## Implementation Phases

### Phase 1: Store ALL events in DesktopLocalCache

**Goal:** Make the cache the source of truth instead of per-screen state.

**Changes:**
- `DesktopLocalCache`: Store note events (not just metadata) when received from relays
- Relay subscription handlers: Call `localCache.consume(event)` for ALL event types
- `DesktopCacheEventStream`: Emit to `newEventBundles` / `deletedEventBundles` flows
- Zaps, reactions, reposts: Store relationship data in Note model (like Android)

**What it fixes:** Data accumulates in a singleton — screens can query it on mount.

### Phase 2: Create Desktop FeedFilters

**Goal:** Query the cache instead of holding per-screen event lists.

**Changes:**
- `DesktopGlobalFeedFilter` — queries cache for kind 1 events, sorted by createdAt
- `DesktopFollowingFeedFilter` — queries cache for events from followed users
- `DesktopThreadFilter` — queries cache for root + replies to a note ID
- `DesktopProfileFeedFilter` — queries cache for events by a specific pubkey
- `DesktopBookmarkFeedFilter` — queries cache for bookmarked event IDs

**What it fixes:** Screens get data from cache immediately, no relay round-trip on back navigation.

### Phase 3: Migrate Screens to FeedViewModel

**Goal:** Replace per-screen `EventCollectionState` with shared `FeedViewModel`.

**Changes per screen:**
- Replace `val eventState = remember { EventCollectionState(...) }` with `val viewModel = remember { FeedViewModel(filter, localCache) }`
- Replace `events by eventState.items.collectAsState()` with `feedState by viewModel.feedContent.collectAsState()`
- Remove per-screen relay subscription handlers (cache handles it)
- Remove per-screen zap/reaction/reply tracking (stored in Note model)

**Migration order:** FeedScreen → ThreadScreen → UserProfileScreen → SearchResultsList → BookmarksScreen → ReadsScreen → NotificationsScreen

**What it fixes:** Full navigation persistence, cleaner screen composables, shared ViewModel pattern.

## Existing Code to Reuse

| Component | Location | Status |
|-----------|----------|--------|
| `ICacheProvider` | `commons/model/cache/ICacheProvider.kt` | ✅ Already implemented by DesktopLocalCache |
| `ICacheEventStream` | `commons/model/cache/ICacheEventStream.kt` | ✅ Already implemented by DesktopCacheEventStream |
| `FeedFilter<T>` | `commons/ui/feeds/FeedFilter.kt` | ✅ Ready to subclass |
| `AdditiveFeedFilter<T>` | `commons/ui/feeds/AdditiveFeedFilter.kt` | ✅ Optimized for incremental updates |
| `FeedViewModel` | `commons/viewmodels/FeedViewModel.kt` | ⚠️ May need adaptation for desktop lifecycle |
| `FeedContentState` | `commons/ui/feeds/FeedContentState.kt` | ✅ Ready to use |
| `User` / `Note` models | `commons/model/` | ✅ Already used by DesktopLocalCache |

## Resolved Questions

| Question | Decision | Rationale |
|----------|----------|-----------|
| **ViewModel lifecycle** | App-level singletons | Desktop has no Activity lifecycle. Create ViewModels at startup, keep alive forever. Simple and matches desktop mental model. |
| **Cache eviction** | LRU eviction | Cap cache per type (e.g., 10k notes, 5k users). Desktop has more RAM but still finite. Defensive choice. |
| **Subscription management** | Centralized coordinator | `DesktopRelaySubscriptionsCoordinator` manages all feed subs. Screens request what they need, coordinator deduplicates. Already partially exists. |

## Resolved Questions (continued)

| Question | Decision | Rationale |
|----------|----------|-----------|
| **Event consumption scope** | Full port of Android's consume methods | Future-proof. Port all event kind handlers from Android LocalCache to DesktopLocalCache. |
