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
package com.vitorpamplona.quartz.utils

import android.R.attr.value

class MapOfSetBuilder<K, V> {
    val data = mutableMapOf<K, MutableSet<V>>()

    fun add(
        key: K,
        value: V,
    ) {
        val set = data[key]
        if (set == null) {
            data.put(key, mutableSetOf(value))
        } else {
            set.add(value)
        }
    }

    fun add(
        key: K,
        value: Set<V>,
    ) {
        val set = data[key]
        if (set == null) {
            data.put(key, value.toMutableSet())
        } else {
            set.addAll(value)
        }
    }

    fun add(map: Map<K, Set<V>>) {
        map.forEach {
            add(it.key, it.value)
        }
    }

    fun build(): Map<K, Set<V>> = data
}

fun <K, V> mapOfSet(init: MapOfSetBuilder<K, V>.() -> Unit): Map<K, Set<V>> {
    val data = MapOfSetBuilder<K, V>()
    data.init()
    return data.build()
}

fun <K, V> merge(maps: List<Map<K, Set<V>>>): Map<K, Set<V>> =
    mapOfSet {
        maps.forEach { map ->
            add(map)
        }
    }
