---
name: account-state
description: Account state and in-memory event store patterns in Amethyst. Use when working with `Account.kt` (per-user state objects — `kind3FollowList`, `nip65RelayList`, `muteList`, `bookmarkState`, each exposing a `.flow` StateFlow), `LocalCache` (the object-level event store backed by `LargeCache`), `User`/`Note` model classes, or any ViewModel that reads user-specific state. Covers how account events cascade from relay arrival to UI state, how to add a new account-scoped setting, and when to read from `LocalCache` vs subscribe to a StateFlow.
---

# Account & Local Cache State

The backbone of Amethyst's client state: one `Account` per signed-in user, plus the singleton `LocalCache` that holds every `Note` and `User` the client has seen.

## When to Use This Skill

- Working on `amethyst/src/main/java/com/vitorpamplona/amethyst/model/Account.kt`
- Working on `amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt`
- Adding a new account-scoped setting (mutes, bookmarks, custom relay lists, private lists)
- Reading/writing user metadata (`User`) or note state (`Note`)
- Deciding whether to query `LocalCache` vs subscribe to an `Account` StateFlow

## Mental Model

```
Relay frame ──► LocalCache.insertOrUpdateNote() ──► LocalCacheFlow emits change
                                                            │
                                                            ▼
                                Account state objects pin the relevant addressable notes
                                                            │
                                                            ▼
                            State-object `.flow` updates (kind3FollowList, nip65RelayList, muteList, …)
                                                            │
                                                            ▼
                                                    ViewModels collect
                                                            │
                                                            ▼
                                                 Composables render
```

`LocalCache` is the event store. `Account` is the *derived* per-user view (follow list, relays, mutes, emojis, bookmarks, etc.). UI listens to the `.flow` of `Account`'s state objects, not directly to `LocalCache`, except for note-level rendering.

## Key Files

### `Account.kt` (singleton-per-session)

- `class Account(...)` — holds 50+ **state objects**, one per feature, each wired to a specific Nostr kind:
  - `kind3FollowList = Kind3FollowListState(...)` ← NIP-02 ContactList (kind 3)
  - `nip65RelayList = Nip65RelayListState(...)` ← NIP-65 RelayList (kind 10002), plus siblings `dmRelayList`, `searchRelayList`, `blockedRelayList`, `trustedRelayList`, `proxyRelayList`, `broadcastRelayList`, `indexerRelayList`, …
  - `muteList = MuteListState(...)` ← NIP-51 MuteList (kind 10000)
  - `bookmarkState = BookmarkListState(...)` ← NIP-51 Bookmarks (kind 10003), plus `labeledBookmarkLists`, `pinState`, `interestSets`, `peopleLists`, `followLists`, `hashtagList`, `geohashList`, `communityList`, `emoji`, `blossomServers`, …
  - Derived/merged views: `hiddenUsers`, `allFollows`, `homeRelays`, `outboxRelays`, `dmRelays`, `notificationRelays`, `trustedRelays`, and the `live*FollowListsPerRelay` outbox loaders.
- **The pattern:** each `XState` class pins its addressable note via `cache.getOrCreateAddressableNote(address)` (a long-term reference so GC/eviction can't drop it), exposes `val flow: StateFlow<…>` derived from the note's metadata flow (decrypted through a per-feature `DecryptionCache`, with backup fallback from `AccountSettings`, `stateIn(scope, Eagerly, …)`), and offers suspend mutation helpers (e.g. `MuteListState.hideUser(pubkey)`) that build the updated signed event. Consumers read `account.muteList.flow`, never a raw `MutableStateFlow` on `Account`.
- Encrypted lists pair the state object with a `DecryptionCache` sibling (`muteListDecryptionCache`, `peopleListDecryptionCache`, …) so NIP-44 decryption results are cached per event.
- UI reads via `collectAsStateWithLifecycle` on Android and `collectAsState` on Desktop.
- Sibling files per feature live alongside: `AccountSettings.kt`, `AccountSyncedSettings.kt`, plus per-NIP state classes under `model/nip02FollowLists/`, `model/nip51Lists/`, `model/nip65RelayList/`, etc.

### `LocalCache.kt`

- `object LocalCache : ILocalCache, ICacheProvider` — the singleton event store.
- Primary structures (all `LargeCache` — see `nostr-expert/references/large-cache.md`):
  - `notes: LargeCache<HexKey, Note>` — every seen event (regular + addressable + replaceable) keyed by id or d-address.
  - `users: LargeCache<HexKey, User>` — every seen pubkey, lazily populated.
  - `addressables: LargeCache<Address, Note>` — secondary index for `kind:pubkey:d-tag` lookups.
  - `channels`, `deletionIndex`, `hashtagIndex`, …
- `LocalCacheFlow` emits coarse-grained "something changed, recheck" signals. Fine-grained reactivity lives in `Account`'s per-kind StateFlows.
- Eviction is driven by `MemoryTrimmingService` (android service) under pressure.

### Model classes

- `User.kt` — mutable profile holder. Contains metadata, follow/follower counts, relay lists, liveset of notes authored.
- `Note.kt` — mutable note holder. Contains the underlying `Event`, replies, reactions, zaps. Mutation via `addReply`, `addReaction`, `addZap`, emitted on `Note.flowSet` flows.
- `Constants.kt` — DEFAULT_RELAYS, magic kinds/limits not covered by quartz.

## Adding a New Account-Scoped Setting

Typical recipe:

1. If the setting is persisted as a Nostr event, pick the right kind (e.g. NIP-51 list, NIP-78 app-specific data, NIP-65 relay list).
2. Add a model folder under `amethyst/.../model/nipXX…/` with an `XState` class modeled on an existing one (`MuteListState` for an encrypted list, `BookmarkListState` for a plain one):
   - Pin the addressable note: `val xNote = cache.getOrCreateAddressableNote(XEvent.createAddress(signer.pubKey))`.
   - Expose `val flow: StateFlow<…>` mapped from `xNote.flow().metadata.stateFlow`, decrypting through a per-feature `DecryptionCache` if the list is private, with backup fallback from `AccountSettings`, then `stateIn(scope, Eagerly, default)`.
   - Add suspend mutation helpers that build the updated event via the quartz event class (`XEvent.add/remove/create`) and return it signed.
3. In `Account.kt`, instantiate the state object (and its `DecryptionCache` sibling if encrypted) as a `val`. Publishing the returned event goes through `Account`'s send path; the relay subscription side is the relayClient pattern (see `relay-client` skill).
4. Add UI that `collect`s `account.x.flow`. Settings screens live in `amethyst/.../ui/screen/loggedIn/settings/`.

## `LocalCache` vs `Account` Flow — Which to Read?

- **Are you rendering a specific note / user you hold an id for?** → `LocalCache.getOrCreateNote(id)` + collect `note.flowSet.metadata`.
- **Are you rendering "my follows", "my mutes", "my relays"?** → `account.<feature>.flow` (e.g. `account.kind3FollowList.flow`, `account.muteList.flow`, `account.nip65RelayList.flow`).
- **Are you rendering a feed?** → Use a `FeedFilter` + `FeedViewModel` (see `feed-patterns` skill). Don't scan `LocalCache` in a composable.

## Gotchas

- **`LocalCache` is a singleton across accounts.** Switching accounts doesn't wipe it — `Account` re-derives its flows from the same cache.
- **Don't store Flows inside `Note` / `User`** expecting them to survive eviction. Eviction drops the whole object.
- **State-object mutation helpers return a signed event — publishing it is the caller's job.** A locally updated list without a publish means other clients won't see it.
- **`Note` is mutable** — treat instances as identity-based (same id → same Note). Use `.flowSet` when you need reactive state.
- **`MemoryTrimmingService` can evict aggressively** on Android under pressure. Don't assume a previously-seen note is still resident.

## References

- `references/account-state-flow.md` — catalog of major `Account` state objects and their source kinds.
- `references/local-cache.md` — `LocalCache` internals, insertion path, indexes.
- Complements: `nostr-expert` (event parsing), `relay-client` (subscription wiring), `feed-patterns` (how feeds consume this state), `auth-signers` (how mutation signs events).
