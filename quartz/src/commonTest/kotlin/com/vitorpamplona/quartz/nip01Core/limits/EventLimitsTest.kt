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
package com.vitorpamplona.quartz.nip01Core.limits

import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip01Core.limits.EventLimitsTestSupport.buildEventJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Regression for security review 2026-04-24 §2.3 / Finding #5: the deserializers used to
// materialize whole events with no upper bound. A hostile relay could OOM the client with
// one giant event or a flood of large tags. Caps are enforced inline by the tag deserializers
// and post-parse by EventLimits.validateContent.
class EventLimitsTest {
    private fun parse(json: String) = KotlinSerializationMapper.fromJson(json)

    @Test
    fun acceptsEventWithinLimits() {
        val event =
            parse(buildEventJson(contentLength = 100, tagCount = 5, tagInnerCount = 3, tagValueLength = 50))
        assertEquals(5, event.tags.size)
        assertEquals(100, event.content.length)
        assertEquals(3, event.tags[0].size)
    }

    @Test
    fun rejectsOversizedContent() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(contentLength = EventLimits.MAX_CONTENT_LENGTH + 1))
            }
        assertEquals(true, ex.message?.contains("content length"), ex.message)
    }

    @Test
    fun rejectsTooManyTags() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(tagCount = EventLimits.MAX_TAG_COUNT + 1))
            }
        assertEquals(true, ex.message?.contains("tags"), ex.message)
    }

    @Test
    fun rejectsTagWithTooManyElements() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(tagInnerCount = EventLimits.MAX_TAG_ELEMENTS_PER_TAG + 2))
            }
        assertEquals(true, ex.message?.contains("elements"), ex.message)
    }

    @Test
    fun rejectsOversizedTagValue() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(tagValueLength = EventLimits.MAX_TAG_VALUE_LENGTH + 1))
            }
        assertEquals(true, ex.message?.contains("Tag value length"), ex.message)
    }

    // --- §2.4: id / pubKey / sig / kind / createdAt validation ---

    @Test
    fun rejectsIdWithWrongLength() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(id = "0".repeat(63)))
            }
        assertEquals(true, ex.message?.contains("id must be 64-char hex"), ex.message)
    }

    @Test
    fun rejectsIdWithNonHex() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(id = "z".repeat(64)))
            }
        assertEquals(true, ex.message?.contains("id must be 64-char hex"), ex.message)
    }

    @Test
    fun rejectsPubKeyWithWrongLength() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(pubKey = "0".repeat(65)))
            }
        assertEquals(true, ex.message?.contains("pubKey must be 64-char hex"), ex.message)
    }

    @Test
    fun rejectsPubKeyWithNonHex() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(pubKey = "g".repeat(64)))
            }
        assertEquals(true, ex.message?.contains("pubKey must be 64-char hex"), ex.message)
    }

    @Test
    fun rejectsSigWithWrongNonZeroLength() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(sig = "0".repeat(127)))
            }
        assertEquals(true, ex.message?.contains("sig must be empty or 128-char hex"), ex.message)
    }

    @Test
    fun rejectsSigWithNonHex() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(sig = "z".repeat(128)))
            }
        assertEquals(true, ex.message?.contains("sig must be empty or 128-char hex"), ex.message)
    }

    @Test
    fun acceptsEmptySig() {
        // NIP-17 (kind:14 DMs) and NIP-37 drafts are stored as Event with sig="" because
        // the inner-event rumor is unsigned per NIP-59. Production caches contain ~5 K
        // such events; the deserializer must accept them.
        val event = parse(buildEventJson(sig = ""))
        assertEquals("", event.sig)
    }

    @Test
    fun rejectsKindAboveMax() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(kind = EventLimits.MAX_KIND + 1))
            }
        assertEquals(true, ex.message?.contains("kind"), ex.message)
    }

    @Test
    fun rejectsKindBelowZero() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                parse(buildEventJson(kind = -1))
            }
        assertEquals(true, ex.message?.contains("kind"), ex.message)
    }

    @Test
    fun acceptsAncientCreatedAt() {
        // Archive replay legitimately produces events with timestamps from years ago.
        // The deserializer doesn't bound createdAt; UI / feed-sort code can apply policy.
        val event = parse(buildEventJson(createdAt = 100L))
        assertEquals(100L, event.createdAt)
    }
}
