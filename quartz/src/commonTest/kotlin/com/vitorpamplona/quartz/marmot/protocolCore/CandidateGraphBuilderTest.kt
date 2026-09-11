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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The candidate-graph SHAPES — which commit hangs off which state, where a
 * branch diverged, how deep it is, and what each unplaced commit is called.
 *
 * Driven by a fake engine on purpose. The graph algorithm is independent of
 * MLS, and building real groups with real key schedules for every shape would
 * make these slow, hard to read, and worse at expressing the case under test.
 * `MlsCandidateGraphTest` covers the real engine on a real fork.
 */
class CandidateGraphBuilderTest {
    /**
     * A toy state: an id, an epoch, and the set of commit ids it authenticates.
     *
     * Authentication is explicit rather than derived so a test can express
     * "these two states share an epoch number but only one is the parent",
     * which is the distinction the spec insists on.
     */
    private class FakeState(
        val id: String,
        val epoch: Long,
        val accepts: Set<String> = emptySet(),
    )

    private class FakeEngine(
        /** commitId -> resulting state, for commits that replay successfully. */
        private val results: Map<String, FakeState>,
        private val unauthorized: Set<String> = emptySet(),
        private val invalidResult: Set<String> = emptySet(),
        private val replayFails: Set<String> = emptySet(),
        private val privileged: Set<String> = emptySet(),
        private val committers: Map<String, ByteArray> = emptyMap(),
    ) : CandidateStateEngine<FakeState> {
        override fun stateId(state: FakeState) = state.id

        override fun epoch(state: FakeState) = state.epoch

        override fun authenticatesAgainst(
            state: FakeState,
            commit: ByteArray,
        ) = commit.decodeToString() in state.accepts

        override fun replay(
            state: FakeState,
            commit: ByteArray,
        ): FakeState? {
            val id = commit.decodeToString()
            if (id in replayFails) return null
            return results[id]
        }

        override fun committerIdentity(
            parent: FakeState,
            commit: ByteArray,
        ): ByteArray = committers[commit.decodeToString()] ?: ByteArray(32)

        override fun isAuthorized(
            parent: FakeState,
            commit: ByteArray,
        ) = commit.decodeToString() !in unauthorized

        override fun tipPriority(
            parent: FakeState,
            commit: ByteArray,
        ) = if (commit.decodeToString() in privileged) TipPriority.PRIVILEGED else TipPriority.ORDINARY

        override fun resultingStateIsValid(resulting: FakeState) = resulting.id !in invalidResult

        override fun commitDigest(commit: ByteArray) =
            ByteArray(32).also { out ->
                commit.decodeToString().encodeToByteArray().copyInto(out, 0, 0, minOf(32, commit.size))
            }
    }

    private fun commit(
        id: String,
        sourceEpoch: Long,
    ) = CandidateCommit(id, id.encodeToByteArray(), sourceEpoch)

    @Test
    fun aSameEpochForkProducesTwoOneCommitBranches() {
        // The canonical race: Alice and Bob both commit from epoch 8.
        val base = FakeState("base", 8, accepts = setOf("alice", "bob"))
        val aliceTip = FakeState("alice-9", 9)
        val bobTip = FakeState("bob-9", 9)

        val graph =
            CandidateGraphBuilder(FakeEngine(mapOf("alice" to aliceTip, "bob" to bobTip))).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("alice", 8), commit("bob", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        assertEquals(2, graph.branches.size)
        assertTrue(graph.branches.all { it.forkEpoch == 8L && it.tipEpoch == 9L && it.rawCommitDepth == 1L })
        assertTrue(graph.outcomes.all { it.disposition == ConvergenceDisposition.ACCEPTED })
    }

    @Test
    fun sharingAnEpochNumberDoesNotMakeAStateACandidateParent() {
        // Two retained states at epoch 8; only one authenticates the commit.
        // A builder keyed on the epoch NUMBER would pick either.
        val real = FakeState("real-8", 8, accepts = setOf("c1"))
        val decoy = FakeState("decoy-8", 8)
        val tip = FakeState("tip-9", 9)

        val graph =
            CandidateGraphBuilder(FakeEngine(mapOf("c1" to tip))).build(
                retainedStates = listOf(decoy, real),
                canonicalStateId = "real-8",
                canonicalAncestry = listOf("real-8"),
                commits = listOf(commit("c1", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        assertEquals(1, graph.branches.size)
        assertEquals(8, graph.branches.single().forkEpoch)
    }

    @Test
    fun aChainIsWalkedToItsForkPointAndCountsItsDepth() {
        val base = FakeState("base", 8, accepts = setOf("c1"))
        val s9 = FakeState("s9", 9, accepts = setOf("c2"))
        val s10 = FakeState("s10", 10, accepts = setOf("c3"))
        val s11 = FakeState("s11", 11)

        val graph =
            CandidateGraphBuilder(
                FakeEngine(mapOf("c1" to s9, "c2" to s10, "c3" to s11)),
            ).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("c1", 8), commit("c2", 9), commit("c3", 10)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        val branch = graph.branches.single()
        assertEquals(8, branch.forkEpoch)
        assertEquals(11, branch.tipEpoch)
        assertEquals(3, branch.rawCommitDepth)
    }

    @Test
    fun aCommitWhoseParentArrivesLaterIsStillPlaced() {
        // The fixed point matters: c2 hangs off c1's RESULT, which does not
        // exist until c1 has been replayed. Offer them in the wrong order.
        val base = FakeState("base", 8, accepts = setOf("c1"))
        val s9 = FakeState("s9", 9, accepts = setOf("c2"))
        val s10 = FakeState("s10", 10)

        val graph =
            CandidateGraphBuilder(FakeEngine(mapOf("c1" to s9, "c2" to s10))).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("c2", 9), commit("c1", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        assertEquals(2, graph.rawCommitDepthOfSingleBranch())
        assertTrue(graph.outcomes.all { it.disposition == ConvergenceDisposition.ACCEPTED })
    }

    private fun <S> CandidateGraph<S>.rawCommitDepthOfSingleBranch(): Long = branches.single().rawCommitDepth

    // --- dispositions ---------------------------------------------------------

    @Test
    fun anOrphanCommitIsDeferredNotFailed() {
        // Absence of a parent is never by itself an authorization failure, and
        // it is not terminal — the parent may still arrive.
        val base = FakeState("base", 8)
        val graph =
            CandidateGraphBuilder(FakeEngine(emptyMap())).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("orphan", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        val outcome = graph.outcomes.single()
        assertEquals(ConvergenceDisposition.DEFERRED, outcome.disposition)
        assertEquals(ConvergenceCategory.MISSING_HISTORY, outcome.category)
        assertTrue(graph.branches.isEmpty())
    }

    @Test
    fun anOrphanGoesStaleOnlyOnceTheLiveTipPassesTheHorizon() {
        val base = FakeState("base", 20)

        fun outcomeAt(tip: Long) =
            CandidateGraphBuilder(FakeEngine(emptyMap()))
                .build(
                    retainedStates = listOf(base),
                    canonicalStateId = "base",
                    canonicalAncestry = listOf("base"),
                    commits = listOf(commit("orphan", 10)),
                    canonicalTipEpoch = tip,
                    passBaseEpoch = tip,
                ).outcomes
                .single()

        assertEquals(ConvergenceDisposition.DEFERRED, outcomeAt(15).disposition)
        assertEquals(ConvergenceDisposition.STALE, outcomeAt(16).disposition)
        assertEquals(ConvergenceCategory.STALE_EPOCH, outcomeAt(16).category)
    }

    @Test
    fun anUnauthorizedCommitIsTerminalOnceItsParentIsKnown() {
        // Different from an orphan: the parent IS identified here, so the
        // authorization verdict is final rather than provisional.
        val base = FakeState("base", 8, accepts = setOf("hijack"))
        val graph =
            CandidateGraphBuilder(
                FakeEngine(mapOf("hijack" to FakeState("s9", 9)), unauthorized = setOf("hijack")),
            ).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("hijack", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        val outcome = graph.outcomes.single()
        assertEquals(ConvergenceDisposition.STALE, outcome.disposition)
        assertEquals(ConvergenceCategory.AUTHORIZATION_FAILED, outcome.category)
        assertTrue(graph.branches.isEmpty(), "an unauthorized commit creates no edge")
    }

    @Test
    fun anEdgeIsNotCreatedWhenTheResultingStateBreaksAnInvariant() {
        // Convergence must never be ABLE to select an invalid transition, so
        // the edge is refused rather than created-and-scored-low.
        val base = FakeState("base", 8, accepts = setOf("bad"))
        val graph =
            CandidateGraphBuilder(
                FakeEngine(mapOf("bad" to FakeState("broken-9", 9)), invalidResult = setOf("broken-9")),
            ).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("bad", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        assertTrue(graph.branches.isEmpty())
        assertEquals(ConvergenceDisposition.STALE, graph.outcomes.single().disposition)
        assertNull(graph.outcomes.single().resultingStateId)
    }

    @Test
    fun anMlsReplayFailureCreatesNoEdge() {
        val base = FakeState("base", 8, accepts = setOf("corrupt"))
        val graph =
            CandidateGraphBuilder(FakeEngine(emptyMap(), replayFails = setOf("corrupt"))).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("corrupt", 8)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )
        assertTrue(graph.branches.isEmpty())
    }

    // --- eligibility and witnesses -------------------------------------------

    @Test
    fun aBranchForkedOutsideTheHorizonIsNotOffered() {
        val old = FakeState("old-2", 2, accepts = setOf("c1"))
        val graph =
            CandidateGraphBuilder(FakeEngine(mapOf("c1" to FakeState("s3", 3)))).build(
                retainedStates = listOf(old),
                canonicalStateId = "old-2",
                canonicalAncestry = listOf("old-2"),
                commits = listOf(commit("c1", 2)),
                canonicalTipEpoch = 10,
                passBaseEpoch = 10,
            )

        assertTrue(graph.branches.isEmpty(), "fork at epoch 2 is 8 behind a base of 10")
        assertEquals(ConvergenceDisposition.ACCEPTED, graph.outcomes.single().disposition)
    }

    @Test
    fun witnessesAreAttachedToTheBranchEpochTheyDecryptedOn() {
        val base = FakeState("base", 8, accepts = setOf("c1"))
        val s9 = FakeState("s9", 9, accepts = setOf("c2"))
        val s10 = FakeState("s10", 10)

        val graph =
            CandidateGraphBuilder(FakeEngine(mapOf("c1" to s9, "c2" to s10))).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("c1", 8), commit("c2", 9)),
                witnesses =
                    listOf(
                        WitnessObservation("s9", "a".repeat(64)),
                        WitnessObservation("s9", "b".repeat(64)),
                        // Attributed to the base state, which is at the fork
                        // point and therefore not a branch epoch.
                        WitnessObservation("base", "c".repeat(64)),
                    ),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        val branch = graph.branches.single()
        assertEquals(setOf("a".repeat(64), "b".repeat(64)), branch.witnessesByEpoch[9L])
        assertNull(branch.witnessesByEpoch[8L], "the fork epoch is not a branch epoch")
        assertTrue(branch.witnessQuorumMet(ConvergencePolicy.V1))
    }

    @Test
    fun theBuilderFeedsTheSelectorEndToEnd() {
        // The whole point: a graph in, one canonical branch out.
        val base = FakeState("base", 8, accepts = setOf("short", "long1"))
        val shortTip = FakeState("short-9", 9)
        val long9 = FakeState("long-9", 9, accepts = setOf("long2"))
        val long10 = FakeState("long-10", 10)

        val graph =
            CandidateGraphBuilder(
                FakeEngine(mapOf("short" to shortTip, "long1" to long9, "long2" to long10)),
            ).build(
                retainedStates = listOf(base),
                canonicalStateId = "base",
                canonicalAncestry = listOf("base"),
                commits = listOf(commit("short", 8), commit("long1", 8), commit("long2", 9)),
                canonicalTipEpoch = 8,
                passBaseEpoch = 8,
            )

        assertEquals(2, graph.branches.size)
        val winner = assertNotNull(BranchSelector.select(graph.branches, passBaseEpoch = 8))
        assertEquals(2, winner.rawCommitDepth, "the deeper branch wins")
        assertEquals(10, winner.tipEpoch)
    }
}
