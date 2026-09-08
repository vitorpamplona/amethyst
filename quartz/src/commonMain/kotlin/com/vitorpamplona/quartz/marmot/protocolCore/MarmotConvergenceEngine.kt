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

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** What happened to a commit offered to the engine. */
enum class ConvergenceAdmission {
    /** Not a candidate here: nothing retained authenticates it. */
    NOT_A_CANDIDATE,

    /** Admitted into an open pass. Resolution happens at the pass cutoff. */
    ADMITTED,

    /** Already in the current batch. Admitted as ordinary; changes nothing. */
    DUPLICATE,
}

/** The outcome of resolving one frozen pass. */
class ConvergenceResolution(
    val groupId: HexKey,
    val status: ConvergenceStatus,
    val lifecycle: GroupLifecycleState,
    /** Epoch of the state that is canonical after resolution. */
    val canonicalEpoch: Long,
    /** True when selection replaced the state that was canonical when the pass opened. */
    val rewound: Boolean,
    val outcomes: List<CommitOutcome>,
)

/**
 * Runs Marmot convergence for the groups a client holds
 * (`protocol-core/convergence.md`).
 *
 * ## The shape, and why it is not "buffer every commit for a second"
 *
 * A literal reading of the bounded pass would hold EVERY inbound commit for the
 * quiescence window before applying it, taxing the common case — one commit, no
 * competitor — with a second of latency for nothing.
 *
 * It does not have to. MLS gives us the fork detector for free: once a commit
 * has been applied, a competitor authored against the same parent no longer
 * authenticates against the new tip, but it still authenticates against the
 * RETAINED parent. So this engine applies linear commits eagerly and keeps a
 * bounded window of the states they came from; a commit that authenticates
 * against a retained state rather than the tip IS the fork, and only then does
 * a pass open.
 *
 * That is not an optimization that changes the answer. When the pass resolves,
 * the incumbent branch is rebuilt from the same retained window and compared by
 * the same six-step rule as its challengers, so a client that saw the commits
 * in one order reaches the state a client that saw them in the other order
 * reaches. Eager application only decides which branch is provisionally
 * displayed while the pass runs.
 *
 * ## What it deliberately does not do
 *
 * Transport metadata never enters any decision here. The superseded MIP-era
 * rule broke same-epoch ties on the outer Nostr `created_at` and event id —
 * both attacker-chosen, and neither authenticated by MLS.
 */
class MarmotConvergenceEngine(
    private val groupManager: MlsGroupManager,
    private val policy: ConvergencePolicy = ConvergencePolicy.V1,
    /** Local monotonic milliseconds. Injected so pass timing is testable. */
    private val monotonicNowMs: () -> Long = { ORIGIN.elapsedNow().inWholeMilliseconds },
) {
    private val stateEngine = MlsCandidateStateEngine()
    private val builder = CandidateGraphBuilder(stateEngine, policy)
    private val mutex = Mutex()
    private val contexts = mutableMapOf<HexKey, GroupContext>()

    private class GroupContext {
        /** Retained canonical states, oldest first. The last one is the tip. */
        val retained = ArrayDeque<MlsGroupState>()

        /** The commit that produced each retained state after the first, oldest first. */
        val canonicalCommits = ArrayDeque<CandidateCommit>()

        /** Commits that did not extend the tip but did authenticate somewhere retained. */
        val divergent = LinkedHashMap<HexKey, CandidateCommit>()

        /** stateId -> the accounts that sent a validated payload decrypting there. */
        val witnesses = mutableMapOf<String, MutableSet<HexKey>>()

        var pass: ConvergencePass? = null
        var lifecycle: GroupLifecycleState = GroupLifecycleState.STABLE
    }

    companion object {
        /**
         * Arbitrary origin for the default clock. A MONOTONIC one, never a wall
         * clock: the pass rules exist so a clock adjustment cannot shorten or
         * extend a window.
         */
        private val ORIGIN = TimeSource.Monotonic.markNow()
    }

    /**
     * How many states to keep. One more than the rollback horizon, so a branch
     * forking the full [ConvergencePolicy.maxRewindCommits] back still has its
     * fork point in the window — without the extra slot the oldest reachable
     * fork point would be evicted exactly when it became relevant.
     */
    private val windowSize: Int get() = (policy.maxRewindCommits + 1).toInt()

    /** Convergence status for [groupId]; `SETTLED` when no pass is running. */
    suspend fun status(groupId: HexKey): ConvergenceStatus =
        mutex.withLock {
            contexts[groupId]?.pass?.status(monotonicNowMs(), resolutionComplete = false)
                ?: ConvergenceStatus.SETTLED
        }

    /** Lifecycle state for [groupId], including the pass's `Stable -> Recovering` move. */
    suspend fun lifecycle(groupId: HexKey): GroupLifecycleState =
        mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock GroupLifecycleState.STABLE
            ctx.pass?.lifecycleWhileRunning(ctx.lifecycle) ?: ctx.lifecycle
        }

    /** Groups with a pass currently open, and the base epoch each snapshotted. */
    suspend fun openPasses(): Map<HexKey, Long> =
        mutex.withLock {
            contexts.mapNotNull { (id, ctx) -> ctx.pass?.let { id to it.passBaseEpoch } }.toMap()
        }

    /**
     * Seed the retained window with a group's current state.
     *
     * Called when a group is created, joined, or restored from disk. Without a
     * seed the first inbound commit has no retained parent and convergence
     * cannot see a fork at all.
     */
    suspend fun trackGroup(groupId: HexKey) =
        mutex.withLock {
            val state = groupManager.snapshot(groupId) ?: return@withLock
            val ctx = contexts.getOrPut(groupId) { GroupContext() }
            if (ctx.retained.isEmpty()) ctx.retained.addLast(state)
        }

    /**
     * Record that [commitBytes] was applied to canonical state, moving the
     * window forward.
     *
     * [preState] is the state it was applied TO — the parent a competitor would
     * authenticate against, and the reason a losing commit is still resolvable
     * after we already moved on.
     */
    suspend fun recordApplied(
        groupId: HexKey,
        commitBytes: ByteArray,
        sourceEpoch: Long,
        preState: MlsGroupState?,
    ) = mutex.withLock {
        val ctx = contexts.getOrPut(groupId) { GroupContext() }
        if (preState != null && ctx.retained.none { sameState(it, preState) }) {
            ctx.retained.addLast(preState)
        }
        val post = groupManager.snapshot(groupId)
        if (post != null && ctx.retained.none { sameState(it, post) }) {
            ctx.retained.addLast(post)
        }
        ctx.canonicalCommits.addLast(candidateOf(commitBytes, sourceEpoch))
        trim(ctx)
    }

    /**
     * Offer a commit that did NOT extend the canonical tip.
     *
     * Returns [ConvergenceAdmission.NOT_A_CANDIDATE] when nothing retained
     * authenticates it — that is an echo of something we already applied, or a
     * commit from before our retention window, and neither is a fork.
     */
    suspend fun offerDivergent(
        groupId: HexKey,
        commitBytes: ByteArray,
        sourceEpoch: Long,
    ): ConvergenceAdmission =
        mutex.withLock {
            val ctx = contexts.getOrPut(groupId) { GroupContext() }
            val candidate = candidateOf(commitBytes, sourceEpoch)
            if (ctx.divergent.containsKey(candidate.id)) {
                ctx.pass?.admit(candidate, InputRelevance.ORDINARY)
                return@withLock ConvergenceAdmission.DUPLICATE
            }

            // Parentage is derived, not claimed: a commit is a candidate here
            // only because some retained state actually authenticates it.
            val hasParent = ctx.retained.any { stateEngine.authenticatesAgainst(it, commitBytes) }
            if (!hasParent) return@withLock ConvergenceAdmission.NOT_A_CANDIDATE

            ctx.divergent[candidate.id] = candidate
            val pass = ctx.pass ?: openPass(groupId, ctx)
            // A new divergent commit can add an eligible edge, so it restarts
            // quiescence. Its admission may be refused if the pass already
            // closed — that input simply belongs to the next pass, and it stays
            // in `divergent` for that pass to pick up.
            pass.admit(candidate, InputRelevance.SELECTION_RELEVANT)
            ConvergenceAdmission.ADMITTED
        }

    /**
     * Record that a validated app payload from [senderAccount] decrypted
     * against [stateId].
     *
     * Witness weight is what lets a branch members are actually talking on beat
     * a slightly longer branch nobody used, so a genuinely NEW (state, account)
     * pair is selection-relevant; a repeat cannot change a capped score and is
     * ordinary.
     */
    suspend fun recordWitness(
        groupId: HexKey,
        stateId: String,
        senderAccount: HexKey,
    ) = mutex.withLock {
        val ctx = contexts.getOrPut(groupId) { GroupContext() }
        val added = ctx.witnesses.getOrPut(stateId) { mutableSetOf() }.add(senderAccount)
        val observation = WitnessObservation(stateId, senderAccount)
        ctx.pass?.admit(
            observation,
            if (added) InputRelevance.SELECTION_RELEVANT else InputRelevance.ORDINARY,
        )
        Unit
    }

    /** Resolve [groupId]'s pass if its cutoff has passed. Null when nothing is due. */
    suspend fun settleIfDue(groupId: HexKey): ConvergenceResolution? {
        val due =
            mutex.withLock {
                val pass = contexts[groupId]?.pass ?: return null
                pass.isClosed(monotonicNowMs())
            }
        return if (due) settle(groupId) else null
    }

    /** Resolve every group whose pass cutoff has passed. */
    suspend fun settleAllDue(): List<ConvergenceResolution> {
        val candidates = mutex.withLock { contexts.keys.toList() }
        return candidates.mapNotNull { settleIfDue(it) }
    }

    /**
     * Resolve [groupId]'s pass now, without waiting for its cutoff.
     *
     * The pass timers are scheduling, not semantics: closing one early changes
     * WHEN the batch is resolved, never what the frozen batch resolves to.
     */
    suspend fun settle(groupId: HexKey): ConvergenceResolution? {
        // Graph construction restores groups and replays MLS bytes, which is
        // slow enough that holding the engine mutex across it would stall every
        // other group. Snapshot the inputs under the lock, resolve outside it,
        // then commit the result under the lock again.
        val inputs = mutex.withLock { freezeInputs(groupId) } ?: return null

        val graph =
            builder.build(
                retainedStates = inputs.retained,
                canonicalStateId = inputs.baseId,
                canonicalAncestry = inputs.ancestry,
                commits = inputs.commits,
                witnesses = inputs.witnesses,
                canonicalTipEpoch = inputs.tipEpoch,
                passBaseEpoch = inputs.passBaseEpoch,
            )

        val selected = BranchSelector.select(graph.branches, inputs.passBaseEpoch, policy)
        val selectedTipId = selected?.let { graph.branchTips[it.tipDigestHex] }
        val rewound = selectedTipId != null && selectedTipId != inputs.tipId

        if (rewound) {
            groupManager.installState(groupId, graph.statesById.getValue(selectedTipId))
        }

        return mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock null
            if (rewound && selectedTipId != null) {
                adoptBranch(ctx, graph, selectedTipId, inputs.baseId)
            }
            ctx.divergent.clear()
            ctx.pass = null
            val epoch = groupManager.getGroup(groupId)?.epoch ?: inputs.tipEpoch
            ConvergenceResolution(
                groupId = groupId,
                status = ConvergenceStatus.SETTLED,
                lifecycle = ctx.lifecycle,
                canonicalEpoch = epoch,
                rewound = rewound,
                outcomes = graph.outcomes,
            )
        }
    }

    /** Forget everything about [groupId] — used when leaving or deleting a group. */
    suspend fun forget(groupId: HexKey) =
        mutex.withLock {
            contexts.remove(groupId)
            Unit
        }

    suspend fun clear() =
        mutex.withLock {
            contexts.clear()
        }

    // --- internals ---

    private class FrozenInputs(
        val retained: List<MlsGroupState>,
        val baseId: String,
        val ancestry: List<String>,
        val commits: List<CandidateCommit>,
        val witnesses: List<WitnessObservation>,
        val tipId: String,
        val tipEpoch: Long,
        val passBaseEpoch: Long,
    )

    /**
     * Freeze the pass and assemble what the graph is built over.
     *
     * The subtle part is the base. Branches are measured as divergences from
     * the canonical path, so if the CURRENT tip were the base the incumbent
     * would not be a branch at all and could not be compared to its
     * challengers. The base is therefore the newest retained state a divergent
     * commit authenticates against — everything after it is contested, the
     * incumbent included, and both sides are rebuilt from the same fork point.
     */
    private fun freezeInputs(groupId: HexKey): FrozenInputs? {
        val ctx = contexts[groupId] ?: return null
        val pass = ctx.pass ?: return null
        pass.freeze()

        val retained = ctx.retained.toList()
        if (retained.isEmpty()) return null
        val tip = retained.last()
        val tipId = stateEngine.stateId(tip)

        val divergent = ctx.divergent.values.toList()
        val baseIndex =
            retained.indices.lastOrNull { i ->
                divergent.any { stateEngine.authenticatesAgainst(retained[i], it.bytes) }
            } ?: return null

        val ancestry = retained.take(baseIndex + 1).map { stateEngine.stateId(it) }
        // The canonical commits applied at or after the base rebuild the
        // incumbent branch, so it is scored by the same rule as the others
        // instead of winning by being already applied.
        val canonicalSuffix = ctx.canonicalCommits.drop(baseIndex)

        return FrozenInputs(
            retained = retained,
            baseId = ancestry.last(),
            ancestry = ancestry,
            commits = canonicalSuffix + divergent,
            witnesses = ctx.witnesses.flatMap { (s, accounts) -> accounts.map { WitnessObservation(s, it) } },
            tipId = tipId,
            tipEpoch = stateEngine.epoch(tip),
            passBaseEpoch = pass.passBaseEpoch,
        )
    }

    /** Re-anchor the retained window onto the branch that just won. */
    private fun adoptBranch(
        ctx: GroupContext,
        graph: CandidateGraph<MlsGroupState>,
        tipId: String,
        baseId: String,
    ) {
        val path = mutableListOf<String>()
        var cursor: String? = tipId
        while (cursor != null && cursor != baseId) {
            path.add(cursor)
            cursor = graph.parentOf[cursor]
        }
        if (cursor != baseId) return
        path.reverse()

        val keepStates = mutableListOf<MlsGroupState>()
        val keepCommits = mutableListOf<CandidateCommit>()
        for (state in ctx.retained) {
            keepStates.add(state)
            if (stateEngine.stateId(state) == baseId) break
        }
        // Canonical commits run parallel to the states after the first, so the
        // prefix to keep is one shorter than the retained prefix.
        repeat(minOf(keepStates.size - 1, ctx.canonicalCommits.size)) {
            keepCommits.add(ctx.canonicalCommits[it])
        }
        for (stateId in path) {
            keepStates.add(graph.statesById.getValue(stateId))
            graph.producedBy[stateId]?.let { keepCommits.add(it) }
        }

        ctx.retained.clear()
        ctx.retained.addAll(keepStates)
        ctx.canonicalCommits.clear()
        ctx.canonicalCommits.addAll(keepCommits)
        trim(ctx)
    }

    private fun openPass(
        groupId: HexKey,
        ctx: GroupContext,
    ): ConvergencePass {
        val baseEpoch = groupManager.getGroup(groupId)?.epoch ?: 0L
        val pass = ConvergencePass(baseEpoch, policy, monotonicNowMs)
        // A divergent commit that authenticates against a retained state IS an
        // eligible divergent edge, which is exactly what makes this a recovery
        // rather than a linear pass.
        pass.markForkDetected()
        ctx.pass = pass
        ctx.lifecycle = pass.lifecycleWhileRunning(ctx.lifecycle)
        return pass
    }

    /**
     * Keep the window bounded, holding the invariant the base search relies on:
     * `canonicalCommits[i]` is the commit that turned `retained[i]` into
     * `retained[i + 1]`, so there is always exactly one fewer commit than state.
     */
    private fun trim(ctx: GroupContext) {
        while (ctx.canonicalCommits.size > ctx.retained.size - 1 && ctx.canonicalCommits.isNotEmpty()) {
            ctx.canonicalCommits.removeFirst()
        }
        while (ctx.retained.size > windowSize) {
            ctx.retained.removeFirst()
            if (ctx.canonicalCommits.isNotEmpty()) ctx.canonicalCommits.removeFirst()
        }
    }

    private fun candidateOf(
        commitBytes: ByteArray,
        sourceEpoch: Long,
    ) = CandidateCommit(
        // The Marmot message id is SHA-256 over the MLS message bytes, and for
        // a commit that is byte-for-byte its commit_digest, so there is no
        // second hash to keep in sync.
        id = sha256(commitBytes).toHexKey(),
        bytes = commitBytes,
        sourceEpoch = sourceEpoch,
    )

    private fun sameState(
        a: MlsGroupState,
        b: MlsGroupState,
    ) = a.groupContext.toTlsBytes().contentEquals(b.groupContext.toTlsBytes())
}
