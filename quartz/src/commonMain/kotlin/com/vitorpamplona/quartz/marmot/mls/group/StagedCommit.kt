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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.messages.GroupContext
import com.vitorpamplona.quartz.marmot.mls.messages.Proposal
import com.vitorpamplona.quartz.marmot.mls.schedule.EpochSecrets
import com.vitorpamplona.quartz.marmot.mls.schedule.KeyNonceGeneration
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree

/**
 * Snapshot of every mutable field of [MlsGroup] at a single point in time.
 *
 * Used to capture state both before a commit (for rollback) and after a commit
 * (for later merging via [MlsGroup.mergeStagedCommit]).
 *
 * Contains secret key material — treat as sensitive. Do not serialize or log.
 */
internal data class MlsGroupSnapshot(
    val groupContext: GroupContext,
    /** TLS-encoded [com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree] bytes. */
    val treeBytes: ByteArray,
    val myLeafIndex: Int,
    val epochSecrets: EpochSecrets,
    val secretTree: SecretTree,
    val initSecret: ByteArray,
    val signingPrivateKey: ByteArray,
    val encryptionPrivateKey: ByteArray,
    val interimTranscriptHash: ByteArray,
    val pskStore: Map<String, ByteArray>,
    val pendingProposals: List<PendingProposal>,
    val sentKeys: Map<Int, KeyNonceGeneration>,
    val pendingSigningKey: ByteArray?,
    val pendingEncryptionKey: ByteArray?,
    val reInitPending: Proposal.ReInit?,
)

/**
 * Result of staging (but not yet merging) a Commit.
 *
 * Mirrors the MDK / OpenMLS `add_members` → `merge_pending_commit` flow
 * (RFC 9420 §12.4): first the Commit is *computed* from the current group
 * state (which remains at epoch N) so that the outer kind:445 can be
 * ChaCha20-encrypted with the **pre-commit (epoch N)** exporter secret —
 * the key that other existing members still hold. Only after the Commit has
 * been handed to the transport does the local group actually advance to
 * epoch N+1 via [MlsGroup.mergeStagedCommit].
 *
 * [preCommitExporterSecret] is the output of
 * `MLS-Exporter("marmot", "group-event", 32)` at the pre-commit epoch —
 * exactly the key existing members need to outer-decrypt and process the
 * Commit on inbound.
 *
 * [commitBytes] is the raw `Commit` TLS struct. [framedCommitBytes] is the
 * full `MlsMessage(PublicMessage(Commit))` envelope ready for
 * ChaCha20-Poly1305 wrapping; publishers MUST use the framed bytes so that
 * receivers can recover the sender's leaf index and confirmation tag.
 */
@ConsistentCopyVisibility
data class StagedCommit internal constructor(
    /** Pre-commit (epoch N) MLS-Exporter output for outer kind:445 encryption. */
    val preCommitExporterSecret: ByteArray,
    /** Pre-commit epoch (N). */
    val preCommitEpoch: Long,
    /** Post-commit epoch (N+1). */
    val postCommitEpoch: Long,
    /** Raw Commit (RFC 9420 §12.4) bytes, for tests / processCommit. */
    val commitBytes: ByteArray,
    /** MlsMessage(PublicMessage(Commit)) envelope for on-the-wire distribution. */
    val framedCommitBytes: ByteArray,
    /** Welcome message bytes for any members added by this Commit, else null. */
    val welcomeBytes: ByteArray?,
    /** GroupInfo bytes for external joiners, else null. */
    val groupInfoBytes: ByteArray?,
    /** Full post-commit snapshot applied by [MlsGroup.mergeStagedCommit]. */
    internal val postState: MlsGroupSnapshot,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StagedCommit) return false
        return commitBytes.contentEquals(other.commitBytes) &&
            framedCommitBytes.contentEquals(other.framedCommitBytes) &&
            preCommitEpoch == other.preCommitEpoch &&
            postCommitEpoch == other.postCommitEpoch
    }

    override fun hashCode(): Int {
        var result = commitBytes.contentHashCode()
        result = 31 * result + framedCommitBytes.contentHashCode()
        result = 31 * result + preCommitEpoch.hashCode()
        result = 31 * result + postCommitEpoch.hashCode()
        return result
    }
}
