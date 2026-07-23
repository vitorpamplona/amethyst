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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Backing ViewModel for [BuzzDmListScreen] — the user's Buzz direct-message inbox.
 *
 * A Buzz DM is a relay-authoritative NIP-29 group whose `h`/id is a relay-generated UUID, so the
 * message timeline reuses the whole relay-group chat stack; this ViewModel owns only *discovery*
 * and the inbox projection. Discovery mirrors how the deployed relay actually models DMs (it does
 * NOT emit a queryable kind-41001): the relay addresses each member a kind-44100 member-added
 * notification (`#p` = me, `h` = channel), and marks a channel a DM via the `t` tag on its
 * kind-39000 metadata (with the participants inlined as `p` tags). So it:
 * - fetches + live-subscribes 44100 (`#p` = me) across the joined Buzz relays to learn the
 *   channels the user is in;
 * - fetches each channel's directory (39000-39003) and keeps the ones whose metadata says
 *   `t` = `dm` — that same 39000 also carries the roster the shared chat composer's member gate
 *   needs, and the DM participants;
 * - subscribes the per-viewer [DmVisibilityEvent] (`kind:30622`) so a hidden DM (tracked in
 *   [BuzzDmRegistry]) drops out;
 * - projects the visible DMs into [rows], sorted by last message time.
 */
class BuzzDmListViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    /** The community (relay) this inbox is scoped to — DMs are per-community. */
    private var scopeRelay: NormalizedRelayUrl? = null
    private val refreshMutex = Mutex()
    private var liveJob: Job? = null

    /** channelId -> relay it was discovered on (from the 44100 provenance). */
    private val memberChannels = ConcurrentHashMap<String, NormalizedRelayUrl>()

    private val _rows = MutableStateFlow<List<DmRow>>(emptyList())
    val rows: StateFlow<List<DmRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** One inbox row: a materialized DM plus what the UI needs to render it. */
    @Immutable
    data class DmRow(
        val channelId: String,
        val relayUrl: NormalizedRelayUrl,
        /** All participants (from the 39000 metadata `p` tags), including me. */
        val allParticipants: List<HexKey>,
        /** Participants other than me — who the DM is "with". */
        val others: List<HexKey>,
        /** Newest message time (or 0 when the DM has no messages yet). */
        val lastActivity: Long,
    )

    private fun relays(): Set<NormalizedRelayUrl> = scopeRelay?.let { setOf(it) } ?: (BuzzWorkspaces.flow.value + BuzzRelayDialect.flow.value)

    /**
     * Binds to [account] scoped to the community [relayUrl]. Marks that relay a joined workspace and
     * pre-approves NIP-42 so the read-only `#p=me` DM discovery authenticates against it.
     */
    fun bind(
        account: Account,
        relayUrl: String,
    ) {
        if (this.account != null) return
        this.account = account
        // A malformed relay URL must not silently fall back to querying every joined workspace
        // (relays() does that when scopeRelay is null) — this screen is scoped to one community.
        val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return
        this.scopeRelay = relay

        val newlyJoined = BuzzWorkspaces.join(relay)
        viewModelScope.launch { account.relayAuthLedger.setDecision(relay.url, RelayAuthDecision.ALLOW) }
        // A join makes the relay first-party; if the socket was already open its one-shot AUTH
        // challenge was spent unauthenticated, so reconnect to re-challenge and authenticate.
        if (newlyJoined) account.client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true)

        refresh()
        startLive()
    }

    fun refresh() {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                _isLoading.value = true
                try {
                    discoverMemberChannels(account)
                    fetchMetadata(account)
                    rebuildRows(account)
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Fetch kind-44100 (`#p` = me) + the visibility snapshot (30622) across the joined relays. These
     * reads are `#p`-gated so the Buzz relay requires NIP-42 auth — use the warm-auth fetch
     * (`pendingOnAuthRequired`) so it authenticates on the `auth-required` CLOSED and retries, rather
     * than returning empty (this is why the import lists channels but a plain fetch wouldn't).
     */
    private suspend fun discoverMemberChannels(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val relays = relays()
        if (relays.isEmpty()) return
        val filters =
            listOf(
                Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
                Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(myPubkey))),
            )
        account.client.fetchAllWithHooks(
            filters = relays.associateWith { filters },
            timeoutMs = 8_000,
            pendingOnAuthRequired = true,
        ) { relay, event ->
            (event as? MemberAddedNotificationEvent)?.channel()?.let { memberChannels[it] = relay }
            false
        }
    }

    /** Fetch the NIP-29 directory (39000-39003) of every discovered channel so its `t`/roster load. */
    private suspend fun fetchMetadata(account: Account) {
        val byRelay =
            memberChannels.entries
                .groupBy({ it.value }, { it.key })
                .mapValues { (_, ids) -> listOf(Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to ids))) }
        if (byRelay.isEmpty()) return
        account.client.fetchAllWithHooks(filters = byRelay, timeoutMs = 8_000, pendingOnAuthRequired = true) { _, _ -> false }
    }

    /** Project the discovered DM channels (metadata `t` = `dm`), minus my hidden set, newest-first. */
    private fun rebuildRows(account: Account) {
        val myPubkey = account.userProfile().pubkeyHex
        val hidden = BuzzDmRegistry.hiddenFor(myPubkey)
        _rows.value =
            memberChannels.entries
                .mapNotNull { (channelId, relay) ->
                    if (channelId in hidden) return@mapNotNull null
                    val channel = LocalCache.getOrCreateRelayGroupChannel(GroupId(channelId, relay))
                    val metadata = channel.event ?: return@mapNotNull null
                    if (!metadata.isBuzzDm()) return@mapNotNull null
                    val participants = metadata.buzzParticipants()
                    DmRow(
                        channelId = channelId,
                        relayUrl = relay,
                        allParticipants = participants,
                        others = participants.filter { it != myPubkey },
                        lastActivity = lastActivityFor(channelId),
                    )
                }.sortedByDescending { it.lastActivity }
    }

    /** Newest message `created_at` for [channelId] from [LocalCache], or 0 when the DM is empty. */
    private fun lastActivityFor(channelId: String): Long =
        LocalCache
            .filter(
                Filter(
                    kinds = listOf(ChatEvent.KIND, StreamMessageV2Event.KIND),
                    tags = mapOf("h" to listOf(channelId)),
                ),
            ).maxOfOrNull { it.createdAt() ?: 0L } ?: 0L

    /**
     * Keeps a live 44100 + 30622 REQ open (so new DMs / hide changes arrive) and re-projects the
     * inbox when the registry or dialect set moves. Idempotent; torn down with the ViewModel.
     */
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
                                rebuildRows(account)
                            }
                        }
                    }
                    launch {
                        val filter = Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))
                        account.client.subscribeAsFlow(relay, filter).collect { /* consumed → BuzzDmRegistry.hidden */ }
                    }
                }
                // Re-project when my hidden set (30622) or the joined-relay set changes.
                launch {
                    combine(BuzzDmRegistry.hidden, BuzzWorkspaces.flow, BuzzRelayDialect.flow) { _, _, _ -> }
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
