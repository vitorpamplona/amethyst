package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.service.model.BadgeAwardEvent
import com.vitorpamplona.amethyst.service.model.BadgeProfilesEvent
import com.vitorpamplona.amethyst.service.model.BookmarkListEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.MetadataEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrAccountDataSource : NostrDataSource("AccountData") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()

    fun createAccountContactListFilter(): TypedFilter {
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(ContactListEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountMetadataFilter(): TypedFilter {
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(MetadataEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountAcceptedAwardsFilter(): TypedFilter {
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(BadgeProfilesEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountBookmarkListFilter(): TypedFilter {
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(BookmarkListEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountReportsFilter(): TypedFilter {
        return TypedFilter(
            types = FeedType.values().toSet(),
            filter = JsonFilter(
                kinds = listOf(ReportEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                since = latestEOSEs.users[account.userProfile()]?.relayList
            )
        )
    }

    fun createNotificationFilter() = TypedFilter(
        types = FeedType.values().toSet(),
        filter = JsonFilter(
            kinds = listOf(
                TextNoteEvent.kind,
                PollNoteEvent.kind,
                ReactionEvent.kind,
                RepostEvent.kind,
                ReportEvent.kind,
                LnZapEvent.kind,
                ChannelMessageEvent.kind,
                BadgeAwardEvent.kind
            ),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
            limit = 400,
            since = latestEOSEs.users[account.userProfile()]?.relayList
        )
    )

    val accountChannel = requestNewChannel { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), relayUrl, time)
    }

    override fun updateChannelFilters() {
        // gets everthing about the user logged in
        accountChannel.typedFilters = listOf(
            createAccountMetadataFilter(),
            createAccountContactListFilter(),
            createNotificationFilter(),
            createAccountReportsFilter(),
            createAccountAcceptedAwardsFilter(),
            createAccountBookmarkListFilter()
        ).ifEmpty { null }
    }
}
