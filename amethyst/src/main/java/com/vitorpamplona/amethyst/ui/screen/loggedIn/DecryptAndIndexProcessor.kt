/**
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

import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass

class EventProcessor(
    private val account: Account,
    private val cache: LocalCache,
) {
    private val decryptionService = DecryptionService(account, cache)
    private val indexingService = IndexingService(account, cache)
    private val chatroomService = ChatroomService(account)
    private val eventHandlers = createEventHandlers()

    suspend fun consume(note: Note) {
        note.event?.let { event ->
            try {
                processEvent(event, note, note)
            } catch (e: Exception) {
                Log.e("EventProcessor", "Error processing note", e)
            }
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

    internal suspend fun processEvent(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        eventHandlers[event::class]?.process(event, eventNote, publicNote)
    }

    internal suspend fun deleteEvent(
        event: Event,
        eventNote: Note,
    ) {
        eventHandlers[event::class]?.delete(event, eventNote)
    }

    private fun createEventHandlers(): Map<KClass<out Event>, EventHandler> =
        mapOf(
            PrivateDmEvent::class to PrivateDmHandler(chatroomService),
            ChatMessageEvent::class to ChatMessageHandler(chatroomService),
            ChatMessageEncryptedFileHeaderEvent::class to ChatMessageEncryptedFileHeaderHandler(chatroomService),
            OtsEvent::class to OtsEventHandler(account),
            DraftEvent::class to DraftEventHandler(decryptionService, indexingService),
            GiftWrapEvent::class to GiftWrapEventHandler(decryptionService, cache, this),
            SealedRumorEvent::class to SealedRumorEventHandler(decryptionService, cache, this),
            LnZapRequestEvent::class to LnZapRequestEventHandler(decryptionService),
            LnZapEvent::class to LnZapEventHandler(decryptionService),
        )

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
                if (event is DraftEvent && event.isDeleted()) note else null
            }

        if (deletedDrafts.isNotEmpty()) {
            Log.w("EventProcessor", "Deleting ${deletedDrafts.size} draft notes")
            account.delete(deletedDrafts)
        }
    }
}

// Service classes following Single Responsibility Principle
class DecryptionService(
    val account: Account,
    private val cache: LocalCache,
) {
    suspend fun unwrapGiftWrap(event: GiftWrapEvent): Event? = event.unwrapOrNull(account.signer)

    suspend fun unsealRumor(event: SealedRumorEvent): Event? = event.unsealOrNull(account.signer)

    suspend fun decryptDraft(event: DraftEvent): Event? =
        account.draftsDecryptionCache.preCachedDraft(event)
            ?: account.draftsDecryptionCache.cachedDraft(event)

    suspend fun handlePrivateZap(event: LnZapRequestEvent) {
        if (account.privateZapsDecryptionCache.cachedPrivateZap(event) == null && event.isPrivateZap()) {
            account.privateZapsDecryptionCache.decryptPrivateZap(event)
        }
    }

    fun deletePrivateZap(event: LnZapRequestEvent) {
        if (event.isPrivateZap()) {
            account.privateZapsDecryptionCache.delete(event)
        }
    }

    fun deletePrivateZapFromZapEvent(event: LnZapEvent) {
        event.zapRequest?.let { req ->
            if (req.isPrivateZap()) {
                account.privateZapsDecryptionCache.delete(req)
            }
        }
    }
}

class IndexingService(
    private val account: Account,
    private val cache: LocalCache,
) {
    fun indexDraftAsRealEvent(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        setupReplyRelationships(draftEventWrap, rumor)
        indexByEventType(draftEventWrap, rumor)
    }

    private fun setupReplyRelationships(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        draftEventWrap.replyTo = cache.computeReplyTo(rumor)
        draftEventWrap.replyTo?.forEach { it.addReply(draftEventWrap) }
    }

    private fun indexByEventType(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        val chatroomService = ChatroomService(account)

        when (rumor) {
            is PrivateDmEvent -> {
                if (rumor.canDecrypt(account.signer)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    chatroomService.addMessage(key, draftEventWrap)
                }
            }
            is ChatMessageEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    chatroomService.addMessage(key, draftEventWrap)
                }
            }
            is ChatMessageEncryptedFileHeaderEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    chatroomService.addMessage(key, draftEventWrap)
                }
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

class ChatroomService(
    val account: Account,
) {
    fun addMessage(
        chatroomKey: ChatroomKey,
        note: Note,
    ) {
        account.chatroomList.addMessage(chatroomKey, note)
    }

    fun removeMessage(
        chatroomKey: ChatroomKey,
        note: Note,
    ) {
        account.chatroomList.removeMessage(chatroomKey, note)
    }
}

// Event handler interface and implementations following Strategy Pattern
interface EventHandler {
    suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {}

    suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {}
}

class PrivateDmHandler(
    private val chatroomService: ChatroomService,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as PrivateDmEvent
        if (event.canDecrypt(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.addMessage(key, eventNote)
        }
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as PrivateDmEvent
        if (event.canDecrypt(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.removeMessage(key, eventNote)
        }
    }
}

class ChatMessageHandler(
    private val chatroomService: ChatroomService,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as ChatMessageEvent
        if (event.isIncluded(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.addMessage(key, eventNote)
        }
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as ChatMessageEvent
        if (event.isIncluded(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.removeMessage(key, eventNote)
        }
    }
}

class ChatMessageEncryptedFileHeaderHandler(
    private val chatroomService: ChatroomService,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as ChatMessageEncryptedFileHeaderEvent
        if (event.isIncluded(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.addMessage(key, eventNote)
        }
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as ChatMessageEncryptedFileHeaderEvent
        if (event.isIncluded(chatroomService.account.signer.pubKey)) {
            val key = event.chatroomKey(chatroomService.account.signer.pubKey)
            chatroomService.removeMessage(key, eventNote)
        }
    }
}

class OtsEventHandler(
    private val account: Account,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as OtsEvent
        Amethyst.instance.otsVerifCache.cacheVerify(event, account.otsResolverBuilder)
    }
}

class DraftEventHandler(
    private val decryptionService: DecryptionService,
    private val indexingService: IndexingService,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as DraftEvent
        if (event.pubKey == decryptionService.account.signer.pubKey && !event.isDeleted()) {
            val rumor = decryptionService.decryptDraft(event)
            rumor?.let { indexingService.indexDraftAsRealEvent(eventNote, it) }
        }
    }
}

class GiftWrapEventHandler(
    private val decryptionService: DecryptionService,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as GiftWrapEvent
        if (event.recipientPubKey() != decryptionService.account.signer.pubKey) return

        val innerGiftId = event.innerEventId
        if (innerGiftId == null) {
            processNewGiftWrap(event, eventNote, publicNote)
        } else {
            processExistingGiftWrap(innerGiftId, publicNote)
        }
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as GiftWrapEvent
        if (event.recipientPubKey() != decryptionService.account.signer.pubKey) return

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
        val innerGift = decryptionService.unwrapGiftWrap(event) ?: return

        eventNote.event = event.copyNoContent()
        if (cache.justConsume(innerGift, null, false)) {
            cache.copyRelaysFromTo(publicNote, innerGift)
            val innerGiftNote = cache.getOrCreateNote(innerGift.id)
            eventProcessor.processEvent(innerGift, innerGiftNote, publicNote)
        }
    }

    private suspend fun processExistingGiftWrap(
        innerGiftId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, innerGiftId)
        val innerGiftNote = cache.getOrCreateNote(innerGiftId)
        innerGiftNote.event?.let { innerGift ->
            eventProcessor.processEvent(innerGift, innerGiftNote, publicNote)
        }
    }
}

class SealedRumorEventHandler(
    private val decryptionService: DecryptionService,
    private val cache: LocalCache,
    private val eventProcessor: EventProcessor,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as SealedRumorEvent

        val rumorId = event.innerEventId
        if (rumorId == null) {
            processNewSealedRumor(event, eventNote, publicNote)
        } else {
            processExistingSealedRumor(rumorId, publicNote)
        }
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as SealedRumorEvent

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
        val innerRumor = decryptionService.unsealRumor(event) ?: return

        eventNote.event = event.copyNoContent()
        cache.justConsume(innerRumor, null, true)
        cache.copyRelaysFromTo(publicNote, innerRumor)

        val innerRumorNote = cache.getOrCreateNote(innerRumor.id)
        eventProcessor.processEvent(innerRumor, innerRumorNote, publicNote)
    }

    private suspend fun processExistingSealedRumor(
        rumorId: String,
        publicNote: Note,
    ) {
        cache.copyRelaysFromTo(publicNote, rumorId)
        val innerRumorNote = cache.getOrCreateNote(rumorId)
        innerRumorNote.event?.let { innerRumor ->
            eventProcessor.processEvent(innerRumor, innerRumorNote, publicNote)
        }
    }
}

class LnZapRequestEventHandler(
    private val decryptionService: DecryptionService,
) : EventHandler {
    override suspend fun process(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        event as LnZapRequestEvent
        decryptionService.handlePrivateZap(event)
    }

    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as LnZapRequestEvent
        decryptionService.deletePrivateZap(event)
    }
}

class LnZapEventHandler(
    private val decryptionService: DecryptionService,
) : EventHandler {
    override suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        event as LnZapEvent
        decryptionService.deletePrivateZapFromZapEvent(event)
    }
}
