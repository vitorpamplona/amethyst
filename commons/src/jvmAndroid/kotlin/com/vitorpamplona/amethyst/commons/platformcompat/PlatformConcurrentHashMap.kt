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
package com.vitorpamplona.amethyst.commons.platformcompat

import java.util.concurrent.ConcurrentHashMap

actual class PlatformConcurrentHashMap<K, V> actual constructor() : MutableMap<K, V> {
    private val delegate = ConcurrentHashMap<K, V>()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = delegate.entries
    override val keys: MutableSet<K> get() = delegate.keys
    override val size: Int get() = delegate.size
    override val values: MutableCollection<V> get() = delegate.values

    override fun clear() = delegate.clear()

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun remove(key: K): V? = delegate.remove(key)

    override fun putAll(from: Map<out K, V>) = delegate.putAll(from)

    override fun put(
        key: K,
        value: V,
    ): V? = delegate.put(key, value)

    override fun get(key: K): V? = delegate.get(key)

    override fun containsValue(value: V): Boolean = delegate.containsValue(value)

    override fun containsKey(key: K): Boolean = delegate.containsKey(key)
}

actual fun <T> platformConcurrentKeySet(): MutableSet<T> = ConcurrentHashMap.newKeySet()
