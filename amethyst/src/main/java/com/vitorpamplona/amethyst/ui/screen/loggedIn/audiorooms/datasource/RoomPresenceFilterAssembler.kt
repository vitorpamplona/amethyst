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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.datasource

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent

/**
 * Per-screen state for the room-presence subscription. The room
 * `a`-tag is the address-pointer string (e.g.
 * `30312:<host-pubkey>:<roomId>`); the relay subscription returns
 * every kind-10312 event tagged with that `#a`.
 */
@Stable
class RoomPresenceQueryState(
    val roomATag: String,
    val account: Account,
)

/**
 * REQs `kinds=[10312], #a=[roomATag]` against every relay the user
 * already talks to. The events arrive in [com.vitorpamplona.amethyst.model.LocalCache]
 * via the normal client pipeline; the room screen observes them
 * with `LocalCache.observeEvents(...)` and feeds each event into
 * `AudioRoomViewModel.onPresenceEvent`.
 *
 * One assembler instance services every audio-room screen — the
 * `key` (= room a-tag) keeps overlapping screens (in unlikely
 * scenarios such as PiP + opened-from-deep-link) cleanly separated.
 */
class RoomPresenceFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RoomPresenceQueryState>,
) : PerUniqueIdEoseManager<RoomPresenceQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: RoomPresenceQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // Use the user's outbox relay set; that's the same fan-out the
        // global rooms-list subscription rides on, so the presence
        // events arrive on the same connections as the room itself.
        val relays = key.account.outboxRelays.flow.value
        return relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(MeetingRoomPresenceEvent.KIND),
                        tags = mapOf("a" to listOf(key.roomATag)),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    override fun id(key: RoomPresenceQueryState): String = key.roomATag
}

@Stable
class RoomPresenceFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<RoomPresenceQueryState>() {
    private val sub = RoomPresenceFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}
