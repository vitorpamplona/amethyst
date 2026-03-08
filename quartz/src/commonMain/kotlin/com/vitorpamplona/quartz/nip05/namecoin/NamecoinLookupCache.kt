/**
 * NamecoinLookupCache.kt
 *
 * In-memory LRU cache for Namecoin name resolution results.
 * SPDX-License-Identifier: MIT
 */
package com.vitorpamplona.quartz.nip05.namecoin

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CachedResult(val result: NamecoinNostrResult?, val timestamp: Long = System.currentTimeMillis())

class NamecoinLookupCache(
    private val maxEntries: Int = 500,
    private val ttlMs: Long = 3_600_000L,
) {
    private val cache = LinkedHashMap<String, CachedResult>(maxEntries, 0.75f, true)
    private val mutex = Mutex()
    private fun key(id: String) = id.trim().lowercase()

    suspend fun get(id: String): CachedResult? = mutex.withLock {
        val e = cache[key(id)] ?: return null
        if (System.currentTimeMillis() - e.timestamp > ttlMs) { cache.remove(key(id)); return null }
        e
    }

    suspend fun put(id: String, result: NamecoinNostrResult?) = mutex.withLock {
        val k = key(id)
        if (cache.size >= maxEntries) cache.entries.firstOrNull()?.let { cache.remove(it.key) }
        cache[k] = CachedResult(result)
    }

    suspend fun clear() = mutex.withLock { cache.clear() }
}
