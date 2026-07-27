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
package com.vitorpamplona.quartz.buzz.workspace

import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two things a Buzz relay requires of a create (kind-9007) that plain NIP-29 does not. Both
 * failed silently — the relay either rejected the event outright or created the channel under an id
 * the client never used — so "create a group" published two events and produced nothing the user
 * could see.
 */
class BuzzChannelCreateTest {
    @Test
    fun createCarriesTheMetadataBuzzReadsOffTheCreateEvent() {
        // `ingest.rs` rejects a 9007 pre-storage with "invalid: channel name is required" unless the
        // create event itself names the channel; NIP-29 alone would leave this to the 9002.
        val tpl =
            CreateGroupEvent.build(
                groupId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
                name = "design",
                about = "where design happens",
                visibility = BUZZ_VISIBILITY_PRIVATE,
                channelType = BUZZ_CHANNEL_TYPE_FORUM,
            )

        assertEquals(CreateGroupEvent.KIND, tpl.kind)
        assertEquals("design", tpl.tags.firstTagValue("name"))
        assertEquals("where design happens", tpl.tags.firstTagValue("about"))
        assertEquals("private", tpl.tags.firstTagValue("visibility"))
        assertEquals("forum", tpl.tags.firstTagValue("channel_type"))
        assertEquals("3f2504e0-4f89-41d3-9a0c-0305e82c3301", tpl.tags.firstTagValue("h"))
    }

    /** A plain NIP-29 create stays exactly as the spec has it: the id and nothing else. */
    @Test
    fun createWithoutBuzzMetadataIsUnchanged() {
        val tpl = CreateGroupEvent.build(groupId = "abc123")

        assertEquals("abc123", tpl.tags.firstTagValue("h"))
        assertNull(tpl.tags.firstTagValue("name"))
        assertNull(tpl.tags.firstTagValue("visibility"))
        assertNull(tpl.tags.firstTagValue("channel_type"))
    }

    /** Blank input is omitted rather than sent as an empty tag, which the relay rejects the same way. */
    @Test
    fun blankMetadataIsOmitted() {
        val tpl = CreateGroupEvent.build(groupId = "abc123", name = "  ", about = "")

        assertNull(tpl.tags.firstTagValue("name"))
        assertNull(tpl.tags.firstTagValue("about"))
    }

    /**
     * Buzz keys channels by UUID and parses the `h` tag with `val.parse::<Uuid>()`. A NIP-29-style
     * 16-char hex id does not parse, so the relay ignores the client's id and creates the channel
     * under one of its own — leaving the app subscribed to an id that does not exist, which is what
     * made a freshly created channel open on an empty feed with a hex id for a title.
     */
    @Test
    fun newChannelIdIsAParseableV4Uuid() {
        val id = newBuzzChannelId()

        assertEquals(36, id.length)
        assertEquals(listOf(8, 4, 4, 4, 12), id.split("-").map { it.length })
        assertTrue(id.all { it.isDigit() || it in 'a'..'f' || it == '-' }, "lowercase hex + dashes only: $id")
        assertEquals('4', id[14], "version nibble must say v4")
        assertTrue(id[19] in "89ab", "variant nibble must be RFC-4122: ${id[19]}")
        assertTrue(newBuzzChannelId() != newBuzzChannelId(), "ids must not repeat")
    }
}
