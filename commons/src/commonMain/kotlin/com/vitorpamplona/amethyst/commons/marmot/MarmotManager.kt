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

import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.MarmotInboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotOutboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager
import com.vitorpamplona.quartz.marmot.MarmotWelcomeSender
import com.vitorpamplona.quartz.marmot.OutboundGroupEvent
import com.vitorpamplona.quartz.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
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
) {
    val groupManager = MlsGroupManager(store)
    val keyPackageRotationManager = KeyPackageRotationManager()
    val subscriptionManager = MarmotSubscriptionManager(signer.pubKey)
    val inboundProcessor = MarmotInboundProcessor(groupManager, keyPackageRotationManager)
    val outboundProcessor = MarmotOutboundProcessor(groupManager)
    val welcomeSender = MarmotWelcomeSender(signer)

    /**
     * Restore all Marmot state from persistent storage.
     * Call once during Account initialization.
     */
    suspend fun restoreAll() {
        try {
            groupManager.restoreAll()
            subscriptionManager.syncWithGroupManager(groupManager.activeGroupIds())
            Log.d("MarmotManager", "Restored ${groupManager.activeGroupIds().size} groups")
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
        val identity = signer.pubKey.hexToByteArray()
        groupManager.createGroup(nostrGroupId, identity)
        subscriptionManager.subscribeGroup(nostrGroupId)
        return nostrGroupId
    }

    /**
     * Leave a group.
     * Returns proposal bytes to publish (as a GroupEvent).
     */
    suspend fun leaveGroup(nostrGroupId: HexKey): OutboundGroupEvent {
        val proposalBytes = groupManager.leaveGroup(nostrGroupId)
        subscriptionManager.unsubscribeGroup(nostrGroupId)
        return outboundProcessor.buildCommitEvent(nostrGroupId, proposalBytes)
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

        return signer.sign(template)
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
            signer.sign<KeyPackageEvent>(template)
        }
    }

    /**
     * Check if KeyPackage rotation is needed.
     */
    fun needsKeyPackageRotation(): Boolean = keyPackageRotationManager.needsRotation()

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
}

/**
 * Information about a member in a Marmot MLS group.
 */
data class GroupMemberInfo(
    val leafIndex: Int,
    val pubkey: HexKey,
)
