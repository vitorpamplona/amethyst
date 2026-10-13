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
package com.vitorpamplona.amethyst.commons.relayClient.dlists

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.quartz.experimental.decentralizedLists.DecentralizedListEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.STANDARD_ITEM_KINDS
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.acceptedItemKinds
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFrom
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/** A D-list header on screen whose items should be loaded. */
class DListItemsQueryState(
    val header: Note,
)

/**
 * What an item writes in its `z` tag to be filed under [header]: the header's own pointer (its
 * id for a 9998, its coordinate for a 39998) and, for a copy, whatever it points at with `b`,
 * since a copy's list is read as the list it copies (Brainstorm `services/dictionary`).
 */
fun dListItemPointers(header: Event): List<String> {
    val own = (header as? DecentralizedListEvent)?.listPointer() ?: return emptyList()
    return (listOf(own) + header.tags.inheritFrom().map { it.target }).distinct()
}

/** The kinds this header's items are published as: its `item-kind`s, else 9999 and 39999. */
fun dListItemKinds(header: Event?): List<Int> = header?.tags?.acceptedItemKinds() ?: STANDARD_ITEM_KINDS

/**
 * Loads the items filed under the D-list headers on screen, from the relays each header was
 * seen on. A header card shows how many items it holds and a few of them; the full list is the
 * relay's to page, not this card's.
 */
class DListItemsFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<DListItemsQueryState>() {
    val group = listOf(DListItemsSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
