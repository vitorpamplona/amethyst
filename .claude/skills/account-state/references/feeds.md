# Feeds

Every scrollable list of notes (home, profile, hashtag, bookmarks, notifications, DMs, discovery,
communities) is a **filter** that decides membership over `LocalCache`, plus a **state holder** that
keeps the result current as events arrive. All of it is headless and shared by Android and Desktop.

Paths are relative to `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/`.

## Where things live

| Piece | Location |
|---|---|
| Filter bases: `IFeedFilter`, `FeedFilter<T>`, `IAdditiveFeedFilter`, `AdditiveFeedFilter<T>`, `AdditiveComplexFeedFilter<T, U>`, `ChangesFlowFilter<T>` | `feeds/` |
| `FilterByListParams` (top-nav list, hidden users, muted threads, future dates, hashtag spam) | `feeds/FilterByListParams.kt` |
| Orders: `DefaultFeedOrder`, `sortedByDefaultFeedOrder()`, `DefaultFeedOrderEvent`, `DefaultFeedOrderCard`, `NotificationFeedOrderCard` | `feeds/DefaultFeedOrder.kt`, `feeds/FeedOrders.kt` |
| State: `FeedContentState`, `FeedState` (`Loading` / `Loaded` / `Empty` / `FeedError`), `LoadedFeedState`, plus `ChannelFeedContentState`, `notifications/CardFeedContentState.kt` | `feeds/`, `notifications/` |
| ViewModels: `FeedViewModel`, `AndroidFeedViewModel` (binds `LocalCache`), `ListChangeFeedViewModel`, `ChatroomFeedViewModel`, `MarmotGroupFeedViewModel` | `viewmodels/` |
| Account-lifetime feeds (home tabs, DMs, discovery, notifications, …) | `viewmodels/AccountFeedContentStates.kt`, exposed as `BaseAccountViewModel.feedStates`, owned by `viewmodels/AccountFeeds.kt` (Active / Warm 15 min / Cold) |
| Universe and candidates: `UniverseFeedFilter`, `OwnedUniverseFeedFilter`, `rebuildFeed` | `feeds/UniverseFeedFilter.kt`; `CandidateSet` in `model/observables/` |
| Notes nothing else holds (an account's drafts, a community's rules): `HeldNotes` | `relayClient/eoseManagers/HeldNotes.kt` |
| Concrete filters + per-screen ViewModels | `<feature>/dal/` — `home/dal/`, `hashtag/dal/`, `notifications/dal/`, `music/dal/`, … (~55 folders) |
| Rendering | `commonsUI/…/commons/ui/screen/FeedView.kt` (`RefresheableFeedView`, `RenderFeedState`), `commonsUI/…/commons/ui/feeds/` (`FeedLoaded`, `WatchLifecycleAndUpdateModel`) |

There is no Android-only or Desktop-only feed layer any more: write a new filter in
`commons/…/<feature>/dal/` and both apps get it.

## Choosing a base

| Situation | Base |
|---|---|
| Recompute on change is fine | `FeedFilter<Note>` |
| Notes arrive one by one and should merge in | `AdditiveFeedFilter<Note>` (`applyFilter` + `sort`) |
| Rows differ in type from incoming notes | `AdditiveComplexFeedFilter<T, U>` — e.g. `home/dal/HomeLiveFilter.kt` (`<Channel, Note>`) |
| Membership is pushed by a list, not discovered in the cache | a `ChangesFlowFilter<Note>` + `ListChangeFeedViewModel` (`feeds/ChatroomFeedFilter.kt`, `feeds/MarmotGroupFeedFilter.kt`) |

## Recipe

Model it on `url/dal/` (`UrlFeedFilter`: universe, candidates, `rebuildFeed`) and
`commonsUI/…/commons/ui/screen/loggedIn/url/UrlScreen.kt` (screen and subscription), or on
`hashtag/dal/` for the same shape with a ViewModel factory.

1. **Filter** (`hashtag/dal/HashtagFeedFilter.kt`, abridged):

   ```kotlin
   class HashtagFeedFilter(
       val tag: String,
       val relays: Set<NormalizedRelayUrl>,
       val account: Account,
       val cache: LocalCache,
   ) : AdditiveFeedFilter<Note>() {
       override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + tag

       override fun feed(): List<Note> {
           val follows = account.followingKeySet()
           val notes = cache.notes.filterIntoSet { _, it -> acceptableEvent(it, tag, follows) }
           return sort(notes)
       }

       override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

       override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
   }
   ```

   `acceptableEvent` ends with `!it.isHiddenFor(account.hiddenUsers.flow.value) && account.isAcceptable(it)`.
   Feeds driven by a top-nav list build `FilterByListParams.create(followLists, account.hiddenUsers.flow.value)`
   and call `match(...)` — see `home/dal/HomeNewThreadFeedFilter.kt` (`buildFilterParams`).

   **Split the check in two when the feed shows a small part of the cache** (a URL, a profile, a
   hashtag, notifications): implement `OwnedUniverseFeedFilter`. Its `universe` is an `IFilter`
   decided by the event alone (kind, tags, author: never mutes, the selected list or settings, so it
   never changes once a note is cached). `feed()` calls `rebuildFeed(name, candidates, keep = { … }) { cache walk }`:
   the feed's owner keeps a `CandidateSet` of the universe and rebuilds read it instead of walking
   the whole cache. `FeedViewModel` makes that set for a screen feed by itself; an account feed gets
   one with `.withCandidates()` in `AccountFeedContentStates`. A feed that takes in most of the
   cache (Home) leaves `candidates` null and walks.

2. **State holder.** A per-screen feed subclasses `AndroidFeedViewModel(filter)` and ships a
   `ViewModelProvider.Factory` (`hashtag/dal/HashtagFeedViewModel.kt`). A feed that lives as long as
   the account is a `FeedContentState(filter, scope, LocalCache, lazy = true)` field in
   `AccountFeedContentStates` — also add it to that class's update/delete/trim fan-out
   (`updateFeedsWith`, `deleteNotes`, `trimFeedsToSize`), or it never sees new events, and to its
   `cursorHolders` list, which refreshes built feeds after a warm spell. `lazy = true` builds nothing
   until something collects the feed or a subscription that fills it calls `activate()`.

3. **Screen.** `rememberViewModel(key = …, factory = HashtagFeedViewModel.Factory(…))`, then
   `WatchLifecycleAndUpdateModel(viewModel)` (refresh on first composition and on resume),
   the relay subscription composable **given the feed that holds what it loads**
   (`UrlFilterAssemblerSubscription(url, accountViewModel, feeds = listOf(feedViewModel.feedState))`,
   see `relay-client`'s recipe), and `RefresheableFeedView(viewModel, …)`, which collects
   `feedState.feedContent` and renders by `FeedState`.

   The feed is a `RelayCursorHolder`: the subscription's EOSE cursors live on it and die with it,
   because it holds the notes they vouch for (`LocalCache` keeps them only weakly). A new feed (the
   screen opened again, another account) asks the relays from scratch; the same feed asks only for
   what is newer. When no feed shows what a subscription loads, hold it with a `HeldNotes` and name
   that instead.

4. **Invalidation.** `FeedViewModel` already collects `cacheProvider.getEventStream()`
   `newEventBundles` → `feedState.updateFeedWith(...)` (additive merge when the filter is additive and
   the feed is loaded, full refresh otherwise) and `deletedEventBundles` → `deleteFromFeed(...)`.
   When an **account input** of the filter changes (top-nav list, mutes), call
   `checkKeysInvalidateDataAndSendToTop()` — it re-runs only if `feedKey()` changed. Real use:
   `WatchAccountForHomeScreen` in `commonsUI/…/ui/screen/loggedIn/home/HomeScreen.kt` keys a
   `LaunchedEffect` on `account.liveHomeFollowLists`. `invalidateData()` forces a full re-run.

5. **Tests.** Existing filter tests are JVM unit tests in `amethyst/src/test/java/…/ui/dal/` and
   `…/ui/screen/loggedIn/notifications/dal/` (e.g. `NotificationTagsAnEventByUserTest.kt`), which
   exercise filter predicates directly.

## Traps

- **`feedKey()` is the invalidation key.** Everything the result depends on — account pubkey,
  selected list, the *resolved* constraint set — must be in it, or
  `checkKeysInvalidateDataAndSendToTop()` swallows the change. `RelayGroupDiscoveryFeedFilter`
  (`chats/publicChannels/relayGroup/dal/`) documents a frozen-feed bug from keying only on the list
  name while the per-relay set resolved a frame later.
- **Additive merges only add.** `AdditiveFeedFilter.updateListWith` unions accepted new items into
  the old list; it never re-checks old rows. If a new event can invalidate an existing row, override
  `updateListWith` (`HomeNewThreadFeedFilter` drops superseded RSVPs first). `applyFilter` must
  accept exactly what `feed()` would, or the list drifts until the next full refresh.
- **Sort with `sortedByDefaultFeedOrder()`, not `sortedWith(DefaultFeedOrder)`, for `Note` lists
  that may change under you.** It snapshots `createdAt` once per note; the comparator alone can throw
  TimSort's "Comparison method violates its general contract!" when a newer addressable swaps a
  note's event mid-sort.
- **`showHiddenKey()`** returns true for the "muted / blocked" views (`FilterByListParams.showHiddenKey`,
  `settings/dal/HiddenAccountsFeedFilter.kt`); hidden content is otherwise dropped silently.
- **`limit()` defaults to 500** and `AccountFeedContentStates` trims to 200 under memory pressure;
  don't build feeds that need the full history in memory.
- **A feed's cursors vouch for what it holds, not what it rejected.** A note the filter rejects
  (muted, hidden word) is held by nothing. Showing more (an unmute) fires `HiddenSetWidenings`, and
  every feed clears its cursors; a key change clears them too. Don't keep a feed's notes elsewhere
  and assume its cursors cover them.
- **Never filter `LocalCache` in a composable.** `feed()` runs on `Dispatchers.IO` inside
  `FeedContentState` with 250 ms bundling; a composable scan reruns every recomposition and never
  invalidates.
