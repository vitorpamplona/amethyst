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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.marmot.protocolCore.ConvergenceAdmission
import com.vitorpamplona.quartz.marmot.protocolCore.ConvergenceResolution
import com.vitorpamplona.quartz.marmot.protocolCore.ConvergenceStatus
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotConvergenceEngine
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Result of processing an inbound GroupEvent (kind:445).
 */
sealed class GroupEventResult {
    /**
     * An application message was decrypted successfully.
     * The [innerEventJson] contains the raw JSON of the inner Nostr event
     * (e.g., kind:9 chat, kind:7 reaction).
     */
    data class ApplicationMessage(
        val groupId: HexKey,
        val innerEventJson: String,
        val senderLeafIndex: Int,
        val epoch: Long,
    ) : GroupEventResult()

    /**
     * A Commit was processed, advancing the group epoch.
     */
    data class CommitProcessed(
        val groupId: HexKey,
        val newEpoch: Long,
    ) : GroupEventResult()

    /**
     * A Commit was received but is pending conflict resolution.
     * Multiple commits arrived for the same epoch.
     */
    data class CommitPending(
        val groupId: HexKey,
        val epoch: Long,
        /** True when this commit forked the group and opened a convergence pass. */
        val forkDetected: Boolean = false,
    ) : GroupEventResult()

    /**
     * The event was already processed (duplicate).
     */
    data class Duplicate(
        val groupId: HexKey,
    ) : GroupEventResult()

    /**
     * The outer ChaCha20-Poly1305 layer could not be decrypted with the
     * current-epoch exporter key or any retained prior-epoch key.
     *
     * This is the expected outcome whenever a member receives a kind:445
     * from an epoch before they joined (via Welcome). Per MLS forward
     * secrecy, the new member never held those keys, so the bytes are
     * unreadable to them — and that is by design, not an error. Callers
     * should surface this at DEBUG, not WARN.
     */
    data class UndecryptableOuterLayer(
        val groupId: HexKey,
        val retainedEpochCount: Int,
    ) : GroupEventResult()

    /**
     * A standalone Proposal (currently only `SelfRemove`) was decoded,
     * verified, and staged in the group's pending-proposals pool. The
     * group epoch did not advance — the proposal is dormant until a
     * subsequent Commit references it by hash.
     */
    data class ProposalStaged(
        val groupId: HexKey,
        val senderLeafIndex: Int,
    ) : GroupEventResult()

    /**
     * An app payload that decrypted only on a losing candidate branch.
     *
     * `protocol-core/inbound-processing.md` is explicit that this is NOT a
     * delivery — rendering it would show the application a message the
     * canonical state contradicts. It is still protocol input: if it passed
     * the payload checks it counted as an app-payload witness for the branch
     * it decrypted on, which is how convergence can prefer a branch members
     * actually used.
     */
    data class AppMessageOnCandidateBranch(
        val groupId: HexKey,
        val branchStateId: String,
        val epoch: Long,
        /** True when it passed the payload checks and was counted as a witness. */
        val countedAsWitness: Boolean,
    ) : GroupEventResult()

    /**
     * The event could not be processed.
     */
    data class Error(
        val groupId: HexKey?,
        val message: String,
        val cause: Exception? = null,
    ) : GroupEventResult()
}

/**
 * Result of processing a Welcome message (kind:444 inside kind:1059).
 */
sealed class WelcomeResult {
    /**
     * Successfully joined a group via Welcome.
     */
    data class Joined(
        val nostrGroupId: HexKey,
        val needsKeyPackageRotation: Boolean,
    ) : WelcomeResult()

    /**
     * The Welcome was for a group we're already a member of — benign replay.
     *
     * Happens after an app restart: the gift-wrapped Welcome (kind:1059) is
     * still sitting on the relay and gets redelivered, but the KeyPackage
     * bundle it referenced was already consumed and marked. Rather than
     * logging a noisy "No matching KeyPackageBundle" error, we detect the
     * replay up front by checking `groupManager.isMember(hintNostrGroupId)`
     * and return this result. Callers should log at DEBUG.
     */
    data class AlreadyJoined(
        val nostrGroupId: HexKey,
    ) : WelcomeResult()

    /**
     * The Welcome could not be processed.
     */
    data class Error(
        val message: String,
        val cause: Exception? = null,
    ) : WelcomeResult()
}

/**
 * Processes inbound Marmot events from relays.
 *
 * Handles:
 * - **GroupEvent (kind:445):** Outer ChaCha20 decrypt → MLS decrypt →
 *   extract inner Nostr event or process Commit
 * - **WelcomeEvent (kind:444):** After NIP-59 unwrap reveals a kind:444,
 *   extract welcome bytes and join the group via MlsGroupManager
 *
 * This class coordinates between [GroupEventEncryption] (outer layer),
 * [MlsGroupManager] (MLS engine), and [MarmotConvergenceEngine] (fork
 * resolution).
 *
 * ## Same-epoch conflicts
 *
 * Competing commits are resolved by `protocol-core/convergence.md`: a bounded
 * pass collects candidates, branches are built by replaying MLS bytes against
 * retained states, and the six-step comparison picks one. The superseded
 * MIP-era rule — lowest outer `created_at`, then lowest Nostr event id — is
 * gone, and deliberately: both are transport metadata the sender chooses and
 * MLS does not authenticate, so a member could win every race by backdating.
 */
class MarmotInboundProcessor(
    private val groupManager: MlsGroupManager,
    private val keyPackageRotationManager: KeyPackageRotationManager,
    private val convergence: MarmotConvergenceEngine = MarmotConvergenceEngine(groupManager),
) {
    private val processedIdsMutex = Mutex()

    /**
     * Marmot message ids (`SHA-256` over the recovered `MLSMessage` bytes) we
     * have already applied, newest last.
     *
     * NOT Nostr event ids. `transports/nostr.md` is explicit: the Nostr event
     * id is transport evidence and MUST NOT be the deduplication id. Relays
     * redeliver, and a client subscribed to several relays receives the same
     * group message repeatedly — those copies share MLS bytes but each carries
     * its own fresh ephemeral pubkey and therefore a different event id, so an
     * event-id dedup collapses nothing. Worse, a hostile republisher can mint
     * unlimited distinct event ids for one MLS message.
     */
    private val processedMessageIds = LinkedHashSet<String>()

    companion object {
        private const val MAX_PROCESSED_IDS = 10_000

        /**
         * Check if an unwrapped event is a Marmot WelcomeEvent.
         */
        fun isWelcomeEvent(event: Event): Boolean = event.kind == WelcomeEvent.KIND
    }

    /**
     * `message_id = SHA-256(mls_message_bytes)` — the Marmot message id from
     * `foundation/wire-envelopes.md`, computed over the recovered bytes
     * without re-encoding so two transport copies of one MLS message agree.
     *
     * For a commit these are byte-for-byte its `commit_digest`, so convergence
     * needs no second hash.
     */
    private fun marmotMessageId(mlsBytes: ByteArray): String = sha256(mlsBytes).toHexKey()

    /**
     * Process an inbound GroupEvent (kind:445).
     *
     * Flow:
     * 1. Extract group ID from `h` tag
     * 2. Get the exporter key from MlsGroupManager
     * 3. Decrypt outer ChaCha20-Poly1305 layer → raw MLS bytes
     * 4. Parse the MLS message to determine type:
     *    - PrivateMessage with APPLICATION content → MLS decrypt → return inner event
     *    - PrivateMessage/PublicMessage with COMMIT content → process commit
     *    - PrivateMessage/PublicMessage with PROPOSAL content → queue proposal
     *
     * @param groupEvent the incoming kind:445 event
     * @return the processing result
     */
    suspend fun processGroupEvent(groupEvent: GroupEvent): GroupEventResult {
        val groupId =
            groupEvent.groupId()
                ?: return GroupEventResult.Error(null, "GroupEvent missing h tag (group ID)")

        if (!groupManager.isMember(groupId)) {
            return GroupEventResult.Error(groupId, "Not a member of group $groupId")
        }

        // Settle FIRST, so this event is processed against resolved state
        // rather than against a branch a pass is about to abandon. Inbound
        // traffic is only an opportunistic tick, though: a group that goes
        // quiet mid-pass has nothing to drive it, which is why
        // [settleDueConvergence] exists for the app layer's timer.
        convergence.settleIfDue(groupId)

        var messageId: String? = null
        val result =
            try {
                // Step 1: Outer ChaCha20-Poly1305 decryption
                val mlsBytes = tryDecryptOuterLayer(groupId, groupEvent.encryptedContent())
                if (mlsBytes == null) {
                    // Expected when this kind:445 was encrypted with an epoch
                    // key that predates our join (classical MLS forward
                    // secrecy), or when the sender's epoch has drifted. Not
                    // an error — callers should log at DEBUG.
                    GroupEventResult.UndecryptableOuterLayer(
                        groupId,
                        retainedEpochCount = groupManager.retainedExporterSecrets(groupId).size,
                    )
                } else {
                    // The Marmot message id is defined over the recovered MLS
                    // bytes, so dedup can only happen AFTER outer decryption —
                    // there is nothing to hash before that.
                    messageId = marmotMessageId(mlsBytes)
                    if (processedIdsMutex.withLock { messageId in processedMessageIds }) {
                        GroupEventResult.Duplicate(groupId)
                    } else {
                        // Step 2: Parse the MLS message
                        val mlsMessage = MlsMessage.decodeTls(TlsReader(mlsBytes))

                        when (mlsMessage.wireFormat) {
                            WireFormat.PRIVATE_MESSAGE -> processPrivateMessage(groupId, mlsMessage, groupEvent)
                            WireFormat.PUBLIC_MESSAGE -> processPublicMessage(groupId, mlsMessage, groupEvent)
                            else -> GroupEventResult.Error(groupId, "Unexpected wire format: ${mlsMessage.wireFormat}")
                        }
                    }
                }
            } catch (e: Exception) {
                GroupEventResult.Error(groupId, "Failed to process GroupEvent: ${e.message}", e)
            }

        // Track processed events for dedup — except UndecryptableOuterLayer,
        // which must stay retryable. These events are typically future-epoch
        // arrivals buffered by the handler and replayed after a
        // CommitProcessed advances our epoch; marking them processed here
        // would cause the retry to hit the Duplicate early-return above and
        // skip MLS decryption entirely. DoS is already bounded by the
        // handler's per-group pending buffer.
        val idToRemember = messageId
        if (idToRemember != null && result !is GroupEventResult.UndecryptableOuterLayer) {
            processedIdsMutex.withLock {
                processedMessageIds.add(idToRemember)
                // Trim the set if it exceeds the max size
                if (processedMessageIds.size > MAX_PROCESSED_IDS) {
                    val iterator = processedMessageIds.iterator()
                    val toRemove = processedMessageIds.size - MAX_PROCESSED_IDS
                    repeat(toRemove) {
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
        }

        return result
    }

    /**
     * Process a WelcomeEvent after NIP-59 gift wrap unwrapping.
     *
     * Called by the platform layer after unwrapping a GiftWrap → SealedRumor → WelcomeEvent.
     *
     * Flow:
     * 1. Extract welcome bytes and KeyPackage event ID
     * 2. Find the matching KeyPackageBundle
     * 3. Call MlsGroupManager.processWelcome()
     * 4. Mark KeyPackage as consumed for rotation
     *
     * @param welcomeEvent the unwrapped kind:444 event
     * @param hintNostrGroupId optional group ID from the "h" tag; validated against MLS content
     *   if provided. If absent (sender omitted "h" tag), the ID is derived from the Welcome's
     *   NostrGroupData extension — the MLS content is always the authoritative source.
     * @return the processing result
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun processWelcome(
        welcomeEvent: WelcomeEvent,
        hintNostrGroupId: HexKey? = null,
    ): WelcomeResult =
        try {
            com.vitorpamplona.quartz.utils.Log
                .d("MarmotDbg") {
                    "MarmotInboundProcessor.processWelcome: hint=${hintNostrGroupId?.take(8)} eventId=${welcomeEvent.id.take(8)}…"
                }

            val welcomeBytes = Base64.decode(welcomeEvent.welcomeBase64())
            val keyPackageEventId = welcomeEvent.keyPackageEventId()
            if (keyPackageEventId == null) {
                return WelcomeResult.Error("WelcomeEvent missing KeyPackage event ID tag")
            }
            com.vitorpamplona.quartz.utils.Log
                .d("MarmotDbg") {
                    "MarmotInboundProcessor.processWelcome: welcomeBytes=${welcomeBytes.size}B looking up KeyPackage by ref=${keyPackageEventId.take(8)}…"
                }

            // Short-circuit if we're already a member of the group the
            // Welcome is inviting us to. Happens every time the app
            // restarts: the gift-wrapped kind:1059 is still on the relay
            // and gets redelivered, but the KeyPackage bundle it
            // referenced was already consumed + marked during the first
            // processing. Without this check the fallthrough below would
            // log a noisy "No matching KeyPackageBundle" warning for what
            // is actually a benign replay.
            if (hintNostrGroupId != null && groupManager.isMember(hintNostrGroupId)) {
                com.vitorpamplona.quartz.utils.Log
                    .d("MarmotDbg") {
                        "MarmotInboundProcessor.processWelcome: already a member of group=${hintNostrGroupId.take(8)}… — treating Welcome as replay"
                    }
                return WelcomeResult.AlreadyJoined(hintNostrGroupId)
            }

            // Find the KeyPackageBundle that was consumed.
            //
            // The Welcome's "e" tag carries the *Nostr event id* of the
            // kind:30443 event (NOT the MLS reference hash), so we must
            // resolve it via the eventId→slot index that
            // [MarmotManager.generateKeyPackageEvent] populates after
            // signing each KeyPackageEvent.
            val bundle = keyPackageRotationManager.findBundleByEventId(keyPackageEventId)
            if (bundle == null) {
                com.vitorpamplona.quartz.utils.Log
                    .w("MarmotDbg") {
                        "MarmotInboundProcessor.processWelcome: NO matching KeyPackageBundle for eventId=${keyPackageEventId.take(8)}… " +
                            "— inviter referenced a KeyPackage we don't have private keys for. " +
                            "Either the bundle was generated in a previous session and never persisted, " +
                            "or this account never published this KeyPackage."
                    }
                return WelcomeResult.Error(
                    "No matching KeyPackageBundle found for event $keyPackageEventId",
                )
            }
            com.vitorpamplona.quartz.utils.Log
                .d("MarmotDbg") { "MarmotInboundProcessor.processWelcome: bundle found — invoking groupManager.processWelcome" }

            // Join the group; nostrGroupId is derived from the MLS GroupContext's
            // NostrGroupData extension. The h-tag hint (if any) is validated inside.
            val (_, nostrGroupId) = groupManager.processWelcome(welcomeBytes, bundle, hintNostrGroupId)
            com.vitorpamplona.quartz.utils.Log
                .d("MarmotDbg") { "MarmotInboundProcessor.processWelcome: joined group=${nostrGroupId.take(8)}…" }

            // Mark the KeyPackage as consumed — triggers rotation
            keyPackageRotationManager.markConsumedByEventId(keyPackageEventId)

            // Seed convergence with the joined state, so the very first inbound
            // commit already has a retained parent to fall back to.
            convergence.trackGroup(nostrGroupId)

            WelcomeResult.Joined(
                nostrGroupId = nostrGroupId,
                needsKeyPackageRotation = keyPackageRotationManager.needsRotation(),
            )
        } catch (e: Exception) {
            com.vitorpamplona.quartz.utils.Log
                .w("MarmotDbg", "MarmotInboundProcessor.processWelcome: exception ${e.message}", e)
            WelcomeResult.Error("Failed to process Welcome: ${e.message}", e)
        }

    /**
     * Mark a kind:445 event id as already processed so that a later relay
     * echo of the same event is treated as a [GroupEventResult.Duplicate]
     * instead of being re-applied.
     *
     * Callers should invoke this right after publishing a commit (e.g. from
     * [com.vitorpamplona.amethyst.commons.marmot.MarmotManager.addMember])
     * because `group.addMember` / `group.commit` have already advanced the
     * local epoch. Reprocessing the same commit bytes would otherwise fail
     * with a confirmation-tag / transcript mismatch.
     */
    suspend fun markMessageProcessed(marmotMessageId: HexKey) {
        processedIdsMutex.withLock {
            processedMessageIds.add(marmotMessageId)
            if (processedMessageIds.size > MAX_PROCESSED_IDS) {
                val iterator = processedMessageIds.iterator()
                val toRemove = processedMessageIds.size - MAX_PROCESSED_IDS
                repeat(toRemove) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Start tracking [groupId] for convergence.
     *
     * Call after creating, joining, or restoring a group. The engine needs the
     * current state in its retained window before the first commit arrives —
     * a commit that loses a race is only recoverable if the state it was
     * authored against is still held.
     */
    suspend fun trackGroup(groupId: HexKey) {
        convergence.trackGroup(groupId)
    }

    /**
     * Record a commit WE authored and already applied locally.
     *
     * Convergence has to see our own commits or it cannot resolve a fork we are
     * half of: with no retained parent for our commit, a peer's competing one
     * would look like an unplaceable orphan and be deferred forever instead of
     * compared. [preState] must be captured BEFORE the local commit advanced
     * the group — the outbound helper cannot recover it afterwards.
     */
    suspend fun recordLocalCommit(
        groupId: HexKey,
        framedCommitBytes: ByteArray,
        sourceEpoch: Long,
        preState: MlsGroupState?,
    ) {
        convergence.recordApplied(groupId, framedCommitBytes, sourceEpoch, preState)
    }

    /**
     * Resolve [groupId]'s open convergence pass now, without waiting for its
     * cutoff.
     *
     * The pass timers are scheduling, not semantics, so closing one early
     * changes WHEN the frozen batch is resolved and never what it resolves to.
     * Returns null when no pass is open.
     */
    suspend fun resolveConvergence(groupId: HexKey): ConvergenceResolution? = convergence.settle(groupId)

    /**
     * Resolve every group whose convergence pass has reached its cutoff.
     *
     * A quiet group settles nothing on its own — there is no inbound traffic to
     * carry it — so the app layer should also drive this from a timer for as
     * long as [openConvergencePasses] is non-empty.
     */
    suspend fun settleDueConvergence(): List<ConvergenceResolution> = convergence.settleAllDue()

    /** Groups with an open convergence pass, and the base epoch each snapshotted. */
    suspend fun openConvergencePasses(): Map<HexKey, Long> = convergence.openPasses()

    /** Convergence status for [groupId]; `SETTLED` when no pass is running. */
    suspend fun convergenceStatus(groupId: HexKey): ConvergenceStatus = convergence.status(groupId)

    /** Group lifecycle state for [groupId], including a running pass's `Recovering`. */
    suspend fun groupLifecycle(groupId: HexKey): GroupLifecycleState = convergence.lifecycle(groupId)

    /** Drop all convergence state. */
    suspend fun clearPendingCommits() {
        convergence.clear()
    }

    private suspend fun processPrivateMessage(
        groupId: HexKey,
        mlsMessage: MlsMessage,
        groupEvent: GroupEvent,
    ): GroupEventResult {
        // Peek at content type from the PrivateMessage header
        val privMsg = PrivateMessage.decodeTls(TlsReader(mlsMessage.payload))

        return when (privMsg.contentType) {
            ContentType.APPLICATION -> {
                val bytes = mlsMessage.toTlsBytes()
                val decrypted = groupManager.decryptOrNull(groupId, bytes)
                if (decrypted == null) {
                    // Canonical state and every retained canonical epoch
                    // failed. Before giving up, try the branches convergence is
                    // holding — a payload sent on a fork decrypts on no
                    // canonical epoch by construction.
                    processCandidateBranchMessage(groupId, bytes)
                } else {
                    val payload = decrypted.content.decodeToString()
                    val author = payloadAuthor(payload)

                    // `foundation/application-messages.md`, "Receiver
                    // authentication": the inner author MUST equal the account
                    // the MLS sender leaf authenticates. Without it any member
                    // could mint messages attributed to anyone else in the
                    // group. Payloads with no author field at all (raw bytes
                    // via buildGroupEventFromBytes) have nothing to compare.
                    val senderIdentity = groupManager.memberIdentityHex(groupId, decrypted.senderLeafIndex)
                    if (author != null && (senderIdentity == null || author != senderIdentity)) {
                        return GroupEventResult.Error(
                            groupId,
                            "inner event pubkey ($author) does not match MLS sender identity ($senderIdentity)",
                        )
                    }

                    // A payload that passed the checks is an app-payload
                    // witness for the canonical branch at its epoch. The
                    // incumbent is rebuilt and rescored at every resolution, so
                    // counting only divergent branches would let any fork win
                    // the witness steps unopposed.
                    if (author != null && senderIdentity != null) {
                        convergence.recordCanonicalWitness(groupId, decrypted.epoch, senderIdentity)
                    }

                    GroupEventResult.ApplicationMessage(
                        groupId = groupId,
                        innerEventJson = asEventShapedJson(payload),
                        senderLeafIndex = decrypted.senderLeafIndex,
                        epoch = decrypted.epoch,
                    )
                }
            }

            ContentType.COMMIT -> {
                handleCommitEvent(groupId, groupEvent)
            }

            ContentType.PROPOSAL -> {
                GroupEventResult.Error(groupId, "Standalone proposals not yet supported")
            }
        }
    }

    private suspend fun processPublicMessage(
        groupId: HexKey,
        mlsMessage: MlsMessage,
        groupEvent: GroupEvent,
    ): GroupEventResult {
        val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))

        return when (pubMsg.contentType) {
            ContentType.COMMIT -> {
                handleCommitEvent(groupId, groupEvent)
            }

            ContentType.PROPOSAL -> {
                // wn/openmls publishes SelfRemove as a standalone PublicMessage
                // proposal — admins fold it into their next commit. Stage it
                // locally so a subsequent commit's `ProposalRef` can resolve;
                // without this every other member silently dropped the
                // proposal and the admin's commit then failed with "Commit
                // references unknown proposal" (marmot-interop test 15).
                val group =
                    groupManager.getGroup(groupId)
                        ?: return GroupEventResult.Error(groupId, "Group not found")
                try {
                    group.receivePublicMessageProposal(pubMsg)
                    GroupEventResult.ProposalStaged(groupId, pubMsg.sender.leafIndex)
                } catch (e: Exception) {
                    GroupEventResult.Error(
                        groupId,
                        "Failed to stage standalone proposal: ${e.message}",
                        e,
                    )
                }
            }

            ContentType.APPLICATION -> {
                GroupEventResult.Error(groupId, "Application messages should use PrivateMessage")
            }
        }
    }

    /**
     * The account a payload claims as its author, or null when it has none.
     *
     * The canonical shape is tried first, because that is what a conformant
     * peer sends and its checks are the strict ones. The legacy fall-back
     * exists only for payloads this client itself wrote before the switch to
     * the unsigned shape: those carry a `sig` member, which the strict decoder
     * refuses by design. It is deliberately not a general "accept anything"
     * path — a payload that is neither shape still has no author, and still
     * fails the comparison rather than passing it.
     */
    private fun payloadAuthor(payload: String): HexKey? =
        MarmotAppEvent.decodeOrNull(payload)?.pubKey
            ?: Event.fromJsonOrNull(payload)?.pubKey

    /**
     * Re-shape a canonical payload into the Event-shaped JSON the app layer
     * consumes.
     *
     * The application pipeline is built around `Event`, which requires a `sig`
     * member; the wire form must not carry one. Rather than force every
     * consumer to learn a second shape, the empty signature is re-added here at
     * the boundary. The id is unaffected either way — NIP-01 never hashed the
     * signature — so a message keeps one identity across the conversion.
     */
    private fun asEventShapedJson(payload: String): String {
        val appEvent = MarmotAppEvent.decodeOrNull(payload) ?: return payload
        return appEvent.toJson().dropLast(1) + ",\"sig\":\"\"}"
    }

    /**
     * Try an app message against the retained candidate branches.
     *
     * A payload that decrypts here is NOT delivered: it belongs to a branch
     * that is not canonical, and handing it to the application would render a
     * message the canonical state contradicts. What it can do is witness for
     * that branch — but only if it passes the SAME payload checks a delivered
     * one does. Decryption alone is not a witness; without the author check a
     * single member could forge many distinct sender identities and buy a
     * branch the witness quorum outright.
     */
    private suspend fun processCandidateBranchMessage(
        groupId: HexKey,
        mlsBytes: ByteArray,
    ): GroupEventResult {
        val candidate =
            convergence.tryCandidateDecrypt(groupId, mlsBytes)
                ?: return GroupEventResult.Error(
                    groupId,
                    "Application message decrypts on no canonical epoch or retained candidate branch",
                )

        val author = payloadAuthor(candidate.content.decodeToString())
        val sender = candidate.senderAccount
        val valid = author != null && sender != null && author == sender
        if (valid && sender != null) {
            convergence.recordWitness(groupId, candidate.stateId, sender)
        }
        return GroupEventResult.AppMessageOnCandidateBranch(
            groupId = groupId,
            branchStateId = candidate.stateId,
            epoch = candidate.epoch,
            countedAsWitness = valid,
        )
    }

    /**
     * Apply an inbound commit, letting convergence decide anything ambiguous.
     *
     * Commits that extend the current tip apply straight away rather than being
     * held for a pass. That is not a shortcut past convergence: the state the
     * commit was applied to is retained, so a competitor authored against the
     * same parent is still recoverable afterwards — it simply stops
     * authenticating against the new tip, which is precisely how the fork is
     * detected. Holding every commit for the quiescence window instead would
     * tax the overwhelmingly common single-commit case with a second of
     * latency and change no outcome.
     */
    private suspend fun handleCommitEvent(
        groupId: HexKey,
        groupEvent: GroupEvent,
    ): GroupEventResult {
        if (groupManager.getGroup(groupId) == null) {
            return GroupEventResult.Error(groupId, "Group not found")
        }
        return applyCommit(groupId, groupEvent)
    }

    private suspend fun applyCommit(
        groupId: HexKey,
        commitEvent: GroupEvent,
    ): GroupEventResult =
        try {
            val mlsBytes =
                tryDecryptOuterLayer(groupId, commitEvent.encryptedContent())
                    ?: return GroupEventResult.UndecryptableOuterLayer(
                        groupId,
                        retainedEpochCount = groupManager.retainedExporterSecrets(groupId).size,
                    )
            val mlsMessage = MlsMessage.decodeTls(TlsReader(mlsBytes))

            when (mlsMessage.wireFormat) {
                WireFormat.PRIVATE_MESSAGE -> {
                    // Sniff the PrivateMessage epoch without consuming any
                    // ratchet state. Past-epoch echoes and future-epoch
                    // arrivals must not advance the secret tree — otherwise
                    // the real handshake / application message gets rejected
                    // when it finally arrives.
                    val privPeek = PrivateMessage.decodeTls(TlsReader(mlsMessage.payload))
                    val currentEpoch = groupManager.getGroup(groupId)?.epoch
                    when {
                        currentEpoch != null && privPeek.epoch < currentEpoch -> {
                            GroupEventResult.Duplicate(groupId)
                        }

                        currentEpoch != null && privPeek.epoch > currentEpoch -> {
                            GroupEventResult.Error(
                                groupId,
                                "PrivateMessage epoch ${privPeek.epoch} is ahead of local epoch $currentEpoch; ignoring",
                            )
                        }

                        else -> {
                            val decrypted = groupManager.decrypt(groupId, mlsMessage.toTlsBytes())
                            if (decrypted.contentType == ContentType.COMMIT) {
                                val group = groupManager.getGroup(groupId)
                                GroupEventResult.CommitProcessed(groupId, group?.epoch ?: 0)
                            } else {
                                GroupEventResult.Error(
                                    groupId,
                                    "Expected COMMIT but got ${decrypted.contentType}",
                                )
                            }
                        }
                    }
                }

                WireFormat.PUBLIC_MESSAGE -> {
                    val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))
                    val tag = pubMsg.confirmationTag
                    val currentEpoch = groupManager.getGroup(groupId)?.epoch
                    when {
                        tag == null -> {
                            GroupEventResult.Error(groupId, "PublicMessage commit missing confirmation_tag")
                        }

                        // A commit for an epoch we already left is either an
                        // echo of something applied, or the losing half of a
                        // same-epoch race. Convergence tells them apart by
                        // asking whether any RETAINED state authenticates it:
                        // an echo authenticates nothing (we consumed its
                        // parent), a competitor authenticates the parent we
                        // still hold.
                        //
                        // Either way it must not go to `processCommit`, which
                        // partially mutates tree / groupContext / epochSecrets
                        // before throwing on the confirmation-tag check and
                        // leaves local state diverged from every other
                        // member's. The fork is replayed against a CLONE of
                        // the retained state instead, so the live group is
                        // never touched until selection has decided.
                        currentEpoch != null && pubMsg.epoch < currentEpoch -> {
                            when (convergence.offerDivergent(groupId, mlsBytes, pubMsg.epoch)) {
                                ConvergenceAdmission.ADMITTED ->
                                    GroupEventResult.CommitPending(groupId, pubMsg.epoch, forkDetected = true)

                                ConvergenceAdmission.DUPLICATE,
                                ConvergenceAdmission.NOT_A_CANDIDATE,
                                -> GroupEventResult.Duplicate(groupId)
                            }
                        }

                        currentEpoch != null && pubMsg.epoch > currentEpoch -> {
                            GroupEventResult.Error(
                                groupId,
                                "Commit epoch ${pubMsg.epoch} is ahead of local epoch $currentEpoch; ignoring",
                            )
                        }

                        else -> {
                            // RFC 9420 §6.2 — reject PublicMessage commits
                            // whose membership_tag doesn't match what the
                            // current epoch's membership_key would produce.
                            // Without this an outsider with the outer
                            // exporter secret could inject arbitrary commit
                            // bytes and advance the group past them.
                            val group = groupManager.getGroup(groupId)
                            if (group != null && !group.verifyPublicMessageCommitMembershipTag(pubMsg)) {
                                GroupEventResult.Error(
                                    groupId,
                                    "Invalid membership_tag on PublicMessage commit",
                                )
                            } else {
                                // Captured BEFORE the epoch advance: this is
                                // the parent a competing commit authenticates
                                // against, and without it a commit that lost
                                // the race would have nothing to replay on and
                                // could never be reconsidered.
                                val preState = groupManager.snapshot(groupId)
                                groupManager.processCommit(
                                    nostrGroupId = groupId,
                                    commitBytes = pubMsg.content,
                                    senderLeafIndex = pubMsg.sender.leafIndex,
                                    confirmationTag = tag,
                                    signature = pubMsg.signature,
                                )
                                convergence.recordApplied(groupId, mlsBytes, pubMsg.epoch, preState)
                                val post = groupManager.getGroup(groupId)
                                GroupEventResult.CommitProcessed(groupId, post?.epoch ?: 0)
                            }
                        }
                    }
                }

                else -> {
                    GroupEventResult.Error(groupId, "Unexpected wire format for commit")
                }
            }
        } catch (e: Exception) {
            GroupEventResult.Error(groupId, "Failed to apply commit: ${e.message}", e)
        }

    /**
     * Decrypt the outer ChaCha20-Poly1305 layer, trying the current epoch's
     * exporter key first and falling back to retained epoch exporter keys.
     *
     * After a commit advances the epoch, late-arriving messages encrypted
     * with the previous epoch's exporter key would fail without this fallback.
     *
     * Returns null when neither the current epoch key nor any retained key
     * decrypts. This happens normally for commits/application messages from
     * epochs that predate our join (we never held those keys), so callers
     * should treat null as an expected "nothing to do here" outcome and log
     * at DEBUG, not as an error.
     */
    private suspend fun tryDecryptOuterLayer(
        groupId: HexKey,
        encryptedContent: String,
    ): ByteArray? {
        // Try current epoch key first
        try {
            val exporterKey = groupManager.exporterSecret(groupId)
            return GroupEventEncryption.decrypt(encryptedContent, exporterKey)
        } catch (_: Exception) {
            // Current epoch key failed — try retained epoch keys
        }

        // Try retained epoch exporter keys (most recent first)
        val retainedKeys = groupManager.retainedExporterSecrets(groupId)
        for (retainedKey in retainedKeys) {
            try {
                return GroupEventEncryption.decrypt(encryptedContent, retainedKey)
            } catch (_: Exception) {
                // This retained key didn't work — try the next one
            }
        }

        // Finally the branches convergence retains. An event published on a
        // fork is keyed by that branch's epoch exporter, which appears in
        // neither the canonical nor the retained-canonical set — so without
        // this a payload on a candidate branch could never even be peeled, and
        // the branch could never accumulate witnesses.
        for (candidateKey in convergence.candidateExporterSecrets(groupId)) {
            try {
                return GroupEventEncryption.decrypt(encryptedContent, candidateKey)
            } catch (_: Exception) {
                // Not this branch — try the next.
            }
        }

        return null
    }
}
