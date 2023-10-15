package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.AppRecommendationEvent
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.BadgeAwardEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PinListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

object NostrUserProfileDataSource : NostrDataSource("UserProfileFeed") {
    var user: User? = null

    fun loadUserProfile(user: User?) {
        this.user = user
    }

    fun createUserInfoFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(MetadataEvent.kind),
                authors = listOf(it.pubkeyHex),
                limit = 1
            )
        )
    }

    fun createUserPostsFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(
                    TextNoteEvent.kind,
                    GenericRepostEvent.kind,
                    RepostEvent.kind,
                    LongTextNoteEvent.kind,
                    AudioTrackEvent.kind,
                    AudioHeaderEvent.kind,
                    PinListEvent.kind,
                    PollNoteEvent.kind,
                    HighlightEvent.kind
                ),
                authors = listOf(it.pubkeyHex),
                limit = 200
            )
        )
    }

    fun createUserReceivedZapsFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(LnZapEvent.kind),
                tags = mapOf("p" to listOf(it.pubkeyHex))
            )
        )
    }

    fun createFollowFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ContactListEvent.kind),
                authors = listOf(it.pubkeyHex),
                limit = 1
            )
        )
    }

    fun createFollowersFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ContactListEvent.kind),
                tags = mapOf("p" to listOf(it.pubkeyHex))
            )
        )
    }

    fun createAcceptedAwardsFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(BadgeProfilesEvent.kind),
                authors = listOf(it.pubkeyHex),
                limit = 1
            )
        )
    }

    fun createBookmarksFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(BookmarkListEvent.kind, PeopleListEvent.kind, AppRecommendationEvent.kind),
                authors = listOf(it.pubkeyHex),
                limit = 100
            )
        )
    }

    fun createReceivedAwardsFilter() = user?.let {
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(BadgeAwardEvent.kind),
                tags = mapOf("p" to listOf(it.pubkeyHex)),
                limit = 20
            )
        )
    }

    val userInfoChannel = requestNewChannel()

    override fun updateChannelFilters() {
        userInfoChannel.typedFilters = listOfNotNull(
            createUserInfoFilter(),
            createUserPostsFilter(),
            createFollowFilter(),
            createFollowersFilter(),
            createUserReceivedZapsFilter(),
            createAcceptedAwardsFilter(),
            createReceivedAwardsFilter(),
            createBookmarksFilter()
        ).ifEmpty { null }
    }
}
