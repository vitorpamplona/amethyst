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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.subassemblies

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/** Timeline kinds shown in a NIP-29 group: chat messages and polls. */
private val RELAY_GROUP_TIMELINE_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/**
 * The message timeline for a NIP-29 group. A group's chat lives entirely on its
 * host relay, scoped by the `h` tag, so the filter is pinned to
 * [RelayGroupChannel.relays] (always the single host) and never fans out to the
 * user's other relays.
 */
fun filterMessagesToRelayGroup(
    channel: RelayGroupChannel,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> =
    channel.relays().toSet().map {
        RelayBasedFilter(
            relay = it,
            filter =
                Filter(
                    kinds = RELAY_GROUP_TIMELINE_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to listOf(channel.groupId.id)),
                    limit = 200,
                    since = since?.get(it)?.time,
                ),
        )
    }
