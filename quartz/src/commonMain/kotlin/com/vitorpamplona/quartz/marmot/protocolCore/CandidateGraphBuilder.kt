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

/**
 * The MLS-side operations the candidate graph needs, as a port.
 *
 * Kept abstract over the state type `S` for one reason: the graph algorithm is
 * about shapes — which commit hangs off which state, where a branch diverged,
 * how deep it is — and that logic is worth testing without standing up real MLS
 * groups, key schedules and signatures for every case. The production adapter
 * is `MlsCandidateStateEngine`.
 *
 * Every method here is defined in terms of AUTHENTICATED bytes. None of them
 * may consult transport metadata.
 */
interface CandidateStateEngine<S> {
    /**
     * A stable identity for one retained state.
     *
     * Must distinguish two states that share an epoch NUMBER but are different
     * states — that is the whole point of a fork. The spec is explicit that
     * "merely sharing an epoch number does not make another retained state an
     * alternate candidate parent".
     */
    fun stateId(state: S): String

    fun epoch(state: S): Long

    /**
     * True when [commit] authenticates against [state] as its candidate parent.
     *
     * For Marmot this is the RFC 9420 §6.2 membership tag plus the sender
     * signature, both of which bind the commit to one specific source-epoch
     * state — not to an epoch number. This is what identifies the parent;
     * transport metadata never does.
     */
    fun authenticatesAgainst(
        state: S,
        commit: ByteArray,
    ): Boolean

    /**
     * Replay [commit] against [state], returning the resulting state, or null
     * when MLS validation fails.
     *
     * MUST NOT mutate [state]: a candidate parent is tried by several commits
     * and must survive each attempt unchanged.
     */
    fun replay(
        state: S,
        commit: ByteArray,
    ): S?

    /**
     * The authenticated Marmot account identity of [commit]'s committer,
     * resolved through [parent]'s tree.
     *
     * Resolved against the PARENT because that is the state whose leaves the
     * commit was authenticated against.
     */
    fun committerIdentity(
        parent: S,
        commit: ByteArray,
    ): ByteArray?

    /**
     * Whether the committer is authorized against [parent]'s policy.
     *
     * Authorization is parent-relative and is only evaluated once
     * [authenticatesAgainst] has already identified the parent. Failing this
     * against a state whose MLS authentication did NOT match is not an
     * authorization failure at all and must not reject the commit.
     */
    fun isAuthorized(
        parent: S,
        commit: ByteArray,
    ): Boolean

    /**
     * The commit's authorization class, from the rule that applies to it —
     * `privileged` exactly when that rule requires an active admin.
     */
    fun tipPriority(
        parent: S,
        commit: ByteArray,
    ): TipPriority

    /**
     * Whether [resulting] satisfies Marmot's cross-component invariants.
     *
     * An edge whose resulting state fails these MUST NOT be created, so that
     * convergence can never select an invalid transition.
     */
    fun resultingStateIsValid(resulting: S): Boolean

    /** `SHA-256` over the commit's MLS bytes — its `commit_digest`. */
    fun commitDigest(commit: ByteArray): ByteArray
}

/** One commit offered to the graph, with a stable id for reporting dispositions. */
class CandidateCommit(
    val id: HexKey,
    val bytes: ByteArray,
    /**
     * The epoch the commit's MLS handshake authenticates as its source.
     *
     * Read from the MLS message, never from transport. Used for
     * deferred-commit expiry before any parent is known.
     */
    val sourceEpoch: Long,
)

/**
 * An app-payload witness: a validated application message that decrypted on a
 * branch state, attributed to the MLS-authenticated sender ACCOUNT.
 */
class WitnessObservation(
    /** The state the payload decrypted against. */
    val stateId: String,
    /** Account identity of the sender, hex. Never a transport pubkey. */
    val senderAccount: HexKey,
)

/** What the graph decided about one offered commit. */
class CommitOutcome(
    val commitId: HexKey,
    val disposition: ConvergenceDisposition,
    val category: ConvergenceCategory?,
    /** Null unless the commit produced an edge. */
    val resultingStateId: String?,
)

/** The result of one build pass. */
class CandidateGraph<S>(
    val branches: List<CandidateBranch>,
    val outcomes: List<CommitOutcome>,
    /** Every state reachable in this graph, by id — canonical and candidate alike. */
    val statesById: Map<String, S>,
)

/**
 * Builds candidate branches by replaying MLS commit bytes against retained
 * group states (`protocol-core/convergence.md`, "Candidate branches").
 *
 * ## What makes this non-obvious
 *
 * A commit does not say what its parent is, and it must not be believed if it
 * did — parentage is DERIVED by replaying MLS bytes against retained states.
 * So the builder is a fixed-point loop, not a single sweep: replaying a commit
 * produces a new state, which may in turn be the parent of a commit that
 * nothing could place a moment earlier. It keeps sweeping the unplaced commits
 * until a pass produces no new edge.
 *
 * ## Where a commit ends up
 *
 * - authenticates, replays, authorized, resulting state valid -> an edge
 * - authenticates but is unauthorized -> TERMINAL `authorization_failed`, because
 *   the parent is now known
 * - authenticates but the resulting state breaks a component invariant -> no
 *   edge is created; convergence must never be able to select it
 * - nothing authenticates it -> `deferred`, and only `stale` once the LIVE
 *   canonical tip has moved past the rollback horizon. Absence of a parent is
 *   never by itself an authorization failure.
 */
class CandidateGraphBuilder<S>(
    private val engine: CandidateStateEngine<S>,
    private val policy: ConvergencePolicy = ConvergencePolicy.V1,
) {
    /**
     * @param retainedStates every retained state at or after the retained anchor,
     *   including the canonical one.
     * @param canonicalStateId the id of the current canonical state; branches are
     *   measured as divergences from the retained canonical path.
     * @param canonicalAncestry canonical state ids from the anchor to the tip, in
     *   order. A branch's `fork_epoch` is the epoch of the newest canonical
     *   ancestor it shares.
     * @param canonicalTipEpoch the LIVE tip, used for deferred-commit expiry —
     *   deliberately not [passBaseEpoch], which is frozen for eligibility.
     */
    fun build(
        retainedStates: List<S>,
        canonicalStateId: String,
        canonicalAncestry: List<String>,
        commits: List<CandidateCommit>,
        witnesses: List<WitnessObservation> = emptyList(),
        canonicalTipEpoch: Long,
        passBaseEpoch: Long,
    ): CandidateGraph<S> {
        val statesById = LinkedHashMap<String, S>()
        retainedStates.forEach { statesById[engine.stateId(it)] = it }

        // stateId -> the state it was produced from, and by which commit.
        val parentOf = HashMap<String, String>()
        val producedBy = HashMap<String, CandidateCommit>()
        val committerOf = HashMap<String, ByteArray>()
        val priorityOf = HashMap<String, TipPriority>()

        val outcomes = LinkedHashMap<HexKey, CommitOutcome>()
        var unplaced = commits.toMutableList()

        // Fixed point: a commit that nothing can place today may be placeable
        // once another commit's resulting state exists.
        while (true) {
            val stillUnplaced = mutableListOf<CandidateCommit>()
            var progressed = false

            for (commit in unplaced) {
                val parent = statesById.values.firstOrNull { engine.authenticatesAgainst(it, commit.bytes) }
                if (parent == null) {
                    stillUnplaced.add(commit)
                    continue
                }

                progressed = true
                if (!engine.isAuthorized(parent, commit.bytes)) {
                    // Terminal: the parent IS known, so this is a real
                    // authorization failure rather than a missing ancestor.
                    outcomes[commit.id] =
                        CommitOutcome(
                            commit.id,
                            ConvergenceDisposition.STALE,
                            ConvergenceCategory.AUTHORIZATION_FAILED,
                            null,
                        )
                    continue
                }

                val resulting = engine.replay(parent, commit.bytes)
                if (resulting == null || !engine.resultingStateIsValid(resulting)) {
                    // No edge is created. A resulting state that breaks a
                    // component invariant must never become selectable.
                    outcomes[commit.id] =
                        CommitOutcome(commit.id, ConvergenceDisposition.STALE, null, null)
                    continue
                }

                val childId = engine.stateId(resulting)
                statesById[childId] = resulting
                parentOf[childId] = engine.stateId(parent)
                producedBy[childId] = commit
                engine.committerIdentity(parent, commit.bytes)?.let { committerOf[childId] = it }
                priorityOf[childId] = engine.tipPriority(parent, commit.bytes)
                outcomes[commit.id] =
                    CommitOutcome(commit.id, ConvergenceDisposition.ACCEPTED, null, childId)
            }

            unplaced = stillUnplaced
            if (!progressed || unplaced.isEmpty()) break
        }

        for (commit in unplaced) {
            val stale = ConvergencePass.isDeferredCommitStale(canonicalTipEpoch, commit.sourceEpoch, policy)
            outcomes[commit.id] =
                CommitOutcome(
                    commit.id,
                    if (stale) ConvergenceDisposition.STALE else ConvergenceDisposition.DEFERRED,
                    if (stale) ConvergenceCategory.STALE_EPOCH else ConvergenceCategory.MISSING_HISTORY,
                    null,
                )
        }

        val branches =
            buildBranches(
                statesById = statesById,
                parentOf = parentOf,
                producedBy = producedBy,
                committerOf = committerOf,
                priorityOf = priorityOf,
                canonicalStateId = canonicalStateId,
                canonicalAncestry = canonicalAncestry.toSet(),
                witnesses = witnesses,
                passBaseEpoch = passBaseEpoch,
            )

        return CandidateGraph(branches, outcomes.values.toList(), statesById)
    }

    private fun buildBranches(
        statesById: Map<String, S>,
        parentOf: Map<String, String>,
        producedBy: Map<String, CandidateCommit>,
        committerOf: Map<String, ByteArray>,
        priorityOf: Map<String, TipPriority>,
        canonicalStateId: String,
        canonicalAncestry: Set<String>,
        witnesses: List<WitnessObservation>,
        passBaseEpoch: Long,
    ): List<CandidateBranch> {
        // A tip is any produced state nothing else was produced from.
        val hasChild = parentOf.values.toSet()
        val tips = producedBy.keys.filterNot { it in hasChild }

        val witnessesByState =
            witnesses.groupBy({ it.stateId }, { it.senderAccount }).mapValues { it.value.toSet() }

        return tips.mapNotNull { tipId ->
            // Walk back to the newest ancestor on the retained canonical path.
            val path = mutableListOf<String>()
            var cursor: String? = tipId
            var forkStateId: String? = null
            while (cursor != null) {
                if (cursor in canonicalAncestry || cursor == canonicalStateId) {
                    forkStateId = cursor
                    break
                }
                path.add(cursor)
                cursor = parentOf[cursor]
            }
            val forkId = forkStateId ?: return@mapNotNull null
            if (path.isEmpty()) return@mapNotNull null

            val forkEpoch = engine.epoch(statesById.getValue(forkId))
            val tipEpoch = engine.epoch(statesById.getValue(tipId))

            // `path` runs tip -> fork; witnesses are keyed per branch epoch.
            val perEpoch = HashMap<Long, MutableSet<HexKey>>()
            for (stateId in path) {
                val epoch = engine.epoch(statesById.getValue(stateId))
                witnessesByState[stateId]?.let { perEpoch.getOrPut(epoch) { mutableSetOf() }.addAll(it) }
            }

            // Comparison step 5 breaks a tie on the tip committer's ACCOUNT
            // pubkey. A tip we cannot attribute to one has no value to compare,
            // and substituting zeros would hand it the lowest possible key —
            // winning a tie-break it never earned. Drop it instead: an
            // unattributable tip is not selectable.
            val tipCommitter = committerOf[tipId]?.takeIf { it.size == 32 } ?: return@mapNotNull null

            CandidateBranch(
                forkEpoch = forkEpoch,
                tipEpoch = tipEpoch,
                rawCommitDepth = path.size.toLong(),
                tipPriority = priorityOf[tipId] ?: TipPriority.ORDINARY,
                tipCommitter = tipCommitter,
                tipDigest = engine.commitDigest(producedBy.getValue(tipId).bytes),
                witnessesByEpoch = perEpoch,
            ).takeIf { it.isEligible(passBaseEpoch, policy) }
        }
    }
}
