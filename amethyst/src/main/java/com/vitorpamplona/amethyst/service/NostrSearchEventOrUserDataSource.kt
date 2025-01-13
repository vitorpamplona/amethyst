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

import com.vitorpamplona.ammolite.relays.ALL_FEED_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.crypto.Hex
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.experimental.audio.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32Entities.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32Entities.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32Entities.parse
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiPackEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
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
                    if (Hex.isHex(mySearchString)) {
                        Hex.decode(mySearchString).toHexKey()
                    } else {
                        null
                    }

                when (val parsed = Nip19Parser.uriToRoute(mySearchString)?.entity) {
                    is NSec -> KeyPair(privKey = parsed.hex.bechToBytes()).pubKey.toHexKey()
                    is NPub -> parsed.hex
                    is NProfile -> parsed.hex
                    is com.vitorpamplona.quartz.nip19Bech32Entities.entities.Note -> parsed.hex
                    is NEvent -> parsed.hex
                    is NEmbed -> parsed.event.id
                    is NRelay -> null
                    is NAddress -> parsed.aTag()
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
                                types = ALL_FEED_TYPES,
                                filter =
                                    SincePerRelayFilter(
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
                            types = ALL_FEED_TYPES,
                            filter =
                                SincePerRelayFilter(
                                    ids = listOfNotNull(it),
                                ),
                        ),
                        // authors
                        TypedFilter(
                            types = ALL_FEED_TYPES,
                            filter =
                                SincePerRelayFilter(
                                    kinds = listOf(MetadataEvent.KIND),
                                    authors = listOfNotNull(it),
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
                        SincePerRelayFilter(
                            kinds = listOf(MetadataEvent.KIND),
                            search = mySearchString,
                            limit = 1000,
                        ),
                ),
                TypedFilter(
                    types = setOf(FeedType.SEARCH),
                    filter =
                        SincePerRelayFilter(
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
                        SincePerRelayFilter(
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
                                    CommentEvent.KIND,
                                ),
                            search = mySearchString,
                            limit = 100,
                        ),
                ),
                TypedFilter(
                    types = setOf(FeedType.SEARCH),
                    filter =
                        SincePerRelayFilter(
                            kinds =
                                listOf(
                                    InteractiveStoryPrologueEvent.KIND,
                                    InteractiveStorySceneEvent.KIND,
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
