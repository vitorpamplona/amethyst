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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CachingEventDecoder.ageOutCache] bounds how long the cache costs memory, which
 * [CachingEventDecoder.capacity] does not: the generations otherwise only rotate
 * inside an insert, so a client that drops to a trickle keeps everything from its
 * initial burst forever.
 *
 * Aging is on a clock, not on idleness, and that distinction is load-bearing —
 * measured on device, relays keep pushing events down open subscriptions
 * indefinitely, so an idle-triggered release returned false on every tick across
 * 240s of a flat heap. The rules pinned here are the lifetime guarantee ("an entry
 * survives one call and dies on the next"), that traffic between calls is kept, and
 * that aging only ever costs a re-parse — never a different message.
 *
 * In `commonTest`: `commonMain` logic, no platform surface, no clock.
 */
class CachingEventDecoderTrimTest {
    private fun hexId(seed: Int) = seed.toString(16).padStart(64, '0')

    private fun event(seed: Int) =
        Event(
            id = hexId(seed),
            pubKey = hexId(seed + 500_000),
            createdAt = seed.toLong(),
            kind = 1,
            tags = arrayOf(arrayOf("t", "trim")),
            content = "trim test $seed",
            sig = "f".repeat(128),
        )

    private fun frame(
        seed: Int,
        subId: String = "s",
    ) = """["EVENT","$subId",${event(seed).toJson()}]"""

    @Test
    fun entriesSurviveExactlyOneAgeOut() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        decoder.decode(frame(1))
        assertEquals(1, decoder.parsedCount.toInt())

        // first tick: retired into `previous`, still addressable
        decoder.ageOutCache()
        decoder.decode(frame(1))
        assertEquals(1, decoder.parsedCount.toInt(), "one age-out must not drop the id")
        assertEquals(1, decoder.reusedCount.toInt())
    }

    @Test
    fun twoAgeOutsWithNoTrafficEmptyTheCache() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        (1..50).forEach { decoder.decode(frame(it)) }
        assertEquals(50, decoder.cachedCount)

        decoder.ageOutCache()
        decoder.ageOutCache()

        assertEquals(0, decoder.cachedCount, "nothing survives two ticks without traffic")
        decoder.decode(frame(1))
        assertEquals(51, decoder.parsedCount.toInt(), "the id is gone, so it re-parses")
        assertEquals(0, decoder.reusedCount.toInt())
    }

    @Test
    fun trafficBetweenTicksIsKept() {
        // the trickle case: events keep arriving, so the cache must keep serving them
        val decoder = CachingEventDecoder(capacity = 1_000)
        decoder.decode(frame(1))
        decoder.ageOutCache()
        decoder.decode(frame(2))
        decoder.ageOutCache()

        // 1 was inserted two ticks ago -> gone; 2 was inserted one tick ago -> kept
        decoder.decode(frame(2))
        assertEquals(2, decoder.parsedCount.toInt())
        assertEquals(1, decoder.reusedCount.toInt(), "the recent event is still cached")

        decoder.decode(frame(1))
        assertEquals(3, decoder.parsedCount.toInt(), "the older event aged out")
    }

    @Test
    fun agingABusyCacheKeepsItSmallInsteadOfUnbounded() {
        // what the device measurement is about: a burst, then a trickle. Without aging
        // the burst's ids stay resident forever.
        val decoder = CachingEventDecoder(capacity = 100_000)
        (1..5_000).forEach { decoder.decode(frame(it)) }
        assertEquals(5_000, decoder.cachedCount)

        decoder.ageOutCache()
        (5_001..5_010).forEach { decoder.decode(frame(it)) } // the trickle
        decoder.ageOutCache()

        assertEquals(10, decoder.cachedCount, "only the trickle survives, not the burst")
    }

    @Test
    fun clearCacheReleasesEverythingAtOnce() {
        // what NostrClient.disconnect() uses when the host backgrounds the app
        val decoder = CachingEventDecoder(capacity = 1_000)
        (1..10).forEach { decoder.decode(frame(it)) }
        assertTrue(decoder.cachedCount > 0)

        decoder.clearCache()
        assertEquals(0, decoder.cachedCount)
    }

    @Test
    fun agingNeverChangesWhatADecodeReturns() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        val before = decoder.decode(frame(7, subId = "a")) as EventMessage
        decoder.ageOutCache()
        decoder.ageOutCache()
        val after = decoder.decode(frame(7, subId = "a")) as EventMessage

        assertEquals(before.subId, after.subId)
        assertEquals(before.event.id, after.event.id)
        assertEquals(before.event.pubKey, after.event.pubKey)
        assertEquals(before.event.content, after.event.content)
        assertEquals(before.event.createdAt, after.event.createdAt)
        // only the parse count differs: aging cost one re-parse, nothing else
        assertEquals(2, decoder.parsedCount.toInt())
    }

    @Test
    fun theDefaultDecoderIgnoresBoth() {
        // stateless: nothing to age or release, and neither call may throw
        MessageDecoder.Default.ageOutCache()
        MessageDecoder.Default.clearCache()
    }
}
