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

import com.vitorpamplona.quartz.utils.cache.CacheCollectors.BiFilter
import com.vitorpamplona.quartz.utils.cache.CacheCollectors.BiMapper
import com.vitorpamplona.quartz.utils.cache.CacheCollectors.BiMapperNotNull
import com.vitorpamplona.quartz.utils.cache.CacheCollectors.BiNotNullMapper
import com.vitorpamplona.quartz.utils.cache.CacheCollectors.BiSumOfLong
import java.util.function.BiConsumer

class BiFilterCollector<K, V>(
    private val filter: BiFilter<K, V>,
) : BiConsumer<K, V> {
    val results: ArrayList<V> = ArrayList()

    override fun accept(
        k: K,
        v: V,
    ) {
        if (filter.filter(k, v)) {
            results.add(v)
        }
    }
}

class BiFilterUniqueCollector<K, V>(
    private val filter: BiFilter<K, V>,
) : BiConsumer<K, V> {
    val results: HashSet<V> = HashSet()

    override fun accept(
        k: K,
        v: V,
    ) {
        if (filter.filter(k, v)) {
            results.add(v)
        }
    }
}

class BiMapCollector<K, V, R>(
    private val mapper: BiMapper<K, V, R?>,
) : BiConsumer<K, V> {
    val results: ArrayList<R> = ArrayList()

    override fun accept(
        k: K,
        v: V,
    ) {
        val result = mapper.map(k, v)
        if (result != null) {
            results.add(result)
        }
    }
}

class BiMapUniqueCollector<K, V, R>(
    private val mapper: BiMapper<K, V, R?>,
) : BiConsumer<K, V> {
    val results: HashSet<R> = HashSet()

    override fun accept(
        k: K,
        v: V,
    ) {
        val result = mapper.map(k, v)
        if (result != null) {
            results.add(result)
        }
    }
}

class BiMapFlattenCollector<K, V, R>(
    private val mapper: BiMapper<K, V, Collection<R>?>,
) : BiConsumer<K, V> {
    val results: ArrayList<R> = ArrayList()

    override fun accept(
        k: K,
        v: V,
    ) {
        val result = mapper.map(k, v)
        if (result != null) {
            results.addAll(result)
        }
    }
}

class BiMapFlattenUniqueCollector<K, V, R>(
    private val mapper: BiMapper<K, V, Collection<R>?>,
) : BiConsumer<K, V> {
    val results: HashSet<R> = HashSet()

    override fun accept(
        k: K,
        v: V,
    ) {
        val result = mapper.map(k, v)
        if (result != null) {
            results.addAll(result)
        }
    }
}

class BiNotNullMapCollector<K, V, R>(
    private val mapper: BiNotNullMapper<K, V, R>,
) : BiConsumer<K, V> {
    val results: ArrayList<R> = ArrayList()

    override fun accept(
        k: K,
        v: V,
    ) {
        results.add(mapper.map(k, v))
    }
}

class BiMaxOfCollector<K, V>(
    private val filter: BiFilter<K, V>,
    private val comparator: Comparator<V>,
) : BiConsumer<K, V> {
    private var _maxK: K? = null
    private var _maxV: V? = null

    val maxK: K? get() = _maxK
    val maxV: V? get() = _maxV

    override fun accept(
        k: K,
        v: V,
    ) {
        if (filter.filter(k, v)) {
            if (_maxK == null || comparator.compare(v, _maxV) > 0) {
                _maxK = k
                _maxV = v
            }
        }
    }
}

class BiSumOfCollector<K, V>(
    private val mapper: CacheCollectors.BiSumOf<K, V>,
) : BiConsumer<K, V> {
    private var _sum = 0
    val sum: Int get() = _sum

    override fun accept(
        k: K,
        v: V,
    ) {
        _sum += mapper.map(k, v)
    }
}

class BiSumOfLongCollector<K, V>(
    private val mapper: BiSumOfLong<K, V>,
) : BiConsumer<K, V> {
    private var _sum = 0L
    val sum: Long get() = _sum

    override fun accept(
        k: K,
        v: V,
    ) {
        _sum += mapper.map(k, v)
    }
}

class BiGroupByCollector<K, V, R>(
    private val mapper: BiNotNullMapper<K, V, R>,
) : BiConsumer<K, V> {
    val results = HashMap<R, ArrayList<V>>()

    override fun accept(
        k: K,
        v: V,
    ) {
        val group = mapper.map(k, v)
        val list = results[group]
        if (list == null) {
            val answer = ArrayList<V>()
            answer.add(v)
            results[group] = answer
        } else {
            list.add(v)
        }
    }
}

class BiCountByGroupCollector<K, V, R>(
    private val mapper: BiNotNullMapper<K, V, R>,
) : BiConsumer<K, V> {
    val results = HashMap<R, Int>()

    override fun accept(
        k: K,
        v: V,
    ) {
        val group = mapper.map(k, v)
        val count = results[group]
        if (count == null) {
            results[group] = 1
        } else {
            results[group] = count + 1
        }
    }
}

class BiSumByGroupCollector<K, V, R>(
    private val mapper: BiNotNullMapper<K, V, R>,
    private val sumOf: BiNotNullMapper<K, V, Long>,
) : BiConsumer<K, V> {
    val results = HashMap<R, Long>()

    override fun accept(
        k: K,
        v: V,
    ) {
        val group = mapper.map(k, v)
        val sum = results[group]
        if (sum == null) {
            results[group] = sumOf.map(k, v)
        } else {
            results[group] = sum + sumOf.map(k, v)
        }
    }
}

class BiCountIfCollector<K, V>(
    private val filter: BiFilter<K, V>,
) : BiConsumer<K, V> {
    private var _count = 0
    val count: Int get() = _count

    override fun accept(
        k: K,
        v: V,
    ) {
        if (filter.filter(k, v)) _count++
    }
}

class BiAssociateCollector<K, V, T, U>(
    size: Int,
    private val mapper: BiMapperNotNull<K, V, Pair<T, U>>,
) : BiConsumer<K, V> {
    val results: LinkedHashMap<T, U> = LinkedHashMap(size)

    override fun accept(
        k: K,
        v: V,
    ) {
        val pair = mapper.map(k, v)
        results[pair.first] = pair.second
    }
}

class BiAssociateNotNullCollector<K, V, T, U>(
    size: Int,
    private val mapper: BiMapper<K, V, Pair<T, U>?>,
) : BiConsumer<K, V> {
    val results: LinkedHashMap<T, U> = LinkedHashMap(size)

    override fun accept(
        k: K,
        v: V,
    ) {
        val pair = mapper.map(k, v)
        if (pair != null) {
            results[pair.first] = pair.second
        }
    }
}

class BiAssociateWithCollector<K, V, U>(
    size: Int,
    private val mapper: BiMapper<K, V, U?>,
) : BiConsumer<K, V> {
    val results: LinkedHashMap<K, U?> = LinkedHashMap(size)

    override fun accept(
        k: K,
        v: V,
    ) {
        results[k] = mapper.map(k, v)
    }
}

class BiAssociateNotNullWithCollector<K, V, U>(
    size: Int,
    private val mapper: BiMapper<K, V, U>,
) : BiConsumer<K, V> {
    val results: LinkedHashMap<K, U> = LinkedHashMap(size)

    override fun accept(
        k: K,
        v: V,
    ) {
        val newValue = mapper.map(k, v)
        if (newValue != null) {
            results[k] = newValue
        }
    }
}
