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

import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.PerRelayFilter

object NostrThreadDataSource : AmethystNostrDataSource("SingleThreadFeed") {
    private var eventToWatch: String? = null

    fun createLoadEventsIfNotLoadedFilter(): TypedFilter? {
        val threadToLoad = eventToWatch ?: return null

        val eventsToLoad =
            ThreadAssembler()
                .findThreadFor(threadToLoad)
                .filter { it.event == null }
                .map { it.idHex }
                .toSet()
                .ifEmpty { null }
                ?: return null

        if (eventsToLoad.isEmpty()) return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                PerRelayFilter(
                    ids = eventsToLoad.toList(),
                ),
        )
    }

    val loadEventsChannel =
        requestNewChannel { _, _ ->
            // Many relays operate with limits in the amount of filters.
            // As information comes, the filters will be rotated to get more data.
            invalidateFilters()
        }

    override fun updateChannelFilters() {
        loadEventsChannel.typedFilters =
            listOfNotNull(createLoadEventsIfNotLoadedFilter()).ifEmpty { null }
    }

    fun loadThread(noteId: String?) {
        if (eventToWatch != noteId) {
            eventToWatch = noteId

            invalidateFilters()
        }
    }
}
