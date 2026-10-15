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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Newer versions of the addressables on screen. The loader (`filterMissingAddressables`) asks
 * for an address only while the cache has no version of it, then stops, so an article, a list or
 * a list header published again from another device never reached a screen that already showed
 * it. This keeps asking, while the note is watched, where its author writes and where it was seen.
 *
 * One filter per relay and kind, `authors` × `#d` (a cross product, as the live-stream watcher
 * does: on-screen addresses are few and rarely share a `d`). Until a relay has answered once, it
 * starts at the oldest version held, so it does not download every address again from zero.
 */
fun filterNewVersionsOfAddresses(
    keys: List<AddressableNote>,
    since: SincePerRelayMap?,
    accountPubKey: HexKey? = null,
): List<RelayBasedFilter>? {
    // An address with no version yet is the loader's to find.
    val loaded = keys.filter { it.event != null }
    if (loaded.isEmpty()) return null

    val perRelay = HashMap<NormalizedRelayUrl, MutableSet<AddressableNote>>()
    loaded.forEach { note ->
        val relays = (note.author?.outboxRelays() ?: emptyList()) + note.relays
        relays.forEach { relay -> perRelay.getOrPut(relay) { LinkedHashSet() }.add(note) }
    }

    return perRelay.flatMap { (relay, notes) ->
        notes
            .groupBy { it.address.kind }
            .toSortedMap()
            .map { (kind, sameKind) ->
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.REFERENCED_EVENTS,
                            accountPubKeys = listOfNotNull(accountPubKey),
                            kinds = listOf(kind),
                            authors = sameKind.map { it.address.pubKeyHex }.distinct().sorted(),
                            tags = mapOf("d" to sameKind.map { it.address.dTag }.distinct().sorted()),
                            since = since?.get(relay)?.time ?: sameKind.minOf { it.createdAt() ?: 0L },
                        ),
                )
            }
    }
}
