package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.BadgeAwardEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.StatusEvent
import com.vitorpamplona.quartz.events.TextNoteEvent

object NostrAccountDataSource : NostrDataSource("AccountData") {
    lateinit var account: Account

    val latestEOSEs = EOSEAccount()
    val hasLoadedTheBasics = mutableMapOf<User, Boolean>()

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
                kinds = listOf(AdvertisedRelayListEvent.kind, StatusEvent.kind),
                authors = listOf(account.userProfile().pubkeyHex),
                limit = 5
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
        if (hasLoadedTheBasics[account.userProfile()] != null) {
            latestEOSEs.addOrUpdate(account.userProfile(), account.defaultNotificationFollowList, relayUrl, time)
        } else {
            hasLoadedTheBasics[account.userProfile()] = true

            invalidateFilters()
        }
    }

    override fun consume(event: Event, relay: Relay) {
        checkNotInMainThread()

        if (LocalCache.justVerify(event)) {
            if (event is GiftWrapEvent) {
                val privateKey = account.keyPair.privKey
                if (privateKey != null) {
                    event.cachedGift(privateKey)?.let {
                        this.consume(it, relay)
                    }
                } else if (account.loginWithExternalSigner) {
                    var cached = ExternalSignerUtils.cachedDecryptedContent[event.id]
                    if (cached == null) {
                        ExternalSignerUtils.decrypt(
                            event.content,
                            event.pubKey,
                            event.id,
                            SignerType.NIP44_DECRYPT
                        )
                        cached = ExternalSignerUtils.cachedDecryptedContent[event.id] ?: ""
                    }
                    event.cachedGift(account.keyPair.pubKey, cached)?.let {
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
                } else if (account.loginWithExternalSigner) {
                    var cached = ExternalSignerUtils.cachedDecryptedContent[event.id]
                    if (cached == null) {
                        ExternalSignerUtils.decrypt(
                            event.content,
                            event.pubKey,
                            event.id,
                            SignerType.NIP44_DECRYPT
                        )
                        cached = ExternalSignerUtils.cachedDecryptedContent[event.id] ?: ""
                    }
                    event.cachedGossip(account.keyPair.pubKey, cached)?.let {
                        LocalCache.justConsume(it, relay)
                    }
                }

                // Don't store sealed gossips to avoid rebroadcasting by mistake.
            } else {
                LocalCache.justConsume(event, relay)
            }
        }
    }

    override fun markAsSeenOnRelay(eventId: String, relay: Relay) {
        super.markAsSeenOnRelay(eventId, relay)

        val note = LocalCache.getNoteIfExists(eventId) ?: return
        val privKey = account.keyPair.privKey ?: return

        val noteEvent = note.event ?: return
        markInnerAsSeenOnRelay(noteEvent, privKey, relay)
    }

    private fun markInnerAsSeenOnRelay(noteEvent: EventInterface, privKey: ByteArray, relay: Relay) {
        LocalCache.getNoteIfExists(noteEvent.id())?.addRelay(relay)

        if (noteEvent is GiftWrapEvent) {
            val gift = noteEvent.cachedGift(privKey) ?: return
            markInnerAsSeenOnRelay(gift, privKey, relay)
        } else if (noteEvent is SealedGossipEvent) {
            val rumor = noteEvent.cachedGossip(privKey) ?: return
            markInnerAsSeenOnRelay(rumor, privKey, relay)
        }
    }

    override fun updateChannelFilters() {
        return if (hasLoadedTheBasics[account.userProfile()] != null) {
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
        } else {
            // just the basics.
            accountChannel.typedFilters = listOf(
                createAccountMetadataFilter(),
                createAccountContactListFilter(),
                createAccountRelayListFilter(),
                createAccountBookmarkListFilter()
            ).ifEmpty { null }
        }
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
