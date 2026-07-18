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
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent

/** Relay-signed group directory kinds: metadata + admins + members + roles + pins. */
private val RELAY_GROUP_METADATA_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        SupportedRolesEvent.KIND,
        GroupPinnedEvent.KIND,
    )

/**
 * The relay-signed metadata for a NIP-29 group (name/picture/about + admin,
 * member and role lists), addressed by the group id (`d` tag) and pinned to the
 * group's host relay. The relay signs these with its own key, so a single-relay
 * query scoped by `#d` returns exactly this group's directory.
 */
fun filterRelayGroupState(
    channel: RelayGroupChannel,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays = channel.relays().toSet()
    val directory =
        relays.map {
            RelayBasedFilter(
                relay = it,
                filter =
                    Filter(
                        kinds = RELAY_GROUP_METADATA_KINDS,
                        tags = mapOf("d" to listOf(channel.groupId.id)),
                        since = since?.get(it)?.time,
                    ),
            )
        }

    // Back-fill the bodies of pinned messages by id from the host relay. A pin can point at a
    // message older than the 200-item timeline window, which the `h`-scoped timeline filter would
    // never return — without this the pin bar shows a blank preview and can't jump to it. No `since`:
    // pinned events are immutable, so we want them regardless of age.
    val pinnedIds = channel.pinnedEventIds
    val pins =
        if (pinnedIds.isEmpty()) {
            emptyList()
        } else {
            relays.map { RelayBasedFilter(relay = it, filter = Filter(ids = pinnedIds)) }
        }

    return directory + pins
}
