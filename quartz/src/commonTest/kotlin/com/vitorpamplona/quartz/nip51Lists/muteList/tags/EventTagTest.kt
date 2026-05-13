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
package com.vitorpamplona.quartz.nip51Lists.muteList.tags

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventTagTest {
    private val rootHex = "3ae34f70016c33d36f9e6ad395591ea36fee7ac488d1dad383ae64ae3d988f50"
    private val authorHex = "d0debf9fb12def81f43d7c69429bb784812ac1e4d2d53a202db6aac7ea4b466c"
    private val relayHint = "wss://relay.damus.io"

    @Test fun isTagged_eventTagWithIdOnly() {
        assertTrue(EventTag.isTagged(arrayOf("e", rootHex)))
    }

    @Test fun isTagged_eventTagWithRelayHint() {
        assertTrue(EventTag.isTagged(arrayOf("e", rootHex, relayHint)))
    }

    @Test fun isTagged_rejectsShortId() {
        assertFalse(EventTag.isTagged(arrayOf("e", "tooShort")))
    }

    @Test fun isTagged_rejectsWrongPrefix() {
        assertFalse(EventTag.isTagged(arrayOf("p", rootHex)))
    }

    @Test fun parse_idOnly() {
        val tag = assertNotNull(EventTag.parse(arrayOf("e", rootHex)))
        assertEquals(rootHex, tag.eventId)
        assertNull(tag.relayHint)
        assertNull(tag.pubKeyHint)
    }

    @Test fun parse_withRelayHint() {
        val tag = assertNotNull(EventTag.parse(arrayOf("e", rootHex, relayHint)))
        assertEquals(rootHex, tag.eventId)
        assertEquals("wss://relay.damus.io/", tag.relayHint?.url)
    }

    @Test fun parse_withRelayAndPubkeyHint() {
        val tag = assertNotNull(EventTag.parse(arrayOf("e", rootHex, relayHint, authorHex)))
        assertEquals(authorHex, tag.pubKeyHint)
    }

    @Test fun parse_rejectsShortId() {
        assertNull(EventTag.parse(arrayOf("e", "tooShort")))
    }

    @Test fun parseId_extractsIdOnly() {
        assertEquals(rootHex, EventTag.parseId(arrayOf("e", rootHex, relayHint)))
    }

    @Test fun toTagArray_roundTripsWithHints() {
        val original = EventTag(rootHex, RelayUrlNormalizer.normalizeOrNull(relayHint), authorHex)
        val parsed = assertNotNull(EventTag.parse(original.toTagArray()))
        assertEquals(rootHex, parsed.eventId)
        assertEquals(authorHex, parsed.pubKeyHint)
    }

    @Test fun toTagIdOnly_stripsHints() {
        val tag = EventTag(rootHex, RelayUrlNormalizer.normalizeOrNull(relayHint), authorHex)
        val stripped = tag.toTagIdOnly()
        assertEquals(2, stripped.size)
        assertEquals("e", stripped[0])
        assertEquals(rootHex, stripped[1])
    }

    @Test fun muteTagCompanion_parsesEventTag() {
        val parsed = assertNotNull(MuteTag.parse(arrayOf("e", rootHex)))
        assertTrue(parsed is EventTag)
    }

    @Test fun muteTagCompanion_isTaggedRecognizesEventTag() {
        assertTrue(MuteTag.isTagged(arrayOf("e", rootHex)))
    }
}
