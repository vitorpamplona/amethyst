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
package com.vitorpamplona.amethyst.service.relayClient.speedLogger

import android.util.Log
import com.vitorpamplona.amethyst.service.relayClient.speedLogger.RelaySpeedLogger.Companion.TAG
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.timer

class FrameStat {
    var eventCount = AtomicInteger(0)
    var kinds = LargeCache<Int, KindGroup>()

    fun increment(
        kind: Int,
        subId: String,
        relayUrl: NormalizedRelayUrl,
        memory: Long,
    ) {
        eventCount.incrementAndGet()

        val kindGroup = kinds.get(kind)
        if (kindGroup != null) {
            kindGroup.increment(memory, subId, relayUrl)
        } else {
            val group = KindGroup()
            group.increment(memory, subId, relayUrl)
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
