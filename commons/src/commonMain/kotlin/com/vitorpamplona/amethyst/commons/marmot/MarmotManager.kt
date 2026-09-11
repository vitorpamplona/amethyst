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
import com.vitorpamplona.quartz.marmot.MarmotIngestDedupStore
import com.vitorpamplona.quartz.marmot.MarmotOutboundProcessor
import com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager
import com.vitorpamplona.quartz.marmot.MarmotWelcomeSender
import com.vitorpamplona.quartz.marmot.OutboundGroupEvent
import com.vitorpamplona.quartz.marmot.WelcomeDelivery
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.appComponents.AdminPolicyV1
import com.vitorpamplona.quartz.marmot.appComponents.CurrentProfileGroupFactory
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.quartz.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupBlossomImageV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupLifecycleV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.marmot.appComponents.MessageRetentionV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamCrypto
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamFinal
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamKeyContextV1
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamStart
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotGroupSnapshot
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotMessageEdit
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotSystemEvent
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotSystemRowDiff
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.mls.messages.CommitResult
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.protocolCore.GroupLifecycleState
import com.vitorpamplona.quartz.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishGate
import com.vitorpamplona.quartz.marmot.protocolCore.MarmotPublishObligation
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
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quote
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * Durable "already decided" markers for inbound events.
     *
     * Null means every backdated gift wrap is re-unwrapped and re-decided on
     * every sync, forever — correct, but it costs a full NIP-59 double
     * decryption per wrap per sync and keeps the relay busy re-serving them.
     */
    private val ingestDedupStore: MarmotIngestDedupStore? = null,
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

    /**
     * Called for every kind:1210 row this client DERIVES, so a front end can
     * surface it in the conversation as it happens.
     *
     * Only derived rows come through here, and that is the point. A 1210 that
     * arrives over the wire is an assertion by its sender — see
     * [syncGroupSystemRows] — so a renderer that took its `actor`/`subject`
     * from the payload would let any member forge an attributed history row.
     * These rows are diffed from MLS-authenticated state instead, which is why
     * they are safe to attribute.
     */
    var onSystemRowDerived: ((nostrGroupId: HexKey, row: Event) -> Unit)? = null

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
            ingestDedupStore?.loadAll()?.let { marks ->
                terminallyIngestedMutex.withLock { terminallyIngested.addAll(marks) }
                Unit
            }
            retryPendingPublishObligations()
            Log.d("MarmotManager") { "restoreAll(): done, ${activeIds.size} groups: $activeIds" }
        } catch (e: Exception) {
            Log.e("MarmotManager", "Failed to restore Marmot state", e)
        }
    }

    /**
     * Event ids this client has terminally decided about — see
     * [MarmotIngestDedupStore]. Loaded once in [restoreAll]; the in-memory set
     * is the hot path, the store only makes it survive a restart.
     */
    private val terminallyIngested = mutableSetOf<HexKey>()
    private val terminallyIngestedMutex = Mutex()

    suspend fun isTerminallyIngested(eventId: HexKey): Boolean = terminallyIngestedMutex.withLock { eventId in terminallyIngested }

    suspend fun markTerminallyIngested(eventId: HexKey) {
        val added = terminallyIngestedMutex.withLock { terminallyIngested.add(eventId) }
        if (added) {
            try {
                ingestDedupStore?.mark(eventId)
            } catch (e: Exception) {
                // A marker we failed to persist costs a re-decision next
                // launch; it never costs correctness, so it is not worth
                // failing the ingest over.
                Log.w("MarmotManager", "could not persist ingest marker for ${eventId.take(8)}: ${e.message}", e)
            }
        }
    }

    /**
     * Re-emit every publish obligation a previous run left unresolved.
     *
     * The bytes are republished VERBATIM — the same signed kind-445, to the
     * same recipient scope. That is the whole reason the obligation stores
     * them rather than storing "there was a commit": a replacement commit for
     * the same epoch is a fork against every peer that accepted the first one,
     * and a peer that already has this event simply deduplicates it.
     *
     * A retry that still fails leaves the group in `PendingPublish`, which is
     * the safe direction: it blocks new local commits until the group actually
     * knows what happened to this one.
     */
    suspend fun retryPendingPublishObligations(onlyGroupId: HexKey? = null) {
        val pending = publishGate.allPending().filter { onlyGroupId == null || it.groupId == onlyGroupId }
        if (pending.isEmpty()) return
        Log.d("MarmotManager") { "retryPendingPublishObligations(): ${pending.size} unresolved" }
        for (obligation in pending) {
            val event =
                try {
                    Event.fromJson(obligation.outboundBytes.decodeToString())
                } catch (e: Exception) {
                    // A record we cannot decode can never be republished, and
                    // holding the group in PendingPublish forever helps nobody.
                    Log.w("MarmotManager", "unreadable publish obligation ${obligation.obligationId}: ${e.message}", e)
                    publishGate.resolve(obligation.obligationId, PublishOutcome.FAILED)
                    continue
                }
            val relays = obligation.recipientScope.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
            val confirmed =
                if (relays.isEmpty()) {
                    false
                } else {
                    try {
                        publisher.publish(event, relays)
                    } catch (e: Exception) {
                        Log.w("MarmotManager", "publish retry failed for ${obligation.groupId}: ${e.message}", e)
                        false
                    }
                }
            val state =
                publishGate.resolve(
                    obligation.obligationId,
                    if (confirmed) PublishOutcome.CONFIRMED else PublishOutcome.UNKNOWN,
                )
            // A confirmed retry makes the commit canonical exactly as
            // [commitAndPublish] would, so it owes the same follow-up. Resolving
            // the obligation and stopping there was enough to install the state
            // and no more: the relay's echo of THIS event was never marked
            // processed, so the inbound pipeline met an unknown kind:445 at an
            // epoch we had already merged and opened a convergence pass against
            // ourselves — a restart could put a healthy group into Recovering
            // purely by succeeding.
            if (confirmed) {
                val framedCommit = framedCommitOf(obligation, event)
                if (framedCommit != null) {
                    inboundProcessor.markMessageProcessed(sha256(framedCommit).toHexKey())
                    inboundProcessor.recordLocalCommit(
                        groupId = obligation.groupId,
                        framedCommitBytes = framedCommit,
                        sourceEpoch = obligation.priorState.groupContext.epoch,
                        preState = obligation.priorState,
                    )
                }
                recordRetentionForCurrentEpoch(obligation.groupId)
                syncGroupSystemRows(obligation.groupId, actor = signer.pubKey)
            }
            Log.d("MarmotManager") {
                "retryPendingPublishObligations(): ${obligation.groupId.take(8)}… " +
                    "confirmed=$confirmed lifecycle=$state"
            }
        }
    }

    /**
     * Recover the framed MLS commit from a stored obligation.
     *
     * The obligation keeps the signed kind:445 and the PRE-commit state, not
     * the commit bytes — but that is enough, because the outer envelope was
     * sealed under the pre-commit exporter secret and the pre-commit state
     * derives it. Storing the commit bytes as well would say the same thing
     * twice and change a persisted record's layout for it.
     *
     * Null when the envelope cannot be opened, which should not happen for our
     * own event: the caller then skips the dedup and fork-window bookkeeping
     * rather than guessing at an id.
     */
    private fun framedCommitOf(
        obligation: MarmotPublishObligation,
        event: Event,
    ): ByteArray? =
        try {
            val preCommitKey =
                MlsGroup
                    .restore(obligation.priorState)
                    .exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
            GroupEventEncryption.decrypt(event.content, preCommitKey)
        } catch (e: Exception) {
            Log.w(
                "MarmotManager",
                "could not reopen retried commit for ${obligation.groupId.take(8)}: ${e.message}",
                e,
            )
            null
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
            is GroupEventResult.RefusedByLifecycle,
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
            // An authenticated re-join is what clears a departure gate — the
            // rule `LocalOutboundGate.REMOVED` states, and `LEAVING` needs it
            // just as much: a member who left and was invited back holds a gate
            // raised against a membership that no longer exists. Nothing else
            // clears it, so without this the rejoined group is readable and
            // permanently unsendable, and the gate's durability makes that
            // survive every restart.
            publishGate.clearGate(result.nostrGroupId)
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
    ): OutboundGroupEvent {
        requireOutboundAllowed(nostrGroupId, "send a message")
        return outboundProcessor.buildGroupEvent(nostrGroupId, innerEvent)
    }

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
            RumorAssembler.assembleRumor<Event>(signer.pubKey, template)
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
     * Build the hidden kind:1200 payload that anchors one agent text stream.
     *
     * The start payload is what makes a live preview renderable at all: its
     * own Marmot app event id goes into [AgentTextStreamKeyContextV1], so a
     * receiver can only derive record keys for a stream it has already seen
     * announced inside the group. That is also why the anchor is an ordinary
     * in-group payload rather than something the broker hands out — the broker
     * relays ciphertext and learns nothing.
     *
     * The returned bundle's `innerEvent.id` IS the `start_event_id`; a caller
     * needs it before it can derive the stream's crypto, which is why this
     * returns the built payload instead of publishing and forgetting it.
     *
     * @param brokerCandidates `quic://host:port` endpoints a receiver may try,
     *   in preference order. Zero is valid — the preview is then unavailable
     *   and every member still gets the final message.
     * @param parentEventId the prompt this stream answers, when there is one.
     */
    suspend fun buildAgentStreamStart(
        nostrGroupId: HexKey,
        streamId: HexKey,
        brokerCandidates: List<String> = emptyList(),
        parentEventId: HexKey? = null,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = AgentTextStreamStart.KIND, description = "") {
                    AgentTextStreamStart
                        .tags(streamId, brokerCandidates, parentEventId = parentEventId)
                        .forEach { addUnique(it) }
                }
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<Event>(signer.pubKey, template)
        // The epoch this went out at is the one the stream's key context binds,
        // so remember it the same way an inbound anchor's epoch is remembered.
        val epoch = currentEpoch(nostrGroupId)
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson(), epoch)
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * Build the durable kind:9 that closes an agent text stream out.
     *
     * This is the authoritative message. A receiver that rendered a preview
     * compares its own fold against [transcriptHash] / [chunkCount]: agreement
     * means it saw exactly the stream the publisher sent, disagreement means
     * records were dropped, reordered or injected even though each one opened.
     * A receiver that skipped the preview just reads this as normal chat.
     */
    suspend fun buildAgentStreamFinal(
        nostrGroupId: HexKey,
        streamId: HexKey,
        transcriptHash: HexKey,
        chunkCount: Long,
        text: String,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = 9, description = text) {
                    AgentTextStreamFinal
                        .tags(streamId, transcriptHash, chunkCount)
                        .forEach { addUnique(it) }
                }
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<Event>(signer.pubKey, template)
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * The record AEAD for one stream in [nostrGroupId].
     *
     * The stream secret is the group's own
     * `MLS-Exporter("marmot", "agent-text-stream-quic", 32)`, so every member
     * of the epoch derives the same one and no key ever crosses the wire. All
     * the per-stream separation comes from the key context: change the stream,
     * the epoch, the sender or the anchoring kind:1200 event and the record
     * key changes with it.
     *
     * [senderPubKey] is the stream's author, which is not necessarily us — a
     * receiver derives the publisher's context, not its own.
     */
    fun agentTextStreamCrypto(
        nostrGroupId: HexKey,
        streamId: ByteArray,
        startEventId: ByteArray,
        senderPubKey: HexKey = signer.pubKey,
        epoch: Long? = null,
    ): AgentTextStreamCrypto {
        val group = groupManager.getGroup(nostrGroupId) ?: error("not a member of group $nostrGroupId")
        return AgentTextStreamCrypto(
            streamSecret = group.agentTextStreamSecret(),
            context =
                AgentTextStreamKeyContextV1(
                    groupId = group.groupId,
                    streamId = streamId,
                    mlsEpoch = epoch ?: group.epoch,
                    senderId = senderPubKey.hexToByteArray(),
                    startEventId = startEventId,
                ),
        )
    }

    /** The group's current MLS epoch, which the stream key context binds. */
    fun currentEpoch(nostrGroupId: HexKey): Long? = groupManager.getGroup(nostrGroupId)?.epoch

    /**
     * Build a kind:1009 edit that replaces the text of a prior message.
     *
     * An edit is not chat and must never render as its own row: the
     * replacement is overlaid on the original body, and a reader who was
     * caught up with the original is caught up with the edit. It carries
     * exactly one `e` tag naming its target — an edit that named several would
     * leave every client to pick one, and they would not all pick the same.
     *
     * Authorship is checked at READ time, not here: only the original author
     * may replace their own words, and a receiver enforces that against the
     * message it actually holds rather than trusting the sender to have.
     */
    suspend fun buildMessageEdit(
        nostrGroupId: HexKey,
        targetEventId: HexKey,
        replacement: String,
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = MarmotAppEvent.KIND_EDIT, description = replacement) {
                    addUnique(arrayOf("e", targetEventId))
                }
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<Event>(signer.pubKey, template)
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
        val template = DeletionEvent.build(targetEvents)
        val innerEvent = RumorAssembler.assembleRumor<DeletionEvent>(signer.pubKey, template)
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
    }

    /**
     * A single invitee for [addMemberInvites]: whose KeyPackage is consumed, the bare
     * KeyPackage bytes as published, and the id of the event that carried them
     * (the Welcome must reference it so the invitee can retire that KeyPackage).
     */
    data class MemberInvite(
        val memberPubKey: HexKey,
        val keyPackageBytes: ByteArray,
        val keyPackageEventId: HexKey,
    )

    /**
     * Add a member to a group by consuming their published [KeyPackageEvent].
     *
     * Convenience over [addMember] that handles base64 decoding and lifts the
     * event id into the WelcomeDelivery. Prefer this overload — both the UI's
     * `Account.addMarmotGroupMember` and the CLI's `group add` command call it.
     */
    suspend fun addMember(
        nostrGroupId: HexKey,
        keyPackageEvent: KeyPackageEvent,
        relays: List<NormalizedRelayUrl>,
    ): Pair<OutboundGroupEvent?, WelcomeDelivery?> {
        val (event, welcomes) = addMembers(nostrGroupId, listOf(keyPackageEvent), relays)
        return Pair(event, welcomes.firstOrNull())
    }

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
    ): Pair<OutboundGroupEvent?, WelcomeDelivery?> {
        val (event, welcomes) =
            addMemberInvites(
                nostrGroupId,
                listOf(MemberInvite(memberPubKey, keyPackageBytes, keyPackageEventId)),
                relays,
            )
        return Pair(event, welcomes.firstOrNull())
    }

    /**
     * Add several members to a group in a SINGLE commit, from their published
     * [KeyPackageEvent]s. See [addMemberInvites] for the batching contract.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun addMembers(
        nostrGroupId: HexKey,
        keyPackageEvents: List<KeyPackageEvent>,
        relays: List<NormalizedRelayUrl>,
    ): Pair<OutboundGroupEvent?, List<WelcomeDelivery>> =
        addMemberInvites(
            nostrGroupId = nostrGroupId,
            invites =
                keyPackageEvents.map {
                    MemberInvite(
                        memberPubKey = it.pubKey,
                        keyPackageBytes =
                            kotlin.io.encoding.Base64
                                .decode(it.keyPackageBase64()),
                        keyPackageEventId = it.id,
                    )
                },
            relays = relays,
        )

    /**
     * Add several members to a group in a SINGLE commit.
     *
     * RFC 9420 §12.4.3.1 lets one Commit carry N Add proposals and one Welcome
     * holding N `EncryptedGroupSecrets`, one per added member keyed by their
     * KeyPackage reference. So the group advances by exactly ONE epoch no
     * matter how many people join, and every invitee receives the SAME Welcome
     * bytes — each finds its own secrets entry. MDK (and therefore White
     * Noise) batches this way, so a per-invitee commit loop would diverge from
     * the reference on epoch numbers and round trips alike.
     *
     * Returns the commit GroupEvent that was published, and one WelcomeDelivery
     * per invitee — empty if the commit was not confirmed by any relay.
     *
     * The event is NULL for a founding add, which publishes no commit at all;
     * see [isFoundingAdd]. Callers must treat null as "there is nothing to
     * deliver to peers", not as failure — the Welcomes are the delivery.
     */
    suspend fun addMemberInvites(
        nostrGroupId: HexKey,
        invites: List<MemberInvite>,
        relays: List<NormalizedRelayUrl>,
    ): Pair<OutboundGroupEvent?, List<WelcomeDelivery>> {
        require(invites.isNotEmpty()) { "addMemberInvites: invites must not be empty" }
        require(invites.map { it.memberPubKey }.toSet().size == invites.size) {
            "addMemberInvites: the same member appears twice in one commit"
        }

        // Verify that each KeyPackage credential matches the expected member
        // pubkey. Accepts either framing — a peer's published KeyPackage is an
        // MLSMessage, and bare bytes still arrive from our own pre-fix
        // publications sitting on relays.
        val decoded =
            invites.map { invite ->
                val kp = KeyPackageUtils.decodeKeyPackage(invite.keyPackageBytes)
                val credential = kp.leafNode.credential
                require(credential is Credential.Basic) {
                    "KeyPackage must use BasicCredential"
                }
                require(credential.identity.toHexKey() == invite.memberPubKey) {
                    "KeyPackage credential identity does not match memberPubKey"
                }
                kp
            }

        // The BARE KeyPackages, not the bytes as published. Transport framing
        // is the Marmot layer's business; MLS takes the struct.
        val keyPackageBytes = decoded.map { it.toTlsBytes() }

        if (isFoundingAdd(nostrGroupId)) {
            return commitFoundingAdd(nostrGroupId, invites, relays, keyPackageBytes)
        }

        // Per RFC 9420 §12.4 (and MDK), the outbound kind:445 MUST be
        // outer-encrypted with the pre-commit (epoch-N) exporter secret so
        // that other existing members still at epoch N can decrypt and
        // process the commit. CommitResult.preCommitExporterSecret carries
        // that key.
        val publication =
            commitAndPublish(nostrGroupId, relays) {
                groupManager.stageAddMembers(nostrGroupId, keyPackageBytes)
            }

        // The Welcomes are SEPARATE, retryable per-invitee delivery obligations
        // that only exist once the Add is canonical. A Welcome for an epoch
        // no relay accepted would invite someone into a group that does not
        // exist anywhere else.
        val welcomeDeliveries =
            if (publication.confirmed) {
                invites.mapNotNull { invite ->
                    welcomeSender.wrapWelcome(
                        commitResult = publication.commitResult,
                        recipientPubKey = invite.memberPubKey,
                        keyPackageEventId = invite.keyPackageEventId,
                        relays = relays,
                        nostrGroupId = nostrGroupId,
                    )
                }
            } else {
                emptyList()
            }

        return Pair(publication.event, welcomeDeliveries)
    }

    /**
     * Is the next Add the group's FOUNDING Add — the one immediately after
     * one-member epoch-0 creation?
     *
     * True only while the group is at epoch 0 and the creator is its sole
     * member. Both conditions matter: epoch 0 alone is not enough, because a
     * group that has already merged its founding Add is at epoch 1, and a
     * sole-member group at a later epoch (everyone else removed) is an
     * ordinary group whose commits peers may still be waiting for.
     */
    private fun isFoundingAdd(nostrGroupId: HexKey): Boolean {
        val group = groupManager.getGroup(nostrGroupId) ?: return false
        return group.epoch == 0L && group.currentMemberIdentities().size == 1
    }

    /**
     * Merge the founding Add Commit locally and send only the Welcomes.
     *
     * `protocol-core/publish-lifecycle.md`: "When founding creation includes
     * initial invitees, the creator next prepares and locally merges one
     * founding Add Commit from epoch 0 to epoch 1. That Commit also has an
     * empty group-message publication obligation: the creator is the only
     * pre-existing member, so no peer can be forked by failure to publish it."
     *
     * So this is NOT publish-before-apply. There is no peer at epoch 0 to fork,
     * and every invitee learns the epoch-1 state from the Welcome's GroupInfo
     * and ratchet tree rather than from the commit. Publishing it anyway did
     * three bad things: it made group creation with invitees depend on a relay
     * acknowledgement that the spec does not require, so a creation against an
     * unreachable relay silently produced an empty epoch-0 group; it spent a
     * signature and an outer encryption on bytes with no audience; and it left
     * a kind:445 on relays that a joiner can receive BEFORE its Welcome, which
     * the reference implementation calls a "welcome-before-commit AlreadyAtEpoch
     * bounce" and avoids for the same reason.
     *
     * The exception stops here. This is the last commit that skips the publish
     * obligation; every later one takes [commitAndPublish].
     */
    private suspend fun commitFoundingAdd(
        nostrGroupId: HexKey,
        invites: List<MemberInvite>,
        relays: List<NormalizedRelayUrl>,
        keyPackageBytes: List<ByteArray>,
    ): Pair<OutboundGroupEvent?, List<WelcomeDelivery>> {
        requireOutboundAllowed(nostrGroupId, "add the founding members")
        Log.d("MarmotManager") {
            "commitFoundingAdd($nostrGroupId): ${invites.size} founding invitee(s), merging locally"
        }

        val staged = groupManager.stageAddMembers(nostrGroupId, keyPackageBytes)

        // Straight to canonical. No obligation is prepared, so there is no
        // record that could later be retried or resolved — which is the point:
        // an obligation with no recipients is one the gate would have to
        // invent an outcome for.
        groupManager.installState(nostrGroupId, staged.pendingState)
        publishGate.satisfyEmptyObligation(nostrGroupId)

        // The same derived rows a confirmed commit gets. Deliberately WITHOUT
        // recordLocalCommit and markMessageProcessed: both exist to reconcile
        // a commit that went to relays, and this one never did.
        recordRetentionForCurrentEpoch(nostrGroupId)
        syncGroupSystemRows(nostrGroupId, actor = signer.pubKey)

        // Each Welcome is its own retryable per-invitee delivery obligation,
        // and unlike the published path they are sent unconditionally: the Add
        // is already canonical, so there is no "commit nobody accepted" case in
        // which a Welcome would invite someone into a group that exists nowhere.
        val welcomeDeliveries =
            invites.mapNotNull { invite ->
                welcomeSender.wrapWelcome(
                    commitResult = staged.result,
                    recipientPubKey = invite.memberPubKey,
                    keyPackageEventId = invite.keyPackageEventId,
                    relays = relays,
                    nostrGroupId = nostrGroupId,
                )
            }

        return Pair(null, welcomeDeliveries)
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
        recordRetentionForCurrentEpoch(nostrGroupId)
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
        recordRetentionForCurrentEpoch(nostrGroupId)
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
        /**
         * The one gate this commit is allowed to pass. Only the disband path
         * uses it, and only for its own `Disbanding` gate — the gate exists to
         * carry that request, so it must not block it.
         */
        ignoringGate: LocalOutboundGate? = null,
        stage: suspend () -> MlsGroupManager.StagedCommit,
    ): CommitPublication {
        requireOutboundAllowed(nostrGroupId, "commit a group-state change", ignoringGate)
        // A commit whose publish went unconfirmed leaves the group in
        // `PendingPublish`, which correctly refuses new commits — but the only
        // thing that ever resolved it was `restoreAll`, so one dropped socket
        // wedged the group until the app was restarted. Retrying this group's
        // obligations here makes the next attempt the recovery: republishing
        // the same event is safe (a peer deduplicates it by id) and a retry
        // that still fails leaves the group held exactly as before.
        if (publishGate.lifecycle(nostrGroupId) == GroupLifecycleState.PENDING_PUBLISH) {
            retryPendingPublishObligations(onlyGroupId = nostrGroupId)
        }
        check(publishGate.canPrepareLocalCommit(nostrGroupId, ignoringGate)) {
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
            // Not confirmed is NOT the same as rejected: a timeout or a
            // dropped connection leaves us unable to say whether a peer took
            // the commit, so the obligation stays retryable.
            if (confirmed) PublishOutcome.CONFIRMED else PublishOutcome.UNKNOWN,
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
            // Our own change is canonical state now, so it gets the same
            // derived rows a peer's commit would. The actor is known here in a
            // way it is not for an inbound commit, which is what lets a
            // self-removal read as "left" rather than "removed".
            recordRetentionForCurrentEpoch(nostrGroupId)
            syncGroupSystemRows(nostrGroupId, actor = signer.pubKey)
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
                // Settlement is the only moment a pending disband can be
                // decided: the branch is chosen, so the request has either won,
                // lost and needs regenerating, or become impossible. Doing it
                // here is what makes the request survive a losing branch
                // instead of being dropped with the pass.
                val outcome = resolveDisbandRequest(resolution.groupId)
                if (outcome != DisbandResolution.NOT_REQUESTED) {
                    Log.d("MarmotManager") {
                        "disband request for ${resolution.groupId.take(8)}… settled as $outcome"
                    }
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
                // Re-check under the flag before releasing it. Between the loop
                // deciding it has nothing left and the flag being cleared, a new
                // pass can open and its startConvergenceSettler() lose the
                // compareAndSet — leaving an open pass with no carrier until
                // unrelated traffic happened to start another. Looping here
                // closes that window: whoever holds the flag keeps working until
                // a check finds nothing left AFTER the flag has been given up.
                do {
                    driveConvergenceToSettlement()
                    settlerRunning.value = false
                    if (inboundProcessor.openConvergencePasses().isEmpty()) break
                    // Something arrived in the gap. Take the flag again if it is
                    // still free; if another carrier got it first, it now owns
                    // the work and this one can stop.
                } while (settlerRunning.compareAndSet(expect = false, update = true))
            } catch (e: Exception) {
                settlerRunning.value = false
                throw e
            }
        }
    }

    private val settlerRunning = MutableStateFlow(false)

    /**
     * The group's effective lifecycle state.
     *
     * Two components track lifecycle for different reasons and neither is the
     * whole answer on its own: the publish gate owns the local-publish states
     * (`PendingPublish`, `Merging`), convergence owns `Recovering` and the two
     * terminal-ish states (`Disbanded`, `Unrecoverable`). Reading only the
     * publish gate — as this used to — meant a disbanded or unrecoverable
     * group still reported `Stable` and every outbound gate keyed on it
     * happily let work through.
     *
     * Terminal wins: a group that convergence has terminalized is terminal
     * regardless of what the publish gate is doing, because the publish that
     * gate is tracking can no longer be applied to anything.
     */
    suspend fun lifecycle(nostrGroupId: HexKey): GroupLifecycleState {
        val converged = inboundProcessor.groupLifecycle(nostrGroupId)
        if (converged == GroupLifecycleState.DISBANDED || converged == GroupLifecycleState.UNRECOVERABLE) {
            return converged
        }
        val publish = publishGate.lifecycle(nostrGroupId)
        return if (publish == GroupLifecycleState.STABLE) converged else publish
    }

    /**
     * Refuse outbound work a group's lifecycle does not permit.
     *
     * The check is here rather than at each call site because every outbound
     * path has the same answer: a `Disbanded` group takes no further work of
     * any kind, and an `Unrecoverable` one takes none until it is repaired —
     * encrypting against state we do not trust produces a message the group
     * will invalidate, which is worse than refusing.
     */
    private suspend fun requireOutboundAllowed(
        nostrGroupId: HexKey,
        what: String,
        ignoringGate: LocalOutboundGate? = null,
    ) {
        when (val state = lifecycle(nostrGroupId)) {
            GroupLifecycleState.DISBANDED ->
                throw IllegalStateException("Group $nostrGroupId is disbanded; cannot $what")

            GroupLifecycleState.UNRECOVERABLE ->
                throw IllegalStateException(
                    "Group $nostrGroupId is unrecoverable locally; repair, restore or rejoin before you $what",
                )

            else -> Log.d("MarmotManager") { "$what allowed for ${nostrGroupId.take(8)}… in $state" }
        }

        // A durable gate blocks outbound work without being a lifecycle state:
        // the member is still in the tree, the group is not terminal, and yet
        // nothing new may be sent. `Disbanding` is the one that matters here —
        // it survives a publish no relay took, so a group whose ending is still
        // pending must not accept messages in the meantime, which is exactly
        // the window where a member would otherwise keep talking into a
        // conversation an admin has already ended.
        val gate = publishGate.outboundGate(nostrGroupId)
        if (gate != null && gate != ignoringGate && gate.blocksOutbound) {
            throw IllegalStateException(
                when (gate) {
                    LocalOutboundGate.DISBANDING ->
                        "Group $nostrGroupId is being disbanded; cannot $what until that resolves"

                    LocalOutboundGate.LEAVING ->
                        "You are leaving group $nostrGroupId; cannot $what"

                    LocalOutboundGate.REMOVED ->
                        "You are no longer a member of group $nostrGroupId; cannot $what"
                },
            )
        }
    }

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
        /**
         * The MLS epoch that delivered this payload, when the caller knows it.
         * Only agent text streams read it back — their record key context
         * binds the epoch, so a receiver has to derive keys under the epoch
         * that carried the stream's anchor rather than the group's current one.
         */
        epoch: Long? = null,
    ) {
        try {
            messageStore?.appendMessage(nostrGroupId, innerEventJson)
            val parsed = Event.fromJsonOrNull(innerEventJson)
            if (epoch != null && parsed != null) {
                messageStore?.recordEpoch(nostrGroupId, parsed.id, epoch)
            }
            // Pinned here, at the moment the message enters the log, because
            // this is the last point at which the retention of its delivering
            // epoch is still the group's current retention.
            parsed?.let { pinExpiry(nostrGroupId, it, epoch) }
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to persist Marmot message for $nostrGroupId", e)
        }
    }

    /**
     * The winning edit for every message a set of app events edits.
     *
     * Returns `target event id → replacement text`. Two rules do the work, and
     * both are read-side because a sender cannot be trusted to have applied
     * them:
     *
     * - **Authorship is by ACCOUNT.** Only the account that wrote a message may
     *   replace it. A second device of the same account holds a different leaf
     *   and may still edit its own account's words; any other account's edit is
     *   ignored outright, or every member could rewrite anyone.
     * - **The latest edit wins, with the event id breaking a tie.** Two devices
     *   of one account can stamp the same second, and without a deterministic
     *   rule two readers would render different text for the same message
     *   forever.
     *
     * [messages] is the group's decrypted app events — the originals and the
     * edits together, since an edit is only authorized against the message it
     * targets.
     */
    fun editOverlays(messages: List<Event>): Map<HexKey, String> {
        val authorOf = HashMap<HexKey, HexKey>(messages.size)
        val edits = HashMap<HexKey, MutableList<MarmotMessageEdit>>()
        for (event in messages) {
            if (event.kind == MarmotAppEvent.KIND_EDIT) {
                val edit =
                    MarmotMessageEdit.fromAppEvent(MarmotAppEvent.fromEvent(event))
                        ?: continue
                edits.getOrPut(edit.targetId) { mutableListOf() }.add(edit)
            } else {
                authorOf[event.id] = event.pubKey
            }
        }
        val overlays = HashMap<HexKey, String>(edits.size)
        for ((targetId, candidates) in edits) {
            // An edit for a message this client does not hold is not applied.
            // It is not dropped as invalid either — the target may simply not
            // have arrived yet — it just has nothing to overlay.
            val originalAuthor = authorOf[targetId] ?: continue
            val authorized = candidates.filter { MarmotMessageEdit.isAuthorized(it, originalAuthor) }
            MarmotMessageEdit.selectOverlay(authorized)?.let { overlays[targetId] = it.replacement }
        }
        return overlays
    }

    /**
     * The ids of messages a kind:5 in [messages] retracted.
     *
     * Deletion is the mirror of [editOverlays] and follows the same authorship
     * rule, for the same reason: a retraction is authorized by Marmot ACCOUNT
     * identity, so a second device of the same account may retract its own
     * account's message and no other account's deletion is honoured. Without
     * that check any member could erase anyone's words by publishing a kind:5
     * naming them.
     *
     * MDK additionally honours an *admin moderation* delete when the deleter
     * held an authenticated moderation grant frozen at ingest. We do not issue
     * or track that grant, so a cross-author delete is ignored here rather than
     * guessed at — ignoring one MDK would have applied hides a message less
     * often than applying one it would have rejected erases a message wrongly.
     *
     * A deletion naming a message this client does not hold contributes
     * nothing: it is not invalid, the target may simply not have arrived yet,
     * and this is recomputed from the whole stored log on every read, so it
     * resolves as soon as the target lands.
     *
     * [messages] is the group's decrypted app events — the deletions and their
     * targets together, since a deletion is only authorized against the message
     * it names.
     */
    fun deletedIds(messages: List<Event>): Set<HexKey> {
        val authorOf = HashMap<HexKey, HexKey>(messages.size)
        val claims = ArrayList<Pair<HexKey, HexKey>>()
        for (event in messages) {
            if (event.kind == DeletionEvent.KIND) {
                for (tag in event.tags) {
                    if (tag.size >= 2 && tag[0] == "e") claims.add(tag[1] to event.pubKey)
                }
            } else {
                authorOf[event.id] = event.pubKey
            }
        }
        val deleted = HashSet<HexKey>(claims.size)
        for ((targetId, deleter) in claims) {
            if (authorOf[targetId] == deleter) deleted.add(targetId)
        }
        return deleted
    }

    /**
     * The slice of canonical group state that kind:1210 rows are derived from,
     * or null when this client is not in the group.
     */
    fun groupSnapshot(nostrGroupId: HexKey): MarmotGroupSnapshot? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        // Name and admins come from [groupView], not from the components
        // alone: a legacy group keeps both inside `0xF2EE`, and reading the
        // current profile's components there would report a nameless group
        // that never changes — so a rename would derive no row at all.
        val view = groupView(nostrGroupId)
        return MarmotGroupSnapshot.of(
            state = group.currentGroupState(),
            memberAccounts = memberPubkeys(nostrGroupId).map { it.pubkey },
            name = view?.name.orEmpty(),
            admins = view?.adminPubkeys.orEmpty(),
        )
    }

    /**
     * Derive the kind:1210 rows for whatever changed since this client last
     * looked, append them to the local log, and record the new baseline.
     *
     * System rows are synthesized from canonical group state, never received:
     * a row derived from an MLS-authenticated commit cannot be forged by one
     * member, and every client that applied the same commits derives the same
     * rows. A client MUST NOT wait for a 1210 *message* to learn that state
     * changed — one that arrives over the wire is an assertion by its sender,
     * not a derived fact.
     *
     * Diffing against a stored baseline rather than against the pre-commit
     * state in hand is what makes this safe to call at any time: it is
     * idempotent, it survives a restart mid-transition, and it cannot
     * double-write a row for a change it already described.
     *
     * The very first call on a group establishes the baseline and writes
     * nothing. Announcing every existing member as newly added would be a
     * timeline full of events that did not happen.
     */
    suspend fun syncGroupSystemRows(
        nostrGroupId: HexKey,
        /** The committer, when the caller knows it. An unattributed row is still true. */
        actor: HexKey? = null,
    ): List<MarmotSystemEvent> {
        val store = messageStore ?: return emptyList()
        val current = groupSnapshot(nostrGroupId) ?: return emptyList()
        return try {
            val stored = store.loadGroupSnapshot(nostrGroupId)
            val baseline = stored?.let { MarmotGroupSnapshot.decode(it) }
            if (baseline == null) {
                store.recordGroupSnapshot(nostrGroupId, current.encode())
                return emptyList()
            }
            val rows = MarmotSystemRowDiff.diff(baseline, current, actor)
            val now = TimeUtils.now()
            for (row in rows) {
                // The row is attributed to the committer when there is one.
                // With no actor it is still attributed to somebody, because an
                // app event has a pubkey — this client, whose local derivation
                // it is.
                val appEvent = row.toAppEvent(actor ?: signer.pubKey, now)
                val json = appEvent.toJson().dropLast(1) + ",\"sig\":\"\"}"
                persistDecryptedMessage(nostrGroupId, json)
                // Surface it now as well as persisting it. Without this the
                // row appears only after a restart re-reads the log, which is
                // the wrong moment to learn that someone was removed.
                Event.fromJsonOrNull(json)?.let { onSystemRowDerived?.invoke(nostrGroupId, it) }
            }
            store.recordGroupSnapshot(nostrGroupId, current.encode())
            rows
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to sync Marmot system rows for $nostrGroupId", e)
            emptyList()
        }
    }

    /** Inner event id → delivering MLS epoch, for whatever the store kept. */
    suspend fun storedEpochs(nostrGroupId: HexKey): Map<String, Long> =
        try {
            messageStore?.loadEpochs(nostrGroupId) ?: emptyMap()
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to read Marmot message epochs for $nostrGroupId", e)
            emptyMap()
        }

    /**
     * Load all persisted inner event JSONs for a group, in append order.
     * Returns an empty list if no message store is configured or none exist.
     */
    suspend fun loadStoredMessages(nostrGroupId: HexKey): List<String> =
        try {
            val store = messageStore ?: return emptyList()
            // Prune before reading, so a restart cannot show a message that
            // expired while the app was closed. Filtering the read alone would
            // leave it on disk, and disk is the whole point: the ratchet moved
            // past the ciphertext long ago, so this store is the only copy.
            pruneExpiredMessages(nostrGroupId)
            store.loadMessages(nostrGroupId)
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to load persisted messages for $nostrGroupId", e)
            emptyList()
        }

    /**
     * The group's disappearing-message duration in seconds, or 0 when off.
     *
     * Read from the current profile's `0x8005` component, falling back to a
     * legacy group's `0xF2EE` field — a legacy group carries the same setting
     * in the monolithic blob, and reading only the component would silently
     * treat every legacy group as having no expiry at all.
     */
    fun retentionSeconds(nostrGroupId: HexKey): Long {
        val fromComponent = groupState(nostrGroupId)?.retention?.disappearingMessageSecs
        if (fromComponent != null) return fromComponent.toLong()
        return groupMetadata(nostrGroupId)?.disappearingMessageSecs?.toLong() ?: 0L
    }

    /**
     * Pin when [innerEvent] stops being displayable, if this group expires
     * messages at all.
     *
     * Pinned at persist time and never recomputed, because the component says
     * a message keeps the retention of its OWN source epoch: a later change to
     * the setting must not shorten, extend, or restore the expiry of a message
     * that already exists.
     *
     * The base is the sender's own `created_at`, which the component
     * acknowledges is only as trustworthy as the MLS-authenticated sender —
     * expiry is advisory, not a deletion guarantee against a hostile member.
     */
    private suspend fun pinExpiry(
        nostrGroupId: HexKey,
        innerEvent: Event,
        sourceEpoch: Long?,
    ) {
        val seconds = retentionForEpoch(nostrGroupId, sourceEpoch)
        if (seconds <= 0L) return
        try {
            messageStore?.recordExpiry(nostrGroupId, innerEvent.id, innerEvent.createdAt + seconds)
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to pin expiry for ${innerEvent.id} in $nostrGroupId", e)
        }
    }

    /**
     * The retention that applied at [sourceEpoch] — the epoch that DELIVERED
     * the message — falling back to the group's current value.
     *
     * The distinction only shows up on a message decrypted late: a retained
     * candidate, or a replay after a restart, arrives under an epoch the group
     * has since moved past. Pinning it to today's setting is exactly what the
     * component forbids, so the recorded history wins whenever it has an entry
     * for that epoch. The fallback is not a shrug — a group whose setting never
     * changed has one value at every epoch, and that is the overwhelmingly
     * common case.
     */
    private suspend fun retentionForEpoch(
        nostrGroupId: HexKey,
        sourceEpoch: Long?,
    ): Long {
        if (sourceEpoch != null) {
            try {
                messageStore?.loadEpochRetentions(nostrGroupId)?.get(sourceEpoch)?.let { return it }
            } catch (e: Exception) {
                Log.w("MarmotManager", "Failed to read epoch retentions for $nostrGroupId", e)
            }
        }
        return retentionSeconds(nostrGroupId)
    }

    /**
     * Write down what this group's retention is at its current epoch.
     *
     * Called wherever the epoch may just have advanced, so the history has an
     * entry before any message delivered under that epoch needs one.
     */
    suspend fun recordRetentionForCurrentEpoch(nostrGroupId: HexKey) {
        val store = messageStore ?: return
        val epoch = currentEpoch(nostrGroupId) ?: return
        try {
            store.recordEpochRetention(nostrGroupId, epoch, retentionSeconds(nostrGroupId))
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to record retention at epoch $epoch for $nostrGroupId", e)
        }
    }

    /**
     * Every pinned expiry in the group, keyed by inner event id.
     *
     * Messages with no entry never expire — either the group has no retention
     * policy or none applied at the epoch that delivered them.
     */
    suspend fun messageExpiries(nostrGroupId: HexKey): Map<HexKey, Long> =
        try {
            messageStore?.loadExpiries(nostrGroupId) ?: emptyMap()
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to read expiries for $nostrGroupId", e)
            emptyMap()
        }

    /**
     * Delete every message whose pinned expiry has passed.
     *
     * @return the inner event ids that were removed, so a front end can drop
     *   them from a conversation it is already showing rather than waiting for
     *   the next read.
     */
    suspend fun pruneExpiredMessages(
        nostrGroupId: HexKey,
        nowSecs: Long = TimeUtils.now(),
    ): Set<HexKey> {
        val store = messageStore ?: return emptySet()
        return try {
            val expired = store.loadExpiries(nostrGroupId).filterValues { it <= nowSecs }.keys
            if (expired.isEmpty()) return emptySet()
            store.removeMessages(nostrGroupId, expired)
            Log.d("MarmotManager") { "expired ${expired.size} message(s) in ${nostrGroupId.take(8)}…" }
            onMessagesExpired?.invoke(nostrGroupId, expired)
            expired
        } catch (e: Exception) {
            Log.w("MarmotManager", "Failed to expire messages for $nostrGroupId", e)
            emptySet()
        }
    }

    /**
     * Called for every message this client expires, so a front end can drop it
     * from a conversation that is already on screen.
     */
    var onMessagesExpired: ((nostrGroupId: HexKey, innerEventIds: Set<HexKey>) -> Unit)? = null

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
        /**
         * The plain-https avatar (`0x8007`), or null when the group carries
         * none. Absent-but-present state reads as null here: a cleared avatar
         * and no avatar look identical to a renderer, and the difference only
         * matters to the codec.
         *
         * When this and [image] are both set, this one wins — see
         * [MarmotGroupState.preferredAvatar].
         */
        val avatarUrl: GroupAvatarUrlV1?,
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
            avatarUrl = state.avatarUrl?.takeIf { !it.isAbsent },
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
     * Set the group's disappearing-message duration, in seconds. `0` disables.
     *
     * Admin-only, both here and at every peer. Changing it is explicitly a
     * mid-life operation the component allows, and it is NOT retroactive: each
     * message already pins the retention of the epoch that delivered it, so a
     * change from here only governs messages delivered by the epoch this
     * commit opens. [commitAndPublish] records the new value against that epoch
     * on confirmation, which is what later arrivals under it read.
     *
     * A legacy group carries the same setting inside the monolithic `0xF2EE`
     * blob, so it is rewritten whole there — with the MIP-01 spelling, where
     * "off" is an absent field rather than a zero.
     */
    suspend fun setMessageRetention(
        nostrGroupId: HexKey,
        disappearingMessageSecs: ULong,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        check(signer.pubKey in view.adminPubkeys) {
            "Only an admin of group $nostrGroupId can change its message retention"
        }
        if (!view.isCurrentProfile) {
            val legacy =
                groupMetadata(nostrGroupId)
                    ?: throw IllegalStateException("Legacy group $nostrGroupId has no MarmotGroupData")
            return updateGroupMetadata(
                nostrGroupId,
                legacy.copy(disappearingMessageSecs = disappearingMessageSecs.takeIf { it > 0uL }),
                relays,
            )
        }
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(
                nostrGroupId,
                MessageRetentionV1.COMPONENT_ID,
                MessageRetentionV1(disappearingMessageSecs).encode(),
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
     * Commit whatever proposals are staged for this group, if any.
     *
     * The case that matters is a departing member's standalone `SelfRemove`
     * (MIP-03): the leaver cannot evict themselves — a proposal advances
     * nothing — so it sits in the pool until an authorized member commits it.
     * Until that happens the leaver is STILL IN THE TREE and still able to
     * decrypt everything the group sends, which is the opposite of what
     * leaving is for.
     *
     * Returns null when there is nothing staged, so a caller can drive this
     * unconditionally after ingest without checking first.
     */
    suspend fun commitPendingProposals(
        nostrGroupId: HexKey,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent? {
        if (!groupManager.hasPendingProposals(nostrGroupId)) return null
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageCommit(nostrGroupId)
        }.event
    }

    /**
     * Disband the group: write `marmot.group.lifecycle.v1` (`0x800c`) as
     * `disbanded` in a Commit every member replays.
     *
     * Disband is ABSORBING and irreversible. There is no un-disband commit and
     * no later branch that supersedes it — a replacement conversation is a new
     * MLS group with a new id. So this is deliberately the only writer of that
     * component, it refuses to run twice, and the caller is expected to have
     * confirmed with a human first.
     *
     * Only an admin may do it. The check is local *as well as* remote: peers
     * reject a non-admin's lifecycle change anyway, but a non-admin who got
     * this far would burn an epoch and desync themselves for a commit nobody
     * applies, which is a worse failure than an exception.
     *
     * Current profile only — MIP-01's `0xF2EE` blob has no lifecycle field, so
     * a legacy group genuinely cannot express "disbanded" and this refuses
     * rather than writing the state somewhere no peer reads.
     *
     * The commit takes the normal publish-before-apply path, so a disband that
     * no relay acknowledged does not terminalize the group locally either —
     * exactly the outcome we want, since a locally-disbanded group nobody else
     * heard about would be unreachable state.
     *
     * The Commit itself is not a bare lifecycle update: `group-lifecycle-v1.md`
     * fixes its whole shape — the lifecycle update, a full admin-policy
     * replacement naming only the committer, and a Remove for every other leaf
     * — and a peer rejects anything else as an unsupported lifecycle
     * transition. See [MlsGroupManager.stageDisband].
     */
    suspend fun disbandGroup(
        nostrGroupId: HexKey,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        check(view.isCurrentProfile) {
            "Group $nostrGroupId is a legacy MIP-01 group and has no carrier for a lifecycle state"
        }
        check(signer.pubKey in view.adminPubkeys) {
            "Only an admin of group $nostrGroupId can disband it"
        }
        check(groupManager.getGroup(nostrGroupId)?.currentGroupState()?.isDisbanded != true) {
            "Group $nostrGroupId is already disbanded"
        }
        // requireOutboundAllowed also refuses an Unrecoverable group, which is
        // the point: disbanding from state we do not trust would publish a
        // terminal commit off a fork.
        requireOutboundAllowed(nostrGroupId, "disband the group", ignoringGate = LocalOutboundGate.DISBANDING)

        // The `Disbanding` gate goes up FIRST and durably. The request is the
        // irreversible thing a human authorized, and it has to outlive
        // everything that can go wrong after this line: a publish no relay
        // acknowledges, a crash, a restart, and a branch race this commit
        // loses. Raising it afterwards would leave the one window where a
        // crash loses the intent entirely and the next start offers the group
        // as ordinarily live.
        publishGate.raiseGate(nostrGroupId, LocalOutboundGate.DISBANDING)

        // The disband Commit is only valid when `0x800c` is ALREADY required in
        // the candidate parent, so a group that predates the component needs
        // the enablement Commit of its own first. Every group this client
        // creates requires it from epoch 0, so this is the older-group and
        // other-implementation path, not the common one.
        if (groupState(nostrGroupId)?.requires(GroupLifecycleV1.COMPONENT_ID) != true) {
            val enablement =
                commitAndPublish(nostrGroupId, relays, ignoringGate = LocalOutboundGate.DISBANDING) {
                    groupManager.stageEnableDisbanding(nostrGroupId)
                }
            check(enablement.confirmed) {
                "Could not enable disbanding on group $nostrGroupId: no relay acknowledged the enablement " +
                    "commit, so the group is unchanged and still live"
            }
        }

        val publication =
            commitAndPublish(nostrGroupId, relays, ignoringGate = LocalOutboundGate.DISBANDING) {
                groupManager.stageDisband(nostrGroupId)
            }
        // An unacknowledged publish is NOT a failed request any more. The
        // commit stays a retryable obligation and the gate keeps the request
        // alive across restarts, so this reports what happened instead of
        // throwing the intent away — the caller reads [isDisbanding] and
        // [lifecycle] to tell "ended" from "ending".
        if (!publication.confirmed) {
            Log.w("MarmotManager") {
                "disbandGroup($nostrGroupId): no relay acknowledged the commit — the request stays " +
                    "durable behind the Disbanding gate and retries with the obligation"
            }
        }

        // Start the carrier ourselves. Applying this Commit opened a bounded
        // convergence pass, and terminalization now happens only when that pass
        // SETTLES — but the settler is otherwise started by inbound traffic
        // that detected a fork, and this pass has neither. Without this the
        // group sits in `Recovering` behind its own `Disbanding` gate
        // indefinitely: nothing may be sent to it and it never ends. A front
        // end that drives settlement itself (the CLI does) would not notice;
        // the app would.
        startConvergenceSettler()
        return publication.event
    }

    /**
     * The epoch each pending disband request was last prepared against, so a
     * regeneration happens at most once per epoch. See [resolveDisbandRequest].
     */
    private val disbandPreparedEpoch = mutableMapOf<HexKey, Long>()

    /** True while an irreversible disband request for [nostrGroupId] is unresolved. */
    suspend fun isDisbanding(nostrGroupId: HexKey): Boolean = publishGate.outboundGate(nostrGroupId) == LocalOutboundGate.DISBANDING

    /**
     * Resolve a pending disband request against the branch convergence just
     * selected.
     *
     * Three outcomes, and the middle one is why this exists:
     *
     * - the selected branch carries the disband → the request succeeded. The
     *   gate comes down; `Disbanded` is already set by the engine, and it is
     *   absorbing, so nothing else is needed.
     * - an ACTIVE branch was selected → our Commit lost. The spec says an
     *   authorized client regenerates it against the selected state, which is
     *   what this does; the gate stays up meanwhile, so the group is not
     *   offered as ordinarily live between attempts.
     * - we are no longer an admin or no longer a member → the request has
     *   become impossible. It ends as a local failure with the gate cleared,
     *   rather than retrying forever against a group that will never accept it.
     *
     * "If any valid disband branch is selected, the request succeeds regardless
     * of which admin authored the selected Commit" — so this deliberately reads
     * the SELECTED STATE rather than tracking whether our own bytes won.
     */
    suspend fun resolveDisbandRequest(nostrGroupId: HexKey): DisbandResolution {
        if (!isDisbanding(nostrGroupId)) return DisbandResolution.NOT_REQUESTED

        if (groupManager.getGroup(nostrGroupId)?.currentGroupState()?.isDisbanded == true) {
            publishGate.clearGate(nostrGroupId)
            disbandPreparedEpoch.remove(nostrGroupId)
            return DisbandResolution.DISBANDED
        }

        val view = groupView(nostrGroupId)
        if (view == null || signer.pubKey !in view.adminPubkeys) {
            // Not an error worth throwing from a settlement loop: the group
            // outlived the requester's authority over it, which is a real
            // outcome the caller has to surface rather than retry.
            publishGate.clearGate(nostrGroupId)
            disbandPreparedEpoch.remove(nostrGroupId)
            Log.w("MarmotManager") {
                "disband request for $nostrGroupId is impossible: no longer an admin or no longer a member"
            }
            return DisbandResolution.IMPOSSIBLE
        }

        // Still pending and still authorized: regenerate against the selected
        // state. A commit still in flight is left alone — republishing the same
        // epoch twice is the fork this gate exists to prevent.
        //
        // The predicate is the PUBLISH gate's, deliberately, not [lifecycle]'s.
        // A group that has just settled a pass still reads `Recovering` from
        // the convergence engine — nothing resets that to `Stable` when a pass
        // ends — so gating on the reported lifecycle would mean never
        // regenerating anything, which is the whole feature. What actually
        // decides whether a new commit may be prepared is an unresolved publish
        // obligation, and that is what this asks about.
        if (!publishGate.canPrepareLocalCommit(nostrGroupId, LocalOutboundGate.DISBANDING)) {
            return DisbandResolution.PENDING
        }

        // ONE attempt per epoch. Regenerating opens a fresh convergence pass,
        // and settling that pass calls back here — so without this the two
        // spin against each other forever: settle, regenerate, settle,
        // regenerate, with the group's epoch stuck wherever the competing
        // branch left it. (MDK bounds the same loop the same way, with
        // `DisbandRequest.last_prepared_epoch`.)
        //
        // Waiting for a NEW epoch is also the right trigger on its merits: a
        // regeneration that would authenticate against the same parent as the
        // attempt that just lost is the same commit, and it would lose again.
        val epoch = currentEpoch(nostrGroupId)
        if (epoch != null && disbandPreparedEpoch[nostrGroupId] == epoch) return DisbandResolution.PENDING
        if (epoch != null) disbandPreparedEpoch[nostrGroupId] = epoch

        return try {
            commitAndPublish(
                nostrGroupId,
                groupRelays(nostrGroupId),
                ignoringGate = LocalOutboundGate.DISBANDING,
            ) { groupManager.stageDisband(nostrGroupId) }
            // Same reason as in [disbandGroup]: the regenerated Commit opens a
            // pass of its own that nothing else would carry to settlement.
            startConvergenceSettler()
            DisbandResolution.PENDING
        } catch (e: Exception) {
            Log.w("MarmotManager", "could not regenerate the disband commit for $nostrGroupId", e)
            DisbandResolution.PENDING
        }
    }

    /** What [resolveDisbandRequest] concluded. */
    enum class DisbandResolution {
        /** No disband request is pending for this group. */
        NOT_REQUESTED,

        /** A disband branch was selected. The group is terminal. */
        DISBANDED,

        /** Still unresolved — regenerated, in flight, or waiting on a pass. */
        PENDING,

        /** The requester is no longer an admin or no longer a member. */
        IMPOSSIBLE,
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

    /**
     * Set or clear the group's plain-https avatar (`marmot.group.avatar-url.v1`).
     *
     * [avatar] null clears it by writing the canonical EMPTY state rather than
     * removing the component. That is the spec's own clear ("Clearing the
     * avatar sends the empty state"), and removal is not a free substitute for
     * it: a component MUST NOT be removed while `app_components` still lists it
     * as required, so a remove would only be legal in the same Commit that
     * stopped requiring it.
     *
     * There is no legacy carrier for this. MIP-01's `0xF2EE` blob had only the
     * encrypted-Blossom fields, so a legacy group genuinely cannot hold a URL
     * avatar and this refuses rather than silently writing somewhere else.
     */
    suspend fun setGroupAvatarUrl(
        nostrGroupId: HexKey,
        avatar: GroupAvatarUrlV1?,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        check(view.isCurrentProfile) {
            "Group $nostrGroupId is a legacy MIP-01 group and has no carrier for a URL avatar"
        }
        // Encode before staging: an invalid or non-normalizable URL should fail
        // the caller here, not halfway through building a Commit.
        val encoded = (avatar?.takeIf { !it.isAbsent } ?: GroupAvatarUrlV1.ABSENT).encode()
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(
                nostrGroupId,
                GroupAvatarUrlV1.COMPONENT_ID,
                encoded,
            )
        }.event
    }

    /**
     * Set or replace the group's encrypted-media policy (`0x800b`).
     *
     * The state is a FULL replacement, including both ordered lists — and
     * `default_blob_endpoints` order is the upload/fetch fallback priority, so
     * a caller reordering it is changing where the group uploads, not
     * reformatting it.
     *
     * Current profile only. The frozen v1 policy at `0x8008` is a different
     * component and MUST NOT be reinterpreted as v2, so there is no legacy
     * carrier to fall back to here.
     */
    suspend fun setEncryptedMediaPolicy(
        nostrGroupId: HexKey,
        policy: EncryptedMediaPolicyV2,
        relays: List<NormalizedRelayUrl> = groupRelays(nostrGroupId),
    ): OutboundGroupEvent {
        val view = groupView(nostrGroupId) ?: throw IllegalStateException("Not a member of group $nostrGroupId")
        check(view.isCurrentProfile) {
            "Group $nostrGroupId is a legacy MIP-01 group and has no carrier for an encrypted-media policy"
        }
        val encoded = policy.encode()
        return commitAndPublish(nostrGroupId, relays) {
            groupManager.stageAppDataUpdate(nostrGroupId, EncryptedMediaPolicyV2.COMPONENT_ID, encoded)
        }.event
    }

    /** The group's media policy, or null when it carries none. */
    fun encryptedMediaPolicy(nostrGroupId: HexKey): EncryptedMediaPolicyV2? = groupState(nostrGroupId)?.encryptedMedia

    /**
     * Encrypt an attachment under the group's media secret
     * (`MLS-Exporter("marmot", "encrypted-media", 32)`).
     *
     * The secret is the CURRENT epoch's. `source_epoch` is deliberately not a
     * field of the reference: it is the epoch of the application message that
     * carries the tag, so a sender must publish the message in the same epoch
     * it encrypted under — which is what publishing right after this does.
     */
    fun encryptMedia(
        nostrGroupId: HexKey,
        plaintext: ByteArray,
        mediaType: String,
        filename: String,
    ): EncryptedMediaV2.EncryptionResult =
        EncryptedMediaV2.encrypt(
            plaintext = plaintext,
            mediaSecret = groupManager.mediaExporterSecret(nostrGroupId),
            mediaType = mediaType,
            filename = filename,
        )

    /** Decrypt an attachment a peer sent, verifying it is the file the reference names. */
    fun decryptMedia(
        nostrGroupId: HexKey,
        reference: EncryptedMediaReferenceV2,
        ciphertext: ByteArray,
        /**
         * The epoch that delivered the carrying message, when the caller knows
         * it. The media secret is per-epoch, so a message from an older epoch
         * does not open under the current one.
         */
        epochSecret: ByteArray? = null,
    ): ByteArray =
        EncryptedMediaV2.decrypt(
            ciphertext = ciphertext,
            mediaSecret = epochSecret ?: groupManager.mediaExporterSecret(nostrGroupId),
            nonce = reference.nonce,
            plaintextSha256 = reference.plaintextSha256,
            mediaType = reference.mediaType,
            filename = reference.filename,
        )

    /**
     * Build the kind:9 that carries an `encrypted-media-v2` attachment.
     *
     * The reference rides in an `imeta` tag; [caption] is the message body a
     * client without media support still reads. The locator URLs are the only
     * thing in the tag a server ever sees, and they name ciphertext.
     */
    suspend fun buildMediaMessage(
        nostrGroupId: HexKey,
        reference: EncryptedMediaReferenceV2,
        caption: String = "",
        persistOwn: Boolean = true,
    ): TextMessageBundle {
        val template =
            com.vitorpamplona.quartz.nip01Core.signers
                .eventTemplate<Event>(kind = 9, description = caption) {
                    addUnique(reference.toImetaTag())
                }
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<Event>(signer.pubKey, template)
        val outbound = buildGroupMessage(nostrGroupId, innerEvent)
        if (persistOwn) persistDecryptedMessage(nostrGroupId, innerEvent.toJson())
        return TextMessageBundle(outbound = outbound, innerEvent = innerEvent)
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
    ): KeyPackageEvent = mintKeyPackageEventForSlot(keyPackageRotationManager.getOrCreateSlotDTag(slotName), relays, currentProfile)

    /**
     * Mint, sign and index a KeyPackage for an already-resolved d-tag slot.
     *
     * Both the first publication and every rotation go through here, and that
     * is the point: a replacement minted any other way can end up on a
     * different protocol profile than the KeyPackage it replaces, which makes
     * the account uninvitable to peers that require the current one.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun mintKeyPackageEventForSlot(
        dTag: String,
        relays: List<NormalizedRelayUrl>,
        currentProfile: Boolean,
    ): KeyPackageEvent {
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
    suspend fun rotateConsumedKeyPackages(
        relays: List<NormalizedRelayUrl>,
        currentProfile: Boolean = true,
    ): List<KeyPackageEvent> {
        val pendingSlots = keyPackageRotationManager.pendingRotationSlots()
        if (pendingSlots.isEmpty()) return emptyList()

        // Same mint path as the first publication. MIP-00 makes us replace a
        // KeyPackage the moment a Welcome consumes it, so this runs right
        // after the first group we are ever invited to — minting the
        // replacement any other way would silently downgrade the only
        // KeyPackage on relays for us, and a peer that requires the current
        // profile refuses it and can never add us again.
        return pendingSlots.map { slot ->
            val signed = mintKeyPackageEventForSlot(slot, relays, currentProfile)
            keyPackageRotationManager.clearPendingRotation(slot)
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
            chatroom.avatarUrl.value = view.avatarUrl
            // Drives which actions a front end may offer at all — a legacy
            // group has no carrier for lifecycle, URL avatar or media policy.
            chatroom.isCurrentProfile.value = view.isCurrentProfile
            chatroom.hasEncryptedMediaPolicy.value = encryptedMediaPolicy(nostrGroupId) != null
        }
        // Read every sync, because a gate is raised and cleared by protocol
        // events the UI never sees directly — a disband request resolving, a
        // removal being realized.
        chatroom.outboundGate.value = publishGate.outboundGateNow(nostrGroupId)
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
