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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
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

class DecryptAndIndexProcessor(
    val account: Account,
    val cache: LocalCache,
) {
    suspend fun consume(note: Note) {
        val noteEvent = note.event
        if (noteEvent != null) {
            try {
                consumeAlreadyVerified(noteEvent, note, note)
            } catch (e: Exception) {
                Log.e("PrecacheNewNotesProcessor", "Error processing note", e)
            }
        }
    }

    suspend fun consumeAlreadyVerified(
        event: Event,
        eventNote: Note,
        publicNote: Note,
    ) {
        when (event) {
            is PrivateDmEvent -> {
                if (event.canDecrypt(account.signer.pubKey)) {
                    val talkingWith = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(talkingWith, eventNote)
                }
            }

            is ChatMessageEvent -> {
                if (event.isIncluded(account.signer.pubKey)) {
                    val key = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(key, eventNote)
                }
            }

            is ChatMessageEncryptedFileHeaderEvent -> {
                if (event.isIncluded(account.signer.pubKey)) {
                    val key = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(key, eventNote)
                }
            }

            is OtsEvent -> {
                // verifies new OTS upon arrival
                Amethyst.instance.otsVerifCache.cacheVerify(event, account::otsResolver)
            }

            is DraftEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                if (event.pubKey == account.signer.pubKey) {
                    if (!event.isDeleted()) {
                        if (account.draftsDecryptionCache.preCachedDraft(event) == null) {
                            account.draftsDecryptionCache.cachedDraft(event)?.let { rumor ->
                                indexDraftAsRealEvent(eventNote, rumor)
                            }
                        }
                    }
                }
            }

            is GiftWrapEvent -> {
                if (event.recipientPubKey() == account.signer.pubKey) {
                    val innerGiftId = event.innerEventId
                    if (innerGiftId == null) {
                        event.unwrapOrNull(account.signer)?.let { innerGift ->
                            // clear the encrypted payload to save memory
                            eventNote.event = event.copyNoContent()
                            if (cache.justConsume(innerGift, null, false)) {
                                cache.copyRelaysFromTo(publicNote, innerGift)
                                val innerGiftNote = cache.getOrCreateNote(innerGift.id)
                                consumeAlreadyVerified(innerGift, innerGiftNote, publicNote)
                            }
                        }
                    } else {
                        cache.copyRelaysFromTo(publicNote, innerGiftId)

                        val innerGiftNote = cache.getOrCreateNote(innerGiftId)
                        val innerGift = innerGiftNote.event
                        if (innerGift != null) {
                            consumeAlreadyVerified(innerGift, innerGiftNote, publicNote)
                        }
                    }
                }
            }

            is SealedRumorEvent -> {
                val rumorId = event.innerEventId
                if (rumorId == null) {
                    event.unsealOrNull(account.signer)?.let { innerRumor ->
                        // clear the encrypted payload to save memory
                        eventNote.event = event.copyNoContent()

                        // rumors cannot be verified
                        cache.justConsume(innerRumor, null, true)

                        cache.copyRelaysFromTo(publicNote, innerRumor)
                        val innerRumorNote = cache.getOrCreateNote(innerRumor.id)
                        consumeAlreadyVerified(innerRumor, innerRumorNote, publicNote)
                    }
                } else {
                    cache.copyRelaysFromTo(publicNote, rumorId)

                    val innerRumorNote = cache.getOrCreateNote(rumorId)
                    val innerRumor = innerRumorNote.event
                    if (innerRumor != null) {
                        consumeAlreadyVerified(innerRumor, innerRumorNote, publicNote)
                    }
                }
            }

            is LnZapRequestEvent -> {
                if (account.privateZapsDecryptionCache.cachedPrivateZap(event) == null && event.isPrivateZap()) {
                    account.privateZapsDecryptionCache.decryptPrivateZap(event)
                }
            }
        }
    }

    suspend fun delete(note: Note) {
        val noteEvent = note.event
        if (noteEvent != null) {
            try {
                delete(noteEvent, note)
            } catch (e: Exception) {
                Log.e("PrecacheNewNotesProcessor", "Error processing note", e)
            }
        }
    }

    suspend fun delete(
        event: Event,
        eventNote: Note,
    ) {
        when (event) {
            is PrivateDmEvent -> {
                if (event.canDecrypt(account.signer.pubKey)) {
                    // Avoid decrypting over and over again if the event already exist.
                    val talkingWith = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.removeMessage(talkingWith, eventNote)
                }
            }

            is ChatMessageEvent -> {
                if (event.isIncluded(account.signer.pubKey)) {
                    val key = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.removeMessage(key, eventNote)
                }
            }

            is ChatMessageEncryptedFileHeaderEvent -> {
                if (event.isIncluded(account.signer.pubKey)) {
                    val key = event.chatroomKey(account.signer.pubKey)
                    account.chatroomList.removeMessage(key, eventNote)
                }
            }

            is DraftEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                if (event.pubKey == account.signer.pubKey) {
                    if (!event.isDeleted()) {
                        if (account.draftsDecryptionCache.preCachedDraft(event) == null) {
                            account.draftsDecryptionCache.cachedDraft(event)?.let {
                                deindexDraftAsRealEvent(eventNote, it)
                            }
                            account.draftsDecryptionCache.delete(event)
                        }
                    }
                }
            }

            is GiftWrapEvent -> {
                if (event.recipientPubKey() == account.signer.pubKey) {
                    val innerGiftId = event.innerEventId
                    if (innerGiftId != null) {
                        val innerGiftNote = cache.getNoteIfExists(innerGiftId)
                        val innerGift = innerGiftNote?.event
                        if (innerGift != null) {
                            delete(innerGift, innerGiftNote)
                        }
                    }
                }
            }

            is SealedRumorEvent -> {
                val rumorId = event.innerEventId
                if (rumorId != null) {
                    val innerRumorNote = cache.getNoteIfExists(rumorId)
                    val innerRumor = innerRumorNote?.event
                    if (innerRumor != null) {
                        delete(innerRumor, innerRumorNote)
                    }
                }
            }

            is LnZapEvent -> {
                event.zapRequest?.let { req ->
                    if (req.isPrivateZap()) {
                        // We can't know which account this was for without going through it.
                        account.privateZapsDecryptionCache.delete(req)
                    }
                }
            }

            is LnZapRequestEvent -> {
                if (event.isPrivateZap()) {
                    account.privateZapsDecryptionCache.delete(event)
                }
            }
            // ..
        }
    }

    fun indexDraftAsRealEvent(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        when (rumor) {
            is PrivateDmEvent -> {
                if (rumor.canDecrypt(account.signer)) {
                    val talkingWith = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(talkingWith, draftEventWrap)
                }
            }
            is ChatMessageEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(key, draftEventWrap)
                }
            }
            is ChatMessageEncryptedFileHeaderEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(key, draftEventWrap)
                }
            }
            is EphemeralChatEvent -> {
                rumor.roomId()?.let {
                    cache.getOrCreateEphemeralChannel(it).addNote(draftEventWrap, null)
                }
            }
            is ChannelMessageEvent -> {
                rumor.channelId()?.let { channelId ->
                    cache.checkGetOrCreatePublicChatChannel(channelId)?.addNote(draftEventWrap, null)
                }
            }
            is LiveActivitiesChatMessageEvent -> {
                rumor.activityAddress()?.let { channelId ->
                    cache.getOrCreateLiveChannel(channelId).addNote(draftEventWrap, null)
                }
            }
            is TextNoteEvent -> {
                val replyTo = cache.computeReplyTo(rumor)
                replyTo.forEach { it.addReply(draftEventWrap) }
            }
        }
    }

    fun deindexDraftAsRealEvent(
        draftEventWrap: Note,
        rumor: Event,
    ) {
        when (rumor) {
            is PrivateDmEvent -> {
                if (rumor.canDecrypt(account.signer.pubKey)) {
                    val talkingWith = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.addMessage(talkingWith, draftEventWrap)
                }
            }
            is ChatMessageEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.removeMessage(key, draftEventWrap)
                }
            }
            is ChatMessageEncryptedFileHeaderEvent -> {
                if (rumor.isIncluded(account.signer.pubKey)) {
                    val key = rumor.chatroomKey(account.signer.pubKey)
                    account.chatroomList.removeMessage(key, draftEventWrap)
                }
            }
            is ChannelMessageEvent -> {
                rumor.channelId()?.let { channelId ->
                    cache.getPublicChatChannelIfExists(channelId)?.removeNote(draftEventWrap)
                }
            }
            is EphemeralChatEvent -> {
                rumor.roomId()?.let {
                    cache.getEphemeralChatChannelIfExists(it)?.removeNote(draftEventWrap)
                }
            }
            is LiveActivitiesChatMessageEvent -> {
                rumor.activityAddress()?.let { channelId ->
                    cache.getLiveActivityChannelIfExists(channelId)?.removeNote(draftEventWrap)
                }
            }
            is TextNoteEvent -> {
                val replyTo = cache.computeReplyTo(rumor)
                replyTo.forEach { it.removeReply(draftEventWrap) }
            }
        }
    }

    suspend fun runNew(newNotes: Set<Note>) {
        try {
            newNotes.forEach {
                consume(it)
            }

            val toDelete =
                newNotes.mapNotNull {
                    val noteEvent = it.event
                    if (noteEvent is DraftEvent && noteEvent.isDeleted()) {
                        it
                    } else {
                        null
                    }
                }

            if (toDelete.isNotEmpty()) {
                Log.w("PrecacheNewNotesProcessor", "Deleting ${toDelete.size} draft notes that should have been deleted already")
                account.delete(toDelete)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PrecacheNewNotesProcessor", "This shouldn't happen", e)
        }
    }

    suspend fun runDeleted(newNotes: Set<Note>) {
        try {
            newNotes.forEach {
                delete(it)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PrecacheNewNotesProcessor", "This shouldn't happen", e)
        }
    }
}
