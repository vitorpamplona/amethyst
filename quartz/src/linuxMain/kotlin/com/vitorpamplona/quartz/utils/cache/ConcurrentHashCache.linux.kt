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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Linux/Native actual for [ConcurrentHashCache].
 *
 * Same fix, and for the same reason, as `LargeCache.linux.kt` — read its docs for the
 * full rationale. This was copy-on-write over a plain `HashMap`, so every [put] rebuilt
 * the entire map: O(n) per write, and a CAS retry loop that re-did the whole rebuild on
 * every lost race. Bad anywhere; worst here, because the only caller is
 * `CachingEventDecoder`, which writes once per event arriving from a relay.
 *
 * A HAMT keeps the wait-free single-load read and the non-blocking write while making
 * the write O(log32 n) — [PersistentMap.putting] shares structure with the map it came
 * from and copies only the path to the changed key.
 */
@OptIn(ExperimentalAtomicApi::class)
actual class ConcurrentHashCache<K : Any, V : Any> {
    private val ref = AtomicReference<PersistentMap<K, V>>(persistentHashMapOf())

    actual fun get(key: K): V? = ref.load()[key]

    actual fun put(
        key: K,
        value: V,
    ) {
        while (true) {
            val current = ref.load()
            val next = current.putting(key, value)
            if (next === current || ref.compareAndSet(current, next)) return
        }
    }

    actual fun size(): Int = ref.load().size

    actual fun clear() {
        ref.store(persistentHashMapOf())
    }
}
