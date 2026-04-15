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
        val innerGift = event.unwrapOrNull(account.signer) ?: return

        eventNote.event = event.copyNoContent()

        // Check if the unwrapped event is a Marmot WelcomeEvent (kind:444)
        if (MarmotInboundProcessor.isWelcomeEvent(innerGift)) {
            processMarmotWelcome(innerGift, eventNote, publicNote)
            return
        }

        if (cache.justConsume(innerGift, null, false)) {
            cache.copyRelaysFromTo(publicNote, innerGift)
            val innerGiftNote = cache.getOrCreateNote(innerGift.id)
            eventProcessor.consumeEvent(innerGift, innerGiftNote, publicNote)
        }
    }

    private suspend fun processMarmotWelcome(
        innerEvent: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        val manager = account.marmotManager ?: return
        if (innerEvent !is WelcomeEvent) return

        val nostrGroupId = innerEvent.nostrGroupId() ?: return

        val result = manager.processWelcome(innerEvent, nostrGroupId)

        when (result) {
            is WelcomeResult.Joined -> {
                Log.d("GiftWrapEventHandler", "Joined Marmot group ${result.nostrGroupId}")

                // Sync MIP-01 metadata from group extensions to chatroom
                val chatroom = account.marmotGroupList.getOrCreateGroup(result.nostrGroupId)
                manager.syncMetadataTo(result.nostrGroupId, chatroom)

                // Notify any open MarmotGroupListScreen that a new invited
                // group has appeared so it can re-render (the screen
                // re-runs `loadGroupList` on every emission). Without this,
                // newly-joined groups only show up after a manual refresh.
                account.marmotGroupList.notifyGroupChanged(result.nostrGroupId)

                // Rotate KeyPackages if needed
                if (result.needsKeyPackageRotation) {
                    account.publishMarmotKeyPackages()
                }
            }

            is WelcomeResult.Error -> {
                Log.w("GiftWrapEventHandler") { "Failed to process Marmot Welcome: ${result.message}" }
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
        val innerRumor = event.unsealOrNull(account.signer) ?: return

        eventNote.event = event.copyNoContent()
        cache.justConsume(innerRumor, null, true)
        cache.copyRelaysFromTo(publicNote, innerRumor)

        val innerRumorNote = cache.getOrCreateNote(innerRumor.id)
        eventProcessor.consumeEvent(innerRumor, innerRumorNote, publicNote)
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
    override suspend fun add(
        event: GroupEvent,
        eventNote: Note,
        publicNote: Note,
    ) {
        val manager = account.marmotManager ?: return

        val groupId = event.groupId() ?: return
        if (!manager.isMember(groupId)) return

        try {
            val result = manager.processGroupEvent(event)

            when (result) {
                is GroupEventResult.ApplicationMessage -> {
                    // Parse the inner event JSON and index it
                    val innerEvent = Event.fromJson(result.innerEventJson)
                    if (cache.justConsume(innerEvent, null, false)) {
                        val innerNote = cache.getOrCreateNote(innerEvent.id)
                        innerNote.event = innerEvent

                        // Track the message in the Marmot group chatroom
                        account.marmotGroupList.addMessage(result.groupId, innerNote)

                        // Persist the decrypted plaintext so the message
                        // survives an app restart. Marmot/MLS application
                        // messages cannot be re-decrypted once the ratchet
                        // has advanced, so we must capture them here.
                        manager.persistDecryptedMessage(result.groupId, result.innerEventJson)
                    }
                }

                is GroupEventResult.CommitProcessed -> {
                    Log.d("GroupEventHandler", "Commit processed for group ${result.groupId}, epoch=${result.newEpoch}")
                    // Sync MIP-01 metadata after epoch advance (extensions may have changed)
                    val chatroom = account.marmotGroupList.getOrCreateGroup(result.groupId)
                    manager.syncMetadataTo(result.groupId, chatroom)
                }

                is GroupEventResult.CommitPending -> {
                    Log.d("GroupEventHandler", "Commit pending for group ${result.groupId}, epoch=${result.epoch}")
                }

                is GroupEventResult.Duplicate -> {
                    Log.d("GroupEventHandler") { "Duplicate GroupEvent for group ${result.groupId}" }
                }

                is GroupEventResult.Error -> {
                    Log.w("GroupEventHandler") { "Error processing GroupEvent: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("GroupEventHandler", "Failed to process GroupEvent", e)
        }
    }
}
