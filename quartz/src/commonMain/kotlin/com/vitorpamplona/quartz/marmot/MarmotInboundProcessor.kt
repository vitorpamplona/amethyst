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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.CommitOrdering
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
 * [MlsGroupManager] (MLS engine), and [CommitOrdering] (conflict resolution).
 */
class MarmotInboundProcessor(
    private val groupManager: MlsGroupManager,
    private val keyPackageRotationManager: KeyPackageRotationManager,
) {
    private val commitTracker = CommitOrdering.EpochCommitTracker()
    private val processedIdsMutex = Mutex()
    private val processedEventIds = LinkedHashSet<String>()

    companion object {
        private const val MAX_PROCESSED_IDS = 10_000

        /**
         * Check if an unwrapped event is a Marmot WelcomeEvent.
         */
        fun isWelcomeEvent(event: Event): Boolean = event.kind == WelcomeEvent.KIND
    }

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
        // Deduplicate already-processed events (thread-safe)
        val eventId = groupEvent.id
        val alreadyProcessed =
            processedIdsMutex.withLock {
                eventId in processedEventIds
            }
        if (alreadyProcessed) {
            return GroupEventResult.Duplicate(groupEvent.groupId() ?: "")
        }

        val groupId =
            groupEvent.groupId()
                ?: return GroupEventResult.Error(null, "GroupEvent missing h tag (group ID)")

        if (!groupManager.isMember(groupId)) {
            return GroupEventResult.Error(groupId, "Not a member of group $groupId")
        }

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
                    // Step 2: Parse the MLS message
                    val mlsMessage = MlsMessage.decodeTls(TlsReader(mlsBytes))

                    when (mlsMessage.wireFormat) {
                        WireFormat.PRIVATE_MESSAGE -> processPrivateMessage(groupId, mlsMessage, groupEvent)
                        WireFormat.PUBLIC_MESSAGE -> processPublicMessage(groupId, mlsMessage, groupEvent)
                        else -> GroupEventResult.Error(groupId, "Unexpected wire format: ${mlsMessage.wireFormat}")
                    }
                }
            } catch (e: Exception) {
                GroupEventResult.Error(groupId, "Failed to process GroupEvent: ${e.message}", e)
            }

        // Track ALL processed events for deduplication (including errors to prevent replay DoS)
        processedIdsMutex.withLock {
            processedEventIds.add(eventId)
            // Trim the set if it exceeds the max size
            if (processedEventIds.size > MAX_PROCESSED_IDS) {
                val iterator = processedEventIds.iterator()
                val toRemove = processedEventIds.size - MAX_PROCESSED_IDS
                repeat(toRemove) {
                    iterator.next()
                    iterator.remove()
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
    suspend fun markEventProcessed(eventId: HexKey) {
        processedIdsMutex.withLock {
            processedEventIds.add(eventId)
            if (processedEventIds.size > MAX_PROCESSED_IDS) {
                val iterator = processedEventIds.iterator()
                val toRemove = processedEventIds.size - MAX_PROCESSED_IDS
                repeat(toRemove) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Resolve any pending commit conflicts for a given epoch.
     *
     * Call this after a brief delay when multiple commits may arrive for
     * the same epoch. The winning commit is applied; losers are discarded.
     *
     * @param groupId the Nostr group ID
     * @param epoch the epoch to resolve
     * @return the result of processing the winning commit, or null if no commits pending
     */
    suspend fun resolveCommitConflict(
        groupId: HexKey,
        epoch: Long,
    ): GroupEventResult? {
        val winner =
            commitTracker.resolve(groupId, epoch)
                ?: return null

        val result = applyCommit(groupId, winner)
        commitTracker.clearEpoch(groupId, epoch)
        return result
    }

    /**
     * Get all (group, epoch) keys that have pending unresolved commits.
     */
    suspend fun pendingCommitGroupEpochs(): Set<CommitOrdering.GroupEpochKey> = commitTracker.pendingGroupEpochs()

    /**
     * Clear all pending commit state.
     */
    suspend fun clearPendingCommits() {
        commitTracker.clear()
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
                // MLS decrypt to get the inner plaintext
                val decrypted = groupManager.decrypt(groupId, mlsMessage.toTlsBytes())
                val innerJson = decrypted.content.decodeToString()

                // MIP-03: if the inner application payload is a Nostr event,
                // its `pubkey` field MUST equal the MLS sender's credential
                // identity. Reject any mismatch — otherwise a group member
                // could mint events claiming a different author. Non-event
                // payloads (raw bytes via buildGroupEventFromBytes) bypass
                // this check since there is no author field to verify.
                val innerEvent =
                    com.vitorpamplona.quartz.nip01Core.core.Event
                        .fromJsonOrNull(innerJson)
                if (innerEvent != null) {
                    val senderIdentity = groupManager.memberIdentityHex(groupId, decrypted.senderLeafIndex)
                    if (senderIdentity == null || innerEvent.pubKey != senderIdentity) {
                        return GroupEventResult.Error(
                            groupId,
                            "MIP-03: inner event pubkey (${innerEvent.pubKey}) does not match MLS sender identity ($senderIdentity)",
                        )
                    }
                }

                GroupEventResult.ApplicationMessage(
                    groupId = groupId,
                    innerEventJson = innerJson,
                    senderLeafIndex = decrypted.senderLeafIndex,
                    epoch = decrypted.epoch,
                )
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
                GroupEventResult.Error(groupId, "Standalone proposals not yet supported")
            }

            ContentType.APPLICATION -> {
                GroupEventResult.Error(groupId, "Application messages should use PrivateMessage")
            }
        }
    }

    private suspend fun handleCommitEvent(
        groupId: HexKey,
        groupEvent: GroupEvent,
    ): GroupEventResult {
        val group =
            groupManager.getGroup(groupId)
                ?: return GroupEventResult.Error(groupId, "Group not found")
        val currentEpoch = group.epoch
        commitTracker.addCommit(groupId, currentEpoch, groupEvent)

        // If this is the only commit for this epoch, apply immediately
        val pending = commitTracker.pendingForEpoch(groupId, currentEpoch)
        return if (pending.size == 1) {
            val result = applyCommit(groupId, groupEvent)
            commitTracker.clearEpoch(groupId, currentEpoch)
            result
        } else {
            GroupEventResult.CommitPending(groupId, currentEpoch)
        }
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
                    // For private commits, MLS decrypt handles epoch advancement
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

                WireFormat.PUBLIC_MESSAGE -> {
                    val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))
                    val tag = pubMsg.confirmationTag
                    val currentEpoch = groupManager.getGroup(groupId)?.epoch
                    when {
                        tag == null -> {
                            GroupEventResult.Error(groupId, "PublicMessage commit missing confirmation_tag")
                        }

                        // Reject commits that are not for our current epoch.
                        // Happens most commonly when our own already-applied
                        // commit is echoed back from the relay after an app
                        // restart (the in-memory dedup set is cleared), and
                        // the outer layer decrypts via a retained epoch key.
                        // Calling `processCommit` on a past-epoch commit
                        // partially mutates tree / groupContext / epochSecrets
                        // before throwing on the confirmation-tag check,
                        // leaving the local state diverged from every other
                        // member's — they then can't decrypt anything we
                        // send next.
                        currentEpoch != null && pubMsg.epoch < currentEpoch -> {
                            GroupEventResult.Duplicate(groupId)
                        }

                        currentEpoch != null && pubMsg.epoch > currentEpoch -> {
                            GroupEventResult.Error(
                                groupId,
                                "Commit epoch ${pubMsg.epoch} is ahead of local epoch $currentEpoch; ignoring",
                            )
                        }

                        else -> {
                            groupManager.processCommit(
                                nostrGroupId = groupId,
                                commitBytes = pubMsg.content,
                                senderLeafIndex = pubMsg.sender.leafIndex,
                                confirmationTag = tag,
                            )
                            val group = groupManager.getGroup(groupId)
                            GroupEventResult.CommitProcessed(groupId, group?.epoch ?: 0)
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
    private fun tryDecryptOuterLayer(
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

        return null
    }
}
