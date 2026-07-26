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
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRun
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backing ViewModel for the [WorkflowRunBoardScreen] — the shared workflow runs of one Buzz channel.
 *
 * A Buzz "workflow" is the source-confirmed structured-work primitive (30620 def, 46020 trigger,
 * 46001-46007 lifecycle, 46010 approval gate, 46030/46031 grant/deny): a member triggers a run, the
 * runner does the work and pauses on a **human-approval gate**, and only ships after someone grants.
 * This VM folds those events into per-run [WorkflowRun] records via the shared [WorkflowRunAggregator]
 * and exposes them, awaiting-approval first, as a [StateFlow]; it drives trigger + approve + deny.
 *
 * Two fetch realities (both mirrored from `amy buzz workflow`): the trigger + lifecycle + gate are
 * `#h`-scoped to the channel, but the grant/deny decisions carry only a `d` tag (= the run id = the
 * token), which quartz's store can't serve via `#d` on a regular kind — so decisions are fetched **by
 * author**, and every 46010 gate names its approver in a `p` tag, giving us exactly those authors.
 */
class WorkflowRunBoardViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null
    private var channelId: String? = null

    private val _runs = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val runs: StateFlow<List<WorkflowRun>> = _runs.asStateFlow()

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
                // Phase 1: the `#h`-scoped trigger + lifecycle + approval gate.
                account.client.fetchAllWithHooks(
                    filters = mapOf(relay to listOf(Filter(kinds = WORKFLOW_H_KINDS, tags = mapOf("h" to listOf(channelId))))),
                    timeoutMs = 8_000,
                    pendingOnAuthRequired = true,
                ) { _, _ -> false }
                // Phase 2: each gate's approver can sign a decision — fetch those by author.
                val approvers = approversInCache(channelId)
                if (approvers.isNotEmpty()) {
                    account.client.fetchAllWithHooks(
                        filters = mapOf(relay to listOf(Filter(kinds = DECISION_KINDS, authors = approvers))),
                        timeoutMs = 8_000,
                        pendingOnAuthRequired = true,
                    ) { _, _ -> false }
                }
                reloadFromCache(channelId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Keep the board live while on screen: any `#h` lifecycle batch (incl. 46005 completion) re-derives it. */
    fun startWatching() {
        val account = account ?: return
        val relay = relay ?: return
        val channelId = channelId ?: return
        if (watchJob != null) return
        watchJob =
            viewModelScope.launch(Dispatchers.IO) {
                account.client
                    .subscribeAsFlow(relay, listOf(Filter(kinds = WORKFLOW_H_KINDS, tags = mapOf("h" to listOf(channelId)))))
                    .collect {
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
            val base =
                LocalCache
                    .filter(Filter(kinds = WORKFLOW_H_KINDS, tags = mapOf("h" to listOf(channelId))))
                    .mapNotNull { it.event }
            val approvers =
                base
                    .filterIsInstance<WorkflowApprovalRequestedEvent>()
                    .mapNotNull { it.approver() }
                    .distinct()
            val decisions =
                if (approvers.isEmpty()) {
                    emptyList()
                } else {
                    LocalCache.filter(Filter(kinds = DECISION_KINDS, authors = approvers)).mapNotNull { it.event }
                }
            _runs.value = WorkflowRunAggregator.byPriority(WorkflowRunAggregator.aggregate(base + decisions))
        }

    /** The approver pubkeys named by 46010 gates in this channel — who can sign a 46030/46031. */
    private fun approversInCache(channelId: String): List<HexKey> =
        LocalCache
            .filter(Filter(kinds = listOf(WorkflowApprovalRequestedEvent.KIND), tags = mapOf("h" to listOf(channelId))))
            .mapNotNull { it.event }
            .filterIsInstance<WorkflowApprovalRequestedEvent>()
            .mapNotNull { it.approver() }
            .distinct()

    fun trigger(
        workflowId: String,
        task: String,
    ) = act { account, relay, channelId ->
        account.triggerBuzzWorkflow(relay, channelId, workflowId, task)
    }

    fun approve(runId: HexKey) =
        act { account, relay, _ ->
            account.approveBuzzWorkflowRun(relay, runId)
        }

    fun deny(runId: HexKey) =
        act { account, relay, _ ->
            account.denyBuzzWorkflowRun(relay, runId)
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
        // The `#h`-scoped workflow kinds: trigger (46020), run/step lifecycle (46001-46007 minus the
        // deny/grant commands), and the relay-signed approval mirror (46010-46012). The client-signed
        // grant/deny commands (46030/46031) carry no `h` tag — fetched by author instead.
        private val WORKFLOW_H_KINDS =
            listOf(
                WorkflowTriggerEvent.KIND,
                WorkflowTriggeredEvent.KIND,
                WorkflowStepStartedEvent.KIND,
                WorkflowStepCompletedEvent.KIND,
                WorkflowStepFailedEvent.KIND,
                WorkflowCompletedEvent.KIND,
                WorkflowFailedEvent.KIND,
                WorkflowCancelledEvent.KIND,
                WorkflowApprovalRequestedEvent.KIND,
                WorkflowApprovalGrantedEvent.KIND,
                WorkflowApprovalDeniedEvent.KIND,
            )
        private val DECISION_KINDS = listOf(ApprovalGrantEvent.KIND, ApprovalDenyEvent.KIND)
    }
}
