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
package com.vitorpamplona.amethyst.model.privateChats

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.collections.immutable.persistentSetOf

class ChatroomList(
    val ownerPubKey: HexKey,
) {
    var chatrooms = LargeCache<ChatroomKey, Chatroom>()
        private set

    private fun getOrCreatePrivateChatroomSync(key: ChatroomKey): Chatroom = chatrooms.getOrCreate(key) { Chatroom() }

    fun getOrCreatePrivateChatroom(user: User): Chatroom {
        val key = ChatroomKey(persistentSetOf(user.pubkeyHex))
        return getOrCreatePrivateChatroom(key)
    }

    fun getOrCreatePrivateChatroom(key: ChatroomKey): Chatroom = getOrCreatePrivateChatroomSync(key)

    fun addMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
        }
    }

    fun addMessage(
        user: User,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.roomMessages) {
            privateChatroom.addMessageSync(msg)
            if (msg.author?.pubkeyHex == ownerPubKey) {
                privateChatroom.ownerSentMessage = true
            }
        }
    }

    fun createChatroom(withKey: ChatroomKey) {
        getOrCreatePrivateChatroom(withKey)
    }

    fun removeMessage(
        user: User,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.removeMessageSync(msg)
        }
    }

    fun removeMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg in privateChatroom.roomMessages) {
            privateChatroom.removeMessageSync(msg)
        }
    }

    fun hasSentMessagesTo(key: ChatroomKey?): Boolean {
        if (key == null) return false
        return chatrooms.get(key)?.ownerSentMessage == true
    }
}
