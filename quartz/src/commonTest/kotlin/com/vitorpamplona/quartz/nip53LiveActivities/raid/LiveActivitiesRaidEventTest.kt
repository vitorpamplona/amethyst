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
package com.vitorpamplona.quartz.nip53LiveActivities.raid

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveActivitiesRaidEventTest {
    private val sourceHost = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val targetHost = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private val author = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    private val dummySig = "0".repeat(128)

    @Test
    fun parsesRootAndMentionAddresses() {
        val event =
            LiveActivitiesRaidEvent(
                id = "1".repeat(64),
                pubKey = author,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("a", "${LiveActivitiesEvent.KIND}:$sourceHost:source-d", "wss://relay.example", "root"),
                        arrayOf("a", "${LiveActivitiesEvent.KIND}:$targetHost:target-d", "", "mention"),
                    ),
                content = "Heading over to stream!",
                sig = dummySig,
            )

        val from = assertNotNull(event.fromAddress())
        assertEquals(LiveActivitiesEvent.KIND, from.kind)
        assertEquals(sourceHost, from.pubKeyHex)
        assertEquals("source-d", from.dTag)

        val to = assertNotNull(event.toAddress())
        assertEquals(LiveActivitiesEvent.KIND, to.kind)
        assertEquals(targetHost, to.pubKeyHex)
        assertEquals("target-d", to.dTag)
    }

    @Test
    fun ignoresUnmarkedOrWrongKindATags() {
        val event =
            LiveActivitiesRaidEvent(
                id = "1".repeat(64),
                pubKey = author,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        // Wrong marker
                        arrayOf("a", "${LiveActivitiesEvent.KIND}:$sourceHost:x", "", "reply"),
                        // Wrong kind
                        arrayOf("a", "30023:$sourceHost:article", "", "root"),
                        // No marker at all
                        arrayOf("a", "${LiveActivitiesEvent.KIND}:$sourceHost:y"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertNull(event.fromAddress())
        assertNull(event.toAddress())
    }
}
