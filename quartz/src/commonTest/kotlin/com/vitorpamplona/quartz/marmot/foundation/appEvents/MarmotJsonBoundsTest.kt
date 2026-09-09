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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Resource bounds on an app payload's `content`, ported from the reference
 * client's `GroupSystemEventFuzzTest` contract.
 *
 * The threat is not a malformed payload — those are dropped either way — but a
 * well-formed one that is expensive. MLS authenticates that a group MEMBER sent
 * these bytes and nothing more, and deep nesting or a huge collection costs a
 * parser far more than it costs whoever sent it.
 *
 * The limits are checked with a linear pre-scan BEFORE any JSON library sees
 * the string, and the pre-scan deliberately does not double as a validity
 * check: malformed input that stays inside the limits still reaches the real
 * parser, so its error paths keep being exercised.
 */
class MarmotJsonBoundsTest {
    private fun nested(containers: Int) =
        buildString {
            append("{\"system_type\":\"nested\",\"data\":")
            repeat(containers) { append('[') }
            append('0')
            repeat(containers) { append(']') }
            append('}')
        }

    private fun wide(members: Int) =
        (0 until members).joinToString(
            prefix = "{\"system_type\":\"wide\",\"data\":{",
            postfix = "}}",
        ) { "\"field$it\":$it" }

    @Test
    fun aPayloadInsideEveryLimitIsAccepted() {
        assertTrue(MarmotJson.withinResourceBounds("""{"v":1,"system_type":"member_added"}"""))
        assertTrue(MarmotJson.withinResourceBounds(nested(MarmotJson.MAX_JSON_DEPTH - 2)))
        assertTrue(MarmotJson.withinResourceBounds(wide(MarmotJson.MAX_COLLECTION_ELEMENTS - 2)))
    }

    @Test
    fun nestingBeyondTheDepthLimitIsRefused() {
        assertFalse(MarmotJson.withinResourceBounds(nested(MarmotJson.MAX_JSON_DEPTH + 1)))
    }

    @Test
    fun aCollectionBeyondTheElementLimitIsRefused() {
        assertFalse(MarmotJson.withinResourceBounds(wide(MarmotJson.MAX_COLLECTION_ELEMENTS + 2)))
    }

    @Test
    fun inputBeyondTheByteLimitIsRefused() {
        val big = "{\"text\":\"" + "a".repeat(MarmotJson.MAX_INPUT_BYTES) + "\"}"
        assertFalse(MarmotJson.withinResourceBounds(big))
    }

    @Test
    fun theByteLimitCountsUtf8NotUtf16() {
        // A four-byte emoji is two Kotlin chars. Counting chars would let a
        // payload four times over the limit through.
        val emoji = "😀"
        val overshoot = "{\"text\":\"" + emoji.repeat(MarmotJson.MAX_INPUT_BYTES / 4) + "\"}"
        assertFalse(MarmotJson.withinResourceBounds(overshoot))
    }

    @Test
    fun bracesInsideStringsDoNotCountAsNesting() {
        // The scanner has to know where strings begin and end, or a caption
        // that merely mentions a bracket would be refused as too deep.
        val text = "[".repeat(MarmotJson.MAX_JSON_DEPTH * 4)
        assertTrue(MarmotJson.withinResourceBounds("""{"system_type":"x","text":"$text"}"""))
    }

    @Test
    fun anEscapedQuoteDoesNotEndTheString() {
        assertTrue(MarmotJson.withinResourceBounds("""{"system_type":"x","text":"she said \"hi\" and [[["}"""))
    }

    @Test
    fun aBoundedButMalformedPayloadStillReachesTheParser() {
        // The pre-scan is not a validity filter. If it rejected malformed input
        // itself, the parser's error paths would stop being exercised.
        assertTrue(MarmotJson.withinResourceBounds("{not json at all"))
    }

    @Test
    fun anOversizedSystemRowIsDroppedRatherThanParsed() {
        val payload = nested(MarmotJson.MAX_JSON_DEPTH + 4)
        val event = MarmotAppEvent("id", "a".repeat(64), 1L, MarmotAppEvent.KIND_SYSTEM, emptyArray(), payload)
        assertNull(MarmotSystemEvent.fromAppEvent(event))
    }

    @Test
    fun anOrdinarySystemRowStillDecodes() {
        // The bound must not cost the real thing: a row this client derived has
        // to survive its own round trip.
        val row = MarmotSystemEvent(MarmotSystemType.GROUP_RENAMED, actor = "b".repeat(64), name = "after")
        val appEvent = row.toAppEvent("b".repeat(64), 1_800_000_000L)
        val decoded = assertNotNull(MarmotSystemEvent.fromAppEvent(appEvent))
        assertEquals(MarmotSystemType.GROUP_RENAMED, decoded.systemType)
        assertEquals("after", decoded.name)
    }
}
