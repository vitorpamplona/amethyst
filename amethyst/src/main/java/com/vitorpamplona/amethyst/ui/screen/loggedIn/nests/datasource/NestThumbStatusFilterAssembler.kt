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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

/**
 * Liveness probe for an audio-room thumbnail. The kind-30312 status
 * tag goes stale silently when a host crashes without publishing a
 * CLOSED replacement, so the room-card alone can't tell whether the
 * audio is still flowing. NIP-53 speakers re-emit kind-10312
 * presence (replaceable per `(kind, pubkey, fixed-d-tag)`) every
 * ~60 s while publishing, so the freshest 10312 with `#a=room`
 * doubles as a heartbeat: a `createdAt` within the last few minutes
 * means at least one speaker is still in the room right now.
 *
 * One REQ per thumb keyed by the room's a-tag, `limit=1` so the
 * relay returns just the single most-recent presence among all
 * speakers in that room. The REQ stays open after EOSE, so fresher
 * heartbeats stream in as they arrive and bump the thumb back to
 * live without re-subscribing.
 */
@Stable
class NestThumbStatusQueryState(
    val roomATag: String,
    val account: Account,
)

class NestThumbStatusFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NestThumbStatusQueryState>,
) : PerUniqueIdEoseManager<NestThumbStatusQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: NestThumbStatusQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(MeetingRoomPresenceEvent.KIND),
                        tags = mapOf("a" to listOf(key.roomATag)),
                        limit = 1,
                    ),
            )
        }
    }

    override fun id(key: NestThumbStatusQueryState): String = key.roomATag
}

@Stable
class NestThumbStatusFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NestThumbStatusQueryState>() {
    private val sub = NestThumbStatusFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}

@Composable
fun NestThumbStatusFilterAssemblerSubscription(
    roomATag: String,
    accountViewModel: AccountViewModel,
) {
    val state =
        remember(roomATag, accountViewModel.account.pubKey) {
            NestThumbStatusQueryState(roomATag, accountViewModel.account)
        }
    KeyDataSourceSubscription(state, accountViewModel.dataSources().nestThumbStatus)
}

/**
 * Returns the unix-seconds `createdAt` of the most recent kind-10312
 * presence cached for [address], or `null` until one arrives. Opens
 * the limit:1 thumb-status REQ as a side effect; the REQ keeps
 * streaming after EOSE so newer heartbeats reactively bump the
 * returned state.
 *
 * Reads version notes off [LocalCache.getOrCreateLiveChannel]: the
 * cache attaches every kind-10312 with `#a=room` to that channel
 * (see `LocalCache.consume(MeetingRoomPresenceEvent, …)`), so
 * scanning `channel.notes` for `MeetingRoomPresenceEvent` and taking
 * `max(createdAt)` lands on the freshest heartbeat from any speaker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeNestRoomLatestPresence(
    address: Address,
    accountViewModel: AccountViewModel,
): State<Long?> {
    NestThumbStatusFilterAssemblerSubscription(address.toValue(), accountViewModel)

    val channel = remember(address) { LocalCache.getOrCreateLiveChannel(address) }
    val flow =
        remember(channel) {
            channel
                .flow()
                .notes.stateFlow
                .mapLatest { state ->
                    var max: Long? = null
                    state.channel.notes.forEach { _, note ->
                        val event = note.event
                        if (event is MeetingRoomPresenceEvent) {
                            val createdAt = event.createdAt
                            if (max == null || createdAt > max!!) max = createdAt
                        }
                    }
                    max
                }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }
    return flow.collectAsStateWithLifecycle(null)
}
