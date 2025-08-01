/**
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
package com.vitorpamplona.amethyst.service.relayClient.searchCommand.subassemblies

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun filterByEvent(
    eventId: HexKey,
    default: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    val note = LocalCache.checkGetOrCreateNote(eventId) ?: return emptyList()

    val list =
        mapOfSet {
            if (note !is AddressableNote && note.event == null) {
                potentialRelaysToFindEvent(note).ifEmpty { default }.forEach { relayUrl ->
                    add(relayUrl, note.idHex)
                }
            }

            // loads threading that is event-based
            note.replyTo?.forEach { parentNote ->
                if (parentNote !is AddressableNote && note.event == null) {
                    potentialRelaysToFindEvent(note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, note.idHex)
                    }
                }
            }
        }

    return filterMissingEvents(list)
}
