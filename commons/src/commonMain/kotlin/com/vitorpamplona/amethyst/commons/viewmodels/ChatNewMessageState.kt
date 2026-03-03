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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrEventUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Slim shared state for DM message composition.
 * Holds only core fields needed for typing and sending messages.
 *
 * Platform-specific concerns (uploads, emoji suggestions, drafts, zapraiser,
 * location, user suggestions) remain in the platform ViewModel layer.
 *
 * Used by both Android ChatNewMessageViewModel and Desktop DM screen.
 */
@Stable
class ChatNewMessageState(
    val account: IAccount,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    private val _message = MutableStateFlow(TextFieldValue(""))
    val message: StateFlow<TextFieldValue> = _message.asStateFlow()

    private val _nip17 = MutableStateFlow(false)
    val nip17: StateFlow<Boolean> = _nip17.asStateFlow()

    private val _replyTo = MutableStateFlow<Note?>(null)
    val replyTo: StateFlow<Note?> = _replyTo.asStateFlow()

    private val _subject = MutableStateFlow(TextFieldValue(""))
    val subject: StateFlow<TextFieldValue> = _subject.asStateFlow()

    private val _room = MutableStateFlow<ChatroomKey?>(null)
    val room: StateFlow<ChatroomKey?> = _room.asStateFlow()

    /** Whether NIP-17 is required (group chat with >1 recipient) */
    private val _requiresNip17 = MutableStateFlow(false)
    val requiresNip17: StateFlow<Boolean> = _requiresNip17.asStateFlow()

    /** Whether a message can be sent (non-blank text + room set) */
    val canSend: Boolean
        get() = _message.value.text.isNotBlank() && _room.value != null

    /**
     * Load a chatroom. Sets the room key, formats toUsers display,
     * and auto-detects NIP-17 requirement (group chats require NIP-17).
     */
    fun load(roomKey: ChatroomKey) {
        _room.value = roomKey
        updateNip17FromRoom()
    }

    /**
     * Auto-detect NIP-17 based on room:
     * - Group chats (>1 recipient) always require NIP-17
     * - Single recipient: NIP-17 off by default (can be toggled)
     */
    fun updateNip17FromRoom() {
        val currentRoom = _room.value
        if (currentRoom != null) {
            _requiresNip17.value = currentRoom.users.size > 1
            if (_requiresNip17.value) {
                _nip17.value = true
            }
        } else {
            _requiresNip17.value = false
            _nip17.value = false
        }
    }

    fun updateMessage(newMessage: TextFieldValue) {
        _message.value = newMessage
    }

    fun updateSubject(newSubject: TextFieldValue) {
        _subject.value = newSubject
    }

    fun setReply(note: Note) {
        _replyTo.value = note
    }

    fun clearReply() {
        _replyTo.value = null
    }

    /**
     * Toggle NIP-04/NIP-17 mode.
     * If NIP-17 is required (group chat), stays on NIP-17.
     */
    fun toggleNip17() {
        if (_requiresNip17.value) {
            _nip17.value = true
        } else {
            _nip17.value = !_nip17.value
        }
    }

    /**
     * Enable NIP-17 (e.g., when recipient has DM relay list).
     */
    fun enableNip17() {
        _nip17.value = true
    }

    /**
     * Send the current message. Builds the appropriate event template
     * (NIP-04 or NIP-17) and delegates to IAccount for signing/broadcasting.
     *
     * @return true if send was initiated, false if preconditions not met
     */
    suspend fun send(): Boolean {
        val currentRoom = _room.value ?: return false
        val messageText = _message.value.text
        if (messageText.isBlank()) return false

        if (_nip17.value || currentRoom.users.size > 1 || _replyTo.value?.event is NIP17Group) {
            sendNip17(currentRoom, messageText)
        } else {
            sendNip04(currentRoom, messageText)
        }

        return true
    }

    private suspend fun sendNip17(
        room: ChatroomKey,
        messageText: String,
    ) {
        val pTags =
            room.users.mapNotNull { hexKey ->
                (cache.getOrCreateUser(hexKey) as? User)?.toPTag()
            }

        val replyHint = _replyTo.value?.toEventHint<BaseDMGroupEvent>()

        val template =
            if (replyHint == null) {
                ChatMessageEvent.build(messageText, pTags) {
                    hashtags(findHashtags(messageText))
                    references(findURLs(messageText))
                    quotes(findNostrEventUris(messageText))
                }
            } else {
                ChatMessageEvent.reply(messageText, replyHint) {
                    hashtags(findHashtags(messageText))
                    references(findURLs(messageText))
                    quotes(findNostrEventUris(messageText))
                }
            }

        account.sendNip17PrivateMessage(template)
    }

    private suspend fun sendNip04(
        room: ChatroomKey,
        messageText: String,
    ) {
        val toUser = (cache.getOrCreateUser(room.users.first()) as? User)?.toPTag() ?: return

        val template =
            PrivateDmEvent.build(
                toUser = toUser,
                message = messageText,
                replyingTo = _replyTo.value?.toEventHint<PrivateDmEvent>(),
                signer = account.signer,
            )

        account.sendNip04PrivateMessage(template)
    }

    /**
     * Clear all composition state after sending or cancelling.
     */
    fun clear() {
        _message.value = TextFieldValue("")
        _subject.value = TextFieldValue("")
        _replyTo.value = null
    }

    /**
     * Format room users as npub display string.
     * Useful for showing recipients in the UI.
     */
    fun toUsersDisplay(): String {
        val currentRoom = _room.value ?: return ""
        return currentRoom.users
            .mapNotNull { hexKey ->
                runCatching { Hex.decode(hexKey).toNpub() }.getOrNull()
            }.joinToString(", ") { "@$it" }
    }
}
