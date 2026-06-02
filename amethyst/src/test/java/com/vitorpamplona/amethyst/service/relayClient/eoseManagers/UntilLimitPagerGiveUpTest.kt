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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UntilLimitPagerGiveUpTest {
    private val mine = NormalizedRelayUrl("wss://vitor.nostr1.com/")
    private val silent = NormalizedRelayUrl("wss://relay.ditto.pub/")
    private val all = listOf(mine, silent)

    @Test
    fun givenUpRelayLeavesTheActiveSet() {
        val pager = UntilLimitPager<String>()

        assertEquals(all, pager.activeRelays("k", all))

        assertTrue("first give-up takes effect", pager.giveUp("k", silent))
        assertEquals(listOf(mine), pager.activeRelays("k", all))

        assertFalse("giving up twice is a no-op", pager.giveUp("k", silent))
    }

    @Test
    fun givingUpEveryRelayExhaustsTheKey() {
        val pager = UntilLimitPager<String>()

        // mine pages to empty cleanly; the silent relay never answers and is given up.
        pager.beginRound("k", all)
        pager.onEose("k", mine) // empty page + EOSE => done
        pager.giveUp("k", silent)

        assertTrue("no relay left to ask", pager.activeRelays("k", all).isEmpty())
    }

    @Test
    fun aRelayThatAlreadyFinishedIsNotMarkedGivenUp() {
        val pager = UntilLimitPager<String>()

        pager.beginRound("k", listOf(mine))
        pager.onEose("k", mine) // done

        // A late silence sweep must not "give up" a relay that already finished cleanly.
        assertFalse(pager.giveUp("k", mine))
        assertTrue(pager.isDone("k", mine))
    }
}
