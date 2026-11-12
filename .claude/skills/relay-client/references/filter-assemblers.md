# Filter assemblers and EOSE managers

Paths are under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/`
unless they start with `commonsUI/`.

## The layers of one feature (a chess board as the example)

| Layer | File | Role |
|---|---|---|
| Query state | `chess/ChessGameFilterAssembler.kt` — `class ChessGameQueryState(gameId, account) : AccountScopedQuery` | what one on-screen consumer needs; equality decides dedup |
| Filter builder | `ChessGameSubAssembler.updateFilter(key, since)` in the same file | state + per-relay `since` → filters (`#e` the game on every relay it lives on) |
| Sub-assembler | `ChessGameSubAssembler(cache, client, allKeys) : PerUniqueIdEoseManager<ChessGameQueryState, HexKey>` | owns the REQ, overrides `updateFilter(key, since)`, `id(key)`, optionally `newSub(key)` to hook events/EOSE |
| Filter assembler | `chess/ChessGameFilterAssembler.kt` — `: ComposeSubscriptionManager<ChessGameQueryState>()` | holds the live states, fans `invalidateFilters`/`destroy` to its `group` of sub-assemblers |
| Registration | `reqCommand/RelaySubscriptionsCoordinator.kt` — `val chessGame = ChessGameFilterAssembler(cache, client)` | one instance per account session |
| Composable | `commonsUI/.../ui/screen/loggedIn/chess/datasource/ChessGameFilterAssemblerSubscription.kt` | builds the state, subscribes via `accountViewModel.dataSources().chessGame` |

A top-nav feed (the chess lobby's `chess/ChessFilterAssembler.kt`) needs none of that by hand:
a `TopNavFeedFilterAssembler` over a `SingleTopNavFeedSubAssembler` and a pure
`makeXxxFilter(feedSettings, since, defaultSince)` (`chess/FilterChessLobby.kt`).

## Recipe for a new screen

1. Query state: a `data class` with exactly the inputs (ids, pubkeys, relays, account) the
   filters need. Remember it per screen (`remember(key) { State(...) }`).
2. Filter function: `filterXxx(key, since: SincePerRelayMap?): List<RelayBasedFilter>` —
   one `RelayBasedFilter(relay, ExplainedFilter(..., purpose = SubPurpose.X))` per relay, with
   `since = since?.get(relay)?.time`. Pick or add a `SubPurpose` (`subscriptions/SubPurpose.kt`).
3. Sub-assembler: extend the EOSE manager that matches how cursors should be shared (below).
4. Assembler: `class XxxFilterAssembler(client) : ComposeSubscriptionManager<XxxQueryState>()`
   grouping the sub-assemblers (copy `ChessGameFilterAssembler`).
5. Add it to `RelaySubscriptionsCoordinator`.
6. In `commonsUI/.../relayClient/<feature>/`, a `@Composable XxxFilterAssemblerSubscription`
   that calls `LifecycleAwareKeyDataSourceSubscription(state, assembler)` (or
   `KeyDataSourceSubscription` for light, always-on data).

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
