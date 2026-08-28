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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip51Lists.originlessServers.OriginlessServersEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `OriginlessServersListState` reads the node list off the addressable note in [LocalCache], and the
 * settings backup that survives a restart is written from that same note. A kind missing from
 * `justConsumeInner`'s dispatch is dropped as "Event Not Supported", which silently empties the list
 * on every launch and makes remote edits invisible -- so the ingestion itself is worth asserting.
 *
 * `LocalCache` is a process-wide object, so this test uses ids of its own.
 */
class OriginlessServersIngestionTest {
    private val author = "e1".repeat(32)

    private fun serverList() =
        OriginlessServersEvent(
            id = "e2".repeat(32),
            pubKey = author,
            createdAt = 1_700_000_000L,
            // Servers live in the NIP-44 payload; the public tags carry no URL.
            tags = arrayOf(arrayOf("client", "amy")),
            content = "encrypted-payload-not-read-during-ingestion",
            sig = "sig",
        )

    @Test
    fun anOriginlessServerListArrivingFromARelayLandsInTheCache() {
        val event = serverList()

        val consumed = LocalCache.justConsume(event, null, true)

        assertTrue("kind ${OriginlessServersEvent.KIND} must be consumed, not dropped as unsupported", consumed)

        val note = LocalCache.getAddressableNoteIfExists(OriginlessServersEvent.createAddress(author))
        assertNotNull("the node list must be readable from the addressable note", note)
        assertEquals(event, note!!.event)
    }
}
