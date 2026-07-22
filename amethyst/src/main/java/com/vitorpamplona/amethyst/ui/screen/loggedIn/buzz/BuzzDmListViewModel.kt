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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Backing ViewModel for [BuzzDmListScreen] — the user's Buzz direct-message inbox.
 *
 * A Buzz DM is a relay-authoritative NIP-29 group whose `h`/id is a relay-generated UUID,
 * so the message timeline reuses the whole relay-group chat stack; this ViewModel only
 * owns *discovery* and the inbox projection. It:
 * - fetches + live-subscribes the relay-signed [DmCreatedEvent] (`kind:41001`, `#p` = me)
 *   and per-viewer [DmVisibilityEvent] (`kind:30622`, `#p` = me) across the Buzz-dialect
 *   relays — [LocalCache] consumes them and feeds [BuzzDmRegistry];
 * - fetches each discovered DM's NIP-29 directory (39000-39003, `#d` = channel id) so the
 *   relay-signed roster is present, which is what the shared chat composer gates on
 *   (a DM isn't in the joined-group list, so nothing else would fetch it);
 * - projects [BuzzDmRegistry] (minus the viewer's hidden set) into [rows], sorted by last
 *   message time and enriched with the other participants for name/avatar rendering.
 */
class BuzzDmListViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private val refreshMutex = Mutex()
    private var liveJob: Job? = null

    private val _rows = MutableStateFlow<List<DmRow>>(emptyList())
    val rows: StateFlow<List<DmRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** One inbox row: a materialized DM plus what the UI needs to render it. */
    @Immutable
    data class DmRow(
        val channelId: String,
        val relayUrl: NormalizedRelayUrl,
        /** All participants (from the 41001), including me. */
        val allParticipants: List<HexKey>,
        /** Participants other than me — who the DM is "with". */
        val others: List<HexKey>,
        /** Newest message time (or the DM's created_at when it has no messages yet). */
        val lastActivity: Long,
    )

    fun bindAccountIfMissing(account: Account) {
        if (this.account != null) return
        this.account = account
        refresh()
        startLive()
    }

    fun refresh() {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                _isLoading.value = true
                try {
                    fetchDiscovery(account)
                    fetchRosters(account)
                    rebuildRows(account)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * One-shot paged fetch of the DM confirmations + visibility snapshots addressed to me
     * (`#p` = me) from every Buzz-dialect relay. Events land in [LocalCache] → [BuzzDmRegistry].
     */
    private suspend fun fetchDiscovery(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = BuzzRelayDialect.flow.value
        if (relays.isEmpty()) return
        val filters =
            listOf(
                Filter(kinds = listOf(DmCreatedEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
            )
        account.client.fetchAllPagesFromPool(relays.associateWith { filters }) { _, _ -> }
    }

    /**
     * Second phase: for the DMs just discovered, fetch each channel's NIP-29 directory
     * (39000-39003) from its own relay so the relay-signed roster populates. Without it the
     * shared chat composer's member gate would hide the input field on a DM.
     */
    private suspend fun fetchRosters(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val byRelay =
            BuzzDmRegistry
                .visibleFor(myPubkey)
                .groupBy { it.relay }
                .mapValues { (_, dms) ->
                    listOf(
                        Filter(
                            kinds = RELAY_GROUP_METADATA_KINDS,
                            tags = mapOf("d" to dms.map { it.channelId }),
                        ),
                    )
                }
        if (byRelay.isEmpty()) return
        account.client.fetchAllPagesFromPool(byRelay) { _, _ -> }
    }

    private fun rebuildRows(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        _rows.value =
            BuzzDmRegistry
                .visibleFor(myPubkey)
                .map { dm ->
                    DmRow(
                        channelId = dm.channelId,
                        relayUrl = dm.relay,
                        allParticipants = dm.participants,
                        others = dm.participants.filter { it != myPubkey },
                        lastActivity = lastActivityFor(dm.channelId, dm.createdAt),
                    )
                }.sortedByDescending { it.lastActivity }
    }

    /** Newest message `created_at` for [channelId] from [LocalCache], or [fallback] when empty. */
    private fun lastActivityFor(
        channelId: String,
        fallback: Long,
    ): Long =
        LocalCache
            .filter(
                Filter(
                    kinds = listOf(ChatEvent.KIND, StreamMessageV2Event.KIND),
                    tags = mapOf("h" to listOf(channelId)),
                ),
            ).maxOfOrNull { it.createdAt() ?: 0L }
            ?.takeIf { it > 0L }
            ?: fallback

    /**
     * Keeps a live REQ open for new DM confirmations / visibility changes and re-projects
     * the inbox whenever the registry moves. Idempotent; torn down with the ViewModel.
     */
    private fun startLive() {
        val account = account ?: return
        if (liveJob != null) return
        val myPubkey = account.userProfile().pubkeyHex
        val relays = BuzzRelayDialect.flow.value

        liveJob =
            viewModelScope.launch(Dispatchers.IO) {
                // (a) Keep the discovery REQs open so LocalCache keeps feeding the registry.
                relays.forEach { relay ->
                    launch {
                        account.client
                            .subscribeAsFlow(
                                relay,
                                Filter(kinds = listOf(DmCreatedEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                            ).collect { /* consumed globally by CacheClientConnector */ }
                    }
                    launch {
                        account.client
                            .subscribeAsFlow(
                                relay,
                                Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                            ).collect { }
                    }
                }
                // (b) Re-project whenever the registry (conversations or my hidden set) changes.
                launch {
                    combine(BuzzDmRegistry.conversations, BuzzDmRegistry.hidden) { _, _ -> }
                        .collect { rebuildRows(account) }
                }
            }
    }

    override fun onCleared() {
        liveJob?.cancel()
        liveJob = null
        super.onCleared()
    }
}
