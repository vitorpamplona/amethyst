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
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey

fun filterMissingEvents(keys: List<EventFinderQueryState>): List<TypedFilter>? {
    val missingEvents = mutableSetOf<String>()

    keys.forEach {
        if (it.note !is AddressableNote && it.note.event == null) {
            missingEvents.add(it.note.idHex)
        }

        // loads threading that is event-based
        it.note.replyTo?.forEach {
            if (it !is AddressableNote && it.event == null) {
                missingEvents.add(it.idHex)
            }
        }
    }

    return filterMissingEvents(missingEvents)
}

fun filterMissingEvents(missingEventIds: Set<HexKey>): List<TypedFilter>? {
    if (missingEventIds.isEmpty()) return null

    return listOf(
        TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter = SincePerRelayFilter(ids = missingEventIds.sorted()),
        ),
    )
}
