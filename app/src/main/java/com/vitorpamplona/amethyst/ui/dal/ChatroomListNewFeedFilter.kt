package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ChatroomKey
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChatroomKeyable
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.actions.updated
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ChatroomListNewFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {

    override fun feedKey(): String {
        return account.userProfile().pubkeyHex
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val privateChatrooms = account.userProfile().privateChatrooms
        val messagingWith = privateChatrooms.keys.filter {
            privateChatrooms[it]?.senderIntersects(followingKeySet) == false &&
                !me.hasSentMessagesTo(it) && !account.isAllHidden(it.users)
        }

        val privateMessages = messagingWith.mapNotNull { it ->
            privateChatrooms[it]
                ?.roomMessages
                ?.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                ?.lastOrNull { it.event != null }
        }

        return privateMessages
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }

    @OptIn(ExperimentalTime::class)
    override fun updateListWith(oldList: List<Note>, newItems: Set<Note>): List<Note> {
        val (feed, elapsed) = measureTimedValue {
            val me = account.userProfile()

            // Gets the latest message by room from the new items.
            val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

            if (newRelevantPrivateMessages.isEmpty()) {
                return oldList
            }

            var myNewList = oldList

            newRelevantPrivateMessages.forEach { newNotePair ->
                oldList.forEach { oldNote ->
                    val oldRoom = (oldNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)

                    if (
                        (newNotePair.key == oldRoom) && (newNotePair.value.createdAt() ?: 0) > (oldNote.createdAt() ?: 0)
                    ) {
                        myNewList = myNewList.updated(oldNote, newNotePair.value)
                    }
                }
            }

            sort(myNewList.toSet()).take(1000)
        }

        // Log.d("Time", "${this.javaClass.simpleName} Modified Additive Feed in $elapsed with ${feed.size} objects")
        return feed
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        return if (newRelevantPrivateMessages.isEmpty()) {
            emptySet()
        } else {
            newRelevantPrivateMessages.values.toSet()
        }
    }

    private fun filterRelevantPrivateMessages(newItems: Set<Note>, account: Account): MutableMap<ChatroomKey, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<ChatroomKey, Note>()
        newItems.filter { it.event is PrivateDmEvent }.forEach { newNote ->
            val roomKey = (newNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)
            val room = account.userProfile().privateChatrooms[roomKey]

            if (roomKey != null && room != null &&
                (newNote.author?.pubkeyHex != me.pubkeyHex && room.senderIntersects(followingKeySet) && !me.hasSentMessagesTo(roomKey)) &&
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
        return newRelevantPrivateMessages
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
