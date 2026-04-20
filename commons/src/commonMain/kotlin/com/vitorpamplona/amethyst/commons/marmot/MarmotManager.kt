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

import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.MarmotInboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotOutboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager
import com.vitorpamplona.quartz.marmot.MarmotWelcomeSender
import com.vitorpamplona.quartz.marmot.OutboundGroupEvent
import com.vitorpamplona.quartz.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Central coordinator for Marmot MLS group messaging.
 *
 * Holds all Marmot components and provides high-level operations for:
 * - Group lifecycle (create, join, leave)
 * - Message sending and receiving
 * - KeyPackage management and rotation
 * - Subscription filter coordination
 *
 * Initialized during Account startup; all methods should be called
 * from the Account's coroutine scope.
 */
class MarmotManager(
    val signer: NostrSigner,
    store: MlsGroupStateStore,
    val messageStore: MarmotMessageStore? = null,
    val keyPackageStore: KeyPackageBundleStore? = null,
) {
    val groupManager = MlsGroupManager(store)
    val keyPackageRotationManager = KeyPackageRotationManager(keyPackageStore)
    val subscriptionManager = MarmotSubscriptionManager(signer.pubKey)
    val inboundProcessor = MarmotInboundProcessor(groupManager, keyPackageRotationManager)
    val outboundProcessor = MarmotOutboundProcessor(groupManager)
    val welcomeSender = MarmotWelcomeSender(signer)

    /**
     * Restore all Marmot state from persistent storage.
     * Call once during Account initialization.
     */
    suspend fun restoreAll() {
        Log.d("MarmotManager") { "restoreAll(): begin for ${signer.pubKey.take(8)}…" }
        try {
            groupManager.restoreAll()
            val activeIds = groupManager.activeGroupIds()
            subscriptionManager.syncWithGroupManager(activeIds)
            // Also restore previously-published KeyPackage bundles so that
            // Welcomes referencing them remain processable across restarts.
            keyPackageRotationManager.restoreFromStore()
            Log.d("MarmotManager") { "restoreAll(): done, ${activeIds.size} groups: $activeIds" }
        } catch (e: Exception) {
            Log.e("MarmotManager", "Failed to restore Marmot state", e)
        }
    }

    // --- Inbound Processing ---

    /**
     * Process an inbound GroupEvent (kind:445).
     * Returns the inner event JSON if it was an application message.
     */
    suspend fun processGroupEvent(groupEvent: GroupEvent): GroupEventResult {
        val result = inboundProcessor.processGroupEvent(groupEvent)

        // Update subscription timestamp
        when (result) {
            is GroupEventResult.ApplicationMessage -> {
                subscriptionManager.updateGroupSince(result.groupId, groupEvent.createdAt)
            }

            is GroupEventResult.CommitProcessed -> {
                subscriptionManager.updateGroupSince(result.groupId, groupEvent.createdAt)
            }

            is GroupEventResult.CommitPending,
            is GroupEventResult.Duplicate,
            is GroupEventResult.Error,
            -> {}
        }

        return result
    }

    /**
     * Process a WelcomeEvent (kind:444) after NIP-59 unwrapping.
     * Returns the result including whether KeyPackage rotation is needed.
     */
    suspend fun processWelcome(
        welcomeEvent: WelcomeEvent,
        nostrGroupId: HexKey,
    ): WelcomeResult {
        // Validate that the provided nostrGroupId matches the WelcomeEvent's h-tag if present
        val eventGroupId = welcomeEvent.nostrGroupId()
        if (eventGroupId != null && eventGroupId != nostrGroupId) {
            return WelcomeResult.Error(
                "nostrGroupId mismatch: expected $nostrGroupId but WelcomeEvent has $eventGroupId",
            )
        }

        val result = inboundProcessor.processWelcome(welcomeEvent, nostrGroupId)

        if (result is WelcomeResult.Joined) {
            // Update subscription state for the new group
            subscriptionManager.subscribeGroup(result.nostrGroupId)
            Log.d("MarmotManager", "Joined group ${result.nostrGroupId}")
        }

        return result
    }

    // --- Outbound Operations ---

    /**
     * Build a GroupEvent for sending a message to a group.
     */
    suspend fun buildGroupMessage(
        nostrGroupId: HexKey,
        innerEvent: Event,
    ): OutboundGroupEvent = outboundProcessor.buildGroupEvent(nostrGroupId, innerEvent)

    /**
     * Add a member to a group.
     * Returns the commit GroupEvent to publish, and the WelcomeDelivery for the new member.
     */
    suspend fun addMember(
        nostrGroupId: HexKey,
        memberPubKey: HexKey,
        keyPackageBytes: ByteArray,
        keyPackageEventId: HexKey,
        relays: List<NormalizedRelayUrl>,
    ): Pair<OutboundGroupEvent, WelcomeDelivery?> {
        // Verify that the KeyPackage credential matches the expected member pubkey
        val kp =
            com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage.decodeTls(
                com.vitorpamplona.quartz.marmot.mls.codec
                    .TlsReader(keyPackageBytes),
            )
        val credential = kp.leafNode.credential
        require(credential is Credential.Basic) {
            "KeyPackage must use BasicCredential"
        }
        require(credential.identity.toHexKey() == memberPubKey) {
            "KeyPackage credential identity does not match memberPubKey"
        }

        val commitResult = groupManager.addMember(nostrGroupId, keyPackageBytes)
        val commitEvent = outboundProcessor.buildCommitEvent(nostrGroupId, commitResult.commitBytes)

        val welcomeDelivery =
            welcomeSender.wrapWelcome(
                commitResult = commitResult,
                recipientPubKey = memberPubKey,
                keyPackageEventId = keyPackageEventId,
                relays = relays,
                nostrGroupId = nostrGroupId,
            )

        return Pair(commitEvent, welcomeDelivery)
    }

    /**
     * Create a new MLS group.
     */
    suspend fun createGroup(nostrGroupId: HexKey): HexKey {
        Log.d("MarmotManager") { "createGroup($nostrGroupId): by ${signer.pubKey.take(8)}…" }
        val identity = signer.pubKey.hexToByteArray()
        groupManager.createGroup(nostrGroupId, identity)
        subscriptionManager.subscribeGroup(nostrGroupId)
        Log.d("MarmotManager") { "createGroup($nostrGroupId): persisted and subscribed" }
        return nostrGroupId
    }

    /**
     * Leave a group.
     * Returns proposal bytes to publish (as a GroupEvent).
     */
    suspend fun leaveGroup(nostrGroupId: HexKey): OutboundGroupEvent {
        // Build the outbound event BEFORE deleting group state (needs exporter secret)
        val group =
            groupManager.getGroup(nostrGroupId)
                ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        val proposalBytes = group.selfRemove()
        val outboundEvent = outboundProcessor.buildCommitEvent(nostrGroupId, proposalBytes)

        // Now clean up group state
        groupManager.removeGroupState(nostrGroupId)
        subscriptionManager.unsubscribeGroup(nostrGroupId)
        try {
            messageStore?.delete(nostrGroupId)
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to delete persisted messages for $nostrGroupId: ${e.message}")
        }
        return outboundEvent
    }

    /**
     * Persist a freshly decrypted inner event for restart recovery.
     * Marmot MLS application messages cannot be re-decrypted after the
     * ratchet advances, so the only way to restore them across app restarts
     * is to capture the plaintext at decryption time.
     */
    suspend fun persistDecryptedMessage(
        nostrGroupId: HexKey,
        innerEventJson: String,
    ) {
        try {
            messageStore?.appendMessage(nostrGroupId, innerEventJson)
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to persist Marmot message for $nostrGroupId: ${e.message}")
        }
    }

    /**
     * Load all persisted inner event JSONs for a group, in append order.
     * Returns an empty list if no message store is configured or none exist.
     */
    suspend fun loadStoredMessages(nostrGroupId: HexKey): List<String> =
        try {
            messageStore?.loadMessages(nostrGroupId) ?: emptyList()
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to load persisted messages for $nostrGroupId: ${e.message}")
            emptyList()
        }

    /**
     * Remove a member from a group.
     * Returns the commit GroupEvent to publish.
     */
    suspend fun removeMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
    ): OutboundGroupEvent {
        val commitResult = groupManager.removeMember(nostrGroupId, targetLeafIndex)
        return outboundProcessor.buildCommitEvent(nostrGroupId, commitResult.commitBytes)
    }

    /**
     * Update group metadata (name, description, etc.) via a GroupContextExtensions proposal.
     * Creates a GCE proposal, commits it, and returns the commit event to publish.
     */
    suspend fun updateGroupMetadata(
        nostrGroupId: HexKey,
        metadata: MarmotGroupData,
    ): OutboundGroupEvent {
        val commitResult = groupManager.updateGroupExtensions(nostrGroupId, listOf(metadata.toExtension()))
        return outboundProcessor.buildCommitEvent(nostrGroupId, commitResult.commitBytes)
    }

    // --- KeyPackage Management ---

    /**
     * Generate and build a KeyPackage event template for publishing.
     * Returns the signed KeyPackageEvent ready for publishing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateKeyPackageEvent(
        relays: List<NormalizedRelayUrl>,
        dTagSlot: String = KeyPackageUtils.PRIMARY_SLOT,
    ): KeyPackageEvent {
        val identity = signer.pubKey.hexToByteArray()
        val bundle = keyPackageRotationManager.generateKeyPackage(identity, dTagSlot)

        val keyPackageBytes = bundle.keyPackage.toTlsBytes()
        val keyPackageBase64 = Base64.encode(keyPackageBytes)
        val keyPackageRef = bundle.keyPackage.reference().toHexKey()

        val template =
            KeyPackageEvent.build(
                keyPackageBase64 = keyPackageBase64,
                dTagSlot = dTagSlot,
                keyPackageRef = keyPackageRef,
                relays = relays,
            )

        val signed = signer.sign<KeyPackageEvent>(template)
        // Welcome receivers identify the consumed KeyPackage by its Nostr
        // event id (the MIP-02 "e" tag), not by the MLS reference hash, so
        // remember the mapping right after we know the signed event id.
        keyPackageRotationManager.recordPublishedEventId(dTagSlot, signed.id)
        return signed
    }

    /**
     * Rotate consumed KeyPackage slots.
     * Returns list of KeyPackageEvents to publish.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun rotateConsumedKeyPackages(relays: List<NormalizedRelayUrl>): List<KeyPackageEvent> {
        val pendingSlots = keyPackageRotationManager.pendingRotationSlots()
        if (pendingSlots.isEmpty()) return emptyList()

        val identity = signer.pubKey.hexToByteArray()
        return pendingSlots.map { slot ->
            val bundle = keyPackageRotationManager.rotateSlot(identity, slot)
            val keyPackageBytes = bundle.keyPackage.toTlsBytes()
            val keyPackageBase64 = Base64.encode(keyPackageBytes)
            val keyPackageRef = bundle.keyPackage.reference().toHexKey()

            val template =
                KeyPackageEvent.build(
                    keyPackageBase64 = keyPackageBase64,
                    dTagSlot = slot,
                    keyPackageRef = keyPackageRef,
                    relays = relays,
                )
            val signed = signer.sign<KeyPackageEvent>(template)
            keyPackageRotationManager.recordPublishedEventId(slot, signed.id)
            signed
        }
    }

    /**
     * Check if KeyPackage rotation is needed.
     */
    suspend fun needsKeyPackageRotation(): Boolean = keyPackageRotationManager.needsRotation()

    /**
     * Check if there are active (locally generated) KeyPackages.
     * Returns true if at least one KeyPackage has been generated and not yet consumed.
     */
    suspend fun hasActiveKeyPackages(): Boolean = keyPackageRotationManager.hasActiveKeyPackages()

    /**
     * Check if a specific group membership exists.
     */
    fun isMember(nostrGroupId: HexKey): Boolean = groupManager.isMember(nostrGroupId)

    /**
     * Get all active group IDs.
     */
    fun activeGroupIds(): Set<HexKey> = groupManager.activeGroupIds()

    // --- Group Info ---

    /**
     * Get the member count for a group.
     */
    fun memberCount(nostrGroupId: HexKey): Int = groupManager.getGroup(nostrGroupId)?.memberCount ?: 0

    /**
     * Get the member list for a group.
     * Returns leaf index and Nostr pubkey (extracted from BasicCredential) for each member.
     */
    fun memberPubkeys(nostrGroupId: HexKey): List<GroupMemberInfo> {
        val group = groupManager.getGroup(nostrGroupId) ?: return emptyList()
        return group.members().mapNotNull { (leafIndex, leafNode) ->
            val pubkey =
                when (val cred = leafNode.credential) {
                    is Credential.Basic -> cred.identity.toHexKey()
                    else -> null
                }
            if (pubkey != null) {
                GroupMemberInfo(leafIndex = leafIndex, pubkey = pubkey)
            } else {
                null
            }
        }
    }

    /**
     * Get the current epoch for a group.
     */
    fun groupEpoch(nostrGroupId: HexKey): Long? = groupManager.getGroup(nostrGroupId)?.epoch

    /**
     * Get the MIP-04 media exporter secret for a group.
     * MLS-Exporter("marmot", "encrypted-media", 32)
     */
    fun mediaExporterSecret(nostrGroupId: HexKey): ByteArray = groupManager.mediaExporterSecret(nostrGroupId)

    /**
     * Get the MIP-01 group metadata from the MLS GroupContext extensions.
     * Returns null if the group doesn't exist or has no MarmotGroupData extension.
     */
    fun groupMetadata(nostrGroupId: HexKey): MarmotGroupData? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        return MarmotGroupData.fromExtensions(group.extensions)
    }

    /**
     * Sync MIP-01 metadata and member info from the MLS group into a [MarmotGroupChatroom].
     * Call after joining a group, processing a commit, or restoring from storage.
     */
    fun syncMetadataTo(
        nostrGroupId: HexKey,
        chatroom: MarmotGroupChatroom,
    ) {
        val metadata = groupMetadata(nostrGroupId)
        if (metadata != null) {
            if (metadata.name.isNotEmpty()) {
                chatroom.displayName.value = metadata.name
            }
            if (metadata.description.isNotEmpty()) {
                chatroom.description.value = metadata.description
            }
            chatroom.adminPubkeys.value = metadata.adminPubkeys
            chatroom.relays.value = metadata.relays
        }
        val members = memberPubkeys(nostrGroupId)
        chatroom.members.value = members
        chatroom.memberCount.value = members.size
    }
}

/**
 * Information about a member in a Marmot MLS group.
 */
data class GroupMemberInfo(
    val leafIndex: Int,
    val pubkey: HexKey,
)
