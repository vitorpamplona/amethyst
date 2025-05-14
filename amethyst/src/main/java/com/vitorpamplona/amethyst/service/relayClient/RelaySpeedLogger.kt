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
package com.vitorpamplona.amethyst.service.relayClient

import android.util.Log
import com.vitorpamplona.amethyst.service.relayClient.RelaySpeedLogger.Companion.TAG
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.concurrent.timer
import kotlin.text.get
import kotlin.text.set

class FrameStat {
    var eventCount = AtomicInteger(0)
    var kinds = LargeCache<Int, AtomicInteger>()

    fun increment(kind: Int) {
        eventCount.incrementAndGet()

        val kindCount = kinds.get(kind)
        if (kindCount != null) {
            kindCount.incrementAndGet()
        } else {
            kinds.put(kind, AtomicInteger(1))
        }
    }

    fun hasAnything() = eventCount.get() > 0

    fun reset() {
        eventCount.set(0)
        kinds.forEach { key, value ->
            value.set(0)
        }
    }

    fun log() {
        Log.d(TAG, "Events Per Second: ${eventCount.get()}")
        kinds.forEach { key, value ->
            if (value.get() > 0) {
                Log.d(TAG, "-- Events Per Second Debug: $key $value")
            }
        }
    }

    // Initialize the timer in the constructor.  This ensures it starts when the
    // ResettableCounter object is created.
    init {
        // Use a timer to reset the counter every second.
        timer(name = "EventsPerSecondCounter", period = 1000, daemon = true) {
            if (hasAnything()) {
                log()
                reset()
            }
        }
    }
}

/**
 * Listens to NostrClient's onNotify messages from the relay
 */
class RelaySpeedLogger(
    val client: NostrClient,
) {
    companion object {
        val TAG = RelaySpeedLogger::class.java.simpleName
    }

    var current = FrameStat()

    private val clientListener =
        object : NostrClient.Listener {
            /** A new message was received */
            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                arrivalTime: Long,
                afterEOSE: Boolean,
            ) {
                current.increment(event.kind)
            }
        }

    init {
        Log.d(TAG, "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d(TAG, "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
