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
package com.vitorpamplona.quartz.nip29RelayGroups.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip29RelayGroups.groupPinLinks
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.groupPins
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.pinnedAddresses
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.pinnedEventIds
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-29 relay-signed list of a group's pinned messages (kind 39005). The relay
 * regenerates it from the accepted [com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent]
 * (kind 9010) moderation actions, so this is the read side clients render — the
 * source of truth for which messages are pinned and in what display order.
 *
 * Addressed by the group id (`d` tag). The pins are carried as `e` tags (regular
 * events, by id) and `a` tags (addressable events, by `kind:pubkey:d`), interleaved in
 * display order.
 */
@Immutable
class GroupPinnedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    /** NIP-29 pinned events; the group this list belongs to is its own `d`, which restates its ADDRESS: not linked. */
    override fun links(): List<Link<*>> = links { groupPinLinks(pins()) }

    fun groupId() = dTag()

    /** The full ordered pin list — `e` and `a` references — in the relay's display order. */
    fun pins(): List<GroupPin> = tags.groupPins()

    /** Only the `e`-tagged pinned event ids, in order. Prefer [pins], which also carries `a` pins. */
    fun pinnedEventIds(): List<HexKey> = tags.pinnedEventIds()

    /** Only the `a`-tagged pinned addresses, in order. */
    fun pinnedAddresses(): List<Address> = tags.pinnedAddresses()

    companion object {
        const val KIND = 39005

        fun build(
            groupId: String,
            pins: List<GroupPin>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GroupPinnedEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(groupId)
            groupPins(pins)
            initializer()
        }
    }
}
