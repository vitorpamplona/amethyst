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
package com.vitorpamplona.quartz.utils.cache

fun interface ICacheBiConsumer<K, V> {
    fun accept(
        k: K,
        v: V,
    )
}

interface ICacheOperations<K, V> {
    fun size(): Int

    fun forEach(consumer: ICacheBiConsumer<K, V>): Unit

    fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V>

    fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V>

    fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R>

    fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R>

    fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R>

    fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R>

    fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R>

    fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V?

    fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int

    fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long

    fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>>

    fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int>

    fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long>

    fun count(consumer: CacheCollectors.BiFilter<K, V>): Int

    fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U>

    fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?>

    fun joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = "",
        limit: Int = -1,
        truncated: CharSequence = "...",
        transform: ((K, V) -> CharSequence)? = null,
    ): String
}
