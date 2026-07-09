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
package com.vitorpamplona.quartz.utils.concurrent

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Copy-on-write, mirroring ConcurrentHashCache.linux: correct and simple. The
// native targets never run the crawl this backs (it is JVM/Android-only work);
// they only compile it, so the O(n)-per-write cost is irrelevant. A CAS retry
// loop gives getOrPut/merge the same atomicity the JVM actual gets for free.
@OptIn(ExperimentalAtomicApi::class)
actual class ConcurrentMap<K : Any, V : Any> {
    private val ref = AtomicReference(HashMap<K, V>())

    actual operator fun get(key: K): V? = ref.load()[key]

    actual operator fun set(
        key: K,
        value: V,
    ) {
        while (true) {
            val cur = ref.load()
            val copy = HashMap(cur)
            copy[key] = value
            if (ref.compareAndSet(cur, copy)) return
        }
    }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V {
        while (true) {
            val cur = ref.load()
            cur[key]?.let { return it }
            val value = defaultValue()
            val copy = HashMap(cur)
            copy[key] = value
            if (ref.compareAndSet(cur, copy)) return value
        }
    }

    actual fun merge(
        key: K,
        value: V,
        remap: (old: V, new: V) -> V,
    ): V {
        while (true) {
            val cur = ref.load()
            val old = cur[key]
            val merged = if (old == null) value else remap(old, value)
            val copy = HashMap(cur)
            copy[key] = merged
            if (ref.compareAndSet(cur, copy)) return merged
        }
    }

    actual fun size(): Int = ref.load().size

    actual fun snapshot(): Map<K, V> = HashMap(ref.load())
}
