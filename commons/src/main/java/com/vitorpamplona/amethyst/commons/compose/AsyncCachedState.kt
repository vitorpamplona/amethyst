/**
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
package com.vitorpamplona.amethyst.commons.compose

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

@Composable
fun <K, V> produceCachedStateAsync(
    cache: AsyncCachedState<K, V>,
    key: K,
): State<V?> =
    @Suppress("ProduceStateDoesNotAssignValue")
    produceState(initialValue = cache.cached(key), key1 = key) {
        val newValue = cache.update(key)
        if (newValue != value) {
            value = newValue
        }
    }

@Composable
fun <K, V> produceCachedStateAsync(
    cache: AsyncCachedState<K, V>,
    key: String,
    updateValue: K,
): State<V?> =
    @Suppress("ProduceStateDoesNotAssignValue")
    produceState(initialValue = cache.cached(updateValue), key1 = key) {
        val newValue = cache.update(updateValue)
        if (newValue != value) {
            value = newValue
        }
    }

interface AsyncCachedState<K, V> {
    fun cached(k: K): V?

    suspend fun update(k: K): V?
}

abstract class GenericBaseCacheAsync<K, V>(
    capacity: Int,
) : AsyncCachedState<K, V> {
    private val cache = LruCache<K, V>(capacity)

    override fun cached(k: K): V? = cache[k]

    override suspend fun update(k: K): V? {
        cache[k]?.let { return it }

        val newValue = compute(k)

        if (newValue != null) {
            cache.put(k, newValue)
        }

        return newValue
    }

    abstract suspend fun compute(key: K): V?
}
