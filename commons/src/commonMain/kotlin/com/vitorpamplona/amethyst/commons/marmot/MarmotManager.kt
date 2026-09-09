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
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupImage
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.MarmotInboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotOutboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager
import com.vitorpamplona.quartz.marmot.MarmotWelcomeSender
import com.vitorpamplona.quartz.marmot.OutboundGroupEvent
import com.vitorpamplona.quartz.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.CurrentProfileGroupFactory
import com.vitorpamplona.quartz.marmot.appComponents.GroupBlossomImageV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.marmot.appComponents.MessageRetentionV1
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
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishGate
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishObligationStore
import com.vitorpamplona.quartz.marmot.protocolCore.PublishOutcome
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quote
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    /**
     * How this client publishes group-state changes and learns whether they
     * were accepted.
     *
     * Publish-before-apply (`protocol-core/publish-lifecycle.md`) needs an
     * acknowledgement, so the manager owns the publish rather than handing
     * bytes to a caller that may or may not report back. The default refuses
     * every obligation: a client that never configures one can read a group
     * but can never advance its state, which is the safe direction to fail.
     */
    val publisher: MarmotPublisher = MarmotPublisher { _, _ -> false },
    publishObligationStore: MarmotPublishObligationStore? = null,
    /**
     * Scope used to carry an open convergence pass to its cutoff.
     *
     * A group that goes quiet mid-pass has no inbound traffic to tick it, so
     * something has to. When null the client MUST drive
     * [driveConvergenceToSettlement] itself, or a fork will sit unresolved
     * until the next message happens to arrive.
     */
    private val scope: CoroutineScope? = null,
) {
    val groupManager = MlsGroupManager(store)
    val keyPackageRotationManager = KeyPackageRotationManager(keyPackageStore)
    val subscriptionManager = MarmotSubscriptionManager(signer.pubKey)
    val inboundProcessor = MarmotInboundProcessor(groupManager, keyPackageRotationManager)
    val outboundProcessor = MarmotOutboundProcessor(groupManager)
    val welcomeSender = MarmotWelcomeSender(signer)
    val publishGate =
        publishObligationStore?.let { MarmotPublishGate(groupManager, it) }
            ?: MarmotPublishGate(groupManager)

    /**
     * Restore all Marmot state from persistent storage.
     * Call once during Account initialization.
     */
    suspend fun restoreAll() {
        Log.d("MarmotManager") { "restoreAll(): begin for ${signer.pubKey.take(8)}…" }
        try {
            groupManager.restoreAll()
            val activeIds = groupManager.activeGroupIds()
            // Register restored groups with a seeded `since` BEFORE
            // syncWithGroupManager fills in default (since = null) entries,
            // so even the first filter set sent to relays skips the
            // already-processed kind:445 backlog.
            subscriptionSinceFromStoredMessages(activeIds).forEach { (groupId, since) ->
                subscriptionManager.subscribeGroup(groupId, since)
            }
            subscriptionManager.syncWithGroupManager(activeIds)
            // Seed convergence with each restored state. A commit that arrives
            // before this has no retained parent, so a fork right after a
            // restart would be invisible.
            activeIds.forEach { inboundProcessor.trackGroup(it) }
            // An interruption does not resolve a publish obligation. Anything
            // still unresolved keeps its group in PendingPublish until it is
            // retried byte-identically and acknowledged — generating a
            // replacement commit instead would fork us at our own epoch.
            publishGate.restore()
            // Also restore previously-published KeyPackage bundles so that
            // Welcomes referencing them remain processable across restarts.
            keyPackageRotationManager.restoreFromStore()
            Log.d("MarmotManager") { "restoreAll(): done, ${activeIds.size} groups: $activeIds" }
        } catch (e: Exception) {
            Log.e("MarmotManager", "Failed to restore Marmot state", e)
        }
    }

    /**
     * Computes a per-group kind:445 subscription `since` from the newest
     * persisted decrypted message of each group.
     *
     * Subscription state does not survive a restart, and neither does the
     * application ratchet position (group state is persisted only at
     * commits). Without a `since`, every restart re-downloads the group's
     * full kind:445 backlog, and the rewound ratchet re-decrypts old
     * application messages as if they had just arrived.
     *
     * [GROUP_EVENT_REFETCH_OVERLAP_SEC] of overlap is kept so late or
     * out-of-order events published shortly before the newest stored message
     * are still fetched; replays inside the window are deduplicated by the
     * message store and by note identity in the chatroom.
     */
    private suspend fun subscriptionSinceFromStoredMessages(groupIds: Set<HexKey>): Map<HexKey, Long> {
        if (messageStore == null) return emptyMap()
        val result = mutableMapOf<HexKey, Long>()
        for (groupId in groupIds) {
            val newestStored =
                loadStoredMessages(groupId).maxOfOrNull { json ->
                    try {
                        Event.fromJson(json).createdAt
                    } catch (e: Exception) {
                        Log.w("MarmotManager", "Unparseable persisted message for $groupId: ${e.message}", e)
                        0L
                    }
                } ?: continue
            // Clamp at wall-clock now: inner createdAt is sender-controlled,
            // and a single future-dated message must not push `since` past
            // the present — that would skip genuinely new events on every
            // restart until a fresher message arrives.
            val newest = minOf(newestStored, TimeUtils.now())
            if (newest > GROUP_EVENT_REFETCH_OVERLAP_SEC) {
                result[groupId] = newest - GROUP_EVENT_REFETCH_OVERLAP_SEC
            }
        }
        return result
    }

    // --- Inbound Processing ---

    /**
     * Process an inbound GroupEvent (kind:445).
     * Returns the inner event JSON if it was an application message.
     */
    suspend fun processGroupEvent(groupEvent: GroupEvent): GroupEventResult {
        val result = inboundProcessor.processGroupEvent(groupEvent)

        // A fork just opened a bounded pass. Inbound traffic settles it
        // opportunistically, but the group may fall silent before its cutoff —
        // so start the carrier now, while we know a pass exists.
        if (result is GroupEventResult.CommitPending && result.forkDetected) {
            startConvergenceSettler()
        }

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
            is GroupEventResult.AppMessageOnCandidateBranch,
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
     * [mentions] become p-tags on the inner kind:9 (users referenced via
     * `nostr:npub…`/`nostr:nprofile…` in [text]), mirroring how NIP-17
     * chat messages tag mentioned users. They stay inside the MLS
     * ciphertext — the outer kind:445 never carries member pubkeys.
     *
     * @return the signed kind:445 outer event together with the inner kind:9
     *   rumor id, so the caller can reference it for replies/reactions.
     */
    suspend fun buildTextMessage(
        nostrGroupId: HexKey,
        text: String,
        replyToEventId: HexKey? = null,
        replyToAuthorPubKey: HexKey? = null,
        persistOwn: Boolean = true,
        mentions: List<PTag> = emptyList(),
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = 9, description = text) {
                    pTags(mentions)
                    if (replyToEventId != null) {
                        // Mirror ChatEvent.reply(): NIP-18 q-tag references the
                        // parent inner kind:9 by id (+ optional author, no
                        // relay hint — the inner rumor never hits a relay
                        // directly). Taking id+pubKey separately (rather than
                        // the full parent Event) lets the push-notification
                        // reply path produce a threaded reply from cold start,
                        // when LocalCache hasn't been re-hydrated yet.
                        quote(
                            QEventTag(
                                eventId = replyToEventId,
                                relayHint = null,
                                authorPubKeyHex = replyToAuthorPubKey,
                            ),
                        )
                    }
                }
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
        // Verify that the KeyPackage credential matches the expected member
        // pubkey. Accepts either framing — a peer's published KeyPackage is an
        // MLSMessage, and bare bytes still arrive from our own pre-fix
        // publications sitting on relays.
        val kp = KeyPackageUtils.decodeKeyPackage(keyPackageBytes)
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
        // that key.
        val publication =
            commitAndPublish(nostrGroupId, relays) {
                // The BARE KeyPackage, not the bytes as published. Transport
                // framing is the Marmot layer's business; MLS takes the struct.
                groupManager.stageAddMember(nostrGroupId, kp.toTlsBytes())
            }

        // The Welcome is a SEPARATE, retryable per-invitee delivery obligation
        // that only exists once the Add is canonical. A Welcome for an epoch
        // no relay accepted would invite someone into a group that does not
        // exist anywhere else.
        val welcomeDelivery =
            if (publication.confirmed) {
                welcomeSender.wrapWelcome(
                    commitResult = publication.commitResult,
                    recipientPubKey = memberPubKey,
                    keyPackageEventId = keyPackageEventId,
                    relays = relays,
                    nostrGroupId = nostrGroupId,
                )
            } else {
                null
            }

        return Pair(publication.event, welcomeDelivery)
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
        // The group-creation exception: a one-member epoch-0 group has no peer
        // that failure to publish could fork, so its obligation is empty and
        // immediately satisfied. Every LATER commit takes the normal
        // publish-before-apply path.
        publishGate.satisfyEmptyObligation(nostrGroupId)
        inboundProcessor.trackGroup(nostrGroupId)
        subscriptionManager.subscribeGroup(nostrGroupId)
        Log.d("MarmotManager") { "createGroup($nostrGroupId): persisted and subscribed" }
        return nostrGroupId
    }

    /**
     * Create a CURRENT-PROFILE group (`app-components/`, the `0x8009` profile).
     *
     * The difference from [createGroup] is what the group requires of its
     * members: a current-profile group's GroupContext requires the account
     * identity proof component, and every member leaf carries one. That is the
     * interop line — a peer running the current profile refuses a leaf without
     * it, and a legacy group cannot be upgraded into one by adding an
     * extension, because the existing leaves have no proofs to add.
     *
     * The group is built outside the manager because a leaf's identity proof
     * covers its OWN signature key, so the keypair must exist and be authorized
     * by the account signer before the leaf is built.
     */
    suspend fun createCurrentProfileGroup(
        nostrGroupId: HexKey,
        relays: List<String>,
        profile: GroupProfileV1? = null,
        additionalAdmins: List<ByteArray> = emptyList(),
        retention: MessageRetentionV1? = null,
    ): HexKey {
        Log.d("MarmotManager") { "createCurrentProfileGroup($nostrGroupId): by ${signer.pubKey.take(8)}…" }
        val group =
            CurrentProfileGroupFactory.createGroup(
                signer = signer,
                nostrGroupId = nostrGroupId.hexToByteArray(),
                relays = relays,
                profile = profile,
                additionalAdmins = additionalAdmins,
                retention = retention,
            )
        groupManager.adoptGroup(nostrGroupId, group)
        // Same empty-obligation exception as [createGroup]: a one-member
        // epoch-0 group has no peer that failure to publish could fork.
        publishGate.satisfyEmptyObligation(nostrGroupId)
        inboundProcessor.trackGroup(nostrGroupId)
        subscriptionManager.subscribeGroup(nostrGroupId)
        return nostrGroupId
    }

    /** A locally prepared commit, published and resolved. */
    class CommitPublication(
        val event: OutboundGroupEvent,
        val commitResult: CommitResult,
        /** Stable id of the publish obligation; a safe retry republishes its bytes. */
        val obligationId: HexKey,
        /** True when at least one relay in scope acknowledged an accept. */
        val confirmed: Boolean,
    )

    /**
     * Prepare a local commit, publish it, and apply it only if publication was
     * acknowledged (`protocol-core/publish-lifecycle.md`).
     *
     * The commit is staged on a clone, so until an acknowledgement arrives the
     * live group has not moved. Apply-then-undo would look equivalent and is
     * not: between the two there is a window in which this client's canonical
     * state is an epoch no peer has, and a crash inside it makes the fork
     * permanent.
     */
    private suspend fun commitAndPublish(
        nostrGroupId: HexKey,
        relays: List<NormalizedRelayUrl>,
        stage: suspend () -> MlsGroupManager.StagedCommit,
    ): CommitPublication {
        check(publishGate.canPrepareLocalCommit(nostrGroupId)) {
            "Group $nostrGroupId cannot prepare a local commit " +
                "(lifecycle=${publishGate.lifecycle(nostrGroupId)}, gate=${publishGate.outboundGate(nostrGroupId)})"
        }

        val staged = stage()
        val event =
            outboundProcessor.buildCommitEvent(
                nostrGroupId = nostrGroupId,
                commitBytes = staged.result.framedCommitBytes,
                exporterKey = staged.result.preCommitExporterSecret,
            )

        // Durable BEFORE the publish. Publishing first would leave a crash
        // window in which peers have accepted a commit this client has no
        // memory of preparing — and on restart it would generate a
        // replacement, forking itself at the same epoch.
        val obligation =
            publishGate.prepare(
                groupId = nostrGroupId,
                staged = staged,
                outboundBytes = event.signedEvent.toJson().encodeToByteArray(),
                recipientScope = relays.map { it.url },
            )

        val confirmed =
            try {
                publisher.publish(event.signedEvent, relays.toSet())
            } catch (e: Exception) {
                Log.w("MarmotManager", "publish failed for $nostrGroupId: ${e.message}", e)
                false
            }

        publishGate.resolve(
            obligation.obligationId,
            if (confirmed) PublishOutcome.CONFIRMED else PublishOutcome.FAILED,
        )

        if (confirmed) {
            // The published kind:445 echoes back from the relay — without this
            // dedup our own inbound pipeline would try to re-apply a commit
            // whose epoch we have already merged.
            inboundProcessor.markMessageProcessed(event.marmotMessageId)
            // Our own commit is half of any fork we are party to. Without it in
            // the retained window a peer's competing commit has no parent to
            // replay against, so it would be deferred as an orphan rather than
            // compared — and the group would quietly stay split.
            inboundProcessor.recordLocalCommit(
                groupId = nostrGroupId,
                framedCommitBytes = staged.result.framedCommitBytes,
                sourceEpoch = staged.priorState.groupContext.epoch,
                preState = staged.priorState,
            )
        } else {
            Log.w("MarmotManager") {
                "commitAndPublish($nostrGroupId): no relay acknowledged the commit — pending state " +
                    "discarded, group stays at epoch ${staged.priorState.groupContext.epoch}"
            }
        }

        return CommitPublication(event, staged.result, obligation.obligationId, confirmed)
    }

    /**
     * Carry open convergence passes to their cutoff and resolve them.
     *
     * Runs only while some pass is open and returns as soon as none is, so a
     * quiet client does no periodic work at all — this is a carrier for work
     * already in flight, not a heartbeat.
     */
    suspend fun driveConvergenceToSettlement(pollMs: Long = CONVERGENCE_POLL_MS) {
        while (inboundProcessor.openConvergencePasses().isNotEmpty()) {
            delay(pollMs)
            inboundProcessor.settleDueConvergence().forEach { resolution ->
                Log.d("MarmotManager") {
                    "convergence settled group=${resolution.groupId.take(8)}… " +
                        "epoch=${resolution.canonicalEpoch} rewound=${resolution.rewound}"
                }
            }
        }
    }

    private fun startConvergenceSettler() {
        val runner = scope ?: return
        // One carrier at a time: every fork in a busy group would otherwise
        // start another, and they would all poll the same passes.
        if (!settlerRunning.compareAndSet(expect = false, update = true)) return
        runner.launch {
            try {
                driveConvergenceToSettlement()
            } finally {
                settlerRunning.value = false
            }
        }
    }

    private val settlerRunning = MutableStateFlow(false)

    /** Lifecycle state for a group, including any unresolved publish obligation. */
    suspend fun lifecycle(nostrGroupId: HexKey): GroupLifecycleState = publishGate.lifecycle(nostrGroupId)

    /**
     * The group's own relay list, as the recipient scope for a publish
     * obligation.
     *
     * Read from the group's canonical state rather than from a caller-supplied
     * list, so a commit's acknowledgement has to come from an endpoint the
     * GROUP names — the same set every other member is listening on.
     */
    fun groupRelays(nostrGroupId: HexKey): List<NormalizedRelayUrl> {
        val group = groupManager.getGroup(nostrGroupId) ?: return emptyList()
        // A current-profile group routes through `marmot.transport.nostr.routing.v1`
        // (`0x8004`); only a legacy group carries its relays in the `0xF2EE`
        // group-data extension. Reading just the legacy one left every
        // current-profile group with an empty recipient scope, so its commits
        // had nowhere to be acknowledged and never became canonical.
        val routed =
            group
                .currentGroupState()
                .routing
                ?.relays
                .orEmpty()
        val legacy = group.currentMarmotData()?.relays.orEmpty()
        return (routed + legacy).distinct().mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
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
            Log.w("MarmotManager", "resetAllState(): groupManager.clearAllState failed", e)
        }
        try {
            keyPackageRotationManager.clearAllState()
        } catch (e: Exception) {
            Log.w("MarmotManager", "resetAllState(): keyPackageRotationManager.clearAllState failed", e)
        }
        try {
            subscriptionManager.clear()
        } catch (e: Exception) {
            Log.w("MarmotManager", "resetAllState(): subscriptionManager.clear failed", e)
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

        // A departure is a SelfRemove PROPOSAL, not a local commit: another
        // authorized member commits it, so the leaver has no pending state and
        // publish-before-apply does not bind here. What does bind is the
        // outbound gate — until the removal is realized, this client must not
        // start new group-state work it would have no standing to publish.
        publishGate.raiseGate(nostrGroupId, LocalOutboundGate.LEAVING)

        subscriptionManager.unsubscribeGroup(nostrGroupId)
        try {
            messageStore?.delete(nostrGroupId)
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to delete persisted messages for $nostrGroupId", e)
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
            Log.w("MarmotManager", "Failed to persist Marmot message for $nostrGroupId", e)
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
            Log.w("MarmotManager", "Failed to load persisted messages for $nostrGroupId", e)
            emptyList()
        }

    /**
     * Remove a member from a group.
     * Returns the commit GroupEvent to publish.
     */
    suspend fun removeMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent =
        commitAndPublish(nostrGroupId, relays) {
            groupManager.stageRemoveMember(nostrGroupId, targetLeafIndex)
        }.event

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
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val group =
            groupManager.getGroup(nostrGroupId)
                ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        val preserved = group.extensions.filter { it.extensionType != MarmotGroupData.EXTENSION_ID_INT }
        val merged = preserved + metadata.toExtension()
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageUpdateGroupExtensions(nostrGroupId, merged)
        }.event
    }

    /**
     * A group's metadata read through whichever profile it actually uses.
     *
     * Every caller that wants a name, an admin list or an avatar wants this,
     * not [groupMetadata]: the legacy accessor returns null for every
     * current-profile group, so the UI, the CLI and the await verbs all showed
     * a blank name and an empty admin set for groups that were perfectly fine.
     */
    class GroupView(
        val name: String,
        val description: String,
        val adminPubkeys: List<HexKey>,
        val relays: List<String>,
        val image: MarmotGroupImage?,
        /** True when the group requires `0x8009` — see [MarmotGroupState.isCurrentProfile]. */
        val isCurrentProfile: Boolean,
    )

    fun groupView(nostrGroupId: HexKey): GroupView? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        val state = group.currentGroupState()
        val legacy = MarmotGroupData.fromExtensions(group.extensions)
        val image = state.image
        return GroupView(
            name = state.profile?.name?.takeIf { it.isNotEmpty() } ?: legacy?.name.orEmpty(),
            description = state.profile?.description?.takeIf { it.isNotEmpty() } ?: legacy?.description.orEmpty(),
            adminPubkeys = state.adminPolicy?.adminHexKeys ?: legacy?.adminPubkeys.orEmpty(),
            relays = state.routing?.relays ?: legacy?.relays.orEmpty(),
            image =
                when {
                    image?.imageHash != null ->
                        MarmotGroupImage(image.imageHash!!.toHexKey(), image.imageKey!!, image.imageNonce!!)

                    legacy?.hasImage() == true ->
                        MarmotGroupImage(legacy.imageHash!!, legacy.imageKey!!, legacy.imageNonce!!)

                    else -> null
                },
            isCurrentProfile = state.isCurrentProfile,
        )
    }

    /**
     * Rename a group, writing to whichever carrier the group actually uses.
     *
     * A current-profile group takes an `app_data_update` naming ONLY the
     * profile component, so a concurrent admin-policy change does not lose its
     * work to this one. A legacy group has no such separation — its single
     * `0xF2EE` extension is rewritten whole.
     */
    suspend fun setGroupProfile(
        nostrGroupId: HexKey,
        name: String,
        description: String,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        if (!view.isCurrentProfile) {
            val legacy =
                groupMetadata(nostrGroupId)
                    ?: throw IllegalStateException("Legacy group $nostrGroupId has no MarmotGroupData")
            return updateGroupMetadata(nostrGroupId, legacy.copy(name = name, description = description), relays)
        }
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(
                nostrGroupId,
                GroupProfileV1.COMPONENT_ID,
                GroupProfileV1(name, description).encode(),
            )
        }.event
    }

    /**
     * Replace the group's admin set, writing to whichever carrier the group uses.
     *
     * Refuses an empty set. Both profiles reject a group with no admins — a
     * groupthat can never again change its own state is not a state anyone
     * can recover from, so the check belongs here rather than at each caller.
     */
    suspend fun setGroupAdmins(
        nostrGroupId: HexKey,
        admins: List<HexKey>,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        require(admins.isNotEmpty()) { "a Marmot group cannot be left with no admins" }
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        if (!view.isCurrentProfile) {
            val legacy =
                groupMetadata(nostrGroupId)
                    ?: throw IllegalStateException("Legacy group $nostrGroupId has no MarmotGroupData")
            return updateGroupMetadata(nostrGroupId, legacy.copy(adminPubkeys = admins), relays)
        }
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(
                nostrGroupId,
                AdminPolicyV1.COMPONENT_ID,
                AdminPolicyV1.ofHex(admins).encode(),
            )
        }.event
    }

    /**
     * Set or clear the group avatar, writing to whichever carrier the group uses.
     *
     * [image] null clears it: the current profile removes the `0x8002`
     * component outright rather than storing an "absent" encoding, so a group
     * with no avatar carries no avatar state.
     */
    suspend fun setGroupImage(
        nostrGroupId: HexKey,
        image: GroupBlossomImageV1?,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        if (!view.isCurrentProfile) {
            val legacy =
                groupMetadata(nostrGroupId)
                    ?: throw IllegalStateException("Legacy group $nostrGroupId has no MarmotGroupData")
            val updated =
                if (image?.imageHash == null) {
                    legacy.withoutImage()
                } else {
                    legacy.withImage(
                        image.imageHash!!.toHexKey(),
                        image.imageKey!!,
                        image.imageNonce!!,
                        image.imageUploadKey!!,
                    )
                }
            return updateGroupMetadata(nostrGroupId, updated, relays)
        }
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(
                nostrGroupId,
                GroupBlossomImageV1.COMPONENT_ID,
                image?.encode(),
            )
        }.event
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
        /**
         * Publish a current-profile KeyPackage, carrying the account identity
         * proof in its leaf.
         *
         * This is the decisive interop switch. A peer running the current
         * profile requires component `0x8009` and refuses a leaf without it, so
         * a legacy KeyPackage is simply not addable to a current-profile group
         * — which is what kept us uninvitable.
         */
        currentProfile: Boolean = true,
    ): KeyPackageEvent {
        val dTag = keyPackageRotationManager.getOrCreateSlotDTag(slotName)
        val identity = signer.pubKey.hexToByteArray()
        val bundle =
            if (currentProfile) {
                keyPackageRotationManager.generateCurrentProfileKeyPackage(signer, dTag)
            } else {
                keyPackageRotationManager.generateKeyPackage(identity, dTag)
            }

        // Framed as an MLSMessage, never bare: `foundation/key-packages.md`
        // says a transport publication IS the framed message. The ref stays
        // over the INNER KeyPackage, which is what RFC 9420 MakeKeyPackageRef
        // hashes — framing the ref too would make our `i` tag disagree with
        // every other implementation's.
        val keyPackageBase64 = Base64.encode(KeyPackageUtils.frameKeyPackage(bundle.keyPackage))
        val keyPackageRef = bundle.keyPackage.reference().toHexKey()

        val template =
            if (currentProfile) {
                // Every id-list tag is derived from the KeyPackage itself.
                // They duplicate metadata already inside it, so writing them by
                // hand is a second source of truth — and a receiver that
                // compares them (MDK does, exactly) rejects the KeyPackage the
                // moment the two disagree.
                //
                // Deliberately no `relays` tag and no `encoding` tag.
                // `transports/nostr.md`: a KeyPackage is fetched from the
                // account's own NIP-65 write set, so repeating relays here
                // would be another drifting duplicate; and the binding forbids
                // an `encoding` tag outright, because a receiver that switched
                // decoders on one could be steered into a different parse of
                // the same bytes.
                KeyPackageEvent.buildCurrentProfileFrom(
                    keyPackage = bundle.keyPackage,
                    dTagSlot = dTag,
                )
            } else {
                KeyPackageEvent.build(
                    keyPackageBase64 = keyPackageBase64,
                    dTagSlot = dTag,
                    keyPackageRef = keyPackageRef,
                    relays = relays,
                )
            }

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
            val keyPackageBase64 = Base64.encode(KeyPackageUtils.frameKeyPackage(bundle.keyPackage))
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
     * The current profile's GroupContext components for a group, or null when
     * the group is unknown. A legacy group returns a state whose component
     * fields are all null — read [groupMetadata] for those.
     */
    fun groupState(nostrGroupId: HexKey): MarmotGroupState? = groupManager.getGroup(nostrGroupId)?.currentGroupState()

    /**
     * Sync MIP-01 metadata and member info from the MLS group into a [MarmotGroupChatroom].
     * Call after joining a group, processing a commit, or restoring from storage.
     */
    fun syncMetadataTo(
        nostrGroupId: HexKey,
        chatroom: MarmotGroupChatroom,
    ) {
        // Read through [groupView], not [groupMetadata]: the legacy accessor
        // returns null for every current-profile group, which left them with a
        // blank name, no admins, no relays and no avatar in the UI. The group
        // worked; it just looked empty.
        val view = groupView(nostrGroupId)
        if (view != null) {
            if (view.name.isNotEmpty()) chatroom.displayName.value = view.name
            if (view.description.isNotEmpty()) chatroom.description.value = view.description
            chatroom.adminPubkeys.value = view.adminPubkeys
            chatroom.relays.value = view.relays
            chatroom.image.value = view.image
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

    companion object {
        /**
         * Overlap window (seconds) subtracted from the newest persisted
         * message's createdAt when seeding a restored group's kind:445
         * subscription `since`. One day is generous: inner and outer events
         * are timestamped at the same send, so the window only needs to
         * absorb relay/system clock skew and out-of-order publishes.
         */
        internal val GROUP_EVENT_REFETCH_OVERLAP_SEC: Long = TimeUtils.ONE_DAY.toLong()

        /**
         * How often the settler re-checks an open pass.
         *
         * A quarter of the quiescence window, so a pass that goes quiet settles
         * promptly without the poll itself becoming the thing that decides
         * timing. The pass's own monotonic deadlines decide when it closes;
         * this only decides how soon afterwards we notice.
         */
        internal const val CONVERGENCE_POLL_MS: Long = 250L
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
