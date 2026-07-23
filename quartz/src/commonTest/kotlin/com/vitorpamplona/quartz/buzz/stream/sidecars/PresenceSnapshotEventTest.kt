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
package com.vitorpamplona.quartz.buzz.stream.sidecars

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PresenceSnapshotEventTest {
    private val alice = "aaaa000000000000000000000000000000000000000000000000000000000001"
    private val bob = "bbbb000000000000000000000000000000000000000000000000000000000002"

    @Test
    fun buildEncodesEntriesAsJsonContent() {
        val snapshot =
            PresenceSnapshotPayload(
                entries =
                    listOf(
                        PresenceEntry(pubkey = alice, status = "online", lastSeenAt = 1_700_000_000L),
                        PresenceEntry(pubkey = bob, status = "away"),
                    ),
            )
        val template =
            PresenceSnapshotEvent.build(
                snapshot = snapshot,
                createdAt = 1_700_000_000L,
            )

        assertEquals(PresenceSnapshotEvent.KIND, template.kind)
        assertEquals(40902, template.kind)

        val decoded = PresenceSnapshotPayload.decodeFromJson(template.content)
        assertEquals(2, decoded.entries.size)
        assertEquals(alice, decoded.entries[0].pubkey)
        assertEquals("online", decoded.entries[0].status)
        assertEquals(1_700_000_000L, decoded.entries[0].lastSeenAt)
        assertEquals("away", decoded.entries[1].status)
    }

    @Test
    fun accessorDecodesContent() {
        val json = """{"entries":[{"pubkey":"$alice","status":"online"}]}"""
        val event =
            PresenceSnapshotEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(),
                content = json,
                sig = "00",
            )

        val snapshot = event.snapshot()
        assertNotNull(snapshot)
        assertEquals(1, snapshot.entries.size)
        assertEquals(alice, snapshot.entries[0].pubkey)
        assertEquals("online", snapshot.entries[0].status)
    }
}
