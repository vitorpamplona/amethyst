# LocalCache: the in-memory event store

`commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/model/cache/`:

- `LocalCache.kt` — `object LocalCache : EventCache()`, the process-wide instance both app modules use.
- `EventCache.kt` — all the logic (`open class EventCache : ILocalCache, ICacheProvider, Dao`).
  It is a class so a test can build a cache of its own instead of sharing the singleton.

## Structures (fields of `EventCache`)

```kotlin
val users = LargeSoftCache<HexKey, User>()
val notes = LargeSoftCache<HexKey, Note>()
val addressables = LargeSoftCache<Address, AddressableNote>()

val chatroomList = LargeCache<HexKey, ChatroomList>()
val publicChatChannels = LargeCache<HexKey, PublicChatChannel>()
val liveChatChannels = LargeCache<Address, LiveActivitiesChannel>()
// … ephemeralChannels, geohashChannels, relayGroupChannels, concordChannels
val deletionIndex = DeletionIndex()
val observables = FilterIndex<Observable>()
val pruner = CachePruner(this)
val search = CacheSearch(this)
val live: LocalCacheFlow = LocalCacheFlow()
```

`LargeSoftCache` (`LargeSoftCache.kt`) holds **weak references** (a `ConcurrentSkipListMap` of weak
refs on JVM/Android). A `Note` or `User` nobody holds strongly can be collected, which is why every
account state object keeps its note in a field. Channel maps are plain `LargeCache` (quartz `utils/cache/LargeCache.kt`; see
quartz's `nostr-expert` skill).

## Insertion path

```
relay EVENT
  └─ CacheClientConnector (commons/src/jvmAndroid/.../relayClient/) → cache.justConsume(event, relay, false)
       ├─ already deleted? → re-send the deletion to that relay, drop the event
       ├─ addressable and we hold a newer version? → re-send ours to that relay
       └─ justConsumeAndUpdateIndexes
            ├─ getOrCreateAddressableNote(address) / getOrCreateNote(id)
            ├─ justConsumeInnerInner(event, …)   ← per-class `when`: a typed consume(...) overload,
            │                                       consumeBaseReplaceable, … ; unlisted class → rejected
            └─ if new: hint / mint / gatherer indexes
       consume(...) → refreshNewNoteObservers(note): matching `observables`, then live.newNote(note)
```

Locally signed events enter through `justConsumeMyOwnEvent(event)`; embedded events through
`consumeEmbedded(event)`.

## Lookups

| Need | Call |
|---|---|
| note by id or address string | `getOrCreateNote(hex)`, `getNoteIfExists(hex)`, `checkGetOrCreateNote(hex)` (null for bad input) |
| addressable | `getOrCreateAddressableNote(address)`, `getAddressableNoteIfExists(address)` |
| user | `getOrCreateUser(pubkey)`, `getUserIfExists(pubkey)`, `checkGetOrCreateUser(key)` |
| live filter results | `observeNotes(filter: Filter): Flow<List<Note>>` |
| text search | `search` (`CacheSearch.kt`) |

`getOrCreate*` never does network I/O and returns the same wrapper for the same id while it is alive.

## Reactive consumption

- One note: `note.flow()` returns its `NoteFlowSet` (`metadata`, `replies`, `reactions`, `boosts`,
  `zaps`, `reports`, `relays`, `ots`, `edits`, `labels`). `flowSet` is null until someone calls `flow()`.
- One user's profile: `user.metadata().flow`.
- Everything: `LocalCache.live.newEventBundles` / `deletedEventBundles` — `SharedFlow<Set<Note>>`
  batched by `BundledInsert` (1 s windows), `DROP_OLDEST` with a 100-bundle buffer. Feeds consume
  it through `ICacheProvider.getEventStream()`.

## Replacement and deletion

- Regular events: first copy wins; kind-5 deletions go to `deletionIndex`, and a later copy of a
  deleted event is refused (`deletionIndex.hasBeenDeleted`).
- Replaceable and addressable events: the newest `created_at` replaces `note.event` in place on the
  same wrapper (addressables are keyed by `Address`). Hold the `Note`, never the `Event`.

## Pruning and memory

- `CachePruner` (`pruner`): `pruneOldMessages`, `pruneHiddenMessages`, `pruneExpiredEvents`,
  `prunePastVersionsOfReplaceables`, `pruneRepliesAndReactions`, `cleanObservers` (calls
  `clearFlow()` on every note).
- `MemoryTrimmingService` (`model/cache/MemoryTrimmingService.kt`) drives it; `AppModules.kt` and
  `DesktopAppModules.kt` each create one.
- No TTL beyond that. A long session accumulates; bound retention in the feed (`limit()`), not by
  hoping the cache drops things.

## Gotchas

- **A new event class must be routed in `justConsumeInnerInner`.** `EventFactory` parsing it is not
  enough; an unlisted class hits the `else` branch and is rejected.
- **Observers fire inside consume.** `refreshNewNoteObservers` runs as part of `consume(...)`, so
  state the cache derives from that event (channels and similar) may not be filled in yet when a
  collector wakes. Re-read in the collector.
- **Full scans are expensive.** `notes.filterIntoSet { … }` over the whole cache is what a feed's
  `feed()` does on refresh; never do it per frame or in composition.
- **Processes**: `LocalCache` lives only in the Android main process — see AGENTS.md *Android Runtime
  Processes*.
