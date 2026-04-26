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
@file:Suppress("DEPRECATION", "UNCHECKED_CAST")

package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.audiorooms.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.NotificationRequestEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRemovalEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
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
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.PutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.RemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.JoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.LeaveRequestEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.inviteRequest.RelayInviteRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.gitAuthorList.GitAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiAuthorList.GoodWikiAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiRelayList.GoodWikiRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.kindMuteSet.KindMuteSetEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaFollowList.MediaFollowListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
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
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.redemption.NutzapRedemptionEvent
import com.vitorpamplona.quartz.nip61Nutzaps.token.TokenEvent
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
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.NIP90ContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.NIP90ContentSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.NIP90EventCountRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.NIP90EventCountResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.NIP90EventPowDelegationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.NIP90EventPowDelegationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.NIP90EventPublishScheduleRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.NIP90EventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.NIP90EventTimestampingRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.NIP90EventTimestampingResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.NIP90ImageGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.NIP90ImageGenerationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.NIP90ImageToVideoRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.NIP90ImageToVideoResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.NIP90MalwareScanRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.NIP90MalwareScanResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.NIP90OpReturnRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.NIP90OpReturnResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.NIP90PeopleSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.NIP90PeopleSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.NIP90SummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.NIP90SummarizationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.NIP90TextExtractionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.NIP90TextExtractionResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.NIP90TextGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.NIP90TextGenerationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.NIP90TextToSpeechRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.NIP90TextToSpeechResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.NIP90TranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.NIP90TranslationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.NIP90VideoConversionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.NIP90VideoConversionResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.NIP90VideoTranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.NIP90VideoTranslationResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

interface EventBuilder {
    fun build(
        id: HexKey,
        pubKey: HexKey,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String,
        sig: HexKey,
    ): Event
}

class EventFactory {
    companion object {
        val factories: MutableMap<Int, EventBuilder> = mutableMapOf()

        fun <T : Event> create(
            id: HexKey,
            pubKey: HexKey,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
            sig: HexKey,
        ): T =
            when (kind) {
                AcceptedBadgeSetEvent.KIND -> AcceptedBadgeSetEvent(id, pubKey, createdAt, tags, content, sig)
                AdvertisedRelayListEvent.KIND -> AdvertisedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                AppCurationSetEvent.KIND -> AppCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                AppDefinitionEvent.KIND -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                AppRecommendationEvent.KIND -> AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                AppSpecificDataEvent.KIND -> AppSpecificDataEvent(id, pubKey, createdAt, tags, content, sig)
                AttestationEvent.KIND -> AttestationEvent(id, pubKey, createdAt, tags, content, sig)
                AttestationRequestEvent.KIND -> AttestationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                AttestorRecommendationEvent.KIND -> AttestorRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                AttestorProficiencyEvent.KIND -> AttestorProficiencyEvent(id, pubKey, createdAt, tags, content, sig)
                ArticleCurationSetEvent.KIND -> ArticleCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                AudioHeaderEvent.KIND -> AudioHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                AuctionEvent.KIND -> AuctionEvent(id, pubKey, createdAt, tags, content, sig)
                AudioTrackEvent.KIND -> AudioTrackEvent(id, pubKey, createdAt, tags, content, sig)
                BadgeAwardEvent.KIND -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
                BadgeDefinitionEvent.KIND -> BadgeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                BidEvent.KIND -> BidEvent(id, pubKey, createdAt, tags, content, sig)
                BidConfirmationEvent.KIND -> BidConfirmationEvent(id, pubKey, createdAt, tags, content, sig)
                BlockedRelayListEvent.KIND -> BlockedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomServersEvent.KIND -> BlossomServersEvent(id, pubKey, createdAt, tags, content, sig)
                NestsServersEvent.KIND -> NestsServersEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomAuthorizationEvent.KIND -> BlossomAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                BroadcastRelayListEvent.KIND -> BroadcastRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                BookmarkListEvent.KIND -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                OldBookmarkListEvent.KIND -> OldBookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarDateSlotEvent.KIND -> CalendarDateSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarEvent.KIND -> CalendarEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarTimeSlotEvent.KIND -> CalendarTimeSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarRSVPEvent.KIND -> CalendarRSVPEvent(id, pubKey, createdAt, tags, content, sig)
                CallAnswerEvent.KIND -> CallAnswerEvent(id, pubKey, createdAt, tags, content, sig)
                CallHangupEvent.KIND -> CallHangupEvent(id, pubKey, createdAt, tags, content, sig)
                CallIceCandidateEvent.KIND -> CallIceCandidateEvent(id, pubKey, createdAt, tags, content, sig)
                CallOfferEvent.KIND -> CallOfferEvent(id, pubKey, createdAt, tags, content, sig)
                CallRejectEvent.KIND -> CallRejectEvent(id, pubKey, createdAt, tags, content, sig)
                CallRenegotiateEvent.KIND -> CallRenegotiateEvent(id, pubKey, createdAt, tags, content, sig)
                CashuMintEvent.KIND -> CashuMintEvent(id, pubKey, createdAt, tags, content, sig)
                CashuMintQuoteEvent.KIND -> CashuMintQuoteEvent(id, pubKey, createdAt, tags, content, sig)
                CashuTokenEvent.KIND -> CashuTokenEvent(id, pubKey, createdAt, tags, content, sig)
                CashuSpendingHistoryEvent.KIND -> CashuSpendingHistoryEvent(id, pubKey, createdAt, tags, content, sig)
                CashuWalletEvent.KIND -> CashuWalletEvent(id, pubKey, createdAt, tags, content, sig)
                ChatEvent.KIND -> ChatEvent(id, pubKey, createdAt, tags, content, sig)
                PutUserEvent.KIND -> PutUserEvent(id, pubKey, createdAt, tags, content, sig)
                RemoveUserEvent.KIND -> RemoveUserEvent(id, pubKey, createdAt, tags, content, sig)
                EditMetadataEvent.KIND -> EditMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                DeleteEventEvent.KIND -> DeleteEventEvent(id, pubKey, createdAt, tags, content, sig)
                CreateGroupEvent.KIND -> CreateGroupEvent(id, pubKey, createdAt, tags, content, sig)
                DeleteGroupEvent.KIND -> DeleteGroupEvent(id, pubKey, createdAt, tags, content, sig)
                CreateInviteEvent.KIND -> CreateInviteEvent(id, pubKey, createdAt, tags, content, sig)
                JoinRequestEvent.KIND -> JoinRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LeaveRequestEvent.KIND -> LeaveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                GroupMetadataEvent.KIND -> GroupMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                GroupAdminsEvent.KIND -> GroupAdminsEvent(id, pubKey, createdAt, tags, content, sig)
                GroupMembersEvent.KIND -> GroupMembersEvent(id, pubKey, createdAt, tags, content, sig)
                SupportedRolesEvent.KIND -> SupportedRolesEvent(id, pubKey, createdAt, tags, content, sig)
                ChessGameEvent.KIND -> ChessGameEvent(id, pubKey, createdAt, tags, content, sig)
                CodeSnippetEvent.KIND -> CodeSnippetEvent(id, pubKey, createdAt, tags, content, sig)
                RelayFeedsListEvent.KIND -> RelayFeedsListEvent(id, pubKey, createdAt, tags, content, sig)
                JesterEvent.KIND -> JesterEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameChallengeEvent.KIND -> LiveChessGameChallengeEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameAcceptEvent.KIND -> LiveChessGameAcceptEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessMoveEvent.KIND -> LiveChessMoveEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameEndEvent.KIND -> LiveChessGameEndEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessDrawOfferEvent.KIND -> LiveChessDrawOfferEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelCreateEvent.KIND -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelHideMessageEvent.KIND -> ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelListEvent.KIND -> ChannelListEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMessageEvent.KIND -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMetadataEvent.KIND -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMuteUserEvent.KIND -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
                ChatMessageEncryptedFileHeaderEvent.KIND -> ChatMessageEncryptedFileHeaderEvent(id.ifBlank { EventHasher.hashId(pubKey, createdAt, kind, tags, content) }, pubKey, createdAt, tags, content, sig)
                ChatMessageEvent.KIND -> ChatMessageEvent(id.ifBlank { EventHasher.hashId(pubKey, createdAt, kind, tags, content) }, pubKey, createdAt, tags, content, sig)
                ChatMessageRelayListEvent.KIND -> ChatMessageRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ClassifiedsEvent.KIND -> ClassifiedsEvent(id, pubKey, createdAt, tags, content, sig)
                CommentEvent.KIND -> CommentEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityDefinitionEvent.KIND -> CommunityDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityListEvent.KIND -> CommunityListEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityPostApprovalEvent.KIND -> CommunityPostApprovalEvent(id, pubKey, createdAt, tags, content, sig)
                ContactListEvent.KIND -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
                DeletionEvent.KIND -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)
                DraftWrapEvent.KIND -> DraftWrapEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiPackEvent.KIND -> EmojiPackEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiPackSelectionEvent.KIND -> EmojiPackSelectionEvent(id, pubKey, createdAt, tags, content, sig)
                EphemeralChatEvent.KIND -> EphemeralChatEvent(id, pubKey, createdAt, tags, content, sig)
                EphemeralChatListEvent.KIND -> EphemeralChatListEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalIdentitiesEvent.KIND -> ExternalIdentitiesEvent(id, pubKey, createdAt, tags, content, sig)
                FedimintEvent.KIND -> FedimintEvent(id, pubKey, createdAt, tags, content, sig)
                FileHeaderEvent.KIND -> FileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                ProfileGalleryEntryEvent.KIND -> ProfileGalleryEntryEvent(id, pubKey, createdAt, tags, content, sig)
                FileServersEvent.KIND -> FileServersEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageEvent.KIND -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageHeaderEvent.KIND -> FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                FhirResourceEvent.KIND -> FhirResourceEvent(id, pubKey, createdAt, tags, content, sig)
                FollowListEvent.KIND -> FollowListEvent(id, pubKey, createdAt, tags, content, sig)
                GenericRepostEvent.KIND -> GenericRepostEvent(id, pubKey, createdAt, tags, content, sig)
                GeohashListEvent.KIND -> GeohashListEvent(id, pubKey, createdAt, tags, content, sig)
                GiftWrapEvent.KIND -> GiftWrapEvent(id, pubKey, createdAt, tags, content, sig)
                EphemeralGiftWrapEvent.KIND -> EphemeralGiftWrapEvent(id, pubKey, createdAt, tags, content, sig)
                GitAuthorListEvent.KIND -> GitAuthorListEvent(id, pubKey, createdAt, tags, content, sig)
                GitRepositoryListEvent.KIND -> GitRepositoryListEvent(id, pubKey, createdAt, tags, content, sig)
                GitIssueEvent.KIND -> GitIssueEvent(id, pubKey, createdAt, tags, content, sig)
                GitReplyEvent.KIND -> GitReplyEvent(id, pubKey, createdAt, tags, content, sig)
                GitPatchEvent.KIND -> GitPatchEvent(id, pubKey, createdAt, tags, content, sig)
                GitPullRequestEvent.KIND -> GitPullRequestEvent(id, pubKey, createdAt, tags, content, sig)
                GitPullRequestUpdateEvent.KIND -> GitPullRequestUpdateEvent(id, pubKey, createdAt, tags, content, sig)
                GitRepositoryEvent.KIND -> GitRepositoryEvent(id, pubKey, createdAt, tags, content, sig)
                GitRepositoryStateEvent.KIND -> GitRepositoryStateEvent(id, pubKey, createdAt, tags, content, sig)
                GitStatusOpenEvent.KIND -> GitStatusOpenEvent(id, pubKey, createdAt, tags, content, sig)
                GitStatusAppliedEvent.KIND -> GitStatusAppliedEvent(id, pubKey, createdAt, tags, content, sig)
                GitStatusClosedEvent.KIND -> GitStatusClosedEvent(id, pubKey, createdAt, tags, content, sig)
                GitStatusDraftEvent.KIND -> GitStatusDraftEvent(id, pubKey, createdAt, tags, content, sig)
                UserGraspListEvent.KIND -> UserGraspListEvent(id, pubKey, createdAt, tags, content, sig)
                GoodWikiAuthorListEvent.KIND -> GoodWikiAuthorListEvent(id, pubKey, createdAt, tags, content, sig)
                GoodWikiRelayListEvent.KIND -> GoodWikiRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                GoalEvent.KIND -> GoalEvent(id, pubKey, createdAt, tags, content, sig)
                FavoriteAlgoFeedsListEvent.KIND -> FavoriteAlgoFeedsListEvent(id, pubKey, createdAt, tags, content, sig)
                HashtagListEvent.KIND -> HashtagListEvent(id, pubKey, createdAt, tags, content, sig)
                HighlightEvent.KIND -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
                HTTPAuthorizationEvent.KIND -> HTTPAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                IndexerRelayListEvent.KIND -> IndexerRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                InterestSetEvent.KIND -> InterestSetEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryPrologueEvent.KIND -> InteractiveStoryPrologueEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStorySceneEvent.KIND -> InteractiveStorySceneEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryReadingStateEvent.KIND -> InteractiveStoryReadingStateEvent(id, pubKey, createdAt, tags, content, sig)
                LabelEvent.KIND -> LabelEvent(id, pubKey, createdAt, tags, content, sig)
                KindMuteSetEvent.KIND -> KindMuteSetEvent(id, pubKey, createdAt, tags, content, sig)
                LabeledBookmarkListEvent.KIND -> LabeledBookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesChatMessageEvent.KIND -> LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesClipEvent.KIND -> LiveActivitiesClipEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesEvent.KIND -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesRaidEvent.KIND -> LiveActivitiesRaidEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapEvent.KIND -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentRequestEvent.KIND -> LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentResponseEvent.KIND -> LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NwcInfoEvent.KIND -> NwcInfoEvent(id, pubKey, createdAt, tags, content, sig)
                NwcNotificationEvent.KIND -> NwcNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                NwcNotificationEvent.LEGACY_KIND -> NwcNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPrivateEvent.KIND -> LnZapPrivateEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapRequestEvent.KIND -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LongTextNoteEvent.KIND -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                MarketplaceEvent.KIND -> MarketplaceEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomEvent.KIND -> MeetingRoomEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomPresenceEvent.KIND -> MeetingRoomPresenceEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingSpaceEvent.KIND -> MeetingSpaceEvent(id, pubKey, createdAt, tags, content, sig)
                AdminCommandEvent.KIND -> AdminCommandEvent(id, pubKey, createdAt, tags, content, sig)
                MintRecommendationEvent.KIND -> MintRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                MediaFollowListEvent.KIND -> MediaFollowListEvent(id, pubKey, createdAt, tags, content, sig)
                MediaStarterPackEvent.KIND -> MediaStarterPackEvent(id, pubKey, createdAt, tags, content, sig)
                MetadataEvent.KIND -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
                MuteListEvent.KIND -> MuteListEvent(id, pubKey, createdAt, tags, content, sig)
                KeyPackageEvent.KIND -> KeyPackageEvent(id, pubKey, createdAt, tags, content, sig)
                KeyPackageRelayListEvent.KIND -> KeyPackageRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                WelcomeEvent.KIND -> WelcomeEvent(id, pubKey, createdAt, tags, content, sig)
                GroupEvent.KIND -> GroupEvent(id, pubKey, createdAt, tags, content, sig)
                NotificationRequestEvent.KIND -> NotificationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                TokenRequestEvent.KIND -> TokenRequestEvent(id, pubKey, createdAt, tags, content, sig)
                TokenListEvent.KIND -> TokenListEvent(id, pubKey, createdAt, tags, content, sig)
                TokenRemovalEvent.KIND -> TokenRemovalEvent(id, pubKey, createdAt, tags, content, sig)
                NamedSiteEvent.KIND -> NamedSiteEvent(id, pubKey, createdAt, tags, content, sig)
                NNSEvent.KIND -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
                NipTextEvent.KIND -> NipTextEvent(id, pubKey, createdAt, tags, content, sig)
                NutzapEvent.KIND -> NutzapEvent(id, pubKey, createdAt, tags, content, sig)
                NutzapInfoEvent.KIND -> NutzapInfoEvent(id, pubKey, createdAt, tags, content, sig)
                NutzapRedemptionEvent.KIND -> NutzapRedemptionEvent(id, pubKey, createdAt, tags, content, sig)
                NostrConnectEvent.KIND -> NostrConnectEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90StatusEvent.KIND -> NIP90StatusEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextExtractionRequestEvent.KIND -> NIP90TextExtractionRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextExtractionResponseEvent.KIND -> NIP90TextExtractionResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90SummarizationRequestEvent.KIND -> NIP90SummarizationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90SummarizationResponseEvent.KIND -> NIP90SummarizationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TranslationRequestEvent.KIND -> NIP90TranslationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TranslationResponseEvent.KIND -> NIP90TranslationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextGenerationRequestEvent.KIND -> NIP90TextGenerationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextGenerationResponseEvent.KIND -> NIP90TextGenerationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ImageGenerationRequestEvent.KIND -> NIP90ImageGenerationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ImageGenerationResponseEvent.KIND -> NIP90ImageGenerationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90VideoConversionRequestEvent.KIND -> NIP90VideoConversionRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90VideoConversionResponseEvent.KIND -> NIP90VideoConversionResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90VideoTranslationRequestEvent.KIND -> NIP90VideoTranslationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90VideoTranslationResponseEvent.KIND -> NIP90VideoTranslationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ImageToVideoRequestEvent.KIND -> NIP90ImageToVideoRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ImageToVideoResponseEvent.KIND -> NIP90ImageToVideoResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextToSpeechRequestEvent.KIND -> NIP90TextToSpeechRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90TextToSpeechResponseEvent.KIND -> NIP90TextToSpeechResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryRequestEvent.KIND -> NIP90ContentDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryResponseEvent.KIND -> NIP90ContentDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryRequestEvent.KIND -> NIP90UserDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryResponseEvent.KIND -> NIP90UserDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentSearchRequestEvent.KIND -> NIP90ContentSearchRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentSearchResponseEvent.KIND -> NIP90ContentSearchResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90PeopleSearchRequestEvent.KIND -> NIP90PeopleSearchRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90PeopleSearchResponseEvent.KIND -> NIP90PeopleSearchResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventCountRequestEvent.KIND -> NIP90EventCountRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventCountResponseEvent.KIND -> NIP90EventCountResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90MalwareScanRequestEvent.KIND -> NIP90MalwareScanRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90MalwareScanResponseEvent.KIND -> NIP90MalwareScanResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventTimestampingRequestEvent.KIND -> NIP90EventTimestampingRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventTimestampingResponseEvent.KIND -> NIP90EventTimestampingResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90OpReturnRequestEvent.KIND -> NIP90OpReturnRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90OpReturnResponseEvent.KIND -> NIP90OpReturnResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventPublishScheduleRequestEvent.KIND -> NIP90EventPublishScheduleRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventPublishScheduleResponseEvent.KIND -> NIP90EventPublishScheduleResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventPowDelegationRequestEvent.KIND -> NIP90EventPowDelegationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90EventPowDelegationResponseEvent.KIND -> NIP90EventPowDelegationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                OtsEvent.KIND -> OtsEvent(id, pubKey, createdAt, tags, content, sig)
                PaymentTargetsEvent.KIND -> PaymentTargetsEvent(id, pubKey, createdAt, tags, content, sig)
                PeopleListEvent.KIND -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
                PictureCurationSetEvent.KIND -> PictureCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                P2POrderEvent.KIND -> P2POrderEvent(id, pubKey, createdAt, tags, content, sig)
                PictureEvent.KIND -> PictureEvent(id, pubKey, createdAt, tags, content, sig)
                PinListEvent.KIND -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
                ProfileBadgesEvent.KIND -> ProfileBadgesEvent(id, pubKey, createdAt, tags, content, sig)
                ZapPollEvent.KIND -> ZapPollEvent(id, pubKey, createdAt, tags, content, sig)
                PollEvent.KIND -> PollEvent(id, pubKey, createdAt, tags, content, sig)
                PollResponseEvent.KIND -> PollResponseEvent(id, pubKey, createdAt, tags, content, sig)
                ProductEvent.KIND -> ProductEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateDmEvent.KIND -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateOutboxRelayListEvent.KIND -> PrivateOutboxRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ProxyRelayListEvent.KIND -> ProxyRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                PublicMessageEvent.KIND -> PublicMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ReactionEvent.KIND -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
                ContactCardEvent.KIND -> ContactCardEvent(id, pubKey, createdAt, tags, content, sig)
                EventAssertionEvent.KIND -> EventAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableAssertionEvent.KIND -> AddressableAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalIdAssertionEvent.KIND -> ExternalIdAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAddMemberEvent.KIND -> RelayAddMemberEvent(id, pubKey, createdAt, tags, content, sig)
                RelayRemoveMemberEvent.KIND -> RelayRemoveMemberEvent(id, pubKey, createdAt, tags, content, sig)
                RelayMembershipListEvent.KIND -> RelayMembershipListEvent(id, pubKey, createdAt, tags, content, sig)
                RelayJoinRequestEvent.KIND -> RelayJoinRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayInviteRequestEvent.KIND -> RelayInviteRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayLeaveRequestEvent.KIND -> RelayLeaveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAuthEvent.KIND -> RelayAuthEvent(id, pubKey, createdAt, tags, content, sig)
                RelayDiscoveryEvent.KIND -> RelayDiscoveryEvent(id, pubKey, createdAt, tags, content, sig)
                RelayMonitorEvent.KIND -> RelayMonitorEvent(id, pubKey, createdAt, tags, content, sig)
                ReleaseArtifactSetEvent.KIND -> ReleaseArtifactSetEvent(id, pubKey, createdAt, tags, content, sig)
                RelaySetEvent.KIND -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
                ReportEvent.KIND -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
                RootSiteEvent.KIND -> RootSiteEvent(id, pubKey, createdAt, tags, content, sig)
                RepostEvent.KIND -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
                RequestToVanishEvent.KIND -> RequestToVanishEvent(id, pubKey, createdAt, tags, content, sig)
                SealedRumorEvent.KIND -> SealedRumorEvent(id, pubKey, createdAt, tags, content, sig)
                SearchRelayListEvent.KIND -> SearchRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                SimpleGroupListEvent.KIND -> SimpleGroupListEvent(id, pubKey, createdAt, tags, content, sig)
                SoftwareApplicationEvent.KIND -> SoftwareApplicationEvent(id, pubKey, createdAt, tags, content, sig)
                SoftwareAssetEvent.KIND -> SoftwareAssetEvent(id, pubKey, createdAt, tags, content, sig)
                StallEvent.KIND -> StallEvent(id, pubKey, createdAt, tags, content, sig)
                StatusEvent.KIND -> StatusEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteEvent.KIND -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                ThreadEvent.KIND -> ThreadEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteModificationEvent.KIND -> TextNoteModificationEvent(id, pubKey, createdAt, tags, content, sig)
                TokenEvent.KIND -> TokenEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentEvent.KIND -> TorrentEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentCommentEvent.KIND -> TorrentCommentEvent(id, pubKey, createdAt, tags, content, sig)
                TrustedRelayListEvent.KIND -> TrustedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                TrustProviderListEvent.KIND -> TrustProviderListEvent(id, pubKey, createdAt, tags, content, sig)
                VideoCurationSetEvent.KIND -> VideoCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                VideoHorizontalEvent.KIND -> VideoHorizontalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoVerticalEvent.KIND -> VideoVerticalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoNormalEvent.KIND -> VideoNormalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoShortEvent.KIND -> VideoShortEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceEvent.KIND -> VoiceEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceReplyEvent.KIND -> VoiceReplyEvent(id, pubKey, createdAt, tags, content, sig)
                WakeUpEvent.KIND -> WakeUpEvent(id, pubKey, createdAt, tags, content, sig)
                WebBookmarkEvent.KIND -> WebBookmarkEvent(id, pubKey, createdAt, tags, content, sig)
                WikiNoteEvent.KIND -> WikiNoteEvent(id, pubKey, createdAt, tags, content, sig)
                else -> factories[kind]?.build(id, pubKey, createdAt, tags, content, sig) ?: Event(id, pubKey, createdAt, kind, tags, content, sig)
            } as T
    }
}
