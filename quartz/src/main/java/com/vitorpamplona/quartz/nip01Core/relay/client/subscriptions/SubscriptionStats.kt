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
package com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions

import android.util.Log
import com.vitorpamplona.quartz.utils.LargeCache

class SubscriptionStats {
    data class Counter(
        val subscriptionId: String,
        val eventKind: Int,
    ) {
        var counter: Int = 0
    }

    private var eventCounter = LargeCache<Int, Counter>()

    private fun eventCounterIndex(
        str1: String,
        str2: Int,
    ): Int = 31 * str1.hashCode() + str2.hashCode()

    fun add(
        subscriptionId: String,
        eventKind: Int,
    ) {
        val key = eventCounterIndex(subscriptionId, eventKind)
        val stats = eventCounter.getOrCreate(key) { Counter(subscriptionId, eventKind) }
        stats.counter++
    }

    fun printCounter(tag: String) {
        eventCounter.forEach { _, stats ->
            Log.d(
                tag,
                "Received Events ${stats.subscriptionId} ${stats.eventKind}: ${stats.counter}",
            )
        }
    }
}
