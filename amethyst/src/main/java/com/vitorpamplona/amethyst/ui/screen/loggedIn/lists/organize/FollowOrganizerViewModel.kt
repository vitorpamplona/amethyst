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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.organize

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessRelayFetchHelper
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.followOrganizer.FollowGrouper
import com.vitorpamplona.amethyst.model.followOrganizer.FollowOrganizer
import com.vitorpamplona.amethyst.model.followOrganizer.FollowStats
import com.vitorpamplona.amethyst.model.followOrganizer.OrganizeStrategy
import com.vitorpamplona.amethyst.model.followOrganizer.ProposedGroup
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Immutable
data class OrganizerGroupUi(
    val title: String,
    val memberCount: Int,
    val selected: Boolean,
)

@Stable
class FollowOrganizerViewModel : ViewModel() {
    lateinit var account: Account

    fun init(accountVM: AccountViewModel) {
        if (!this::account.isInitialized || this.account != accountVM.account) {
            this.account = accountVM.account
        }
    }

    val strategy = MutableStateFlow(OrganizeStrategy.LAST_SEEN)
    val makePrivate = MutableStateFlow(true)
    val isAnalyzing = MutableStateFlow(false)
    val isCreating = MutableStateFlow(false)

    val isBackfilling = MutableStateFlow(false)
    val backfillDone = MutableStateFlow(0)
    val backfillTotal = MutableStateFlow(0)

    val totalFollows = MutableStateFlow(0)
    val followsWithData = MutableStateFlow(0)

    private var backfillStarted = false

    private val stats = MutableStateFlow<List<FollowStats>?>(null)

    /** Titles the user has unchecked; everything not in here is created. */
    private val deselected = MutableStateFlow<Set<String>>(emptySet())

    /** Emits the number of lists created once a creation run finishes. */
    val created = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    private val proposed: StateFlow<List<ProposedGroup>> =
        combine(stats, strategy) { s, strat ->
            if (s == null) emptyList() else FollowGrouper.group(strat, s, TimeUtils.now())
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<OrganizerGroupUi>> =
        combine(proposed, deselected) { list, off ->
            list.map { OrganizerGroupUi(it.title, it.members.size, it.title !in off) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStrategy(newStrategy: OrganizeStrategy) {
        strategy.value = newStrategy
        deselected.value = emptySet()
    }

    fun toggle(title: String) {
        deselected.update { if (title in it) it - title else it + title }
    }

    /** Rescans the local cache and refreshes the proposed groups. */
    private suspend fun recompute() {
        val follows = account.kind3FollowList.flow.value.authors
        val result =
            withContext(Dispatchers.Default) {
                FollowOrganizer.analyze(follows, account.cache)
            }
        totalFollows.value = result.size
        followsWithData.value = result.count { it.lastSeen != null }
        stats.value = result
    }

    /** Initial analysis from the local cache, then a one-time relay backfill. */
    suspend fun analyze() {
        isAnalyzing.value = true
        try {
            deselected.value = emptySet()
            recompute()
        } finally {
            isAnalyzing.value = false
        }

        if (!backfillStarted) {
            backfillStarted = true
            backfill()
        }
    }

    /**
     * Pulls recent events authored by the follows from their outbox relays so the
     * cache has enough activity to group on. Authors are fetched in batches and
     * the proposal is refreshed after each batch so the UI fills in progressively.
     */
    suspend fun backfill() {
        val follows =
            account.kind3FollowList.flow.value.authors
                .toList()
        val relays = account.defaultGlobalRelays.flow.value
        if (follows.isEmpty() || relays.isEmpty()) return

        val batches = follows.chunked(AUTHOR_BATCH)
        isBackfilling.value = true
        backfillDone.value = 0
        backfillTotal.value = batches.size
        try {
            val helper = ChessRelayFetchHelper(account.client)
            for (batch in batches) {
                val filter =
                    Filter(
                        authors = batch,
                        kinds = ACTIVITY_KINDS,
                        limit = batch.size * EVENTS_PER_AUTHOR,
                    )
                val filters = relays.associateWith { listOf(filter) }
                withContext(Dispatchers.IO) {
                    helper.fetchEvents(filters, timeoutMs = BATCH_TIMEOUT_MS)
                }
                backfillDone.value += 1
                recompute()
            }
        } finally {
            isBackfilling.value = false
        }
    }

    companion object {
        private const val AUTHOR_BATCH = 300
        private const val EVENTS_PER_AUTHOR = 3
        private const val BATCH_TIMEOUT_MS = 10_000L

        // Kinds that signal what a person posts: notes, reposts, photos,
        // short/long video and long-form articles.
        private val ACTIVITY_KINDS =
            listOf(
                TextNoteEvent.KIND,
                RepostEvent.KIND,
                PictureEvent.KIND,
                21,
                22,
                LongTextNoteEvent.KIND,
            )
    }

    /** Signs and publishes one kind:30000 people list per selected group. */
    suspend fun createSelected() {
        val off = deselected.value
        val toCreate = proposed.value.filter { it.title !in off && it.members.isNotEmpty() }
        if (toCreate.isEmpty()) {
            created.tryEmit(0)
            return
        }

        isCreating.value = true
        try {
            val isPrivate = makePrivate.value
            var count = 0
            for (group in toCreate) {
                val members = group.members.mapNotNull { account.cache.checkGetOrCreateUser(it) }
                if (members.isEmpty()) continue
                account.peopleLists.addFollowListWithMembers(
                    listName = group.title,
                    listDescription = null,
                    members = members,
                    isPrivate = isPrivate,
                    account = account,
                )
                count++
            }
            created.tryEmit(count)
        } finally {
            isCreating.value = false
        }
    }
}
