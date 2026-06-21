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
package com.vitorpamplona.quartz.kinds

import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.manage.ManageEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.NotificationRequestEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRemovalEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRequestEvent
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
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
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
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppDataEvent
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
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * Human-readable label and defining NIP for a Nostr event kind.
 */
data class KindName(
    val name: String,
    val nip: String?,
)

/**
 * Canonical, **i18n-free** registry mapping event-kind numbers to an English
 * label and the NIP that defines them. This is the single source of truth for
 * "what is kind N" across the project:
 *
 *  - headless consumers (the `amy` CLI) print [nameFor] directly;
 *  - localized front ends (the Android app) overlay their own translated
 *    strings on top and fall back to [nameFor] for kinds they don't translate.
 *
 * Keep this list as the place new kinds are registered; translations are a
 * platform concern layered on top, never a fork of this data.
 */
object KindNames {
    val names: Map<Int, KindName> =
        mapOf(
            AcceptedBadgeSetEvent.KIND to KindName("Accepted Badge Set", "58"),
            AdvertisedRelayListEvent.KIND to KindName("Outbox Relays", "65"),
            AppDefinitionEvent.KIND to KindName("Apps", "89"),
            AppRecommendationEvent.KIND to KindName("App Recommendations", "89"),
            AppSpecificDataEvent.KIND to KindName("User Settings", "78"),
            AudioHeaderEvent.KIND to KindName("Audio Header", null),
            AudioTrackEvent.KIND to KindName("Audio Track", null),
            MusicTrackEvent.KIND to KindName("Music Track", null),
            MusicPlaylistEvent.KIND to KindName("Music Playlist", null),
            PodcastEpisodeEvent.KIND to KindName("Podcast Episode", "F4"),
            PodcastMetadataEvent.KIND to KindName("Podcast Show", "F4"),
            AuthoredPodcastsEvent.KIND to KindName("Authored Podcasts", "F4"),
            FavoritePodcastsListEvent.KIND to KindName("Favorite Podcasts", "F4"),
            AttestationEvent.KIND to KindName("Attestation", null),
            AttestationRequestEvent.KIND to KindName("Attestation Request", null),
            AttestorRecommendationEvent.KIND to KindName("Attestor Recommendation", null),
            AttestorProficiencyEvent.KIND to KindName("Attestor Proficiency", null),
            BadgeAwardEvent.KIND to KindName("Badge Awards", "58"),
            BadgeDefinitionEvent.KIND to KindName("Badge Definitions", "58"),
            BlockedRelayListEvent.KIND to KindName("Blocked Relays", "51"),
            BlossomServersEvent.KIND to KindName("Blossom Servers", "B7"),
            NestsServersEvent.KIND to KindName("Nests Servers", "53"),
            BlossomAuthorizationEvent.KIND to KindName("Blossom Auth", "B7"),
            BroadcastRelayListEvent.KIND to KindName("Broadcast Relays", "51"),
            BookmarkListEvent.KIND to KindName("Bookmark List", "51"),
            OldBookmarkListEvent.KIND to KindName("Old Bookmark List", "51"),
            CalendarDateSlotEvent.KIND to KindName("Day Appointment", "52"),
            CalendarEvent.KIND to KindName("Calendar", "52"),
            CalendarTimeSlotEvent.KIND to KindName("Appointment", "52"),
            CalendarRSVPEvent.KIND to KindName("Appt RSVP", "52"),
            ChessGameEvent.KIND to KindName("Chess Games", "64"),
            JesterEvent.KIND to KindName("Chess Auth", "64"),
            RelayFeedsListEvent.KIND to KindName("Favorite Relays", "51"),
            LiveChessGameChallengeEvent.KIND to KindName("Chess Challenges", "64"),
            LiveChessGameAcceptEvent.KIND to KindName("Chess Game Accept", "64"),
            LiveChessMoveEvent.KIND to KindName("Chess Move", "64"),
            LiveChessGameEndEvent.KIND to KindName("Chess Game End", "64"),
            LiveChessDrawOfferEvent.KIND to KindName("Chess Draw Offer", "64"),
            ChannelCreateEvent.KIND to KindName("Channel Definition", "28"),
            ChannelHideMessageEvent.KIND to KindName("Channel Hide Msg", "28"),
            ChannelListEvent.KIND to KindName("Channel List", "28"),
            ChannelMessageEvent.KIND to KindName("Channel Message", "28"),
            ChannelMetadataEvent.KIND to KindName("Channel Metadata", "28"),
            ChannelMuteUserEvent.KIND to KindName("Channel Mute User", "28"),
            ChatMessageEncryptedFileHeaderEvent.KIND to KindName("DM File", "17"),
            ChatMessageEvent.KIND to KindName("DM Message", "17"),
            ChatMessageRelayListEvent.KIND to KindName("DM Relays", "17"),
            ClassifiedsEvent.KIND to KindName("Classifieds", "99"),
            CommentEvent.KIND to KindName("Comments", "22"),
            CommunityDefinitionEvent.KIND to KindName("Community Def", "72"),
            CommunityListEvent.KIND to KindName("Community List", "72"),
            CommunityPostApprovalEvent.KIND to KindName("Community Post", "72"),
            ContactListEvent.KIND to KindName("Follow List", "02"),
            DeletionEvent.KIND to KindName("Deletions", "09"),
            DraftWrapEvent.KIND to KindName("Drafts", "37"),
            EmojiPackEvent.KIND to KindName("Emoji Packs", "30"),
            EmojiPackSelectionEvent.KIND to KindName("Emoji Pack List", "30"),
            EphemeralChatEvent.KIND to KindName("Ephemeral Chat", null),
            EphemeralChatListEvent.KIND to KindName("Ephemeral Chatrooms", null),
            FileHeaderEvent.KIND to KindName("File Headers", "94"),
            ProfileGalleryEntryEvent.KIND to KindName("Profile Gallery", null),
            FileServersEvent.KIND to KindName("File Servers", "96"),
            FileStorageEvent.KIND to KindName("Blob Data", null),
            FileStorageHeaderEvent.KIND to KindName("Blob Headers", null),
            FhirResourceEvent.KIND to KindName("Medical Data", null),
            FollowListEvent.KIND to KindName("Follow Packs", "51"),
            GenericRepostEvent.KIND to KindName("Reposts (16)", "18"),
            GeohashListEvent.KIND to KindName("Geohash Follows", "51"),
            GiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            EphemeralGiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            GitIssueEvent.KIND to KindName("Git Issue", "34"),
            GitPatchEvent.KIND to KindName("Git Patch", "34"),
            GitRepositoryEvent.KIND to KindName("Git Repo", "34"),
            GitReplyEvent.KIND to KindName("Git Reply", "34"),
            GoalEvent.KIND to KindName("Zap Goals", "75"),
            HashtagListEvent.KIND to KindName("Hashtag Follows", "51"),
            HighlightEvent.KIND to KindName("Highlights", "84"),
            HTTPAuthorizationEvent.KIND to KindName("Http Auth", "98"),
            IndexerRelayListEvent.KIND to KindName("Index Relay List", "51"),
            InteractiveStoryPrologueEvent.KIND to KindName("Adventure Prologue", null),
            InteractiveStorySceneEvent.KIND to KindName("Adventure Scene", null),
            InteractiveStoryReadingStateEvent.KIND to KindName("Adventure Reading", null),
            LabeledBookmarkListEvent.KIND to KindName("Named Bookmarks", "51"),
            LiveActivitiesChatMessageEvent.KIND to KindName("Live Chats", "53"),
            LiveActivitiesEvent.KIND to KindName("Live Streams", "53"),
            LnZapEvent.KIND to KindName("Zaps", "57"),
            LnZapPaymentRequestEvent.KIND to KindName("NWC Request", "47"),
            LnZapPaymentResponseEvent.KIND to KindName("NWC Response", "47"),
            LnZapPrivateEvent.KIND to KindName("Private Zaps", "57"),
            LnZapRequestEvent.KIND to KindName("Zap Req", "57"),
            LongTextNoteEvent.KIND to KindName("Blogs", "23"),
            MeetingRoomEvent.KIND to KindName("Meeting Room", "53"),
            MeetingRoomPresenceEvent.KIND to KindName("Room Presence", "53"),
            MeetingSpaceEvent.KIND to KindName("Meeting Space", "53"),
            MetadataEvent.KIND to KindName("Profile", "01"),
            MuteListEvent.KIND to KindName("Mute List", "51"),
            NNSEvent.KIND to KindName("NNS", null),
            NipTextEvent.KIND to KindName("NIP", null),
            NostrConnectEvent.KIND to KindName("Nostr Connect", "46"),
            NIP90StatusEvent.KIND to KindName("DVM Status", "90"),
            NIP90ContentDiscoveryRequestEvent.KIND to KindName("DVM Content Req", "90"),
            NIP90ContentDiscoveryResponseEvent.KIND to KindName("DVM Content Resp", "90"),
            NIP90UserDiscoveryRequestEvent.KIND to KindName("DVM User Req", "90"),
            NIP90UserDiscoveryResponseEvent.KIND to KindName("DVM User Resp", "90"),
            OtsEvent.KIND to KindName("OTS", "03"),
            PaymentTargetsEvent.KIND to KindName("PayTo", null),
            PeopleListEvent.KIND to KindName("People Lists", "51"),
            ProfileBadgesEvent.KIND to KindName("Profile Badges", "58"),
            PictureEvent.KIND to KindName("Pictures", "68"),
            WorkoutRecordEvent.KIND to KindName("Workouts", null),
            PinListEvent.KIND to KindName("Pins", "51"),
            ZapPollEvent.KIND to KindName("Zap Poll", null),
            PollEvent.KIND to KindName("Poll", "88"),
            PollResponseEvent.KIND to KindName("Poll Response", "88"),
            PrivateDmEvent.KIND to KindName("NIP-04 DMs", "04"),
            PrivateOutboxRelayListEvent.KIND to KindName("Private Relays", "37"),
            ProxyRelayListEvent.KIND to KindName("Proxy Relays", "51"),
            PublicMessageEvent.KIND to KindName("Public Message", "A4"),
            ReactionEvent.KIND to KindName("Reactions", "25"),
            ContactCardEvent.KIND to KindName("Contact Card", "85"),
            RelayAuthEvent.KIND to KindName("Relay Auth", "42"),
            RelayDiscoveryEvent.KIND to KindName("Relay Discovery", "66"),
            RelayMonitorEvent.KIND to KindName("Relay Monitor Announcement", "66"),
            RelaySetEvent.KIND to KindName("Relay Set", "51"),
            ReportEvent.KIND to KindName("Reports", "56"),
            RepostEvent.KIND to KindName("Reposts", "18"),
            RequestToVanishEvent.KIND to KindName("User Delete", "62"),
            SealedRumorEvent.KIND to KindName("Seals", "59"),
            SearchRelayListEvent.KIND to KindName("Search Relays", "50"),
            StatusEvent.KIND to KindName("User Status", "38"),
            TextNoteEvent.KIND to KindName("Notes", "10"),
            TextNoteModificationEvent.KIND to KindName("Edits", null),
            TorrentEvent.KIND to KindName("Torrents", "35"),
            TorrentCommentEvent.KIND to KindName("Torrent Comments", "35"),
            TrustedRelayListEvent.KIND to KindName("Trusted Relays", "51"),
            TrustProviderListEvent.KIND to KindName("Trusted Providers", "85"),
            VideoHorizontalEvent.KIND to KindName("Video (Repl)", "71"),
            VideoVerticalEvent.KIND to KindName("Shorts (Repl)", "71"),
            VideoNormalEvent.KIND to KindName("Video", "71"),
            VideoShortEvent.KIND to KindName("Shorts", "71"),
            VoiceEvent.KIND to KindName("Voice Msg", "A0"),
            VoiceReplyEvent.KIND to KindName("Voice Reply", "A0"),
            WakeUpEvent.KIND to KindName("WakeUp", null),
            WebBookmarkEvent.KIND to KindName("Web Bookmark", "B0"),
            WikiNoteEvent.KIND to KindName("Wiki", "54"),
            ChatEvent.KIND to KindName("Relay Chat", "C7"),
            ThreadEvent.KIND to KindName("Thread", "7D"),
            AppDataEvent.KIND to KindName("App Data", "78"),
            WelcomeEvent.KIND to KindName("MLS Welcome", null),
            GroupEvent.KIND to KindName("MLS Group Message", null),
            NotificationRequestEvent.KIND to KindName("MLS Notification Request", null),
            TokenRequestEvent.KIND to KindName("MLS Token Request", null),
            TokenListEvent.KIND to KindName("MLS Token List", null),
            TokenRemovalEvent.KIND to KindName("MLS Token Removal", null),
            BidEvent.KIND to KindName("Marketplace Bid", "15"),
            BidConfirmationEvent.KIND to KindName("Bid Confirmation", "15"),
            LiveActivitiesRaidEvent.KIND to KindName("Live Raid", "53"),
            LiveActivitiesClipEvent.KIND to KindName("Live Clip", "53"),
            RoadEventReportEvent.KIND to KindName("Road Report", null),
            RoadEventConfirmationEvent.KIND to KindName("Road Confirmation", null),
            CodeSnippetEvent.KIND to KindName("Code Snippet", "C0"),
            GitPullRequestEvent.KIND to KindName("Git Pull Request", "34"),
            GitPullRequestUpdateEvent.KIND to KindName("Git PR Update", "34"),
            LabelEvent.KIND to KindName("Label", "32"),
            SoftwareAssetEvent.KIND to KindName("Software Asset", "82"),
            AdminCommandEvent.KIND to KindName("Nests Admin Command", null),
            NIP90TextExtractionRequestEvent.KIND to KindName("DVM Text Extraction Req", "90"),
            NIP90SummarizationRequestEvent.KIND to KindName("DVM Summarization Req", "90"),
            NIP90TranslationRequestEvent.KIND to KindName("DVM Translation Req", "90"),
            NIP90TextGenerationRequestEvent.KIND to KindName("DVM Text Generation Req", "90"),
            NIP90ImageGenerationRequestEvent.KIND to KindName("DVM Image Generation Req", "90"),
            NappletSnapshotEvent.KIND to KindName("Napplet Snapshot", "5D"),
            NIP90VideoConversionRequestEvent.KIND to KindName("DVM Video Conversion Req", "90"),
            NIP90VideoTranslationRequestEvent.KIND to KindName("DVM Video Translation Req", "90"),
            NIP90ImageToVideoRequestEvent.KIND to KindName("DVM Image To Video Req", "90"),
            NIP90TextToSpeechRequestEvent.KIND to KindName("DVM Text To Speech Req", "90"),
            NIP90ContentSearchRequestEvent.KIND to KindName("DVM Content Search Req", "90"),
            NIP90PeopleSearchRequestEvent.KIND to KindName("DVM People Search Req", "90"),
            NIP90EventCountRequestEvent.KIND to KindName("DVM Event Count Req", "90"),
            NIP90MalwareScanRequestEvent.KIND to KindName("DVM Malware Scan Req", "90"),
            NIP90EventTimestampingRequestEvent.KIND to KindName("DVM Timestamping Req", "90"),
            NIP90OpReturnRequestEvent.KIND to KindName("DVM OpReturn Req", "90"),
            NIP90EventPublishScheduleRequestEvent.KIND to KindName("DVM Publish Schedule Req", "90"),
            NIP90EventPowDelegationRequestEvent.KIND to KindName("DVM PoW Delegation Req", "90"),
            NIP90TextExtractionResponseEvent.KIND to KindName("DVM Text Extraction Resp", "90"),
            NIP90SummarizationResponseEvent.KIND to KindName("DVM Summarization Resp", "90"),
            NIP90TranslationResponseEvent.KIND to KindName("DVM Translation Resp", "90"),
            NIP90TextGenerationResponseEvent.KIND to KindName("DVM Text Generation Resp", "90"),
            NIP90ImageGenerationResponseEvent.KIND to KindName("DVM Image Generation Resp", "90"),
            NIP90VideoConversionResponseEvent.KIND to KindName("DVM Video Conversion Resp", "90"),
            NIP90VideoTranslationResponseEvent.KIND to KindName("DVM Video Translation Resp", "90"),
            NIP90ImageToVideoResponseEvent.KIND to KindName("DVM Image To Video Resp", "90"),
            NIP90TextToSpeechResponseEvent.KIND to KindName("DVM Text To Speech Resp", "90"),
            NIP90ContentSearchResponseEvent.KIND to KindName("DVM Content Search Resp", "90"),
            NIP90PeopleSearchResponseEvent.KIND to KindName("DVM People Search Resp", "90"),
            NIP90EventCountResponseEvent.KIND to KindName("DVM Event Count Resp", "90"),
            NIP90MalwareScanResponseEvent.KIND to KindName("DVM Malware Scan Resp", "90"),
            NIP90EventTimestampingResponseEvent.KIND to KindName("DVM Timestamping Resp", "90"),
            NIP90OpReturnResponseEvent.KIND to KindName("DVM OpReturn Resp", "90"),
            NIP90EventPublishScheduleResponseEvent.KIND to KindName("DVM Publish Schedule Resp", "90"),
            NIP90EventPowDelegationResponseEvent.KIND to KindName("DVM PoW Delegation Resp", "90"),
            CashuMintQuoteEvent.KIND to KindName("Cashu Mint Quote", "60"),
            CashuTokenEvent.KIND to KindName("Cashu Token", "60"),
            CashuSpendingHistoryEvent.KIND to KindName("Cashu History", "60"),
            RelayAddMemberEvent.KIND to KindName("Relay Add Member", "43"),
            RelayRemoveMemberEvent.KIND to KindName("Relay Remove Member", "43"),
            OnchainZapEvent.KIND to KindName("Onchain Zap", "BC"),
            PutUserEvent.KIND to KindName("Group Put User", "29"),
            RemoveUserEvent.KIND to KindName("Group Remove User", "29"),
            EditMetadataEvent.KIND to KindName("Group Edit Metadata", "29"),
            DeleteEventEvent.KIND to KindName("Group Delete Event", "29"),
            CreateGroupEvent.KIND to KindName("Group Create", "29"),
            DeleteGroupEvent.KIND to KindName("Group Delete", "29"),
            CreateInviteEvent.KIND to KindName("Group Create Invite", "29"),
            JoinRequestEvent.KIND to KindName("Group Join Request", "29"),
            LeaveRequestEvent.KIND to KindName("Group Leave Request", "29"),
            NutzapEvent.KIND to KindName("Nutzap", "61"),
            SimpleGroupListEvent.KIND to KindName("Group List", "51"),
            ExternalIdentitiesEvent.KIND to KindName("External Identities", "39"),
            GitAuthorListEvent.KIND to KindName("Git Authors", "51"),
            GitRepositoryListEvent.KIND to KindName("Git Repos", "51"),
            NutzapInfoEvent.KIND to KindName("Nutzap Info", "61"),
            MediaFollowListEvent.KIND to KindName("Media Follows", "51"),
            EncryptionKeyListEvent.KIND to KindName("Encryption Keys", null),
            KeyPackageRelayListEvent.KIND to KindName("MLS KeyPackage Relays", null),
            FavoriteAlgoFeedsListEvent.KIND to KindName("Favorite Feeds", "51"),
            GoodWikiAuthorListEvent.KIND to KindName("Wiki Authors", "51"),
            GoodWikiRelayListEvent.KIND to KindName("Wiki Relays", "51"),
            UserGraspListEvent.KIND to KindName("GRASP Servers", "34"),
            BirdexEvent.KIND to KindName("Birdex", null),
            NwcInfoEvent.KIND to KindName("NWC Info", "47"),
            RelayMembershipListEvent.KIND to KindName("Relay Memberships", "43"),
            RootSiteEvent.KIND to KindName("Website Root", "5A"),
            RootNappletEvent.KIND to KindName("Napplet Root", "5D"),
            CashuWalletEvent.KIND to KindName("Cashu Wallet", "60"),
            OfferEvent.KIND to KindName("CLINK Offer", null),
            DebitEvent.KIND to KindName("CLINK Debit", null),
            ManageEvent.KIND to KindName("CLINK Manage", null),
            NwcNotificationEvent.KIND to KindName("NWC Notification", "47"),
            CallOfferEvent.KIND to KindName("Call Offer", "AC"),
            CallAnswerEvent.KIND to KindName("Call Answer", "AC"),
            CallIceCandidateEvent.KIND to KindName("Call ICE Candidate", "AC"),
            CallHangupEvent.KIND to KindName("Call Hangup", "AC"),
            CallRejectEvent.KIND to KindName("Call Reject", "AC"),
            CallRenegotiateEvent.KIND to KindName("Call Renegotiate", "AC"),
            RelayJoinRequestEvent.KIND to KindName("Relay Join Request", "43"),
            RelayInviteRequestEvent.KIND to KindName("Relay Invite Request", "43"),
            RelayLeaveRequestEvent.KIND to KindName("Relay Leave Request", "43"),
            ArticleCurationSetEvent.KIND to KindName("Article Sets", "51"),
            VideoCurationSetEvent.KIND to KindName("Video Sets", "51"),
            PictureCurationSetEvent.KIND to KindName("Picture Sets", "51"),
            KindMuteSetEvent.KIND to KindName("Kind Mute Sets", "51"),
            InterestSetEvent.KIND to KindName("Interest Sets", "51"),
            StallEvent.KIND to KindName("Marketplace Stall", "15"),
            ProductEvent.KIND to KindName("Marketplace Product", "15"),
            MarketplaceEvent.KIND to KindName("Marketplace", "15"),
            AuctionEvent.KIND to KindName("Marketplace Auction", "15"),
            ReleaseArtifactSetEvent.KIND to KindName("Release Artifacts", "51"),
            AppCurationSetEvent.KIND to KindName("App Sets", "51"),
            EventAssertionEvent.KIND to KindName("Event Assertion", "85"),
            AddressableAssertionEvent.KIND to KindName("Addressable Assertion", "85"),
            ExternalIdAssertionEvent.KIND to KindName("External ID Assertion", "85"),
            KeyPackageEvent.KIND to KindName("MLS KeyPackage", null),
            GitRepositoryStateEvent.KIND to KindName("Git Repo State", "34"),
            FeedDefinitionEvent.KIND to KindName("Feed Definition", null),
            SoftwareApplicationEvent.KIND to KindName("Software Application", "82"),
            ExerciseTemplateEvent.KIND to KindName("Exercise Template", null),
            FundraiserEvent.KIND to KindName("Fundraiser", null),
            CommunityRulesEvent.KIND to KindName("Community Rules", "72"),
            NamedSiteEvent.KIND to KindName("Website", "5A"),
            NamedNappletEvent.KIND to KindName("Napplet", "5D"),
            MintRecommendationEvent.KIND to KindName("Mint Recommendation", "87"),
            CashuMintEvent.KIND to KindName("Cashu Mint", "87"),
            FedimintEvent.KIND to KindName("Fedimint", "87"),
            P2POrderEvent.KIND to KindName("P2P Order", "69"),
            GroupMetadataEvent.KIND to KindName("Group Metadata", "29"),
            GroupAdminsEvent.KIND to KindName("Group Admins", "29"),
            GroupMembersEvent.KIND to KindName("Group Members", "29"),
            SupportedRolesEvent.KIND to KindName("Group Roles", "29"),
            MediaStarterPackEvent.KIND to KindName("Media Starter Pack", "51"),
        )

    fun infoFor(kind: Int): KindName? = names[kind]

    fun nameFor(kind: Int): String? = names[kind]?.name

    fun nipFor(kind: Int): String? = names[kind]?.nip

    /** Case-insensitive substring search by label; returns matching (kind, info) sorted by kind. */
    fun search(query: String): List<Pair<Int, KindName>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return names.entries
            .filter {
                it.value.name
                    .lowercase()
                    .contains(q)
            }.map { it.key to it.value }
            .sortedBy { it.first }
    }
}
