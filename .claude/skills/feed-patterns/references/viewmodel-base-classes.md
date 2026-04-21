# ViewModel Base Classes

Inheritance tree for the shared feed ViewModels in `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/viewmodels/`.

## Tree

```
androidx.lifecycle.ViewModel
    │
    ▼
InvalidatableContent  (interface)
    │
    ▼
FeedViewModel(localFilter: FeedFilter<Note>, cacheProvider: ICacheProvider)
    │
    ├── ListChangeFeedViewModel   (list membership changes often)
    │       │
    │       └── (concrete bookmark / list feeds)
    │
    ├── ChatroomFeedViewModel     (DM thread)
    │
    └── MarmotGroupFeedViewModel  (NIP-29 group chat)
```

Tangentially related (same folder, not in the tree):
- `LiveStreamTopZappersViewModel.kt` — sidebar state for live streams.
- `SearchBarState.kt` — search input + suggestions.
- `ChatNewMessageState.kt` — composer state for a new DM.
- `thread/*` — thread ViewModels (not technically feeds but share wiring).

## `FeedViewModel`

```kotlin
abstract class FeedViewModel(
    localFilter: FeedFilter<Note>,
    val cacheProvider: ICacheProvider,
) : ViewModel(), InvalidatableContent {

    val feedState = FeedContentState(localFilter, viewModelScope, cacheProvider)

    fun invalidateAll()                      // full recompute
    fun invalidateInsertData(newNotes: Set<Note>)   // additive path
    fun invalidateReplace(replaced: Set<Note>)      // replaceable/addressable update
}
```

`FeedContentState` is the thing the UI collects:

- `feedContent: StateFlow<FeedState>` — the actual list, loading flag, paging state.
- Runs the `localFilter.feed()` on a background dispatcher.
- Debounces consecutive invalidations so bursts of relay frames don't thrash the filter.

## `ListChangeFeedViewModel`

Extends `FeedViewModel`. Override point:

```kotlin
abstract class ListChangeFeedViewModel(...) : FeedViewModel(...) {
    // Automatically re-invalidates on Account list-flow changes
    abstract fun dependencyList(): List<Flow<*>>
}
```

Used for bookmarks, mutes, and custom `NIP-51` lists — anything whose membership is decided by an `Account` StateFlow.

## `ChatroomFeedViewModel`

Wraps filter + relay subscription + typing-indicator state for a single DM thread. Use directly for chat screens; don't reimplement per-thread.

## `MarmotGroupFeedViewModel`

NIP-29 (marmot variant) group feed. Adds group membership / moderator state on top of the base feed.

## When to Extend vs Reuse

- **Just a new filter** → instantiate `FeedViewModel` with your filter; no new class needed.
- **New invalidation signal** → subclass and override `init` to add collectors.
- **Entirely new paging model** (infinite scroll, server-assisted paging) → subclass with a custom `FeedContentState`.
- **Non-feed state** (search, composer) → don't use `FeedViewModel` at all; see `SearchBarState.kt` / `ChatNewMessageState.kt` for narrow-state patterns.

## Platform Wrapping

On Android, feed ViewModels are created via `viewModel { HashtagFeedViewModel(...) }` in the composable. On Desktop, they're instantiated directly and stored in a `WorkspaceManager` column (see `desktopApp/.../ui/deck/WorkspaceManager.kt`). The ViewModel class itself is KMP-friendly.

## Gotchas

- **Multiple subscribers to `feedContent`** are fine — it's a `StateFlow`.
- **ViewModels survive configuration changes on Android** but not on Desktop `key {}` rebuilds — re-instantiate in Desktop's workspace lifecycle.
- **`cacheProvider` is almost always `LocalCache`** but the parameter exists so tests can inject a fixture.
- **Don't call `invalidateAll()` from UI** — it's triggered by the ViewModel's own collectors. Calling it from the composable just causes extra filter runs.
