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
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.citations.ExternalCitationEvent
import com.vitorpamplona.quartz.experimental.citations.HardcopyCitationEvent
import com.vitorpamplona.quartz.experimental.citations.PromptCitationEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.manage.ManageEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.ListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RelayReviewEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
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
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip25Reactions.ExternalReactionEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.secretUpdate.CiSecretUpdateEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
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
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.inviteRequest.RelayInviteRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.gitAuthorList.GitAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiAuthorList.GoodWikiAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiRelayList.GoodWikiRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.kindMuteSet.KindMuteSetEvent
import com.vitorpamplona.quartz.nip51Lists.mediaFollowList.MediaFollowListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeAcceptanceEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeRequestEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
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
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import com.vitorpamplona.quartz.nip71Video.views.VideoViewEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppDataEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventCount.DvmEventCountResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPowDelegation.DvmEventPowDelegationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventPublishSchedule.DvmEventPublishScheduleResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.eventTimestamping.DvmEventTimestampingResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.DvmImageGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageGeneration.DvmImageGenerationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.DvmImageToVideoRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.imageToVideo.DvmImageToVideoResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.malwareScanning.DvmMalwareScanResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.opReturn.DvmOpReturnResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.summarization.DvmSummarizationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.DvmTextExtractionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textExtraction.DvmTextExtractionResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textGeneration.DvmTextGenerationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.DvmTextToSpeechRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.textToSpeech.DvmTextToSpeechResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.DvmTranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.translation.DvmTranslationResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.DvmUserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.DvmUserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.DvmVideoConversionRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoConversion.DvmVideoConversionResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.DvmVideoTranslationRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.videoTranslation.DvmVideoTranslationResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.nipXXPrivateNoteStorage.PnsEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.deregistration.PushDeregistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferencesEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushRegistrationEvent

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
    @Suppress("DEPRECATION") // registry intentionally names deprecated kinds (GitReply, TorrentComment) for display
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
            Podcasting20EpisodeEvent.KIND to KindName("Podcast Episode (Podcasting 2.0)", null),
            Podcasting20TrailerEvent.KIND to KindName("Podcast Trailer (Podcasting 2.0)", null),
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
            CalendarCollectionEvent.KIND to KindName("Calendar", "52"),
            CalendarTimeSlotEvent.KIND to KindName("Appointment", "52"),
            CalendarRSVPEvent.KIND to KindName("Appt RSVP", "52"),
            ChessGameEvent.KIND to KindName("Chess Games", "64"),
            JesterEvent.KIND to KindName("Chess Auth", "64"),
            FavoriteRelayListEvent.KIND to KindName("Favorite Relays", "51"),
            LiveChessGameChallengeEvent.KIND to KindName("Chess Challenges", "64"),
            LiveChessGameAcceptEvent.KIND to KindName("Chess Game Accept", "64"),
            LiveChessMoveEvent.KIND to KindName("Chess Move", "64"),
            LiveChessGameEndEvent.KIND to KindName("Chess Game End", "64"),
            LiveChessDrawOfferEvent.KIND to KindName("Chess Draw Offer", "64"),
            ChannelCreateEvent.KIND to KindName("Channel Definition", "28"),
            ChannelHideMessageEvent.KIND to KindName("Channel Hide Msg", "28"),
            PublicChatListEvent.KIND to KindName("Channel List", "28"),
            ChannelMessageEvent.KIND to KindName("Channel Message", "28"),
            ChannelMetadataEvent.KIND to KindName("Channel Metadata", "28"),
            ChannelMuteUserEvent.KIND to KindName("Channel Mute User", "28"),
            ChatMessageEncryptedFileHeaderEvent.KIND to KindName("DM File", "17"),
            ChatMessageEvent.KIND to KindName("DM Message", "17"),
            DmRelayListEvent.KIND to KindName("DM Relays", "17"),
            ClassifiedsEvent.KIND to KindName("Classifieds", "99"),
            CommentEvent.KIND to KindName("Comments", "22"),
            GeocacheListingEvent.KIND to KindName("Geocache", "CC"),
            GeocacheFoundLogEvent.KIND to KindName("Geocache Found Log", "CC"),
            GeocacheVerificationEvent.KIND to KindName("Geocache Verification", "CC"),
            GeocacheCurationListEvent.KIND to KindName("Geocache Curation List", "CC"),
            CommunityDefinitionEvent.KIND to KindName("Community Def", "72"),
            CommunityListEvent.KIND to KindName("Community List", "72"),
            CommunityPostApprovalEvent.KIND to KindName("Community Post", "72"),
            ContactListEvent.KIND to KindName("Follow List", "02"),
            DeletionRequestEvent.KIND to KindName("Deletions", "09"),
            DraftWrapEvent.KIND to KindName("Drafts", "37"),
            EmojiPackEvent.KIND to KindName("Emoji Packs", "30"),
            StickerPackEvent.KIND to KindName("Sticker Packs", null),
            EmojiListEvent.KIND to KindName("Emoji Pack List", "30"),
            EphemeralChatEvent.KIND to KindName("Ephemeral Chat", null),
            EphemeralChatListEvent.KIND to KindName("Ephemeral Chatrooms", null),
            FileMetadataEvent.KIND to KindName("File Headers", "94"),
            ProfileGalleryEntryEvent.KIND to KindName("Profile Gallery", null),
            FileServersEvent.KIND to KindName("File Servers", "96"),
            FileStorageEvent.KIND to KindName("Blob Data", null),
            FileStorageHeaderEvent.KIND to KindName("Blob Headers", null),
            FhirResourceEvent.KIND to KindName("Medical Data", null),
            StarterPackEvent.KIND to KindName("Follow Packs", "51"),
            GenericRepostEvent.KIND to KindName("Reposts (16)", "18"),
            GeohashListEvent.KIND to KindName("Geohash Follows", "51"),
            GiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            EphemeralGiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            GitIssueEvent.KIND to KindName("Git Issue", "34"),
            GitPatchEvent.KIND to KindName("Git Patch", "34"),
            GitRepositoryEvent.KIND to KindName("Git Repo", "34"),
            GitReplyEvent.KIND to KindName("Git Reply", "34"),
            GitCoverNoteEvent.KIND to KindName("Git Cover Note", null),
            CiManualTriggerEvent.KIND to KindName("CI Manual Trigger", null),
            CiJobResultEvent.KIND to KindName("CI Job Result", null),
            CiWorkflowResultEvent.KIND to KindName("CI Workflow Result", null),
            CiServiceRequestEvent.KIND to KindName("CI Service Request", null),
            CiServiceStopEvent.KIND to KindName("CI Service Stop", null),
            CiWorkflowProgressEvent.KIND to KindName("CI Workflow Progress", null),
            CiCoordinatorAdvertisementEvent.KIND to KindName("CI Coordinator", null),
            CiRequestReadinessListEvent.KIND to KindName("CI Request Readiness", null),
            CiRepositoryStatusEvent.KIND to KindName("CI Repo Status", null),
            CiSecretUpdateEvent.KIND to KindName("CI Secret Update", null),
            ZapGoalEvent.KIND to KindName("Zap Goals", "75"),
            InterestListEvent.KIND to KindName("Hashtag Follows", "51"),
            HighlightEvent.KIND to KindName("Highlights", "84"),
            HTTPAuthorizationEvent.KIND to KindName("Http Auth", "98"),
            IndexerRelayListEvent.KIND to KindName("Index Relay List", "51"),
            InteractiveStoryPrologueEvent.KIND to KindName("Adventure Prologue", null),
            InteractiveStorySceneEvent.KIND to KindName("Adventure Scene", null),
            InteractiveStoryReadingStateEvent.KIND to KindName("Adventure Reading", null),
            BookmarkSetEvent.KIND to KindName("Named Bookmarks", "51"),
            LiveActivitiesChatMessageEvent.KIND to KindName("Live Chats", "53"),
            LiveActivitiesEvent.KIND to KindName("Live Streams", "53"),
            ZapReceiptEvent.KIND to KindName("Zaps", "57"),
            NwcRequestEvent.KIND to KindName("NWC Request", "47"),
            NwcResponseEvent.KIND to KindName("NWC Response", "47"),
            PrivateZapEvent.KIND to KindName("Private Zaps", "57"),
            ZapRequestEvent.KIND to KindName("Zap Req", "57"),
            LongFormContentEvent.KIND to KindName("Blogs", "23"),
            LongFormDraftEvent.KIND to KindName("Blog Drafts", "23"),
            MeetingRoomEvent.KIND to KindName("Meeting Room", "53"),
            MeetingRoomPresenceEvent.KIND to KindName("Room Presence", "53"),
            MeetingSpaceEvent.KIND to KindName("Meeting Space", "53"),
            MetadataEvent.KIND to KindName("Profile", "01"),
            MuteListEvent.KIND to KindName("Mute List", "51"),
            NNSEvent.KIND to KindName("NNS", null),
            NipTextEvent.KIND to KindName("NIP", null),
            NostrConnectEvent.KIND to KindName("Nostr Connect", "46"),
            DvmStatusEvent.KIND to KindName("DVM Status", "90"),
            DvmContentDiscoveryRequestEvent.KIND to KindName("DVM Content Req", "90"),
            DvmContentDiscoveryResponseEvent.KIND to KindName("DVM Content Resp", "90"),
            DvmUserDiscoveryRequestEvent.KIND to KindName("DVM User Req", "90"),
            DvmUserDiscoveryResponseEvent.KIND to KindName("DVM User Resp", "90"),
            OtsEvent.KIND to KindName("OTS", "03"),
            PaymentTargetsEvent.KIND to KindName("PayTo", null),
            FollowSetEvent.KIND to KindName("People Lists", "51"),
            ProfileBadgesEvent.KIND to KindName("Profile Badges", "58"),
            PictureEvent.KIND to KindName("Pictures", "68"),
            WorkoutRecordEvent.KIND to KindName("Workouts", null),
            PinListEvent.KIND to KindName("Pins", "51"),
            PnsEvent.KIND to KindName("Private Note Storage", null),
            ZapPollEvent.KIND to KindName("Zap Poll", null),
            PollEvent.KIND to KindName("Poll", "88"),
            PollResponseEvent.KIND to KindName("Poll Response", "88"),
            EncryptedDmEvent.KIND to KindName("NIP-04 DMs", "04"),
            PrivateOutboxRelayListEvent.KIND to KindName("Private Relays", "37"),
            ProxyRelayListEvent.KIND to KindName("Proxy Relays", "51"),
            PublicMessageEvent.KIND to KindName("Public Message", "A4"),
            ReactionEvent.KIND to KindName("Reactions", "25"),
            UserAssertionEvent.KIND to KindName("Contact Card", "85"),
            RelayAuthEvent.KIND to KindName("Relay Auth", "42"),
            RelayDiscoveryEvent.KIND to KindName("Relay Discovery", "66"),
            RelayMonitorEvent.KIND to KindName("Relay Monitor Announcement", "66"),
            RelaySetEvent.KIND to KindName("Relay Set", "51"),
            ReportEvent.KIND to KindName("Reports", "56"),
            RepostEvent.KIND to KindName("Reposts", "18"),
            RequestToVanishEvent.KIND to KindName("User Delete", "62"),
            SealEvent.KIND to KindName("Seals", "59"),
            SearchRelayListEvent.KIND to KindName("Search Relays", "50"),
            UserStatusEvent.KIND to KindName("User Status", "38"),
            TextNoteEvent.KIND to KindName("Notes", "10"),
            TextNoteModificationEvent.KIND to KindName("Edits", null),
            TorrentEvent.KIND to KindName("Torrents", "35"),
            TorrentCommentEvent.KIND to KindName("Torrent Comments", "35"),
            TrustedRelayListEvent.KIND to KindName("Trusted Relays", "51"),
            TrustProviderListEvent.KIND to KindName("Trusted Providers", "85"),
            AddressableNormalVideoEvent.KIND to KindName("Video (Repl)", "71"),
            AddressableShortVideoEvent.KIND to KindName("Shorts (Repl)", "71"),
            VideoNormalEvent.KIND to KindName("Video", "71"),
            VideoShortEvent.KIND to KindName("Shorts", "71"),
            VideoCollaborationEvent.KIND to KindName("Video Collaboration", null),
            TextTrackEvent.KIND to KindName("Video Subtitles", null),
            VideoViewEvent.KIND to KindName("Video Views", null),
            PushRegistrationEvent.KIND to KindName("Push Registration", null),
            PushDeregistrationEvent.KIND to KindName("Push Deregistration", null),
            PushPreferencesEvent.KIND to KindName("Push Preferences", null),
            VoiceEvent.KIND to KindName("Voice Msg", "A0"),
            VoiceReplyEvent.KIND to KindName("Voice Reply", "A0"),
            WakeUpEvent.KIND to KindName("WakeUp", null),
            WebBookmarkEvent.KIND to KindName("Web Bookmark", "B0"),
            WikiArticleEvent.KIND to KindName("Wiki", "54"),
            WikiMergeRequestEvent.KIND to KindName("Wiki Merge Request", "54"),
            WikiMergeAcceptanceEvent.KIND to KindName("Wiki Merge Accepted", null),
            WikiRedirectEvent.KIND to KindName("Wiki Redirect", "54"),
            ExternalReactionEvent.KIND to KindName("External Reaction", "25"),
            ExternalCitationEvent.KIND to KindName("Citation (Web)", null),
            HardcopyCitationEvent.KIND to KindName("Citation (Print)", null),
            PromptCitationEvent.KIND to KindName("Citation (Prompt)", null),
            LearningResourceEvent.KIND to KindName("Learning Resource", null),
            BookshelfDirectoryEvent.KIND to KindName("Directory", null),
            BlossomPieceIndexEvent.KIND to KindName("Blossom Piece Index", null),
            EntityRatingEvent.KIND to KindName("Rating", null),
            PublicationIndexEvent.KIND to KindName("Publication", null),
            PublicationContentEvent.KIND to KindName("Publication Section", null),
            RelayReviewEvent.KIND to KindName("Relay Review", null),
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
            DvmTextExtractionRequestEvent.KIND to KindName("DVM Text Extraction Req", "90"),
            DvmSummarizationRequestEvent.KIND to KindName("DVM Summarization Req", "90"),
            DvmTranslationRequestEvent.KIND to KindName("DVM Translation Req", "90"),
            DvmTextGenerationRequestEvent.KIND to KindName("DVM Text Generation Req", "90"),
            DvmImageGenerationRequestEvent.KIND to KindName("DVM Image Generation Req", "90"),
            NappletSnapshotEvent.KIND to KindName("Napplet Snapshot", "5D"),
            SiteSnapshotEvent.KIND to KindName("Website Snapshot", "5A"),
            DvmVideoConversionRequestEvent.KIND to KindName("DVM Video Conversion Req", "90"),
            DvmVideoTranslationRequestEvent.KIND to KindName("DVM Video Translation Req", "90"),
            DvmImageToVideoRequestEvent.KIND to KindName("DVM Image To Video Req", "90"),
            DvmTextToSpeechRequestEvent.KIND to KindName("DVM Text To Speech Req", "90"),
            DvmContentSearchRequestEvent.KIND to KindName("DVM Content Search Req", "90"),
            DvmPeopleSearchRequestEvent.KIND to KindName("DVM People Search Req", "90"),
            DvmEventCountRequestEvent.KIND to KindName("DVM Event Count Req", "90"),
            DvmMalwareScanRequestEvent.KIND to KindName("DVM Malware Scan Req", "90"),
            DvmEventTimestampingRequestEvent.KIND to KindName("DVM Timestamping Req", "90"),
            DvmOpReturnRequestEvent.KIND to KindName("DVM OpReturn Req", "90"),
            DvmEventPublishScheduleRequestEvent.KIND to KindName("DVM Publish Schedule Req", "90"),
            DvmEventPowDelegationRequestEvent.KIND to KindName("DVM PoW Delegation Req", "90"),
            DvmTextExtractionResponseEvent.KIND to KindName("DVM Text Extraction Resp", "90"),
            DvmSummarizationResponseEvent.KIND to KindName("DVM Summarization Resp", "90"),
            DvmTranslationResponseEvent.KIND to KindName("DVM Translation Resp", "90"),
            DvmTextGenerationResponseEvent.KIND to KindName("DVM Text Generation Resp", "90"),
            DvmImageGenerationResponseEvent.KIND to KindName("DVM Image Generation Resp", "90"),
            DvmVideoConversionResponseEvent.KIND to KindName("DVM Video Conversion Resp", "90"),
            DvmVideoTranslationResponseEvent.KIND to KindName("DVM Video Translation Resp", "90"),
            DvmImageToVideoResponseEvent.KIND to KindName("DVM Image To Video Resp", "90"),
            DvmTextToSpeechResponseEvent.KIND to KindName("DVM Text To Speech Resp", "90"),
            DvmContentSearchResponseEvent.KIND to KindName("DVM Content Search Resp", "90"),
            DvmPeopleSearchResponseEvent.KIND to KindName("DVM People Search Resp", "90"),
            DvmEventCountResponseEvent.KIND to KindName("DVM Event Count Resp", "90"),
            DvmMalwareScanResponseEvent.KIND to KindName("DVM Malware Scan Resp", "90"),
            DvmEventTimestampingResponseEvent.KIND to KindName("DVM Timestamping Resp", "90"),
            DvmOpReturnResponseEvent.KIND to KindName("DVM OpReturn Resp", "90"),
            DvmEventPublishScheduleResponseEvent.KIND to KindName("DVM Publish Schedule Resp", "90"),
            DvmEventPowDelegationResponseEvent.KIND to KindName("DVM PoW Delegation Resp", "90"),
            CashuMintQuoteEvent.KIND to KindName("Cashu Mint Quote", "60"),
            CashuTokenEvent.KIND to KindName("Cashu Token", "60"),
            CashuSpendingHistoryEvent.KIND to KindName("Cashu History", "60"),
            RelayAddMemberEvent.KIND to KindName("Relay Add Member", "43"),
            RelayRemoveMemberEvent.KIND to KindName("Relay Remove Member", "43"),
            OnchainZapEvent.KIND to KindName("Onchain Zap", "BC"),
            Bolt12ZapEvent.KIND to KindName("Bolt12 Zap", "B1"),
            Bolt12OfferListEvent.KIND to KindName("Bolt12 Offers", "B1"),
            GroupPutUserEvent.KIND to KindName("Group Put User", "29"),
            GroupRemoveUserEvent.KIND to KindName("Group Remove User", "29"),
            GroupEditMetadataEvent.KIND to KindName("Group Edit Metadata", "29"),
            GroupDeleteEventEvent.KIND to KindName("Group Delete Event", "29"),
            CreateGroupEvent.KIND to KindName("Group Create", "29"),
            DeleteGroupEvent.KIND to KindName("Group Delete", "29"),
            GroupCreateInviteEvent.KIND to KindName("Group Create Invite", "29"),
            GroupJoinRequestEvent.KIND to KindName("Group Join Request", "29"),
            GroupLeaveRequestEvent.KIND to KindName("Group Leave Request", "29"),
            NutzapEvent.KIND to KindName("Nutzap", "61"),
            SimpleGroupListEvent.KIND to KindName("Group List", "51"),
            ExternalIdentitiesEvent.KIND to KindName("External Identities", "39"),
            GitAuthorListEvent.KIND to KindName("Git Authors", "51"),
            GitRepositoryListEvent.KIND to KindName("Git Repos", "51"),
            NutzapInfoEvent.KIND to KindName("Nutzap Info", "61"),
            MediaFollowListEvent.KIND to KindName("Media Follows", "51"),
            EncryptionKeyListEvent.KIND to KindName("Encryption Keys", null),
            EncryptionKeyRequestEvent.KIND to KindName("Encryption Key Request", null),
            EncryptionKeyTransferEvent.KIND to KindName("Encryption Key Transfer", null),
            KeyPackageRelayListEvent.KIND to KindName("MLS KeyPackage Relays", null),
            FavoriteAlgoFeedsListEvent.KIND to KindName("Favorite Feeds", "51"),
            FavoriteFollowSetsListEvent.KIND to KindName("Favorite Follow Sets", "51"),
            GoodWikiAuthorListEvent.KIND to KindName("Wiki Authors", "51"),
            GoodWikiRelayListEvent.KIND to KindName("Wiki Relays", "51"),
            UserGraspListEvent.KIND to KindName("GRASP Servers", "34"),
            BirdexEvent.KIND to KindName("Birdex", null),
            BirdDetectionEvent.KIND to KindName("Bird Detection", null),
            Ps1SaveEvent.KIND to KindName("PS1 Save", null),
            NwcInfoEvent.KIND to KindName("NWC Info", "47"),
            RelayMembershipListEvent.KIND to KindName("Relay Memberships", "43"),
            RelayRoleEvent.KIND to KindName("Relay Role", "43"),
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
            UserTrustedListEvent.KIND to KindName("Trusted List of Pubkeys", null),
            EventTrustedListEvent.KIND to KindName("Trusted List of Events", null),
            AddressableTrustedListEvent.KIND to KindName("Trusted List of Addressables", null),
            ExternalIdTrustedListEvent.KIND to KindName("Trusted List of External IDs", null),
            ListHeaderEvent.KIND to KindName("Decentralized List", null),
            AddressableListHeaderEvent.KIND to KindName("Editable Decentralized List", null),
            ListItemEvent.KIND to KindName("Decentralized List Item", null),
            AddressableListItemEvent.KIND to KindName("Editable Decentralized List Item", null),
            KeyPackageEvent.KIND to KindName("MLS KeyPackage", null),
            GitRepositoryStateEvent.KIND to KindName("Git Repo State", "34"),
            FeedDefinitionEvent.KIND to KindName("Feed Definition", null),
            SoftwareApplicationEvent.KIND to KindName("Software Application", "82"),
            ExerciseTemplateEvent.KIND to KindName("Exercise Template", null),
            WorkoutTemplateEvent.KIND to KindName("Workout Template", null),
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
            GroupRolesEvent.KIND to KindName("Group Roles", "29"),
            GroupParticipantsEvent.KIND to KindName("Group Participants", "29"),
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
