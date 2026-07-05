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
package com.vitorpamplona.quartz.experimental.ps1saves

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Ps1SaveEventTest {
    // Tags and content prefix from a real event seen in the wild
    // (6a21107f33e5aa5f02839812b6d1a5387cfa3df0e30398798430f3f3814e65e7 on nos.lol);
    // the full content is an 8 KiB memory-card block, truncated here, so id/sig
    // are zeroed placeholders (EventFactory does not verify them).
    private fun sampleEvent(): Event =
        EventFactory.create(
            id = "00".repeat(32),
            pubKey = "f1fc95d1634e54f7aa7fe816496b3f26b8e6100440c4500de1c2dfc6be2f60bf",
            createdAt = 1_783_151_940L,
            kind = Ps1SaveEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("d", "synctest-f1fc95d1-1"),
                    arrayOf("m", "synctest-f1fc95d1"),
                    arrayOf("block", "1"),
                    arrayOf("x", "4235e8c1d7164927b78dd50e0a12acbea04b2d80f0864fc3298e7d84225d8f41"),
                    arrayOf("state", "first"),
                    arrayOf("filename", "BASCUS-00001SOFTCARD"),
                    arrayOf("region", "America"),
                    arrayOf("title", "SYNCED OVER NOST Card"),
                    arrayOf("alt", "PS1 save 'SYNCED OVER NOST Card' (BASCUS-00001SOFTCARD)"),
                ),
            content = "5343110153594e434544204f564552204e4f53542043617264000000000000",
            sig = "00".repeat(64),
        )

    @Test
    fun factoryBuildsPs1SaveForKind38192() {
        assertIs<Ps1SaveEvent>(sampleEvent())
    }

    @Test
    fun kind38192IsNowKnown() {
        assertTrue(EventFactory.isKnownKind(Ps1SaveEvent.KIND), "kind 38192 should be a known kind")
    }

    @Test
    fun parsesPs1SaveFields() {
        val event = sampleEvent()
        assertIs<Ps1SaveEvent>(event)

        assertEquals("SYNCED OVER NOST Card", event.saveTitle())
        assertEquals("BASCUS-00001SOFTCARD", event.filename())
        assertEquals("America", event.region())
        assertEquals(1, event.blockNumber())
        assertEquals("PS1 save 'SYNCED OVER NOST Card' (BASCUS-00001SOFTCARD)", event.summary())
        assertEquals("synctest-f1fc95d1-1", event.dTag())
    }

    @Test
    fun blockNumberIsNullWhenTagIsMissingOrNotANumber() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = "00".repeat(32),
                createdAt = 1_783_151_940L,
                kind = Ps1SaveEvent.KIND,
                tags = arrayOf(arrayOf("d", "card-1"), arrayOf("block", "not-a-number")),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<Ps1SaveEvent>(event)

        assertEquals(null, event.blockNumber())
        assertEquals(null, event.saveTitle())
    }
}
