---
name: feed-patterns
description: Feed composition and data-access layer patterns in Amethyst. Use when adding or modifying a feed (home, profile, hashtag, bookmarks, notifications, DMs, communities), working with the shared `FeedFilter` / `AdditiveFeedFilter` / `ChangesFlowFilter` / `FeedContentState` in `commons/.../ui/feeds/`, the Android-only `AdditiveComplexFeedFilter` / `FilterByListParams` in `amethyst/.../ui/dal/`, or extending the `FeedViewModel` family in `commons/.../viewmodels/`. Covers how feeds scan `LocalCache`, react to changes, apply ordering, and render through Compose.
---

# Feed Patterns

Amethyst's "feed" abstraction is: a `FeedFilter` that decides which notes belong in a list, plus a `FeedViewModel` that exposes the current state reactively to the UI. Every scrollable list — home, profile, hashtag, bookmarks, notifications, DMs — is a variant of this.

## When to Use This Skill

- Adding a new screen that shows a list of notes.
- Modifying an existing feed's filtering / ordering / inclusion rules.
- Investigating why a feed doesn't update after a mute/follow/bookmark change.
- Deciding whether to extend a ViewModel or write a new filter.
- Understanding the Android ⇄ Desktop sharing boundary for feeds.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ commons/.../viewmodels/  (shared, KMP)                      │
│   FeedViewModel  ◄── ListChangeFeedViewModel                │
│                 ◄── ChatroomFeedViewModel                   │
│                 ◄── MarmotGroupFeedViewModel                │
│                                                             │
│                                                             │
│ commons/.../ui/feeds/  (shared, KMP)                        │
│   IFeedFilter / FeedFilter<T>  (abstract base)              │
│   IAdditiveFeedFilter / AdditiveFeedFilter<T>               │
│   ChangesFlowFilter                                         │
│   FeedContentState, FeedState — the flow the UI collects    │
└─────────────────────────────────────────────────────────────┘
              ▲
              │ uses
              │
┌─────────────────────────────────────────────────────────────┐
│ amethyst/.../ui/dal/  (Android-only additions)              │
│   AdditiveComplexFeedFilter<T, U>                           │
│   FilterByListParams                                        │
│   DefaultFeedOrder (Note/Event/Card comparators)            │
│   (FeedFilters.kt & ChangesFlowFilter.kt here are just      │
│    back-compat typealiases re-exporting commons)            │
│                                                             │
│   Concrete feeds: HomeNewThreadFeedFilter,                  │
│   HashtagFeedFilter, NotificationFeedFilter, … live in      │
│   feature folders under ui/screen/loggedIn/*/dal/           │
└─────────────────────────────────────────────────────────────┘
              ▲
              │ reads
              │
┌─────────────────────────────────────────────────────────────┐
│ model/LocalCache.kt + account.<feature>.flow                │
└─────────────────────────────────────────────────────────────┘
```

## Key Files

### Shared (commons)

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/viewmodels/`:

- **`FeedViewModel.kt`** — `abstract class FeedViewModel(localFilter, cacheProvider)`. Holds a `FeedContentState`, subscribes to invalidation signals (from `Account` flows and `LocalCacheFlow`), re-runs the filter, and emits a new `FeedState` for the UI.
- **`ListChangeFeedViewModel.kt`** — specialization for feeds whose membership changes frequently (e.g. bookmarks).
- **`ChatroomFeedViewModel.kt`** — DM thread feed.
- **`MarmotGroupFeedViewModel.kt`** — NIP-29 / marmot group feed.
- **`LiveStreamTopZappersViewModel.kt`, `SearchBarState.kt`, `ChatNewMessageState.kt`** — narrower, non-feed states that share the plumbing.

### Shared filter bases (commons)

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/ui/feeds/`:

- **`FeedFilter.kt`** — `abstract class FeedFilter<T> : IFeedFilter<T>`. Has `feed(): List<T>` (the sync query against the cache), `feedKey(): String` (identity used to cache), `limit()`, and `loadTop()`.
- **`AdditiveFeedFilter.kt`** — `abstract class AdditiveFeedFilter<T> : FeedFilter<T>(), IAdditiveFeedFilter<T>`. Adds incremental updates (the "additive" part): `updateListWith(oldList, newItems)` runs `applyFilter(newItems)` and grafts accepted items onto the existing list (re-`sort` + `take(limit())`) without recomputing everything.
- **`ChangesFlowFilter.kt`** — wraps a filter with a coarse "state changed" signal so the ViewModel knows to re-query.
- **`FeedContentState.kt` / `FeedState.kt`** — the reactive state the UI collects.

### Android DAL (additions on top)

`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/dal/`:

- **`AdditiveComplexFeedFilter.kt`** — `abstract class AdditiveComplexFeedFilter<T, U> : FeedFilter<T>()`: like `AdditiveFeedFilter` but the incoming items (`Set<U>`) are a different type than the list rows (`T`).
- **`FilterByListParams.kt`** — common parameters (top-nav filter, exclude muted, since/until) shared across many filters.
- **`DefaultFeedOrder.kt`** — standard comparators (`createdAt` desc + id tiebreaker for stable paging) for `Note`, `Event`, and `Card`.
- **`FeedFilters.kt` / `ChangesFlowFilter.kt`** — back-compat typealiases re-exporting the commons classes; don't add logic here.

Concrete filters (Home, Hashtag, Profile, Bookmark, Notifications, Communities, etc.) live in feature `dal/` subfolders under `amethyst/.../ui/screen/loggedIn/*/` — each extends `FeedFilter`, `AdditiveFeedFilter`, or `AdditiveComplexFeedFilter`. Desktop has its own in `desktopApp/.../feeds/DesktopFeedFilters.kt`.

## Adding a New Feed

1. **Define the filter.** Extend `AdditiveFeedFilter<Note>` (or plain `FeedFilter<Note>` if additivity doesn't matter; `AdditiveComplexFeedFilter<T, U>` if incoming items differ in type from list rows). Implement:
   - `feedKey()` — stable identity (e.g. hashtag name, account pubkey).
   - `feed()` — synchronous scan over `LocalCache` / `Account` state producing an ordered list.
   - `limit()` — pagination hint.
   - If additive: `applyFilter(collection: Set<Note>): Set<Note>` and `sort(collection: Set<Note>): List<Note>`.
2. **Pick or write a ViewModel.** If the feed's membership shifts often (bookmarks, notifications), extend `ListChangeFeedViewModel`. Otherwise `FeedViewModel`.
3. **Wire invalidation.** The ViewModel must observe the right `Account` flows + `LocalCacheFlow` so it re-queries when state changes.
4. **Render.** In the composable, collect `viewModel.feedState.feedContent` and render with a `LazyColumn { items(..., key = { it.id }) { NoteCompose(it) } }`.
5. **Subscribe to relays.** Most feeds also need a `Subscribable` to fetch historical events. See the `relay-client` skill.

## Filter Sharing (Android vs Desktop)

- The filter **base classes** (`FeedFilter`, `AdditiveFeedFilter`, `ChangesFlowFilter`) and feed state (`FeedContentState`) are in `commons/.../ui/feeds/` — **shared**. ViewModels are in `commons/.../viewmodels/` — **shared**.
- The **concrete** filters are platform-local: Android's in `amethyst/.../ui/screen/loggedIn/*/dal/`, Desktop's in `desktopApp/.../feeds/`. `amethyst/.../ui/dal/` keeps Android-only helpers (`AdditiveComplexFeedFilter`, `FilterByListParams`, `DefaultFeedOrder`) plus back-compat typealiases.
- When porting a feed, share the concrete filter only if both platforms need identical inclusion rules.

## Gotchas

- **Never scan `LocalCache` from a composable.** Always go through a `FeedFilter` + `FeedViewModel`, which does it on a background dispatcher and debounces invalidation.
- **`feedKey()` is used as a cache key.** Two different semantic feeds must produce different keys, otherwise their state cross-contaminates.
- **Additive updates must stay consistent with the full recompute.** If `applyFilter` accepts a note that `feed()` wouldn't include, UX drifts.
- **Paging isn't free** — use `limit()` and `since/until` in `FilterByListParams` rather than trimming a giant scan.
- **Notifications feed is special** — it inspects the follow/mute state (`account.kind3FollowList.flow`, `account.hiddenUsers`) and `LocalCache` deletions to hide muted/deleted content; always run through the `FilterByListParams` exclusion paths rather than filtering post-hoc.

## References

- `references/feed-filter-composition.md` — step-by-step for adding a feed.
- `references/viewmodel-base-classes.md` — inheritance graph for the `FeedViewModel` family.
- Complements: `account-state` (where the data lives), `relay-client` (how to subscribe), `compose-expert` (how to render).
