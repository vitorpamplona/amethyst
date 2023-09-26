package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils

@Stable
class Chatroom() {
    var roomMessages: Set<Note> = setOf()
    var subject: String? = null
    var subjectCreatedAt: Long? = null

    @Synchronized
    fun addMessageSync(msg: Note) {
        checkNotInMainThread()

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg

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

        if (msg !in roomMessages) {
            roomMessages = roomMessages + msg

            roomMessages.filter { it.event?.subject() != null }.sortedBy { it.createdAt() }.lastOrNull()?.let {
                subject = it.event?.subject()
                subjectCreatedAt = it.createdAt()
            }
        }
    }

    fun senderIntersects(keySet: Set<HexKey>): Boolean {
        return roomMessages.any { it.author?.pubkeyHex in keySet }
    }

    fun pruneMessagesToTheLatestOnly(): Set<Note> {
        val sorted = roomMessages.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()

        val toKeep = if ((sorted.firstOrNull()?.createdAt() ?: 0) > TimeUtils.oneWeekAgo()) {
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
