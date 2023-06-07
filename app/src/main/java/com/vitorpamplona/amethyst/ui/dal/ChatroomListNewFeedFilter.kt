package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.actions.updated
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ChatroomListNewFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val privateChatrooms = account.userProfile().privateChatrooms
        val messagingWith = privateChatrooms.keys.filter {
            it.pubkeyHex !in followingKeySet && !me.hasSentMessagesTo(it) && account.isAcceptable(it)
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
                    val oldRoom = (oldNote.event as? PrivateDmEvent)?.talkingWith(me.pubkeyHex)

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

    private fun filterRelevantPrivateMessages(newItems: Set<Note>, account: Account): MutableMap<String, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<String, Note>()
        newItems.filter { it.event is PrivateDmEvent }.forEach { newNote ->
            val roomUserHex = (newNote.event as? PrivateDmEvent)?.talkingWith(me.pubkeyHex)
            val roomUser = roomUserHex?.let { LocalCache.users[it] }

            if (roomUserHex != null &&
                (newNote.author?.pubkeyHex != me.pubkeyHex && roomUserHex !in followingKeySet && !me.hasSentMessagesTo(roomUser)) &&
                !account.isHidden(roomUserHex)
            ) {
                val lastNote = newRelevantPrivateMessages.get(roomUserHex)
                if (lastNote != null) {
                    if ((newNote.createdAt() ?: 0) > (lastNote.createdAt() ?: 0)) {
                        newRelevantPrivateMessages.put(roomUserHex, newNote)
                    }
                } else {
                    newRelevantPrivateMessages.put(roomUserHex, newNote)
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
