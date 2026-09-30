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
package com.vitorpamplona.quartz.buzz.huddles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HuddleLivenessEventTest {
    private val session = "4e8a7c2e-3b1f-4d6a-9a1e-7c5b2d9f0e11"
    private val channel = "9b353519-f4fe-4757-aef4-bec6cc0ae54c"

    @Test
    fun relayShapedSnapshotParses() {
        // The exact shape `handle_huddle_liveness_req` signs: d, h, and a string generation.
        val tags = arrayOf(arrayOf("d", session), arrayOf("h", channel))
        val content = """{"ephemeral_channel_id":"$session","generation":"18446744073709551615"}"""
        val ev = HuddleLivenessEvent("0".repeat(64), "e".repeat(64), 1, tags, content, "sig")

        assertEquals(48104, ev.kind)
        assertEquals(session, ev.sessionId())
        assertEquals(channel, ev.channelId())
        assertEquals(session, ev.liveness()?.ephemeralChannelId)
        assertEquals("18446744073709551615", ev.generation())
    }

    @Test
    fun malformedContentIsNotAuthoritative() {
        val tags = arrayOf(arrayOf("d", session), arrayOf("h", channel))
        assertNull(HuddleLivenessEvent("0".repeat(64), "e".repeat(64), 1, tags, "{", "sig").generation())
        assertNull(HuddleLivenessEvent("0".repeat(64), "e".repeat(64), 1, tags, """{"ephemeral_channel_id":"$session","generation":7}""", "sig").generation())
        assertNull(HuddleLivenessEvent("0".repeat(64), "e".repeat(64), 1, tags, """{"ephemeral_channel_id":"$session","generation":""}""", "sig").generation())
    }

    @Test
    fun buildRoundTrips() {
        val tpl = HuddleLivenessEvent.build(session, channel, "42")
        assertEquals(listOf("d", "h"), tpl.tags.map { it[0] })
        val ev = HuddleLivenessEvent("0".repeat(64), "e".repeat(64), tpl.createdAt, tpl.tags, tpl.content, "sig")
        assertEquals(session, ev.sessionId())
        assertEquals(channel, ev.channelId())
        assertEquals("42", ev.generation())
    }

    @Test
    fun filterIsLivenessOnly() {
        val filter = HuddleLivenessEvent.filter(listOf(channel), listOf(session))
        assertEquals(listOf(48104), filter.kinds)
        assertEquals(mapOf("h" to listOf(channel), "d" to listOf(session)), filter.tags)
        assertEquals(1, filter.limit)

        val json = filter.toJson()
        assertTrue(json.contains("\"kinds\":[48104]"), json)
        assertTrue(json.contains("\"#h\":[\"$channel\"]"), json)
        assertTrue(json.contains("\"#d\":[\"$session\"]"), json)

        val allSessions = HuddleLivenessEvent.filter(listOf(channel))
        assertEquals(mapOf("h" to listOf(channel)), allSessions.tags)
        assertNull(allSessions.limit)

        assertFailsWith<IllegalArgumentException> { HuddleLivenessEvent.filter(emptyList()) }
        assertFailsWith<IllegalArgumentException> { HuddleLivenessEvent.filter(List(129) { channel }) }
    }

    @Test
    fun generationsCompareOnlyWhenBothAreDecimal() {
        assertEquals(-1, HuddleLivenessContent.compareGenerations("9", "10"))
        assertEquals(1, HuddleLivenessContent.compareGenerations("18446744073709551615", "18446744073709551614"))
        assertEquals(0, HuddleLivenessContent.compareGenerations("007", "7"))
        assertNull(HuddleLivenessContent.compareGenerations("epoch-a", "3"))
        assertNull(HuddleLivenessContent.compareGenerations("", "3"))
        assertNull(HuddleLivenessContent.compareGenerations("-1", "3"))
    }
}
