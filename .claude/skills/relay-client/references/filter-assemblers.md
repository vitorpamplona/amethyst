# Filter assemblers and EOSE managers

Paths are under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/`
unless they start with `commonsUI/`.

## The layers of one feature (a chess board as the example)

| Layer | File | Role |
|---|---|---|
| Query state | `chess/ChessGameFilterAssembler.kt` — `class ChessGameQueryState(gameId, account) : AccountScopedQuery` | what one on-screen consumer needs; equality decides dedup |
| Filter builder | `ChessGameSubAssembler.updateFilter(key, since)` in the same file | state → filters (`#e` the game on every relay it lives on); the game's start note holds the moves, so its cursors set each relay's `since` (`cursorHoldersOf`) |
| Sub-assembler | `ChessGameSubAssembler(cache, client, allKeys) : PerUniqueIdEoseManager<ChessGameQueryState, HexKey>` | owns the REQ, overrides `updateFilter(key, since)`, `id(key)`, optionally `newSub(key)` to hook events/EOSE |
| Filter assembler | `chess/ChessGameFilterAssembler.kt` — `: ComposeSubscriptionManager<ChessGameQueryState>()` | holds the live states, fans `invalidateFilters`/`destroy` to its `group` of sub-assemblers |
| Registration | `reqCommand/RelaySubscriptionsCoordinator.kt` — `val chessGame = ChessGameFilterAssembler(cache, client)` | one instance per account session |
| Composable | `commonsUI/.../ui/screen/loggedIn/chess/datasource/ChessGameFilterAssemblerSubscription.kt` | builds the state, subscribes via `accountViewModel.dataSources().chessGame` |

A top-nav feed (the chess lobby's `chess/ChessFilterAssembler.kt`) needs none of that by hand:
a `TopNavFeedFilterAssembler` over a `SingleTopNavFeedSubAssembler` and a pure
`makeXxxFilter(feedSettings, since, defaultSince)` (`chess/FilterChessLobby.kt`).

## Recipe for a new screen

The URL screen is the worked example of every step: `url/UrlFilterAssembler.kt`,
`url/UrlFeedFilterSubAssembler.kt`, and `commonsUI/.../ui/screen/loggedIn/url/UrlScreen.kt`.

1. **Decide what holds the notes the subscription loads.** Usually the screen's feed
   (`feedViewModel.feedState`), which keeps what it shows. Otherwise a `Channel`, a `User`, a
   `Note` (its children), the `Account`, or the key itself: see "Where the cursor lives" below.
   This decides when the relays are asked again from scratch, so it is not optional: a manager
   whose keys name nothing asks every relay from scratch on every rebuild.
2. **Query state:** a class with exactly the inputs (ids, pubkeys, relays, account) the filters
   need, plus that holder (`feeds: List<RelayCursorHolder>`, or implement `CursorHoldingQuery` /
   `KeyHeldCursors`). Remember it per screen (`remember(key, feed) { State(...) }`): a key made
   again on recomposition starts over.
3. **Filter function:** `filterXxx(key, since: SincePerRelayMap?): List<RelayBasedFilter>`, one
   `RelayBasedFilter(relay, ExplainedFilter(..., purpose = SubPurpose.X))` per relay, with
   `since = since?.get(relay)?.time`. `since` is null when holders set the windows: the planner
   then sets each filter's `since` to the later of its own floor and the holder's cursor, so put
   a floor in the filter only if the screen needs one. Pick or add a `SubPurpose`
   (`subscriptions/SubPurpose.kt`).
4. **Sub-assembler:** extend the EOSE manager that splits the REQs the way you need (below) and
   override `cursorHoldersOf(key)` to return the holders from step 1 (`= key.feeds`), unless the
   key already names them. A lazy account feed must be woken before it vouches
   (`(it as? FeedContentState)?.activate()`).
5. **Assembler:** `class XxxFilterAssembler(client) : ComposeSubscriptionManager<XxxQueryState>()`
   grouping the sub-assemblers (copy `ChessGameFilterAssembler`), added to
   `RelaySubscriptionsCoordinator`.
6. **Composable:** in `commonsUI/.../relayClient/<feature>/`, a `@Composable XxxFilterAssemblerSubscription`
   taking the holder (`feeds = listOf(feedViewModel.feedState)`) and calling
   `LifecycleAwareKeyDataSourceSubscription(state, assembler)` (or `KeyDataSourceSubscription` for
   light, always-on data).
7. **`CursorHolderCoverageTest`** (`commons/src/jvmTest/.../eoseManagers/`) fails when a manager
   names no holder. If yours never reads a cursor (a history pager bounded by `until`, a tail from
   a fixed floor, a request that ignores `since`), add it to that test's `NEEDS_NO_HOLDER` with
   the reason.

## Choosing the EOSE manager (`eoseManagers/`)

All extend `BaseEoseManager(client, allKeys, sampleTime = 500)`, which coalesces
invalidations through a `BundledUpdate` and drives a `SubscriptionController`.

| Manager | Subscriptions | Used by |
|---|---|---|
| `SingleSubEoseManager<T>(client, allKeys, invalidateAfterEose)` | one REQ for every key; `updateFilter(keys, since)`, `distinct(key)`; entities whose cursors agree on every relay are built together | `UserProfileMetadataFilterSubAssembler`, `CommunityFeedFilterSubAssembler` |
| `PerUniqueIdEoseManager<T, U>` | one REQ per `id(key)` | `chess/ChessGameSubAssembler`, `url/UrlFeedFilterSubAssembler` |
| `PerUserEoseManager<T>` | one REQ per `user(key)` | `podcasts/MyPodcastFeedSubAssembler`, `napplets/ConnectedAppsSubAssembler` |
| `PerUserAndFollowListEoseManager<T>` | one REQ per logged-in user; wakes the key's lazy feeds | `topNavFeeds/TopNavFeedSubAssembler` (home, video, discovery, notifications) |
| `SingleSubNoEoseCacheEoseManager`, `AccountScopedSingleSubNoEoseCacheEoseManager` | one REQ, no cursor | `event/loaders/NoteEventLoaderSubAssembler`, `reqCommand/channel/nip28PublicChats/ChannelLoaderSubAssembler` |

## Where the cursor lives

A cursor says "everything up to T is in memory", which is true only while something holds those
notes (`LocalCache` keeps notes and users weakly). So the cursors live on that holder
(`RelayCursorHolder`, `RelayCursors` slots per manager), named by `cursorHoldersOf(key)`, never in
the manager, and `CursorPlanner` turns them into windows:

| What holds the notes | Holder | Example |
|---|---|---|
| a screen feed | its `FeedContentState` (pass it in the key's `feeds`) | top-nav feeds, hashtag, profile posts |
| a channel (held strongly by `LocalCache`) | the `Channel`; the pruner rewinds it | `ChannelPublicFilterSubAssembler`, relay-group tails |
| a user object | the `User` | `UserProfileMetadataFilterSubAssembler`, `UserWatcherSubAssembler` |
| a note's children | the `Note` (`childCursors()`) | thread, poll responses, chess game, repository items |
| the account, for as long as it is loaded | the `Account` | wallets, drafts, joined groups, followed chats |
| only the screen that made the key | the key itself (`KeyHeldCursors`; remember it for the screen) | search, connected apps, git dashboard |

A key that names none is asked from scratch, with nothing recorded.

## Gotchas

- A key whose holder is the key must be `remember`ed for the screen's lifetime, or every
  recomposition asks from scratch.
- Build filters with the `since` you are given (null when holders window them); `limit` large
  author sets.
- `ExplainedFilter.copy(since = …)` keeps the purpose; constructing a plain `Filter` loses it.
- Tests: `commons/src/jvmTest/.../relayClient/` (`eoseManagers/CursorPlannerTest`, `eoseManagers/SingleSubPerEntityCursorTest`,
  `assemblers/FeedMetadataCoordinatorTest`, …).
