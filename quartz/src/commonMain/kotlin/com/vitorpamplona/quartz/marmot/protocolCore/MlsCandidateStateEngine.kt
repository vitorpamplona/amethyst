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

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * [CandidateStateEngine] backed by real MLS groups.
 *
 * A retained state is an [MlsGroupState] snapshot. Every trial replay restores
 * a FRESH [MlsGroup] from that snapshot and mutates the clone, so a candidate
 * parent survives being tried by several competing commits — which is exactly
 * the situation a fork is.
 *
 * Restoring per attempt is not free, but it is the only shape that is
 * obviously correct here: `processFramedCommit` advances a group in place, and
 * "roll it back afterwards" is the kind of thing that works until the day an
 * exception escapes halfway through.
 */
class MlsCandidateStateEngine : CandidateStateEngine<MlsGroupState> {
    /**
     * Identity of a retained state.
     *
     * `SHA-256` over the serialized GroupContext, NOT the epoch number. Two
     * states can share an epoch number and be different states — that is what a
     * fork is — and the GroupContext covers the tree hash and transcript hash,
     * so it separates them.
     */
    override fun stateId(state: MlsGroupState): String = sha256(state.groupContext.toTlsBytes()).toHexKey()

    override fun epoch(state: MlsGroupState): Long = state.groupContext.epoch

    override fun authenticatesAgainst(
        state: MlsGroupState,
        commit: ByteArray,
    ): Boolean {
        val pubMsg = publicMessageOrNull(commit) ?: return false
        // Cheap rejects first: a mismatched group or epoch cannot possibly
        // authenticate, and skipping the restore keeps a wide retained set
        // from costing a group rebuild per candidate.
        if (!pubMsg.groupId.contentEquals(state.groupContext.groupId)) return false
        if (pubMsg.epoch != state.groupContext.epoch) return false

        return try {
            MlsGroup.restore(state).verifyPublicMessageCommitMembershipTag(pubMsg)
        } catch (_: Exception) {
            false
        }
    }

    override fun replay(
        state: MlsGroupState,
        commit: ByteArray,
    ): MlsGroupState? =
        try {
            // A clone, so the retained snapshot is untouched no matter how this
            // attempt ends.
            val group = MlsGroup.restore(state)
            group.processFramedCommit(commit)
            group.saveState()
        } catch (_: Exception) {
            null
        }

    override fun committerIdentity(
        parent: MlsGroupState,
        commit: ByteArray,
    ): ByteArray? {
        val pubMsg = publicMessageOrNull(commit) ?: return null
        return try {
            MlsGroup.restore(parent).memberIdentity(pubMsg.sender.leafIndex)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Whether the committer may make this change, judged against the PARENT.
     *
     * Delegates to the same gate the local commit path uses, so an inbound
     * commit and one we authored are held to one rule rather than two that
     * drift.
     */
    override fun isAuthorized(
        parent: MlsGroupState,
        commit: ByteArray,
    ): Boolean {
        val pubMsg = publicMessageOrNull(commit) ?: return false
        return try {
            val group = MlsGroup.restore(parent)
            // A group that names no admins yet is bootstrapping; the gate is
            // open there for the same reason the local path leaves it open.
            if (group.currentAdminIdentities().isEmpty()) return true
            group.isCommitAuthorized(pubMsg)
        } catch (_: Exception) {
            false
        }
    }

    override fun tipPriority(
        parent: MlsGroupState,
        commit: ByteArray,
    ): TipPriority {
        val pubMsg = publicMessageOrNull(commit) ?: return TipPriority.ORDINARY
        return try {
            val group = MlsGroup.restore(parent)
            // Privileged exactly when the rule that applies REQUIRES an active
            // admin. A commit a non-admin could also have made is ordinary even
            // when an admin happened to send it.
            if (group.isSelfOnlyCommit(pubMsg)) TipPriority.ORDINARY else TipPriority.PRIVILEGED
        } catch (_: Exception) {
            TipPriority.ORDINARY
        }
    }

    override fun resultingStateIsValid(resulting: MlsGroupState): Boolean =
        try {
            // Reading the component set is the validation: every decoder here
            // is strict, so malformed or unsorted component bytes throw rather
            // than producing a lenient value. An admin policy that named nobody
            // would also fail its own constructor.
            val group = MlsGroup.restore(resulting)
            // Decoding the component set IS the validation: every component
            // decoder is strict, so malformed, unsorted or duplicated bytes
            // throw rather than yielding a lenient value, and an admin policy
            // naming nobody fails its own constructor.
            group.currentGroupState()
            true
        } catch (_: Exception) {
            false
        }

    override fun commitDigest(commit: ByteArray): ByteArray = sha256(commit)

    private fun publicMessageOrNull(commit: ByteArray): PublicMessage? =
        try {
            val message = MlsMessage.decodeTls(TlsReader(commit))
            if (message.wireFormat != WireFormat.PUBLIC_MESSAGE) return null
            val pubMsg = PublicMessage.decodeTls(TlsReader(message.payload))
            if (pubMsg.contentType != ContentType.COMMIT) null else pubMsg
        } catch (_: Exception) {
            null
        }
}
