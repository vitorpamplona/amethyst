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

import kotlin.concurrent.AtomicReference

// Copy-on-write, mirroring LargeCache.linux: correct and simple; the linux
// target is CI-only so write cost is acceptable.
actual class ConcurrentHashCache<K : Any, V : Any> {
    private val mapRef = AtomicReference(HashMap<K, V>())

    actual fun get(key: K): V? = mapRef.value[key]

    actual fun put(
        key: K,
        value: V,
    ) {
        while (true) {
            val current = mapRef.value
            val copy = HashMap(current)
            copy[key] = value
            if (mapRef.compareAndSet(current, copy)) return
        }
    }

    actual fun size(): Int = mapRef.value.size

    actual fun clear() {
        mapRef.value = HashMap()
    }
}
