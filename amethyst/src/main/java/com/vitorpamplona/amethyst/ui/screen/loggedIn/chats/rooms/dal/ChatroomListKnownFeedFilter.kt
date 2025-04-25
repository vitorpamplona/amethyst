/**
 * Copyright (c) 2024 Vitor Pamplona
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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEventIds
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent

class ChatroomListKnownFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val knownChatrooms =
            me.privateChatrooms.filter {
                (it.value.senderIntersects(followingKeySet) || me.hasSentMessagesTo(it.key)) &&
                    !account.isAllHidden(it.key.users)
            }

        val privateMessages =
            knownChatrooms.mapNotNull { it ->
                it.value.roomMessages.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).lastOrNull {
                    it.event != null
                }
            }

        val publicChannels =
            account
                .selectedChatsFollowList()
                .mapNotNull { LocalCache.getChannelIfExists(it) }
                .mapNotNull { it ->
                    it.notes
                        .filter { key, it -> account.isAcceptable(it) && it.event != null }
                        .sortedWith(DefaultFeedOrder)
                        .firstOrNull()
                }

        return (privateMessages + publicChannels).sortedWith(DefaultFeedOrder)
    }

    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        val me = account.userProfile()

        // Gets the latest message by channel from the new items.
        val newRelevantPublicMessages = filterRelevantPublicMessages(newItems, account)

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        if (newRelevantPrivateMessages.isEmpty() && newRelevantPublicMessages.isEmpty()) {
            return oldList
        }

        var myNewList = oldList

        newRelevantPublicMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                if (newNotePair.key == oldNote.channelHex()) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0) > (oldNote.createdAt() ?: 0)) {
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
                    if ((newNotePair.value.createdAt() ?: 0) > (oldNote.createdAt() ?: 0)) {
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

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        return if (newRelevantPrivateMessages.isEmpty() && newRelevantPublicMessages.isEmpty()) {
            emptySet()
        } else {
            (newRelevantPrivateMessages.values + newRelevantPublicMessages.values).toSet()
        }
    }

    private fun filterRelevantPublicMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        val followingChannels =
            account
                .userProfile()
                .latestContactList
                ?.taggedEventIds()
                ?.toSet() ?: emptySet()
        val newRelevantPublicMessages = mutableMapOf<String, Note>()
        newItems
            .filter { it.event is ChannelMessageEvent }
            .forEach { newNote ->
                newNote.channelHex()?.let { channelHex ->
                    if (channelHex in followingChannels && account.isAcceptable(newNote)) {
                        val lastNote = newRelevantPublicMessages.get(channelHex)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0) > (lastNote.createdAt() ?: 0)) {
                                newRelevantPublicMessages.put(channelHex, newNote)
                            }
                        } else {
                            newRelevantPublicMessages.put(channelHex, newNote)
                        }
                    }
                }
            }
        return newRelevantPublicMessages
    }

    private fun filterRelevantPrivateMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<ChatroomKey, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<ChatroomKey, Note>()
        newItems
            .filter { it.event is ChatroomKeyable }
            .forEach { newNote ->
                val roomKey = (newNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)
                val room = account.userProfile().privateChatrooms[roomKey]

                if (roomKey != null && room != null) {
                    if (
                        (
                            newNote.author?.pubkeyHex == me.pubkeyHex ||
                                room.senderIntersects(followingKeySet) ||
                                me.hasSentMessagesTo(roomKey)
                        ) &&
                        !account.isAllHidden(roomKey.users)
                    ) {
                        val lastNote = newRelevantPrivateMessages.get(roomKey)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0) > (lastNote.createdAt() ?: 0)) {
                                newRelevantPrivateMessages.put(roomKey, newNote)
                            }
                        } else {
                            newRelevantPrivateMessages.put(roomKey, newNote)
                        }
                    }
                }
            }
        return newRelevantPrivateMessages
    }

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(DefaultFeedOrder)
}
