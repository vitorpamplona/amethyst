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
package com.vitorpamplona.amethyst.service.relayClient

import android.util.Log
import com.vitorpamplona.amethyst.service.relayClient.RelaySpeedLogger.Companion.TAG
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.timer
import kotlin.jvm.java

class KindGroup(
    var count: AtomicInteger = AtomicInteger(0),
    val subs: LargeCache<String, AtomicInteger> = LargeCache<String, AtomicInteger>(),
    val relays: LargeCache<String, AtomicInteger> = LargeCache<String, AtomicInteger>(),
) {
    fun increment(
        subId: String,
        relayUrl: String,
    ) {
        count.incrementAndGet()

        val subStats = subs.get(subId)
        if (subStats != null) {
            subStats.incrementAndGet()
        } else {
            subs.put(subId, AtomicInteger(1))
        }

        val relayStats = relays.get(relayUrl)
        if (relayStats != null) {
            relayStats.incrementAndGet()
        } else {
            relays.put(relayUrl, AtomicInteger(1))
        }
    }

    fun reset() {
        count.set(0)
        subs.forEach { key, value -> value.set(0) }
        relays.forEach { key, value -> value.set(0) }
    }

    fun printSubs() = subs.joinToString(", ") { key, value -> if (value.get() > 0) "$key ($value)" else "" }

    fun printRelays() = relays.joinToString(", ") { key, value -> if (value.get() > 0) "${key.removePrefix("wss://").removeSuffix("/")} ($value)" else "" }

    override fun toString() = "(${count.get()}); ${printSubs()}; ${printRelays()}"
}

class FrameStat {
    var eventCount = AtomicInteger(0)
    var kinds = LargeCache<Int, KindGroup>()

    fun increment(
        kind: Int,
        subId: String,
        relayUrl: String,
    ) {
        eventCount.incrementAndGet()

        val kindGroup = kinds.get(kind)
        if (kindGroup != null) {
            kindGroup.increment(subId, relayUrl)
        } else {
            val group = KindGroup(AtomicInteger(0))
            group.increment(subId, relayUrl)
            kinds.put(kind, group)
        }
    }

    fun hasAnything() = eventCount.get() > 0

    fun reset() {
        eventCount.set(0)
        kinds.forEach { key, value -> value.reset() }
    }

    fun log() {
        Log.d(TAG, "Events Per Second: ${eventCount.get()}")
        kinds.forEach { key, value ->
            if (value.count.get() > 0) {
                Log.d(TAG, "-- Kind $key $value")
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
                current.increment(event.kind, subscriptionId, relay.url)
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
