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
package com.vitorpamplona.quartz.experimental.agora

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FundraiserEventTest {
    private fun sampleEvent(): Event =
        EventFactory.create(
            id = "45e3f48a12b461fc4684002136944ad061921368e993a832c506f0eebb5cf807",
            pubKey = "652b1b36da75133890148f685af9038e1934e00a276815ac6b16d8b249d53de1",
            createdAt = 1_780_094_572L,
            kind = FundraiserEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("d", "please-help-me-i-m-amira-from-gaza-i-need-your-help"),
                    arrayOf("title", "Please help me, I'm Amira from Gaza"),
                    arrayOf("banner", "https://blossom.primal.net/abc.jpg"),
                    arrayOf("goal", "10000"),
                    arrayOf("deadline", "9700905600"),
                    arrayOf("w", "bc1p2zmtzrlxde9zsd3fxhwf5fpxmutzzkvq7gnuwmxcans7wwxjee7s9dft56"),
                    arrayOf("w", "sp1qqfsmnyaerhghjceqfu95az6qfw0ugf4f"),
                    arrayOf("t", "community"),
                    arrayOf("t", "emergency"),
                ),
            content = "Help Amira from Gaza continue her education.",
            sig = "00".repeat(64),
        )

    @Test
    fun factoryBuildsFundraiserForKind33863() {
        val event = sampleEvent()
        assertTrue(
            event is FundraiserEvent,
            "Expected a FundraiserEvent but got ${event::class.simpleName}",
        )
    }

    @Test
    fun kind33863IsNowKnown() {
        assertTrue(EventFactory.isKnownKind(FundraiserEvent.KIND), "kind 33863 should be a known kind")
    }

    @Test
    fun parsesFundraiserFields() {
        val event = sampleEvent()
        assertIs<FundraiserEvent>(event)

        assertEquals("Please help me, I'm Amira from Gaza", event.title())
        assertEquals("https://blossom.primal.net/abc.jpg", event.coverImage())
        assertEquals(10000L, event.goal())
        assertEquals(9700905600L, event.deadline())
        assertEquals(2, event.wallets().size)
        assertEquals("bc1p2zmtzrlxde9zsd3fxhwf5fpxmutzzkvq7gnuwmxcans7wwxjee7s9dft56", event.wallets().first())
        assertEquals(listOf("community", "emergency"), event.topics())
        assertEquals("please-help-me-i-m-amira-from-gaza-i-need-your-help", event.dTag())
    }
}
