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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.MarmotInboundProcessor
import com.vitorpamplona.quartz.marmot.WelcomeResult
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.IEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapCache
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EventProcessor(
    private val account: Account,
    private val cache: LocalCache,
) {
    private val chatHandler = ChatHandler(account.chatroomList)
    private val draftHandler = DraftEventHandler(account, cache)

    private val giftWrapHandler = GiftWrapEventHandler(account, cache, this)
    private val sealHandler = SealedRumorEventHandler(account, cache, this)

    private val zapRequest = LnZapRequestEventHandler(account.privateZapsDecryptionCache)
    private val zapEvent = LnZapEventHandler(account.privateZapsDecryptionCache)

    private val groupEventHandler = GroupEventHandler(account, cache)

    var callManager: CallManager? = null

    suspend fun consume(note: Note) {
        note.event?.let { event ->
            try {
                consumeEvent(event, note, note)
            } catch (e: Exception) {
                Log.e("EventProcessor", "Error processing note", e)
            }
        }
    }

    internal suspend fun consumeEvent(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        when (event) {
            is CallOfferEvent,
            is CallAnswerEvent,
            is CallIceCandidateEvent,
            is CallHangupEvent,
            is CallRejectEvent,
            is CallRenegotiateEvent,
            -> callManager?.onSignalingEvent(event)

            is ChatroomKeyable -> chatHandler.add(event, eventNote, publicNote)

            is DraftWrapEvent -> draftHandler.add(event, eventNote, publicNote)

            is GroupEvent -> groupEventHandler.add(event, eventNote, publicNote)

            is GiftWrapEvent -> giftWrapHandler.add(event, eventNote, publicNote)

            is SealedRumorEvent -> sealHandler.add(event, eventNote, publicNote)

            is LnZapRequestEvent -> zapRequest.add(event, eventNote, publicNote)
        }
    }

    suspend fun delete(note: Note) {
        note.event?.let { event ->
            try {
                deleteEvent(event, note)
            } catch (e: Exception) {
                Log.e("EventProcessor", "Error deleting note", e)
            }
        }
    }

    internal suspend fun deleteEvent(
        event: Event,
        note: Note,
    ) {
        when (event) {
            is ChatroomKeyable -> chatHandler.delete(event, note)
            is DraftWrapEvent -> draftHandler.delete(event, note)
            is GiftWrapEvent -> giftWrapHandler.delete(event, note)
            is SealedRumorEvent -> sealHandler.delete(event, note)
            is LnZapRequestEvent -> zapRequest.delete(event, note)
            is LnZapEvent -> zapEvent.delete(event, note)
        }
    }

    suspend fun runNew(newNotes: Set<Note>) {
        try {
            newNotes.forEach { consume(it) }
            handleDeletedDrafts(newNotes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("EventProcessor", "Error processing batch", e)
        }
    }

    suspend fun runDeleted(notes: Set<Note>) {
        try {
            notes.forEach { delete(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("EventProcessor", "Error deleting batch", e)
        }
    }

    private suspend fun handleDeletedDrafts(newNotes: Set<Note>) {
        val deletedDrafts =
            newNotes.mapNotNull { note ->
                val event = note.event
                if (event is DraftWrapEvent &&
                    event.isDeleted() &&
                    !cache.deletionIndex.hasBeenDeleted(event)
                ) {
                    note
                } else {
                    null
                }
            }

        if (deletedDrafts.isNotEmpty()) {
            Log.w("EventProcessor") { "Deleting ${deletedDrafts.size} draft notes" }
            account.delete(deletedDrafts)
        }
    }
}

interface EventHandler<T : IEvent> {
    // Default no-ops; implementations override only the operations they handle
    suspend fun add(
        event: T,
        eventNote: Note,
        publicNote: Note,
    ) {}

    suspend fun delete(
        event: T,
        eventNote: Note,
    ) {}
}

class ChatHandler(
    private val chatroomList: ChatroomList,
) : EventHandler<ChatroomKeyable> {
    override suspend fun add(
        event: ChatroomKeyable,
        eventNote: Note,
        publicNote: Note,
    ) {
        chatroomList.add(event, eventNote)
    }

    override suspend fun delete(
        event: ChatroomKeyable,
        eventNote: Note,
    ) {
        chatroomList.delete(event, eventNote)
    }
}

class DraftEventHandler(
    private val account: Account,
    private val cache: LocalCache,
) : EventHandler<DraftWrapEvent> {
    override suspend fun add(
        event: DraftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        if (event.pubKey == account.signer.pubKey && !event.isDeleted() && !LocalCache.deletionIndex.hasBeenDeleted(event)) {
            val rumor = account.draftsDecryptionCache.preCachedDraft(event) ?: account.draftsDecryptionCache.cachedDraft(event)
            rumor?.let { indexDraftAsRealEvent(eventNote, it) }
        }
    }

    fun indexDraftAsRealEvent(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        draftEventWrap.replyTo = cache.computeReplyTo(rumor)
        draftEventWrap.replyTo?.forEach { it.addReply(draftEventWrap) }

        when (rumor) {
            is ChatroomKeyable -> {
                account.chatroomList.add(rumor, draftEventWrap)
            }

            is EphemeralChatEvent -> {
                rumor.roomId()?.let { roomId ->
                    val channel = cache.getOrCreateEphemeralChannel(roomId)
                    channel.addNote(draftEventWrap, null)
                }
            }

            is ChannelMessageEvent -> {
                rumor.channelId()?.let { channelId ->
                    val channel = cache.checkGetOrCreatePublicChatChannel(channelId)
                    channel?.addNote(draftEventWrap, null)
                }
            }

            is LiveActivitiesChatMessageEvent -> {
                rumor.activityAddress()?.let { channelId ->
                    val channel = cache.getOrCreateLiveChannel(channelId)
                    channel.addNote(draftEventWrap, null)
                }
            }
        }
    }
}

class GiftWrapEventHandler(
    private val account: Account,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler<GiftWrapEvent> {
    override suspend fun add(
        event: GiftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        if (event.recipientPubKey() != account.signer.pubKey) return

        val innerGiftId = event.innerEventId
        if (innerGiftId == null) {
            processNewGiftWrap(event, eventNote, publicNote)
        } else {
            processExistingGiftWrap(innerGiftId, publicNote)
        }
    }

    override suspend fun delete(
        event: GiftWrapEvent,
        eventNote: Note,
    ) {
        if (event.recipientPubKey() != account.signer.pubKey) return

        event.innerEventId?.let { innerGiftId ->
            val innerGiftNote = cache.getNoteIfExists(innerGiftId)
            innerGiftNote?.event?.let { innerGift ->
                eventProcessor.deleteEvent(innerGift, innerGiftNote)
            }
        }
    }

    private suspend fun processNewGiftWrap(
        event: GiftWrapEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        Log.d("MarmotDbg") {
            "GiftWrapEventHandler.processNewGiftWrap: id=${event.id.take(8)}… recipient=${event.recipientPubKey()?.take(8)}…"
        }
        val innerGift = event.unwrapOrNull(account.signer)
        if (innerGift == null) {
            Log.w("MarmotDbg") {
                "GiftWrapEventHandler.processNewGiftWrap: unwrap returned null (decrypt failed) for id=${event.id.take(8)}…"
            }
            return
        }
        Log.d("MarmotDbg") {
            "GiftWrapEventHandler.processNewGiftWrap: unwrapped innerKind=${innerGift.kind} innerId=${innerGift.id.take(8)}…"
        }

        eventNote.event = event.copyNoContent()

        if (cache.justConsume(innerGift, null, false)) {
            cache.copyRelaysFromTo(publicNote, innerGift)
            val innerGiftNote = cache.getOrCreateNote(innerGift.id)

            // Marmot Welcomes need MLS processing in addition to being cached,
            // but they do not route through the normal eventProcessor consumer.
            if (MarmotInboundProcessor.isWelcomeEvent(innerGift)) {
                Log.d("MarmotDbg") {
                    "GiftWrapEventHandler: detected Marmot WelcomeEvent — routing to processMarmotWelcomeFlow"
                }
                processMarmotWelcomeFlow(innerGift, account)
            } else {
                eventProcessor.consumeEvent(innerGift, innerGiftNote, publicNote)
            }
        }
    }

    private suspend fun processExistingGiftWrap(
        innerGiftId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, innerGiftId)
        val innerGiftNote = cache.getOrCreateNote(innerGiftId)
        innerGiftNote.event?.let { innerGift ->
            eventProcessor.consumeEvent(innerGift, innerGiftNote, publicNote)
        }
    }
}

/**
 * Shared Marmot Welcome handler used by both [GiftWrapEventHandler]
 * (in case a Welcome arrives directly inside a kind:1059 with no Seal
 * layer) and [SealedRumorEventHandler] (the actual production path —
 * Welcomes are wrapped GiftWrap → Seal → Welcome per
 * [com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeGiftWrap]).
 */
private suspend fun processMarmotWelcomeFlow(
    innerEvent: Event,
    account: Account,
) {
    Log.d("MarmotDbg") {
        "processMarmotWelcomeFlow: innerKind=${innerEvent.kind} innerId=${innerEvent.id.take(8)}…"
    }
    val manager = account.marmotManager
    if (manager == null) {
        Log.w("MarmotDbg") { "processMarmotWelcomeFlow: marmotManager is null — Marmot store probably failed to init" }
        return
    }
    if (innerEvent !is WelcomeEvent) {
        Log.w("MarmotDbg") { "processMarmotWelcomeFlow: inner is not WelcomeEvent (kind=${innerEvent.kind})" }
        return
    }

    // "h" tag is optional per MIP-02 — some senders (e.g. whitenoise-rs) omit it.
    // nostrGroupId is derived from the MLS GroupContext's NostrGroupData extension instead.
    val hintNostrGroupId = innerEvent.nostrGroupId()
    Log.d("MarmotDbg") {
        "processMarmotWelcomeFlow: h-tag=${hintNostrGroupId?.take(8) ?: "(absent)"} — deriving from MLS content"
    }

    val result = manager.processWelcome(innerEvent, hintNostrGroupId)

    when (result) {
        is WelcomeResult.Joined -> {
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: Joined ${result.nostrGroupId.take(8)}… needsKeyPackageRotation=${result.needsKeyPackageRotation}"
            }

            // Sync MIP-01 metadata from group extensions to chatroom
            val chatroom = account.marmotGroupList.getOrCreateGroup(result.nostrGroupId)
            manager.syncMetadataTo(result.nostrGroupId, chatroom)
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: synced metadata name=${chatroom.displayName.value} " +
                    "members=${chatroom.memberCount.value} relays=${chatroom.relays.value}"
            }

            // Notify any open MarmotGroupListScreen that a new invited
            // group has appeared so it can re-render (the screen
            // re-runs `loadGroupList` on every emission). Without this,
            // newly-joined groups only show up after a manual refresh.
            account.marmotGroupList.notifyGroupChanged(result.nostrGroupId)

            // Rotate KeyPackages if needed
            if (result.needsKeyPackageRotation) {
                account.publishMarmotKeyPackages()
            }

            // Fire the "You've been added to <group>" notification. Welcomes
            // have no `p` tag, so the cache-observer path can't route them;
            // this is the single point where we know the recipient account.
            Amethyst.instance.notificationDispatcher.notifyWelcome(innerEvent, account)
        }

        is WelcomeResult.AlreadyJoined -> {
            // Benign replay of a gift-wrapped Welcome (kind:1059) we already
            // processed in a prior session — the relay is just re-delivering
            // it after app restart. Log at DEBUG, not WARN.
            Log.d("MarmotDbg") {
                "processMarmotWelcomeFlow: already joined group=${result.nostrGroupId.take(8)}… — treating Welcome as replay"
            }
        }

        is WelcomeResult.Error -> {
            Log.w("MarmotDbg") { "processMarmotWelcomeFlow: ERROR ${result.message}" }
        }
    }
}

class SealedRumorEventHandler(
    private val account: Account,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler<SealedRumorEvent> {
    override suspend fun add(
        event: SealedRumorEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        val rumorId = event.innerEventId
        if (rumorId == null) {
            processNewSealedRumor(event, eventNote, publicNote)
        } else {
            processExistingSealedRumor(rumorId, publicNote)
        }
    }

    override suspend fun delete(
        event: SealedRumorEvent,
        eventNote: Note,
    ) {
        event.innerEventId?.let { rumorId ->
            val innerRumorNote = cache.getNoteIfExists(rumorId)
            innerRumorNote?.event?.let { innerRumor ->
                eventProcessor.deleteEvent(innerRumor, innerRumorNote)
            }
        }
    }

    private suspend fun processNewSealedRumor(
        event: SealedRumorEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        Log.d("MarmotDbg") {
            "SealedRumorEventHandler.processNewSealedRumor: id=${event.id.take(8)}…"
        }
        val innerRumor = event.unsealOrNull(account.signer)
        if (innerRumor == null) {
            Log.w("MarmotDbg") {
                "SealedRumorEventHandler.processNewSealedRumor: unseal returned null for ${event.id.take(8)}…"
            }
            return
        }
        Log.d("MarmotDbg") {
            "SealedRumorEventHandler.processNewSealedRumor: unsealed innerKind=${innerRumor.kind} innerId=${innerRumor.id.take(8)}…"
        }

        eventNote.event = event.copyNoContent()

        cache.justConsume(innerRumor, null, true)
        cache.copyRelaysFromTo(publicNote, innerRumor)

        val innerRumorNote = cache.getOrCreateNote(innerRumor.id)

        // Marmot Welcome: GiftWrap → Seal → WelcomeEvent. The Seal handler
        // is the actual point at which we see the kind:444 inner. Route it
        // to the MLS flow for group joining in addition to caching — there's
        // no normal eventProcessor consumer for kind:444.
        if (MarmotInboundProcessor.isWelcomeEvent(innerRumor)) {
            Log.d("MarmotDbg") {
                "SealedRumorEventHandler: detected Marmot WelcomeEvent inside seal — routing to processMarmotWelcomeFlow"
            }
            processMarmotWelcomeFlow(innerRumor, account)
        } else {
            eventProcessor.consumeEvent(innerRumor, innerRumorNote, publicNote)
        }
    }

    private suspend fun processExistingSealedRumor(
        rumorId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, rumorId)
        val innerRumorNote = cache.getOrCreateNote(rumorId)
        innerRumorNote.event?.let { innerRumor ->
            eventProcessor.consumeEvent(innerRumor, innerRumorNote, publicNote)
        }
    }
}

class LnZapRequestEventHandler(
    val decryptionCache: PrivateZapCache,
) : EventHandler<LnZapRequestEvent> {
    override suspend fun add(
        event: LnZapRequestEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        if (decryptionCache.cachedPrivateZap(event) == null && event.isPrivateZap()) {
            decryptionCache.decryptPrivateZap(event)
        }
    }

    override suspend fun delete(
        event: LnZapRequestEvent,
        eventNote: Note,
    ) {
        if (event.isPrivateZap()) {
            decryptionCache.delete(event)
        }
    }
}

class LnZapEventHandler(
    val decryptionCache: PrivateZapCache,
) : EventHandler<LnZapEvent> {
    override suspend fun delete(
        event: LnZapEvent,
        eventNote: Note,
    ) {
        event.zapRequest?.let { req ->
            if (req.isPrivateZap()) {
                decryptionCache.delete(req)
            }
        }
    }
}

/**
 * Handles inbound GroupEvent (kind:445) for Marmot MLS group messaging.
 *
 * Decrypts the outer ChaCha20-Poly1305 layer and the inner MLS layer,
 * then indexes the resulting inner event in LocalCache.
 */
class GroupEventHandler(
    private val account: Account,
    private val cache: LocalCache,
) : EventHandler<GroupEvent> {
    /**
     * Per-group buffer of kind:445 events whose outer ChaCha20-Poly1305
     * layer couldn't be decrypted yet — typically because they carry an
     * epoch the receiver hasn't advanced to. The relay subscription
     * doesn't guarantee arrival order, so a run like
     *
     *   1) B sends msg-1..5 at epoch 1
     *   2) B adds C (commit advances B → epoch 2)
     *   3) B sends msg-6..8 at epoch 2
     *   4) B renames (commit advances B → epoch 3)
     *
     * can reach a receiver in the order {msg-6, msg-7, msg-8, rename,
     * add-C, msg-1..5} depending on relay ordering. Everything from step
     * 3+ above is `UndecryptableOuterLayer` until the add-C commit lands;
     * we re-run those pending events every time an epoch-advancing
     * CommitProcessed fires for the same group.
     *
     * Bounded per-group so a stuck stream of "truly undecryptable"
     * pre-join events can't grow unbounded. FIFO eviction on overflow.
     */
    private val pendingUndecryptable: MutableMap<String, ArrayDeque<GroupEvent>> = mutableMapOf()
    private val pendingMutex = Mutex()

    override suspend fun add(
        event: GroupEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        Log.d("MarmotDbg") {
            "GroupEventHandler.add: kind:445 id=${event.id.take(8)}… groupId=${event.groupId()?.take(8)}…"
        }
        val manager = account.marmotManager
        if (manager == null) {
            Log.w("MarmotDbg") { "GroupEventHandler.add: marmotManager is null" }
            return
        }

        val groupId = event.groupId()
        if (groupId == null) {
            Log.w("MarmotDbg") { "GroupEventHandler.add: kind:445 missing 'h' tag" }
            return
        }
        if (!manager.isMember(groupId)) {
            Log.w("MarmotDbg") {
                "GroupEventHandler.add: not a member of group=${groupId.take(8)}… — dropping kind:445 ${event.id.take(8)}…"
            }
            return
        }

        val chatroom = account.marmotGroupList.getOrCreateGroup(groupId)
        eventNote.relays.forEach { chatroom.recordRelayActivity(it, event.createdAt) }

        try {
            val result = manager.processGroupEvent(event)
            Log.d("MarmotDbg") {
                "GroupEventHandler.add: processGroupEvent returned ${result::class.simpleName} for group=${groupId.take(8)}…"
            }

            when (result) {
                is GroupEventResult.ApplicationMessage -> {
                    // Parse the inner event JSON and index it
                    val innerEvent = Event.fromJson(result.innerEventJson)
                    Log.d("MarmotDbg") {
                        "GroupEventHandler.add: ApplicationMessage decrypted innerKind=${innerEvent.kind} " +
                            "innerId=${innerEvent.id.take(8)}… author=${innerEvent.pubKey.take(8)}…"
                    }
                    // wasVerified=true: MIP-03 inner events are unsigned
                    // rumors (empty sig); LocalCache.justVerify would reject
                    // every one on Schnorr verification. Authenticity is
                    // already guaranteed by MLS — MarmotInboundProcessor
                    // enforces innerEvent.pubKey == MLS sender's credential
                    // identity before returning ApplicationMessage. Without
                    // this, wn-signed kind:9 chat messages, kind:7 reactions,
                    // and kind:5 deletions would all silently drop here.
                    //
                    // Always call marmotGroupList.addMessage regardless of
                    // justConsume's return value. `addMessageSync` dedupes
                    // by Note identity, so a duplicate is a cheap no-op —
                    // but a previous-session persist that hydrated the
                    // cache at startup without populating the chatroom
                    // needs this path to surface the note (otherwise the
                    // operator saw nothing but a misleading "inner event
                    // already in cache" log and the message never rendered).
                    val isNew = cache.justConsume(innerEvent, null, true)
                    val innerNote = cache.getOrCreateNote(innerEvent.id)
                    if (isNew) {
                        innerNote.event = innerEvent
                    } else {
                        Log.d("MarmotDbg") {
                            "GroupEventHandler.add: inner event already in cache — surfacing in chatroom anyway"
                        }
                    }

                    // Track the message in the Marmot group chatroom
                    account.marmotGroupList.addMessage(result.groupId, innerNote)

                    // Persist the decrypted plaintext so the message
                    // survives an app restart. Marmot/MLS application
                    // messages cannot be re-decrypted once the ratchet
                    // has advanced, so we must capture them here. Skip
                    // when the cache reported duplicate — the message
                    // was already persisted on the run that originally
                    // decrypted it (the message store appends, so a
                    // re-persist would silently grow the on-disk log).
                    if (isNew) {
                        manager.persistDecryptedMessage(result.groupId, result.innerEventJson)
                    }
                }

                is GroupEventResult.CommitProcessed -> {
                    Log.d("MarmotDbg") {
                        "GroupEventHandler.add: CommitProcessed group=${result.groupId.take(8)}… newEpoch=${result.newEpoch}"
                    }
                    // Sync MIP-01 metadata after epoch advance (extensions may have changed)
                    val chatroom = account.marmotGroupList.getOrCreateGroup(result.groupId)
                    manager.syncMetadataTo(result.groupId, chatroom)
                    // Epoch just advanced — drain any kind:445 events that
                    // previously failed as UndecryptableOuterLayer for this
                    // group. See `pendingUndecryptable` for the scenario.
                    retryPendingFor(result.groupId, eventNote, publicNote)
                }

                is GroupEventResult.CommitPending -> {
                    Log.d("MarmotDbg") {
                        "GroupEventHandler.add: CommitPending group=${result.groupId.take(8)}… epoch=${result.epoch}"
                    }
                }

                is GroupEventResult.Duplicate -> {
                    Log.d("MarmotDbg") { "GroupEventHandler.add: Duplicate kind:445 for group=${result.groupId.take(8)}…" }
                }

                is GroupEventResult.UndecryptableOuterLayer -> {
                    // Two common causes for this result:
                    //   (a) event's epoch predates our join — we never held
                    //       those exporter keys, nothing to retry later.
                    //   (b) event's epoch is ahead of our current one — the
                    //       epoch-advancing commit for this group hasn't
                    //       arrived yet but will in a moment (relays don't
                    //       guarantee arrival order across a kind:445
                    //       subscription). We can't tell (a) from (b) here,
                    //       so buffer the event and retry after any future
                    //       CommitProcessed for this group advances us.
                    //
                    // Bounded per-group: keep the most recent
                    // MAX_PENDING_PER_GROUP events, FIFO-evict on overflow.
                    // Prevents a flood of genuinely pre-join events from
                    // pinning unbounded memory.
                    Log.d("MarmotDbg") {
                        "GroupEventHandler.add: undecryptable outer layer for group=${result.groupId.take(8)}… " +
                            "(current + ${result.retainedEpochCount} retained epoch key(s) tried) — buffering for post-commit retry"
                    }
                    pendingMutex.withLock {
                        val queue = pendingUndecryptable.getOrPut(result.groupId) { ArrayDeque() }
                        if (queue.size >= MAX_PENDING_PER_GROUP) {
                            queue.removeFirst()
                        }
                        queue.addLast(event)
                    }
                }

                is GroupEventResult.Error -> {
                    Log.w("MarmotDbg") { "GroupEventHandler.add: ERROR ${result.message}" }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MarmotDbg", "GroupEventHandler.add: exception processing kind:445", e)
        }
    }

    /**
     * Drain the pending-undecryptable queue for [groupId] and re-run each
     * event through `add`. A successful retry may itself emit another
     * CommitProcessed and trigger recursive draining — that's fine, MLS
     * epoch advances are strictly monotonic so recursion depth is bounded
     * by the number of queued events.
     *
     * `eventNote` / `publicNote` are re-used for the retry path; they only
     * feed into relay-activity tracking and compose-note lookup, both of
     * which are safe to re-fire with the triggering event's notes.
     */
    private suspend fun retryPendingFor(
        groupId: String,
        eventNote: Note,
        publicNote: Note,
    ) {
        val drained =
            pendingMutex.withLock {
                pendingUndecryptable.remove(groupId)?.toList() ?: return
            }
        if (drained.isEmpty()) return
        Log.d("MarmotDbg") {
            "GroupEventHandler.retryPendingFor: replaying ${drained.size} previously-undecryptable kind:445 event(s) for group=${groupId.take(8)}…"
        }
        for (pending in drained) {
            add(pending, eventNote, publicNote)
        }
    }

    companion object {
        private const val MAX_PENDING_PER_GROUP = 64
    }
}
