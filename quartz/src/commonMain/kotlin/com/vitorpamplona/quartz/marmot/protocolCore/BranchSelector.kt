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

/**
 * Branch selection (`protocol-core/convergence.md`, "Branch selection").
 *
 * Two clients that start from the same retained anchor, use the same policy,
 * and resolve the same frozen input batch MUST select the same branch —
 * regardless of transport arrival order, local scheduling, or device speed.
 * This object is where that determinism lives, so everything it reads is
 * authenticated and nothing it reads is transport metadata.
 *
 * This REPLACES the superseded MIP-03 rule, which broke a same-epoch tie on the
 * outer Nostr `created_at` and then the Nostr event id. Both are transport
 * evidence: timestamps are chosen by senders, and each transport copy of one
 * MLS message carries a different event id.
 */
object BranchSelector {
    /**
     * Compare two eligible branches, most-preferred first.
     *
     * The order is exactly:
     *
     * 1. higher `effective_commit_depth`
     * 2. witness quorum beats no quorum
     * 3. higher `app_witness_score`
     * 4. lower `tip_priority` (privileged before ordinary)
     * 5. lower `tip_committer`
     * 6. lower `tip_digest`
     *
     * `raw_commit_depth` has NO step of its own: it is already inside
     * `effective_commit_depth`, so once effective depth and quorum status are
     * both tied a further raw-depth comparison is necessarily tied too. (A
     * widely circulated write-up of this algorithm lists raw depth as step 2 —
     * it is not in the spec, and adding it would change nothing except to make
     * two implementations disagree about where the comparison ended.)
     */
    fun comparator(policy: ConvergencePolicy): Comparator<CandidateBranch> =
        Comparator { a, b ->
            var result = b.effectiveCommitDepth(policy).compareTo(a.effectiveCommitDepth(policy))
            if (result != 0) return@Comparator result

            result = quorumRank(b, policy).compareTo(quorumRank(a, policy))
            if (result != 0) return@Comparator result

            result = b.appWitnessScore(policy).compareTo(a.appWitnessScore(policy))
            if (result != 0) return@Comparator result

            result = a.tipPriority.order.compareTo(b.tipPriority.order)
            if (result != 0) return@Comparator result

            result = compareUnsigned(a.tipCommitter, b.tipCommitter)
            if (result != 0) return@Comparator result

            compareUnsigned(a.tipDigest, b.tipDigest)
        }

    /**
     * The canonical branch among [branches], or null when none is eligible.
     *
     * [passBaseEpoch] is the pass's frozen base; branches whose fork lies
     * outside the rollback horizon MUST NOT be selected.
     */
    fun select(
        branches: Collection<CandidateBranch>,
        passBaseEpoch: Long,
        policy: ConvergencePolicy = ConvergencePolicy.V1,
    ): CandidateBranch? =
        branches
            .filter { it.isEligible(passBaseEpoch, policy) }
            .minWithOrNull(comparator(policy))

    /** [branches] ordered most-preferred first, eligible ones only. */
    fun rank(
        branches: Collection<CandidateBranch>,
        passBaseEpoch: Long,
        policy: ConvergencePolicy = ConvergencePolicy.V1,
    ): List<CandidateBranch> =
        branches
            .filter { it.isEligible(passBaseEpoch, policy) }
            .sortedWith(comparator(policy))

    private fun quorumRank(
        branch: CandidateBranch,
        policy: ConvergencePolicy,
    ): Int = if (branch.witnessQuorumMet(policy)) 1 else 0

    /**
     * Lexicographic order over raw bytes, compared UNSIGNED.
     *
     * Both a 32-byte x-only account key and a SHA-256 digest are uniformly
     * distributed, so about half of all comparisons involve a byte above 0x7f.
     * A signed comparison would invert those and two implementations would
     * disagree about the winner roughly half the time a tie reached this far.
     */
    private fun compareUnsigned(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        val common = minOf(a.size, b.size)
        for (i in 0 until common) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
