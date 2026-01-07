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
package com.vitorpamplona.amethyst.commons.model.nip56Reports

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.amethyst.commons.relays.EOSERelayList
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class UserReportCache : UserDependencies {
    val receivedReportsByAuthor = MutableStateFlow(mapOf<User, Set<Note>>())

    /** Tracks EOSE (End Of Stored Events) for relay subscriptions */
    val latestEOSEs = EOSERelayList()

    fun addReport(note: Note) {
        val author = note.author ?: return

        val reportsBy = receivedReportsByAuthor.value[author]

        // if it's already there, quick exit
        if (reportsBy != null && reportsBy.contains(note)) return

        receivedReportsByAuthor.update {
            val author = note.author
            if (author == null) {
                it
            } else {
                val reportsByInner = it[author] ?: emptySet()
                it + (author to reportsByInner + note)
            }
        }
    }

    fun removeReport(deleteNote: Note) {
        val author = deleteNote.author ?: return
        val reportsBy = receivedReportsByAuthor.value[author]

        // if it's not already there, quick exit
        if (reportsBy == null || !reportsBy.contains(deleteNote)) return

        receivedReportsByAuthor.update {
            val author = deleteNote.author
            if (author == null) {
                it
            } else {
                val reportsByInner = it[author]
                if (reportsByInner == null) {
                    it
                } else {
                    it + (author to reportsByInner - deleteNote)
                }
            }
        }
    }

    fun reportsBy(user: User): Set<Note> = receivedReportsByAuthor.value[user] ?: emptySet()

    fun count() = receivedReportsByAuthor.value.values.sumOf { it.size }

    fun countFlow() =
        receivedReportsByAuthor.map { reportPerAuthor ->
            reportPerAuthor.values.sumOf { it.size }
        }

    fun countReportAuthorsBy(users: Set<HexKey>): Int = receivedReportsByAuthor.value.count { it.key.pubkeyHex in users }

    fun all() = receivedReportsByAuthor.value.values.flatten()

    fun reportsBy(users: Set<HexKey>): List<Note> =
        receivedReportsByAuthor.value
            .flatMap {
                if (it.key.pubkeyHex in users) {
                    it.value
                } else {
                    emptyList()
                }
            }

    fun hasReport(
        loggedIn: User,
        type: ReportType,
    ): Boolean =
        receivedReportsByAuthor.value[loggedIn]?.firstOrNull {
            (it.event as? ReportEvent)?.reportedAuthor()?.any { it.type == type } ?: false
        } != null

    fun hasReportNewerThan(timeInSeconds: Long): Boolean = receivedReportsByAuthor.value.any { pair -> pair.value.firstOrNull { (it.createdAt() ?: 0L) > timeInSeconds } != null }
}
