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
package com.vitorpamplona.quartz.nip53LiveActivities.presence

import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingRoomPresenceEventTest {
    private val participant = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Test
    fun kind10312IsReplaceable() {
        val event =
            MeetingRoomPresenceEvent(
                id = "0".repeat(64),
                pubKey = participant,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("a", "30313:${"b".repeat(64)}:annual-2026"),
                        arrayOf("hand", "1"),
                    ),
                content = "",
                sig = "0".repeat(128),
            )

        // Per NIP-53, kind 10312 is a regular replaceable event: "presence can only
        // be indicated in one room at a time". Replaceable events have a fixed empty
        // d-tag regardless of what's in the event tags.
        assertEquals("", event.dTag())
        assertEquals("${MeetingRoomPresenceEvent.KIND}:$participant:", event.addressTag())
    }

    @Test
    fun createAddressKeysByPubKeyOnly() {
        val a = MeetingRoomPresenceEvent.createAddress(participant)
        assertEquals(MeetingRoomPresenceEvent.KIND, a.kind)
        assertEquals(participant, a.pubKeyHex)
        assertEquals("", a.dTag)

        val aTag = MeetingRoomPresenceEvent.createAddressATag(participant)
        assertEquals(MeetingRoomPresenceEvent.KIND, aTag.kind)
        assertEquals(participant, aTag.pubKeyHex)
        assertEquals("", aTag.dTag)

        assertEquals(
            "${MeetingRoomPresenceEvent.KIND}:$participant:",
            MeetingRoomPresenceEvent.createAddressTag(participant),
        )
    }
}
