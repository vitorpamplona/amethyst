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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * State-machine tests for [BackwardRelayPager]: drive its relay callbacks directly (no network) and
 * assert the cursor / done / stalled / exhausted bookkeeping — the logic that backed the "All caught up
 * while messages missing" and the stalled-vs-done bugs. The relay's own `until`+`limit`+EOSE wire
 * behaviour is covered separately against the in-process relay in `UntilLimitPagingRelayTest`.
 */
class BackwardRelayPagerTest {
    private val r1 = NormalizedRelayUrl("wss://r1.example/")
    private val r2 = NormalizedRelayUrl("wss://r2.example/")
    private val r3 = NormalizedRelayUrl("wss://r3.example/")
    private val key = "acct"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun pagerOf(vararg relays: NormalizedRelayUrl): BackwardRelayPager<String> = BackwardRelayPager<String>("test") { relays.toList() }.also { it.activate(key) }

    @Test
    fun firstPageRequestsTheFloorAndAnEmptyPageIsCaughtUp() {
        val p = pagerOf(r1)
        assertFalse(p.exhausted.value)

        assertTrue(p.advance(key, r1, scope))
        // The very first page asks `until = floor`.
        assertEquals(p.floorFor(key), p.requestedUntilFor(key, r1))

        // Empty page + EOSE → that relay is done; the only relay is done → genuinely caught up.
        assertTrue(p.onEose(key, r1))
        assertTrue(
            p.relayProgress.value
                .getValue(r1)
                .done,
        )
        assertTrue(p.exhausted.value)
        assertEquals(0, p.stalledCount.value)
    }

    @Test
    fun nonEmptyPageMovesTheCursorThenBottomsOut() {
        val p = pagerOf(r1)
        p.advance(key, r1, scope)

        // A page of three events; the oldest is 80, so the reached cursor drops to 80 (not done).
        p.onEvent(key, r1, 100)
        p.onEvent(key, r1, 80)
        p.onEvent(key, r1, 90)
        assertFalse(p.onEose(key, r1))
        assertFalse(
            p.relayProgress.value
                .getValue(r1)
                .done,
        )
        assertEquals(80L, p.reachedBack.value)
        assertFalse(p.exhausted.value)

        // The next page must start strictly below the oldest reached (80 → until 79).
        assertTrue(p.advance(key, r1, scope))
        assertEquals(79L, p.requestedUntilFor(key, r1))

        // Empty page now → done → caught up.
        assertTrue(p.onEose(key, r1))
        assertTrue(p.exhausted.value)
        assertEquals(0, p.stalledCount.value)
    }

    @Test
    fun aStalledRelayMakesExhaustionIncompleteNotCaughtUp() {
        val p = pagerOf(r1, r2)
        p.advance(key, r1, scope)
        p.advance(key, r2, scope)

        // r1 genuinely bottoms out; r2 is still pending, so not exhausted yet.
        p.onEose(key, r1)
        assertFalse(p.exhausted.value)

        // r2 auth-walls the REQ → stalled (kept, not done).
        p.onClosed(key, r2, "auth-required")
        assertTrue(
            p.relayProgress.value
                .getValue(r2)
                .stalled,
        )
        assertFalse(
            p.relayProgress.value
                .getValue(r2)
                .done,
        )

        // Every relay is now done-or-stalled → exhausted, but it is INCOMPLETE: one relay unreachable.
        assertTrue(p.exhausted.value)
        assertEquals(1, p.stalledCount.value)
    }

    @Test
    fun cannotConnectAlsoStalls() {
        val p = pagerOf(r1)
        p.advance(key, r1, scope)
        p.onCannotConnect(key, r1, "offline")
        assertTrue(
            p.relayProgress.value
                .getValue(r1)
                .stalled,
        )
        assertTrue(p.exhausted.value)
        assertEquals(1, p.stalledCount.value)
    }

    @Test
    fun reAdvancingAStalledRelayClearsTheStallAndUnExhausts() {
        val p = pagerOf(r1)
        p.advance(key, r1, scope)
        p.onClosed(key, r1, "auth-required")
        assertTrue(p.exhausted.value)
        assertEquals(1, p.stalledCount.value)

        // Retrying it re-arms the relay: no longer stalled, no longer exhausted.
        assertTrue(p.advance(key, r1, scope))
        assertFalse(
            p.relayProgress.value
                .getValue(r1)
                .stalled,
        )
        assertFalse(p.exhausted.value)
        assertEquals(0, p.stalledCount.value)
    }

    @Test
    fun reachedBackIsTheDeepestCursorAcrossRelays() {
        val p = pagerOf(r1, r2)
        p.advance(key, r1, scope)
        p.advance(key, r2, scope)

        p.onEvent(key, r1, 500)
        p.onEose(key, r1) // r1 reached 500

        p.onEvent(key, r2, 300)
        p.onEose(key, r2) // r2 reached 300

        // Deepest = the oldest point any relay has reached.
        assertEquals(300L, p.reachedBack.value)
    }

    @Test
    fun aDoneRelayWillNotAdvanceAgain() {
        val p = pagerOf(r1)
        p.advance(key, r1, scope)
        p.onEose(key, r1) // empty → done
        assertFalse(p.advance(key, r1, scope))
    }

    @Test
    fun advanceAllArmsEveryNotDoneRelay() {
        val p = pagerOf(r1, r2, r3)
        // r2 already finished; advanceAll should arm only r1 and r3.
        p.advance(key, r2, scope)
        p.onEose(key, r2)

        assertTrue(p.advanceAll(key, scope))
        assertEquals(setOf(r1, r3), p.armedRelays(key, listOf(r1, r2, r3)).toSet())
    }

    @Test
    fun switchingActiveKeyRepointsTheDisplayFlows() {
        val keyA = "a"
        val keyB = "b"
        val relaysByKey = mapOf(keyA to listOf(r1), keyB to listOf(r2))
        val p = BackwardRelayPager<String>("test") { relaysByKey[it] }

        p.activate(keyA)
        p.advance(keyA, r1, scope)
        p.onClosed(keyA, r1, "auth-required") // A: exhausted + 1 stalled
        assertTrue(p.exhausted.value)
        assertEquals(1, p.stalledCount.value)

        // Switching to a fresh key B repoints the flows to B's own state: nothing stalled, and its
        // reach sits at B's floor (no history fetched yet — the markers start at the live-tail boundary).
        p.activate(keyB)
        assertFalse(p.exhausted.value)
        assertEquals(0, p.stalledCount.value)
        assertEquals(p.floorFor(keyB), p.reachedBack.value)

        // Switching back to A restores its remembered terminal state.
        p.activate(keyA)
        assertTrue(p.exhausted.value)
        assertEquals(1, p.stalledCount.value)
    }
}
