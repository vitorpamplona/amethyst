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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * The authorization class of a branch's tip Commit
 * (`protocol-core/convergence.md`, "Candidate branches").
 *
 * A Commit is [PRIVILEGED] exactly when its applicable Marmot authorization
 * rule REQUIRES an active admin in the candidate parent state, and [ORDINARY]
 * when the rule permits a non-admin committer. This follows the authorization
 * rule, not the operation's apparent importance: a component change whose
 * owning document explicitly permits a non-admin committer is `ordinary`.
 */
enum class TipPriority {
    PRIVILEGED,
    ORDINARY,
    ;

    /** Lower sorts first, and privileged wins a tie. */
    val order: Int get() = if (this == PRIVILEGED) 0 else 1
}

/**
 * One candidate branch in a convergence pass.
 *
 * Every value here MUST come from MLS-valid bytes, retained state, decrypted
 * app payloads, or the pinned policy. Transport arrival order, transport
 * timestamps, outer event ids and local receive order MUST NOT appear — which
 * is exactly what the superseded MIP-03 rule got wrong by ranking on
 * `created_at` and the Nostr event id.
 */
data class CandidateBranch(
    /** Epoch where this branch diverged from retained canonical state. */
    val forkEpoch: Long,
    /** Epoch reached after replaying this branch's valid commits. */
    val tipEpoch: Long,
    /** Number of valid commits from [forkEpoch] to [tipEpoch]. */
    val rawCommitDepth: Long,
    val tipPriority: TipPriority,
    /** Authenticated Marmot account identity of the tip committer (32 raw bytes). */
    val tipCommitter: ByteArray,
    /** `SHA-256` of the tip Commit's serialized MLS message bytes (32 bytes). */
    val tipDigest: ByteArray,
    /**
     * Distinct valid app-payload sender identities per branch epoch.
     *
     * Keyed by epoch; the value is the set of ACCOUNT identities (hex) that
     * sent a fully validated app payload decrypting on this branch at that
     * epoch. Counting by account rather than by leaf is what stops a
     * multi-device member from counting several times, and counting distinct
     * senders is what stops one member inflating a branch by sending a lot.
     */
    val witnessesByEpoch: Map<Long, Set<HexKey>>,
) {
    init {
        require(tipCommitter.size == 32) { "tip_committer must be a 32-byte account identity" }
        require(tipDigest.size == 32) { "tip_digest must be a 32-byte SHA-256" }
        require(rawCommitDepth >= 0) { "raw_commit_depth must not be negative" }
    }

    /** Epochs strictly greater than [forkEpoch] and at most [tipEpoch]. */
    val branchEpochs: LongRange get() = (forkEpoch + 1)..tipEpoch

    /**
     * `sum over branch epochs of min(distinct senders, quorum size)`.
     *
     * The per-epoch `min` is why one very chatty epoch cannot outweigh several
     * quiet ones.
     */
    fun appWitnessScore(policy: ConvergencePolicy): Long =
        branchEpochs.sumOf { epoch ->
            val senders = witnessesByEpoch[epoch]?.size ?: 0
            minOf(senders, policy.witnessQuorumSendersPerEpoch).toLong()
        }

    /**
     * True when at least [ConvergencePolicy.witnessQuorumEpochs] branch epochs
     * each had at least [ConvergencePolicy.witnessQuorumSendersPerEpoch]
     * distinct senders.
     *
     * Each epoch is evaluated independently: the qualifying sender set MAY
     * differ from epoch to epoch, and no cohort has to span them all.
     */
    fun witnessQuorumMet(policy: ConvergencePolicy): Boolean =
        branchEpochs.count { epoch ->
            (witnessesByEpoch[epoch]?.size ?: 0) >= policy.witnessQuorumSendersPerEpoch
        } >= policy.witnessQuorumEpochs

    /** `raw_commit_depth` plus the bounded witness boost. */
    fun effectiveCommitDepth(policy: ConvergencePolicy): Long = rawCommitDepth + if (witnessQuorumMet(policy)) policy.maxWitnessOverrideDepth else 0L

    /**
     * Eligible only when the fork lies inside the rollback horizon measured
     * from the pass's FROZEN base epoch.
     *
     * The base is frozen for the whole pass so candidate replay cannot move
     * the horizon while candidates are being compared. Deferred-commit expiry
     * uses the LIVE canonical tip instead, so obsolete input still ages out as
     * canonical state advances across passes — the two use different reference
     * points on purpose.
     */
    fun isEligible(
        passBaseEpoch: Long,
        policy: ConvergencePolicy,
    ): Boolean = passBaseEpoch - forkEpoch <= policy.maxRewindCommits

    val tipCommitterHex: HexKey get() = tipCommitter.toHexKey()
    val tipDigestHex: HexKey get() = tipDigest.toHexKey()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CandidateBranch) return false
        return forkEpoch == other.forkEpoch &&
            tipEpoch == other.tipEpoch &&
            rawCommitDepth == other.rawCommitDepth &&
            tipPriority == other.tipPriority &&
            tipCommitter.contentEquals(other.tipCommitter) &&
            tipDigest.contentEquals(other.tipDigest) &&
            witnessesByEpoch == other.witnessesByEpoch
    }

    override fun hashCode(): Int {
        var result = forkEpoch.hashCode()
        result = 31 * result + tipEpoch.hashCode()
        result = 31 * result + rawCommitDepth.hashCode()
        result = 31 * result + tipPriority.hashCode()
        result = 31 * result + tipCommitter.contentHashCode()
        result = 31 * result + tipDigest.contentHashCode()
        result = 31 * result + witnessesByEpoch.hashCode()
        return result
    }
}
