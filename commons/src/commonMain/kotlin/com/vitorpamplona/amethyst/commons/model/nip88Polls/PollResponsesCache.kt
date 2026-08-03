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

@Stable
class PollResponsesCache : UserDependencies {
    companion object {
        val DefaultFeedOrder: Comparator<PollResponseEvent> =
            compareByDescending<PollResponseEvent> { it.createdAt }.thenBy { it.id }
    }

    class ResponseTally(
        val allResponses: List<Note> = emptyList(),
        /**
         * The poll's own rules. Null until the kind-1068 event arrives — responses often show up
         * first — in which case the tally stays permissive rather than dropping votes it cannot
         * yet validate.
         */
        val policy: PollTallyPolicy? = null,
    ) {
        /** Responses stamped outside the poll's window, excluded from [votes]. */
        val lateVotes: Int

        /** Voters whose response cast no valid option code at all, excluded from [tally]. */
        val ignoredVotes: Int

        /** One response per author — the latest one that is actually eligible. */
        val votes: Map<User, PollResponseEvent>

        /** Option code -> the voters who picked it. */
        val tally: Map<String, Set<User>>

        /** Voters whose response cast at least one valid option code. */
        val countedVoters: Set<User>

        init {
            var late = 0
            val eligible =
                if (policy == null) {
                    allResponses
                } else {
                    allResponses.filter { note ->
                        val event = note.event
                        val inWindow = event !is PollResponseEvent || policy.isInWindow(event.createdAt)
                        if (!inWindow) late++
                        inWindow
                    }
                }

            lateVotes = late
            votes = eligible.latestByAuthor()

            val counted = mutableMapOf<String, MutableSet<User>>()
            val voters = mutableSetOf<User>()
            votes.forEach { (user, responseEvent) ->
                val codes = responseEvent.responses()
                val accepted = policy?.accept(codes) ?: codes.toSet()
                if (accepted.isNotEmpty()) {
                    voters.add(user)
                    accepted.forEach { code -> counted.getOrPut(code) { mutableSetOf() }.add(user) }
                }
            }

            ignoredVotes = votes.size - voters.size
            countedVoters = voters
            tally = counted
        }

        fun winning() = tally.maxByOrNull { it.value.size }?.key

        /**
         * Distinct people whose vote counts. This is the denominator for every percentage: on a
         * multiple-choice poll someone who ticks three boxes is still one voter, so the bars read
         * as "share of people" and may sum past 100%.
         */
        fun totalVoters() = countedVoters.size

        /** Boxes ticked across all voters. Equal to [totalVoters] on a single-choice poll. */
        fun totalSelections() = tally.entries.sumOf { it.value.size }
    }

    val responses = MutableStateFlow(ResponseTally())

    fun addResponse(note: Note) {
        // if it's already there, quick exit
        if (responses.value.allResponses.contains(note)) return

        responses.update {
            ResponseTally(
                it.allResponses + note,
                it.policy,
            )
        }
    }

    fun removeResponse(deleteNote: Note) {
        // if it's not already there, quick exit
        if (!responses.value.allResponses.contains(deleteNote)) return

        responses.update {
            ResponseTally(
                it.allResponses - deleteNote,
                it.policy,
            )
        }
    }

    /**
     * Hands the tally the poll's rules, recomputing it in place. Idempotent — the common case is
     * the same poll event arriving again from another relay.
     */
    fun updatePolicy(newPolicy: PollTallyPolicy) {
        if (responses.value.policy == newPolicy) return

        responses.update {
            ResponseTally(it.allResponses, newPolicy)
        }
    }

    fun ResponseTally.filterTo(
        code: String,
        forKey: HexKey,
        priority: Set<HexKey>,
    ): TallyResults {
        val comparator = compareByDescending<User> { it.pubkeyHex == forKey }.thenByDescending { it.pubkeyHex in priority }.thenBy { it.pubkeyHex }

        val usersThatVotedForThisOption = tally[code] ?: emptyList()

        val voters = totalVoters()

        // Share of voters, not share of selections: a multiple-choice voter who ticks three boxes
        // is one person, and a bar that reads "70%" should mean "7 in 10 people". Identical to the
        // selections basis on a single-choice poll.
        val percent =
            if (voters > 0) {
                usersThatVotedForThisOption.size.toFloat() / voters.toFloat()
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

    // Counted, not merely present: a response whose option codes the poll doesn't recognise casts
    // no vote, so its author should still be offered the voting controls.
    fun hasPubKeyVoted(user: User): Boolean = responses.value.countedVoters.contains(user)

    fun hasPubKeyVotedFlow(user: User): Flow<Boolean> = responses.map { it.countedVoters.contains(user) }.distinctUntilChanged()
}

@Stable
class TallyResults(
    val users: List<User> = emptyList(),
    val percent: Float = 0.0f,
    val isWinning: Boolean = false,
)
