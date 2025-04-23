/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.dal

import kotlin.time.measureTimedValue

abstract class AdditiveFeedFilter<T> : FeedFilter<T>() {
    abstract fun applyFilter(collection: Set<T>): Set<T>

    abstract fun sort(collection: Set<T>): List<T>

    open fun updateListWith(
        oldList: List<T>,
        newItems: Set<T>,
    ): List<T> {
        val (feed, elapsed) =
            measureTimedValue {
                val newItemsToBeAdded = applyFilter(newItems)
                if (newItemsToBeAdded.isNotEmpty()) {
                    val newList = oldList.toSet() + newItemsToBeAdded
                    sort(newList).take(limit())
                } else {
                    oldList
                }
            }

        // Log.d("Time", "${this.javaClass.simpleName} Additive Feed in $elapsed with ${feed.size}
        // objects")
        return feed
    }
}
