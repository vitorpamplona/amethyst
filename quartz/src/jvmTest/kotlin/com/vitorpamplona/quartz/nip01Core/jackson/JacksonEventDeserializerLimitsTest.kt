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
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.limits.EventLimits
import com.vitorpamplona.quartz.nip01Core.limits.EventLimitsTestSupport.buildEventJson
import com.vitorpamplona.quartz.nip01Core.limits.assertRejectsBecause
import kotlin.test.Test
import kotlin.test.assertEquals

// Jackson-path mirror of EventLimitsTest (commonTest, kotlinx path). Shared assertion helper
// from EventLimitsTestSupport keeps the two classes aligned.
class JacksonEventDeserializerLimitsTest {
    private fun parse(json: String): Event = JacksonMapper.mapper.readValue(json)

    @Test
    fun acceptsEventWithinLimits() {
        val event = parse(buildEventJson(contentLength = 100, tagCount = 5, tagInnerCount = 3, tagValueLength = 50))
        assertEquals(5, event.tags.size)
        assertEquals(100, event.content.length)
    }

    // --- §2.3: size / count / length caps ---

    @Test
    fun rejectsOversizedContent() = assertRejectsBecause(::parse, buildEventJson(contentLength = EventLimits.MAX_CONTENT_LENGTH + 1), "content length")

    @Test
    fun rejectsTooManyTags() = assertRejectsBecause(::parse, buildEventJson(tagCount = EventLimits.MAX_TAG_COUNT + 1), "tags")

    @Test
    fun rejectsTagWithTooManyElements() = assertRejectsBecause(::parse, buildEventJson(tagInnerCount = EventLimits.MAX_TAG_ELEMENTS_PER_TAG + 2), "elements")

    @Test
    fun rejectsOversizedTagValue() = assertRejectsBecause(::parse, buildEventJson(tagValueLength = EventLimits.MAX_TAG_VALUE_LENGTH + 1), "Tag value length")

    // --- §2.4: id / pubKey / sig / kind validation ---

    @Test
    fun rejectsIdWithWrongLength() = assertRejectsBecause(::parse, buildEventJson(id = "0".repeat(63)), "id must be 64-char hex")

    @Test
    fun rejectsIdWithNonHex() = assertRejectsBecause(::parse, buildEventJson(id = "z".repeat(64)), "id must be 64-char hex")

    @Test
    fun rejectsPubKeyWithWrongLength() = assertRejectsBecause(::parse, buildEventJson(pubKey = "0".repeat(65)), "pubKey must be 64-char hex")

    @Test
    fun rejectsPubKeyWithNonHex() = assertRejectsBecause(::parse, buildEventJson(pubKey = "g".repeat(64)), "pubKey must be 64-char hex")

    @Test
    fun rejectsSigWithWrongNonZeroLength() = assertRejectsBecause(::parse, buildEventJson(sig = "0".repeat(127)), "sig must be empty or 128-char hex")

    @Test
    fun rejectsSigWithNonHex() = assertRejectsBecause(::parse, buildEventJson(sig = "z".repeat(128)), "sig must be empty or 128-char hex")

    @Test
    fun rejectsKindAboveMax() = assertRejectsBecause(::parse, buildEventJson(kind = EventLimits.MAX_KIND + 1), "kind")

    @Test
    fun rejectsKindBelowZero() = assertRejectsBecause(::parse, buildEventJson(kind = -1), "kind")

    @Test
    fun acceptsEmptySig() {
        val event = parse(buildEventJson(sig = ""))
        assertEquals("", event.sig)
    }

    @Test
    fun acceptsAncientCreatedAt() {
        val event = parse(buildEventJson(createdAt = 100L))
        assertEquals(100L, event.createdAt)
    }
}
