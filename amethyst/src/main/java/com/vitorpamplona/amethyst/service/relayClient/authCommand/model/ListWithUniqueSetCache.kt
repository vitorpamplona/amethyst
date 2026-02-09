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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import java.util.concurrent.atomic.AtomicReference

class ListWithUniqueSetCache<T, U>(
    val key: (T) -> U,
) {
    private val list = AtomicReference(listOf<T>())
    private val cacheSet = AtomicReference<Set<U>?>(setOf<U>())

    fun isEmpty() = list.get().isEmpty()

    fun add(item: T) = set(list.get() + item)

    fun remove(item: T) = set(list.get() - item)

    fun set(newList: List<T>) {
        list.set(newList)
        // Invalidate the cache - next read will recompute
        cacheSet.set(null)
    }

    fun distinct(): Set<U> {
        var currentSet = cacheSet.get()

        // Check if the cached set is based on the current list
        if (currentSet != null) {
            return currentSet
        }

        // Compute and attempt to atomically update the cache
        val newSet = list.get().mapTo(mutableSetOf<U>(), key)
        cacheSet.compareAndSet(currentSet, newSet)
        return newSet
    }

    fun forEachSubscriber(action: (T) -> Unit) {
        list.get().forEach(action)
    }

    fun forEachUniqueSubscriber(action: (U) -> Unit) {
        distinct().forEach(action)
    }
}
