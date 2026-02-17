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
package com.vitorpamplona.amethyst.commons.model.nip88Polls

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.amethyst.commons.model.latestByAuthor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@Stable
class PollResponsesCache : UserDependencies {
    companion object {
        val DefaultFeedOrder: Comparator<PollResponseEvent> =
            compareByDescending<PollResponseEvent> { it.createdAt }.thenBy { it.id }
    }

    class ResponseTally(
        val allResponses: List<Note> = emptyList(),
    ) {
        val votes: Map<User, PollResponseEvent> = allResponses.latestByAuthor()
        val tally: Map<String, Set<User>> = votes.votesByOption()

        fun winning() = tally.maxByOrNull { it.value.size }?.key

        fun totalVotes() = tally.entries.sumOf { it.value.size }
    }

    val responses = MutableStateFlow(ResponseTally())

    fun addResponse(note: Note) {
        // if it's already there, quick exit
        if (responses.value.allResponses.contains(note)) return

        responses.update {
            ResponseTally(
                it.allResponses + note,
            )
        }
    }

    fun removeResponse(deleteNote: Note) {
        // if it's not already there, quick exit
        if (!responses.value.allResponses.contains(deleteNote)) return

        responses.update {
            ResponseTally(
                it.allResponses - deleteNote,
            )
        }
    }

    fun ResponseTally.filterTo(
        code: String,
        forKey: HexKey,
        priority: Set<HexKey>,
    ): TallyResults {
        val comparator = compareByDescending<User> { it.pubkeyHex == forKey }.thenByDescending { it.pubkeyHex in priority }.thenBy { it.pubkeyHex }

        val usersThatVotedForThisOption = tally[code] ?: emptyList()

        val votes = totalVotes()

        val percent =
            if (votes > 0) {
                usersThatVotedForThisOption.size.toFloat() / votes.toFloat()
            } else {
                0f
            }

        val sortedUsers = usersThatVotedForThisOption.sortedWith(comparator)

        return TallyResults(sortedUsers, percent, code == winning())
    }

    fun currentTally(
        code: String,
        forKey: HexKey,
        priorityAccounts: Set<HexKey>,
    ): TallyResults = responses.value.filterTo(code, forKey, priorityAccounts)

    fun tallyFlow(
        code: String,
        forKey: HexKey,
        priorityAccounts: Flow<Set<HexKey>>,
    ): Flow<TallyResults> =
        combine(responses, priorityAccounts) { responses, priority ->
            responses.filterTo(code, forKey, priority)
        }

    fun hasPubKeyVoted(user: User): Boolean = responses.value.votes.containsKey(user)

    fun hasPubKeyVotedFlow(user: User): Flow<Boolean> = responses.map { hasPubKeyVoted(user) }.distinctUntilChanged()
}

@Stable
class TallyResults(
    val users: List<User> = emptyList(),
    val percent: Float = 0.0f,
    val isWinning: Boolean = false,
)

fun Map<User, PollResponseEvent>.votesByOption(): Map<String, Set<User>> {
    val tally = mutableMapOf<String, MutableSet<User>>()

    this.forEach { (user, responseEvent) ->
        responseEvent.responses().forEach { code ->
            val currentTally = tally[code]
            if (currentTally == null) {
                tally[code] = mutableSetOf(user)
            } else {
                currentTally.add(user)
            }
        }
    }
    return tally
}
