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

import com.vitorpamplona.quartz.nip01Core.relay.client.paging.UntilLimitPager
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UntilLimitPagerTest {
    private val key = "acct"
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://a.relay")!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull("wss://b.relay")!!
    private val start = 1_000L

    @Test
    fun unarmedRelayIsNotRequestedButCountsAsActive() {
        val pager = UntilLimitPager<String>()
        assertFalse(pager.isArmed(key, relayA))
        assertEquals(emptyList<Any>(), pager.armedRelays(key, listOf(relayA)))
        // not done, so still "active" (there is history to ask for once advanced)
        assertEquals(listOf(relayA), pager.activeRelays(key, listOf(relayA)))
        // marker sits at the floor until it delivers
        assertEquals(start, pager.reachedUntilFor(key, relayA, start))
    }

    @Test
    fun firstAdvanceRequestsTheFloorThenSubsequentPagesStepBelowReached() {
        val pager = UntilLimitPager<String>()

        assertTrue(pager.advance(key, relayA, start))
        assertTrue(pager.isArmed(key, relayA))
        assertEquals(start, pager.requestedUntilFor(key, relayA))

        // page returns events; oldest seen = 800
        pager.onEvent(key, relayA, 900)
        pager.onEvent(key, relayA, 800)
        pager.onEose(key, relayA)
        assertEquals(800L, pager.reachedUntilFor(key, relayA, start))
        // EOSE does NOT move the requested cursor — the relay parks at the same filter
        assertEquals(start, pager.requestedUntilFor(key, relayA))

        // next advance steps to reached - 1
        assertTrue(pager.advance(key, relayA, start))
        assertEquals(799L, pager.requestedUntilFor(key, relayA))
    }

    @Test
    fun emptyPageMarksRelayDoneAndBlocksFurtherAdvance() {
        val pager = UntilLimitPager<String>()
        pager.advance(key, relayA, start)
        pager.onEose(key, relayA) // no events
        assertTrue(pager.isDone(key, relayA))
        assertFalse(pager.advance(key, relayA, start))
        assertEquals(emptyList<Any>(), pager.activeRelays(key, listOf(relayA)))
        assertEquals(emptyList<Any>(), pager.armedRelays(key, listOf(relayA)))
    }

    @Test
    fun aPageThatDoesNotStepOlderEndsTheRelayInsteadOfLooping() {
        val pager = UntilLimitPager<String>()
        pager.advance(key, relayA, start)
        pager.onEvent(key, relayA, 800)
        pager.onEose(key, relayA)
        assertEquals(800L, pager.reachedUntilFor(key, relayA, start))

        // misbehaving relay: next page echoes an event no older than what we already reached
        pager.advance(key, relayA, start) // requested = 799
        pager.onEvent(key, relayA, 900) // newer than reached(800) — not strictly older
        pager.onEose(key, relayA)
        assertTrue("a non-advancing page should end the relay, not re-loop", pager.isDone(key, relayA))
        assertEquals(800L, pager.reachedUntilFor(key, relayA, start))
    }

    @Test
    fun relaysAreTrackedIndependently() {
        val pager = UntilLimitPager<String>()
        pager.advance(key, relayA, start)
        pager.onEvent(key, relayA, 500)
        pager.onEose(key, relayA)
        // B never advanced
        assertEquals(listOf(relayA), pager.armedRelays(key, listOf(relayA, relayB)))
        assertEquals(500L, pager.reachedUntilFor(key, relayA, start))
        assertEquals(start, pager.reachedUntilFor(key, relayB, start))
        // deepest reached across both = A's 500 (B counts as the floor)
        assertEquals(500L, pager.deepestReached(key, listOf(relayA, relayB), start))
    }

    @Test
    fun deepestReachedIsNullWhenNoRelays() {
        val pager = UntilLimitPager<String>()
        assertEquals(null, pager.deepestReached(key, emptyList(), start))
    }
}
