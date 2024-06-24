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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils

@Stable
class Chatroom() {
    var authors: Set<User> = setOf()
    var roomMessages: Set<Note> = setOf()
    var subject: String? = null
    var subjectCreatedAt: Long? = null

    @Synchronized
    fun addMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg

            msg.author?.let { author ->
                if (author !in authors) {
                    authors += author
                }
            }

            val newSubject = msg.event?.subject()

            if (newSubject != null && (msg.createdAt() ?: 0) > (subjectCreatedAt ?: 0)) {
                subject = newSubject
                subjectCreatedAt = msg.createdAt()
            }
        }
    }

    @Synchronized
    fun removeMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg in roomMessages) {
            roomMessages = roomMessages - msg

            roomMessages
                .filter { it.event?.subject() != null }
                .sortedBy { it.createdAt() }
                .lastOrNull()
                ?.let {
                    subject = it.event?.subject()
                    subjectCreatedAt = it.createdAt()
                }
        }
    }

    fun senderIntersects(keySet: Set<HexKey>): Boolean {
        return authors.any { it.pubkeyHex in keySet }
    }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = roomMessages.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()

        val toKeep =
            if ((sorted.firstOrNull()?.createdAt() ?: 0) > TimeUtils.oneWeekAgo()) {
                // Recent messages, keep last 100
                sorted.take(100).toSet()
            } else {
                // Old messages, keep the last one.
                sorted.take(1).toSet()
            } + sorted.filter { it.liveSet?.isInUse() ?: false }

        val toRemove = roomMessages.minus(toKeep)
        roomMessages = toKeep
        return toRemove
    }
}
