---
name: account-state
description: Account, LocalCache, User/Note and feeds in commons/model + commons/feeds: per-user XState objects (.flow), FeedFilter/AdditiveFeedFilter, FeedViewModel. Use when adding an account setting or list, a feed, or choosing LocalCache vs account flows.
---

# Account, LocalCache and Feeds

The client state backbone, all headless in `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/`
(abbreviated `commons/…/` below):

- `model/cache/LocalCache.kt` — `object LocalCache : EventCache()`. Every `Note` / `User` the client has
  seen. All logic is in `model/cache/EventCache.kt` (an open class so tests can build their own);
  Android and Desktop both use the `LocalCache` singleton (`AppModules.kt`, `DesktopAppModules.kt`).
- `model/Account.kt` — one `Account` per signed-in user: ~50 per-feature **state objects**
  (`kind3FollowList`, `nip65RelayList`, `muteList`, `bookmarkState`, …), each exposing `.flow`.
- `feeds/` + `viewmodels/` + `<feature>/dal/` — feeds: a `FeedFilter` scans `LocalCache`, a
  `FeedContentState` / `FeedViewModel` keeps the result live.

```
relay EVENT ─► EventCache.justConsume ─► Note/User updated, indexes, live.newNote(...)
                     │                                   │
                     ▼                                   ▼
   XState pins its addressable note,            LocalCacheFlow.newEventBundles
   note.flow().metadata → XState.flow           → FeedViewModel → FeedFilter.applyFilter
                     │                                   │
                     └────────► ViewModels / composables ◄┘
```

## Account state objects

Pattern, copied from `model/nip51Lists/muteList/MuteListState.kt`:

- **Pin the note.** `val muteListNote = cache.getOrCreateAddressableNote(MuteListEvent.createAddress(signer.pubKey))`.
  `LocalCache`'s `notes` / `addressables` are `LargeSoftCache` (weak references), so the state
  object's field is what keeps its note from being collected.
- **Expose one `StateFlow`.** Map `muteListNote.flow().metadata.stateFlow` → decrypt (private lists go
  through a per-feature `…DecryptionCache` such as `MuteListDecryptionCache`, held next to it in
  `Account` as `muteListDecryptionCache`) → fall back to the `AccountSettings` backup
  (`settings.backupMuteList`) when the event is absent → `stateIn(scope, SharingStarted.Eagerly, …)`.
- **Mutation helpers return a signed event — publishing it is the caller's job.**
  `MuteListState.hideUser(pubkey)` returns a `MuteListEvent`; `Account.hideUser` does
  `sendMyPublicAndPrivateOutbox(muteList.hideUser(pubkeyHex))`. A state change you never send is
  invisible to every other client and is lost when the next relay copy arrives.
- Consumers read `account.<feature>.flow`, never a `MutableStateFlow` on `Account`.

Full catalog of properties, classes, kinds and packages: `references/account-state-flow.md`.

### Adding an account-scoped setting or list

1. Persisted as a Nostr event? Pick the kind (NIP-51 list, NIP-78 app data, a relay list) and make
   sure quartz has the event class (see quartz's `nostr-expert` skill; a new one is a quartz
   change, built here with `-PquartzDir=../quartz`).
2. Add `commons/…/model/nipXX…/XState.kt` modeled on `MuteListState` (encrypted) or
   `nip51Lists/BookmarkListState.kt` (plain): pin, flow, suspend mutation helpers that build the
   event with the quartz class and sign it.
3. Instantiate it as a `val` in `Account.kt` (plus its `DecryptionCache` if private) and add an
   `Account` method that sends the returned event (`sendMyPublicAndPrivateOutbox`, `sendAutomatic`, …).
   If the list must survive relay loss, add a `backupX` field to `AccountSettings`.
4. Make sure `EventCache.justConsumeInnerInner` lists the event class — a kind that is not listed
   falls into the `else` branch and is **rejected**, so the state object never sees it, even though
   `EventFactory` parses it. Replaceable kinds without custom logic go in the
   `consumeBaseReplaceable` group.
5. Relay subscription: `relay-client`. UI: settings screens are shared in
   `commonsUI/…/commons/ui/screen/loggedIn/settings/`; strings follow the AGENTS.md *Strings* rules.

## LocalCache vs Account flow vs feed

| You render… | Read |
|---|---|
| one note / user you have an id for | `LocalCache.getOrCreateNote(id)` / `getOrCreateUser(pubkey)`, then `note.flow().metadata` (or `replies`, `reactions`, `zaps`, …) |
| "my follows / mutes / relays / bookmarks" | `account.kind3FollowList.flow`, `account.muteList.flow`, `account.nip65RelayList.flow`, `account.hiddenUsers.flow`, … |
| a list of notes | a `FeedFilter` + `FeedContentState` / `FeedViewModel` — `references/feeds.md` |
| ad-hoc filter results | `LocalCache.observeNotes(filter: Filter): Flow<List<Note>>` |

Never scan `LocalCache.notes` inside a composable.

## Feeds in one paragraph

Bases are in `commons/…/feeds/`: `IFeedFilter` / `FeedFilter<T>` (`feed()`, `feedKey()`, `limit()`
default 500, `showHiddenKey()`), `AdditiveFeedFilter<T>` (adds `applyFilter` + `sort` for
incremental merges), `AdditiveComplexFeedFilter<T, U>` (incoming type differs from row type, e.g.
`home/dal/HomeLiveFilter.kt`), `ChangesFlowFilter<T>`, `FilterByListParams`, `DefaultFeedOrder` /
`sortedByDefaultFeedOrder()`, `FeedContentState`, `FeedState`. Concrete filters are **shared**, in
`commons/…/<feature>/dal/` (`home/dal/`, `hashtag/dal/`, `notifications/dal/`, ~55 of them). The
account's main-tab feeds are long-lived `FeedContentState`s in `viewmodels/AccountFeedContentStates.kt`
(owned by `BaseAccountViewModel.feedStates`); per-screen feeds use a `FeedViewModel` subclass. Recipe,
real code and traps: `references/feeds.md`.

## Gotchas

- **`LocalCache` is shared across accounts.** Switching accounts doesn't wipe it; every `Account`
  derives its flows from the same cache. Filters that depend on the account must put the account
  pubkey in `feedKey()`.
- **Hold the `Note`, not the `Event`.** `getOrCreate*` returns the same wrapper per id; for
  replaceable/addressable notes `note.event` is swapped in place when a newer version arrives.
- **Flows on a `Note` are not durable.** `CachePruner` (`model/cache/CachePruner.kt`) calls
  `clearFlow()` on notes during cleanup, and anything not strongly held can be collected. Re-fetch
  via `note.flow()`; don't stash flow references.
- **Observers can fire before derived state exists.** `EventCache` notifies observers
  (`refreshNewNoteObservers`) as part of consuming an event, before some of what it derives from it
  (channels, indexes) is filled in. Re-read state in the collector rather than trusting it is complete.
- **Memory pressure trims.** `MemoryTrimmingService` (`model/cache/`) is run by both app modules,
  and `AccountFeedContentStates` trims every feed to 200 items on `memoryPressure`. Never assume a
  previously seen note is still resident.

## References

- `references/account-state-flow.md` — catalog of `Account` state objects, kinds and packages.
- `references/local-cache.md` — `EventCache` structures, insertion path, lookups, pruning.
- `references/feeds.md` — adding a feed: filter, state holder, invalidation, rendering, tests.
- Related skills: quartz's `nostr-expert` (event classes, `EventFactory`), `relay-client` (subscriptions),
  `auth-signers` (how mutations sign), `compose-expert` (rendering).
