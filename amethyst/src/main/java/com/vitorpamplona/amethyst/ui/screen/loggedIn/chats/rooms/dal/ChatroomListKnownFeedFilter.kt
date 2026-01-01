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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent

class ChatroomListKnownFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val chatList = account.chatroomList
        val followingKeySet = account.followingKeySet()

        val privateMessages =
            chatList.rooms.mapNotNull { key, chatroom ->
                if ((chatroom.senderIntersects(followingKeySet) || chatList.hasSentMessagesTo(key)) &&
                    !account.isAllHidden(key.users)
                ) {
                    chatroom.newestMessage
                } else {
                    null
                }
            }

        val publicChannels =
            account
                .publicChatList.flow.value
                .mapNotNull { it ->
                    LocalCache
                        .getOrCreatePublicChatChannel(it.eventId)
                        .notes
                        .filter { key, it -> account.isAcceptable(it) && it.event != null }
                        .sortedWith(DefaultFeedOrder)
                        .firstOrNull()
                }

        val ephemeralChats =
            account
                .ephemeralChatList.liveEphemeralChatList.value
                .mapNotNull { it ->
                    LocalCache
                        .getOrCreateEphemeralChannel(it)
                        .notes
                        .filter { key, it -> account.isAcceptable(it) && it.event != null }
                        .sortedWith(DefaultFeedOrder)
                        .firstOrNull()
                }

        return (privateMessages + publicChannels + ephemeralChats).sortedWith(DefaultFeedOrder)
    }

    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        val me = account.userProfile()

        // Gets the latest message by channel from the new items.
        val newRelevantPublicMessages = filterRelevantPublicMessages(newItems, account)
        val newRelevantEphemeralChats = filterRelevantEphemeralChats(newItems, account)

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        if (newRelevantPrivateMessages.isEmpty() && newRelevantPublicMessages.isEmpty() && newRelevantEphemeralChats.isEmpty()) {
            return oldList
        }

        var myNewList = oldList

        newRelevantPublicMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val channelId = (oldNote.event as? ChannelMessageEvent)?.channelId()
                if (newNotePair.key == channelId) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantEphemeralChats.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val noteEvent = (oldNote.event as? EphemeralChatEvent)?.roomId()
                if (newNotePair.key == noteEvent) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantPrivateMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val oldRoom = (oldNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)

                if (newNotePair.key == oldRoom) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        return sort(myNewList.toSet()).take(1000)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        // Gets the latest message by channel from the new items.
        val newRelevantPublicMessages = filterRelevantPublicMessages(newItems, account)
        val newRelevantEphemeralChats = filterRelevantEphemeralChats(newItems, account)

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        return if (newRelevantPrivateMessages.isEmpty() && newRelevantPublicMessages.isEmpty() && newRelevantEphemeralChats.isEmpty()) {
            emptySet()
        } else {
            (newRelevantPrivateMessages.values + newRelevantPublicMessages.values + newRelevantEphemeralChats.values).toSet()
        }
    }

    private fun filterRelevantPublicMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        val followingChannels = account.publicChatList.flowSet.value
        val newRelevantPublicMessages = mutableMapOf<String, Note>()
        newItems
            .forEach { newNote ->
                val channelId = (newNote.event as? ChannelMessageEvent)?.channelId()
                if (channelId != null) {
                    if (channelId in followingChannels && account.isAcceptable(newNote)) {
                        val lastNote = newRelevantPublicMessages.get(channelId)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                                newRelevantPublicMessages.put(channelId, newNote)
                            }
                        } else {
                            newRelevantPublicMessages.put(channelId, newNote)
                        }
                    }
                }
            }
        return newRelevantPublicMessages
    }

    private fun filterRelevantEphemeralChats(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<RoomId, Note> {
        val followingEphemeralChats = account.ephemeralChatList.liveEphemeralChatList.value
        val newRelevantEphemeralChats = mutableMapOf<RoomId, Note>()
        newItems
            .forEach { newNote ->
                val noteEvent = newNote.event as? EphemeralChatEvent
                if (noteEvent != null) {
                    val room = noteEvent.roomId()
                    if (room != null && room in followingEphemeralChats && account.isAcceptable(newNote)) {
                        val lastNote = newRelevantEphemeralChats.get(room)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                                newRelevantEphemeralChats.put(room, newNote)
                            }
                        } else {
                            newRelevantEphemeralChats.put(room, newNote)
                        }
                    }
                }
            }
        return newRelevantEphemeralChats
    }

    private fun filterRelevantPrivateMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<ChatroomKey, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<ChatroomKey, Note>()
        newItems
            .forEach { newNote ->
                val roomKey = (newNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)
                if (roomKey != null) {
                    val room = account.chatroomList.rooms.get(roomKey)
                    if (room != null) {
                        if (
                            (
                                newNote.author?.pubkeyHex == me.pubkeyHex ||
                                    room.senderIntersects(followingKeySet) ||
                                    account.chatroomList.hasSentMessagesTo(roomKey)
                            ) &&
                            !account.isAllHidden(roomKey.users)
                        ) {
                            val lastNote = newRelevantPrivateMessages.get(roomKey)
                            if (lastNote != null) {
                                if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                                    newRelevantPrivateMessages.put(roomKey, newNote)
                                }
                            } else {
                                newRelevantPrivateMessages.put(roomKey, newNote)
                            }
                        }
                    }
                }
            }
        return newRelevantPrivateMessages
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
