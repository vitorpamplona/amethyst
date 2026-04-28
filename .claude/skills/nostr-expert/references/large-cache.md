# LargeCache: Platform-Aware In-Memory Store

`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/cache/LargeCache.kt` provides a thread-safe key-value cache with a functional iteration API. Used everywhere Amethyst needs to hold many events, users, or derived state in memory.

## Files

- `LargeCache.kt` — `expect class LargeCache<K, V>` and its factory `createLargeCache()`.
- `ICacheOperations.kt` — interface the cache exposes: `forEach`, `filter`, `map`, `mapNotNull`, `groupBy`, `maxOrNullOf`, `sumOf`, `count`, `any`, `firstOrNull`, etc.
- `CacheCollectors.kt` — functional collector helpers used by the cache API.

### Actual implementations

- **Android** (`androidMain`) — backed by a `ConcurrentHashMap` (and optionally `androidx.collection.LruCache` variants for size-bounded caches).
- **JVM/Desktop** (`jvmMain`) — `ConcurrentHashMap` directly.
- **iOS** (`iosMain`) — `NSMapTable`/Kotlin concurrent map wrapper.

## Core API

```kotlin
val cache: LargeCache<HexKey, Note> = LargeCache()

cache.put(id, note)
cache.get(id)                         // V?
cache.getOrCreate(id) { Note(id) }    // atomic compute-if-absent
cache.containsKey(id)
cache.remove(id)
cache.size()

// Functional iteration — thread-safe snapshot semantics
cache.forEach { key, value -> ... }
cache.filter { key, value -> value.kind == 1 }
cache.map { key, value -> value.pubKey }
cache.count { _, v -> v.isUnread }
cache.maxOrNullOf { _, v -> v.createdAt }
cache.groupBy { _, v -> v.kind }
```

The important contract: **functional operations iterate a consistent snapshot**, so you can `filter` inside a coroutine without racing concurrent writers. This is why `LocalCache` (the Amethyst event store) can be scanned to build a feed while relays are still inserting.

## When to Use

- **Event / note stores** — `LocalCache.notes: LargeCache<HexKey, Note>`.
- **User profiles** — `LocalCache.users: LargeCache<HexKey, User>`.
- **Address → event** lookups for addressable (parameterized replaceable) events.
- **Shared-secret caches** (see `SharedKeyCache.kt` — a similar pattern at smaller scale).

## When Not to Use

- Small maps (<100 entries) — regular `mutableMapOf` is fine.
- Off-process state (DB, disk) — use the `store/` event DB, not LargeCache.
- Hot one-shot lookups — if you're already inside a Flow pipeline, chain operators rather than maintaining a parallel cache.

## Gotchas

- **`getOrCreate` vs `put`** — `getOrCreate` is atomic and safe under contention; `get` then `put` is a race.
- **Iteration during mutation is safe** but the snapshot may include or exclude a concurrent write. Don't rely on a just-put value being visible inside a currently-running `forEach`.
- **Don't store `Flow`s inside LargeCache.** Cache values should be immutable / thread-safe objects. For reactive state, keep a `StateFlow` next to the cache and emit on writes.
- **No TTL / eviction by default.** If you need bounded size, wrap with `LruCache` or build an explicit eviction loop keyed off a secondary structure.

## Related

- `amethyst/src/main/java/com/vitorpamplona/amethyst/model/LocalCache.kt` — the canonical user of `LargeCache<HexKey, Note>` and `LargeCache<HexKey, User>`.
- `nip44Encryption/SharedKeyCache.kt` — smaller domain-specific cache using the same pattern.
