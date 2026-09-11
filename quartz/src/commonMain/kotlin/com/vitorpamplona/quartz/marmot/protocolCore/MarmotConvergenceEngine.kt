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

import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
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

/**
 * An MLS application message that decrypted against a RETAINED CANDIDATE state
 * rather than against canonical state.
 *
 * Per `protocol-core/inbound-processing.md` this is not a delivery: a payload
 * that decrypts only on a losing branch is invalidated, not handed to the
 * application. It is still protocol input — it can be an app-payload witness
 * for the branch it decrypted on, which is how a branch members actually used
 * outweighs an equally long one nobody did.
 */
class CandidateAppMessage(
    /** The candidate state it decrypted against. */
    val stateId: String,
    val epoch: Long,
    val senderLeafIndex: Int,
    /** Account identity from the MLS leaf credential, hex. Never a transport key. */
    val senderAccount: HexKey?,
    val content: ByteArray,
)

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

        /**
         * States reachable only from divergent commits, by id.
         *
         * Kept so an app message that decrypts on no canonical epoch can still
         * be tried against the branches under evaluation. Without them a
         * losing branch could never accumulate witnesses and the witness steps
         * of the comparison would be dead code.
         */
        val candidateStates = LinkedHashMap<String, MlsGroupState>()

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

        /**
         * How many concurrent branches' worth of states to hold for trial
         * decryption. A bound, not a protocol constant: a real group forks in
         * two, and anything that produces more than a handful of live branches
         * is an attack, not usage.
         */
        private const val MAX_CANDIDATE_BRANCHES = 4
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
        admitDisbandForSelection(groupId, ctx)
    }

    /**
     * A disband Commit reached canonical state — open a pass and wait for
     * selection instead of terminalizing here.
     *
     * `group-lifecycle-v1.md` ("Convergence and realization"): a valid disband
     * Commit is never terminalized through ordinary linear advancement.
     * Admitting one moves the lifecycle to `Recovering` EVEN WHEN NO DIVERGENT
     * EDGE EXISTS, and only a SELECTED disband Commit moves it on to
     * `Disbanded`.
     *
     * The distinction is the whole safety property. Terminalizing on
     * application means a disband that loses a branch race has already
     * destroyed this client's group: `Disbanded` is absorbing, so it stops
     * processing group traffic and can never learn that the branch it lost was
     * the one everyone else kept. Waiting for selection costs one bounded pass
     * and makes the outcome the group's rather than ours.
     *
     * The pass is opened WITHOUT [ConvergencePass.markForkDetected] — the spec
     * is explicit that this forced transition does not assert that a fork
     * exists — so a no-fork disband settles on quiescence with one branch and
     * terminalizes, while a real race is resolved on its merits with no special
     * ordering priority for the disband.
     */
    private fun admitDisbandForSelection(
        groupId: HexKey,
        ctx: GroupContext,
    ) {
        if (ctx.lifecycle == GroupLifecycleState.DISBANDED) return
        if (groupManager.getGroup(groupId)?.currentGroupState()?.isDisbanded != true) return
        val pass = ctx.pass ?: openPass(groupId, ctx, forkDetected = false)
        pass.markDisbandCandidateAdmitted()
        ctx.lifecycle = pass.lifecycleWhileRunning(ctx.lifecycle)
    }

    /**
     * Move a group to `Disbanded` once its lifecycle component says so.
     *
     * `Disbanded` is absorbing: there is no outgoing transition, no later
     * branch supersedes a terminalized disband, and a replacement conversation
     * is a new MLS group. Deriving it from the applied state rather than from
     * a transport claim is the whole point — the only thing that can disband a
     * group is an authenticated Commit that every member replays identically.
     */
    private fun terminalizeIfDisbanded(
        groupId: HexKey,
        ctx: GroupContext,
    ) {
        if (ctx.lifecycle == GroupLifecycleState.DISBANDED) return
        val disbanded = groupManager.getGroup(groupId)?.currentGroupState()?.isDisbanded == true
        if (disbanded) ctx.lifecycle = GroupLifecycleState.DISBANDED
    }

    /**
     * Mark a group locally unrecoverable.
     *
     * Local to ONE client: it does not mean the group is dead, it means this
     * client cannot safely apply more traffic until it repairs, restores,
     * rejoins or discards its copy. Settling for the current local state just
     * because it is the only one available is exactly what this state exists
     * to prevent, so it also drops any pass in flight rather than letting it
     * resolve against material we no longer trust.
     */
    suspend fun markUnrecoverable(groupId: HexKey) =
        mutex.withLock {
            val ctx = contexts.getOrPut(groupId) { GroupContext() }
            if (ctx.lifecycle == GroupLifecycleState.DISBANDED) return@withLock
            ctx.lifecycle = GroupLifecycleState.UNRECOVERABLE
            ctx.pass = null
        }

    /**
     * Clear `Unrecoverable` after a verified repair — a replacement Welcome, a
     * restore, or a rejoin. `Disbanded` is NOT clearable.
     */
    suspend fun markRepaired(groupId: HexKey) =
        mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock
            if (ctx.lifecycle == GroupLifecycleState.UNRECOVERABLE) {
                ctx.lifecycle = GroupLifecycleState.STABLE
            }
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
            // Replay it now, not only at resolution. The resulting state is
            // what an app message on this branch decrypts against, and
            // witnesses have to accumulate DURING the pass to influence the
            // selection that pass makes.
            for (parent in ctx.retained) {
                if (!stateEngine.authenticatesAgainst(parent, commitBytes)) continue
                if (!stateEngine.isAuthorized(parent, commitBytes)) continue
                val child = stateEngine.replay(parent, commitBytes) ?: continue
                if (!stateEngine.resultingStateIsValid(child)) continue
                ctx.candidateStates[stateEngine.stateId(child)] = child
                trimCandidates(ctx)
            }
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

    /**
     * Outer transport keys derived from retained CANDIDATE states.
     *
     * The Marmot outer layer is keyed by a per-epoch exporter secret, so an
     * event published on a fork is not merely undecryptable at the MLS layer —
     * it does not even peel. `protocol-core/inbound-processing.md` calls a
     * transport object we cannot peel `transport_deferred` and requires a retry
     * "whenever the transport decryption context changes", and retaining a
     * candidate state IS such a change. Deriving from the retained state rather
     * than storing another secret keeps the release condition in one place:
     * when the state goes, the key goes with it.
     */
    suspend fun candidateExporterSecrets(groupId: HexKey): List<ByteArray> =
        mutex.withLock {
            contexts[groupId]?.candidateStates?.values?.mapNotNull { state ->
                try {
                    MlsGroup.restore(state).exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
        }

    /**
     * Try to decrypt an MLS application message against retained CANDIDATE
     * states, after canonical and retained-epoch decryption have both failed.
     *
     * This is the bounded trial set `protocol-core/retained-history.md`
     * describes: canonical epochs inside the app-payload window (which the
     * group manager's own retained-epoch fallback covers), plus the candidate
     * parents convergence is holding. It is deliberately not "try every key we
     * have ever seen" — the set is bounded by the rollback horizon, so a
     * flood of undecryptable ciphertext costs a bounded number of attempts.
     */
    suspend fun tryCandidateDecrypt(
        groupId: HexKey,
        mlsBytes: ByteArray,
    ): CandidateAppMessage? =
        mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock null
            for ((stateId, state) in ctx.candidateStates) {
                val decrypted =
                    try {
                        // A clone per attempt: decrypting advances the secret
                        // tree, and a candidate state gets tried by every
                        // message that failed canonically.
                        MlsGroup.restore(state).decrypt(mlsBytes)
                    } catch (_: Exception) {
                        continue
                    }
                if (decrypted.contentType != ContentType.APPLICATION) continue
                return@withLock CandidateAppMessage(
                    stateId = stateId,
                    epoch = decrypted.epoch,
                    senderLeafIndex = decrypted.senderLeafIndex,
                    senderAccount = MlsGroup.restore(state).memberIdentityHex(decrypted.senderLeafIndex),
                    content = decrypted.content,
                )
            }
            null
        }

    /**
     * Record a witness for an app payload that decrypted on CANONICAL state at
     * [epoch].
     *
     * The incumbent is rebuilt as a candidate branch at resolution time and
     * scored by the same rule as its challengers, so it needs its witnesses
     * counted too. Counting only divergent branches would make every fork win
     * on witness score by default.
     */
    suspend fun recordCanonicalWitness(
        groupId: HexKey,
        epoch: Long,
        senderAccount: HexKey,
    ) = mutex.withLock {
        val ctx = contexts[groupId] ?: return@withLock
        val state = ctx.retained.lastOrNull { stateEngine.epoch(it) == epoch } ?: return@withLock
        val added = ctx.witnesses.getOrPut(stateEngine.stateId(state)) { mutableSetOf() }.add(senderAccount)
        ctx.pass?.admit(
            WitnessObservation(stateEngine.stateId(state), senderAccount),
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
        settleUncontested(groupId)?.let { return it }

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
            // The selected tip's state must be rebuildable from retained
            // material. When it is not — the anchor the rewind needs fell out
            // of the window, or a retained state failed to replay — this
            // client cannot reach the branch the group selected, and the one
            // thing it must NOT do is keep its own losing branch and call that
            // settled. That is exactly `Unrecoverable`: local, repairable, and
            // never resolved by pretending the pass succeeded.
            val target = graph.statesById[selectedTipId]
            if (target == null) {
                markUnrecoverable(groupId)
                return null
            }
            try {
                groupManager.installState(groupId, target)
            } catch (e: Exception) {
                Log.w("MarmotConvergence", "rewind of $groupId to the selected branch failed: ${e.message}", e)
                markUnrecoverable(groupId)
                return null
            }
        }

        return mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock null
            if (rewound && selectedTipId != null) {
                adoptBranch(ctx, graph, selectedTipId, inputs.baseId)
            }
            // Keep the states of branches that LOST but stay eligible: losing
            // one pass is not permanent ineligibility, and a payload that
            // arrives afterwards still needs somewhere to decrypt. Everything
            // now on the canonical path is dropped from the candidate set — it
            // is reachable as retained state.
            val canonical = ctx.retained.map { stateEngine.stateId(it) }.toSet()
            ctx.candidateStates.keys.retainAll { it !in canonical }
            graph.branches
                .mapNotNull { graph.branchTips[it.tipDigestHex] }
                .forEach { tipId ->
                    var cursor: String? = tipId
                    while (cursor != null && cursor !in canonical) {
                        graph.statesById[cursor]?.let { ctx.candidateStates[cursor!!] = it }
                        cursor = graph.parentOf[cursor]
                    }
                }
            trimCandidates(ctx)
            ctx.divergent.clear()
            ctx.pass = null
            terminalizeIfDisbanded(groupId, ctx)
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

    /**
     * Resolve a pass that has no divergent material at all.
     *
     * Every pass used to be opened BY a divergent commit, so this shape could
     * not occur: [freezeInputs] needs a retained state some divergent candidate
     * authenticates against, and with nothing divergent there is no such index,
     * so it returns null — and a null there means `settle` returns without
     * clearing `ctx.pass`. The pass then stays open forever and every caller
     * polling for settlement spins.
     *
     * A disband opens exactly that shape: the spec has it open a bounded pass
     * "even when no divergent edge exists", so that a competitor arriving
     * inside the window is still considered. When the window closes with none,
     * selection is trivial — the canonical branch is the only branch — and the
     * pass resolves with nothing rewound.
     */
    private suspend fun settleUncontested(groupId: HexKey): ConvergenceResolution? =
        mutex.withLock {
            val ctx = contexts[groupId] ?: return@withLock null
            val pass = ctx.pass ?: return@withLock null
            if (ctx.divergent.isNotEmpty()) return@withLock null

            pass.freeze()
            ctx.pass = null
            terminalizeIfDisbanded(groupId, ctx)
            ConvergenceResolution(
                groupId = groupId,
                status = ConvergenceStatus.SETTLED,
                lifecycle = ctx.lifecycle,
                canonicalEpoch = groupManager.getGroup(groupId)?.epoch ?: 0L,
                rewound = false,
                outcomes = emptyList(),
            )
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
        forkDetected: Boolean = true,
    ): ConvergencePass {
        val baseEpoch = groupManager.getGroup(groupId)?.epoch ?: 0L
        val pass = ConvergencePass(baseEpoch, policy, monotonicNowMs)
        // A divergent commit that authenticates against a retained state IS an
        // eligible divergent edge, which is exactly what makes this a recovery
        // rather than a linear pass. A disband opens a pass without one: it is
        // a recovery because the spec says terminalization waits for selection,
        // not because anything forked.
        if (forkDetected) pass.markForkDetected()
        ctx.pass = pass
        ctx.lifecycle = pass.lifecycleWhileRunning(ctx.lifecycle)
        return pass
    }

    /**
     * Bound the candidate set the same way the retained window is bounded.
     *
     * A branch outside the rollback horizon can never be selected, so holding
     * its states would only widen the trial-decryption cost for input that can
     * no longer matter.
     */
    private fun trimCandidates(ctx: GroupContext) {
        val max = windowSize * MAX_CANDIDATE_BRANCHES
        while (ctx.candidateStates.size > max) {
            val oldest = ctx.candidateStates.keys.first()
            ctx.candidateStates.remove(oldest)
        }
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
