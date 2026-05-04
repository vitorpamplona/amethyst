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

            is GroupEventResult.ProposalStaged -> {
                subscriptionManager.updateGroupSince(result.groupId, groupEvent.createdAt)
            }

            is GroupEventResult.CommitPending,
            is GroupEventResult.Duplicate,
            is GroupEventResult.UndecryptableOuterLayer,
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
        hintNostrGroupId: HexKey? = welcomeEvent.nostrGroupId(),
    ): WelcomeResult {
        // nostrGroupId is derived from the MLS GroupContext's NostrGroupData extension.
        // The h-tag value (hintNostrGroupId) is validated against the MLS content inside
        // inboundProcessor, so senders that omit the h-tag are handled transparently.
        val result = inboundProcessor.processWelcome(welcomeEvent, hintNostrGroupId)

        if (result is WelcomeResult.Joined) {
            subscriptionManager.subscribeGroup(result.nostrGroupId)
            Log.d("MarmotManager") { "Joined group ${result.nostrGroupId}" }
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
     * Build a kind:9 chat-message GroupEvent from plain text. The inner
     * event is built as an UNSIGNED rumor per MIP-03 ("Inner events MUST
     * remain unsigned — this ensures leaked events cannot be published to
     * public relays"): the MLS sender authenticates via the LeafNode
     * credential + the `pubkey` ↔ sender-identity equality check, so
     * the inner Nostr signature is redundant, and leaving it in would
     * let a leaked plaintext be replayed as a valid public kind:9.
     *
     * Optionally persisted to the local decrypted-message log so
     * `loadStoredMessages` reflects our own outbound immediately
     * (without waiting for relay loopback).
     *
     * Platform callers that already maintain their own "own event" cache
     * (i.e. Amethyst's `LocalCache.justConsumeMyOwnEvent`) should pass
     * `persistOwn = false`. Headless callers (CLI) should leave it at
     * the default.
     *
     * @return the signed kind:445 outer event together with the inner kind:9
     *   rumor id, so the caller can reference it for replies/reactions.
     */
    suspend fun buildTextMessage(
        nostrGroupId: HexKey,
        text: String,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = 9, description = text)
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<Event>(signer.pubKey, template)
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * Build a kind:7 reaction inner event targeting another inner event in
     * the same group. Like [buildTextMessage], the inner event is an
     * unsigned rumor (MIP-03). The reaction uses NIP-25 conventions: content
     * is the emoji / `+` / `-`; e-tag + p-tag + k-tag reference the target.
     */
    suspend fun buildReactionMessage(
        nostrGroupId: HexKey,
        targetEvent: Event,
        reaction: String,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
                .build(
                    reaction,
                    com.vitorpamplona.quartz.nip01Core.hints
                        .EventHintBundle(targetEvent),
                )
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<com.vitorpamplona.quartz.nip25Reactions.ReactionEvent>(
                    signer.pubKey,
                    template,
                )
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * Build a kind:5 deletion inner event targeting one or more prior inner
     * events in the same group. Unsigned rumor (MIP-03); e-tag + k-tag for
     * each target per NIP-09.
     */
    suspend fun buildDeletionMessage(
        nostrGroupId: HexKey,
        targetEvents: List<Event>,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        require(targetEvents.isNotEmpty()) { "buildDeletionMessage: targetEvents must not be empty" }
        val template =
            com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
                .build(targetEvents)
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<com.vitorpamplona.quartz.nip09Deletions.DeletionEvent>(
                    signer.pubKey,
                    template,
                )
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * Add a member to a group by consuming their published [KeyPackageEvent].
     *
     * Convenience over [addMember] that handles base64 decoding and lifts the
     * event id into the WelcomeDelivery. Prefer this overload — both the UI's
     * `Account.addMarmotGroupMember` and the CLI's `group add` command call it.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun addMember(
        nostrGroupId: HexKey,
        keyPackageEvent: KeyPackageEvent,
        relays: List<NormalizedRelayUrl>,
    ): Pair<OutboundGroupEvent, WelcomeDelivery?> =
        addMember(
            nostrGroupId = nostrGroupId,
            memberPubKey = keyPackageEvent.pubKey,
            keyPackageBytes =
                kotlin.io.encoding.Base64
                    .decode(keyPackageEvent.keyPackageBase64()),
            keyPackageEventId = keyPackageEvent.id,
            relays = relays,
        )

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

        // Per RFC 9420 §12.4 (and MDK), the outbound kind:445 MUST be
        // outer-encrypted with the pre-commit (epoch-N) exporter secret so
        // that other existing members still at epoch N can decrypt and
        // process the commit. CommitResult.preCommitExporterSecret carries
        // that key; the local group state has already advanced to N+1 by
        // the time addMember returns, so we can't read it from the group
        // any more.
        val commitResult = groupManager.addMember(nostrGroupId, keyPackageBytes)
        val commitEvent =
            outboundProcessor.buildCommitEvent(
                nostrGroupId = nostrGroupId,
                commitBytes = commitResult.framedCommitBytes,
                exporterKey = commitResult.preCommitExporterSecret,
            )
        // The published kind:445 will echo back from the relay — without this
        // dedup our own inbound pipeline would try to re-apply a commit whose
        // epoch we've already merged.
        inboundProcessor.markEventProcessed(commitEvent.signedEvent.id)

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
     *
     * When [initialMetadata] is non-null it is baked into epoch 0's
     * GroupContext.extensions. Later joiners' welcomes therefore carry the
     * group name / admin list / relays from the get-go and no separate
     * "bootstrap commit" needs to be published. The bootstrap-commit path
     * works in theory, but it produces a kind:445 encrypted with epoch 0's
     * exporter secret that no post-membership peer (amethyst or wn) has,
     * so each such peer wastes their commit-retry budget on an
     * undecryptable event before processing the real state.
     */
    suspend fun createGroup(
        nostrGroupId: HexKey,
        initialMetadata: MarmotGroupData? = null,
    ): HexKey {
        Log.d("MarmotManager") { "createGroup($nostrGroupId): by ${signer.pubKey.take(8)}…" }
        val identity = signer.pubKey.hexToByteArray()
        val extras = initialMetadata?.let { listOf(it.toExtension()) } ?: emptyList()
        groupManager.createGroup(nostrGroupId, identity, initialExtensions = extras)
        subscriptionManager.subscribeGroup(nostrGroupId)
        Log.d("MarmotManager") { "createGroup($nostrGroupId): persisted and subscribed" }
        return nostrGroupId
    }

    /**
     * Nuke all local Marmot state — every MLS group, every retained epoch
     * secret, every persisted KeyPackage bundle, and every relay
     * subscription. Does NOT publish any leave/SelfRemove commits: the
     * reset path is specifically for recovering from corrupted or
     * unrecoverable local state where graceful teardown may be impossible.
     *
     * The caller is responsible for wiping any higher-level in-memory
     * structures (e.g. `MarmotGroupList`) and for re-publishing a fresh
     * KeyPackage once the reset completes, if the account is still active.
     */
    suspend fun resetAllState() {
        Log.w("MarmotManager") { "resetAllState(): wiping all Marmot local state for ${signer.pubKey.take(8)}…" }
        try {
            groupManager.clearAllState()
        } catch (e: Exception) {
            Log.w("MarmotManager") { "resetAllState(): groupManager.clearAllState failed: ${e.message}" }
        }
        try {
            keyPackageRotationManager.clearAllState()
        } catch (e: Exception) {
            Log.w("MarmotManager") { "resetAllState(): keyPackageRotationManager.clearAllState failed: ${e.message}" }
        }
        try {
            subscriptionManager.clear()
        } catch (e: Exception) {
            Log.w("MarmotManager") { "resetAllState(): subscriptionManager.clear failed: ${e.message}" }
        }
    }

    /**
     * Leave a group.
     * Returns proposal bytes to publish (as a GroupEvent).
     */
    suspend fun leaveGroup(nostrGroupId: HexKey): OutboundGroupEvent {
        // leaveGroup() returns the framed standalone SelfRemove proposal
        // (PublicMessage{Proposal}) plus the pre-commit exporter key for
        // outer encryption. Runs BEFORE we tear down the subscriptions so
        // the pre-commit exporter is still derivable.
        val (framedBytes, exporterKey) = groupManager.leaveGroup(nostrGroupId)
        val outboundEvent =
            outboundProcessor.buildCommitEvent(
                nostrGroupId = nostrGroupId,
                commitBytes = framedBytes,
                exporterKey = exporterKey,
            )

        subscriptionManager.unsubscribeGroup(nostrGroupId)
        try {
            messageStore?.delete(nostrGroupId)
        } catch (e: Exception) {
            Log.w("MarmotManager") { "Failed to delete persisted messages for $nostrGroupId: ${e.message}" }
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
            Log.w("MarmotManager") { "Failed to persist Marmot message for $nostrGroupId: ${e.message}" }
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
            Log.w("MarmotManager") { "Failed to load persisted messages for $nostrGroupId: ${e.message}" }
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
        val commitEvent =
            outboundProcessor.buildCommitEvent(
                nostrGroupId = nostrGroupId,
                commitBytes = commitResult.framedCommitBytes,
                exporterKey = commitResult.preCommitExporterSecret,
            )
        inboundProcessor.markEventProcessed(commitEvent.signedEvent.id)
        return commitEvent
    }

    /**
     * Update group metadata (name, description, etc.) via a GroupContextExtensions proposal.
     * Creates a GCE proposal, commits it, and returns the commit event to publish.
     *
     * RFC 9420 §12.1.7: a GroupContextExtensions proposal REPLACES the entire
     * extension list in GroupContext. Peers (notably mdk-core / whitenoise-rs)
     * reject welcomes whose GroupContext is missing the required-capabilities
     * extension, and strip metadata from groups whose context lacks the
     * MarmotGroupData extension. We therefore preserve every other existing
     * extension and overwrite only the slot we actually want to update.
     */
    suspend fun updateGroupMetadata(
        nostrGroupId: HexKey,
        metadata: MarmotGroupData,
    ): OutboundGroupEvent {
        val group =
            groupManager.getGroup(nostrGroupId)
                ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        val preserved = group.extensions.filter { it.extensionType != MarmotGroupData.EXTENSION_ID_INT }
        val merged = preserved + metadata.toExtension()
        val commitResult = groupManager.updateGroupExtensions(nostrGroupId, merged)
        val commitEvent =
            outboundProcessor.buildCommitEvent(
                nostrGroupId = nostrGroupId,
                commitBytes = commitResult.framedCommitBytes,
                exporterKey = commitResult.preCommitExporterSecret,
            )
        inboundProcessor.markEventProcessed(commitEvent.signedEvent.id)
        return commitEvent
    }

    // --- KeyPackage Management ---

    /**
     * Generate and build a KeyPackage event template for publishing.
     * Returns the signed KeyPackageEvent ready for publishing.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateKeyPackageEvent(
        relays: List<NormalizedRelayUrl>,
        slotName: String = KeyPackageUtils.PRIMARY_SLOT,
    ): KeyPackageEvent {
        val dTag = keyPackageRotationManager.getOrCreateSlotDTag(slotName)
        val identity = signer.pubKey.hexToByteArray()
        val bundle = keyPackageRotationManager.generateKeyPackage(identity, dTag)

        val keyPackageBytes = bundle.keyPackage.toTlsBytes()
        val keyPackageBase64 = Base64.encode(keyPackageBytes)
        val keyPackageRef = bundle.keyPackage.reference().toHexKey()

        val template =
            KeyPackageEvent.build(
                keyPackageBase64 = keyPackageBase64,
                dTagSlot = dTag,
                keyPackageRef = keyPackageRef,
                relays = relays,
            )

        val signed = signer.sign<KeyPackageEvent>(template)
        // Welcome receivers identify the consumed KeyPackage by its Nostr
        // event id (the MIP-02 "e" tag), not by the MLS reference hash, so
        // remember the mapping right after we know the signed event id.
        keyPackageRotationManager.recordPublishedEventId(dTag, signed.id)
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
     * Hex-encoded MLS group id for the group keyed by [nostrGroupId], or null if
     * that group is not locally known.
     *
     * Interop note: whitenoise-rs (and every mdk consumer) indexes groups by the
     * MLS GroupContext's groupId, NOT the MIP-01 nostr_group_id that we use as
     * the primary key internally. When a harness or external caller needs to
     * cross-reference a group with another client (e.g. the interop harness
     * calling `wn messages list <mls_id>`), it needs this translation.
     */
    fun mlsGroupIdHex(nostrGroupId: HexKey): HexKey? = groupManager.getGroup(nostrGroupId)?.groupId?.toHexKey()

    /**
     * Resolve the MLS leaf index for a member by Nostr pubkey, or null if that
     * pubkey isn't currently in the group.
     *
     * `removeMember` needs a leaf index; every caller (UI remove-member dialog
     * and CLI `group remove`) previously did its own pubkey→leaf lookup through
     * [memberPubkeys]. Centralised here so the scan lives in one place.
     */
    fun leafIndexOf(
        nostrGroupId: HexKey,
        pubKey: HexKey,
    ): Int? = memberPubkeys(nostrGroupId).firstOrNull { it.pubkey == pubKey }?.leafIndex

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
        val previousCount = chatroom.members.value.size
        val members = memberPubkeys(nostrGroupId)
        chatroom.members.value = members
        chatroom.memberCount.value = members.size
        Log.d("MarmotDbg") {
            "syncMetadataTo: group=${nostrGroupId.take(8)}… members $previousCount→${members.size} " +
                "(leafs=${members.map { it.leafIndex }})"
        }
    }
}

/**
 * Information about a member in a Marmot MLS group.
 */
data class GroupMemberInfo(
    val leafIndex: Int,
    val pubkey: HexKey,
)

/**
 * Result of [MarmotManager.buildTextMessage]: the signed outer kind:445 event
 * to publish on group relays, plus the inner kind:9 event (for callers that
 * need the inner id to reference it in replies or reactions).
 */
data class TextMessageBundle(
    val outbound: OutboundGroupEvent,
    val innerEvent: Event,
)
