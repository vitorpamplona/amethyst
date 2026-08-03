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
package com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache.ResponseTally
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

/** One option's share of the poll, ready to draw. */
@Immutable
class PollOptionResult(
    val code: String,
    val label: String,
    val voters: Int,
    /** Share of *voters*, so multiple-choice bars can sum past 100%. */
    val percent: Float,
    val isWinning: Boolean,
    /** The first few voters in relevance order, for the avatar stack. */
    val topVoters: List<User>,
)

/** One person's vote: who they are, what they picked, and when. */
@Immutable
class PollVoterRow(
    val user: User,
    val codes: List<String>,
    val labels: List<String>,
    val createdAt: Long,
)

@Immutable
class PollResultsUiState(
    val options: List<PollOptionResult> = emptyList(),
    val voters: List<PollVoterRow> = emptyList(),
    val totalVoters: Int = 0,
    val totalSelections: Int = 0,
    val ignoredVotes: Int = 0,
    val lateVotes: Int = 0,
    val hiddenVoters: Int = 0,
    val myVote: List<String> = emptyList(),
    val type: PollType = PollType.SINGLE_CHOICE,
    val endsAt: Long? = null,
)

/**
 * Screen state for the poll results page.
 *
 * Reads the live tally off the poll [Note] — no new subscription, no second cache — and turns it
 * into rows the UI can render without doing set arithmetic in composition. The only interactive
 * state is [selectedOption], which scopes the voter list without touching the summary above it.
 *
 * Muting is applied here rather than in the tally: a muted voter still counts toward the totals
 * (they did vote, and hiding them from the maths would misreport the poll), they are just not
 * listed, and [PollResultsUiState.hiddenVoters] says how many.
 */
@Stable
class PollResultsViewModel(
    private val pollNote: Note,
    private val forKey: HexKey,
    private val isHidden: (HexKey) -> Boolean,
    follows: Flow<Set<HexKey>>,
    hiddenChanges: Flow<Any?>,
) : ViewModel() {
    companion object {
        /** Faces shown per option before collapsing into a "+N" chip, matching UserGallery. */
        const val AVATAR_STACK = 4
    }

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    val uiState: StateFlow<PollResultsUiState> =
        combine(
            pollNote.pollState().responses,
            follows,
            _selectedOption,
            hiddenChanges,
        ) { tally, followSet, selected, _ ->
            build(tally, followSet, selected)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = build(pollNote.pollState().responses.value, emptySet(), null),
        )

    fun selectOption(code: String?) {
        _selectedOption.value = if (_selectedOption.value == code) null else code
    }

    private fun build(
        tally: ResponseTally,
        follows: Set<HexKey>,
        selected: String?,
    ): PollResultsUiState {
        val event = pollNote.event as? PollEvent

        // Poll order, not tally order, so the rows never reshuffle as votes arrive. Falls back to
        // whatever the responses referenced while the kind-1068 event is still in flight.
        val options = event?.options().orEmpty()
        val codesInOrder = if (options.isNotEmpty()) options.map { it.code } else tally.tally.keys.sorted()
        val labels = options.associate { it.code to it.label }

        val byRelevance =
            compareByDescending<User> { it.pubkeyHex == forKey }
                .thenByDescending { it.pubkeyHex in follows }
                .thenBy { it.pubkeyHex }

        val totalVoters = tally.totalVoters()

        val optionResults =
            codesInOrder.map { code ->
                val voters = tally.tally[code].orEmpty()
                PollOptionResult(
                    code = code,
                    label = labels[code] ?: code,
                    voters = voters.size,
                    percent = if (totalVoters > 0) voters.size.toFloat() / totalVoters else 0f,
                    isWinning = voters.isNotEmpty() && code == tally.winning(),
                    topVoters = voters.sortedWith(byRelevance).take(AVATAR_STACK),
                )
            }

        // Invert the tally once: voter -> the codes they picked, in poll order.
        val picks = mutableMapOf<User, MutableList<String>>()
        codesInOrder.forEach { code ->
            tally.tally[code]?.forEach { user ->
                picks.getOrPut(user) { mutableListOf() }.add(code)
            }
        }

        var hidden = 0
        val rows =
            picks
                .mapNotNull { (user, codes) ->
                    if (isHidden(user.pubkeyHex)) {
                        hidden++
                        return@mapNotNull null
                    }
                    if (selected != null && selected !in codes) return@mapNotNull null

                    PollVoterRow(
                        user = user,
                        codes = codes,
                        labels = codes.map { labels[it] ?: it },
                        createdAt = tally.votes[user]?.createdAt ?: 0L,
                    )
                }.sortedWith { a, b -> byRelevance.compare(a.user, b.user) }

        return PollResultsUiState(
            options = optionResults,
            voters = rows,
            totalVoters = totalVoters,
            totalSelections = tally.totalSelections(),
            ignoredVotes = tally.ignoredVotes,
            lateVotes = tally.lateVotes,
            hiddenVoters = hidden,
            myVote =
                picks.entries
                    .firstOrNull { it.key.pubkeyHex == forKey }
                    ?.value
                    .orEmpty(),
            type = event?.pollType() ?: PollType.SINGLE_CHOICE,
            endsAt = event?.endsAt(),
        )
    }

    class Factory(
        private val pollNote: Note,
        private val forKey: HexKey,
        private val isHidden: (HexKey) -> Boolean,
        private val follows: Flow<Set<HexKey>>,
        private val hiddenChanges: Flow<Any?>,
    ) : ViewModelProvider.Factory {
        // The multiplatform signature — commonMain has no java.lang.Class.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = PollResultsViewModel(pollNote, forKey, isHidden, follows, hiddenChanges) as T
    }
}
