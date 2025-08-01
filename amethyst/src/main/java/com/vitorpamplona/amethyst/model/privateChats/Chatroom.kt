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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NotesGatherer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class Chatroom : NotesGatherer {
    var activeSenders: Set<User> = setOf()
    var messages: Set<Note> = setOf()
    var subject = MutableStateFlow<String?>(null)
    var subjectCreatedAt: Long? = null
    var ownerSentMessage: Boolean = false
    var lastMessage: Note? = null

    override fun removeNote(note: Note) {
        removeMessageSync(note)
    }

    @Synchronized
    fun addMessageSync(msg: Note): Boolean {
        if (msg !in messages) {
            messages = messages + msg
            msg.addGatherer(this)

            msg.author?.let { author ->
                if (author !in activeSenders) {
                    activeSenders += author
                }
            }

            val createdAt = msg.createdAt() ?: 0
            if (createdAt > (lastMessage?.createdAt() ?: 0)) {
                lastMessage = msg
            }

            val newSubject = msg.event?.subject()

            if (newSubject != null && (msg.createdAt() ?: 0) > (subjectCreatedAt ?: 0)) {
                subject.tryEmit(newSubject)
                subjectCreatedAt = msg.createdAt()
            }
            return true
        }
        return false
    }

    @Synchronized
    fun removeMessageSync(msg: Note): Boolean {
        if (msg in messages) {
            messages = messages - msg
            msg.removeGatherer(this)

            messages
                .filter { it.event?.subject() != null }
                .sortedBy { it.createdAt() }
                .lastOrNull()
                ?.let {
                    subject.tryEmit(it.event?.subject())
                    subjectCreatedAt = it.createdAt()
                }
            return true
        }
        return false
    }

    fun senderIntersects(keySet: Set<HexKey>): Boolean = activeSenders.any { it.pubkeyHex in keySet }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = messages.sortedWith(DefaultFeedOrder)

        val toKeep =
            if ((sorted.firstOrNull()?.createdAt() ?: 0) > TimeUtils.oneWeekAgo()) {
                // Recent messages, keep last 100
                sorted.take(100).toSet()
            } else {
                // Old messages, keep the last one.
                sorted.take(1).toSet()
            } + sorted.filter { it.flowSet?.isInUse() ?: false } + sorted.filter { it.event !is PrivateDmEvent }

        val toRemove = messages.minus(toKeep)
        messages = toKeep
        return toRemove
    }
}
