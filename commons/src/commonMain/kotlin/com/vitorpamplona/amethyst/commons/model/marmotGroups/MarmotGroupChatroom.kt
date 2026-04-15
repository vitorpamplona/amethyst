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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel.Companion.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.ListChange
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NotesGatherer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference

/**
 * Represents a Marmot MLS group chat room.
 * Tracks decrypted inner messages for a single group.
 * Follows the same pattern as [com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom].
 */
@Stable
class MarmotGroupChatroom(
    val nostrGroupId: HexKey,
) : NotesGatherer {
    var messages: Set<Note> = setOf()
    var displayName = MutableStateFlow<String?>(null)
    var description = MutableStateFlow<String?>(null)
    var adminPubkeys = MutableStateFlow<List<HexKey>>(emptyList())
    var relays = MutableStateFlow<List<String>>(emptyList())
    var memberCount = MutableStateFlow(0)
    var newestMessage: Note? = null
    val unreadCount = MutableStateFlow(0)

    private var changesFlow: WeakReference<MutableSharedFlow<ListChange<Note>>> = WeakReference(null)

    fun changesFlow(): MutableSharedFlow<ListChange<Note>> {
        val current = changesFlow.get()
        if (current != null) return current
        val new = MutableSharedFlow<ListChange<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
        changesFlow = WeakReference(new)
        return new
    }

    override fun removeNote(note: Note) {
        removeMessageSync(note)
    }

    @Synchronized
    fun addMessageSync(msg: Note): Boolean {
        if (msg !in messages) {
            messages = messages + msg
            msg.addGatherer(this)

            val createdAt = msg.createdAt() ?: 0L
            if (createdAt > (newestMessage?.createdAt() ?: 0L)) {
                newestMessage = msg
            }

            unreadCount.value += 1
            changesFlow.get()?.tryEmit(ListChange.Addition(msg))
            return true
        }
        return false
    }

    /**
     * Add a message that is being restored from persistent storage on app
     * startup. Behaves like [addMessageSync] but does NOT bump the unread
     * count — restored messages were already seen by the user in a previous
     * session.
     */
    @Synchronized
    fun restoreMessageSync(msg: Note): Boolean {
        if (msg !in messages) {
            messages = messages + msg
            msg.addGatherer(this)

            val createdAt = msg.createdAt() ?: 0L
            if (createdAt > (newestMessage?.createdAt() ?: 0L)) {
                newestMessage = msg
            }

            changesFlow.get()?.tryEmit(ListChange.Addition(msg))
            return true
        }
        return false
    }

    @Synchronized
    fun removeMessageSync(msg: Note): Boolean {
        if (msg in messages) {
            messages = messages - msg
            msg.removeGatherer(this)

            if (msg == newestMessage) {
                newestMessage = messages.maxByOrNull { it.createdAt() ?: 0L }
            }

            changesFlow.get()?.tryEmit(ListChange.Deletion(msg))
            return true
        }
        return false
    }

    fun markAsRead() {
        unreadCount.value = 0
    }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = messages.sortedWith(DefaultFeedOrder)
        val toKeep =
            sorted.take(100).toSet() +
                sorted.filter { it.flowSet?.isInUse() ?: false }

        val toRemove = messages.minus(toKeep)
        messages = toKeep

        changesFlow.get()?.tryEmit(ListChange.SetDeletion<Note>(toRemove))
        return toRemove
    }
}
