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
 * A Marmot group's canonical lifecycle state (`protocol-core/group-state.md`).
 *
 * Each group has exactly one canonical MLS state at a time. A client may hold
 * candidate or pending state alongside it, but only one state is visible as
 * canonical, and this says which phase the group is in.
 */
enum class GroupLifecycleState {
    /** Has a canonical epoch. The ONLY state where a new local commit may be prepared. */
    STABLE,

    /** A local commit is prepared but its publish obligation is unconfirmed. */
    PENDING_PUBLISH,

    /** Publication confirmed; the staged commit is being applied. */
    MERGING,

    /**
     * Selecting a branch after a fork-shaped conflict, or after admitting a
     * valid disband candidate — which forces this state even with no fork, so
     * terminalization can only happen after selection.
     */
    RECOVERING,

    /**
     * Required retained material is permanently missing or corrupt and no
     * verified repair path exists.
     *
     * Local to one client: it does NOT mean the group is dead. It means this
     * client must repair, restore, rejoin, or discard its copy before it can
     * safely apply more group traffic. A client here MUST NOT settle for its
     * current local state just because it is the only one available.
     */
    UNRECOVERABLE,

    /** An authenticated disband Commit was selected. Absorbing; no rejoin. */
    DISBANDED,
    ;

    val isTerminal: Boolean get() = this == DISBANDED

    /** Only `Stable` may prepare a new local group-state commit. */
    val canPrepareLocalCommit: Boolean get() = this == STABLE

    /**
     * Whether retained inbound may change canonical group state here.
     *
     * False during `PendingPublish` and `Merging` (a local transition is in
     * flight), during `Unrecoverable` (nothing is safe to apply), and in
     * `Disbanded` (inbound is not even retained). `Recovering` is false too:
     * canonical state changes only when a SELECTED branch is applied, and
     * replaying candidates before then is not application.
     */
    val mayApplyInboundToCanonicalState: Boolean get() = this == STABLE

    fun canTransitionTo(next: GroupLifecycleState): Boolean = next in LEGAL_TRANSITIONS.getValue(this)

    companion object {
        /**
         * The legal transition table.
         *
         * Two absences are deliberate rather than oversights:
         *
         * - There is no `Merging -> Recovering` edge. A competing branch seen
         *   while applying our own confirmed commit is retained, the merge
         *   completes to `Stable`, and admission into a bounded pass then
         *   triggers `Stable -> Recovering`. Diverting mid-merge would leave a
         *   half-applied epoch.
         * - `Disbanded` has no outgoing edge at all. It is absorbing: no later
         *   branch supersedes a terminalized disband, and a replacement
         *   conversation is a new MLS group.
         *
         * `Recovering` re-entry is implicit rather than a self-edge: input
         * arriving during recovery, before the pass cutoff, folds into the
         * pass already running.
         */
        private val LEGAL_TRANSITIONS: Map<GroupLifecycleState, Set<GroupLifecycleState>> =
            mapOf(
                STABLE to setOf(PENDING_PUBLISH, RECOVERING, UNRECOVERABLE),
                PENDING_PUBLISH to setOf(MERGING, STABLE, UNRECOVERABLE),
                MERGING to setOf(STABLE, UNRECOVERABLE),
                RECOVERING to setOf(STABLE, DISBANDED, UNRECOVERABLE),
                UNRECOVERABLE to setOf(STABLE),
                DISBANDED to emptySet(),
            )
    }
}

/**
 * Convergence's derived status (`protocol-core/group-state.md`, "Convergence
 * status").
 *
 * Derived from stored input and policy — never a claim made by the transport.
 * The lifecycle state is authoritative; this is a view of how convergence is
 * progressing within it.
 */
enum class ConvergenceStatus {
    /** A bounded pass is collecting selection-relevant input. */
    SYNCING,

    /**
     * The batch is frozen and a deterministic fixed point is being computed
     * over already-retained state. Does NOT wait for fetches or admit later
     * input.
     */
    RESOLVING,

    /**
     * A fixed point was reached and any selected branch applied.
     *
     * Local to the input this client retained and admitted. It is NOT global
     * finality, not proof that transports finished synchronizing, and not a
     * promise that later valid input cannot open another pass.
     */
    SETTLED,

    /** Cannot continue without a repair path, missing material, or resources. */
    BLOCKED,
    ;

    /**
     * Whether this status permits preparing a group-state change or encrypting
     * an app payload.
     *
     * Only `Settled`. Outbound work is held while convergence is unresolved
     * because a payload MUST be encrypted against the SELECTED canonical state
     * — encrypting against a state that later loses branch selection produces
     * a message the group will invalidate.
     */
    val allowsOutboundWork: Boolean get() = this == SETTLED

    fun isLegalIn(lifecycle: GroupLifecycleState): Boolean = lifecycle in LEGAL_COMBINATIONS.getValue(this)

    companion object {
        /**
         * Which lifecycle states each status may appear in.
         *
         * `PendingPublish` and `Merging` appear nowhere: they are local-publish
         * states, not convergence passes, so convergence status is not
         * meaningful in them.
         */
        private val LEGAL_COMBINATIONS: Map<ConvergenceStatus, Set<GroupLifecycleState>> =
            mapOf(
                SYNCING to setOf(GroupLifecycleState.STABLE, GroupLifecycleState.RECOVERING),
                RESOLVING to setOf(GroupLifecycleState.STABLE, GroupLifecycleState.RECOVERING),
                SETTLED to setOf(GroupLifecycleState.STABLE, GroupLifecycleState.DISBANDED),
                BLOCKED to
                    setOf(
                        GroupLifecycleState.STABLE,
                        GroupLifecycleState.RECOVERING,
                        GroupLifecycleState.UNRECOVERABLE,
                    ),
            )
    }
}

/**
 * Durable local gates that restrict outbound work without being canonical
 * lifecycle states.
 *
 * Each survives restart and each is one-way until the protocol event that
 * clears it. They are separate from [GroupLifecycleState] because the MLS group
 * state does not change when they are set — the member is still in the tree.
 */
enum class LocalOutboundGate {
    /**
     * `Leaving`: a SelfRemove proposal was sent. The member is still in the MLS
     * group until a commit removes it, and the gate may span several
     * epoch-bound SelfRemove proposals.
     */
    LEAVING,

    /**
     * `Disbanding`: an admin's irreversible disband request is unresolved. It
     * blocks all new outbound work while the request is prepared, published,
     * retried, or evaluated by convergence, and survives publication failure
     * and restart.
     */
    DISBANDING,

    /**
     * The local member's own removal has been realized. The group is held as a
     * removed, inactive copy: history may be kept, but the group must not be
     * presented as active and nothing may be sent to it. Cleared only by an
     * authenticated re-join.
     */
    REMOVED,
    ;

    val blocksOutbound: Boolean get() = true
}
