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
 * The Marmot convergence policy (`protocol-core/convergence.md`).
 *
 * Version 1 is a set of PROTOCOL CONSTANTS, not a preference. It is not carried
 * in group state and cannot be negotiated: every client uses exactly these
 * values, and every branch scored within a pass uses the same ones.
 *
 * That rigidity is the point. Convergence is deliberately not group-tunable
 * because a bad policy choice forks a group — two clients scoring the same
 * candidates under different constants can each be internally consistent and
 * still disagree about which branch is canonical. A future change ships as a
 * NEW app component behind a required capability; clients MUST NOT infer the
 * active policy from a software version, and until such a component exists
 * there is no mechanism to change these at all.
 */
data class ConvergencePolicy(
    /** How far back from the tip a branch MAY fork and still be eligible. */
    val maxRewindCommits: Long,
    /** How many past epochs may still produce delivered payloads or witnesses. */
    val appPayloadPastEpochLimit: Long,
    /** Minimum quiet time before a pass MAY be treated as settled. */
    val settlementQuiescenceMs: Long,
    /** Maximum duration of one input-collection window; never extended by later input. */
    val maxConvergencePassMs: Long,
    /** Distinct senders needed for one branch epoch to count toward quorum. */
    val witnessQuorumSendersPerEpoch: Int,
    /** How many branch epochs must meet sender quorum. */
    val witnessQuorumEpochs: Int,
    /** Maximum commit-depth boost a branch may receive from witness quorum. */
    val maxWitnessOverrideDepth: Long,
) {
    init {
        // The bound exists so app-payload traffic can never push a branch past
        // the rollback horizon: without it, message volume could beat an
        // arbitrarily longer valid commit branch.
        require(maxWitnessOverrideDepth <= maxRewindCommits) {
            "max_witness_override_depth ($maxWitnessOverrideDepth) must not exceed " +
                "max_rewind_commits ($maxRewindCommits)"
        }
    }

    companion object {
        /** Marmot convergence policy, version 1. */
        val V1 =
            ConvergencePolicy(
                maxRewindCommits = 5,
                appPayloadPastEpochLimit = 5,
                settlementQuiescenceMs = 1_000,
                maxConvergencePassMs = 5_000,
                witnessQuorumSendersPerEpoch = 2,
                witnessQuorumEpochs = 1,
                maxWitnessOverrideDepth = 1,
            )
    }
}
