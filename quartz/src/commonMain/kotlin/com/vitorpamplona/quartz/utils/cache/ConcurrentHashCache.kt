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
 * A minimal thread-safe hash map for hot lookup paths: `get`/`put`/`size`.
 *
 * Exists because commonMain has no `java.util.concurrent.ConcurrentHashMap`
 * and [LargeCache]'s JVM actual is a `ConcurrentSkipListMap` (ordered,
 * O(log n) lookups). This one is unordered and tuned for read-heavy caches
 * where lookups must be lock-free on JVM/Android — e.g. the per-frame
 * duplicate check in
 * [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder].
 *
 * Actuals: JVM/Android → `ConcurrentHashMap`; Apple → `CacheMap` (same
 * backing as [LargeCache]); Linux → copy-on-write (CI-only target).
 */
expect class ConcurrentHashCache<K : Any, V : Any>() {
    fun get(key: K): V?

    fun put(
        key: K,
        value: V,
    )

    fun size(): Int

    fun clear()
}
