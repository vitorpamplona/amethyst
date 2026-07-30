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
package com.vitorpamplona.amethyst.commons.model.concord

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The per-community banner state: one value at a time, and retractions that can't stomp a more
 * important state that landed meanwhile.
 *
 * The single-value shape is the point. Stranding, dissolution and a dead invite link all explain the
 * same thing — why the feed is empty or frozen — and stacking two banners is how a status surface
 * becomes chrome users scroll past.
 */
class ConcordCommunityHealthTest {
    private val alpha = "a1".repeat(32)
    private val beta = "b2".repeat(32)

    @Test
    fun aCommunityStartsHealthyAndSubscribingDoesNotChangeThat() {
        val state = ConcordCommunityHealthState()
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(alpha))
        assertEquals(ConcordCommunityHealth.Healthy, state.flowFor(alpha).value)
    }

    @Test
    fun theFlowIsStableAcrossReadsSoTheUiCanSubscribeBeforeAnyWrite() {
        // The banner collects on first composition, which routinely happens before the rekey drain
        // has anything to say; both sides must share one flow instance or the update never arrives.
        val state = ConcordCommunityHealthState()
        val first = state.flowFor(alpha)
        state.set(alpha, ConcordCommunityHealth.Dissolved)

        assertSame(first, state.flowFor(alpha))
        assertEquals(ConcordCommunityHealth.Dissolved, first.value)
    }

    @Test
    fun communitiesAreIndependent() {
        val state = ConcordCommunityHealthState()
        state.set(alpha, ConcordCommunityHealth.Stranded(strandedAtEpoch = 3, newEpoch = 4, recoverable = true))

        assertTrue(state.currentFor(alpha) is ConcordCommunityHealth.Stranded)
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(beta))
    }

    @Test
    fun aFinishedCatchUpRetractsItselfWithoutClearingSomethingWorse() {
        val state = ConcordCommunityHealthState()
        state.set(alpha, ConcordCommunityHealth.CatchingUp(fromEpoch = 3))

        // The recovery that started the catch-up finished: retract only the catch-up.
        state.clearIf(alpha) { it is ConcordCommunityHealth.CatchingUp }
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(alpha))

        // Now the same retraction runs when the community has since been dissolved. Dissolution is
        // terminal and was observed independently, so a late "recovery finished" must not erase it.
        state.set(alpha, ConcordCommunityHealth.Dissolved)
        state.clearIf(alpha) { it is ConcordCommunityHealth.CatchingUp }
        assertEquals(ConcordCommunityHealth.Dissolved, state.currentFor(alpha))
    }

    @Test
    fun adoptingARotationClearsEverythingExceptDissolution() {
        // What the rekey drain does after successfully adopting a new root.
        val state = ConcordCommunityHealthState()

        state.set(alpha, ConcordCommunityHealth.Stranded(3, 4, recoverable = true))
        state.clearIf(alpha) { it !is ConcordCommunityHealth.Dissolved }
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(alpha))

        state.set(alpha, ConcordCommunityHealth.RecoveryFailed(ConcordCommunityHealth.RecoveryFailed.Reason.LINK_REVOKED))
        state.clearIf(alpha) { it !is ConcordCommunityHealth.Dissolved }
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(alpha))

        state.set(alpha, ConcordCommunityHealth.Dissolved)
        state.clearIf(alpha) { it !is ConcordCommunityHealth.Dissolved }
        assertEquals(ConcordCommunityHealth.Dissolved, state.currentFor(alpha))
    }

    @Test
    fun strandingRecordsBothEpochsAndWhetherAWayBackExists() {
        // recoverable=false is the dead end: no stored invite link means nothing to re-resolve, so the
        // copy has to ask for a new invite rather than promise a reconnect.
        val recoverable = ConcordCommunityHealth.Stranded(strandedAtEpoch = 7, newEpoch = 8, recoverable = true)
        assertEquals(7, recoverable.strandedAtEpoch)
        assertEquals(8, recoverable.newEpoch)

        val dead = ConcordCommunityHealth.Stranded(strandedAtEpoch = 7, newEpoch = 8, recoverable = false)
        assertEquals(false, dead.recoverable)
    }

    @Test
    fun clearResetsEveryKnownCommunity() {
        val state = ConcordCommunityHealthState()
        state.set(alpha, ConcordCommunityHealth.Dissolved)
        state.set(beta, ConcordCommunityHealth.Stranded(1, 2, recoverable = false))

        state.clear()

        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(alpha))
        assertEquals(ConcordCommunityHealth.Healthy, state.currentFor(beta))
    }
}
