# Filter assemblers and EOSE managers

Paths are under `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/`
unless they start with `commonsUI/`.

## The layers of one feature (chess as the example)

| Layer | File | Role |
|---|---|---|
| Query state | `chess/ChessFeedFilterSubAssembler.kt` — `data class ChessQueryState(userPubkey, inboxRelays, globalRelays, isGlobal, …)` | what one on-screen consumer needs; equality decides dedup |
| Filter builder | `chess/FilterChessEvent.kt` — `fun filterChessEvents(key, since): List<RelayBasedFilter>` | pure: state + per-relay `since` → filters |
| Sub-assembler | `ChessFeedFilterSubAssembler(client, allKeys) : PerUniqueIdEoseManager<ChessQueryState, String>` | owns the REQ, overrides `updateFilter(key, since)`, `id(key)`, optionally `newSub(key)` to hook events/EOSE |
| Filter assembler | `chess/ChessFilterAssembler.kt` — `: ComposeSubscriptionManager<ChessQueryState>()` | holds the live states, fans `invalidateFilters`/`destroy` to its `group` of sub-assemblers |
| Registration | `reqCommand/RelaySubscriptionsCoordinator.kt` — `val chess = ChessFilterAssembler(client)` | one instance per account session |
| Composable | `commonsUI/.../ui/screen/loggedIn/chess/datasource/ChessSubscription.kt` | builds the state, subscribes via `accountViewModel.dataSources().chess` |

## Recipe for a new screen

1. Query state: a `data class` with exactly the inputs (ids, pubkeys, relays, account) the
   filters need. Remember it per screen (`remember(key) { State(...) }`).
2. Filter function: `filterXxx(key, since: SincePerRelayMap?): List<RelayBasedFilter>` —
   one `RelayBasedFilter(relay, ExplainedFilter(..., purpose = SubPurpose.X))` per relay, with
   `since = since?.get(relay)?.time`. Pick or add a `SubPurpose` (`subscriptions/SubPurpose.kt`).
3. Sub-assembler: extend the EOSE manager that matches how cursors should be shared (below).
4. Assembler: `class XxxFilterAssembler(client) : ComposeSubscriptionManager<XxxQueryState>()`
   grouping the sub-assemblers (copy `ChessFilterAssembler`).
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
| `PerUniqueIdEoseManager<T, U>` | one REQ per `id(key)` | per id | `ChessFeedFilterSubAssembler`, `url/UrlFeedFilterSubAssembler` |
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
