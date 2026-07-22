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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovers the workspace channels the user is a member of on each joined Buzz relay, so the
 * Workspaces hub shows them even though Buzz membership is server-side (no NIP-29 join event).
 *
 * The relay addresses each member a kind-44100 member-added notification (`#p` = me, `h` =
 * channel), so this fetches + live-subscribes 44100 across the joined relays ([BuzzWorkspaces]),
 * then fetches each channel's kind-39000 metadata to read its Buzz `t` channel type. Channels
 * whose type is **not** `dm` are workspace channels (DMs surface in the DM inbox instead). Rows
 * are grouped by relay = workspace; the screen unions this with the NIP-29 joined-group list so
 * both membership models render.
 */
class BuzzWorkspacesViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private val refreshMutex = Mutex()
    private var liveJob: Job? = null

    /** channelId -> relay it was discovered on (from the 44100 provenance). */
    private val memberChannels = ConcurrentHashMap<String, NormalizedRelayUrl>()

    private val _channelsByRelay = MutableStateFlow<Map<NormalizedRelayUrl, List<GroupId>>>(emptyMap())

    /** Non-DM member channels grouped by workspace relay; the hub collects this. */
    val channelsByRelay: StateFlow<Map<NormalizedRelayUrl, List<GroupId>>> = _channelsByRelay.asStateFlow()

    private fun relays(): Set<NormalizedRelayUrl> = BuzzWorkspaces.flow.value + BuzzRelayDialect.flow.value

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
                discoverMemberChannels(account)
                fetchMetadata(account)
                rebuild()
            }
        }
    }

    /** Fetch kind-44100 (`#p` = me) across the joined relays, recording each channel's relay. */
    private suspend fun discoverMemberChannels(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = relays()
        if (relays.isEmpty()) return
        val filter = Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))
        account.client.fetchAllPagesFromPool(relays.associateWith { listOf(filter) }) { event, relay ->
            (event as? MemberAddedNotificationEvent)?.channel()?.let { memberChannels[it] = relay }
        }
    }

    /** Fetch the NIP-29 directory (39000-39003) of every discovered channel so its `t` type loads. */
    private suspend fun fetchMetadata(account: Account) {
        val byRelay =
            memberChannels.entries
                .groupBy({ it.value }, { it.key })
                .mapValues { (_, ids) -> listOf(Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to ids))) }
        if (byRelay.isEmpty()) return
        account.client.fetchAllPagesFromPool(byRelay) { _, _ -> }
    }

    /** Project the discovered non-DM channels, grouped by relay. */
    private fun rebuild() {
        _channelsByRelay.value =
            memberChannels.entries
                .mapNotNull { (channelId, relay) ->
                    val groupId = GroupId(channelId, relay)
                    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                    // Keep only non-DM channels here; DMs render in the DM inbox. A channel whose
                    // metadata hasn't arrived yet (type unknown) is optimistically shown as a
                    // workspace channel — a later refresh moves it out once a `t:dm` is seen.
                    if (channel.event?.isBuzzDm() == true) null else relay to groupId
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, ids) -> ids.sortedBy { it.id } }
    }

    /** Keep a live 44100 subscription open (so new channels appear) and re-project on registry moves. */
    private fun startLive() {
        val account = account ?: return
        if (liveJob != null) return
        val myPubkey = account.userProfile().pubkeyHex

        liveJob =
            viewModelScope.launch(Dispatchers.IO) {
                relays().forEach { relay ->
                    launch {
                        val filter = Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))
                        account.client.subscribeAsFlow(relay, filter).collect { events ->
                            var changed = false
                            events.filterIsInstance<MemberAddedNotificationEvent>().forEach { e ->
                                e.channel()?.let { if (memberChannels.put(it, relay) == null) changed = true }
                            }
                            if (changed) {
                                fetchMetadata(account)
                                rebuild()
                            }
                        }
                    }
                }
                // Re-project when the joined-workspace set or dialect marks change.
                launch {
                    combine(BuzzWorkspaces.flow, BuzzRelayDialect.flow) { _, _ -> }.collect { refresh() }
                }
            }
    }

    override fun onCleared() {
        liveJob?.cancel()
        liveJob = null
        super.onCleared()
    }
}
