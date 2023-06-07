package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class FeedFilter<T> {
    @OptIn(ExperimentalTime::class)
    fun loadTop(): List<T> {
        checkNotInMainThread()

        val (feed, elapsed) = measureTimedValue {
            feed()
        }

        Log.d("Time", "${this.javaClass.simpleName} Full Feed in $elapsed with ${feed.size} objects")
        return feed.take(1000)
    }

    abstract fun feed(): List<T>
}

abstract class AdditiveFeedFilter<T> : FeedFilter<T>() {
    abstract fun applyFilter(collection: Set<T>): Set<T>
    abstract fun sort(collection: Set<T>): List<T>

    @OptIn(ExperimentalTime::class)
    open fun updateListWith(oldList: List<T>, newItems: Set<T>): List<T> {
        checkNotInMainThread()

        val (feed, elapsed) = measureTimedValue {
            val newItemsToBeAdded = applyFilter(newItems)
            if (newItemsToBeAdded.isNotEmpty()) {
                val newList = oldList.toSet() + newItemsToBeAdded
                sort(newList).take(1000)
            } else {
                oldList
            }
        }

        // Log.d("Time", "${this.javaClass.simpleName} Additive Feed in $elapsed with ${feed.size} objects")
        return feed
    }
}
