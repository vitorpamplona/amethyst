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
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Filter
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexValidator
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.NNSEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent
import kotlin.coroutines.cancellation.CancellationException

object NostrSearchEventOrUserDataSource : AmethystNostrDataSource("SearchEventFeed") {
    private var searchString: String? = null

    private fun createAnythingWithIDFilter(): List<TypedFilter>? {
        val mySearchString = searchString
        if (mySearchString.isNullOrBlank()) {
            return null
        }

        val hexToWatch =
            try {
                val isAStraightHex =
                    if (HexValidator.isHex(mySearchString)) {
                        Hex.decode(mySearchString).toHexKey()
                    } else {
                        null
                    }

                when (val parsed = Nip19Bech32.uriToRoute(mySearchString)?.entity) {
                    is Nip19Bech32.NSec -> KeyPair(privKey = parsed.hex.bechToBytes()).pubKey.toHexKey()
                    is Nip19Bech32.NPub -> parsed.hex
                    is Nip19Bech32.NProfile -> parsed.hex
                    is Nip19Bech32.Note -> parsed.hex
                    is Nip19Bech32.NEvent -> parsed.hex
                    is Nip19Bech32.NEmbed -> parsed.event.id
                    is Nip19Bech32.NRelay -> null
                    is Nip19Bech32.NAddress -> parsed.atag
                    else -> isAStraightHex
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }

        val directReferenceFilters =
            hexToWatch?.let {
                if (it.contains(":")) {
                    // naddr
                    listOfNotNull(
                        ATag.parse(it, null)?.let { aTag ->
                            TypedFilter(
                                types = COMMON_FEED_TYPES,
                                filter =
                                    Filter(
                                        kinds = listOf(MetadataEvent.KIND, aTag.kind),
                                        authors = listOfNotNull(aTag.pubKeyHex),
                                        // just to be sure
                                        limit = 5,
                                    ),
                            )
                        },
                    )
                } else {
                    // event ids
                    listOf(
                        TypedFilter(
                            types = COMMON_FEED_TYPES,
                            filter =
                                Filter(
                                    ids = listOfNotNull(hexToWatch),
                                ),
                        ),
                        // authors
                        TypedFilter(
                            types = COMMON_FEED_TYPES,
                            filter =
                                Filter(
                                    kinds = listOf(MetadataEvent.KIND),
                                    authors = listOfNotNull(hexToWatch),
                                    // just to be sure
                                    limit = 5,
                                ),
                        ),
                    )
                }
            } ?: emptyList()

        // downloads all the reactions to a given event.
        return directReferenceFilters +
            listOfNotNull(
                TypedFilter(
                    types = setOf(FeedType.SEARCH),
                    filter =
                        Filter(
                            kinds = listOf(MetadataEvent.KIND),
                            search = mySearchString,
                            limit = 1000,
                        ),
                ),
                TypedFilter(
                    types = setOf(FeedType.SEARCH),
                    filter =
                        Filter(
                            kinds =
                                listOf(
                                    TextNoteEvent.KIND,
                                    LongTextNoteEvent.KIND,
                                    BadgeDefinitionEvent.KIND,
                                    PeopleListEvent.KIND,
                                    BookmarkListEvent.KIND,
                                    AudioHeaderEvent.KIND,
                                    AudioTrackEvent.KIND,
                                    PinListEvent.KIND,
                                    PollNoteEvent.KIND,
                                    ChannelCreateEvent.KIND,
                                ),
                            search = mySearchString,
                            limit = 100,
                        ),
                ),
                TypedFilter(
                    types = setOf(FeedType.SEARCH),
                    filter =
                        Filter(
                            kinds =
                                listOf(
                                    ChannelMetadataEvent.KIND,
                                    ClassifiedsEvent.KIND,
                                    CommunityDefinitionEvent.KIND,
                                    EmojiPackEvent.KIND,
                                    HighlightEvent.KIND,
                                    LiveActivitiesEvent.KIND,
                                    PollNoteEvent.KIND,
                                    NNSEvent.KIND,
                                    WikiNoteEvent.KIND,
                                ),
                            search = mySearchString,
                            limit = 100,
                        ),
                ),
            )
    }

    val searchChannel = requestNewChannel()

    override fun updateChannelFilters() {
        searchChannel.typedFilters = createAnythingWithIDFilter()
    }

    fun search(searchString: String) {
        if (this.searchString != searchString) {
            println("DataSource: ${this.javaClass.simpleName} Search for $searchString")
            this.searchString = searchString
            invalidateFilters()
        }
    }

    fun clear() {
        if (searchString != null) {
            println("DataSource: ${this.javaClass.simpleName} Clear")
            searchString = null
            invalidateFilters()
        }
    }
}
