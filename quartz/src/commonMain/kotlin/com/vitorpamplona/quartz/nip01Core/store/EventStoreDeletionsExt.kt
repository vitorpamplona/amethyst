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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent

/** The addressable/replaceable coordinate of [event] as a NIP-01 `a`-tag value. */
private fun addressValue(event: Event): String {
    val dTag = if (event.kind.isAddressable()) event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: "" else ""
    return Address.assemble(event.kind, event.pubKey, dTag)
}

/**
 * The local deletion events that would make [relay] remove one of [serverEvents] — the
 * events the relay HAS that we LACK. Used by sync to push *only* the deletions that
 * actually apply to what the relay holds, and nothing else (not other deletions by the
 * same author). Covers every way a stored deletion can reach an event:
 *
 *  - **NIP-09, id-based** — a kind-5 with an `e` tag naming a server event's id.
 *  - **NIP-09, address-based** — a kind-5 with an `a` tag naming a server event's
 *    addressable/replaceable coordinate, at or after that event's `created_at`
 *    (NIP-09 only deletes `created_at <= deletion.created_at`).
 *  - **NIP-62 vanish** — a kind-62 by a server event's author, targeting [relay] (its
 *    `relay` tags name the URL or `ALL_RELAYS`), issued after that event (a vanish
 *    deletes `created_at < vanish.created_at`).
 *
 * Deduped by event id; a single deletion covering several server events is returned once.
 */
suspend fun IEventStore.deletionsCovering(
    serverEvents: List<Event>,
    relay: NormalizedRelayUrl,
): List<Event> {
    if (serverEvents.isEmpty()) return emptyList()
    val covering = LinkedHashMap<HexKey, Event>()

    // 1. id-based NIP-09: a kind-5 `e`-tagging a server id.
    query<Event>(Filter(kinds = listOf(DeletionEvent.KIND), tags = mapOf("e" to serverEvents.map { it.id })))
        .forEach { covering[it.id] = it }

    // 2. address-based NIP-09: a kind-5 `a`-tagging a server event's coordinate, cutoff-checked.
    val byAddress = serverEvents.filter { it.kind.isAddressable() || it.kind.isReplaceable() }.groupBy(::addressValue)
    if (byAddress.isNotEmpty()) {
        query<Event>(Filter(kinds = listOf(DeletionEvent.KIND), tags = mapOf("a" to byAddress.keys.toList())))
            .forEach { del ->
                if (del !is DeletionEvent) return@forEach
                for (addr in del.deleteAddresses()) {
                    val hit = byAddress[addr.toValue()] ?: continue
                    if (hit.any { it.createdAt <= del.createdAt }) {
                        covering[del.id] = del
                        break
                    }
                }
            }
    }

    // 3. NIP-62 vanish: a kind-62 by a server author, targeting this relay, issued after the event.
    query<Event>(Filter(kinds = listOf(RequestToVanishEvent.KIND), authors = serverEvents.mapTo(HashSet()) { it.pubKey }.toList()))
        .forEach { vanish ->
            if (vanish !is RequestToVanishEvent || !vanish.shouldVanishFrom(relay)) return@forEach
            if (serverEvents.any { it.pubKey == vanish.pubKey && it.createdAt < vanish.createdAt }) covering[vanish.id] = vanish
        }

    return covering.values.toList()
}
