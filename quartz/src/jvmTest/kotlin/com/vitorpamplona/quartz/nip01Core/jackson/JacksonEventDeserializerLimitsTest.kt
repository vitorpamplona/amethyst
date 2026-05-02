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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Jackson-path coverage for security review 2026-04-24 §2.3 / Finding #5.
// The kotlinx path is exercised by EventLimitsTest in commonTest; this file targets the
// streaming Jackson deserializers (EventDeserializer + TagArrayDeserializer) which terminate
// the parse early on cap overflow.
class JacksonEventDeserializerLimitsTest {
    private val id = "0".repeat(63) + "1"
    private val pubKey = "0".repeat(63) + "2"
    private val sig = "0".repeat(128)

    private fun parse(json: String): Event = JacksonMapper.mapper.readValue(json)

    private fun buildEventJson(
        contentLength: Int = 10,
        tagCount: Int = 1,
        tagInnerCount: Int = 2,
        tagValueLength: Int = 5,
    ): String {
        val content = "x".repeat(contentLength)
        val tagValue = "v".repeat(tagValueLength)
        val inner = "\"t\"" + ",\"$tagValue\"".repeat((tagInnerCount - 1).coerceAtLeast(0))
        val tag = "[$inner]"
        val tags = (1..tagCount).joinToString(",") { tag }
        return """{"id":"$id","pubkey":"$pubKey","created_at":1,"kind":1,"tags":[$tags],"content":"$content","sig":"$sig"}"""
    }

    @Test
    fun acceptsEventWithinLimits() {
        val event = parse(buildEventJson(contentLength = 100, tagCount = 5, tagInnerCount = 3, tagValueLength = 50))
        assertEquals(5, event.tags.size)
        assertEquals(100, event.content.length)
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
}
