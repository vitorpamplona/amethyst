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
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.BadgeAwardEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.CalendarDateSlotEvent
import com.vitorpamplona.quartz.events.CalendarRSVPEvent
import com.vitorpamplona.quartz.events.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DirectMessageRelayListEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.GitIssueEvent
import com.vitorpamplona.quartz.events.GitPatchEvent
import com.vitorpamplona.quartz.events.GitReplyEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.StatusEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils

// TODO: Migrate this to a property of AccountVi
object NostrAccountDataSource : NostrDataSource("AccountData") {
    lateinit var account: Account
    var otherAccounts = listOf<HexKey>()

    val latestEOSEs = EOSEAccount()
    val hasLoadedTheBasics = mutableMapOf<User, Boolean>()

    fun createAccountContactListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(ContactListEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 1,
                ),
        )
    }

    fun createAccountMetadataFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(MetadataEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 1,
                ),
        )
    }

    fun createAccountRelayListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(StatusEvent.KIND, AdvertisedRelayListEvent.KIND, DirectMessageRelayListEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 5,
                ),
        )
    }

    fun createOtherAccountsBaseFilter(): TypedFilter? {
        if (otherAccounts.isEmpty()) return null
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds =
                        listOf(
                            MetadataEvent.KIND,
                            ContactListEvent.KIND,
                            AdvertisedRelayListEvent.KIND,
                            DirectMessageRelayListEvent.KIND,
                            MuteListEvent.KIND,
                            PeopleListEvent.KIND,
                        ),
                    authors = otherAccounts.filter { it != account.userProfile().pubkeyHex },
                    limit = 100,
                ),
        )
    }

    fun createAccountSettingsFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(BookmarkListEvent.KIND, PeopleListEvent.KIND, MuteListEvent.KIND, BadgeProfilesEvent.KIND, EmojiPackSelectionEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 100,
                ),
        )
    }

    fun createAccountReportsFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds = listOf(DraftEvent.KIND, ReportEvent.KIND),
                    authors = listOf(account.userProfile().pubkeyHex),
                    since =
                        latestEOSEs.users[account.userProfile()]
                            ?.followList
                            ?.get(account.defaultNotificationFollowList.value)
                            ?.relayList,
                ),
        )
    }

    fun createAccountLastPostsListFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    authors = listOf(account.userProfile().pubkeyHex),
                    limit = 400,
                ),
        )
    }

    fun createNotificationFilter(): TypedFilter {
        val since =
            latestEOSEs.users[account.userProfile()]
                ?.followList
                ?.get(account.defaultNotificationFollowList.value)
                ?.relayList
                ?: account.activeRelays()?.associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }
                ?: account.convertLocalRelays().associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
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
                ?.get(account.defaultNotificationFollowList.value)
                ?.relayList
                ?: account.activeRelays()?.associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }
                ?: account.convertLocalRelays().associate { it.url to EOSETime(TimeUtils.oneWeekAgo()) }

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                JsonFilter(
                    kinds =
                        listOf(
                            GitReplyEvent.KIND,
                            GitIssueEvent.KIND,
                            GitPatchEvent.KIND,
                            HighlightEvent.KIND,
                            CalendarDateSlotEvent.KIND,
                            CalendarTimeSlotEvent.KIND,
                            CalendarRSVPEvent.KIND,
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
                JsonFilter(
                    kinds = listOf(GiftWrapEvent.KIND),
                    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
                ),
        )

    val accountChannel =
        requestNewChannel { time, relayUrl ->
            if (hasLoadedTheBasics[account.userProfile()] != null) {
                latestEOSEs.addOrUpdate(
                    account.userProfile(),
                    account.defaultNotificationFollowList.value,
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
        checkNotInMainThread()

        if (LocalCache.justVerify(event)) {
            when (event) {
                is DraftEvent -> {
                    // Avoid decrypting over and over again if the event already exist.

                    if (!event.isDeleted()) {
                        val note = LocalCache.getAddressableNoteIfExists(event.addressTag())
                        val noteEvent = note?.event
                        if (noteEvent != null) {
                            if (event.createdAt > noteEvent.createdAt() || relay.brief !in note.relays) {
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
                            noteEvent.cachedGift(account.signer) {
                                this.consume(it, relay)
                            }
                        }
                    } else {
                        // new event
                        event.cachedGift(account.signer) { this.consume(it, relay) }
                        LocalCache.justConsume(event, relay)
                    }
                }

                is SealedGossipEvent -> {
                    // Avoid decrypting over and over again if the event already exist.
                    val note = LocalCache.getNoteIfExists(event.id)
                    val noteEvent = note?.event as? SealedGossipEvent
                    if (noteEvent != null) {
                        if (relay.brief !in note.relays) {
                            // adds the relay to seal and inner chat
                            LocalCache.consume(noteEvent, relay)
                            noteEvent.cachedGossip(account.signer) {
                                LocalCache.justConsume(it, relay)
                            }
                        }
                    } else {
                        // new event
                        event.cachedGossip(account.signer) { LocalCache.justConsume(it, relay) }
                        LocalCache.justConsume(event, relay)
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
    }

    override fun markAsSeenOnRelay(
        eventId: String,
        relay: Relay,
    ) {
        checkNotInMainThread()

        super.markAsSeenOnRelay(eventId, relay)

        val note = LocalCache.getNoteIfExists(eventId) ?: return
        val privKey = account.keyPair.privKey ?: return

        val noteEvent = note.event ?: return
        markInnerAsSeenOnRelay(noteEvent, privKey, relay)
    }

    private fun markInnerAsSeenOnRelay(
        noteEvent: EventInterface,
        privKey: ByteArray,
        relay: Relay,
    ) {
        LocalCache.getNoteIfExists(noteEvent.id())?.addRelay(relay)

        if (noteEvent is GiftWrapEvent) {
            noteEvent.cachedGift(account.signer) { gift -> markInnerAsSeenOnRelay(gift, privKey, relay) }
        } else if (noteEvent is SealedGossipEvent) {
            noteEvent.cachedGossip(account.signer) { rumor ->
                markInnerAsSeenOnRelay(rumor, privKey, relay)
            }
        }
    }

    override fun updateChannelFilters() {
        return if (hasLoadedTheBasics[account.userProfile()] != null) {
            // gets everything about the user logged in
            accountChannel.typedFilters =
                listOfNotNull(
                    createAccountMetadataFilter(),
                    createAccountContactListFilter(),
                    createAccountRelayListFilter(),
                    createNotificationFilter(),
                    createNotificationFilter2(),
                    createGiftWrapsToMeFilter(),
                    createAccountReportsFilter(),
                    createAccountSettingsFilter(),
                    createAccountLastPostsListFilter(),
                    createOtherAccountsBaseFilter(),
                )
                    .ifEmpty { null }
        } else {
            // just the basics.
            accountChannel.typedFilters =
                listOf(
                    createAccountMetadataFilter(),
                    createAccountContactListFilter(),
                    createAccountRelayListFilter(),
                    createAccountSettingsFilter(),
                )
                    .ifEmpty { null }
        }
    }

    override fun auth(
        relay: Relay,
        challenge: String,
    ) {
        super.auth(relay, challenge)

        if (this::account.isInitialized) {
            account.createAuthEvent(relay, challenge) {
                Client.send(
                    it,
                    relay.url,
                )
            }
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
