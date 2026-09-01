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

/**
 * Linux/Native actual for [ConcurrentHashCache], over the same [StripedHashMap] as
 * `LargeCache.linux.kt` — read its docs for why.
 *
 * This one was the worst-placed of the copy-on-write caches: its only caller,
 * `CachingEventDecoder`, writes once per event arriving from a relay, so every decode
 * rebuilt the whole map under a CAS retry loop. Now a write touches one bucket and a
 * read takes no lock at all.
 */
actual class ConcurrentHashCache<K : Any, V : Any> {
    private val cache = StripedHashMap<K, V>()

    actual fun get(key: K): V? = cache.get(key)

    actual fun put(
        key: K,
        value: V,
    ) {
        cache.put(key, value)
    }

    actual fun size(): Int = cache.size()

    actual fun clear() {
        cache.clear()
    }
}
