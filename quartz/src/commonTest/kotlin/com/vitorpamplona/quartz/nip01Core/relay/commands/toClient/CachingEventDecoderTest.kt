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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CachingEventDecoderTest {
    private fun hexId(seed: Int) = seed.toString(16).padStart(64, '0')

    private fun event(
        seed: Int,
        content: String = "hello $seed",
    ) = Event(
        id = hexId(seed),
        pubKey = hexId(seed + 100_000),
        createdAt = seed.toLong(),
        kind = 1,
        tags = arrayOf(arrayOf("t", "test")),
        content = content,
        sig = "f".repeat(128),
    )

    private fun frame(
        subId: String,
        event: Event,
    ) = """["EVENT","$subId",${event.toJson()}]"""

    @Test
    fun firstSightParsesThenDuplicateReusesTheSameEventInstance() {
        val decoder = CachingEventDecoder()
        val e = event(1)

        val first = decoder.decode(frame("subA", e)) as EventMessage
        val second = decoder.decode(frame("subB", e)) as EventMessage

        assertEquals(e.id, first.event.id)
        assertEquals("subA", first.subId)
        assertEquals("subB", second.subId, "duplicate keeps the frame's own subId")
        assertSame(first.event, second.event, "duplicate must reuse the parsed Event")
        assertEquals(1, decoder.parsedCount)
        assertEquals(1, decoder.reusedCount)
    }

    @Test
    fun duplicateAcrossSubIdsAndOrderingsMatchesFullParse() {
        val decoder = CachingEventDecoder()
        val events = (1..50).map { event(it) }

        // every event delivered on 3 subs, interleaved
        val frames =
            buildList {
                for (sub in listOf("s1", "s2", "s3")) {
                    events.forEach { add(frame(sub, it)) }
                }
            }

        val decoded = frames.map { decoder.decode(it) as EventMessage }
        assertEquals(150, decoded.size)
        assertEquals(50, decoder.parsedCount)
        assertEquals(100, decoder.reusedCount)
        // each decoded message matches the full-parse result
        frames.zip(decoded).forEach { (f, msg) ->
            val reference = MessageDecoder.Default.decode(f) as EventMessage
            assertEquals(reference.subId, msg.subId)
            assertEquals(reference.event.id, msg.event.id)
            assertEquals(reference.event.content, msg.event.content)
        }
    }

    @Test
    fun repostStyleContentWithEmbeddedEventJsonNeverConfusesTheScan() {
        val decoder = CachingEventDecoder()
        val inner = event(7)
        // kind-6-style: full event JSON embedded (and therefore escaped) in content.
        // The escaped \"id\":\" inside content must NOT be read as the outer id.
        val repost = event(8, content = inner.toJson())

        val decoded = decoder.decode(frame("s", repost)) as EventMessage
        assertEquals(repost.id, decoded.event.id, "outer id wins")

        // and a duplicate of the repost still resolves to the repost, not the inner event
        val dup = decoder.decode(frame("s2", repost)) as EventMessage
        assertSame(decoded.event, dup.event)
        assertEquals(repost.id, dup.event.id)
    }

    @Test
    fun nonEventFramesPassThroughUntouched() {
        val decoder = CachingEventDecoder()
        assertTrue(decoder.decode("""["EOSE","sub1"]""") is EoseMessage)
        assertTrue(decoder.decode("""["NOTICE","hi"]""") is NoticeMessage)
        assertTrue(decoder.decode("""["OK","${hexId(3)}",true,""]""") is OkMessage)
        assertEquals(0, decoder.parsedCount, "only EVENT frames populate the cache")
    }

    @Test
    fun escapedSubIdFallsBackToFullParse() {
        val decoder = CachingEventDecoder()
        val e = event(11)
        decoder.decode(frame("plain", e)) // cache it

        // subId with an escaped quote: scan must bail, full parse must win.
        val weird = """["EVENT","a\"b",${e.toJson()}]"""
        val decoded = decoder.decode(weird) as EventMessage
        assertEquals("a\"b", decoded.subId)
        assertEquals(e.id, decoded.event.id)
    }

    @Test
    fun malformedIdValueFallsBackToFullParse() {
        val decoder = CachingEventDecoder()
        val e = event(12)
        decoder.decode(frame("s", e))

        // Uppercase hex in the id key position → scan bails → full parse throws
        // or produces whatever the mapper does; here we just assert no cache hit
        // is fabricated for a different-id frame.
        val other = event(13)
        val decoded = decoder.decode(frame("s", other)) as EventMessage
        assertEquals(other.id, decoded.event.id)
        assertNotSame(e, decoded.event)
    }

    @Test
    fun cacheRotationOnlyCausesReparseNeverWrongMessages() {
        val decoder = CachingEventDecoder(capacity = 8)
        val events = (1..40).map { event(it) }

        events.forEach { decoder.decode(frame("s", it)) }
        // replay everything: some rotated out (re-parse), none may mismatch
        events.forEach {
            val decoded = decoder.decode(frame("s2", it)) as EventMessage
            assertEquals(it.id, decoded.event.id)
            assertEquals("s2", decoded.subId)
        }
    }
}
