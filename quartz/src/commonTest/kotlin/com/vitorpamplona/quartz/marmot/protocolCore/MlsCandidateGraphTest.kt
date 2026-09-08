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

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CandidateGraphBuilder] driven by [MlsCandidateStateEngine] over REAL MLS
 * groups and a REAL same-epoch fork.
 *
 * `CandidateGraphBuilderTest` proves the graph algebra against a fake engine.
 * This one proves the part a fake cannot: that parentage really is recoverable
 * by replaying MLS bytes, that two commits authored from one epoch really do
 * land on the same parent and produce two distinct states, and that a state id
 * built from the GroupContext separates two states that share an epoch NUMBER.
 */
class MlsCandidateGraphTest {
    private val engine = MlsCandidateStateEngine()
    private val builder = CandidateGraphBuilder(engine)

    /**
     * A 2-member group plus the pieces needed to author competing commits from
     * one epoch.
     *
     * [aliceState] is captured BEFORE any second-epoch commit, so a test can
     * restore as many independent Alices from it as it needs; each one authors
     * a commit that believes it is extending the same parent. That is the
     * situation a fork is, reproduced honestly rather than by hand-editing
     * bytes.
     */
    private class Fixture(
        val baseState: MlsGroupState,
        val aliceState: MlsGroupState,
        val charliePkg: ByteArray,
        val davePkg: ByteArray,
    )

    /**
     * A Marmot leaf credential carries the member's 32-byte account pubkey, and
     * convergence's committer tie-break is defined over exactly that. Fixtures
     * use full-width identities so the branches they produce are scorable.
     */
    private fun account(seed: Byte) = ByteArray(32) { seed }

    private fun fixture(): Fixture {
        val alice = MlsGroup.create(identity = account(0x0a))
        val bobBundle = alice.createKeyPackage(identity = account(0x0b), signingKey = ByteArray(32) { 1 })
        val addBob = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addBob.welcomeBytes!!, bobBundle)

        val charlie = alice.createKeyPackage(identity = account(0x0c), signingKey = ByteArray(32) { 2 })
        val dave = alice.createKeyPackage(identity = account(0x0d), signingKey = ByteArray(32) { 3 })

        return Fixture(
            baseState = bob.saveState(),
            aliceState = alice.saveState(),
            charliePkg = charlie.keyPackage.toTlsBytes(),
            davePkg = dave.keyPackage.toTlsBytes(),
        )
    }

    private fun candidate(
        bytes: ByteArray,
        sourceEpoch: Long,
    ) = CandidateCommit(id = sha256(bytes).toHexKey(), bytes = bytes, sourceEpoch = sourceEpoch)

    /** Commit authored by a fresh clone of [state], so [state] stays unspent. */
    private fun addFrom(
        state: MlsGroupState,
        keyPackage: ByteArray,
    ): Pair<ByteArray, MlsGroup> {
        val author = MlsGroup.restore(state)
        val commit = author.addMember(keyPackage)
        return commit.framedCommitBytes to author
    }

    private fun buildOver(
        base: MlsGroupState,
        commits: List<CandidateCommit>,
        witnesses: List<WitnessObservation> = emptyList(),
        canonicalTipEpoch: Long = 1L,
        passBaseEpoch: Long = 1L,
    ): CandidateGraph<MlsGroupState> {
        val baseId = engine.stateId(base)
        return builder.build(
            retainedStates = listOf(base),
            canonicalStateId = baseId,
            canonicalAncestry = listOf(baseId),
            commits = commits,
            witnesses = witnesses,
            canonicalTipEpoch = canonicalTipEpoch,
            passBaseEpoch = passBaseEpoch,
        )
    }

    /**
     * The core claim: two commits authored from one epoch both authenticate
     * against the same retained parent, and each yields its own branch.
     *
     * If parentage were read from transport metadata this test could not exist
     * — neither commit carries a parent pointer, and both claim epoch 1.
     */
    @Test
    fun realForkYieldsTwoBranchesOffOneParent() {
        val fx = fixture()
        val (commitA, _) = addFrom(fx.aliceState, fx.charliePkg)
        val (commitB, _) = addFrom(fx.aliceState, fx.davePkg)

        val graph =
            buildOver(fx.baseState, listOf(candidate(commitA, 1L), candidate(commitB, 1L)))

        assertEquals(2, graph.branches.size, "one branch per competing commit")
        assertTrue(graph.outcomes.all { it.disposition == ConvergenceDisposition.ACCEPTED })

        graph.branches.forEach { branch ->
            assertEquals(1L, branch.forkEpoch)
            assertEquals(2L, branch.tipEpoch)
            assertEquals(1L, branch.rawCommitDepth)
        }

        // Two states at the same epoch NUMBER, and the graph holds them apart:
        // the id is over the GroupContext, which covers the tree hash.
        val tipIds = graph.outcomes.mapNotNull { it.resultingStateId }
        assertEquals(2, tipIds.toSet().size, "same-epoch states must not collide")
        tipIds.forEach { assertEquals(2L, engine.epoch(graph.statesById.getValue(it))) }
    }

    /**
     * A two-commit chain offered in reverse order still forms one branch of
     * depth 2 — the fixed point places the child once its parent's resulting
     * state exists.
     */
    @Test
    fun chainIsRebuiltEvenWhenCommitsArriveOutOfOrder() {
        val fx = fixture()
        val (commitA, aliceAfterA) = addFrom(fx.aliceState, fx.charliePkg)
        val commitB = aliceAfterA.addMember(fx.davePkg).framedCommitBytes

        val graph =
            buildOver(
                fx.baseState,
                // Child first: nothing can place it on the first sweep.
                listOf(candidate(commitB, 2L), candidate(commitA, 1L)),
            )

        assertTrue(graph.outcomes.all { it.disposition == ConvergenceDisposition.ACCEPTED })
        assertEquals(1, graph.branches.size)
        val branch = graph.branches.single()
        assertEquals(1L, branch.forkEpoch)
        assertEquals(3L, branch.tipEpoch)
        assertEquals(2L, branch.rawCommitDepth, "both commits count toward the branch depth")
    }

    /** A commit from a different group never authenticates here. */
    @Test
    fun commitFromAnotherGroupIsDeferredNotAccepted() {
        val fx = fixture()
        val stranger = fixture()
        val (foreign, _) = addFrom(stranger.aliceState, stranger.charliePkg)

        val graph = buildOver(fx.baseState, listOf(candidate(foreign, 1L)))

        assertTrue(graph.branches.isEmpty())
        val outcome = graph.outcomes.single()
        assertEquals(ConvergenceDisposition.DEFERRED, outcome.disposition)
        assertEquals(ConvergenceCategory.MISSING_HISTORY, outcome.category)
        assertNull(outcome.resultingStateId)
    }

    /**
     * Tampering with a commit costs it its parent: the membership tag no longer
     * verifies, so no retained state claims it. It is `deferred`, NOT
     * `authorization_failed` — a commit we cannot place is not a commit we
     * caught misbehaving.
     */
    @Test
    fun tamperedCommitFindsNoParentAndIsNeverAuthorizationFailed() {
        val fx = fixture()
        val (commitA, _) = addFrom(fx.aliceState, fx.charliePkg)
        val tampered = commitA.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }

        val graph = buildOver(fx.baseState, listOf(candidate(tampered, 1L)))

        assertTrue(graph.branches.isEmpty())
        val outcome = graph.outcomes.single()
        assertEquals(ConvergenceDisposition.DEFERRED, outcome.disposition)
        assertNotEquals(ConvergenceCategory.AUTHORIZATION_FAILED, outcome.category)
    }

    /**
     * A commit whose source epoch has fallen behind the live canonical tip by
     * more than the rollback horizon is stale, not deferred forever.
     */
    @Test
    fun unplaceableCommitGoesStaleOnceTheLiveTipPassesTheHorizon() {
        val fx = fixture()
        val stranger = fixture()
        val (foreign, _) = addFrom(stranger.aliceState, stranger.charliePkg)

        val farAhead = 1L + ConvergencePolicy.V1.maxRewindCommits + 1L
        val graph =
            buildOver(
                fx.baseState,
                listOf(candidate(foreign, 1L)),
                canonicalTipEpoch = farAhead,
            )

        val outcome = graph.outcomes.single()
        assertEquals(ConvergenceDisposition.STALE, outcome.disposition)
        assertEquals(ConvergenceCategory.STALE_EPOCH, outcome.category)
    }

    /**
     * The state id the graph reports for an edge is the same one a caller gets
     * by replaying that commit itself. That is what lets a witness observation
     * — recorded when an app payload decrypts against some state — be matched
     * back to a branch epoch.
     */
    @Test
    fun witnessOnAReplayedStateAttachesToItsBranchEpoch() {
        val fx = fixture()
        val (commitA, _) = addFrom(fx.aliceState, fx.charliePkg)
        val (commitB, _) = addFrom(fx.aliceState, fx.davePkg)

        val replayedA = engine.replay(fx.baseState, commitA)
        assertNotNull(replayedA)
        val stateIdA = engine.stateId(replayedA)

        val witnesses =
            listOf(
                WitnessObservation(stateIdA, "aa".repeat(32)),
                WitnessObservation(stateIdA, "bb".repeat(32)),
            )
        val graph =
            buildOver(
                fx.baseState,
                listOf(candidate(commitA, 1L), candidate(commitB, 1L)),
                witnesses = witnesses,
            )

        val idA = graph.outcomes.single { it.commitId == sha256(commitA).toHexKey() }.resultingStateId
        assertEquals(stateIdA, idA, "replaying the same commit twice must name the same state")

        val witnessed = graph.branches.single { it.witnessesByEpoch.isNotEmpty() }
        assertEquals(setOf(2L), witnessed.witnessesByEpoch.keys)
        assertEquals(2, witnessed.witnessesByEpoch.getValue(2L).size)

        // And the branch that carries the witnesses is the one selection prefers.
        val chosen = BranchSelector.select(graph.branches, passBaseEpoch = 1L)
        assertEquals(witnessed, chosen)
    }

    /**
     * Selection over real branches is deterministic and symmetric: offering the
     * same fork in either order picks the same tip. With everything else tied,
     * the tie-break falls through to the tip commit digest — a property of the
     * bytes, not of arrival order.
     */
    @Test
    fun selectionOverARealForkIsIndependentOfArrivalOrder() {
        val fx = fixture()
        val (commitA, _) = addFrom(fx.aliceState, fx.charliePkg)
        val (commitB, _) = addFrom(fx.aliceState, fx.davePkg)
        val a = candidate(commitA, 1L)
        val b = candidate(commitB, 1L)

        val forward = BranchSelector.select(buildOver(fx.baseState, listOf(a, b)).branches, passBaseEpoch = 1L)
        val reverse = BranchSelector.select(buildOver(fx.baseState, listOf(b, a)).branches, passBaseEpoch = 1L)

        assertNotNull(forward)
        assertNotNull(reverse)
        assertEquals(forward.tipDigestHex, reverse.tipDigestHex)
    }

    /**
     * A group whose leaf credentials are not account pubkeys produces no
     * selectable branch. Its commits still replay — MLS does not care how wide
     * a Basic credential is — but the committer tie-break has nothing to
     * compare, and the builder drops the tip rather than substituting zeros.
     */
    @Test
    fun tipThatCannotBeAttributedToAnAccountIsNotSelectable() {
        val alice = MlsGroup.create(identity = "alice".encodeToByteArray())
        val bobBundle = alice.createKeyPackage(identity = "bob".encodeToByteArray(), signingKey = ByteArray(32) { 1 })
        val addBob = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addBob.welcomeBytes!!, bobBundle)
        val charliePkg = alice.createKeyPackage(identity = "charlie".encodeToByteArray(), signingKey = ByteArray(32) { 2 })

        val base = bob.saveState()
        val (commit, _) = addFrom(alice.saveState(), charliePkg.keyPackage.toTlsBytes())

        val graph = buildOver(base, listOf(candidate(commit, 1L)))

        // The commit itself is fine — it replayed and produced a state.
        assertEquals(ConvergenceDisposition.ACCEPTED, graph.outcomes.single().disposition)
        assertTrue(graph.branches.isEmpty(), "an unattributable tip must not become a branch")
    }

    /** Replaying against a candidate parent must not spend the retained snapshot. */
    @Test
    fun replayLeavesTheRetainedStateReusable() {
        val fx = fixture()
        val (commitA, _) = addFrom(fx.aliceState, fx.charliePkg)
        val baseIdBefore = engine.stateId(fx.baseState)

        repeat(3) {
            val replayed = engine.replay(fx.baseState, commitA)
            assertNotNull(replayed)
            assertEquals(2L, engine.epoch(replayed))
        }

        assertEquals(baseIdBefore, engine.stateId(fx.baseState))
        assertEquals(1L, engine.epoch(fx.baseState))
    }
}
