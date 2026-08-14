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
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CachingEventDecoder.trimIfIdle] bounds how long the cache costs memory, which
 * [CachingEventDecoder.capacity] does not: the generations only rotate inside an
 * insert, so without a trim a client that goes quiet pins every cached event for
 * the rest of the process's life.
 *
 * The rules that matter here are "release when genuinely idle", "do NOT release
 * out from under live traffic" — including traffic that is *only* cache hits,
 * which is exactly when the cache is earning its keep — and "releasing never
 * changes what a decode returns, only whether it re-parsed".
 *
 * In `commonTest`: this is `commonMain` logic with no platform surface, and the
 * clock is injected so no target needs a real one.
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

    private val minute = 60_000L

    @Test
    fun releasesTheCacheOnceIdle() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        (1..50).forEach { decoder.decode(frame(it)) }
        assertEquals(50, decoder.parsedCount.toInt())

        // read the clock AFTER the decodes: they set the idle clock as they run, so a
        // cutoff measured from before them is only a full minute later while the
        // parsing itself takes under a millisecond
        val quietSince = TimeUtils.nowMillis()
        assertTrue(decoder.trimIfIdle(minute, quietSince + minute), "should trim after a quiet minute")

        // the ids are gone, so a repeat of a previously cached frame parses again
        decoder.decode(frame(1))
        assertEquals(51, decoder.parsedCount.toInt())
        assertEquals(0, decoder.reusedCount.toInt())
    }

    @Test
    fun keepsTheCacheWhileTrafficIsFlowing() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        val t0 = TimeUtils.nowMillis()
        (1..50).forEach { decoder.decode(frame(it)) }

        assertFalse(decoder.trimIfIdle(minute, t0 + 1_000), "1s of quiet is not idle")

        decoder.decode(frame(1))
        assertEquals(50, decoder.parsedCount.toInt(), "cached ids must survive")
        assertEquals(1, decoder.reusedCount.toInt())
    }

    @Test
    fun cacheHitsCountAsActivity() {
        // A stream of pure duplicates inserts nothing, but it is precisely when the
        // cache is most valuable. Keying idleness off inserts alone would trim it away
        // under the traffic it is serving.
        val decoder = CachingEventDecoder(capacity = 1_000)
        decoder.decode(frame(1))

        val muchLater = TimeUtils.nowMillis() + 10 * minute
        repeat(5) { decoder.decode(frame(1, subId = "sub$it")) }

        assertEquals(5, decoder.reusedCount.toInt())
        assertFalse(
            decoder.trimIfIdle(minute, TimeUtils.nowMillis() + 1_000),
            "hits must refresh the idle clock",
        )
        // ...but a genuinely quiet stretch after those hits still trims
        assertTrue(decoder.trimIfIdle(minute, muchLater))
    }

    @Test
    fun trimmingAnEmptyCacheDoesNothing() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        assertFalse(decoder.trimIfIdle(0, TimeUtils.nowMillis() + minute), "nothing to release")
    }

    @Test
    fun zeroIdleReleasesImmediately() {
        // what NostrClient.disconnect() uses when the host backgrounds the app
        val decoder = CachingEventDecoder(capacity = 1_000)
        (1..10).forEach { decoder.decode(frame(it)) }
        assertTrue(decoder.trimIfIdle(idleMillis = 0, nowMillis = TimeUtils.nowMillis()))
    }

    @Test
    fun aTrimNeverChangesWhatADecodeReturns() {
        val decoder = CachingEventDecoder(capacity = 1_000)
        val before = decoder.decode(frame(7, subId = "a")) as EventMessage
        decoder.trimIfIdle(0, TimeUtils.nowMillis())
        val after = decoder.decode(frame(7, subId = "a")) as EventMessage

        assertEquals(before.subId, after.subId)
        assertEquals(before.event.id, after.event.id)
        assertEquals(before.event.pubKey, after.event.pubKey)
        assertEquals(before.event.content, after.event.content)
        assertEquals(before.event.createdAt, after.event.createdAt)
        // only the parse count differs: the trim cost one re-parse, nothing else
        assertEquals(2, decoder.parsedCount.toInt())
    }

    @Test
    fun theDefaultDecoderIgnoresTrim() {
        assertFalse(MessageDecoder.Default.trimIfIdle(0, TimeUtils.nowMillis()), "stateless: nothing to release")
    }
}
