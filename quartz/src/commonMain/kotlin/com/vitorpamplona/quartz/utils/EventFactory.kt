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

import com.vitorpamplona.quartz.buzz.aeEngrams.EngramEvent
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactRemovalEvent
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadWindowBoundsEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.WindowBoundsEvent
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.erReminders.EventReminderEvent
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleGuidelinesEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleLivenessEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleReactionEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentitiesListEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationBanEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationResolveReportEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationTimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationUntimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ProductFeedbackEvent
import com.vitorpamplona.quartz.buzz.mpProjects.ProjectEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.buzz.pairing.PairingEvent
import com.vitorpamplona.quartz.buzz.plPushLease.PushLeaseEvent
import com.vitorpamplona.quartz.buzz.presence.PresenceUpdateEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminChangeRoleEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageBookmarkedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessagePinnedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageScheduledEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.StreamReminderEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.PresenceSnapshotEvent
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.buzz.wpWorkspaceProfile.SetWorkspaceProfileEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEvent
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmToolsListEvent
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoObjectEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoShardEvent
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.ballots.BallotEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
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
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.UnrecognizedKind30301Event
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.UnrecognizedKind30302Event
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
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RelayReviewEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.experimental.zapstore.identityProof.IdentityProofEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.LegacyKeyPackageEvent
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
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
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
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
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
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.MostroDevFeePaymentEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.MostroDisputeEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.UnrecognizedKind38386Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.UnrecognizedKind38385Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.MostroUserRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.UnrecognizedKind38384Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.RoboSatsCoordinatorRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.UnrecognizedKind31986Event
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
import com.vitorpamplona.quartz.nip87Ecash.recommendation.UnrecognizedKind38000Event
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentSearch.DvmContentSearchResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
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
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent
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
                ConcordChatEditEvent.KIND -> ConcordChatEditEvent(id, pubKey, createdAt, tags, content, sig)
                ConcordTimerNoticeEvent.KIND -> ConcordTimerNoticeEvent(id, pubKey, createdAt, tags, content, sig)
                AdvertisedRelayListEvent.KIND -> AdvertisedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                CvmServerAnnouncementEvent.KIND -> CvmServerAnnouncementEvent(id, pubKey, createdAt, tags, content, sig)
                CvmToolsListEvent.KIND -> CvmToolsListEvent(id, pubKey, createdAt, tags, content, sig)
                AgentTurnMetricEvent.KIND -> AgentTurnMetricEvent(id, pubKey, createdAt, tags, content, sig)
                EngramEvent.KIND -> EngramEvent(id, pubKey, createdAt, tags, content, sig)
                AgentProfileEvent.KIND -> AgentProfileEvent(id, pubKey, createdAt, tags, content, sig)
                ObserverFrameEvent.KIND -> ObserverFrameEvent(id, pubKey, createdAt, tags, content, sig)
                PersonaEvent.KIND -> PersonaEvent(id, pubKey, createdAt, tags, content, sig)
                ArtifactEvent.KIND -> ArtifactEvent(id, pubKey, createdAt, tags, content, sig)
                ArtifactRemovalEvent.KIND -> ArtifactRemovalEvent(id, pubKey, createdAt, tags, content, sig)
                AuditEntryEvent.KIND -> AuditEntryEvent(id, pubKey, createdAt, tags, content, sig)
                WindowBoundsEvent.KIND -> WindowBoundsEvent(id, pubKey, createdAt, tags, content, sig)
                ThreadWindowBoundsEvent.KIND -> ThreadWindowBoundsEvent(id, pubKey, createdAt, tags, content, sig)
                DmAddMemberEvent.KIND -> DmAddMemberEvent(id, pubKey, createdAt, tags, content, sig)
                DmCreatedEvent.KIND -> DmCreatedEvent(id, pubKey, createdAt, tags, content, sig)
                DmHideEvent.KIND -> DmHideEvent(id, pubKey, createdAt, tags, content, sig)
                DmOpenEvent.KIND -> DmOpenEvent(id, pubKey, createdAt, tags, content, sig)
                DmVisibilityEvent.KIND -> DmVisibilityEvent(id, pubKey, createdAt, tags, content, sig)
                EventReminderEvent.KIND -> EventReminderEvent(id, pubKey, createdAt, tags, content, sig)
                ForumCommentEvent.KIND -> ForumCommentEvent(id, pubKey, createdAt, tags, content, sig)
                ForumPostEvent.KIND -> ForumPostEvent(id, pubKey, createdAt, tags, content, sig)
                ForumVoteEvent.KIND -> ForumVoteEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleEndedEvent.KIND -> HuddleEndedEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleGuidelinesEvent.KIND -> HuddleGuidelinesEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleLivenessEvent.KIND -> HuddleLivenessEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleParticipantJoinedEvent.KIND -> HuddleParticipantJoinedEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleParticipantLeftEvent.KIND -> HuddleParticipantLeftEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleReactionEvent.KIND -> HuddleReactionEvent(id, pubKey, createdAt, tags, content, sig)
                HuddleStartedEvent.KIND -> HuddleStartedEvent(id, pubKey, createdAt, tags, content, sig)
                ArchiveRequestEvent.KIND -> ArchiveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                ArchivedIdentitiesListEvent.KIND -> ArchivedIdentitiesListEvent(id, pubKey, createdAt, tags, content, sig)
                ArchivedIdentityEvent.KIND -> ArchivedIdentityEvent(id, pubKey, createdAt, tags, content, sig)
                UnarchiveRequestEvent.KIND -> UnarchiveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                UnarchivedIdentityEvent.KIND -> UnarchivedIdentityEvent(id, pubKey, createdAt, tags, content, sig)
                JobAcceptedEvent.KIND -> JobAcceptedEvent(id, pubKey, createdAt, tags, content, sig)
                JobCancelEvent.KIND -> JobCancelEvent(id, pubKey, createdAt, tags, content, sig)
                JobErrorEvent.KIND -> JobErrorEvent(id, pubKey, createdAt, tags, content, sig)
                JobProgressEvent.KIND -> JobProgressEvent(id, pubKey, createdAt, tags, content, sig)
                JobRequestEvent.KIND -> JobRequestEvent(id, pubKey, createdAt, tags, content, sig)
                JobResultEvent.KIND -> JobResultEvent(id, pubKey, createdAt, tags, content, sig)
                ManagedAgentEvent.KIND -> ManagedAgentEvent(id, pubKey, createdAt, tags, content, sig)
                ModerationBanEvent.KIND -> ModerationBanEvent(id, pubKey, createdAt, tags, content, sig)
                ModerationResolveReportEvent.KIND -> ModerationResolveReportEvent(id, pubKey, createdAt, tags, content, sig)
                ModerationTimeoutEvent.KIND -> ModerationTimeoutEvent(id, pubKey, createdAt, tags, content, sig)
                ModerationUntimeoutEvent.KIND -> ModerationUntimeoutEvent(id, pubKey, createdAt, tags, content, sig)
                ProductFeedbackEvent.KIND -> ProductFeedbackEvent(id, pubKey, createdAt, tags, content, sig)
                ProjectEvent.KIND -> ProjectEvent(id, pubKey, createdAt, tags, content, sig)
                MemberAddedNotificationEvent.KIND -> MemberAddedNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                MemberRemovedNotificationEvent.KIND -> MemberRemovedNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                PairingEvent.KIND -> PairingEvent(id, pubKey, createdAt, tags, content, sig)
                PushLeaseEvent.KIND -> PushLeaseEvent(id, pubKey, createdAt, tags, content, sig)
                TypingIndicatorEvent.KIND -> TypingIndicatorEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAdminAddMemberEvent.KIND -> RelayAdminAddMemberEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAdminChangeRoleEvent.KIND -> RelayAdminChangeRoleEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAdminRemoveMemberEvent.KIND -> RelayAdminRemoveMemberEvent(id, pubKey, createdAt, tags, content, sig)
                CanvasEvent.KIND -> CanvasEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessageBookmarkedEvent.KIND -> StreamMessageBookmarkedEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessageDiffEvent.KIND -> StreamMessageDiffEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessageEditEvent.KIND -> StreamMessageEditEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessagePinnedEvent.KIND -> StreamMessagePinnedEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessageScheduledEvent.KIND -> StreamMessageScheduledEvent(id, pubKey, createdAt, tags, content, sig)
                StreamMessageV2Event.KIND -> StreamMessageV2Event(id, pubKey, createdAt, tags, content, sig)
                StreamReminderEvent.KIND -> StreamReminderEvent(id, pubKey, createdAt, tags, content, sig)
                SystemMessageEvent.KIND -> SystemMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelSummaryEvent.KIND -> ChannelSummaryEvent(id, pubKey, createdAt, tags, content, sig)
                PresenceSnapshotEvent.KIND -> PresenceSnapshotEvent(id, pubKey, createdAt, tags, content, sig)
                TeamEvent.KIND -> TeamEvent(id, pubKey, createdAt, tags, content, sig)
                TeamCatalogEvent.KIND -> TeamCatalogEvent(id, pubKey, createdAt, tags, content, sig)
                ApprovalDenyEvent.KIND -> ApprovalDenyEvent(id, pubKey, createdAt, tags, content, sig)
                ApprovalGrantEvent.KIND -> ApprovalGrantEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowApprovalDeniedEvent.KIND -> WorkflowApprovalDeniedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowApprovalGrantedEvent.KIND -> WorkflowApprovalGrantedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowApprovalRequestedEvent.KIND -> WorkflowApprovalRequestedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowCancelledEvent.KIND -> WorkflowCancelledEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowCompletedEvent.KIND -> WorkflowCompletedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowDefEvent.KIND -> WorkflowDefEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowFailedEvent.KIND -> WorkflowFailedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowStepCompletedEvent.KIND -> WorkflowStepCompletedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowStepFailedEvent.KIND -> WorkflowStepFailedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowStepStartedEvent.KIND -> WorkflowStepStartedEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowTriggerEvent.KIND -> WorkflowTriggerEvent(id, pubKey, createdAt, tags, content, sig)
                WorkflowTriggeredEvent.KIND -> WorkflowTriggeredEvent(id, pubKey, createdAt, tags, content, sig)
                SetWorkspaceProfileEvent.KIND -> SetWorkspaceProfileEvent(id, pubKey, createdAt, tags, content, sig)
                AppCurationSetEvent.KIND -> AppCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                AppDefinitionEvent.KIND -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                AppRecommendationEvent.KIND -> AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                AppDataEvent.KIND -> AppDataEvent(id, pubKey, createdAt, tags, content, sig)
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
                BirdDetectionEvent.KIND -> BirdDetectionEvent(id, pubKey, createdAt, tags, content, sig)
                BirdexEvent.KIND -> BirdexEvent(id, pubKey, createdAt, tags, content, sig)
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
                CalendarCollectionEvent.KIND -> CalendarCollectionEvent(id, pubKey, createdAt, tags, content, sig)
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
                GroupPutUserEvent.KIND -> GroupPutUserEvent(id, pubKey, createdAt, tags, content, sig)
                GroupRemoveUserEvent.KIND -> GroupRemoveUserEvent(id, pubKey, createdAt, tags, content, sig)
                GroupEditMetadataEvent.KIND -> GroupEditMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                GroupDeleteEventEvent.KIND -> GroupDeleteEventEvent(id, pubKey, createdAt, tags, content, sig)
                CreateGroupEvent.KIND -> CreateGroupEvent(id, pubKey, createdAt, tags, content, sig)
                DeleteGroupEvent.KIND -> DeleteGroupEvent(id, pubKey, createdAt, tags, content, sig)
                GroupCreateInviteEvent.KIND -> GroupCreateInviteEvent(id, pubKey, createdAt, tags, content, sig)
                GroupJoinRequestEvent.KIND -> GroupJoinRequestEvent(id, pubKey, createdAt, tags, content, sig)
                GroupLeaveRequestEvent.KIND -> GroupLeaveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                GroupMetadataEvent.KIND -> GroupMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                GroupAdminsEvent.KIND -> GroupAdminsEvent(id, pubKey, createdAt, tags, content, sig)
                GroupMembersEvent.KIND -> GroupMembersEvent(id, pubKey, createdAt, tags, content, sig)
                GroupRolesEvent.KIND -> GroupRolesEvent(id, pubKey, createdAt, tags, content, sig)
                GroupParticipantsEvent.KIND -> GroupParticipantsEvent(id, pubKey, createdAt, tags, content, sig)
                // kind:39005 is shared by two relay-signed, addressable events that never coexist on
                // one relay: NIP-29's group pin list (relay29 family) and Buzz's NIP-CW thread
                // summary. Disambiguate by Buzz's required `h` (channel) tag — a thread summary is
                // addressed by `d` = the thread ROOT EVENT ID and also carries `e` = that root and
                // `h` = the channel, while a pin list is addressed by `d` = the GROUP ID with the
                // pinned ids as `e` tags and no `h` at all.
                //
                // Without this, a Buzz thread summary parses as a GroupPinnedEvent — silently, since
                // nothing throws — and `pinnedEventIds()` would read the summary's `e` tag and report
                // the thread root as a pinned message. Buzz publishes these live to channel
                // subscribers (`side_effects.rs`, Redis fan-out to `EventTopic::Channel`), not only on
                // its HTTP bridge, so any future channel-scoped filter that asks for 39005 receives
                // them. Buzz's own pinned message is a different kind entirely (40004).
                GroupPinnedEvent.KIND ->
                    if (tags.any { it.size > 1 && it[0] == GroupIdTag.TAG_NAME }) {
                        ThreadSummaryEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        GroupPinnedEvent(id, pubKey, createdAt, tags, content, sig)
                    }
                GroupUpdatePinListEvent.KIND -> GroupUpdatePinListEvent(id, pubKey, createdAt, tags, content, sig)
                ChessGameEvent.KIND -> ChessGameEvent(id, pubKey, createdAt, tags, content, sig)
                CodeSnippetEvent.KIND -> CodeSnippetEvent(id, pubKey, createdAt, tags, content, sig)
                FavoriteRelayListEvent.KIND -> FavoriteRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                JesterEvent.KIND -> JesterEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameChallengeEvent.KIND -> LiveChessGameChallengeEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameAcceptEvent.KIND -> LiveChessGameAcceptEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessMoveEvent.KIND -> LiveChessMoveEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessGameEndEvent.KIND -> LiveChessGameEndEvent(id, pubKey, createdAt, tags, content, sig)
                LiveChessDrawOfferEvent.KIND -> LiveChessDrawOfferEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelCreateEvent.KIND -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelHideMessageEvent.KIND -> ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
                PublicChatListEvent.KIND -> PublicChatListEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMessageEvent.KIND -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMetadataEvent.KIND -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMuteUserEvent.KIND -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
                ChatMessageEncryptedFileHeaderEvent.KIND -> ChatMessageEncryptedFileHeaderEvent(id.ifBlank { EventHasher.hashId(pubKey, createdAt, kind, tags, content) }, pubKey, createdAt, tags, content, sig)
                ChatMessageEvent.KIND -> ChatMessageEvent(id.ifBlank { EventHasher.hashId(pubKey, createdAt, kind, tags, content) }, pubKey, createdAt, tags, content, sig)
                DmRelayListEvent.KIND -> DmRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ClassifiedsEvent.KIND -> ClassifiedsEvent(id, pubKey, createdAt, tags, content, sig)
                CommentEvent.KIND -> CommentEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityDefinitionEvent.KIND -> CommunityDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityListEvent.KIND -> CommunityListEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityPostApprovalEvent.KIND -> CommunityPostApprovalEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityRulesEvent.KIND -> CommunityRulesEvent(id, pubKey, createdAt, tags, content, sig)
                ContactListEvent.KIND -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
                DeletionRequestEvent.KIND -> DeletionRequestEvent(id, pubKey, createdAt, tags, content, sig)
                EncryptionKeyRequestEvent.KIND -> EncryptionKeyRequestEvent(id, pubKey, createdAt, tags, content, sig)
                EncryptionKeyTransferEvent.KIND -> EncryptionKeyTransferEvent(id, pubKey, createdAt, tags, content, sig)
                DraftWrapEvent.KIND -> DraftWrapEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiPackEvent.KIND -> EmojiPackEvent(id, pubKey, createdAt, tags, content, sig)
                StickerPackEvent.KIND -> StickerPackEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiListEvent.KIND -> EmojiListEvent(id, pubKey, createdAt, tags, content, sig)
                EphemeralChatEvent.KIND -> EphemeralChatEvent(id, pubKey, createdAt, tags, content, sig)
                EphemeralChatListEvent.KIND -> EphemeralChatListEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalIdentitiesEvent.KIND -> ExternalIdentitiesEvent(id, pubKey, createdAt, tags, content, sig)
                FedimintEvent.KIND -> FedimintEvent(id, pubKey, createdAt, tags, content, sig)
                FeedDefinitionEvent.KIND -> FeedDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                FileMetadataEvent.KIND -> FileMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                ProfileGalleryEntryEvent.KIND -> ProfileGalleryEntryEvent(id, pubKey, createdAt, tags, content, sig)
                FileServersEvent.KIND -> FileServersEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageEvent.KIND -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageHeaderEvent.KIND -> FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                FhirResourceEvent.KIND -> FhirResourceEvent(id, pubKey, createdAt, tags, content, sig)
                StarterPackEvent.KIND -> StarterPackEvent(id, pubKey, createdAt, tags, content, sig)
                FundraiserEvent.KIND -> FundraiserEvent(id, pubKey, createdAt, tags, content, sig)
                GenericRepostEvent.KIND -> GenericRepostEvent(id, pubKey, createdAt, tags, content, sig)
                CyberspaceBagEvent.KIND -> CyberspaceBagEvent(id, pubKey, createdAt, tags, content, sig)
                SnoObjectEvent.KIND -> SnoObjectEvent(id, pubKey, createdAt, tags, content, sig)
                SnoAvatarEvent.KIND -> SnoAvatarEvent(id, pubKey, createdAt, tags, content, sig)
                SnoShardEvent.KIND -> SnoShardEvent(id, pubKey, createdAt, tags, content, sig)
                GeocacheListingEvent.KIND -> GeocacheListingEvent(id, pubKey, createdAt, tags, content, sig)
                GeocacheFoundLogEvent.KIND -> GeocacheFoundLogEvent(id, pubKey, createdAt, tags, content, sig)
                GeocacheVerificationEvent.KIND -> GeocacheVerificationEvent(id, pubKey, createdAt, tags, content, sig)
                GeocacheCurationListEvent.KIND -> GeocacheCurationListEvent(id, pubKey, createdAt, tags, content, sig)
                GeohashChatEvent.KIND -> GeohashChatEvent(id, pubKey, createdAt, tags, content, sig)
                GeohashListEvent.KIND -> GeohashListEvent(id, pubKey, createdAt, tags, content, sig)
                // kind:20001 is shared by BitChat's GeohashPresenceEvent and Buzz's
                // PresenceUpdateEvent. Disambiguate by BitChat's required `g` (geohash) tag:
                // BitChat presence always carries one (empty content); Buzz presence never does
                // (the status lives in content, plus a `status` tag from clients or a `p` tag on
                // relay-synthesized reads). This routes both inbound parse and outbound signing,
                // since both go through this factory.
                GeohashPresenceEvent.KIND ->
                    if (tags.any { it.size > 1 && it[0] == "g" }) {
                        GeohashPresenceEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        PresenceUpdateEvent(id, pubKey, createdAt, tags, content, sig)
                    }
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
                GitCoverNoteEvent.KIND -> GitCoverNoteEvent(id, pubKey, createdAt, tags, content, sig)
                CiManualTriggerEvent.KIND -> CiManualTriggerEvent(id, pubKey, createdAt, tags, content, sig)
                CiJobResultEvent.KIND -> CiJobResultEvent(id, pubKey, createdAt, tags, content, sig)
                CiWorkflowResultEvent.KIND -> CiWorkflowResultEvent(id, pubKey, createdAt, tags, content, sig)
                CiServiceRequestEvent.KIND -> CiServiceRequestEvent(id, pubKey, createdAt, tags, content, sig)
                CiServiceStopEvent.KIND -> CiServiceStopEvent(id, pubKey, createdAt, tags, content, sig)
                CiWorkflowProgressEvent.KIND -> CiWorkflowProgressEvent(id, pubKey, createdAt, tags, content, sig)
                CiCoordinatorAdvertisementEvent.KIND -> CiCoordinatorAdvertisementEvent(id, pubKey, createdAt, tags, content, sig)
                CiRequestReadinessListEvent.KIND -> CiRequestReadinessListEvent(id, pubKey, createdAt, tags, content, sig)
                CiRepositoryStatusEvent.KIND -> CiRepositoryStatusEvent(id, pubKey, createdAt, tags, content, sig)
                CiSecretUpdateEvent.KIND -> CiSecretUpdateEvent(id, pubKey, createdAt, tags, content, sig)
                UserGraspListEvent.KIND -> UserGraspListEvent(id, pubKey, createdAt, tags, content, sig)
                GoodWikiAuthorListEvent.KIND -> GoodWikiAuthorListEvent(id, pubKey, createdAt, tags, content, sig)
                GoodWikiRelayListEvent.KIND -> GoodWikiRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ZapGoalEvent.KIND -> ZapGoalEvent(id, pubKey, createdAt, tags, content, sig)
                FavoriteAlgoFeedsListEvent.KIND -> FavoriteAlgoFeedsListEvent(id, pubKey, createdAt, tags, content, sig)
                FavoriteFollowSetsListEvent.KIND -> FavoriteFollowSetsListEvent(id, pubKey, createdAt, tags, content, sig)
                InterestListEvent.KIND -> InterestListEvent(id, pubKey, createdAt, tags, content, sig)
                HighlightEvent.KIND -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
                HTTPAuthorizationEvent.KIND -> HTTPAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                IndexerRelayListEvent.KIND -> IndexerRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                InterestSetEvent.KIND -> InterestSetEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryPrologueEvent.KIND -> InteractiveStoryPrologueEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStorySceneEvent.KIND -> InteractiveStorySceneEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryReadingStateEvent.KIND -> InteractiveStoryReadingStateEvent(id, pubKey, createdAt, tags, content, sig)
                LabelEvent.KIND -> LabelEvent(id, pubKey, createdAt, tags, content, sig)
                KindMuteSetEvent.KIND -> KindMuteSetEvent(id, pubKey, createdAt, tags, content, sig)
                BookmarkSetEvent.KIND -> BookmarkSetEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesChatMessageEvent.KIND -> LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesClipEvent.KIND -> LiveActivitiesClipEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesEvent.KIND -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesRaidEvent.KIND -> LiveActivitiesRaidEvent(id, pubKey, createdAt, tags, content, sig)
                ZapReceiptEvent.KIND -> ZapReceiptEvent(id, pubKey, createdAt, tags, content, sig)
                NwcRequestEvent.KIND -> NwcRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NwcResponseEvent.KIND -> NwcResponseEvent(id, pubKey, createdAt, tags, content, sig)
                OfferEvent.KIND -> OfferEvent(id, pubKey, createdAt, tags, content, sig)
                DebitEvent.KIND -> DebitEvent(id, pubKey, createdAt, tags, content, sig)
                ManageEvent.KIND -> ManageEvent(id, pubKey, createdAt, tags, content, sig)
                NwcInfoEvent.KIND -> NwcInfoEvent(id, pubKey, createdAt, tags, content, sig)
                NwcNotificationEvent.KIND -> NwcNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                NwcNotificationEvent.LEGACY_KIND -> NwcNotificationEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateZapEvent.KIND -> PrivateZapEvent(id, pubKey, createdAt, tags, content, sig)
                ZapRequestEvent.KIND -> ZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LongFormContentEvent.KIND -> LongFormContentEvent(id, pubKey, createdAt, tags, content, sig)
                LongFormDraftEvent.KIND -> LongFormDraftEvent(id, pubKey, createdAt, tags, content, sig)
                MarketplaceEvent.KIND -> MarketplaceEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomEvent.KIND -> MeetingRoomEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomPresenceEvent.KIND -> MeetingRoomPresenceEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingSpaceEvent.KIND -> MeetingSpaceEvent(id, pubKey, createdAt, tags, content, sig)
                AdminCommandEvent.KIND -> AdminCommandEvent(id, pubKey, createdAt, tags, content, sig)
                // kind:38000 is NIP-87's mint recommendation, but unrelated apps reuse the number:
                // BAO Markets publishes prediction markets on it, an auditable-voting app publishes
                // ballots, and most of what a big relay holds for it is spam (`d`-only "sybil test
                // votes"). Parsing all of it as a recommendation rendered markets, ballots and spam
                // as ecash mint cards. Disambiguate by tags, recommendation first — its `k` (the
                // recommended mint's kind) or, on old events without one, its `u` (mint URL) or the
                // mint's `a` — and fall back to UnrecognizedKind38000Event: still addressable, so
                // stores replace and delete it by `d`, but never indexed and drawn as no card.
                MintRecommendationEvent.KIND ->
                    when {
                        MintRecommendationEvent.isMintRecommendation(tags) -> MintRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                        BallotEvent.isBallot(tags) -> BallotEvent(id, pubKey, createdAt, tags, content, sig)
                        PredictionMarketEvent.isPredictionMarket(tags) -> PredictionMarketEvent(id, pubKey, createdAt, tags, content, sig)
                        else -> UnrecognizedKind38000Event(id, pubKey, createdAt, tags, content, sig)
                    }
                MediaFollowListEvent.KIND -> MediaFollowListEvent(id, pubKey, createdAt, tags, content, sig)
                MediaStarterPackEvent.KIND -> MediaStarterPackEvent(id, pubKey, createdAt, tags, content, sig)
                MetadataEvent.KIND -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
                MusicPlaylistEvent.KIND -> MusicPlaylistEvent(id, pubKey, createdAt, tags, content, sig)
                MusicTrackEvent.KIND -> MusicTrackEvent(id, pubKey, createdAt, tags, content, sig)
                MuteListEvent.KIND -> MuteListEvent(id, pubKey, createdAt, tags, content, sig)
                KeyPackageEvent.KIND -> KeyPackageEvent(id, pubKey, createdAt, tags, content, sig)
                KeyPackageRelayListEvent.KIND -> KeyPackageRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                LegacyKeyPackageEvent.KIND -> LegacyKeyPackageEvent(id, pubKey, createdAt, tags, content, sig)
                WelcomeEvent.KIND -> WelcomeEvent(id, pubKey, createdAt, tags, content, sig)
                GroupEvent.KIND -> GroupEvent(id, pubKey, createdAt, tags, content, sig)
                NotificationRequestEvent.KIND -> NotificationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                TokenRequestEvent.KIND -> TokenRequestEvent(id, pubKey, createdAt, tags, content, sig)
                TokenListEvent.KIND -> TokenListEvent(id, pubKey, createdAt, tags, content, sig)
                TokenRemovalEvent.KIND -> TokenRemovalEvent(id, pubKey, createdAt, tags, content, sig)
                NamedSiteEvent.KIND -> NamedSiteEvent(id, pubKey, createdAt, tags, content, sig)
                NappletSnapshotEvent.KIND -> NappletSnapshotEvent(id, pubKey, createdAt, tags, content, sig)
                RootNappletEvent.KIND -> RootNappletEvent(id, pubKey, createdAt, tags, content, sig)
                NamedNappletEvent.KIND -> NamedNappletEvent(id, pubKey, createdAt, tags, content, sig)
                NNSEvent.KIND -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
                NipTextEvent.KIND -> NipTextEvent(id, pubKey, createdAt, tags, content, sig)
                NutzapEvent.KIND -> NutzapEvent(id, pubKey, createdAt, tags, content, sig)
                NutzapInfoEvent.KIND -> NutzapInfoEvent(id, pubKey, createdAt, tags, content, sig)
                NostrConnectEvent.KIND -> NostrConnectEvent(id, pubKey, createdAt, tags, content, sig)
                DvmStatusEvent.KIND -> DvmStatusEvent(id, pubKey, createdAt, tags, content, sig)
                DvmHeartbeatEvent.KIND -> DvmHeartbeatEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextExtractionRequestEvent.KIND -> DvmTextExtractionRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextExtractionResponseEvent.KIND -> DvmTextExtractionResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmSummarizationRequestEvent.KIND -> DvmSummarizationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmSummarizationResponseEvent.KIND -> DvmSummarizationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTranslationRequestEvent.KIND -> DvmTranslationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTranslationResponseEvent.KIND -> DvmTranslationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextGenerationRequestEvent.KIND -> DvmTextGenerationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextGenerationResponseEvent.KIND -> DvmTextGenerationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmImageGenerationRequestEvent.KIND -> DvmImageGenerationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmImageGenerationResponseEvent.KIND -> DvmImageGenerationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmVideoConversionRequestEvent.KIND -> DvmVideoConversionRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmVideoConversionResponseEvent.KIND -> DvmVideoConversionResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmVideoTranslationRequestEvent.KIND -> DvmVideoTranslationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmVideoTranslationResponseEvent.KIND -> DvmVideoTranslationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmImageToVideoRequestEvent.KIND -> DvmImageToVideoRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmImageToVideoResponseEvent.KIND -> DvmImageToVideoResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextToSpeechRequestEvent.KIND -> DvmTextToSpeechRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmTextToSpeechResponseEvent.KIND -> DvmTextToSpeechResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmContentDiscoveryRequestEvent.KIND -> DvmContentDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmContentDiscoveryResponseEvent.KIND -> DvmContentDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmUserDiscoveryRequestEvent.KIND -> DvmUserDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmUserDiscoveryResponseEvent.KIND -> DvmUserDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmContentSearchRequestEvent.KIND -> DvmContentSearchRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmContentSearchResponseEvent.KIND -> DvmContentSearchResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmPeopleSearchRequestEvent.KIND -> DvmPeopleSearchRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmPeopleSearchResponseEvent.KIND -> DvmPeopleSearchResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventCountRequestEvent.KIND -> DvmEventCountRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventCountResponseEvent.KIND -> DvmEventCountResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmMalwareScanRequestEvent.KIND -> DvmMalwareScanRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmMalwareScanResponseEvent.KIND -> DvmMalwareScanResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventTimestampingRequestEvent.KIND -> DvmEventTimestampingRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventTimestampingResponseEvent.KIND -> DvmEventTimestampingResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmOpReturnRequestEvent.KIND -> DvmOpReturnRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmOpReturnResponseEvent.KIND -> DvmOpReturnResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventPublishScheduleRequestEvent.KIND -> DvmEventPublishScheduleRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventPublishScheduleResponseEvent.KIND -> DvmEventPublishScheduleResponseEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventPowDelegationRequestEvent.KIND -> DvmEventPowDelegationRequestEvent(id, pubKey, createdAt, tags, content, sig)
                DvmEventPowDelegationResponseEvent.KIND -> DvmEventPowDelegationResponseEvent(id, pubKey, createdAt, tags, content, sig)
                OnchainZapEvent.KIND -> OnchainZapEvent(id, pubKey, createdAt, tags, content, sig)
                Bolt12ZapEvent.KIND -> Bolt12ZapEvent(id, pubKey, createdAt, tags, content, sig)
                Bolt12ZapIntentEvent.KIND -> Bolt12ZapIntentEvent(id, pubKey, createdAt, tags, content, sig)
                Bolt12OfferListEvent.KIND -> Bolt12OfferListEvent(id, pubKey, createdAt, tags, content, sig)
                OtsEvent.KIND -> OtsEvent(id, pubKey, createdAt, tags, content, sig)
                PaymentTargetsEvent.KIND -> PaymentTargetsEvent(id, pubKey, createdAt, tags, content, sig)
                FollowSetEvent.KIND -> FollowSetEvent(id, pubKey, createdAt, tags, content, sig)
                PictureCurationSetEvent.KIND -> PictureCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                P2POrderEvent.KIND -> P2POrderEvent(id, pubKey, createdAt, tags, content, sig)
                // Mostro split its kind 38383 into 38384 ratings / 38385 info / 38386 disputes,
                // but Paygress (a compute marketplace) publishes heartbeats, lease revocations and
                // promotion announcements on the same three numbers, and other apps add their own.
                // Each is told apart by its Mostro `z` tag; the rest stays addressable but unread.
                MostroUserRatingEvent.KIND ->
                    if (MostroUserRatingEvent.isMostroRating(tags)) {
                        MostroUserRatingEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        UnrecognizedKind38384Event(id, pubKey, createdAt, tags, content, sig)
                    }
                MostroInfoEvent.KIND ->
                    if (MostroInfoEvent.isMostroInfo(tags)) {
                        MostroInfoEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        UnrecognizedKind38385Event(id, pubKey, createdAt, tags, content, sig)
                    }
                MostroDisputeEvent.KIND ->
                    if (MostroDisputeEvent.isMostroDispute(tags)) {
                        MostroDisputeEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        UnrecognizedKind38386Event(id, pubKey, createdAt, tags, content, sig)
                    }
                MostroDevFeePaymentEvent.KIND -> MostroDevFeePaymentEvent(id, pubKey, createdAt, tags, content, sig)
                // RoboSats coordinator ratings carry a coordinator-signed token; Borkstr's NIP
                // compatibility reports share the number without one.
                RoboSatsCoordinatorRatingEvent.KIND ->
                    if (RoboSatsCoordinatorRatingEvent.isCoordinatorRating(tags)) {
                        RoboSatsCoordinatorRatingEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        UnrecognizedKind31986Event(id, pubKey, createdAt, tags, content, sig)
                    }
                // kind:30301 is a Kanban board in NIP PR #1665, but WalletScrutiny publishes its
                // reproducible-build verifications on it and an encrypted planner app its tasks.
                // Verifications carry `i` + `status`, boards a `title` or a named `col`; the planner's
                // events fall to UnrecognizedKind30301Event (addressable, never indexed, no edges).
                KanbanBoardEvent.KIND ->
                    when {
                        BuildVerificationEvent.isBuildVerification(tags) -> BuildVerificationEvent(id, pubKey, createdAt, tags, content, sig)
                        KanbanBoardEvent.isKanbanBoard(tags) -> KanbanBoardEvent(id, pubKey, createdAt, tags, content, sig)
                        else -> UnrecognizedKind30301Event(id, pubKey, createdAt, tags, content, sig)
                    }
                // Kanban cards (NIP PR #1665) share 30302 with Fieldbook's team memberships.
                KanbanCardEvent.KIND ->
                    if (KanbanCardEvent.isKanbanCard(tags)) {
                        KanbanCardEvent(id, pubKey, createdAt, tags, content, sig)
                    } else {
                        UnrecognizedKind30302Event(id, pubKey, createdAt, tags, content, sig)
                    }
                PictureEvent.KIND -> PictureEvent(id, pubKey, createdAt, tags, content, sig)
                PinListEvent.KIND -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
                PnsEvent.KIND -> PnsEvent(id, pubKey, createdAt, tags, content, sig)
                ProfileBadgesEvent.KIND -> ProfileBadgesEvent(id, pubKey, createdAt, tags, content, sig)
                ZapPollEvent.KIND -> ZapPollEvent(id, pubKey, createdAt, tags, content, sig)
                PollEvent.KIND -> PollEvent(id, pubKey, createdAt, tags, content, sig)
                PollResponseEvent.KIND -> PollResponseEvent(id, pubKey, createdAt, tags, content, sig)
                PodcastMetadataEvent.KIND -> PodcastMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                PodcastEpisodeEvent.KIND -> PodcastEpisodeEvent(id, pubKey, createdAt, tags, content, sig)
                Ps1SaveEvent.KIND -> Ps1SaveEvent(id, pubKey, createdAt, tags, content, sig)
                PostingStreakEvent.KIND -> PostingStreakEvent(id, pubKey, createdAt, tags, content, sig)
                ActiveProfileThemeEvent.KIND -> ActiveProfileThemeEvent(id, pubKey, createdAt, tags, content, sig)
                ThemeDefinitionEvent.KIND -> ThemeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                AuthoredPodcastsEvent.KIND -> AuthoredPodcastsEvent(id, pubKey, createdAt, tags, content, sig)
                FavoritePodcastsListEvent.KIND -> FavoritePodcastsListEvent(id, pubKey, createdAt, tags, content, sig)
                Podcasting20EpisodeEvent.KIND -> Podcasting20EpisodeEvent(id, pubKey, createdAt, tags, content, sig)
                Podcasting20TrailerEvent.KIND -> Podcasting20TrailerEvent(id, pubKey, createdAt, tags, content, sig)
                ProductEvent.KIND -> ProductEvent(id, pubKey, createdAt, tags, content, sig)
                EncryptedDmEvent.KIND -> EncryptedDmEvent(id, pubKey, createdAt, tags, content, sig)
                EncryptionKeyListEvent.KIND -> EncryptionKeyListEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateOutboxRelayListEvent.KIND -> PrivateOutboxRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ProxyRelayListEvent.KIND -> ProxyRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                PublicMessageEvent.KIND -> PublicMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ReactionEvent.KIND -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalReactionEvent.KIND -> ExternalReactionEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalCitationEvent.KIND -> ExternalCitationEvent(id, pubKey, createdAt, tags, content, sig)
                HardcopyCitationEvent.KIND -> HardcopyCitationEvent(id, pubKey, createdAt, tags, content, sig)
                PromptCitationEvent.KIND -> PromptCitationEvent(id, pubKey, createdAt, tags, content, sig)
                LearningResourceEvent.KIND -> LearningResourceEvent(id, pubKey, createdAt, tags, content, sig)
                BookshelfDirectoryEvent.KIND -> BookshelfDirectoryEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomPieceIndexEvent.KIND -> BlossomPieceIndexEvent(id, pubKey, createdAt, tags, content, sig)
                WikiMergeRequestEvent.KIND -> WikiMergeRequestEvent(id, pubKey, createdAt, tags, content, sig)
                WikiMergeAcceptanceEvent.KIND -> WikiMergeAcceptanceEvent(id, pubKey, createdAt, tags, content, sig)
                WikiRedirectEvent.KIND -> WikiRedirectEvent(id, pubKey, createdAt, tags, content, sig)
                EntityRatingEvent.KIND -> EntityRatingEvent(id, pubKey, createdAt, tags, content, sig)
                PublicationIndexEvent.KIND -> PublicationIndexEvent(id, pubKey, createdAt, tags, content, sig)
                PublicationContentEvent.KIND -> PublicationContentEvent(id, pubKey, createdAt, tags, content, sig)
                RelayReviewEvent.KIND -> RelayReviewEvent(id, pubKey, createdAt, tags, content, sig)
                UserAssertionEvent.KIND -> UserAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                EventAssertionEvent.KIND -> EventAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableAssertionEvent.KIND -> AddressableAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalIdAssertionEvent.KIND -> ExternalIdAssertionEvent(id, pubKey, createdAt, tags, content, sig)
                UserTrustedListEvent.KIND -> UserTrustedListEvent(id, pubKey, createdAt, tags, content, sig)
                EventTrustedListEvent.KIND -> EventTrustedListEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableTrustedListEvent.KIND -> AddressableTrustedListEvent(id, pubKey, createdAt, tags, content, sig)
                ExternalIdTrustedListEvent.KIND -> ExternalIdTrustedListEvent(id, pubKey, createdAt, tags, content, sig)
                ListHeaderEvent.KIND -> ListHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableListHeaderEvent.KIND -> AddressableListHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                ListItemEvent.KIND -> ListItemEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableListItemEvent.KIND -> AddressableListItemEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAddMemberEvent.KIND -> RelayAddMemberEvent(id, pubKey, createdAt, tags, content, sig)
                RelayRemoveMemberEvent.KIND -> RelayRemoveMemberEvent(id, pubKey, createdAt, tags, content, sig)
                RelayMembershipListEvent.KIND -> RelayMembershipListEvent(id, pubKey, createdAt, tags, content, sig)
                RelayRoleEvent.KIND -> RelayRoleEvent(id, pubKey, createdAt, tags, content, sig)
                RelayJoinRequestEvent.KIND -> RelayJoinRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayInviteRequestEvent.KIND -> RelayInviteRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayLeaveRequestEvent.KIND -> RelayLeaveRequestEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAuthEvent.KIND -> RelayAuthEvent(id, pubKey, createdAt, tags, content, sig)
                RelayDiscoveryEvent.KIND -> RelayDiscoveryEvent(id, pubKey, createdAt, tags, content, sig)
                RelayMonitorEvent.KIND -> RelayMonitorEvent(id, pubKey, createdAt, tags, content, sig)
                ReleaseArtifactSetEvent.KIND -> ReleaseArtifactSetEvent(id, pubKey, createdAt, tags, content, sig)
                RelaySetEvent.KIND -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
                ReportEvent.KIND -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
                RoadEventConfirmationEvent.KIND -> RoadEventConfirmationEvent(id, pubKey, createdAt, tags, content, sig)
                RoadEventReportEvent.KIND -> RoadEventReportEvent(id, pubKey, createdAt, tags, content, sig)
                RootSiteEvent.KIND -> RootSiteEvent(id, pubKey, createdAt, tags, content, sig)
                SiteSnapshotEvent.KIND -> SiteSnapshotEvent(id, pubKey, createdAt, tags, content, sig)
                RepostEvent.KIND -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
                RequestToVanishEvent.KIND -> RequestToVanishEvent(id, pubKey, createdAt, tags, content, sig)
                ConcordCommunityListEvent.KIND -> ConcordCommunityListEvent(id, pubKey, createdAt, tags, content, sig)
                ConcordCommunityListFragmentEvent.KIND -> ConcordCommunityListFragmentEvent(id, pubKey, createdAt, tags, content, sig)
                ControlEditionEvent.KIND -> ControlEditionEvent(id, pubKey, createdAt, tags, content, sig)
                ConcordInviteListEvent.KIND -> ConcordInviteListEvent(id, pubKey, createdAt, tags, content, sig)
                ConcordInviteBundleEvent.KIND -> ConcordInviteBundleEvent(id, pubKey, createdAt, tags, content, sig)
                SealEvent.KIND -> SealEvent(id, pubKey, createdAt, tags, content, sig)
                SearchRelayListEvent.KIND -> SearchRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                SimpleGroupListEvent.KIND -> SimpleGroupListEvent(id, pubKey, createdAt, tags, content, sig)
                SoftwareApplicationEvent.KIND -> SoftwareApplicationEvent(id, pubKey, createdAt, tags, content, sig)
                SoftwareAssetEvent.KIND -> SoftwareAssetEvent(id, pubKey, createdAt, tags, content, sig)
                // Zapstore's APK-signing-certificate proof and WalletScrutiny's asset bundles sit
                // beside NIP-82: both are about app releases, neither points at a NIP-82 event.
                IdentityProofEvent.KIND -> IdentityProofEvent(id, pubKey, createdAt, tags, content, sig)
                AssetBundleEvent.KIND -> AssetBundleEvent(id, pubKey, createdAt, tags, content, sig)
                StallEvent.KIND -> StallEvent(id, pubKey, createdAt, tags, content, sig)
                UserStatusEvent.KIND -> UserStatusEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteEvent.KIND -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                ThreadEvent.KIND -> ThreadEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteModificationEvent.KIND -> TextNoteModificationEvent(id, pubKey, createdAt, tags, content, sig)
                TopEightEvent.KIND -> TopEightEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentEvent.KIND -> TorrentEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentCommentEvent.KIND -> TorrentCommentEvent(id, pubKey, createdAt, tags, content, sig)
                TrustedRelayListEvent.KIND -> TrustedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                TrustProviderListEvent.KIND -> TrustProviderListEvent(id, pubKey, createdAt, tags, content, sig)
                VideoCurationSetEvent.KIND -> VideoCurationSetEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableNormalVideoEvent.KIND -> AddressableNormalVideoEvent(id, pubKey, createdAt, tags, content, sig)
                AddressableShortVideoEvent.KIND -> AddressableShortVideoEvent(id, pubKey, createdAt, tags, content, sig)
                TextTrackEvent.KIND -> TextTrackEvent(id, pubKey, createdAt, tags, content, sig)
                VideoViewEvent.KIND -> VideoViewEvent(id, pubKey, createdAt, tags, content, sig)
                PushRegistrationEvent.KIND -> PushRegistrationEvent(id, pubKey, createdAt, tags, content, sig)
                PushDeregistrationEvent.KIND -> PushDeregistrationEvent(id, pubKey, createdAt, tags, content, sig)
                PushPreferencesEvent.KIND -> PushPreferencesEvent(id, pubKey, createdAt, tags, content, sig)
                VideoCollaborationEvent.KIND -> VideoCollaborationEvent(id, pubKey, createdAt, tags, content, sig)
                VideoNormalEvent.KIND -> VideoNormalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoShortEvent.KIND -> VideoShortEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceEvent.KIND -> VoiceEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceReplyEvent.KIND -> VoiceReplyEvent(id, pubKey, createdAt, tags, content, sig)
                WakeUpEvent.KIND -> WakeUpEvent(id, pubKey, createdAt, tags, content, sig)
                WebBookmarkEvent.KIND -> WebBookmarkEvent(id, pubKey, createdAt, tags, content, sig)
                WikiArticleEvent.KIND -> WikiArticleEvent(id, pubKey, createdAt, tags, content, sig)
                WorkoutRecordEvent.KIND -> WorkoutRecordEvent(id, pubKey, createdAt, tags, content, sig)
                ExerciseTemplateEvent.KIND -> ExerciseTemplateEvent(id, pubKey, createdAt, tags, content, sig)
                WorkoutTemplateEvent.KIND -> WorkoutTemplateEvent(id, pubKey, createdAt, tags, content, sig)
                else -> factories[kind]?.build(id, pubKey, createdAt, tags, content, sig) ?: Event(id, pubKey, createdAt, kind, tags, content, sig)
            } as T

        /**
         * True when [kind] maps to a typed Quartz event class — either a
         * compiled-in branch above or a registered [factories] builder. Unknown
         * kinds are parsed as a bare [Event], so a probe instance's runtime type
         * equals [Event] exactly when the kind has no dedicated class.
         *
         * Used to decide whether a repost's inner (boosted) kind is something
         * Amethyst can parse and render at all.
         */
        fun isKnownKind(kind: Int): Boolean = probe(kind)::class != Event::class

        /**
         * A tagless, contentless instance of [kind], for questions asked of a kind rather than of
         * an event: does it have a typed class ([isKnownKind]), is it searchable (the stores'
         * reindex pre-filters, `SearchableKinds`)?
         *
         * For most kinds that is just [create] with no tags. It is not for kinds whose class is
         * chosen by tags, where a tagless event can land on a different class than the kind's
         * real events. Kind 38000 is the case that matters: with no tags it is an
         * [UnrecognizedKind38000Event] (junk, unsearchable), yet every typed shape of it — mint recommendation, ballot, prediction market —
         * is a searchable, renderable class, so it answers as its primary class. Mostro's 38384,
         * 38385 and 38386 and RoboSats' 31986 do the same: with no tags they fall to their
         * `UnrecognizedKind…Event`, and 38385's primary class is searchable. Kind 30301 answers as
         * its Kanban board (WalletScrutiny's verifications on it are searchable too) and 30302 as
         * the Kanban card. (The other tag-split kinds, 39005 and 20001, already land on a typed
         * class with no tags.)
         *
         * The id is non-blank so kinds that lazily hash a missing id (NIP-17 chat) skip that work
         * — only the runtime type matters here.
         */
        fun probe(kind: Int): Event =
            when (kind) {
                MintRecommendationEvent.KIND -> MintRecommendationEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                MostroUserRatingEvent.KIND -> MostroUserRatingEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                MostroInfoEvent.KIND -> MostroInfoEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                MostroDisputeEvent.KIND -> MostroDisputeEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                RoboSatsCoordinatorRatingEvent.KIND -> RoboSatsCoordinatorRatingEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                KanbanBoardEvent.KIND -> KanbanBoardEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                KanbanCardEvent.KIND -> KanbanCardEvent(PROBE_ID, PROBE_ID, 0L, emptyArray(), "", "")
                else -> create(PROBE_ID, PROBE_ID, 0L, kind, emptyArray(), "", "")
            }

        private const val PROBE_ID = "0"
    }
}
