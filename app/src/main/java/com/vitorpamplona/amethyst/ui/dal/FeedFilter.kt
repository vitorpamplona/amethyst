package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

abstract class FeedFilter<T>() {
    @OptIn(ExperimentalTime::class)
    fun loadTop(): List<T> {
        val (feed, elapsed) = measureTimedValue {
            feed().take(1000)
        }

        Log.d("Time", "${this.javaClass.simpleName} Feed in $elapsed with ${feed.size} objects")
        return feed
    }

    abstract fun feed(): List<T>
}
