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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.unwrapAndUnsealOrNull
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.MarmotInboundProcessor
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Outcome of routing a single inbound Nostr event through the Marmot pipeline.
 *
 * Drives both Amethyst's `DecryptAndIndexProcessor.GroupEventHandler` and the
 * CLI's `Context.syncIncoming`. Platform-specific side effects (UI notifications,
 * "mark as known", cache hints) happen at the call site based on which variant
 * came back; the ingest method handles only the MLS/crypto parts that are the
 * same everywhere.
 */
sealed class MarmotIngestResult {
    /** A Welcome arrived and we successfully joined a new group. */
    data class JoinedGroup(
        val nostrGroupId: HexKey,
        val needsKeyPackageRotation: Boolean,
    ) : MarmotIngestResult()

    /** A Welcome for a group we're already in — benign replay. */
    data class AlreadyInGroup(
        val nostrGroupId: HexKey,
    ) : MarmotIngestResult()

    /** A kind:445 carried an application message we decrypted. Already persisted. */
    data class Message(
        val inner: GroupEventResult.ApplicationMessage,
    ) : MarmotIngestResult()

    /** A kind:445 carried a commit that advanced the group epoch. */
    data class Commit(
        val inner: GroupEventResult.CommitProcessed,
    ) : MarmotIngestResult()

    /**
     * A kind:445 carried a standalone Proposal (currently only `SelfRemove`)
     * which was staged in the group's pending pool. The group epoch did
     * not advance — a later Commit referencing this proposal will pick
     * it up.
     */
    data class ProposalStaged(
        val groupId: HexKey,
        val senderLeafIndex: Int,
    ) : MarmotIngestResult()

    /** A kind:445 whose outer layer we couldn't decrypt (pre-join epoch, etc). Debug-only. */
    data class UndecryptableOuter(
        val groupId: HexKey,
        val retainedEpochCount: Int,
    ) : MarmotIngestResult()

    /**
     * Deduplicate / out-of-order commits / unsupported content. Not an error.
     *
     * [reason] names the branch that produced it. Several very different
     * situations land here — a replayed event we already merged, a commit held
     * for convergence, an app message decrypted on a losing branch, traffic in
     * a group we have terminalized — and collapsing them into one unlabelled
     * result made a stuck client indistinguishable from a quiet one in the
     * logs.
     */
    data class Ignored(
        val reason: String,
    ) : MarmotIngestResult()

    /** Something blew up. Callers log. */
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : MarmotIngestResult()
}

/**
 * Route a single event through the Marmot inbound pipeline.
 *
 * Kind 1059 (gift wraps): unwrap → if inner kind:444, process Welcome.
 * Kind 445 (group events): decrypt + process; on [GroupEventResult.ApplicationMessage],
 * persist the decrypted inner JSON so [MarmotManager.loadStoredMessages] sees it.
 *
 * Other kinds are returned as [MarmotIngestResult.Ignored] — callers decide
 * what to do with them (most route through their own feed ingestion).
 */
suspend fun MarmotManager.ingest(event: Event): MarmotIngestResult =
    when (event) {
        is GiftWrapEvent -> ingestGiftWrap(event)
        is GroupEvent -> ingestGroupEvent(event)
        else -> MarmotIngestResult.Ignored("unhandled kind ${event.kind}")
    }

private suspend fun MarmotManager.ingestGiftWrap(wrap: GiftWrapEvent): MarmotIngestResult {
    // A relay `since` cursor cannot skip a backdated event, and NIP-59 wraps
    // are backdated by up to two days on purpose — so without a durable marker
    // every wrap in that band is unwrapped and re-decided on every single sync.
    if (isTerminallyIngested(wrap.id)) return MarmotIngestResult.Ignored("already ingested")
    val result = ingestGiftWrapUncached(wrap)
    when (result) {
        // Joined, or already in the group: nothing more can come of this wrap.
        is MarmotIngestResult.JoinedGroup, is MarmotIngestResult.AlreadyInGroup -> markTerminallyIngested(wrap.id)

        // A Welcome naming a KeyPackage whose private half we never held can
        // never become processable: bundles are generated locally BEFORE the
        // KeyPackage is published, so one we do not have is one we never will.
        is MarmotIngestResult.Failure ->
            if (result.message.contains("No matching KeyPackageBundle")) markTerminallyIngested(wrap.id)

        else -> Unit
    }
    return result
}

private suspend fun MarmotManager.ingestGiftWrapUncached(wrap: GiftWrapEvent): MarmotIngestResult =
    try {
        // NIP-59 wraps carry two encryption layers (kind:1059 → kind:13 → rumor).
        // [unwrapAndUnsealOrNull] peels both so we land directly on the inner
        // kind:444 Welcome rumor. Checking `isWelcomeEvent` on the seal itself
        // (the old bug) always took the Ignored branch and silently dropped
        // every inbound Welcome.
        val rumor = wrap.unwrapAndUnsealOrNull(signer) ?: return MarmotIngestResult.Ignored("gift wrap is not for us")
        if (!MarmotInboundProcessor.isWelcomeEvent(rumor) || rumor !is WelcomeEvent) {
            return MarmotIngestResult.Ignored("gift wrap does not carry a Welcome")
        }
        when (val result = processWelcome(rumor, rumor.nostrGroupId())) {
            is WelcomeResult.Joined -> {
                // Establish the baseline for this group's system rows without
                // writing any: a joiner announcing every existing member as
                // newly added would be a timeline full of events that never
                // happened.
                recordRetentionForCurrentEpoch(result.nostrGroupId)
                syncGroupSystemRows(result.nostrGroupId)
                MarmotIngestResult.JoinedGroup(
                    nostrGroupId = result.nostrGroupId,
                    needsKeyPackageRotation = result.needsKeyPackageRotation,
                )
            }

            is WelcomeResult.AlreadyJoined -> {
                MarmotIngestResult.AlreadyInGroup(result.nostrGroupId)
            }

            is WelcomeResult.Error -> {
                MarmotIngestResult.Failure(result.message, result.cause)
            }
        }
    } catch (e: Exception) {
        MarmotIngestResult.Failure("giftwrap unwrap failed: ${e.message}", e)
    }

private suspend fun MarmotManager.ingestGroupEvent(ge: GroupEvent): MarmotIngestResult =
    when (val result = processGroupEvent(ge)) {
        is GroupEventResult.ApplicationMessage -> {
            // MLS ratchets once we decrypt; future reads of the same ciphertext
            // would fail — persist the plaintext now so restarts/replays see it.
            persistDecryptedMessage(result.groupId, result.innerEventJson, result.epoch)
            // Traffic is the natural clock for expiry: a group that is being
            // read is a group whose expired messages should already be gone.
            pruneExpiredMessages(result.groupId)
            MarmotIngestResult.Message(result)
        }

        is GroupEventResult.CommitProcessed -> {
            // The epoch just advanced, so whatever this commit changed about
            // the group is now canonical state — which is exactly what a
            // kind:1210 row is derived from. Deriving here rather than at
            // render time means the rows land in the same log as the messages
            // they sit between, in the order they happened.
            recordRetentionForCurrentEpoch(result.groupId)
            syncGroupSystemRows(result.groupId)
            MarmotIngestResult.Commit(result)
        }

        is GroupEventResult.ProposalStaged -> {
            MarmotIngestResult.ProposalStaged(result.groupId, result.senderLeafIndex)
        }

        is GroupEventResult.Duplicate -> MarmotIngestResult.Ignored("already merged")

        // Held, not dropped: a commit we cannot advance onto linearly is
        // candidate material for a convergence pass that has to settle before
        // it can be applied. Saying so matters — this is the one Ignored that
        // means "come back", and a client that never settles repeats it
        // forever while looking idle.
        is GroupEventResult.CommitPending -> MarmotIngestResult.Ignored("commit held for convergence")

        // Decrypted only on a losing branch: real protocol input (it may have
        // witnessed for that branch), but never application output.
        is GroupEventResult.AppMessageOnCandidateBranch ->
            MarmotIngestResult.Ignored("app message on a candidate branch")

        // Disbanded or locally unrecoverable — refused before decryption, so
        // there is nothing to deliver and nothing to retain.
        is GroupEventResult.RefusedByLifecycle ->
            MarmotIngestResult.Ignored("refused by lifecycle ${result.lifecycle}")

        is GroupEventResult.UndecryptableOuterLayer -> {
            MarmotIngestResult.UndecryptableOuter(result.groupId, result.retainedEpochCount)
        }

        is GroupEventResult.Error -> {
            MarmotIngestResult.Failure(result.message, result.cause)
        }
    }
