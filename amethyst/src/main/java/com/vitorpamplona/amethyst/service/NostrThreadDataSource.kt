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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter

object NostrThreadDataSource : AmethystNostrDataSource("SingleThreadFeed") {
    private var eventToWatch: String? = null

    fun createLoadEventsIfNotLoadedFilter(): List<TypedFilter> {
        val threadToLoad = eventToWatch ?: return emptyList()

        val branch = ThreadAssembler().findThreadFor(threadToLoad) ?: return emptyList()

        val eventsToLoad =
            branch.allNotes
                .filter { it.event == null }
                .map { it.idHex }
                .toSet()
                .ifEmpty { null }

        val address = if (branch.root is AddressableNote) branch.root.idHex else null
        val event = if (branch.root !is AddressableNote) branch.root.idHex else branch.root.event?.id()

        return listOfNotNull(
            eventsToLoad?.let {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            ids = it.toList(),
                        ),
                )
            },
            event?.let {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            tags =
                                mapOf(
                                    "e" to listOf(event),
                                ),
                        ),
                )
            },
            address?.let {
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter =
                        SincePerRelayFilter(
                            tags =
                                mapOf(
                                    "a" to listOf(address),
                                ),
                        ),
                )
            },
        )
    }

    val loadEventsChannel =
        requestNewChannel { _, _ ->
            // Many relays operate with limits in the amount of filters.
            // As information comes, the filters will be rotated to get more data.
            invalidateFilters()
        }

    override fun updateChannelFilters() {
        loadEventsChannel.typedFilters = createLoadEventsIfNotLoadedFilter()
    }

    fun loadThread(noteId: String?) {
        if (eventToWatch != noteId) {
            eventToWatch = noteId

            invalidateFilters()
        }
    }
}
