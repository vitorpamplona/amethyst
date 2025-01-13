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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.GitReplyEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip96FileStorage.FileServersEvent
import com.vitorpamplona.quartz.utils.TimeUtils

// TODO: Migrate this to a property of AccountVi
object NostrAccountDataSource : AmethystNostrDataSource("AccountData") {
    lateinit var account: Account
    var otherAccounts = listOf<HexKey>()

    val latestEOSEs = EOSEAccount()
    val hasLoadedTheBasics = mutableMapOf<User, Boolean>()

    fun createAccountMetadataFilter(): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            MetadataEvent.KIND,
                            ContactListEvent.KIND,
                            StatusEvent.KIND,
                            AdvertisedRelayListEvent.KIND,
                            ChatMessageRelayListEvent.KIND,
                            SearchRelayListEvent.KIND,
                            FileServersEvent.KIND,
                            BlossomServersEvent.KIND,
                            PrivateOutboxRelayListEvent.KIND,
                        ),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 20,
                ),
        )

    fun createOtherAccountsBaseFilter(): TypedFilter? {
        val otherAuthors = otherAccounts.filter { it != account.userProfile().pubkeyHex }
        if (otherAuthors.isEmpty()) return null
        return TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            MetadataEvent.KIND,
                            ContactListEvent.KIND,
                            AdvertisedRelayListEvent.KIND,
                            ChatMessageRelayListEvent.KIND,
                            SearchRelayListEvent.KIND,
                            FileServersEvent.KIND,
                            BlossomServersEvent.KIND,
                            MuteListEvent.KIND,
                            PeopleListEvent.KIND,
                        ),
                    authors = otherAuthors,
                    limit = otherAuthors.size * 20,
                ),
        )
    }

    fun createAccountSettingsFilter(): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(BookmarkListEvent.KIND, PeopleListEvent.KIND, MuteListEvent.KIND, BadgeProfilesEvent.KIND, EmojiPackSelectionEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 100,
                ),
        )

    fun createAccountSettings2Filter(): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(AppSpecificDataEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    tags = mapOf("d" to listOf(Account.APP_SPECIFIC_DATA_D_TAG)),
                    limit = 1,
                ),
        )

    fun createAccountReportsFilter(): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(DraftEvent.KIND, ReportEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.settings.defaultNotificationFollowList.value)
                            ?.relayList,
                ),
        )

    fun createAccountLastPostsListFilter(): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 400,
                ),
        )

    fun createNotificationFilter(): TypedFilter {
        var since =
            latestEOSEs.users[account.userProfile()]
                ?.followList
                ?.get(account.settings.defaultNotificationFollowList.value)
                ?.relayList
                ?.toMutableMap()

        if (since == null) {
            since =
                account.connectToRelays.value
                    .associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }
                    .toMutableMap()
        } else {
            account.connectToRelays.value.forEach {
                val eose = since.get(it.url)
                if (eose == null) {
                    since.put(it.url, EOSETime(TimeUtils.oneWeekAgo()))
                }
            }
        }

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            PollNoteEvent.KIND,
                            ReactionEvent.KIND,
                            RepostEvent.KIND,
                            GenericRepostEvent.KIND,
                            ReportEvent.KIND,
                            LnZapEvent.KIND,
                            LnZapPaymentResponseEvent.KIND,
                            ChannelMessageEvent.KIND,
                            BadgeAwardEvent.KIND,
                        ),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    limit = 4000,
                    since = since,
                ),
        )
    }

    fun createNotificationFilter2(): TypedFilter {
        val since =
            latestEOSEs.users[account.userProfile()]
                ?.followList
                ?.get(account.settings.defaultNotificationFollowList.value)
                ?.relayList
                ?: account.connectToRelays.value.associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }
                ?: account.convertLocalRelays().associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            GitReplyEvent.KIND,
                            GitIssueEvent.KIND,
                            GitPatchEvent.KIND,
                            HighlightEvent.KIND,
                            CommentEvent.KIND,
                            CalendarDateSlotEvent.KIND,
                            CalendarTimeSlotEvent.KIND,
                            CalendarRSVPEvent.KIND,
                            InteractiveStoryPrologueEvent.KIND,
                            InteractiveStorySceneEvent.KIND,
                        ),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    limit = 400,
                    since = since,
                ),
        )
    }

    fun createGiftWrapsToMeFilter() =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(GiftWrapEvent.KIND),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get("&&((GIFTWRAPS_EOSE))&&")
                            ?.relayList
                            ?.mapValues {
                                EOSETime(it.value.time - TimeUtils.twoDays())
                            },
                ),
        )

    val accountChannel =
        requestNewChannel { time, relayUrl ->
            if (hasLoadedTheBasics[account.userProfile()] != null) {
                latestEOSEs.addOrUpdate(
                    account.userProfile(),
                    account.settings.defaultNotificationFollowList.value,
                    relayUrl,
                    time,
                )

                latestEOSEs.addOrUpdate(
                    account.userProfile(),
                    "&&((GIFTWRAPS_EOSE))&&",
                    relayUrl,
                    time,
                )
            } else {
                hasLoadedTheBasics[account.userProfile()] = true

                invalidateFilters()
            }
        }

    override fun consume(
        event: Event,
        relay: Relay,
    ) {
        if (LocalCache.justVerify(event)) {
            consumeAlreadyVerified(event, relay)
        }
    }

    fun consumeAlreadyVerified(
        event: Event,
        relay: Relay,
    ) {
        checkNotInMainThread()

        when (event) {
            is PrivateOutboxRelayListEvent -> {
                val note = LocalCache.getAddressableNoteIfExists(event.addressTag())
                val noteEvent = note?.event
                if (noteEvent == null || event.createdAt > noteEvent.createdAt) {
                    event.privateTags(account.signer) {
                        LocalCache.justConsume(event, relay)
                    }
                }
            }

            is DraftEvent -> {
                // Avoid decrypting over and over again if the event already exist.

                if (!event.isDeleted()) {
                    val note = LocalCache.getAddressableNoteIfExists(event.addressTag())
                    val noteEvent = note?.event
                    if (noteEvent != null) {
                        if (event.createdAt > noteEvent.createdAt || relay.brief !in note.relays) {
                            LocalCache.consume(event, relay)
                        }
                    } else {
                        // decrypts
                        event.cachedDraft(account.signer) {}

                        LocalCache.justConsume(event, relay)
                    }
                }
            }

            is GiftWrapEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                val note = LocalCache.getNoteIfExists(event.id)
                val noteEvent = note?.event as? GiftWrapEvent
                if (noteEvent != null) {
                    if (relay.brief !in note.relays) {
                        LocalCache.justConsume(noteEvent, relay)

                        noteEvent.innerEventId?.let {
                            (LocalCache.getNoteIfExists(it)?.event as? Event)?.let {
                                this.consumeAlreadyVerified(it, relay)
                            }
                        } ?: run {
                            event.unwrap(account.signer) {
                                this.consume(it, relay)
                                noteEvent.innerEventId = it.id
                            }
                        }
                    }
                } else {
                    // new event
                    event.unwrap(account.signer) {
                        LocalCache.justConsume(event, relay)
                        this.consume(it, relay)
                    }
                }
            }

            is SealedGossipEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                val note = LocalCache.getNoteIfExists(event.id)
                val noteEvent = note?.event as? SealedGossipEvent
                if (noteEvent != null) {
                    if (relay.brief !in note.relays) {
                        LocalCache.justConsume(noteEvent, relay)

                        noteEvent.innerEventId?.let {
                            (LocalCache.getNoteIfExists(it)?.event as? Event)?.let {
                                LocalCache.justConsume(it, relay)
                            }
                        } ?: run {
                            event.unseal(account.signer) {
                                LocalCache.justConsume(it, relay)
                                noteEvent.innerEventId = it.id
                            }
                        }
                    }
                } else {
                    // new event
                    event.unseal(account.signer) {
                        LocalCache.justConsume(event, relay)
                        LocalCache.justConsume(it, relay)
                    }
                }
            }

            is LnZapEvent -> {
                // Avoid decrypting over and over again if the event already exist.
                val note = LocalCache.getNoteIfExists(event.id)
                if (note?.event == null) {
                    event.zapRequest?.let {
                        if (it.isPrivateZap()) {
                            it.decryptPrivateZap(account.signer) {}
                        }
                    }

                    LocalCache.justConsume(event, relay)
                }
            }

            else -> {
                LocalCache.justConsume(event, relay)
            }
        }
    }

    override fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) {
        checkNotInMainThread()

        super.markAsSeenOnRelay(eventId, relay)

        val note = LocalCache.getNoteIfExists(eventId) ?: return
        val noteEvent = note.event ?: return
        markInnerAsSeenOnRelay(noteEvent, relay)
    }

    private fun markInnerAsSeenOnRelay(
        newNoteEvent: Event,
        relay: Relay,
    ) {
        markInnerAsSeenOnRelay(newNoteEvent.id, relay)
    }

    private fun markInnerAsSeenOnRelay(
        eventId: HexKey,
        relay: Relay,
    ) {
        val note = LocalCache.getNoteIfExists(eventId)

        if (note != null) {
            note.addRelay(relay)

            val noteEvent = note.event
            if (noteEvent is GiftWrapEvent) {
                noteEvent.innerEventId?.let {
                    markInnerAsSeenOnRelay(it, relay)
                }
            } else if (noteEvent is SealedGossipEvent) {
                noteEvent.innerEventId?.let {
                    markInnerAsSeenOnRelay(it, relay)
                }
            }
        }
    }

    override fun updateChannelFilters() =
        if (hasLoadedTheBasics[account.userProfile()] != null) {
            // gets everything about the user logged in
            accountChannel.typedFilters =
                listOfNotNull(
                    createAccountMetadataFilter(),
                    createAccountSettings2Filter(),
                    createNotificationFilter(),
                    createNotificationFilter2(),
                    createGiftWrapsToMeFilter(),
                    createAccountReportsFilter(),
                    createAccountSettingsFilter(),
                    createAccountLastPostsListFilter(),
                    createOtherAccountsBaseFilter(),
                ).ifEmpty { null }
        } else {
            // just the basics.
            accountChannel.typedFilters =
                listOf(
                    createAccountMetadataFilter(),
                    createAccountSettingsFilter(),
                    createAccountSettings2Filter(),
                ).ifEmpty { null }
        }

    override fun auth(
        relay: Relay,
        challenge: String,
    ) {
        super.auth(relay, challenge)

        if (this::account.isInitialized) {
            account.sendAuthEvent(relay, challenge)
        }
    }

    override fun notify(
        relay: Relay,
        description: String,
    ) {
        super.notify(relay, description)

        if (this::account.isInitialized) {
            account.addPaymentRequestIfNew(Account.PaymentRequest(relay.url, description))
        }
    }
}
