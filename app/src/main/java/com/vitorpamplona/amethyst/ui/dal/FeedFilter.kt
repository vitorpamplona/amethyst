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

import android.util.Log
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlin.time.measureTimedValue

abstract class FeedFilter<T> {
    fun loadTop(): List<T> {
        checkNotInMainThread()

        val (feed, elapsed) = measureTimedValue { feed() }

        Log.d("Time", "${this.javaClass.simpleName} Full Feed in $elapsed with ${feed.size} objects")
        return feed.take(limit())
    }

    open fun limit() = 1000

    /** Returns a string that serves as the key to invalidate the list if it changes. */
    abstract fun feedKey(): String

    open fun showHiddenKey(): Boolean = false

    abstract fun feed(): List<T>
}

abstract class AdditiveFeedFilter<T> : FeedFilter<T>() {
    abstract fun applyFilter(collection: Set<T>): Set<T>

    abstract fun sort(collection: Set<T>): List<T>

    open fun updateListWith(
        oldList: List<T>,
        newItems: Set<T>,
    ): List<T> {
        checkNotInMainThread()

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
