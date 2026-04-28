/*
 * Copyright (c) 2025 Vitor Pamplona
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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.util.timeDiffAgoShortish
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.appendLink
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.graspLink
import com.vitorpamplona.amethyst.ui.note.nipLink
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.datasource.RelayInfoNip66FilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Height25Modifier
import com.vitorpamplona.amethyst.ui.theme.Size100dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.redColorOnSecondSurface
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.ErrorDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.IRelayDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.NoticeDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.SpamDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.draw.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.monitor.RelayMonitorEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val HTTPS_PREFIX = "https://"

@Composable
fun RelayInformationScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let {
        RelayInformationScreen(
            relay = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayInformationScreen(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                actions = {},
                title = {
                    Text(
                        relay.displayUrl(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row {
                        Spacer(modifier = StdHorzSpacer)
                        BackButton(
                            onPress = nav::popBack,
                        )
                    }
                },
            )
        },
    ) { pad ->
        RelayInfoNip66FilterAssemblerSubscription(relay, accountViewModel)

        val relayInfo by loadRelayInfo(relay)

        val discoveryEvents by loadRelayDiscoveryEvents(relay)

        val messages =
            remember(relay) {
                Amethyst.instance.relayStats
                    .get(url = relay)
                    .messages
                    .snapshot()
                    .values
                    .sortedByDescending { it.time }
                    .toImmutableList()
            }

        RelayInformationBody(relay, relayInfo, discoveryEvents, Amethyst.instance.relayStats.get(relay), messages, pad, accountViewModel, nav)
    }
}

@Composable
fun RelayInformationBody(
    relay: NormalizedRelayUrl,
    relayInfo: Nip11RelayInformation,
    discoveryEvents: ImmutableList<RelayDiscoveryEvent>,
    relayStats: RelayStat,
    messages: ImmutableList<IRelayDebugMessage>,
    pad: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val activeReqs = remember(relay) { accountViewModel.account.client.activeRequests(relay) }
    val activeCounts = remember(relay) { accountViewModel.account.client.activeCounts(relay) }
    val activeOutbox = remember(relay) { accountViewModel.account.client.activeOutboxCache(relay) }

    val usedBy =
        remember(relay) {
            accountViewModel.account.declaredFollowsPerUsingRelay.value[relay]?.mapNotNull { hex ->
                LocalCache.checkGetOrCreateUser(hex)
            } ?: emptyList()
        }

    LazyColumn(
        modifier =
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Header Section
        item {
            RelayHeader(relay, relayStats, relayInfo, accountViewModel, nav)
        }

        val targetAudience =
            relayInfo.tags != null ||
                relayInfo.language_tags != null ||
                relayInfo.relay_countries != null

        if (targetAudience) {
            item { SectionHeader(stringRes(R.string.target_audience)) }
            item { TargetAudienceCard(relayInfo, nav) }
        }

        relayInfo.pubkey?.let {
            item {
                SectionHeader(stringRes(R.string.owner))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        relayInfo.self?.let {
            item {
                SectionHeader(stringRes(R.string.self))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        item { SectionHeader(stringRes(R.string.policies_and_links)) }
        item { PoliciesCard(relayInfo) }

        relayInfo.fees?.let { fees ->
            item { SectionHeader(stringRes(R.string.fees_and_payments)) }
            item { FeesCard(fees, relayInfo.payments_url) }
        }

        relayInfo.limitation?.let {
            item { SectionHeader(stringRes(R.string.limitations)) }
            item { LimitationsCard(it) }
        }

        val atLeastOneSoftware =
            relayInfo.software != null ||
                relayInfo.version != null ||
                relayInfo.supported_grasps != null ||
                relayInfo.supported_nips != null

        if (atLeastOneSoftware) {
            item { SectionHeader(stringRes(R.string.software)) }
            item { SoftwareCard(relayInfo) }
        }

        if (discoveryEvents.isNotEmpty()) {
            item { SectionHeader(stringRes(R.string.relay_monitor_reports)) }
            items(discoveryEvents, key = { it.addressTag() }) { event ->
                RelayMonitorReportCard(event, accountViewModel, nav)
            }
        }

        if (usedBy.isNotEmpty()) {
            item {
                SectionHeader(stringRes(R.string.used_by))
            }
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    FlowRow(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                        usedBy.take(30).forEach {
                            UserPicture(
                                user = it,
                                size = Size25dp,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                        if (usedBy.size > 30) {
                            Box(contentAlignment = Alignment.Center, modifier = Height25Modifier) {
                                Text(
                                    text = stringRes(R.string.and_more, usedBy.size - 30),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active subscriptions and outbox section
        val hasReqs = activeReqs.isNotEmpty()
        val hasCounts = activeCounts.isNotEmpty()
        val hasOutbox = activeOutbox.isNotEmpty()

        if (hasReqs || hasCounts || hasOutbox) {
            item { SectionHeader(stringRes(R.string.relay_active_subscriptions)) }

            if (hasReqs) {
                item {
                    Text(
                        text = stringRes(R.string.relay_req_subscriptions, activeReqs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(activeReqs.entries.toList(), key = { "req_${it.key}" }) { (subId, filters) ->
                    SubscriptionCard(subId = subId, filters = filters)
                    Spacer(modifier = StdVertSpacer)
                }
            }

            if (hasCounts) {
                item {
                    Text(
                        text = stringRes(R.string.relay_count_subscriptions, activeCounts.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp, top = if (hasReqs) 8.dp else 0.dp),
                    )
                }
                items(activeCounts.entries.toList(), key = { "count_${it.key}" }) { (subId, filters) ->
                    SubscriptionCard(subId = subId, filters = filters)
                    Spacer(modifier = StdVertSpacer)
                }
            }

            if (hasOutbox) {
                item {
                    Text(
                        text = stringRes(R.string.relay_outbox_events, activeOutbox.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 4.dp, top = if (hasReqs || hasCounts) 8.dp else 0.dp),
                    )
                }
                item {
                    OutboxEventsCard(eventIds = activeOutbox)
                }
            }
        }

        item {
            SectionHeader(stringRes(R.string.relay_error_messages))
        }

        items(messages) { msg ->
            RenderDebugMessage(msg)

            Spacer(modifier = StdVertSpacer)
        }
    }
}

// ---------------------------------------------------------------------------
// Active subscriptions + outbox display
// ---------------------------------------------------------------------------

@Suppress("DEPRECATION")
fun kindDisplayName(kind: Int): Int =
    when (kind) {
        AcceptedBadgeSetEvent.KIND -> R.string.kind_accepted_badge_set
        AdvertisedRelayListEvent.KIND -> R.string.kind_outbox_relays
        AppDefinitionEvent.KIND -> R.string.kind_apps
        AppRecommendationEvent.KIND -> R.string.kind_app_recommendations
        AppSpecificDataEvent.KIND -> R.string.kind_user_settings
        AudioHeaderEvent.KIND -> R.string.kind_audio_header
        AudioTrackEvent.KIND -> R.string.kind_audio_track
        AttestationEvent.KIND -> R.string.attestation
        AttestationRequestEvent.KIND -> R.string.attestation_request
        AttestorRecommendationEvent.KIND -> R.string.attestor_recommendation
        AttestorProficiencyEvent.KIND -> R.string.attestor_proficiency
        BadgeAwardEvent.KIND -> R.string.kind_badge_awards
        BadgeDefinitionEvent.KIND -> R.string.kind_badge_definitions
        BlockedRelayListEvent.KIND -> R.string.kind_blocked_relays
        BlossomServersEvent.KIND -> R.string.kind_blossom_servers
        NestsServersEvent.KIND -> R.string.kind_nests_servers
        BlossomAuthorizationEvent.KIND -> R.string.kind_blossom_auth
        BroadcastRelayListEvent.KIND -> R.string.kind_broadcast_relays
        BookmarkListEvent.KIND -> R.string.kind_bookmark_list
        OldBookmarkListEvent.KIND -> R.string.kind_old_bookmark_list
        CalendarDateSlotEvent.KIND -> R.string.kind_day_appointment
        CalendarEvent.KIND -> R.string.kind_calendar
        CalendarTimeSlotEvent.KIND -> R.string.kind_appointment
        CalendarRSVPEvent.KIND -> R.string.kind_appt_rsvp
        ChessGameEvent.KIND -> R.string.kind_chess_games
        JesterEvent.KIND -> R.string.kind_chess_auth
        RelayFeedsListEvent.KIND -> R.string.kind_favorite_relays
        LiveChessGameChallengeEvent.KIND -> R.string.kind_chess_challenges
        LiveChessGameAcceptEvent.KIND -> R.string.kind_chess_game_accept
        LiveChessMoveEvent.KIND -> R.string.kind_chess_move
        LiveChessGameEndEvent.KIND -> R.string.kind_chess_game_end
        LiveChessDrawOfferEvent.KIND -> R.string.kind_chess_draw_offer
        ChannelCreateEvent.KIND -> R.string.kind_channel_definition
        ChannelHideMessageEvent.KIND -> R.string.kind_channel_hide_msg
        ChannelListEvent.KIND -> R.string.kind_channel_list
        ChannelMessageEvent.KIND -> R.string.kind_channel_message
        ChannelMetadataEvent.KIND -> R.string.kind_channel_metadata
        ChannelMuteUserEvent.KIND -> R.string.kind_channel_mute_user
        ChatMessageEncryptedFileHeaderEvent.KIND -> R.string.kind_dm_file
        ChatMessageEvent.KIND -> R.string.kind_dm_message
        ChatMessageRelayListEvent.KIND -> R.string.kind_dm_relays
        ClassifiedsEvent.KIND -> R.string.kind_classifieds
        CommentEvent.KIND -> R.string.kind_comments
        CommunityDefinitionEvent.KIND -> R.string.kind_community_def
        CommunityListEvent.KIND -> R.string.kind_community_list
        CommunityPostApprovalEvent.KIND -> R.string.kind_community_post
        ContactListEvent.KIND -> R.string.kind_follow_list
        DeletionEvent.KIND -> R.string.kind_deletions
        DraftWrapEvent.KIND -> R.string.kind_drafts
        EmojiPackEvent.KIND -> R.string.kind_emoji_packs
        EmojiPackSelectionEvent.KIND -> R.string.kind_emoji_pack_list
        EphemeralChatEvent.KIND -> R.string.kind_ephemeral_chat
        EphemeralChatListEvent.KIND -> R.string.kind_ephemeral_chatrooms
        FileHeaderEvent.KIND -> R.string.kind_file_headers
        ProfileGalleryEntryEvent.KIND -> R.string.kind_profile_gallery
        FileServersEvent.KIND -> R.string.kind_file_servers
        FileStorageEvent.KIND -> R.string.kind_blob_data
        FileStorageHeaderEvent.KIND -> R.string.kind_blob_headers
        FhirResourceEvent.KIND -> R.string.kind_medical_data
        FollowListEvent.KIND -> R.string.kind_follow_packs
        GenericRepostEvent.KIND -> R.string.kind_reposts_16
        GeohashListEvent.KIND -> R.string.kind_geohash_follows
        GiftWrapEvent.KIND -> R.string.kind_gift_wraps
        EphemeralGiftWrapEvent.KIND -> R.string.kind_gift_wraps
        GitIssueEvent.KIND -> R.string.kind_git_issue
        GitPatchEvent.KIND -> R.string.kind_git_patch
        GitRepositoryEvent.KIND -> R.string.kind_git_repo
        GitReplyEvent.KIND -> R.string.kind_git_reply
        GoalEvent.KIND -> R.string.kind_zap_goals
        HashtagListEvent.KIND -> R.string.kind_hashtag_follows
        HighlightEvent.KIND -> R.string.kind_highlights
        HTTPAuthorizationEvent.KIND -> R.string.kind_http_auth
        IndexerRelayListEvent.KIND -> R.string.kind_index_relay_list
        InteractiveStoryPrologueEvent.KIND -> R.string.kind_adventure_prologue
        InteractiveStorySceneEvent.KIND -> R.string.kind_adventure_scene
        InteractiveStoryReadingStateEvent.KIND -> R.string.kind_adventure_reading
        LabeledBookmarkListEvent.KIND -> R.string.kind_named_bookmarks
        LiveActivitiesChatMessageEvent.KIND -> R.string.kind_live_chats
        LiveActivitiesEvent.KIND -> R.string.kind_live_streams
        LnZapEvent.KIND -> R.string.kind_zaps
        LnZapPaymentRequestEvent.KIND -> R.string.kind_nwc_request
        LnZapPaymentResponseEvent.KIND -> R.string.kind_nwc_response
        LnZapPrivateEvent.KIND -> R.string.kind_private_zaps
        LnZapRequestEvent.KIND -> R.string.kind_zap_req
        LongTextNoteEvent.KIND -> R.string.kind_blogs
        MeetingRoomEvent.KIND -> R.string.kind_meeting_room
        MeetingRoomPresenceEvent.KIND -> R.string.kind_room_presence
        MeetingSpaceEvent.KIND -> R.string.kind_meeting_space
        MetadataEvent.KIND -> R.string.kind_profile
        MuteListEvent.KIND -> R.string.kind_mute_list
        NNSEvent.KIND -> R.string.kind_nns
        NipTextEvent.KIND -> R.string.kind_nip
        NostrConnectEvent.KIND -> R.string.kind_nostr_connect
        NIP90StatusEvent.KIND -> R.string.kind_dvm_status
        NIP90ContentDiscoveryRequestEvent.KIND -> R.string.kind_dvm_content_req
        NIP90ContentDiscoveryResponseEvent.KIND -> R.string.kind_dvm_content_resp
        NIP90UserDiscoveryRequestEvent.KIND -> R.string.kind_dvm_user_req
        NIP90UserDiscoveryResponseEvent.KIND -> R.string.kind_dvm_user_resp
        OtsEvent.KIND -> R.string.kind_ots
        PaymentTargetsEvent.KIND -> R.string.kind_pay_to
        PeopleListEvent.KIND -> R.string.kind_people_lists
        ProfileBadgesEvent.KIND -> R.string.kind_profile_badges
        PictureEvent.KIND -> R.string.kind_pictures
        PinListEvent.KIND -> R.string.kind_pins
        ZapPollEvent.KIND -> R.string.kind_zap_poll
        PollEvent.KIND -> R.string.kind_poll
        PollResponseEvent.KIND -> R.string.kind_poll_response
        PrivateDmEvent.KIND -> R.string.kind_nip04_dms
        PrivateOutboxRelayListEvent.KIND -> R.string.kind_private_relays
        ProxyRelayListEvent.KIND -> R.string.kind_proxy_relays
        PublicMessageEvent.KIND -> R.string.kind_public_message
        ReactionEvent.KIND -> R.string.kind_reactions
        ContactCardEvent.KIND -> R.string.kind_contact_card
        RelayAuthEvent.KIND -> R.string.kind_relay_auth
        RelayDiscoveryEvent.KIND -> R.string.kind_relay_discovery
        RelayMonitorEvent.KIND -> R.string.kind_relay_monitor
        RelaySetEvent.KIND -> R.string.kind_relay_set
        ReportEvent.KIND -> R.string.kind_reports
        RepostEvent.KIND -> R.string.kind_reposts
        RequestToVanishEvent.KIND -> R.string.kind_user_delete
        SealedRumorEvent.KIND -> R.string.kind_seals
        SearchRelayListEvent.KIND -> R.string.kind_search_relays
        StatusEvent.KIND -> R.string.kind_user_status
        TextNoteEvent.KIND -> R.string.kind_notes
        TextNoteModificationEvent.KIND -> R.string.kind_edits
        TorrentEvent.KIND -> R.string.kind_torrents
        TorrentCommentEvent.KIND -> R.string.kind_torrent_comments
        TrustedRelayListEvent.KIND -> R.string.kind_trusted_relays
        TrustProviderListEvent.KIND -> R.string.kind_trusted_providers
        VideoHorizontalEvent.KIND -> R.string.kind_video_repl
        VideoVerticalEvent.KIND -> R.string.kind_shorts_repl
        VideoNormalEvent.KIND -> R.string.kind_video
        VideoShortEvent.KIND -> R.string.kind_shorts
        VoiceEvent.KIND -> R.string.kind_voice_msg
        VoiceReplyEvent.KIND -> R.string.kind_voice_reply
        WakeUpEvent.KIND -> R.string.kind_wake
        WebBookmarkEvent.KIND -> R.string.kind_web_bookmark
        WikiNoteEvent.KIND -> R.string.kind_wiki
        else -> -1
    }

val posts = setOf(0, 1, 6, 7, 16, 30023)
val settings = setOf(3, 10002, 10000, 10001, 10003, 10004, 30000)
val dms = setOf(4, GiftWrapEvent.KIND, EphemeralGiftWrapEvent.KIND, 10050)
val zaps = setOf(9734, 9735, 9041, 17375, 23194, 23195)
val reports = setOf(ReportEvent.KIND, MuteListEvent.KIND, DeletionEvent.KIND, RequestToVanishEvent.KIND)

@Composable
fun KindChip(kind: Int) {
    val nameResId = kindDisplayName(kind)
    val name = if (nameResId != -1) stringResource(nameResId) else "k$kind"
    val (bg, fg) =
        when (kind) {
            in posts -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            //
            in settings -> {
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }

            // dms
            in dms -> {
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }

            // zaps
            in zaps -> {
                MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.bitcoinColor
            }

            in reports -> {
                MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.redColorOnSecondSurface
            }

            else -> {
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = fg,
        )
    }
}

@Composable
private fun FilterAttributeChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
        )
    }
}

@Composable
private fun FilterVisual(
    filter: Filter,
    index: Int,
) {
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "filter ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Kinds
                filter.kinds?.forEach { kind ->
                    KindChip(kind)
                }

                // Authors
                filter.authors?.let { authors ->
                    if (authors.isNotEmpty()) {
                        FilterAttributeChip(
                            text = "👤 ${stringRes(R.string.relay_filter_authors, authors.size)}",
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                // IDs
                filter.ids?.let { ids ->
                    if (ids.isNotEmpty()) {
                        FilterAttributeChip(
                            text = "🆔 ${stringRes(R.string.relay_filter_ids, ids.size)}",
                        )
                    }
                }

                // Tags
                filter.tags?.forEach { (tagName, values) ->
                    if (values.isNotEmpty()) {
                        FilterAttributeChip(
                            text =
                                when {
                                    values.size == 1 -> {
                                        if (values[0].length > 8) {
                                            "#$tagName:${values[0].take(8)}..."
                                        } else {
                                            "#$tagName:${values[0]}"
                                        }
                                    }

                                    else -> {
                                        "#$tagName ×${values.size}"
                                    }
                                },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // tagsAll
                filter.tagsAll?.forEach { (tagName, values) ->
                    if (values.isNotEmpty()) {
                        FilterAttributeChip(
                            text =
                                when {
                                    values.size == 1 -> {
                                        if (values[0].length > 8) {
                                            "&$tagName:${values[0].take(8)}..."
                                        } else {
                                            "&$tagName:${values[0]}"
                                        }
                                    }

                                    else -> {
                                        "&$tagName ×${values.size}"
                                    }
                                },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                // Since
                filter.since?.let { since ->
                    FilterAttributeChip(
                        text = stringRes(R.string.relay_filter_since, timeAgoNoDot(since, context)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Until
                filter.until?.let { until ->
                    FilterAttributeChip(
                        text = stringRes(R.string.relay_filter_until, timeAgoNoDot(until, context)),
                    )
                }

                // Limit
                filter.limit?.let { limit ->
                    FilterAttributeChip(
                        text = stringRes(R.string.relay_filter_limit, limit),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }

                // Search
                filter.search?.let { search ->
                    if (search.isNotEmpty()) {
                        FilterAttributeChip(
                            text = "🔍 ${search.take(20)}${if (search.length > 20) "…" else ""}",
                            color = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subId: String,
    filters: List<Filter>,
) {
    val displayId =
        if (subId.length > 28) {
            subId.take(22) + "…" + subId.takeLast(4)
        } else {
            subId
        }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayId,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${filters.size}f",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            filters.forEachIndexed { index, filter ->
                FilterVisual(filter = filter, index = index)
            }
        }
    }
}

@Composable
private fun OutboxEventsCard(eventIds: Set<HexKey>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            eventIds.forEach { eventId ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = eventId.take(8) + "…" + eventId.takeLast(4),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDebugMessage(msg: IRelayDebugMessage) {
    val context = LocalContext.current

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Timestamp with Monospace font for alignment
            Text(
                text = timeAgoNoDot(msg.time, context),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            // Type Tag
            Text(
                text =
                    when (msg) {
                        is ErrorDebugMessage -> stringRes(R.string.errors)
                        is NoticeDebugMessage -> stringRes(R.string.relay_notice)
                        is SpamDebugMessage -> stringRes(R.string.spam)
                    },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                color =
                    when (msg) {
                        is ErrorDebugMessage -> MaterialTheme.colorScheme.error
                        is NoticeDebugMessage -> MaterialTheme.colorScheme.primary
                        is SpamDebugMessage -> MaterialTheme.colorScheme.outline
                    },
            )
        }

        when (msg) {
            is ErrorDebugMessage -> {
                SelectionContainer {
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            is NoticeDebugMessage -> {
                SelectionContainer {
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            is SpamDebugMessage -> {
                SelectionContainer {
                    val uri = LocalUriHandler.current
                    val start = stringRes(R.string.duplicated_post)
                    Text(
                        text =
                            remember {
                                buildAnnotatedString {
                                    append(start)
                                    append(" ")
                                    appendLink(msg.link1) {
                                        runCatching {
                                            uri.openUri(msg.link1)
                                        }
                                    }
                                    appendLink(msg.link2) {
                                        runCatching {
                                            uri.openUri(msg.link2)
                                        }
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayOwnerInformation(
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = userHex, accountViewModel) { loadedUser ->
        CrossfadeIfEnabled(loadedUser, accountViewModel = accountViewModel) {
            if (it != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    UserCompose(
                        baseUser = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayHeader(
    relay: NormalizedRelayUrl,
    relayStats: RelayStat,
    relayInfo: Nip11RelayInformation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = SpacedBy10dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        RenderRelayIcon(
            displayUrl = relay.displayUrl(),
            iconUrl = relayInfo.icon,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            pingInMs = relayStats.pingInMs,
            iconModifier =
                Modifier
                    .size(Size100dp)
                    .clip(shape = CircleShape),
        )
        Text(
            text = relayInfo.description ?: relay.displayUrl(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                shape = ButtonBorder,
                onClick = { nav.nav(Route.RelayFeed(url = relay.url)) },
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Feed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringRes(R.string.see_relay_feed))
            }

            if (supportsNip43(relayInfo.supported_nips)) {
                OutlinedButton(
                    onClick = { nav.nav(Route.RelayMembers(relay.url)) },
                    shape = ButtonBorder,
                ) {
                    Icon(
                        MaterialSymbols.People,
                        contentDescription = stringRes(R.string.relay_members),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(R.string.relay_members))
                }
            }

            if (Nip86Client.supportsNip86(relayInfo.supported_nips)) {
                OutlinedButton(
                    onClick = { nav.nav(Route.RelayManagement(relay.url)) },
                    shape = ButtonBorder,
                ) {
                    Icon(
                        MaterialSymbols.Settings,
                        contentDescription = stringRes(R.string.manage),
                        modifier = Height25Modifier,
                    )
                }
            }
        }
    }
}

fun supportsNip43(supportedNips: List<String>?): Boolean = supportedNips?.any { it == "43" } == true

@Composable
fun FeesCard(
    fees: Nip11RelayInformation.RelayInformationFees,
    payUrl: String?,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            fees.admission?.forEach {
                FeeRow(stringRes(R.string.admission), it)
            }
            fees.subscription?.forEach {
                FeeRow(stringRes(R.string.subscription), it)
            }
            fees.publication?.forEach {
                FeeRow(stringRes(R.string.publication), it)
            }
            payUrl?.let {
                val uri = LocalUriHandler.current
                ClickableInfoRow(MaterialSymbols.Payment, stringRes(R.string.payments_url), it.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }
        }
    }
}

@Composable
fun LimitationsCard(lim: Nip11RelayInformation.RelayInformationLimitation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val atLeastOneAccessControl =
                lim.auth_required != null ||
                    lim.payment_required != null ||
                    lim.restricted_writes != null ||
                    lim.min_pow_difficulty != null ||
                    lim.min_prefix != null

            if (atLeastOneAccessControl) {
                Column {
                    Text(stringRes(R.string.access_control), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val yes = stringRes(R.string.yes)
                    val no = stringRes(R.string.no)

                    lim.auth_required?.let {
                        InfoRow(MaterialSymbols.History, stringRes(R.string.auth_required), if (it) yes else no)
                    }
                    lim.payment_required?.let {
                        InfoRow(MaterialSymbols.Lock, stringRes(R.string.payment_required), if (it) yes else no)
                    }
                    lim.restricted_writes?.let {
                        InfoRow(MaterialSymbols.EditOff, stringRes(R.string.restricted_writes), if (it) yes else no)
                    }

                    val minPoW = lim.min_pow_difficulty

                    if (minPoW != null && minPoW > 0) {
                        InfoRow(MaterialSymbols.Bolt, stringRes(R.string.minimum_pow), stringRes(R.string.amount_in_bits, minPoW))
                    } else {
                        lim.min_prefix?.let {
                            if (it > 0) {
                                InfoRow(MaterialSymbols.Key, stringRes(R.string.minimum_prefix), stringRes(R.string.amount_in_bits, it * 8))
                            }
                        }
                    }
                }
            }

            val atLeastOneConnectivity =
                lim.max_message_length.isNotNullAndNotZero() ||
                    lim.max_subscriptions.isNotNullAndNotZero() ||
                    lim.max_filters.isNotNullAndNotZero() ||
                    lim.max_limit.isNotNullAndNotZero() ||
                    lim.default_limit.isNotNullAndNotZero() ||
                    lim.max_subid_length.isNotNullAndNotZero()

            if (atLeastOneConnectivity) {
                Column {
                    Text(stringRes(R.string.connectivity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_message_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Message, stringRes(R.string.max_message_length), "${it / 1024} kb")
                    }
                    lim.max_subscriptions?.let {
                        InfoRow(MaterialSymbols.Dns, stringRes(R.string.max_subs), it.toString())
                    }
                    lim.max_filters?.let {
                        InfoRow(MaterialSymbols.FilterAlt, stringRes(R.string.max_filters_per_sub), it.toString())
                    }
                    lim.max_limit?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.List, stringRes(R.string.max_limit_events_returning), it.toString())
                    }
                    lim.default_limit?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.List, stringRes(R.string.max_limit_events_returning), it.toString())
                    }
                    lim.max_subid_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Label, stringRes(R.string.max_subid_length), it.toString())
                    }
                }
            }

            val atLeastOneContentSize =
                lim.max_event_tags != null ||
                    lim.max_content_length != null

            if (atLeastOneContentSize) {
                Column {
                    Text(stringRes(R.string.content_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_event_tags?.let {
                        InfoRow(MaterialSymbols.Tag, stringRes(R.string.maximum_event_tags), it.toString())
                    }

                    lim.max_content_length?.let {
                        InfoRow(MaterialSymbols.AutoMirrored.Article, stringRes(R.string.max_content_length), "${it / 1024} kb")
                    }
                }
            }

            val atLeastOneRestriction =
                lim.created_at_lower_limit.isNotNullAndNotZero() ||
                    lim.created_at_upper_limit.isNotNullAndNotZero()

            if (atLeastOneRestriction) {
                Column {
                    Text(stringRes(R.string.event_retention), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.created_at_lower_limit?.let {
                        if (it > 0) {
                            InfoRow(MaterialSymbols.History, stringRes(R.string.discards_older_than), stringRes(R.string.time_in_the_past, timeDiffAgoShortish(it)))
                        }
                    }
                    lim.created_at_upper_limit?.let {
                        if (it > 0) {
                            InfoRow(MaterialSymbols.History, stringRes(R.string.accepts_up_to), stringRes(R.string.time_in_the_future, timeDiffAgoShortish(it)))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
fun Int?.isNotNullAndNotZero(): Boolean {
    contract {
        returns(true) implies (this@isNotNullAndNotZero != null)
    }

    return this != null && this != 0
}

@Composable
fun SoftwareCard(relayInfo: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relayInfo.software?.let {
                if (it.contains(HTTPS_PREFIX)) {
                    ClickableInfoRow(MaterialSymbols.Code, stringRes(R.string.software), it.removePrefix("git+https://").removePrefix(HTTPS_PREFIX)) {
                        runCatching {
                            uri.openUri(it.removePrefix("git+"))
                        }
                    }
                } else {
                    InfoRow(MaterialSymbols.Code, stringRes(R.string.software), it)
                }
            }

            relayInfo.version?.let {
                InfoRow(MaterialSymbols.Storage, stringRes(R.string.version), it)
            }

            relayInfo.supported_nips?.let { nips ->
                Text(
                    stringRes(R.string.supports),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nips.forEach { nip ->
                        val nipStr = nip.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(nipLink(nipStr))
                                }
                            },
                            label = {
                                Text(nipStr)
                            },
                        )
                    }
                }
            }

            relayInfo.supported_grasps?.let { grasps ->
                Text(
                    stringRes(R.string.supported_grasps),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grasps.forEach { grasp ->
                        val graspStr = grasp.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(graspLink(graspStr))
                                }
                            },
                            label = {
                                Text(graspStr)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAudienceCard(
    relay: Nip11RelayInformation,
    nav: INav,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            relay.tags?.let { tags ->
                if (tags.size > 2) {
                    Column {
                        Text(stringRes(R.string.topics), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { nav.nav(Route.Hashtag(tag)) },
                                    label = {
                                        Text(tag)
                                    },
                                )
                            }
                        }
                    }
                } else if (tags.isNotEmpty()) {
                    InfoRow(MaterialSymbols.Topic, stringRes(R.string.topics), tags.joinToString())
                }
            }
            relay.relay_countries?.let { countries ->
                val allCountries = stringRes(R.string.all_countries)
                if (countries.size > 2) {
                    Column {
                        Text(stringRes(R.string.countries), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            countries.forEach { country ->
                                if (country == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allCountries)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(country)
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (countries.isNotEmpty()) {
                    InfoRow(
                        MaterialSymbols.Language,
                        stringRes(R.string.countries),
                        countries.joinToString {
                            if (it == "*") allCountries else it
                        },
                    )
                }
            }
            relay.language_tags?.let { languages ->
                val allLang = stringRes(R.string.all_languages)
                if (languages.size > 2) {
                    Column {
                        Text(stringRes(R.string.languages), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languages.forEach { lang ->
                                if (lang == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allLang)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(lang)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (languages.isNotEmpty()) {
                    InfoRow(
                        MaterialSymbols.Translate,
                        stringRes(R.string.languages),
                        languages.joinToString {
                            if (it == "*") allLang else it
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PoliciesCard(relay: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relay.contact?.let {
                if (it.contains("@")) {
                    ClickableInfoRow(MaterialSymbols.AutoMirrored.ContactSupport, stringRes(R.string.contact), it) {
                        runCatching {
                            uri.openUri("mailto:$it")
                        }
                    }
                } else {
                    InfoRow(MaterialSymbols.AutoMirrored.ContactSupport, stringRes(R.string.contact), it)
                }
            }

            relay.posting_policy?.let {
                ClickableInfoRow(MaterialSymbols.EditNote, stringRes(R.string.posting_policy), it) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }

            val pp = relay.privacy_policy

            if (pp != null) {
                ClickableInfoRow(MaterialSymbols.PrivacyTip, stringRes(R.string.privacy_policy), pp.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(pp)
                    }
                }
            } else {
                InfoRow(MaterialSymbols.PrivacyTip, stringRes(R.string.privacy_policy), stringRes(R.string.not_available_acronym))
            }

            val ts = relay.terms_of_service
            if (ts != null) {
                ClickableInfoRow(MaterialSymbols.Gavel, stringRes(R.string.terms_and_conditions), ts.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(ts)
                    }
                }
            } else {
                InfoRow(MaterialSymbols.Gavel, stringRes(R.string.terms_and_conditions), stringRes(R.string.not_available_acronym))
            }
        }
    }
}

@Composable
fun loadRelayDiscoveryEvents(relay: NormalizedRelayUrl): State<ImmutableList<RelayDiscoveryEvent>> =
    remember(relay) {
        LocalCache
            .observeEvents<RelayDiscoveryEvent>(
                Filter(
                    kinds = listOf(RelayDiscoveryEvent.KIND),
                    tags = mapOf("d" to listOf(relay.url)),
                    since = TimeUtils.oneWeekAgo(),
                    limit = 3,
                ),
            ).map {
                it.toImmutableList()
            }
    }.collectAsStateWithLifecycle(persistentListOf())

@Composable
private fun RelayMonitorReportCard(
    event: RelayDiscoveryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Monitor author + timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LoadUser(baseUserHex = event.pubKey, accountViewModel) { user ->
                    if (user != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            UserPicture(
                                user = user,
                                size = Size25dp,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                            UsernameDisplay(user, weight = Modifier.weight(1f), accountViewModel = accountViewModel)
                        }
                    }
                }

                Text(
                    text = timeAgoNoDot(event.createdAt, context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            HorizontalDivider()

            // RTT metrics
            val rttOpen = event.rttOpen()
            val rttRead = event.rttRead()
            val rttWrite = event.rttWrite()

            if (rttOpen != null || rttRead != null || rttWrite != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rttOpen?.let {
                        RttChip(stringRes(R.string.relay_monitor_rtt_open), it)
                    }
                    rttRead?.let {
                        RttChip(stringRes(R.string.relay_monitor_rtt_read), it)
                    }
                    rttWrite?.let {
                        RttChip(stringRes(R.string.relay_monitor_rtt_write), it)
                    }
                }
            }

            // Network type
            val networkTypes = event.networkTypes()
            if (networkTypes.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Language,
                    stringRes(R.string.relay_monitor_network),
                    networkTypes.joinToString { it.code },
                )
            }

            // Relay type
            val relayTypes = event.relayTypes()
            if (relayTypes.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Dns,
                    stringRes(R.string.relay_monitor_relay_type),
                    relayTypes.joinToString(),
                )
            }

            // Requirements
            val requirements = event.requirements()
            if (requirements.isNotEmpty()) {
                InfoRow(
                    MaterialSymbols.Lock,
                    stringRes(R.string.relay_monitor_requirements),
                    requirements.joinToString { req ->
                        if (req.negated) "!${req.value}" else req.value
                    },
                )
            }
        }
    }
}

@Composable
private fun RttChip(
    label: String,
    ms: Long,
) {
    val color =
        when {
            ms < 200 -> Color(0xFF4CAF50)

            // green
            ms < 500 -> Color(0xFFFFC107)

            // amber
            else -> MaterialTheme.colorScheme.error
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.15f),
        ) {
            Text(
                text = stringRes(R.string.relay_monitor_ms, ms.toInt()),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoRow(
    icon: MaterialSymbol,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
private fun ClickableInfoRow(
    icon: MaterialSymbol,
    label: String,
    value: String,
    onClickValue: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClickValue).weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
fun FeeRow(
    label: String,
    fee: Nip11RelayInformation.RelayInformationFee,
) {
    fee.amount?.let {
        val period = fee.period
        val combinedLabel =
            if (period != null) {
                label + " (${timeDiffAgoShortish(period)})"
            } else {
                label
            }

        if (fee.unit == "msats") {
            InfoRow(MaterialSymbols.AttachMoney, combinedLabel, "${it / 1000} sats")
        } else {
            InfoRow(MaterialSymbols.AttachMoney, combinedLabel, "$it ${fee.unit}")
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Nost.wine Relay Info", device = "spec:width=2160px,height=5640px,dpi=440")
fun RelayHeaderPreview() {
    ThemeComparisonRow {
        RelayInformationBody(
            relay = NormalizedRelayUrl("wss://nostr.wine/"),
            relayInfo =
                Nip11RelayInformation(
                    name = "Nostr.wine",
                    icon = "https://image.nostr.build/30acdce4a81926f386622a07343228ae99fa68d012d54c538c0b2129dffe400c.png",
                    description = "A paid nostr relay for wine enthusiasts and everyone else",
                    software = "https://nostr.wine",
                    contact = "wino@nostr.wine",
                    version = "0.3.3",
                    self = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    pubkey = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    payments_url = "https://nostr.wine/invoices",
                    privacy_policy = "https://nostr.wine/terms",
                    terms_of_service = "https://nostr.wine/terms",
                    tags = listOf("Bitcoin", "Amethyst"),
                    supported_nips = listOf("1", "2", "4", "9", "11", "40", "42", "50", "70", "77"),
                    relay_countries = listOf("*"),
                    language_tags = listOf("*"),
                    limitation =
                        Nip11RelayInformation.RelayInformationLimitation(
                            auth_required = false,
                            created_at_lower_limit = 94608000,
                            created_at_upper_limit = 300,
                            max_event_tags = 4000,
                            max_limit = 1000,
                            default_limit = 20,
                            max_message_length = 524288,
                            max_subid_length = 71,
                            max_subscriptions = 50,
                            min_pow_difficulty = 0,
                            payment_required = true,
                            restricted_writes = true,
                        ),
                    fees =
                        Nip11RelayInformation.RelayInformationFees(
                            admission =
                                listOf(
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 3000,
                                        unit = "msats",
                                        period = 2628003,
                                    ),
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 8000,
                                        unit = "sats",
                                        period = 7884009,
                                    ),
                                ),
                        ),
                    supported_grasps = listOf("GRASP-01"),
                ),
            discoveryEvents = persistentListOf(),
            pad = PaddingValues(0.dp),
            relayStats = RelayStat(),
            messages =
                persistentListOf(
                    NoticeDebugMessage(
                        time = TimeUtils.now(),
                        message = "Subscription closed: AccountNotificationsEoseFromRandomRelaysManagerugZU9o auth-required: At least one matching event requires AUTH",
                    ),
                    ErrorDebugMessage(
                        time = TimeUtils.now() - 24000,
                        message = "No such subscription",
                    ),
                    SpamDebugMessage(
                        time = TimeUtils.now() - 24000,
                        link1 = "http://test1.com",
                        link2 = "http://test2.com",
                    ),
                ),
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
