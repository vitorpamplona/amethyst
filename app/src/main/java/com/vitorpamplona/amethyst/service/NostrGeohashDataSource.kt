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

import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.JsonFilter
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent

object NostrGeohashDataSource : AmethystNostrDataSource("SingleGeoHashFeed") {
    private var geohashToWatch: String? = null

    fun createLoadHashtagFilter(): TypedFilter? {
        val hashToLoad = geohashToWatch ?: return null

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    tags =
                        mapOf(
                            "g" to
                                listOf(
                                    hashToLoad,
                                ),
                        ),
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            ChannelMessageEvent.KIND,
                            LongTextNoteEvent.KIND,
                            PollNoteEvent.KIND,
                            LiveActivitiesChatMessageEvent.KIND,
                            ClassifiedsEvent.KIND,
                            HighlightEvent.KIND,
                            AudioTrackEvent.KIND,
                            AudioHeaderEvent.KIND,
                            WikiNoteEvent.KIND,
                        ),
                    limit = 200,
                ),
        )
    }

    val loadGeohashChannel = requestNewChannel()

    override fun updateChannelFilters() {
        loadGeohashChannel.typedFilters = listOfNotNull(createLoadHashtagFilter()).ifEmpty { null }
    }

    fun loadHashtag(tag: String?) {
        geohashToWatch = tag

        invalidateFilters()
    }
}
