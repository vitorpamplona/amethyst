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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator
import com.vitorpamplona.amethyst.commons.model.buzz.JobView
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backing ViewModel for the [JobBoardScreen] — the shared backlog of one Buzz channel.
 *
 * A Buzz "job" is the agent-job protocol (kinds 43001-43006): a member files a request, the
 * workspace bot works it, and every step lands as a signed event the whole room sees. This VM
 * fetches those events (plus their kind-7 upvotes) scoped to the channel `h`, folds them into
 * per-job [JobView] records via the shared [BuzzJobAggregator], and exposes the backlog as a
 * [StateFlow]. It also drives the three write actions the board offers: file, upvote, cancel.
 *
 * The heavy lifting (correlation, state machine, upvote priority) lives in `commons`; this VM is
 * the Android glue (fetch → LocalCache → re-derive → publish via [Account]).
 */
class JobBoardViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null
    private var channelId: String? = null

    private val _jobs = MutableStateFlow<List<JobView>>(emptyList())
    val jobs: StateFlow<List<JobView>> = _jobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var watchJob: Job? = null
    private val reloadMutex = Mutex()

    fun bind(
        account: Account,
        channelId: String,
        relayUrl: String,
    ) {
        if (this.account != null) return
        this.account = account
        this.channelId = channelId
        this.relay = RelayUrlNormalizer.normalizeOrNull(relayUrl)
        refresh()
    }

    fun refresh() {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                account.client.fetchAllWithHooks(
                    filters = mapOf(relay to boardFilters(channelId)),
                    timeoutMs = 8_000,
                    pendingOnAuthRequired = true,
                ) { _, _ -> false }
                reloadFromCache(channelId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Keep the board live while it's on screen: any batch of job/reaction events re-derives it. */
    fun startWatching() {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        if (watchJob != null) return
        watchJob =
            viewModelScope.launch(Dispatchers.IO) {
                account.client.subscribeAsFlow(relay, boardFilters(channelId)).collect {
                    // The client's global listener already consumed the batch into LocalCache.
                    reloadFromCache(channelId)
                }
            }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun reloadFromCache(channelId: String) =
        reloadMutex.withLock {
            val events =
                LocalCache
                    .filter(Filter(kinds = ALL_KINDS, tags = mapOf("h" to listOf(channelId))))
                    .mapNotNull { it.event }
            _jobs.value = BuzzJobAggregator.aggregate(events)
        }

    fun file(request: String) =
        act { account, relay, channelId ->
            account.fileBuzzJob(relay, channelId, request)
        }

    fun upvote(
        jobId: String,
        jobAuthor: String?,
    ) = act { account, relay, channelId ->
        account.upvoteBuzzJob(relay, channelId, jobId, jobAuthor)
    }

    fun cancel(jobId: String) =
        act { account, relay, channelId ->
            account.cancelBuzzJob(relay, channelId, jobId)
        }

    private inline fun act(crossinline block: suspend (Account, NormalizedRelayUrl, String) -> Unit) {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            block(account, relay, channelId)
            reloadFromCache(channelId) // optimistic local re-derive; the live watch catches relay echoes
        }
    }

    override fun onCleared() {
        stopWatching()
        super.onCleared()
    }

    companion object {
        // The Buzz agent-job protocol (request/accepted/progress/result/cancel/error) plus the
        // kind-7 upvotes that prioritize it.
        private val JOB_KINDS = (43001..43006).toList()
        private val ALL_KINDS = JOB_KINDS + ReactionEvent.KIND

        private fun boardFilters(channelId: String) =
            listOf(
                Filter(kinds = JOB_KINDS, tags = mapOf("h" to listOf(channelId))),
                Filter(kinds = listOf(ReactionEvent.KIND), tags = mapOf("h" to listOf(channelId))),
            )
    }
}
