# LocalCache: The Singleton Event Store

`amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt` is the singleton (`object LocalCache`) that holds every event the client has received during the session. All rendering, all feed building, all search goes through it.

## Shape

```kotlin
object LocalCache : ILocalCache, ICacheProvider {
    val notes:        LargeCache<HexKey, Note>
    val users:        LargeCache<HexKey, User>
    val addressables: LargeCache<Address, Note>
    val channels, deletionIndex, hashtagIndex, …
}
```

All `LargeCache<K,V>` — see `nostr-expert/references/large-cache.md`. Thread-safe `getOrCreate`, functional scan with `forEach` / `filter` / `map`.

## Insertion Path

```
Relay frame (EVENT "sub-id" {...})
   │
   ▼
RelayPool / subscription manager calls parseNostrEvent(json)
   │
   ▼
EventFactory.create(kind, ...) → typed Event subclass
   │
   ▼
LocalCache.consume(event) / insertOrUpdateNote(event)
   │
   ├─ notes.getOrCreate(id) { Note(id) } — finds/creates the Note wrapper
   ├─ updates note.event if this is a newer replaceable / first time for regular
   ├─ reindex: hashtag tags → hashtagIndex, addressable → addressables, deletions → deletionIndex
   ├─ for metadata: user.latestMetadata = event; user.liveMetadata.tryEmit(user)
   └─ LocalCacheFlow signals listeners that something changed
```

`Note` and `User` are mutable wrappers — `getOrCreate` returns the same object across subsequent inserts for the same id/pubkey, which is why other code can `remember(noteId)` a `Note` reference and have it stay fresh.

## Lookup

```kotlin
// By id (regular or replaceable)
val note: Note = LocalCache.getOrCreateNote(id)

// By `kind:pubkey:d-tag`
val addressable: Note? = LocalCache.getAddressableNoteIfExists(address)

// By pubkey
val user: User = LocalCache.getOrCreateUser(pubKey)

// By hashtag
LocalCache.hashtagIndex.filter { _, notes -> ... }
```

All `getOrCreate*` functions are safe to call from any thread. They return immediately; they do NOT trigger network I/O.

## Eviction

Android-only. `amethyst/.../service/eventCache/MemoryTrimmingService.kt` listens for `ComponentCallbacks2.onTrimMemory` levels and drops least-recently-used entries from `notes` and `users`. On aggressive eviction, previously-returned `Note` / `User` references remain usable (they're just detached from the cache) but any new ids will produce new objects.

## Reactive Consumption

### Note-level

```kotlin
val note = LocalCache.getOrCreateNote(id)
val metadata by note.flowSet.metadata.collectAsState()
// `flowSet` has flows for: metadata, replies, reactions, zaps, reports, …
```

### Global

```kotlin
LocalCacheFlow.live.collectLatest {
    // coarse "something changed" ping — used by feeds to re-run filters
}
```

For per-feature reactivity (follow list changed, relays changed), prefer `Account.<featureFlow>` over `LocalCacheFlow`.

## Deletion / Replacement

- **Regular events**: once inserted, the first event wins unless explicitly deleted via a kind-5 deletion. `deletionIndex` tracks ids to hide.
- **Replaceable** (kinds 0, 3, 10000-19999): a newer `created_at` replaces the older event in-place on the same `Note` wrapper.
- **Addressable** (kinds 30000-39999): same as replaceable but keyed by `kind:pubkey:d-tag` in the `addressables` index.

## Gotchas

- **Don't hold a direct `Event` reference** — hold the `Note` wrapper. The `Note.event` field can be replaced by newer replaceable/addressable events behind your back.
- **`LocalCache` is process-global**. Tests must either use a dedicated test fixture or reset it between cases.
- **No TTL beyond memory pressure.** A long-running session accumulates. If you need bounded retention, do it at the feed / filter layer.
- **Scanning the full cache is expensive** in hot paths. Always prefer an index (hashtag, addressable) or a pre-built feed filter.
- **Eviction is not atomic with in-flight coroutines**. If you `forEach` during low-memory, you may see concurrent removals — that's fine, the snapshot semantics in `LargeCache` keep it safe, but your result set shrinks.

## Related

- `nostr-expert/references/large-cache.md` — the underlying cache primitive.
- `nostr-expert/references/event-factory.md` — how raw JSON becomes the typed `Event` that `LocalCache` stores.
- `feed-patterns` skill — how feeds scan and observe `LocalCache` efficiently.
