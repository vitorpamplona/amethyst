package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
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
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.TypedFilter

object NostrAccountDataSource : NostrDataSource("AccountData") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()

    fun createAccountContactListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ContactListEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountMetadataFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(MetadataEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountRelayListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(AdvertisedRelayListEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 1
            )
        )
    }

    fun createAccountAcceptedAwardsFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(BadgeProfilesEvent.kind, EmojiPackSelectionEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 10
            )
        )
    }

    fun createAccountBookmarkListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(BookmarkListEvent.kind, PeopleListEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 100
            )
        )
    }

    fun createAccountReportsFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                kinds = listOf(ReportEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultNotificationFollowList)?.relayList
            )
        )
    }

    fun createAccountLastPostsListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = JsonFilter(
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 400
            )
        )
    }

    fun createNotificationFilter() = TypedFilter(
        types = COMMON_FEED_TYPES,
        filter = JsonFilter(
            kinds = listOf(
                TextNoteEvent.kind,
                PollNoteEvent.kind,
                ReactionEvent.kind,
                RepostEvent.kind,
                GenericRepostEvent.kind,
                ReportEvent.kind,
                LnZapEvent.kind,
                LnZapPaymentResponseEvent.kind,
                ChannelMessageEvent.kind,
                BadgeAwardEvent.kind
            ),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
            limit = 4000,
            since = latestEOSEs.users[account.userProfile()]?.followList?.get(account.defaultNotificationFollowList)?.relayList
        )
    )

    fun createGiftWrapsToMeFilter() = TypedFilter(
        types = COMMON_FEED_TYPES,
        filter = JsonFilter(
            kinds = listOf(GiftWrapEvent.kind),
            tags = mapOf("p" to listOf(account.userProfile().pubkeyHex))
        )
    )

    val accountChannel = requestNewChannel { time, relayUrl ->
        latestEOSEs.addOrUpdate(account.userProfile(), account.defaultNotificationFollowList, relayUrl, time)
    }

    override fun consume(event: Event, relay: Relay) {
        if (LocalCache.justVerify(event)) {
            if (event is GiftWrapEvent) {
                val privateKey = account.keyPair.privKey
                if (privateKey != null) {
                    event.cachedGift(privateKey)?.let {
                        this.consume(it, relay)
                    }
                }
            }

            if (event is SealedGossipEvent) {
                val privateKey = account.keyPair.privKey
                if (privateKey != null) {
                    event.cachedGossip(privateKey)?.let {
                        LocalCache.justConsume(it, relay)
                    }
                }

                // Don't store sealed gossips to avoid rebroadcasting by mistake.
            } else {
                LocalCache.justConsume(event, relay)
            }
        }
    }

    override fun updateChannelFilters() {
        // gets everthing about the user logged in
        accountChannel.typedFilters = listOf(
            createAccountMetadataFilter(),
            createAccountContactListFilter(),
            createAccountRelayListFilter(),
            createNotificationFilter(),
            createGiftWrapsToMeFilter(),
            createAccountReportsFilter(),
            createAccountAcceptedAwardsFilter(),
            createAccountBookmarkListFilter(),
            createAccountLastPostsListFilter()
        ).ifEmpty { null }
    }

    override fun auth(relay: Relay, challenge: String) {
        super.auth(relay, challenge)

        if (this::account.isInitialized) {
            val event = account.createAuthEvent(relay, challenge)

            if (event != null) {
                Client.send(
                    event,
                    relay.url
                )
            }
        }
    }
}
