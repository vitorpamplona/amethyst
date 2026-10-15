# LargeCache

[core/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/cache/LargeCache.kt](https://github.com/vitorpamplona/quartz/blob/main/core/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/cache/LargeCache.kt) —
`expect class LargeCache<K, V>()` implementing `ICacheOperations<K, V>`. It is the store
behind `LocalCache` (`commons/.../model/cache/`: `notes`, `addressables`, `users`);
see the `account-state` skill for how `LocalCache` uses it.

Actuals:

- `jvmAndroid/.../LargeCache.jvmAndroid.kt` — a `ConcurrentSkipListMap`.
- `nativeMain/.../LargeCache.native.kt` — a lock-free-read, striped-write hash table
  (`StripedHashMap`); its KDoc explains why.

API: `get`, `put`, `remove`, `containsKey`, `size`, `keys()`, `values()`, `clear`,
`getOrCreate(key) { builder }` (returns the winner under a race), `createIfAbsent` (true
when this call created it), plus the functional scans from `ICacheOperations`
(`forEach`, `filter`, `filterIntoSet`, `map`, `mapNotNull`, `mapFlatten`, `count`,
`groupBy`, `countByGroup`, `sumOf`, `maxOrNullOf`, `associate`, …) with collector types
in `CacheCollectors.kt`. Siblings: `ConcurrentHashCache.kt`, `ConcurrentLruCache.kt`.

Rules:

- Use `getOrCreate`/`createIfAbsent`, not `get` then `put` (a race).
- Scans run while writers insert; a concurrent write may or may not be seen.
- No eviction. Bounded caches use `ConcurrentLruCache`; `LocalCache` prunes via
  `CachePruner`.
- A small map does not need a `LargeCache`.
