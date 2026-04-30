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
package com.vitorpamplona.quartz.utils

/**
 * KMP substitute for `java.util.TreeSet`. Backed by a sorted [ArrayList];
 * O(log n) add/remove/contains via binary search, O(1) [last]/[size],
 * in-order iteration.
 *
 * The [cmp] must be total — distinct elements must not compare equal,
 * otherwise duplicates collapse silently. Optimised for small-to-medium
 * `n`; insert/remove in the middle is O(n) due to ArrayList shifting,
 * which is faster in practice than a tree for the bounded sizes typical
 * of per-filter caps.
 */
class SortedList<E>(
    private val cmp: Comparator<in E>,
) : Iterable<E> {
    private val list = ArrayList<E>()

    val size: Int get() = list.size

    fun isEmpty(): Boolean = list.isEmpty()

    fun add(element: E): Boolean {
        val idx = list.binarySearch(element, cmp)
        if (idx >= 0) return false
        list.add(-idx - 1, element)
        return true
    }

    fun remove(element: E): Boolean {
        val idx = list.binarySearch(element, cmp)
        if (idx < 0) return false
        list.removeAt(idx)
        return true
    }

    fun contains(element: E): Boolean = list.binarySearch(element, cmp) >= 0

    fun last(): E = list.last()

    fun addAll(other: Iterable<E>) {
        for (e in other) add(e)
    }

    override fun iterator(): Iterator<E> = list.iterator()
}
