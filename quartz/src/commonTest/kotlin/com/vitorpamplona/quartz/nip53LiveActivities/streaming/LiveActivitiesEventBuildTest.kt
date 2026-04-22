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
package com.vitorpamplona.quartz.nip53LiveActivities.streaming

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveActivitiesEventBuildTest {
    private val host = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val guest = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    @Test
    fun buildAssemblesAllRequiredAndOptionalTags() {
        val template =
            LiveActivitiesEvent.build(dTag = "my-stream", createdAt = 1_700_000_000L) {
                title("My Stream")
                summary("A live event")
                image("https://example.com/img.png")
                streaming("https://example.com/stream.m3u8")
                starts(1_700_000_000L)
                status(StatusTag.STATUS.LIVE)
                currentParticipants(42)
                totalParticipants(100)
                participant(host, role = "Host")
                participant(guest, role = "Speaker")
            }

        assertEquals(LiveActivitiesEvent.KIND, template.kind)
        assertEquals("", template.content)

        val tagsByName = template.tags.groupBy { it[0] }

        assertEquals("my-stream", tagsByName["d"]?.single()?.get(1))
        assertEquals("My Stream", tagsByName["title"]?.single()?.get(1))
        assertEquals("A live event", tagsByName["summary"]?.single()?.get(1))
        assertEquals("https://example.com/img.png", tagsByName["image"]?.single()?.get(1))
        assertEquals("https://example.com/stream.m3u8", tagsByName["streaming"]?.single()?.get(1))
        assertEquals("1700000000", tagsByName["starts"]?.single()?.get(1))
        assertEquals("live", tagsByName["status"]?.single()?.get(1))
        assertEquals("42", tagsByName["current_participants"]?.single()?.get(1))
        assertEquals("100", tagsByName["total_participants"]?.single()?.get(1))
        assertEquals(LiveActivitiesEvent.ALT, tagsByName["alt"]?.single()?.get(1))

        // Per NIP-53, role lives at index 3 of the p-tag; a missing relay must be
        // represented as an empty string placeholder, not dropped.
        val pTags = tagsByName["p"] ?: emptyList()
        assertEquals(2, pTags.size)
        assertContentEquals(arrayOf("p", host, "", "Host"), pTags[0])
        assertContentEquals(arrayOf("p", guest, "", "Speaker"), pTags[1])
    }

    @Test
    fun buildAllowsMultiplePinnedAndHashtagsWithoutClobbering() {
        val template =
            LiveActivitiesEvent.build(dTag = "my-stream") {
                title("title")
                pinned("1".repeat(64))
                pinned("2".repeat(64))
            }

        val pinned = template.tags.filter { it[0] == "pinned" }
        assertEquals(2, pinned.size)
    }

    @Test
    fun buildUsesRandomDTagWhenNotProvided() {
        val a = LiveActivitiesEvent.build { title("a") }
        val b = LiveActivitiesEvent.build { title("b") }
        val aDTag = a.tags.first { it[0] == "d" }[1]
        val bDTag = b.tags.first { it[0] == "d" }[1]
        assertNotNull(aDTag)
        assertTrue(aDTag.isNotEmpty())
        assertTrue(aDTag != bDTag, "random dTag should differ between builds")
    }
}
