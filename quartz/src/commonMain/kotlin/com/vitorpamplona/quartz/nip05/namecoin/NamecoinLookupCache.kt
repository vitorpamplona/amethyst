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

import androidx.collection.LruCache
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CachedResult(
    val result: NamecoinNostrResult?,
    val timestamp: Long = TimeUtils.now(),
)

class NamecoinLookupCache(
    private val maxEntries: Int = 500,
    private val ttlSecs: Long = 3_600L, // 1 hour
) {
    private val cache = LruCache<String, CachedResult>(maxEntries)
    private val mutex = Mutex()

    /**
     * Normalised cache key from the user's raw input.
     */
    private fun cacheKey(identifier: String): String = identifier.trim().lowercase()

    suspend fun get(identifier: String): CachedResult? =
        mutex.withLock {
            val key = cacheKey(identifier)
            val entry = cache[key] ?: return null
            val age = TimeUtils.now() - entry.timestamp
            if (age > ttlSecs) {
                cache.remove(key)
                return null
            }
            return entry
        }

    suspend fun put(
        identifier: String,
        result: NamecoinNostrResult?,
    ) = mutex.withLock {
        val key = cacheKey(identifier)
        cache.put(key, CachedResult(result))
    }

    suspend fun invalidate(identifier: String) =
        mutex.withLock {
            cache.remove(cacheKey(identifier))
        }

    suspend fun clear() =
        mutex.withLock {
            cache.evictAll()
        }
}
