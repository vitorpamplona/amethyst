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
 * Why an admitted input matters to the pass currently collecting
 * (`protocol-core/convergence.md`, "Convergence policy").
 */
enum class InputRelevance {
    /**
     * Newly retained or reclassified input that can still change deterministic
     * resolution of this batch — it adds or invalidates an eligible candidate
     * edge, changes a bounded witness score, supplies a missing parent, or
     * makes deferred input processable.
     *
     * Only this restarts the quiescence window.
     */
    SELECTION_RELEVANT,

    /**
     * Admitted, but cannot change branch selection: ordinary app-payload
     * delivery, and duplicate, invalid, stale, already-dominated or
     * already-fully-counted witness inputs.
     *
     * Deliberately does NOT restart quiescence. If it did, a busy group would
     * never go quiet and outbound work would be held indefinitely behind chat
     * traffic that changes nothing.
     */
    ORDINARY,
}

/**
 * One bounded convergence input-collection window
 * (`protocol-core/convergence.md`).
 *
 * A pass exists so a client resolves a FIXED batch rather than chasing a moving
 * one. It opens when the scheduler admits eligible retained input — receipt or
 * durable retention alone does not start one — snapshots `pass_base_epoch`, and
 * closes at the earlier of quiescence or the absolute deadline. At that cutoff
 * the batch is frozen: resolution then reaches a deterministic fixed point using
 * only what was admitted, without waiting for a fetch or admitting later input.
 *
 * Both intervals are measured with the client's local MONOTONIC clock, never a
 * wall clock, so a clock adjustment cannot shorten or extend a pass.
 *
 * ## What the timers are and are not
 *
 * They are scheduling, not semantics. Input arrival time, cutoff time and pass
 * membership MUST NOT enter candidate validity or the branch score, and
 * implementations MUST NOT use timer tuning or pass partitioning to resolve a
 * difference the deterministic rules leave open. Dividing the same retained
 * input across different passes must not change the eventual result — the
 * timers only decide when work happens, never what it decides.
 *
 * Fixed-point resolution itself has no protocol deadline: device speed MUST NOT
 * make two clients resolve the same frozen batch differently.
 */
class ConvergencePass(
    /** Canonical epoch when the pass started. Fixed until a branch is applied. */
    val passBaseEpoch: Long,
    private val policy: ConvergencePolicy = ConvergencePolicy.V1,
    /** Local monotonic milliseconds. */
    private val monotonicNowMs: () -> Long,
) {
    private val startedAtMs: Long = monotonicNowMs()

    /** When the quiescence window last restarted. */
    private var lastSelectionRelevantMs: Long = startedAtMs

    private var frozen: Boolean = false
    private var forkDetected: Boolean = false
    private var disbandCandidateAdmitted: Boolean = false

    private val batch = mutableListOf<Any>()

    /** The absolute deadline. Starts when the pass starts and never restarts. */
    val absoluteDeadlineMs: Long get() = startedAtMs + policy.maxConvergencePassMs

    /** The current quiescence deadline; moves forward on selection-relevant input. */
    val quiescenceDeadlineMs: Long get() = lastSelectionRelevantMs + policy.settlementQuiescenceMs

    /** The earlier of the two — the cutoff at which the batch freezes. */
    val cutoffMs: Long get() = minOf(quiescenceDeadlineMs, absoluteDeadlineMs)

    /** Inputs admitted before the cutoff. Immutable once frozen. */
    val admitted: List<Any> get() = batch.toList()

    val isFrozen: Boolean get() = frozen

    /** True once an eligible divergent edge made this a recovery pass. */
    val isRecovery: Boolean get() = forkDetected || disbandCandidateAdmitted

    /**
     * Admit [input] into this pass.
     *
     * Returns false when the pass has already frozen: that input is not
     * discarded, it simply belongs to a LATER pass. Retention and admission are
     * different things.
     */
    fun admit(
        input: Any,
        relevance: InputRelevance,
    ): Boolean {
        if (isClosed()) return false
        batch.add(input)
        if (relevance == InputRelevance.SELECTION_RELEVANT) {
            lastSelectionRelevantMs = monotonicNowMs()
        }
        return true
    }

    /**
     * Record that an eligible divergent edge is available, turning this into a
     * recovery pass.
     *
     * Does NOT restart either timer or resnapshot [passBaseEpoch]: a pass that
     * becomes a recovery is the SAME pass, and restarting would let a steady
     * trickle of forks keep it open forever.
     */
    fun markForkDetected() {
        forkDetected = true
    }

    /**
     * Record that a valid candidate changing the group lifecycle to
     * `disbanded` was admitted.
     *
     * This forces `Stable -> Recovering` even when the candidate is a linear
     * edge and no fork exists, so terminalization can only happen after branch
     * selection. It asserts nothing about the candidate set being forked, gives
     * the disband no scoring priority, and — like [markForkDetected] — restarts
     * nothing.
     */
    fun markDisbandCandidateAdmitted() {
        disbandCandidateAdmitted = true
    }

    fun isClosed(nowMs: Long = monotonicNowMs()): Boolean = frozen || nowMs >= cutoffMs

    /**
     * Freeze the batch. Idempotent; after this, [admit] refuses everything.
     */
    fun freeze(): List<Any> {
        frozen = true
        return admitted
    }

    /**
     * The convergence status this pass implies, given whether resolution of the
     * frozen batch has finished.
     *
     * `Settled` is a LOCAL fixed point over the input this client retained and
     * admitted. It is not global finality and not a promise that later valid
     * input cannot open another pass.
     */
    fun status(
        nowMs: Long = monotonicNowMs(),
        resolutionComplete: Boolean = false,
    ): ConvergenceStatus =
        when {
            !isClosed(nowMs) -> ConvergenceStatus.SYNCING
            !resolutionComplete -> ConvergenceStatus.RESOLVING
            else -> ConvergenceStatus.SETTLED
        }

    /**
     * The lifecycle state this pass implies while it runs, starting from
     * [current].
     *
     * A linear pass that began in `Stable` stays `Stable`; an eligible
     * divergent edge or an admitted disband candidate moves it to `Recovering`.
     */
    fun lifecycleWhileRunning(current: GroupLifecycleState): GroupLifecycleState = if (current == GroupLifecycleState.STABLE && isRecovery) GroupLifecycleState.RECOVERING else current

    companion object {
        /**
         * Whether a deferred Commit has aged out
         * (`protocol-core/convergence.md`, "Candidate branches").
         *
         * ```text
         * canonical_tip_epoch - commit_source_epoch > max_rewind_commits
         * ```
         *
         * Note the reference point: expiry uses the LIVE canonical tip, so
         * obsolete input ages out as canonical state advances across completed
         * passes. Branch ELIGIBILITY uses the frozen `pass_base_epoch` instead,
         * so an open pass cannot change its own rollback horizon while it is
         * comparing candidates. Using one epoch for both would make a pass's
         * horizon move under it.
         */
        fun isDeferredCommitStale(
            canonicalTipEpoch: Long,
            commitSourceEpoch: Long,
            policy: ConvergencePolicy = ConvergencePolicy.V1,
        ): Boolean = canonicalTipEpoch - commitSourceEpoch > policy.maxRewindCommits
    }
}
