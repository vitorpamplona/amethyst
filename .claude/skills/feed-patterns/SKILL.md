---
name: feed-patterns
description: Feed composition and data-access layer patterns in Amethyst. Use when adding or modifying a feed (home, profile, hashtag, bookmarks, notifications, DMs, communities), working with `FeedFilter` / `AdditiveComplexFeedFilter` / `ChangesFlowFilter` / `FilterByListParams` in `amethyst/.../ui/dal/`, or extending the `FeedViewModel` family in `commons/.../viewmodels/`. Covers how feeds scan `LocalCache`, react to changes, apply ordering, and render through Compose.
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
│   FeedContentState — the flow the UI collects               │
└─────────────────────────────────────────────────────────────┘
              ▲
              │ uses
              │
┌─────────────────────────────────────────────────────────────┐
│ amethyst/.../ui/dal/  (Android; feeds defined per screen)   │
│   FeedFilter<T>   (abstract)                                │
│   AdditiveComplexFeedFilter<T, U>                           │
│   ChangesFlowFilter                                         │
│   FilterByListParams                                        │
│   DefaultFeedOrder                                          │
│                                                             │
│   Plus concrete feeds: HomeFeedFilter, HashtagFeedFilter,   │
│   BookmarkListFeedFilter, NotificationFeedFilter, …         │
└─────────────────────────────────────────────────────────────┘
              ▲
              │ reads
              │
┌─────────────────────────────────────────────────────────────┐
│ model/LocalCache.kt + Account.<featureFlow>                 │
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

### Android DAL (the filters)

`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/dal/`:

- **`FeedFilters.kt`** — `abstract class FeedFilter<T>`. Has `feed(): List<T>` (the sync query against `LocalCache`) and `feedKey(): String` (identity used to cache).
- **`AdditiveComplexFeedFilter.kt`** — `abstract class AdditiveComplexFeedFilter<T, U> : FeedFilter<T>()`. Adds incremental updates (the "additive" part): when a single new event arrives, the filter can decide whether to graft it onto the existing list without recomputing everything.
- **`ChangesFlowFilter.kt`** — wraps a filter with a coarse "Account state changed" signal so the ViewModel knows to re-query.
- **`FilterByListParams.kt`** — common parameters (author set, exclude muted, limit, since/until) shared across many filters.
- **`DefaultFeedOrder.kt`** — standard sort (by `createdAt` desc, plus tiebreakers for stable paging).

Concrete filters (Home, Hashtag, Profile, Bookmark, Notifications, Communities, etc.) live in feature subfolders under `amethyst/.../ui/screen/loggedIn/*/` — each extends `FeedFilter` or `AdditiveComplexFeedFilter`.

## Adding a New Feed

1. **Define the filter.** Extend `AdditiveComplexFeedFilter<Note, Set<HexKey>>` (or plain `FeedFilter<Note>` if additivity doesn't matter). Implement:
   - `feedKey()` — stable identity (e.g. hashtag name, account pubkey).
   - `feed()` — synchronous scan over `LocalCache` / `Account` state producing an ordered list.
   - `limit()` — pagination hint.
   - If using `AdditiveComplexFeedFilter`: `applyFilter(collection: Set<Note>): Set<Note>` and `sort(collection: Set<Note>): List<Note>`.
2. **Pick or write a ViewModel.** If the feed's membership shifts often (bookmarks, notifications), extend `ListChangeFeedViewModel`. Otherwise `FeedViewModel`.
3. **Wire invalidation.** The ViewModel must observe the right `Account` flows + `LocalCacheFlow` so it re-queries when state changes.
4. **Render.** In the composable, collect `viewModel.feedState.feedContent` and render with a `LazyColumn { items(..., key = { it.id }) { NoteCompose(it) } }`.
5. **Subscribe to relays.** Most feeds also need a `Subscribable` to fetch historical events. See the `relay-client` skill.

## Filter Sharing (Android vs Desktop)

- `FeedFilter` and the concrete filters currently live in `amethyst/.../ui/dal/` — **Android-only**. Desktop has parallel filters in `desktopApp/.../feeds/`.
- ViewModels are in `commons/commonMain/` — **shared**. That's the boundary: filter is Android (could be extracted), ViewModel is shared.
- When porting a new feed, extract the filter to a KMP-friendly location only if both platforms need it.

## Gotchas

- **Never scan `LocalCache` from a composable.** Always go through a `FeedFilter` + `FeedViewModel`, which does it on a background dispatcher and debounces invalidation.
- **`feedKey()` is used as a cache key.** Two different semantic feeds must produce different keys, otherwise their state cross-contaminates.
- **Additive updates must stay consistent with the full recompute.** If `applyFilter` accepts a note that `feed()` wouldn't include, UX drifts.
- **Paging isn't free** — use `limit()` and `since/until` in `FilterByListParams` rather than trimming a giant scan.
- **Notifications feed is special** — it inspects `Account.followListFlow` and `LocalCache` deletions to hide muted/deleted content; always run through `FilterByListParams.exclude*` paths rather than filtering post-hoc.

## References

- `references/feed-filter-composition.md` — step-by-step for adding a feed.
- `references/viewmodel-base-classes.md` — inheritance graph for the `FeedViewModel` family.
- Complements: `account-state` (where the data lives), `relay-client` (how to subscribe), `compose-expert` (how to render).
