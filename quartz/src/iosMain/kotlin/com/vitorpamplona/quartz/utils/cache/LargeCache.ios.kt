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

// Need of find a Concurrent/ThreadSafe HashMap in iOS
actual class LargeCache<K, V> : ICacheOperations<K, V> {
    actual fun keys(): Set<K> {
        TODO("Not yet implemented")
    }

    actual fun values(): Iterable<V> {
        TODO("Not yet implemented")
    }

    actual fun get(key: K): V? {
        TODO("Not yet implemented")
    }

    actual fun remove(key: K): V? {
        TODO("Not yet implemented")
    }

    actual fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun clear() {
    }

    actual fun containsKey(key: K): Boolean {
        TODO("Not yet implemented")
    }

    actual fun put(
        key: K,
        value: V,
    ) {
    }

    actual fun getOrCreate(
        key: K,
        builder: (K) -> V,
    ): V {
        TODO("Not yet implemented")
    }

    actual fun createIfAbsent(
        key: K,
        builder: (K) -> V,
    ): Boolean {
        TODO("Not yet implemented")
    }

    actual override fun size(): Int {
        TODO("Not yet implemented")
    }

    actual override fun forEach(consumer: ICacheBiConsumer<K, V>) {
        TODO("Not yet implemented")
    }

    actual override fun filter(consumer: CacheCollectors.BiFilter<K, V>): List<V> {
        TODO("Not yet implemented")
    }

    actual override fun filterIntoSet(consumer: CacheCollectors.BiFilter<K, V>): Set<V> {
        TODO("Not yet implemented")
    }

    actual override fun <R> map(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapNotNull(consumer: CacheCollectors.BiMapper<K, V, R?>): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapNotNullIntoSet(consumer: CacheCollectors.BiMapper<K, V, R?>): Set<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapFlatten(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapFlattenIntoSet(consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>): Set<R> {
        TODO("Not yet implemented")
    }

    actual override fun maxOrNullOf(
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        TODO("Not yet implemented")
    }

    actual override fun sumOf(consumer: CacheCollectors.BiSumOf<K, V>): Int {
        TODO("Not yet implemented")
    }

    actual override fun sumOfLong(consumer: CacheCollectors.BiSumOfLong<K, V>): Long {
        TODO("Not yet implemented")
    }

    actual override fun <R> groupBy(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, List<V>> {
        TODO("Not yet implemented")
    }

    actual override fun <R> countByGroup(consumer: CacheCollectors.BiNotNullMapper<K, V, R>): Map<R, Int> {
        TODO("Not yet implemented")
    }

    actual override fun <R> sumByGroup(
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        TODO("Not yet implemented")
    }

    actual override fun count(consumer: CacheCollectors.BiFilter<K, V>): Int {
        TODO("Not yet implemented")
    }

    actual override fun <T, U> associate(transform: (K, V) -> Pair<T, U>): Map<T, U> {
        TODO("Not yet implemented")
    }

    actual override fun <U> associateWith(transform: (K, V) -> U?): Map<K, U?> {
        TODO("Not yet implemented")
    }

    actual override fun filter(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): List<V> {
        TODO("Not yet implemented")
    }

    actual override fun filterIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Set<V> {
        TODO("Not yet implemented")
    }

    actual override fun <R> map(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapNotNull(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapNotNullIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, R?>,
    ): Set<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapFlatten(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): List<R> {
        TODO("Not yet implemented")
    }

    actual override fun <R> mapFlattenIntoSet(
        from: K,
        to: K,
        consumer: CacheCollectors.BiMapper<K, V, Collection<R>?>,
    ): Set<R> {
        TODO("Not yet implemented")
    }

    actual override fun maxOrNullOf(
        from: K,
        to: K,
        filter: CacheCollectors.BiFilter<K, V>,
        comparator: Comparator<V>,
    ): V? {
        TODO("Not yet implemented")
    }

    actual override fun sumOf(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOf<K, V>,
    ): Int {
        TODO("Not yet implemented")
    }

    actual override fun sumOfLong(
        from: K,
        to: K,
        consumer: CacheCollectors.BiSumOfLong<K, V>,
    ): Long {
        TODO("Not yet implemented")
    }

    actual override fun <R> groupBy(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, List<V>> {
        TODO("Not yet implemented")
    }

    actual override fun <R> countByGroup(
        from: K,
        to: K,
        consumer: CacheCollectors.BiNotNullMapper<K, V, R>,
    ): Map<R, Int> {
        TODO("Not yet implemented")
    }

    actual override fun <R> sumByGroup(
        from: K,
        to: K,
        groupMap: CacheCollectors.BiNotNullMapper<K, V, R>,
        sumOf: CacheCollectors.BiNotNullMapper<K, V, Long>,
    ): Map<R, Long> {
        TODO("Not yet implemented")
    }

    actual override fun count(
        from: K,
        to: K,
        consumer: CacheCollectors.BiFilter<K, V>,
    ): Int {
        TODO("Not yet implemented")
    }

    actual override fun <T, U> associate(
        from: K,
        to: K,
        transform: (K, V) -> Pair<T, U>,
    ): Map<T, U> {
        TODO("Not yet implemented")
    }

    actual override fun <U> associateWith(
        from: K,
        to: K,
        transform: (K, V) -> U?,
    ): Map<K, U?> {
        TODO("Not yet implemented")
    }

    actual override fun joinToString(
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: ((K, V) -> CharSequence)?,
    ): String {
        TODO("Not yet implemented")
    }
}
