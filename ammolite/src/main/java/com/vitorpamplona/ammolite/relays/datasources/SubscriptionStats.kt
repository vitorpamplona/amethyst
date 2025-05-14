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
package com.vitorpamplona.ammolite.relays.datasources

import android.util.Log

class SubscriptionStats {
    data class Counter(
        val subscriptionId: String,
        val eventKind: Int,
        var counter: Int,
    )

    private var eventCounter = mapOf<Int, Counter>()

    fun eventCounterIndex(
        str1: String,
        str2: Int,
    ): Int = 31 * str1.hashCode() + str2.hashCode()

    fun add(
        subscriptionId: String,
        eventKind: Int,
    ) {
        val key = eventCounterIndex(subscriptionId, eventKind)
        val keyValue = eventCounter[key]
        if (keyValue != null) {
            keyValue.counter++
        } else {
            eventCounter = eventCounter + Pair(key, Counter(subscriptionId, eventKind, 1))
        }
    }

    fun printCounter(tag: String) {
        eventCounter.forEach {
            Log.d(
                tag,
                "Received Events ${it.value.subscriptionId} ${it.value.eventKind}: ${it.value.counter}",
            )
        }
    }
}
