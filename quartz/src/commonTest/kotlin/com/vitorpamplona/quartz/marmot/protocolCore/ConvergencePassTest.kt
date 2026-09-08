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
package com.vitorpamplona.quartz.marmot.protocolCore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bounded convergence pass: when a batch freezes, and what does or does not
 * move the deadlines.
 *
 * Driven by a fake monotonic clock, because the point of these rules is that
 * they depend on elapsed time and nothing else — asserting them against a real
 * clock would make the tests flaky and prove less.
 */
class ConvergencePassTest {
    private val policy = ConvergencePolicy.V1

    private class FakeClock(
        var nowMs: Long = 0,
    ) {
        fun advance(ms: Long) {
            nowMs += ms
        }
    }

    private fun pass(
        clock: FakeClock,
        baseEpoch: Long = 8,
    ) = ConvergencePass(passBaseEpoch = baseEpoch, policy = policy, monotonicNowMs = { clock.nowMs })

    @Test
    fun aQuietPassClosesAfterTheQuiescenceWindow() {
        val clock = FakeClock()
        val pass = pass(clock)

        clock.advance(policy.settlementQuiescenceMs - 1)
        assertFalse(pass.isClosed())
        assertEquals(ConvergenceStatus.SYNCING, pass.status())

        clock.advance(1)
        assertTrue(pass.isClosed())
        assertEquals(ConvergenceStatus.RESOLVING, pass.status())
        assertEquals(ConvergenceStatus.SETTLED, pass.status(resolutionComplete = true))
    }

    @Test
    fun selectionRelevantInputRestartsQuiescence() {
        val clock = FakeClock()
        val pass = pass(clock)

        clock.advance(900)
        assertTrue(pass.admit("commit", InputRelevance.SELECTION_RELEVANT))

        clock.advance(900)
        assertFalse(pass.isClosed(), "the window restarted, so 1800ms of activity has not closed it")

        clock.advance(100)
        assertTrue(pass.isClosed())
    }

    @Test
    fun ordinaryInputDoesNotRestartQuiescence() {
        // Chat traffic that cannot change branch selection must not hold the
        // pass open — outbound work is gated on settling, so a busy group would
        // otherwise never send anything.
        val clock = FakeClock()
        val pass = pass(clock)

        clock.advance(900)
        assertTrue(pass.admit("chat", InputRelevance.ORDINARY))

        clock.advance(100)
        assertTrue(pass.isClosed(), "ordinary input left the original window running")
    }

    @Test
    fun theAbsoluteDeadlineIsNeverExtended() {
        // A steady stream of selection-relevant input keeps restarting
        // quiescence, so without the hard deadline the pass would never close.
        val clock = FakeClock()
        val pass = pass(clock)

        var admitted = 0
        repeat(20) {
            clock.advance(500)
            if (pass.admit("commit", InputRelevance.SELECTION_RELEVANT)) admitted++
        }

        assertTrue(pass.isClosed())
        assertEquals(policy.maxConvergencePassMs, pass.absoluteDeadlineMs)
        // Ticks land at 500..10000; admission stops at the 5000ms deadline, and
        // the tick AT the deadline is already too late (the pass closes when
        // now reaches the cutoff, not after it). So 500..4500 => 9.
        assertEquals(9, admitted, "admission stops at the absolute deadline")
    }

    @Test
    fun theCutoffIsTheEarlierOfTheTwoDeadlines() {
        val clock = FakeClock()
        val pass = pass(clock)
        assertEquals(policy.settlementQuiescenceMs, pass.cutoffMs, "quiet start: quiescence is earlier")

        // Keep the pass alive with steady selection-relevant input until
        // quiescence would run past the absolute deadline; the cutoff clamps.
        repeat(6) {
            clock.advance(800)
            assertTrue(pass.admit("commit", InputRelevance.SELECTION_RELEVANT))
        }
        assertEquals(4_800, clock.nowMs)
        assertEquals(5_800, pass.quiescenceDeadlineMs)
        assertEquals(policy.maxConvergencePassMs, pass.cutoffMs, "clamped to the absolute deadline")
        assertFalse(pass.isClosed())

        clock.advance(200)
        assertTrue(pass.isClosed(), "the absolute deadline closes it even though quiescence has not elapsed")
    }

    @Test
    fun inputAfterTheCutoffBelongsToALaterPass() {
        val clock = FakeClock()
        val pass = pass(clock)
        pass.admit("first", InputRelevance.SELECTION_RELEVANT)

        clock.advance(policy.settlementQuiescenceMs)
        assertFalse(pass.admit("late", InputRelevance.SELECTION_RELEVANT))
        assertEquals(listOf<Any>("first"), pass.admitted, "the late input is not in this batch")
    }

    @Test
    fun freezingIsIdempotentAndFinal() {
        val clock = FakeClock()
        val pass = pass(clock)
        pass.admit("a", InputRelevance.SELECTION_RELEVANT)

        val frozen = pass.freeze()
        assertEquals(listOf<Any>("a"), frozen)
        assertTrue(pass.isFrozen)
        assertFalse(pass.admit("b", InputRelevance.SELECTION_RELEVANT), "a frozen batch admits nothing")
        assertEquals(frozen, pass.freeze())
    }

    @Test
    fun aForkTurnsThePassIntoRecoveryWithoutRestartingIt() {
        // The same pass becomes a recovery. Restarting the timers here would
        // let a trickle of forks hold it open indefinitely.
        val clock = FakeClock()
        val pass = pass(clock)
        val originalDeadline = pass.absoluteDeadlineMs

        clock.advance(600)
        pass.markForkDetected()

        assertTrue(pass.isRecovery)
        assertEquals(originalDeadline, pass.absoluteDeadlineMs)
        assertEquals(8, pass.passBaseEpoch, "the base epoch is not resnapshotted")
        assertEquals(
            GroupLifecycleState.RECOVERING,
            pass.lifecycleWhileRunning(GroupLifecycleState.STABLE),
        )
    }

    @Test
    fun aDisbandCandidateForcesRecoveryEvenWithNoFork() {
        // Terminalization may only happen after branch selection, so admitting
        // a valid disband opens a mandatory bounded pass even on a linear edge.
        val clock = FakeClock()
        val pass = pass(clock)

        assertEquals(GroupLifecycleState.STABLE, pass.lifecycleWhileRunning(GroupLifecycleState.STABLE))
        pass.markDisbandCandidateAdmitted()

        assertTrue(pass.isRecovery)
        assertEquals(
            GroupLifecycleState.RECOVERING,
            pass.lifecycleWhileRunning(GroupLifecycleState.STABLE),
        )
    }

    @Test
    fun aLinearPassStaysStable() {
        val clock = FakeClock()
        val pass = pass(clock)
        pass.admit("linear commit", InputRelevance.SELECTION_RELEVANT)
        assertFalse(pass.isRecovery)
        assertEquals(GroupLifecycleState.STABLE, pass.lifecycleWhileRunning(GroupLifecycleState.STABLE))
    }

    @Test
    fun deferredCommitExpiryUsesTheLiveTipNotThePassBase() {
        // Expiry deliberately tracks the live canonical tip so obsolete input
        // ages out as state advances; branch ELIGIBILITY uses the frozen
        // pass_base_epoch instead so an open pass cannot move its own horizon.
        assertFalse(ConvergencePass.isDeferredCommitStale(canonicalTipEpoch = 10, commitSourceEpoch = 5, policy = policy))
        assertTrue(ConvergencePass.isDeferredCommitStale(canonicalTipEpoch = 11, commitSourceEpoch = 5, policy = policy))
        // A commit from ahead of the tip is never stale — it is waiting for its parent.
        assertFalse(ConvergencePass.isDeferredCommitStale(canonicalTipEpoch = 10, commitSourceEpoch = 20, policy = policy))
    }

    @Test
    fun timersDoNotAffectWhatIsSelected() {
        // Splitting the same inputs across two passes must reach the same
        // answer as resolving them in one: the timers decide when work happens,
        // never what it decides.
        val a = CandidateBranch(8, 11, 3, TipPriority.ORDINARY, ByteArray(32) { 1 }, ByteArray(32) { 2 }, emptyMap())
        val b = CandidateBranch(8, 12, 4, TipPriority.ORDINARY, ByteArray(32) { 3 }, ByteArray(32) { 4 }, emptyMap())

        val together = BranchSelector.select(listOf(a, b), passBaseEpoch = 8, policy = policy)
        val split = BranchSelector.select(listOf(b), passBaseEpoch = 8, policy = policy)
        assertEquals(together, split, "the longer branch wins whether or not it shared a pass")
    }
}
