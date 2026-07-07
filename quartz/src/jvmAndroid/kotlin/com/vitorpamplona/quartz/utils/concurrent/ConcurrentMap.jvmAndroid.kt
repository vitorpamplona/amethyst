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

import java.util.concurrent.ConcurrentHashMap

actual class ConcurrentMap<K : Any, V : Any> {
    private val map = ConcurrentHashMap<K, V>()

    actual operator fun get(key: K): V? = map[key]

    actual operator fun set(
        key: K,
        value: V,
    ) {
        map[key] = value
    }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V = map.computeIfAbsent(key) { defaultValue() }

    actual fun merge(
        key: K,
        value: V,
        remap: (old: V, new: V) -> V,
    ): V = map.merge(key, value) { old, new -> remap(old, new) }!!

    actual fun size(): Int = map.size

    actual fun snapshot(): Map<K, V> = HashMap(map)
}
