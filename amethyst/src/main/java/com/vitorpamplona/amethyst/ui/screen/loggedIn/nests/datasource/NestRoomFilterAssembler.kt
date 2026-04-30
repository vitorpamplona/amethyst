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
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUniqueIdEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent

/**
 * Per-room state for every wire subscription scoped to a single
 * Nest. Combines what used to be four parallel `*QueryState`s
 * (chat, presence, reactions, admin commands) into one — the four
 * relay REQs collapsed into a single subscription with two
 * [Filter]s per relay (see [NestRoomFilterSubAssembler.updateFilter]).
 */
@Stable
class NestRoomQueryState(
    val note: AddressableNote,
    val account: Account,
)

/**
 * Single per-room sub-assembler. Issues two [Filter]s per outbox
 * relay on every key update:
 *
 *   1. `kinds=[1311, 10312, 7]`, `#a=[roomATag]` — the chat, presence
 *      and reaction streams the room consumes. Bundling them into
 *      one Filter is safe: a single Filter requires (kind ∈ kinds)
 *      AND (every tag dimension matches), and these three streams
 *      share the same `#a` gate.
 *
 *   2. `kinds=[4312]`, `#a=[roomATag]`, `#p=[localPubkey]` — admin
 *      commands. Stays separate from (1) because the additional
 *      `#p` constraint must NOT apply to the chat/presence/reaction
 *      streams (they don't tag the local user). A second Filter on
 *      the same REQ keeps them as one wire subscription.
 *
 * Replaces the previous fan-out of four sibling assemblers — chat,
 * presence, reactions, admin commands — that each opened their own
 * REQ. Net wire change: 4 REQs per relay → 1 REQ per relay with 2
 * Filters, same event coverage.
 */
class NestRoomFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NestRoomQueryState>,
) : PerUniqueIdEoseManager<NestRoomQueryState, String>(client, allKeys) {
    override fun updateFilter(
        key: NestRoomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val relays = key.account.outboxRelays.flow.value + key.note.relays + ((key.note.event as? MeetingSpaceEvent)?.relays()?.toSet() ?: emptySet())

        return relays.flatMap { relay ->
            listOf(
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds =
                                listOf(
                                    LiveActivitiesChatMessageEvent.KIND,
                                    MeetingRoomPresenceEvent.KIND,
                                    ReactionEvent.KIND,
                                ),
                            tags = mapOf("a" to listOf(key.note.idHex)),
                            since = since?.get(relay)?.time,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds = listOf(AdminCommandEvent.KIND),
                            tags = mapOf("a" to listOf(key.note.idHex), "p" to listOf(key.account.pubKey)),
                            since = since?.get(relay)?.time,
                        ),
                ),
            )
        }
    }

    override fun id(key: NestRoomQueryState): String = key.note.idHex
}

@Stable
class NestRoomFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<NestRoomQueryState>() {
    private val sub = NestRoomFilterSubAssembler(client, ::allKeys)

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = sub.invalidateFilters()

    override fun destroy() = sub.destroy()
}

/**
 * Lifecycle-aware subscription. Stays open while the room screen is
 * in the tree; closes on dispose. One call replaces the previous
 * four (`RoomChatFilterAssemblerSubscription`,
 * `RoomPresenceFilterAssemblerSubscription`,
 * `RoomReactionsFilterAssemblerSubscription`,
 * `RoomAdminCommandsFilterAssemblerSubscription`).
 */
@Composable
fun NestRoomFilterAssemblerSubscription(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
) = NestRoomFilterAssemblerSubscription(
    note,
    accountViewModel.account,
    accountViewModel.dataSources().nestRoom,
)

@Composable
fun NestRoomFilterAssemblerSubscription(
    note: AddressableNote,
    account: Account,
    filterAssembler: NestRoomFilterAssembler,
) {
    val state =
        remember(note, account.pubKey) {
            NestRoomQueryState(note, account)
        }
    KeyDataSourceSubscription(state, filterAssembler)
}
