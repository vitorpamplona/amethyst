---
name: account-state
description: Account state and in-memory event store patterns in Amethyst. Use when working with `Account.kt` (per-user StateFlow properties — follow list, relays, settings, mutes, bookmarks), `LocalCache` (the object-level event store backed by `LargeCache`), `User`/`Note` model classes, or any ViewModel that reads user-specific state. Covers how account events cascade from relay arrival to UI state, how to add a new account-scoped setting, and when to read from `LocalCache` vs subscribe to a StateFlow.
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
                                Account observes relevant kinds (3, 10002, 10000, …)
                                                            │
                                                            ▼
                                     Account StateFlow updates (followList, relays, mutes, …)
                                                            │
                                                            ▼
                                                    ViewModels collect
                                                            │
                                                            ▼
                                                 Composables render
```

`LocalCache` is the event store. `Account` is the *derived* per-user view (follow list, relays, mutes, emojis, bookmarks, etc.). UI listens to `Account`'s StateFlows, not directly to `LocalCache`, except for note-level rendering.

## Key Files

### `Account.kt` (singleton-per-session)

- `class Account(...)` — holds 50+ StateFlow properties, each wired to a specific Nostr kind:
  - `followListFlow` ← NIP-02 ContactList (kind 3)
  - `relayListFlow` ← NIP-65 RelayList (kind 10002)
  - `muteListFlow` ← NIP-51 Lists (kind 10000)
  - `bookmarkListFlow` ← NIP-51 Lists (kind 10003)
  - `topNavFeedsFlow`, `marmotGroupsFlow`, `customEmojisFlow`, `privateBookmarksFlow`, etc.
  - Settings: `defaultZapAmountsFlow`, `theme`, `language`, `proxyFlow`, `showSensitiveContentFlow`, …
- Each flow has a private `MutableStateFlow` and a public read-only `StateFlow` view. Mutation goes through specific methods (`sendPost`, `follow(pubKey)`, `addBookmark(...)`) that both update the flow and publish the signed replaceable event.
- Uses `CoroutinesExt.launchIO` for network / crypto; UI reads via `collectAsStateWithLifecycle` on Android and `collectAsState` on Desktop.
- Sibling files per feature live alongside: `AccountSettings.kt`, `AccountSyncedSettings.kt`, plus per-NIP state objects under `model/nip02FollowLists/`, `model/nip51Lists/`, `model/nip65RelayList/`, etc.

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
2. Add a model folder under `amethyst/.../model/nipXX…/` with an `ExtState`/builder class if needed.
3. In `Account.kt`:
   - Add a private `MutableStateFlow<T>`.
   - Expose a `StateFlow<T>` read view.
   - Subscribe to the relay (via the relayClient subscription pattern — see `relay-client` skill).
   - On event arrival, parse with the quartz event class and update the flow.
   - Write a mutation method (`updateX(...)`) that builds a new event via the corresponding `TagArrayBuilder`, signs through `NostrSigner`, and publishes.
4. Add UI that `collect`s the flow. Settings screens live in `amethyst/.../ui/screen/loggedIn/settings/`.

## `LocalCache` vs `Account` Flow — Which to Read?

- **Are you rendering a specific note / user you hold an id for?** → `LocalCache.getOrCreateNote(id)` + collect `note.flowSet.metadata`.
- **Are you rendering "my follows", "my mutes", "my relays"?** → `Account.<featureFlow>`.
- **Are you rendering a feed?** → Use a `FeedFilter` + `FeedViewModel` (see `feed-patterns` skill). Don't scan `LocalCache` in a composable.

## Gotchas

- **`LocalCache` is a singleton across accounts.** Switching accounts doesn't wipe it — `Account` re-derives its flows from the same cache.
- **Don't store Flows inside `Note` / `User`** expecting them to survive eviction. Eviction drops the whole object.
- **Mutations to `Account` flows must also publish the signing event.** A flow update without a publish means other clients won't see it.
- **`Note` is mutable** — treat instances as identity-based (same id → same Note). Use `.flowSet` when you need reactive state.
- **`MemoryTrimmingService` can evict aggressively** on Android under pressure. Don't assume a previously-seen note is still resident.

## References

- `references/account-state-flow.md` — catalog of major `Account` StateFlow properties and their source kinds.
- `references/local-cache.md` — `LocalCache` internals, insertion path, indexes.
- Complements: `nostr-expert` (event parsing), `relay-client` (subscription wiring), `feed-patterns` (how feeds consume this state), `auth-signers` (how mutation signs events).
