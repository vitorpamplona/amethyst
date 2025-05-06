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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent

// This allows multiple screen to be listening to tags, even the same tag
class UserProfileQueryState(
    val user: User,
)

class UserProfileFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<UserProfileQueryState>(client) {
    val list = "A"
    val latestEOSEs = EOSEAccount()

    fun since(key: UserProfileQueryState) =
        latestEOSEs.users[key.user]
            ?.followList
            ?.get(list)
            ?.relayList

    fun newEose(
        key: UserProfileQueryState,
        relayUrl: String,
        time: Long,
    ) {
        latestEOSEs.addOrUpdate(
            key.user,
            list,
            relayUrl,
            time,
        )
    }

    fun createUserInfoFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 1,
                        since = since(state),
                    ),
            )
        }

    fun createUserPostsFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(
                                TextNoteEvent.KIND,
                                GenericRepostEvent.KIND,
                                RepostEvent.KIND,
                                LongTextNoteEvent.KIND,
                                AudioTrackEvent.KIND,
                                AudioHeaderEvent.KIND,
                                PinListEvent.KIND,
                                PollNoteEvent.KIND,
                                HighlightEvent.KIND,
                                WikiNoteEvent.KIND,
                            ),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 200,
                        since = since(state),
                    ),
            )
        }

    fun createUserPostsFilter2(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(
                                TorrentEvent.KIND,
                                TorrentCommentEvent.KIND,
                                InteractiveStoryPrologueEvent.KIND,
                                CommentEvent.KIND,
                            ),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 50,
                        since = since(state),
                    ),
            )
        }

    fun createUserReceivedZapsFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(LnZapEvent.KIND),
                        tags = mapOf("p" to listOf(state.user.pubkeyHex)),
                        limit = 200,
                        since = since(state),
                    ),
            )
        }

    fun createFollowFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ContactListEvent.KIND),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 1,
                        since = since(state),
                    ),
            )
        }

    fun createFollowersFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf(state.user.pubkeyHex)),
                        since = since(state),
                    ),
            )
        }

    fun createAcceptedAwardsFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(BadgeProfilesEvent.KIND),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 1,
                        since = since(state),
                    ),
            )
        }

    fun createBookmarksFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(
                                BookmarkListEvent.KIND,
                                PeopleListEvent.KIND,
                                FollowListEvent.KIND,
                                AppRecommendationEvent.KIND,
                            ),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 100,
                        since = since(state),
                    ),
            )
        }

    fun createProfileGalleryFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(
                                ProfileGalleryEntryEvent.KIND,
                                PictureEvent.KIND,
                                VideoVerticalEvent.KIND,
                                VideoHorizontalEvent.KIND,
                            ),
                        authors = listOf(state.user.pubkeyHex),
                        limit = 1000,
                        since = since(state),
                    ),
            )
        }

    fun createReceivedAwardsFilter(keys: List<UserProfileQueryState>) =
        keys.map { state ->
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(BadgeAwardEvent.KIND),
                        tags = mapOf("p" to listOf(state.user.pubkeyHex)),
                        limit = 20,
                        since = since(state),
                    ),
            )
        }

    val userInfoChannel =
        requestNewSubscription { time, relayUrl ->
            forEachSubscriber {
                newEose(it, relayUrl, time)
            }
        }

    override fun updateSubscriptions(keys1: Set<UserProfileQueryState>) {
        if (keys1.isEmpty()) return

        val keys = keys1.distinctBy { it.user.pubkeyHex }

        userInfoChannel.typedFilters =
            listOfNotNull(
                createUserInfoFilter(keys),
                createUserPostsFilter(keys),
                createUserPostsFilter2(keys),
                createProfileGalleryFilter(keys),
                createFollowFilter(keys),
                createFollowersFilter(keys),
                createUserReceivedZapsFilter(keys),
                createAcceptedAwardsFilter(keys),
                createReceivedAwardsFilter(keys),
                createBookmarksFilter(keys),
            ).flatten().ifEmpty { null }
    }
}
