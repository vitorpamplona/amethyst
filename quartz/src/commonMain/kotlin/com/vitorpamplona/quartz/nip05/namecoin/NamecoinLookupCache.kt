/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip05.namecoin

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CachedResult(
    val result: NamecoinNostrResult?,
    val timestamp: Long = System.currentTimeMillis(),
)

class NamecoinLookupCache(
    private val maxEntries: Int = 500,
    private val ttlMs: Long = 3_600_000L,
) {
    private val cache = LinkedHashMap<String, CachedResult>(maxEntries, 0.75f, true)
    private val mutex = Mutex()

    private fun key(id: String) = id.trim().lowercase()

    suspend fun get(id: String): CachedResult? =
        mutex.withLock {
            val e = cache[key(id)] ?: return null
            if (System.currentTimeMillis() - e.timestamp > ttlMs) {
                cache.remove(key(id))
                return null
            }
            e
        }

    suspend fun put(
        id: String,
        result: NamecoinNostrResult?,
    ) = mutex.withLock {
        val k = key(id)
        if (cache.size >= maxEntries) cache.entries.firstOrNull()?.let { cache.remove(it.key) }
        cache[k] = CachedResult(result)
    }

    suspend fun clear() = mutex.withLock { cache.clear() }
}
