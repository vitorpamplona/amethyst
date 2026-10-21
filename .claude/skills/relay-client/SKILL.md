---
name: relay-client
description: Compose-scoped relay subscriptions in commons/relayClient: observeUser*/EventFinder, FilterAssemblers, EOSE managers, ComposeSubscriptionManager. Use when a screen must load events from relays while visible.
---

# Relay client: screen-scoped subscriptions

How a composable gets events from relays only while it is on screen. Headless code (amy,
geode, tests, one-shot fetches) does not use this layer: it uses the `INostrClient`
extensions — see AGENTS.md › Feature Workflow ("Relay client ops already exist").

## Where it lives

- `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/` — the
  headless half: one package per feature (`chess/`, `communities/`, `home/`, `thread/`,
  `user/`, `event/`, …) holding query states, filter assemblers and sub-assemblers;
  `composeSubscriptionManagers/`, `eoseManagers/`, `subscriptions/` (`ExplainedFilter`,
  `SubPurpose`), `assemblers/` (shared ones: `MetadataFilterAssembler`,
  `ReactionsFilterAssembler`, `ContactCardFilters.kt`), `oneshot/`, `paging/`, `preload/`.
- `reqCommand/RelaySubscriptionsCoordinator.kt` — owns one instance of every assembler
  (`account`, `home`, `userFinder`, `eventFinder`, `chess`, `community`, …). Screens reach it
  as `accountViewModel.dataSources()` (`BaseAccountViewModel.dataSources`).
- `commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/` — the
  `@Composable` entry points, same package names: `observeUser*`,
  `EventFinderFilterAssemblerSubscription`, `KeyDataSourceSubscription`,
  `LifecycleAwareKeyDataSourceSubscription`, and per-feature `*FilterAssemblerSubscription`s.

## First choice: `observeUser*` and `EventFinder`

Before writing any assembler, check whether these already load what you need:

- **Users** (`commonsUI/.../relayClient/user/UserMetadataObservers.kt`):
  `observeUserInfo`, `observeUserPicture`, `observeUserBanner`, `observeUserAboutMe`,
  `observeUserName(user)` each open `UserFinderFilterAssemblerSubscription(user)` and return
  a `State`. Metadata, relay lists, reports and contact cards load for on-screen users only,
  batched into one REQ per relay for the whole visible set. A `LazyColumn` composes only the
  visible window, so this is "load what's on screen" for free.
- **Notes** (`commonsUI/.../relayClient/event/EventFinderFilterAssemblerSubscription.kt`):
  `EventFinderFilterAssemblerSubscription(note)` loads the note's reactions, zaps, reposts and
  replies while composed; the observers in `event/EventObservers.kt` /
  `AccountEventObservers.kt` sit on top of it.
- Both read `LocalUserFinder`, `LocalUserFinderAccount` and `LocalEventFinder`, provided
  once in `commonsUI/.../ui/app/AmethystApp.kt` from `dataSources()`. Their defaults are
  `error(...)`, so they must never be reached from a composition with no relay client (e.g.
  the Android `:napplet` process).

Don't hand-roll "load metadata for this list". `FeedMetadataCoordinator` and
`MetadataPreloader` (`assemblers/`, `preload/`) still exist but have no production caller;
prefer the observers.

## The building blocks

```kotlin
// composeSubscriptionManagers/ComposeSubscriptionManager.kt
interface Subscribable<T> {
    fun subscribe(query: T?)          // called on main: keep it fast
    fun unsubscribe(query: T?)
    fun subscribe(query: List<T>)
    fun unsubscribe(query: List<T>)
}
abstract class ComposeSubscriptionManager<T> : ComposeSubscriptionManagerControls, Subscribable<T>
//   keeps the live query states; allKeys() snapshots them; each change calls invalidateKeys()
interface ComposeSubscriptionManagerControls { fun invalidateKeys(); fun invalidateFilters(); fun destroy() }
```

`MutableComposeSubscriptionManager<T : MutableQueryState>` is the variant for query states
that change over time (it collects their flow).

A **filter assembler** is a `ComposeSubscriptionManager` that groups **sub-assemblers**; each
sub-assembler is an EOSE manager that turns the current keys into relay filters. They are
not pure: they own subscriptions on the `INostrClient` and per-relay EOSE cursors. Real shape
(`chess/ChessFilterAssembler.kt`):

```kotlin
class ChessFilterAssembler(client: INostrClient) : ComposeSubscriptionManager<ChessQueryState>() {
    private val subAssembler = ChessFeedFilterSubAssembler(client, ::allKeys)
    val group = listOf(subAssembler)
    override fun invalidateKeys() = invalidateFilters()
    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }
    override fun destroy() = group.forEach { it.destroy() }
}
```

EOSE managers (`eoseManagers/`, all on `BaseEoseManager(client, allKeys, sampleTime = 500)`,
which batches invalidations): `SingleSubEoseManager` (one REQ for all keys, one shared EOSE
cursor per relay — `MetadataFilterAssembler` uses it), `PerUserEoseManager`,
`PerKeyEoseManager`, `PerUniqueIdEoseManager`, `PerUserAndFollowListEoseManager`,
`SingleSubNoEoseCacheEoseManager`, `AccountScopedSingleSubNoEoseCacheEoseManager`. Pick by how
the EOSE cursor must be shared. Details and the new-screen recipe:
`references/filter-assemblers.md`.

## Wiring a composable

```kotlin
// commonsUI/.../relayClient/communities/CommunityFilterAssemblerSubscription.kt
@Composable
fun CommunityFilterAssemblerSubscription(channel: AddressableNote, filterAssembler: CommunityFilterAssembler) {
    val state = remember(channel) { CommunityQueryState(channel) }   // different screens get different states
    LifecycleAwareKeyDataSourceSubscription(state, filterAssembler)
}
// call site: CommunityFilterAssemblerSubscription(note, accountViewModel.dataSources().community)
```

- `KeyDataSourceSubscription(state, dataSource)` — `DisposableEffect`: subscribe on enter,
  unsubscribe on dispose. For light, always-needed subscriptions.
- `LifecycleAwareKeyDataSourceSubscription(state, dataSource)` — also unsubscribes 30 s after
  the lifecycle stops (cancelled if it restarts), and immediately on dispose. Use it for feeds
  and anything heavy.

Events arrive in `LocalCache`; the screen renders from `LocalCache`/`Account` flows (see
`account-state`), not from the subscription.

## Rules

- Build filters as `ExplainedFilter(..., purpose = SubPurpose.X)`. The purpose never reaches the
  relay but drives the relay screens; Android debug builds wrap the client in
  `UntaggedFilterWarningClient`, which logs any untagged REQ.
- No raw `client.subscribe` or `Filter` in a composable — put it in a sub-assembler.
- A one-shot read (fetch once, page back, count) is not a compose subscription: use the
  `INostrClient` accessories or `relayClient/oneshot/` (`ProfileLoader`, `ThreadLoader`, …).
- New kinds a screen subscribes to also need `LocalCache` dispatch (see quartz's `nostr-expert`
  skill, [event-factory.md](https://github.com/vitorpamplona/quartz/blob/main/.claude/skills/nostr-expert/references/event-factory.md)), or they arrive and are dropped.

Related skills: `account-state` (`LocalCache`, feeds), quartz's `nostr-expert` (filters, kinds),
`kotlin-coroutines` (flows, dispatchers).
