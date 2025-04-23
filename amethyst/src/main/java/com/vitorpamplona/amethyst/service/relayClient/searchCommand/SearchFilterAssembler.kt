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
package com.vitorpamplona.amethyst.service.relayClient.searchCommand

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.ammolite.relays.ALL_FEED_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.entities.Note
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.collections.flatten

@Stable
class SearchQueryState(
    val searchQuery: MutableStateFlow<String>,
) : MutableQueryState {
    override fun flow(): Flow<String> = searchQuery
}

class SearchFilterAssembler(
    val cache: LocalCache,
    client: NostrClient,
    scope: CoroutineScope,
) : MutableQueryBasedSubscriptionOrchestrator<SearchQueryState>(client, scope) {
    fun filterByAuthor(pubKey: HexKey) =
        listOf(
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = listOfNotNull(pubKey),
                        limit = 1,
                    ),
            ),
        )

    fun filterByEvent(eventId: HexKey) =
        listOf(
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        ids = listOfNotNull(eventId),
                    ),
            ),
        )

    fun filterByAddress(parsed: NAddress) =
        listOf(
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = listOfNotNull(parsed.author),
                        limit = 1,
                    ),
            ),
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(parsed.kind),
                        authors = listOfNotNull(parsed.author),
                        tags = mapOf("d" to listOf(parsed.dTag)),
                        limit = 5,
                    ),
            ),
        )

    private fun createAnythingWithIDFilter(keys: Set<SearchQueryState>): List<TypedFilter> {
        if (keys.isEmpty()) return emptyList()

        val uniqueQueries =
            keys.mapNotNullTo(mutableSetOf()) {
                if (!it.searchQuery.value.isBlank()) {
                    it.searchQuery.value
                } else {
                    null
                }
            }

        return uniqueQueries
            .map { mySearchString ->
                val directFilters =
                    runCatching {
                        if (Hex.isHex(mySearchString)) {
                            val key = Hex.decode(mySearchString).toHexKey()
                            filterByAuthor(key) + filterByEvent(key)
                        } else {
                            when (val parsed = Nip19Parser.uriToRoute(mySearchString)?.entity) {
                                is NSec -> filterByAuthor(Nip01.pubKeyCreate(parsed.hex.hexToByteArray()).toHexKey())
                                is NPub -> filterByAuthor(parsed.hex)
                                is NProfile -> filterByAuthor(parsed.hex)
                                is Note -> filterByEvent(parsed.hex)
                                is NEvent -> filterByEvent(parsed.hex)
                                is NEmbed -> {
                                    cache.verifyAndConsume(parsed.event, null)
                                    emptyList()
                                }

                                is NRelay -> emptyList()
                                is NAddress -> filterByAddress(parsed)
                                else -> emptyList()
                            }
                        }
                    }.getOrDefault(emptyList())

                val searchFilters =
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

                directFilters + searchFilters
            }.flatten()
    }

    val searchChannel = requestNewSubscription()

    override fun updateSubscriptions(keys: Set<SearchQueryState>) {
        searchChannel.typedFilters =
            logTime(
                debugMessage = { "Search DataSource UpdateSubscriptions with ${it?.size} filter size" },
                block = { createAnythingWithIDFilter(keys).ifEmpty { null } },
            )
    }
}
