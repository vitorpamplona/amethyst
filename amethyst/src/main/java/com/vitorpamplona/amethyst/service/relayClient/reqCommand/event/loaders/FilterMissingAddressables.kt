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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address

fun filterMissingAddressables(
    keys: List<EventFinderQueryState>,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    val missingAddressables = mutableSetOf<Address>()

    keys.forEach {
        if (it.note is AddressableNote && it.note.event == null) {
            missingAddressables.add(it.note.address())
        }

        // loads threading that is event-based
        it.note.replyTo?.forEach {
            if (it is AddressableNote && it.event == null) {
                missingAddressables.add(it.address())
            }
        }
    }

    return filterMissingAddressables(missingAddressables, since)
}

fun filterMissingAddressables(
    missingAddressables: Set<Address>,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    if (missingAddressables.isEmpty()) return null

    return missingAddressables.map { aTag ->
        if (aTag.kind < 25000 && aTag.dTag.isBlank()) {
            TypedFilter(
                types = EVENT_FINDER_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(aTag.kind),
                        authors = listOf(aTag.pubKeyHex),
                        limit = 1,
                    ),
            )
        } else {
            TypedFilter(
                types = EVENT_FINDER_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(aTag.kind),
                        tags = mapOf("d" to listOf(aTag.dTag)),
                        authors = listOf(aTag.pubKeyHex),
                        limit = 1,
                    ),
            )
        }
    }
}
