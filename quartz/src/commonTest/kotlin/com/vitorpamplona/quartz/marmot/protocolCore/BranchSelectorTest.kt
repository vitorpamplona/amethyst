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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Branch selection under convergence policy v1.
 *
 * These are the rules that decide which history a group keeps, so a
 * disagreement here is a group split rather than a wrong answer. Everything is
 * pure — no timing, no transport — which is exactly why it can be pinned this
 * precisely.
 */
class BranchSelectorTest {
    private val policy = ConvergencePolicy.V1

    private fun key(b: Int) = ByteArray(32) { b.toByte() }

    private fun branch(
        forkEpoch: Long = 8,
        depth: Long,
        priority: TipPriority = TipPriority.ORDINARY,
        committer: Int = 0x10,
        digest: Int = 0x20,
        witnesses: Map<Long, Set<String>> = emptyMap(),
    ) = CandidateBranch(
        forkEpoch = forkEpoch,
        tipEpoch = forkEpoch + depth,
        rawCommitDepth = depth,
        tipPriority = priority,
        tipCommitter = key(committer),
        tipDigest = key(digest),
        witnessesByEpoch = witnesses,
    )

    /** Two distinct senders on one branch epoch — the quorum shape. */
    private fun quorumAt(epoch: Long) = mapOf(epoch to setOf("a".repeat(64), "b".repeat(64)))

    @Test
    fun policyV1MatchesTheAdoptedConstants() {
        assertEquals(5, policy.maxRewindCommits)
        assertEquals(5, policy.appPayloadPastEpochLimit)
        assertEquals(1_000, policy.settlementQuiescenceMs)
        assertEquals(5_000, policy.maxConvergencePassMs)
        assertEquals(2, policy.witnessQuorumSendersPerEpoch)
        assertEquals(1, policy.witnessQuorumEpochs)
        assertEquals(1, policy.maxWitnessOverrideDepth)
    }

    @Test
    fun theWitnessBoostCannotExceedTheRewindHorizon() {
        // Without this bound, app-payload traffic could push a branch past the
        // rollback horizon and beat an arbitrarily longer valid commit branch.
        assertFailsWith<IllegalArgumentException> {
            policy.copy(maxWitnessOverrideDepth = policy.maxRewindCommits + 1)
        }
    }

    // --- the worked example ---------------------------------------------------

    @Test
    fun aWitnessedThreeCommitBranchBeatsAnUnwitnessedFour() {
        // Effective depth ties at 4 (3 + the 1-commit boost), so selection
        // falls to step 2, where quorum beats no quorum.
        val witnessed = branch(depth = 3, witnesses = quorumAt(10), digest = 0xff)
        val plain = branch(depth = 4, digest = 0x01)

        assertEquals(4, witnessed.effectiveCommitDepth(policy))
        assertEquals(4, plain.effectiveCommitDepth(policy))
        assertTrue(witnessed.witnessQuorumMet(policy))
        assertTrue(!plain.witnessQuorumMet(policy))

        assertSame(witnessed, BranchSelector.select(listOf(plain, witnessed), passBaseEpoch = 8, policy = policy))
    }

    @Test
    fun aFiveCommitBranchStillBeatsBoth() {
        // The boost is capped at one commit, so raw depth wins outright here.
        val witnessed = branch(depth = 3, witnesses = quorumAt(10))
        val plain = branch(depth = 4)
        val longest = branch(depth = 5, digest = 0xfe)

        assertSame(
            longest,
            BranchSelector.select(listOf(witnessed, plain, longest), passBaseEpoch = 8, policy = policy),
        )
    }

    // --- the comparison chain -------------------------------------------------

    @Test
    fun rawDepthIsNotASeparateComparisonStep() {
        // Both reach effective depth 4 and both have quorum, so step 3 is the
        // witness SCORE — not raw depth. The shorter branch has the higher
        // score here, and it must win: an implementation that inserted a
        // raw-depth step would pick the other one.
        val shortHighScore =
            branch(depth = 3, witnesses = mapOf(10L to setOf("a".repeat(64), "b".repeat(64)), 11L to setOf("c".repeat(64), "d".repeat(64))))
        val longLowScore = branch(depth = 3, witnesses = quorumAt(9), digest = 0x01)

        assertEquals(shortHighScore.effectiveCommitDepth(policy), longLowScore.effectiveCommitDepth(policy))
        assertTrue(shortHighScore.appWitnessScore(policy) > longLowScore.appWitnessScore(policy))
        assertSame(
            shortHighScore,
            BranchSelector.select(listOf(longLowScore, shortHighScore), passBaseEpoch = 8, policy = policy),
        )
    }

    @Test
    fun privilegedTipsBeatOrdinaryOnesOnceEverythingElseTies() {
        // Stops commit-byte choice alone from letting an ordinary self-update
        // beat a tied admin removal.
        val admin = branch(depth = 1, priority = TipPriority.PRIVILEGED, committer = 0xff, digest = 0xff)
        val ordinary = branch(depth = 1, priority = TipPriority.ORDINARY, committer = 0x01, digest = 0x01)

        assertSame(admin, BranchSelector.select(listOf(ordinary, admin), passBaseEpoch = 8, policy = policy))
    }

    @Test
    fun committerThenDigestBreakTheFinalTie() {
        val lowCommitter = branch(depth = 1, committer = 0x01, digest = 0xff)
        val highCommitter = branch(depth = 1, committer = 0x02, digest = 0x00)
        assertSame(
            lowCommitter,
            BranchSelector.select(listOf(highCommitter, lowCommitter), passBaseEpoch = 8, policy = policy),
        )

        // Digest decides only when the SAME committer produced both.
        val lowDigest = branch(depth = 1, committer = 0x01, digest = 0x01)
        val highDigest = branch(depth = 1, committer = 0x01, digest = 0x02)
        assertSame(
            lowDigest,
            BranchSelector.select(listOf(highDigest, lowDigest), passBaseEpoch = 8, policy = policy),
        )
    }

    @Test
    fun byteOrderingIsUnsigned() {
        // 0x01 must sort before 0x80. Account keys and digests are uniformly
        // distributed, so a signed comparison would invert roughly half of all
        // final ties — and two clients would disagree that often.
        val low = branch(depth = 1, committer = 0x01, digest = 0x01)
        val high = branch(depth = 1, committer = 0x80, digest = 0x01)
        assertSame(low, BranchSelector.select(listOf(high, low), passBaseEpoch = 8, policy = policy))

        val lowDigest = branch(depth = 1, committer = 0x01, digest = 0x01)
        val highDigest = branch(depth = 1, committer = 0x01, digest = 0x80)
        assertSame(
            lowDigest,
            BranchSelector.select(listOf(highDigest, lowDigest), passBaseEpoch = 8, policy = policy),
        )
    }

    // --- witness scoring ------------------------------------------------------

    @Test
    fun oneSenderCannotInflateABranchByShouting() {
        // Witnesses are counted by DISTINCT sender per epoch, so a hundred
        // messages from one account score exactly one.
        val loner = branch(depth = 1, witnesses = mapOf(9L to setOf("a".repeat(64))))
        assertEquals(1, loner.appWitnessScore(policy))
        assertTrue(!loner.witnessQuorumMet(policy), "one sender is not a quorum")
    }

    @Test
    fun perEpochScoreIsCappedAtTheQuorumSize() {
        // Five senders in one epoch score 2, not 5 — so one very busy epoch
        // cannot outweigh several quiet ones.
        val busy = branch(depth = 1, witnesses = mapOf(9L to (1..5).map { "$it".repeat(64) }.toSet()))
        assertEquals(2, busy.appWitnessScore(policy))
    }

    @Test
    fun witnessesAtOrBeforeTheForkEpochDoNotCount() {
        // Branch epochs are strictly greater than fork_epoch: traffic from
        // before the divergence is shared history, not evidence for a branch.
        val branch = branch(forkEpoch = 8, depth = 2, witnesses = mapOf(8L to setOf("a".repeat(64), "b".repeat(64))))
        assertEquals(0, branch.appWitnessScore(policy))
        assertTrue(!branch.witnessQuorumMet(policy))
    }

    @Test
    fun quorumEpochsAreEvaluatedIndependently() {
        // The qualifying sender set may differ per epoch; no cohort has to
        // span them all.
        val branch =
            branch(
                depth = 2,
                witnesses =
                    mapOf(
                        9L to setOf("a".repeat(64), "b".repeat(64)),
                        10L to setOf("c".repeat(64), "d".repeat(64)),
                    ),
            )
        assertTrue(branch.witnessQuorumMet(policy))
        assertEquals(4, branch.appWitnessScore(policy))
    }

    // --- eligibility ----------------------------------------------------------

    @Test
    fun branchesForkedOutsideTheRewindHorizonAreNeverSelected() {
        val inHorizon = branch(forkEpoch = 5, depth = 1)
        val outside = branch(forkEpoch = 4, depth = 50, digest = 0x01)

        assertTrue(inHorizon.isEligible(passBaseEpoch = 10, policy = policy))
        assertTrue(!outside.isEligible(passBaseEpoch = 10, policy = policy))

        // Even though it is far longer, the out-of-horizon branch is not a
        // candidate at all.
        assertSame(
            inHorizon,
            BranchSelector.select(listOf(outside, inHorizon), passBaseEpoch = 10, policy = policy),
        )
        assertNull(BranchSelector.select(listOf(outside), passBaseEpoch = 10, policy = policy))
    }

    @Test
    fun selectionIsIndependentOfInputOrder() {
        // The whole point of the algorithm: two clients that received the same
        // candidates in different orders must choose the same branch.
        val branches =
            listOf(
                branch(depth = 2, committer = 0x30, digest = 0x40),
                branch(depth = 3, witnesses = quorumAt(10), committer = 0x10, digest = 0x90),
                branch(depth = 4, committer = 0x20, digest = 0x10),
                branch(depth = 1, priority = TipPriority.PRIVILEGED, committer = 0x05, digest = 0x05),
            )
        val expected = BranchSelector.select(branches, passBaseEpoch = 8, policy = policy)

        assertEquals(expected, BranchSelector.select(branches.reversed(), passBaseEpoch = 8, policy = policy))
        assertEquals(expected, BranchSelector.select(branches.shuffled(), passBaseEpoch = 8, policy = policy))
        for (rotation in branches.indices) {
            val rotated = branches.drop(rotation) + branches.take(rotation)
            assertEquals(expected, BranchSelector.select(rotated, passBaseEpoch = 8, policy = policy))
        }
    }

    @Test
    fun rankIsATotalOrderOverEligibleBranches() {
        val branches =
            listOf(
                branch(depth = 1, committer = 0x03),
                branch(depth = 1, committer = 0x01),
                branch(depth = 1, committer = 0x02),
                branch(forkEpoch = 0, depth = 9),
            )
        val ranked = BranchSelector.rank(branches, passBaseEpoch = 8, policy = policy)
        assertEquals(3, ranked.size, "the out-of-horizon branch is filtered out")
        assertEquals(listOf(0x01, 0x02, 0x03), ranked.map { it.tipCommitter[0].toInt() })
    }

    @Test
    fun noEligibleBranchesSelectsNothing() {
        assertNull(BranchSelector.select(emptyList(), passBaseEpoch = 8, policy = policy))
    }
}
