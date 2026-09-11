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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The canonical lifecycle transition table and its couplings. */
class GroupLifecycleStateTest {
    @Test
    fun onlyStableMayPrepareALocalCommit() {
        for (state in GroupLifecycleState.entries) {
            assertEquals(state == GroupLifecycleState.STABLE, state.canPrepareLocalCommit, "$state")
        }
    }

    @Test
    fun mergingNeverDivertsIntoRecovering() {
        // A competing branch observed mid-merge is retained; the merge finishes
        // to Stable and the bounded pass then triggers Stable -> Recovering.
        // Diverting here would leave a half-applied epoch.
        assertFalse(GroupLifecycleState.MERGING.canTransitionTo(GroupLifecycleState.RECOVERING))
        assertTrue(GroupLifecycleState.MERGING.canTransitionTo(GroupLifecycleState.STABLE))
        assertTrue(GroupLifecycleState.STABLE.canTransitionTo(GroupLifecycleState.RECOVERING))
    }

    @Test
    fun disbandedIsAbsorbing() {
        for (target in GroupLifecycleState.entries) {
            assertFalse(
                GroupLifecycleState.DISBANDED.canTransitionTo(target),
                "no later branch may supersede a terminalized disband ($target)",
            )
        }
        assertTrue(GroupLifecycleState.DISBANDED.isTerminal)
    }

    @Test
    fun onlyRecoveringReachesDisbanded() {
        for (state in GroupLifecycleState.entries) {
            assertEquals(
                state == GroupLifecycleState.RECOVERING,
                state.canTransitionTo(GroupLifecycleState.DISBANDED),
                "$state",
            )
        }
    }

    @Test
    fun unrecoverableIsLocalAndRepairable() {
        // Local to one client, and it does have a way out — a repair, a
        // restore, or a verified re-join.
        assertTrue(GroupLifecycleState.UNRECOVERABLE.canTransitionTo(GroupLifecycleState.STABLE))
        assertFalse(GroupLifecycleState.UNRECOVERABLE.isTerminal)
        assertFalse(GroupLifecycleState.UNRECOVERABLE.canTransitionTo(GroupLifecycleState.RECOVERING))
    }

    @Test
    fun inboundChangesCanonicalStateOnlyInStable() {
        for (state in GroupLifecycleState.entries) {
            assertEquals(
                state == GroupLifecycleState.STABLE,
                state.mayApplyInboundToCanonicalState,
                "$state",
            )
        }
    }

    @Test
    fun everyStateReachableFromStableEventuallyReturnsToIt() {
        // Sanity on the table: nothing except Disbanded is a dead end.
        for (state in GroupLifecycleState.entries) {
            if (state == GroupLifecycleState.DISBANDED) continue
            assertTrue(
                reaches(state, GroupLifecycleState.STABLE),
                "$state must be able to reach Stable again",
            )
        }
    }

    private fun reaches(
        from: GroupLifecycleState,
        target: GroupLifecycleState,
        seen: MutableSet<GroupLifecycleState> = mutableSetOf(),
    ): Boolean {
        if (from == target) return true
        if (!seen.add(from)) return false
        return GroupLifecycleState.entries.any { from.canTransitionTo(it) && reaches(it, target, seen) }
    }

    // --- convergence status ---------------------------------------------------

    @Test
    fun onlySettledReleasesOutboundWork() {
        for (status in ConvergenceStatus.entries) {
            assertEquals(status == ConvergenceStatus.SETTLED, status.allowsOutboundWork, "$status")
        }
    }

    @Test
    fun theStatusLifecycleCombinationTableHolds() {
        assertTrue(ConvergenceStatus.SYNCING.isLegalIn(GroupLifecycleState.RECOVERING))
        assertTrue(ConvergenceStatus.SETTLED.isLegalIn(GroupLifecycleState.DISBANDED))
        // A group leaves Recovering for Stable only after Settled — so Settled
        // is never legal *in* Recovering.
        assertFalse(ConvergenceStatus.SETTLED.isLegalIn(GroupLifecycleState.RECOVERING))
        // Blocked on missing state is the Unrecoverable condition.
        assertTrue(ConvergenceStatus.BLOCKED.isLegalIn(GroupLifecycleState.UNRECOVERABLE))
        assertFalse(ConvergenceStatus.SYNCING.isLegalIn(GroupLifecycleState.UNRECOVERABLE))

        // PendingPublish and Merging are local-publish states, not convergence
        // passes, so no status is meaningful in them.
        for (status in ConvergenceStatus.entries) {
            assertFalse(status.isLegalIn(GroupLifecycleState.PENDING_PUBLISH), "$status")
            assertFalse(status.isLegalIn(GroupLifecycleState.MERGING), "$status")
        }
    }
}

private fun assertEquals(
    expected: Boolean,
    actual: Boolean,
    message: String,
) = kotlin.test.assertEquals(expected, actual, message)
