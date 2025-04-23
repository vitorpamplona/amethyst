/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.collections.flatten
import kotlin.collections.ifEmpty

// This allows multiple screen to be listening to tags, even the same tag
class ThreadQueryState(
    val eventId: HexKey,
)

class ThreadFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<ThreadQueryState>(client) {
    fun createLoadEventsIfNotLoadedFilter(keys: Set<ThreadQueryState>): List<TypedFilter> {
        if (keys.isEmpty()) return emptyList()

        val uniqueThreadIds = keys.mapTo(mutableSetOf()) { it.eventId }

        val branches =
            uniqueThreadIds.map {
                ThreadAssembler().findThreadFor(it) ?: return emptyList()
            }

        val eventsToLoad =
            branches
                .mapNotNull {
                    it.allNotes
                        .filter { it.event == null }
                        .map { it.idHex }
                        .toSet()
                        .ifEmpty { null }
                }.flatten()
                .distinct()

        val addressRoots =
            branches
                .mapNotNull {
                    if (it.root is AddressableNote) it.root.idHex else null
                }.distinct()

        val eventRoots =
            branches
                .mapNotNull {
                    if (it.root !is AddressableNote) it.root.idHex else it.root.event?.id
                }.distinct()

        return listOfNotNull(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = SincePerRelayFilter(tags = mapOf("e" to eventRoots)),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = SincePerRelayFilter(tags = mapOf("a" to addressRoots)),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = SincePerRelayFilter(tags = mapOf("E" to eventRoots)),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = SincePerRelayFilter(tags = mapOf("A" to addressRoots)),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = SincePerRelayFilter(ids = eventsToLoad),
            ),
        )
    }

    val loadEventsChannel =
        requestNewSubscription { _, _ ->
            // Many relays operate with limits in the amount of filters.
            // As information comes, the filters will be rotated to get more data.
            invalidateFilters()
        }

    override fun updateSubscriptions(keys: Set<ThreadQueryState>) {
        loadEventsChannel.typedFilters = createLoadEventsIfNotLoadedFilter(keys).ifEmpty { null }
    }
}
