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
package com.vitorpamplona.quartz.utils.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * A bounded, thread-safe cache with a **lock-free [get]**.
 *
 * The common alternative — a `Collections.synchronizedMap` wrapping an
 * access-order `LinkedHashMap`, or an `@Synchronized`-guarded `LinkedHashMap` —
 * takes a single monitor on *every* operation, including reads (an access-order
 * map structurally mutates on `get`, so it cannot be read without the lock).
 * On hot read paths (zap-receipt validation, feed rich-text rendering) that one
 * monitor serializes every reader across all dispatcher threads.
 *
 * Here storage is a [ConcurrentHashMap], so [get] never takes a lock. [put] and
 * [clear] hold a small monitor that keeps the map and the recency order
 * consistent; that lock is off the read path entirely. Eviction is
 * **least-recently-put** order (a `get` does not refresh recency); re-putting a
 * key moves it to the youngest position. This matches the semantics the previous
 * monitor-based caches relied on and keeps the read path contention-free.
 *
 * Because writes and eviction happen atomically under the monitor while readers
 * see the [ConcurrentHashMap] directly, an external [size] can transiently
 * observe at most `maxSize + 1` (the instant a new entry is inserted before the
 * over-cap entry is evicted, within a single locked section). It settles to
 * `<= maxSize` once writers quiesce.
 *
 * Keys and values must be non-null (a [ConcurrentHashMap] constraint).
 */
class ConcurrentLruCache<K : Any, V : Any>(
    private val maxSize: Int,
) {
    init {
        require(maxSize > 0) { "maxSize must be > 0, was $maxSize" }
    }

    private val map = ConcurrentHashMap<K, V>()

    // Guards writes + eviction so the map and the recency order stay consistent.
    // Reads never touch it. Writes are the cold path here, so serializing them
    // is fine; the point is a lock-free [get].
    private val writeLock = Any()
    private val order = ArrayDeque<K>()

    fun get(key: K): V? = map[key]

    fun put(
        key: K,
        value: V,
    ) {
        synchronized(writeLock) {
            // Re-inserting an existing key makes it the youngest again.
            val existed = map.put(key, value) != null
            if (existed) order.remove(key)
            order.addLast(key)
            while (order.size > maxSize) {
                val oldest = order.removeFirst()
                map.remove(oldest)
            }
        }
    }

    fun clear() {
        synchronized(writeLock) {
            order.clear()
            map.clear()
        }
    }

    fun size(): Int = map.size
}
