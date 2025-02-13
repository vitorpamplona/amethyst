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

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
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

object NostrUserProfileDataSource : AmethystNostrDataSource("UserProfileFeed") {
    var user: User? = null

    fun loadUserProfile(user: User?) {
        this.user = user
    }

    fun createUserInfoFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = listOf(it.pubkeyHex),
                        limit = 1,
                    ),
            )
        }

    fun createUserPostsFilter() =
        user?.let {
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
                        authors = listOf(it.pubkeyHex),
                        limit = 200,
                    ),
            )
        }

    fun createUserPostsFilter2() =
        user?.let {
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
                        authors = listOf(it.pubkeyHex),
                        limit = 50,
                    ),
            )
        }

    fun createUserReceivedZapsFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(LnZapEvent.KIND),
                        tags = mapOf("p" to listOf(it.pubkeyHex)),
                        limit = 200,
                    ),
            )
        }

    fun createFollowFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ContactListEvent.KIND),
                        authors = listOf(it.pubkeyHex),
                        limit = 1,
                    ),
            )
        }

    fun createFollowersFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(ContactListEvent.KIND),
                        tags = mapOf("p" to listOf(it.pubkeyHex)),
                    ),
            )
        }

    fun createAcceptedAwardsFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(BadgeProfilesEvent.KIND),
                        authors = listOf(it.pubkeyHex),
                        limit = 1,
                    ),
            )
        }

    fun createBookmarksFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(BookmarkListEvent.KIND, PeopleListEvent.KIND, AppRecommendationEvent.KIND),
                        authors = listOf(it.pubkeyHex),
                        limit = 100,
                    ),
            )
        }

    fun createProfileGalleryFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds =
                            listOf(ProfileGalleryEntryEvent.KIND, PictureEvent.KIND, VideoVerticalEvent.KIND, VideoHorizontalEvent.KIND),
                        authors = listOf(it.pubkeyHex),
                        limit = 1000,
                    ),
            )
        }

    fun createReceivedAwardsFilter() =
        user?.let {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(BadgeAwardEvent.KIND),
                        tags = mapOf("p" to listOf(it.pubkeyHex)),
                        limit = 20,
                    ),
            )
        }

    val userInfoChannel = requestNewChannel()

    override fun updateChannelFilters() {
        userInfoChannel.typedFilters =
            listOfNotNull(
                createUserInfoFilter(),
                createUserPostsFilter(),
                createUserPostsFilter2(),
                createProfileGalleryFilter(),
                createFollowFilter(),
                createFollowersFilter(),
                createUserReceivedZapsFilter(),
                createAcceptedAwardsFilter(),
                createReceivedAwardsFilter(),
                createBookmarksFilter(),
            ).ifEmpty { null }
    }
}
