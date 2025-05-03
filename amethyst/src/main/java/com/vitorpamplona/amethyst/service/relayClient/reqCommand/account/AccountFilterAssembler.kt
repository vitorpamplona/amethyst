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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relays.EOSERelayList
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.utils.TimeUtils

// This allows multiple screen to be listening to tags, even the same tag
class AccountQueryState(
    val account: Account,
    val otherAccounts: Set<HexKey>,
)

class AccountFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<AccountQueryState>(client) {
    val latestEOSE = EOSERelayList()
    var hasLoadedTheBasics: Boolean = false

    fun createAccountMetadataFilter(authorsHexes: List<String>): TypedFilter =
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
                    authors = authorsHexes,
                    limit = 20 * authorsHexes.size,
                ),
        )

    fun createOtherAccountsBaseFilter(otherAccounts: List<HexKey>): TypedFilter? {
        if (otherAccounts.isEmpty()) return null
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
                    authors = otherAccounts,
                    limit = otherAccounts.size * 20,
                ),
        )
    }

    fun createAccountSettingsFilter(authorsHexes: List<String>): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            PeopleListEvent.KIND,
                            FollowListEvent.KIND,
                            MuteListEvent.KIND,
                            BadgeProfilesEvent.KIND,
                            EmojiPackSelectionEvent.KIND,
                            EphemeralChatListEvent.KIND,
                            ChannelListEvent.KIND,
                        ),
                    authors = authorsHexes,
                    limit = 100 * authorsHexes.size,
                ),
        )

    fun createAccountSettings2Filter(authorsHexes: List<String>): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            AppSpecificDataEvent.KIND,
                        ),
                    authors = authorsHexes,
                    tags = mapOf("d" to listOf(Account.APP_SPECIFIC_DATA_D_TAG)),
                    limit = 2 * authorsHexes.size,
                ),
        )

    fun createAccountLastPostsListFilter(authorsHexes: List<String>): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    authors = authorsHexes,
                    limit = 500,
                ),
        )

    fun createAccountReportsAndDraftsFilter(authorsHexes: List<String>): TypedFilter =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            DraftEvent.KIND,
                            ReportEvent.KIND,
                            BookmarkListEvent.KIND,
                        ),
                    authors = authorsHexes,
                    since = latestEOSE.relayList,
                ),
        )

    fun createGiftWrapsToMeFilter(authorsHexes: List<String>) =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(GiftWrapEvent.KIND),
                    tags = mapOf("p" to authorsHexes),
                    since =
                        latestEOSE.relayList.mapValues {
                            EOSETime(it.value.time - TimeUtils.twoDays())
                        },
                ),
        )

    fun createNotificationFilter(authorsHexes: List<String>) =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            ReactionEvent.KIND,
                            RepostEvent.KIND,
                            GenericRepostEvent.KIND,
                            ReportEvent.KIND,
                            LnZapEvent.KIND,
                            LnZapPaymentResponseEvent.KIND,
                            ChannelMessageEvent.KIND,
                            EphemeralChatEvent.KIND,
                            BadgeAwardEvent.KIND,
                        ),
                    tags = mapOf("p" to authorsHexes),
                    limit = 4000,
                    since = latestEOSE.relayList,
                ),
        )

    fun createNotificationFilter2(authorsHexes: List<String>) =
        TypedFilter(
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
                    tags = mapOf("p" to authorsHexes),
                    limit = 400,
                    since = latestEOSE.relayList,
                ),
        )

    fun createNotificationFilter3(authorsHexes: List<String>) =
        TypedFilter(
            types = COMMON_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            PollNoteEvent.KIND,
                        ),
                    tags = mapOf("p" to authorsHexes),
                    limit = 100,
                    since = latestEOSE.relayList,
                ),
        )

    fun mergeAllFilters(
        mainAccounts: List<HexKey>,
        otherAccounts: List<HexKey>,
    ): List<TypedFilter>? =
        if (hasLoadedTheBasics) {
            // gets everything about the user logged in
            listOfNotNull(
                createAccountMetadataFilter(mainAccounts),
                createAccountSettings2Filter(mainAccounts),
                createNotificationFilter(mainAccounts),
                createNotificationFilter2(mainAccounts),
                createNotificationFilter3(mainAccounts),
                createGiftWrapsToMeFilter(mainAccounts),
                createAccountReportsAndDraftsFilter(mainAccounts),
                createAccountSettingsFilter(mainAccounts),
                createAccountLastPostsListFilter(mainAccounts),
                createOtherAccountsBaseFilter(otherAccounts),
            ).ifEmpty { null }
        } else {
            // just the basics.
            listOf(
                createAccountMetadataFilter(mainAccounts),
                createAccountSettingsFilter(mainAccounts),
                createAccountSettings2Filter(mainAccounts),
            ).ifEmpty { null }
        }

    val accountChannel =
        requestNewSubscription { time, relayUrl ->
            if (hasLoadedTheBasics) {
                latestEOSE.addOrUpdate(relayUrl, time)
            } else {
                hasLoadedTheBasics = true

                invalidateFilters()
            }
        }

    // One sub per subscribed account
    override fun updateSubscriptions(keys: Set<AccountQueryState>) {
        val mainAccounts = mutableSetOf<HexKey>()
        val otherAccounts = mutableSetOf<HexKey>()

        keys.forEach {
            mainAccounts.add(it.account.userProfile().pubkeyHex)
            otherAccounts.addAll(it.otherAccounts)
        }

        accountChannel.typedFilters = mergeAllFilters(mainAccounts.toList(), (otherAccounts - mainAccounts).toList())
    }
}
