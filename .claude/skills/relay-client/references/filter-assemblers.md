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

| Manager | Subscriptions | EOSE cursor | Used by |
|---|---|---|---|
| `SingleSubEoseManager<T>(client, allKeys, invalidateAfterEose)` | one REQ for every key; `updateFilter(keys, since)`, `distinct(key)` | one per relay, shared by all keys; `clearEoseFor(relay)` when the key set grows | `MetadataFilterAssembler`, `UserCardsSubAssembler` |
| `PerUniqueIdEoseManager<T, U>` | one REQ per `id(key)` | per id | `chess/ChessGameSubAssembler`, `url/UrlFeedFilterSubAssembler` |
| `PerUserEoseManager<T>` | one REQ per `user(key)` | per user | `podcasts/MyPodcastFeedSubAssembler`, `napplets/ConnectedAppsSubAssembler` |
| `PerKeyEoseManager<T, K>` | one REQ per `extractKey(state)` | per key | (no subclass today) |
| `PerUserAndFollowListEoseManager` | one REQ per logged-in user and selected top-nav list | per user + list | `topNavFeeds/TopNavFeedSubAssembler` (home, video, discovery, notifications) |
| `SingleSubNoEoseCacheEoseManager`, `AccountScopedSingleSubNoEoseCacheEoseManager` | one REQ | not cached | `event/loaders/NoteEventLoaderSubAssembler`, `reqCommand/channel/nip28PublicChats/ChannelLoaderSubAssembler` |

`ReactionsFilterAssembler` and `MetadataFilterAssembler` (`assemblers/`) are the smallest
complete `SingleSubEoseManager` examples.

## Gotchas

- A merged single-sub filter that gains a key inherits a cursor earned by the older keys —
  call `clearEoseFor(relay)` (see the KDoc on `SingleSubEoseManager`).
- `limit` large author sets, and set `since` from the EOSE map so reconnects don't refetch
  history.
- `ExplainedFilter.copy(since = …)` keeps the purpose; constructing a plain `Filter` loses it.
- Tests: `commons/src/jvmTest/.../relayClient/` (`eoseManagers/MergedAuthorTrackerTest`,
  `assemblers/FeedMetadataCoordinatorTest`, …).
