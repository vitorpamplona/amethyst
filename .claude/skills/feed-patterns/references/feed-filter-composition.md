# Adding a New Feed

Step-by-step recipe for composing a new feed. Assume the feed shows `Note`s filtered by some criterion and should update reactively when the underlying state changes.

## 1. Choose a Filter Base

| If… | Use |
|-----|-----|
| Membership is stable (e.g. "my follows") and you re-compute on change | `FeedFilter<Note>` |
| New notes arrive one at a time and should slot into the list incrementally | `AdditiveComplexFeedFilter<Note, Set<Note>>` |
| The feed is a simple list that changes frequently (e.g. bookmarks, lists) | `FeedFilter<Note>` + `ListChangeFeedViewModel` |
| The feed is a DM thread | `ChatroomFeedViewModel` (already provides filter machinery) |

All live in `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/dal/`.

## 2. Write the Filter

```kotlin
class HashtagFeedFilter(
    private val accountViewModel: AccountViewModel,
    private val hashtag: String,
) : AdditiveComplexFeedFilter<Note, Set<Note>>() {

    override fun feedKey(): String = "Hashtag-$hashtag"

    override fun showHiddenKey(): Boolean = false

    override fun feed(): List<Note> {
        val params = FilterByListParams.create(
            excludeMuted = true,
            hiddenUsers  = accountViewModel.hiddenUsersFlow.value,
        )
        return LocalCache.hashtagIndex[hashtag]
            .orEmpty()
            .filter { params.match(it) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> =
        collection.filter { it.event?.isTaggedHash(hashtag) == true }.toSet()

    override fun sort(collection: Set<Note>): List<Note> =
        collection.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 1000
}
```

Key points:

- `feedKey()` must uniquely identify this filter *instance*. The parameter (hashtag in this case) is part of the key so two hashtag feeds don't share state.
- `feed()` is the full recompute — synchronous, runs on a background dispatcher.
- `applyFilter()` is the per-event membership check used by the additive path.
- Always use `FilterByListParams` rather than re-implementing mute / hidden-user logic.
- `DefaultFeedOrder` is the canonical sort; deviating breaks paging assumptions.

## 3. Pick a ViewModel

If an existing ViewModel already matches the flow pattern, reuse it with your new filter:

```kotlin
class HashtagFeedViewModel(
    val hashtag: String,
    accountViewModel: AccountViewModel,
) : FeedViewModel(
    localFilter = HashtagFeedFilter(accountViewModel, hashtag),
    cacheProvider = LocalCache,
)
```

If membership changes aggressively (e.g. the user toggles a mute), use `ListChangeFeedViewModel` instead and hook into `Account.muteListFlow`.

## 4. Wire Invalidation

`FeedViewModel` already re-queries on `LocalCacheFlow` ticks. For changes that come from `Account` state (mutes, follows, bookmarks, relay list updates) add them in the ViewModel:

```kotlin
init {
    viewModelScope.launch {
        accountViewModel.muteListFlow.collect { invalidateAll() }
    }
}
```

`invalidateAll()` triggers a full `feed()` re-run; `invalidateInsertData(addedNotes)` is the additive path.

## 5. Subscribe to Relays

Unless the feed only shows already-cached data, write a `Subscribable` that fetches history. See `relay-client` skill. Typically:

```kotlin
val subscribable = rememberSubscribable(hashtag) {
    HashtagFilterAssembler(hashtag).toSubscribable()
}
DisposableEffect(hashtag) {
    subscribable.subscribe()
    onDispose { subscribable.unsubscribe() }
}
```

## 6. Render

```kotlin
val feedState by viewModel.feedState.feedContent.collectAsStateWithLifecycle()

LazyColumn {
    items(
        items = feedState.feed.value,
        key = { it.idHex },
    ) { note ->
        NoteCompose(note)
    }
}
```

Use `key = { it.idHex }` so Compose can diff efficiently across additive updates.

## 7. Test

Unit-test the filter in isolation: feed it a known `LocalCache` snapshot and assert the output order. Filters are side-effect-free once `LocalCache` is fixed, so they're straightforward to pin.

## Common Mistakes

- **Inline filtering in composables.** If you call `LocalCache.notes.filter { … }` in a composable, the filter recomputes every recomposition and never invalidates correctly. Always go through a `FeedFilter`.
- **Forgetting `showHiddenKey()`.** If you want a "show hidden" toggle, override it; otherwise hidden content is silently dropped.
- **Non-stable `feedKey()`.** Using a hash that depends on current time or mutable state causes the ViewModel to lose its cached state on every invalidation.
- **Skipping `FilterByListParams`.** Muted users, reported users, spam filter — all of it lives there. Reimplementing is a source of bugs.
