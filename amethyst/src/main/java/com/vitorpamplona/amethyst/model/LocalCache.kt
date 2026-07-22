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

package com.vitorpamplona.amethyst.model

import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.cashu.MintDirectoryIndex
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.OnchainZapStatus
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmConversation
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzTypingState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.LargeSoftCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.observables.CreatedAtIdHexComparator
import com.vitorpamplona.amethyst.commons.model.observables.EventListMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.NewEventMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.NoteListMatchingFilter
import com.vitorpamplona.amethyst.commons.model.observables.Observable
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.commons.service.nwc.NwcPaymentTracker
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.LocalCache.observeEvents
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nipBCOnchainZaps.OnchainZapResolver
import com.vitorpamplona.amethyst.service.BundledInsert
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.dateFormatter
import com.vitorpamplona.quartz.buzz.aeEngrams.EngramEvent
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
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
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.buzz.pairing.PairingEvent
import com.vitorpamplona.quartz.buzz.plPushLease.PushLeaseEvent
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
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRoot
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
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isAddressable
import com.vitorpamplona.quartz.nip01Core.core.isRegular
import com.vitorpamplona.quartz.nip01Core.core.isReplaceable
import com.vitorpamplona.quartz.nip01Core.core.tagValueContains
import com.vitorpamplona.quartz.nip01Core.crypto.checkSignature
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterIndex
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUsers
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.BaseRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuoteIds
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.isATag
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.PutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.RemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.UpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.JoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.LeaveRequestEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
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
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
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
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnZapReceiptValidator
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointResolver
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.RootNappletEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
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
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.ClientTag
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
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
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.SortedSet

interface ILocalCache {
    fun markAsSeen(
        eventId: String,
        relay: NormalizedRelayUrl,
    ) {
        // Default no-op; implementations may override to track seen events per relay
    }
}

object LocalCache : ILocalCache, ICacheProvider {
    val antiSpam = AntiSpamFilter()

    val users = LargeSoftCache<HexKey, User>()
    val notes = LargeSoftCache<HexKey, Note>()
    val addressables = LargeSoftCache<Address, AddressableNote>()

    val chatroomList = LargeCache<HexKey, ChatroomList>()
    val publicChatChannels = LargeCache<HexKey, PublicChatChannel>()
    val liveChatChannels = LargeCache<Address, LiveActivitiesChannel>()
    val ephemeralChannels = LargeCache<RoomId, EphemeralChatChannel>()
    val geohashChannels = LargeCache<String, GeohashChatChannel>()
    val relayGroupChannels = LargeCache<GroupId, RelayGroupChannel>()
    val concordChannels = LargeCache<ConcordChannelId, ConcordChannel>()

    val paymentTracker = NwcPaymentTracker()

    /**
     * Bitcoin chain backend used by [consume]`(OnchainZapEvent)` to verify NIP-BC zaps
     * against the actual on-chain transaction. `null` disables verification (incoming
     * onchain zap events still get cached, but no `Note.zapsAmount` contribution).
     * Set during account init.
     */
    @Volatile
    var onchainBackend: OnchainBackend? = null

    /**
     * NIP-BC on-chain zap verification coordinator. Owns the chain-tip poller flow,
     * the in-flight de-duplication of verifier calls across consume/reverify paths,
     * and the parallelism cap. `consume(OnchainZapEvent)` delegates the async
     * verification side here; the gallery's reverification driver also calls in here
     * directly.
     */
    val onchainZapResolver = OnchainZapResolver(this)

    /**
     * Resolver for LNURL provider metadata used by [consume]`(LnZapEvent)` to
     * validate NIP-57 Appendix F. `null` skips the receipt-signer check (the
     * receipt is still accepted on signature verification alone — matches legacy
     * behavior); set this in app init so receipts can be verified against the
     * recipient's provider's advertised `nostrPubkey`.
     */
    @Volatile
    var lnurlEndpointResolver: LnurlEndpointResolver? = null

    val relayHints = HintIndexer()

    /**
     * Cashu mint URL directory, populated passively as
     * `NutzapInfoEvent` / `MintRecommendationEvent` / `CashuMintEvent`
     * flow through `updateMintIndex`. Backs the autocomplete in any text
     * field where the user types a mint URL.
     *
     * Use [mintDirectoryBackfilled] semantics via [ensureMintDirectoryBackfilled]
     * when reading from the UI — the first call sweeps already-cached
     * events so suggestions are useful before the next relay round-trip.
     */
    val mintDirectory = MintDirectoryIndex()

    @Volatile private var mintDirectoryBackfilled = false
    private val mintDirectoryBackfillLock = Any()

    /**
     * Sweeps `notes` + `addressables` for any NIP-87 / NIP-61 event the
     * cache already holds and feeds them into [mintDirectory]. The wallet
     * directory + every other user's kind:10019 we've ever loaded may
     * have arrived BEFORE [updateMintIndex] existed (or before any
     * caller cared), so without this backfill the autocomplete is empty
     * until new events arrive.
     *
     * Idempotent — subsequent calls return immediately. Best-effort: a
     * scan failure is swallowed so the index stays usable even if the
     * cache is in an unexpected state.
     */
    fun ensureMintDirectoryBackfilled() {
        if (mintDirectoryBackfilled) return
        synchronized(mintDirectoryBackfillLock) {
            if (mintDirectoryBackfilled) return
            runCatching {
                notes.forEach { _, note -> note.event?.let(::updateMintIndex) }
                addressables.forEach { _, note -> note.event?.let(::updateMintIndex) }
            }
            mintDirectoryBackfilled = true
        }
    }

    val deletionIndex = DeletionIndex()

    /**
     * Inverted index over the active [Observable]s. New events fan
     * out only to observers whose filter actually narrows on a field
     * the event carries (author, kind, single-letter tag), instead
     * of waking every observer per event. Predicate-only observers
     * (no underlying [Filter]) live in the unindexed pool and still
     * see every event.
     */
    val observables = FilterIndex<Observable>()

    fun Filter.match(note: Note): Boolean {
        val event = note.event
        return if (event != null) {
            match(event)
        } else {
            false
        }
    }

    fun filter(filter: Filter): SortedSet<Note> {
        val byKinds = filter.kinds?.filter { it.isAddressable() || it.isReplaceable() }

        val addressableMatches =
            if (!byKinds.isNullOrEmpty()) {
                val byAuthors = filter.authors
                if (!byAuthors.isNullOrEmpty()) {
                    // optimized
                    byKinds.flatMap { kind ->
                        byAuthors.flatMap { pubkey ->
                            addressables.filter(kind, pubkey) { _, note ->
                                filter.match(note)
                            }
                        }
                    }
                } else {
                    // optimized
                    byKinds.flatMap { kind ->
                        addressables.filter(kind) { _, note ->
                            filter.match(note)
                        }
                    }
                }
            } else {
                addressables.filter { _, note ->
                    filter.match(note)
                }
            }

        val noteMatches =
            notes.filter { _, note ->
                val event = note.event
                if (event != null && event.kind.isRegular()) {
                    filter.match(event)
                } else {
                    false
                }
            }

        val limit = filter.limit

        val limitedSet =
            if (limit != null) {
                (addressableMatches + noteMatches).take(limit)
            } else {
                (addressableMatches + noteMatches)
            }

        return limitedSet.toSortedSet(CreatedAtIdHexComparator)
    }

    fun observeNotes(filter: Filter): Flow<List<Note>> =
        callbackFlow {
            val newFilter =
                NoteListMatchingFilter(filter, this@LocalCache::filter) {
                    trySend(it)
                }

            newFilter.init()

            observables.register(filter, newFilter)

            awaitClose {
                observables.unregister(newFilter)
            }
        }.buffer(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun <T : Event> observeEvents(filter: Filter): Flow<List<T>> =
        callbackFlow {
            val cachedFilter =
                EventListMatchingFilter<T>(filter, this@LocalCache::filter) {
                    trySend(it)
                }

            cachedFilter.init()

            observables.register(filter, cachedFilter)

            awaitClose {
                observables.unregister(cachedFilter)
            }
        }.buffer(kotlinx.coroutines.channels.Channel.CONFLATED)

    /**
     * Emits each new event for which [predicate] returns true, one at a time,
     * as it is inserted into the cache. Unlike [observeEvents], this does not
     * accumulate a list — useful for per-event reactive pipelines like
     * notifications.
     *
     * The predicate runs on every insertion, so keep it cheap. Callers with a
     * Nostr [Filter] can pass `filter::match`; compose additional local checks
     * (rolling windows, derived fields the Filter grammar can't express) with
     * `&&`.
     */
    fun <T : Event> observeNewEvents(predicate: (Event) -> Boolean): Flow<T> =
        callbackFlow {
            val newFilter =
                NewEventMatchingFilter<T>(predicate) {
                    trySend(it)
                }

            // Unindexed: predicate is opaque, the index can't narrow.
            // Caller is delivered every event and runs the predicate.
            observables.registerUnindexed(newFilter)

            awaitClose {
                observables.unregister(newFilter)
            }
        }

    fun <T : Event> observeNewEvents(filter: Filter): Flow<T> =
        callbackFlow {
            val newFilter =
                NewEventMatchingFilter<T>(filter::match) {
                    trySend(it)
                }

            observables.register(filter, newFilter)

            awaitClose {
                observables.unregister(newFilter)
            }
        }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> observeLatestEvent(filter: Filter) = observeEvents<T>(filter).map { it.firstOrNull() }

    fun observeLatestNote(filter: Filter) = observeNotes(filter).map { it.firstOrNull() }

    fun checkGetOrCreateUser(key: String): User? = runCatching { getOrCreateUser(key) }.getOrNull()

    fun load(keys: List<String>): List<User> = keys.mapNotNull(::checkGetOrCreateUser)

    fun load(keys: Set<String>): Set<User> = keys.mapNotNullTo(mutableSetOf(), ::checkGetOrCreateUser)

    override fun getOrCreateUser(pubkey: HexKey): User {
        require(isValidHex(key = pubkey)) { "$pubkey is not a valid hex" }
        // Pass `this` as the UserContext — User now resolves each pinned
        // addressable note (kind:10002 / 10050 / 10019) lazily on first
        // read, instead of all-or-nothing at construction time.
        return users.getOrCreate(pubkey) { User(it, userContext) }
    }

    /** [UserContext] bridge to this cache's addressable lookup. */
    private val userContext = UserContext(::getOrCreateAddressableNoteInternal)

    override fun getUserIfExists(pubkey: String): User? {
        if (pubkey.isEmpty()) return null
        return users.get(pubkey)
    }

    override fun countUsers(predicate: (String, User) -> Boolean): Int {
        var count = 0
        users.forEach { key, user ->
            if (predicate(key, user)) count++
        }
        return count
    }

    fun countContactLists(predicate: (ContactListEvent) -> Boolean): Int {
        var count = 0
        addressables.filter(ContactListEvent.KIND).forEach { note ->
            val event = note.event as? ContactListEvent
            if (event != null && predicate(event)) count++
        }
        return count
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? = Address.parse(key)?.let { addressables.get(it) }

    fun getAddressableNoteIfExists(address: Address): AddressableNote? = addressables.get(address)

    override fun getNoteIfExists(hexKey: String): Note? = if (hexKey.length == 64) notes.get(hexKey) else Address.parse(hexKey)?.let { addressables.get(it) }

    fun getNoteIfExists(key: ETag): Note? = notes.get(key.eventId)

    fun getPublicChatChannelIfExists(key: String): PublicChatChannel? = publicChatChannels.get(key)

    fun getEphemeralChatChannelIfExists(key: RoomId): EphemeralChatChannel? = ephemeralChannels.get(key)

    fun getGeohashChannelIfExists(geohash: String): GeohashChatChannel? = geohashChannels.get(geohash)

    fun getRelayGroupChannelIfExists(key: GroupId): RelayGroupChannel? = relayGroupChannels.get(key)

    /** Every relay group we know of that is hosted on [relay] (its channel directory). */
    fun getRelayGroupChannelsOnRelay(relay: NormalizedRelayUrl): List<RelayGroupChannel> = relayGroupChannels.filter { key, _ -> key.relayUrl == relay }

    fun getLiveActivityChannelIfExists(key: Address): LiveActivitiesChannel? = liveChatChannels.get(key)

    fun getNoteIfExists(event: Event): Note? =
        if (event is AddressableEvent) {
            getAddressableNoteIfExists(event.addressTag())
        } else {
            getNoteIfExists(event.id)
        }

    fun getOrCreateNote(event: Event): Note =
        if (event is AddressableEvent) {
            getOrCreateAddressableNote(event.address())
        } else {
            getOrCreateNote(event.id)
        }

    fun checkGetOrCreateNote(etag: ETag): Note? {
        if (isValidHex(etag.eventId)) {
            return getOrCreateNote(etag)
        }
        return null
    }

    override fun checkGetOrCreateNote(hexKey: String): Note? {
        if (ATag.isATag(hexKey)) {
            return checkGetOrCreateAddressableNote(hexKey)
        }
        if (isValidHex(hexKey)) {
            val note = getOrCreateNote(hexKey)
            val noteEvent = note.event
            return if (noteEvent is AddressableEvent) {
                // upgrade to the latest
                val newNote = getOrCreateAddressableNote(noteEvent.address())

                if (newNote.event == null) {
                    val author = note.author ?: getOrCreateUser(noteEvent.pubKey)
                    newNote.loadEvent(noteEvent as Event, author, emptyList())
                    note.moveAllReferencesTo(newNote)
                }

                newNote
            } else {
                note
            }
        }
        return null
    }

    override fun getEventStream(): com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream =
        object : com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream {
            override val newEventBundles = live.newEventBundles
            override val deletedEventBundles = live.deletedEventBundles
        }

    override fun hasBeenDeleted(event: Any): Boolean =
        if (event is Event) {
            deletionIndex.hasBeenDeleted(event)
        } else {
            false
        }

    fun getOrAddAliasNote(
        idHex: String,
        note: Note,
    ): Note {
        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            note
        }
    }

    fun getOrCreateNote(idHex: String): Note {
        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            Note(idHex)
        }
    }

    fun getOrCreateChatroomList(key: HexKey): ChatroomList = chatroomList.getOrCreate(key) { ChatroomList(key) }

    fun getOrCreatePublicChatChannel(key: HexKey): PublicChatChannel = publicChatChannels.getOrCreate(key) { PublicChatChannel(key) }

    fun getOrCreateLiveChannel(key: Address): LiveActivitiesChannel = liveChatChannels.getOrCreate(key) { LiveActivitiesChannel(key) }

    fun getOrCreateEphemeralChannel(key: RoomId): EphemeralChatChannel = ephemeralChannels.getOrCreate(key) { EphemeralChatChannel(key) }

    fun getOrCreateGeohashChannel(geohash: String): GeohashChatChannel = geohashChannels.getOrCreate(geohash) { GeohashChatChannel(geohash) }

    fun getOrCreateRelayGroupChannel(key: GroupId): RelayGroupChannel = relayGroupChannels.getOrCreate(key) { RelayGroupChannel(key) }

    fun getConcordChannelIfExists(key: ConcordChannelId): ConcordChannel? = concordChannels.get(key)

    fun getOrCreateConcordChannel(key: ConcordChannelId): ConcordChannel = concordChannels.getOrCreate(key) { ConcordChannel(key) }

    /**
     * Lands a decrypted Concord chat rumor in the cache as a real Note and, for
     * message-like kinds, attaches it to its channel so the shared chat feed and
     * the Messages inbox render it (with previews, threading, OTS, reactions/zaps
     * reusing the same id-keyed machinery as every other chat). Reactions (kind 7),
     * deletes (kind 5), etc. are consumed too — they wire to their target Note by
     * `e`-tag through [justConsume] — but are not themselves added as channel rows.
     *
     * Fed by [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager]
     * once a wrap decrypts + validates against the folded Control Plane.
     */
    fun consumeConcordRumor(
        communityId: String,
        channelIdHex: String,
        rumor: Event,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        // Attach to the channel BEFORE justConsume sets the event and notifies feeds,
        // so the note already carries its ConcordChannel gatherer when it flows through
        // the Messages-list incremental filter (which routes rows by that gatherer).
        val messageRow =
            if (rumor is ChatEvent || rumor is CommentEvent) {
                val ch = getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                val note = getOrCreateNote(rumor.id)
                // Skip attaching a row for a message we already know is deleted (its kind-5 delete
                // was processed first). Otherwise every reproject — which re-emits the whole wrap
                // buffer — would re-add then re-remove it, churning the feed. justConsume still
                // records the (already-known) deletion below; a delete arriving LATER is handled by
                // the normal deletion cascade unlinking the note from its gatherers.
                if (!deletionIndex.hasBeenDeleted(rumor)) ch.addNote(note)
                ch to note
            } else {
                null
            }
        // wasVerified = true: a Concord rumor is unsigned (its `sig` is empty), so a signature
        // check would fail and the event would never load onto its Note — leaving the chat row
        // stuck on the "loading / not found" placeholder. Its authenticity is already established
        // by the envelope open path (ConcordStreamEnvelope.open verifies the seal signature,
        // binds rumor.pubKey == seal.pubKey, and checks rumor.verifyId()), exactly like a NIP-59
        // gift-wrapped DM rumor, so we consume it as pre-verified.
        justConsume(rumor, null, true)

        // A Concord rumor is decrypted locally with the plane key, so it arrives with no
        // per-relay attribution (relay = null above). Stamp the relays its carrying wrap was
        // seen on — mirroring NIP-17's addRelayToNoteAndInners — so the chat UI can show where
        // the message actually came from, not just the channel's configured relays.
        if (seenOnRelays.isNotEmpty()) {
            getNoteIfExists(rumor.id)?.takeIf { it.event != null }?.let { note ->
                seenOnRelays.forEach { note.addRelay(it) }
            }
        }

        // justConsume bails without loading the event when the rumor has already been deleted
        // (a kind-5 delete referencing it was processed first — easy to hit in Concord because a
        // reproject re-emits the whole wrap buffer and ordering isn't guaranteed) or fails to
        // verify. We attached the row up front, so an unpopulated note would otherwise linger as a
        // permanent "Event is loading…" ghost. Drop it; the reverse order (delete after the message)
        // is already handled by the normal deletion cascade unlinking the note from its gatherers.
        messageRow?.let { (ch, note) ->
            if (note.event == null) {
                ch.removeNote(note)
            } else {
                // The row was attached (addNote) BEFORE justConsume set the event, so addNote saw a
                // null createdAt and could not pick lastNote or order the feed. The event is loaded
                // now — refresh so the channel's last-message preview, unread count, and ordering are
                // correct (otherwise lastNote stays null forever and every row reads "No messages yet").
                ch.refreshAfterEventLoad(note)
            }
        }
    }

    fun checkGetOrCreatePublicChatChannel(key: String): PublicChatChannel? {
        if (isValidHex(key)) {
            return getOrCreatePublicChatChannel(key)
        }
        return null
    }

    private fun isValidHex(key: String): Boolean {
        if (key.length != 64) return false
        return Hex.isHex64(key)
    }

    fun checkGetOrCreateAddressableNote(key: String): AddressableNote? =
        try {
            val addr = Address.parse(key)
            if (addr != null) {
                getOrCreateAddressableNote(addr)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            Log.e("LocalCache", "Invalid Key to create channel: $key", e)
            null
        }

    fun getOrCreateAddressableNoteInternal(key: Address): AddressableNote = addressables.getOrCreate(key) { AddressableNote(key) }

    override fun getOrCreateAddressableNote(key: Address): AddressableNote {
        val note = getOrCreateAddressableNoteInternal(key)
        // Loads the user outside a Syncronized block to avoid blocking
        if (note.author == null) {
            note.author = checkGetOrCreateUser(key.pubKeyHex)
        }
        return note
    }

    fun getOrCreateNote(key: GenericETag): Note {
        val note = getOrCreateNote(key.eventId)
        // Loads the user outside a Syncronized block to avoid blocking
        val possibleAuthor = key.author
        if (note.author == null && possibleAuthor != null) {
            note.author = checkGetOrCreateUser(possibleAuthor)
        }
        val relayHint = key.relay
        if (relayHint != null) {
            note.addRelay(relayHint)
        }
        return note
    }

    fun consume(
        event: MetadataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // new event
        consumeBaseReplaceable(event, relay, wasVerified)

        val user = getOrCreateUser(event.pubKey)

        if (user.metadata().shouldUpdateWith(event)) {
            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null && (wasVerified || justVerify(event))) {
                user.updateUserInfo(newUserMetadata, event)
                if (relay != null) {
                    user.addRelayBeingUsed(relay, event.createdAt)
                }

                return true
            }
        }

        return false
    }

    fun consumeRegularEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            // A gift wrap re-delivered by another relay is a duplicate (returns
            // false below and is never re-processed), so drill into the already
            // unwrapped chain here — otherwise the relay never reaches the
            // rumor note that the chat UI actually renders.
            addRelayToNoteAndInners(note, relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (event is BaseNoteEvent && antiSpam.isSpam(event, relay)) {
            return false
        }

        return if (wasVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            // Counts the replies
            replyTo.forEach { it.addReply(note) }

            // NIP-18 quote reposts: a note carrying a `q` tag is a quote-repost of the
            // quoted note. Count it as a boost so it shows in the quoted note's repost
            // counter alongside kind:6/kind:16 reposts. The quoted note is deliberately
            // kept out of `replyTo` so the quote still renders as a root post in the home
            // feed (see Note.isNewThread); deletion cleanup lives in unlinkAndRemove.
            addQuoteBoosts(event, note, replyTo)

            refreshNewNoteObservers(note)

            true
        } else {
            false
        }
    }

    /**
     * Adds [note] as a boost of every event/address referenced by a NIP-18 `q` tag
     * (a quote-repost). Targets already in [replyTo] are skipped so a note that both
     * replies to and quotes the same note isn't counted twice, and self-quotes are
     * ignored.
     */
    private fun addQuoteBoosts(
        event: Event,
        note: Note,
        replyTo: List<Note>,
    ) {
        event.taggedQuoteIds().forEach { quotedId ->
            val quoted = checkGetOrCreateNote(quotedId)
            if (quoted != null && quoted != note && quoted !in replyTo) {
                quoted.addBoost(note)
            }
        }
    }

    fun consume(
        event: NipTextEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            if (event.createdAt > (note.createdAt() ?: 0L)) {
                note.loadEvent(event, author, replyTo)

                refreshNewNoteObservers(note)

                return true
            }
        }

        return false
    }

    fun consume(
        event: LongTextNoteEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            if (event.createdAt > (note.createdAt() ?: 0L)) {
                note.loadEvent(event, author, replyTo)

                refreshNewNoteObservers(note)

                return true
            }
        }

        return false
    }

    fun consume(
        event: PaymentTargetsEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if ((isVerified || justVerify(event)) && event.createdAt > (note.createdAt() ?: 0L)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: WikiNoteEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if ((isVerified || justVerify(event)) && event.createdAt > (note.createdAt() ?: 0L)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    @Suppress("DEPRECATION")
    fun computeReplyTo(event: Event): List<Note> =
        when (event) {
            is ZapPollEvent -> {
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            }

            is LongTextNoteEvent -> {
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            }

            is GitReplyEvent -> {
                event.tagsWithoutCitations().filter { it != event.repository()?.toTag() }.mapNotNull { checkGetOrCreateNote(it) }
            }

            is TextNoteEvent -> {
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            }

            is CommentEvent -> {
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            }

            is VoiceReplyEvent -> {
                event.markedReplyTos().mapNotNull { checkGetOrCreateNote(it) }
            }

            is ChatMessageEvent -> {
                event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            }

            is ChatMessageEncryptedFileHeaderEvent -> {
                event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            }

            is LnZapEvent -> {
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) } +
                    (event.zapRequest?.taggedAddresses()?.map { getOrCreateAddressableNote(it) } ?: emptyList())
            }

            is OnchainZapEvent -> {
                // NIP-BC zaps can target an event id (e), an addressable event (a),
                // or just the recipient profile (p). Profile-only zaps have no
                // Note target and are surfaced through profile zap queries.
                buildList {
                    event.zappedEvent()?.let { checkGetOrCreateNote(it)?.let { add(it) } }
                    event.zappedAddress()?.let { coord ->
                        Address.parse(coord)?.let { add(getOrCreateAddressableNote(it)) }
                    }
                }
            }

            is NutzapEvent -> {
                // The zapped event is carried in the kind:9321's `e` tags
                // (and optionally an `a` tag for addressables). Whichever
                // notes those resolve to receive the nutzap entry —
                // analogous to how LnZapEvent flows into `addZap`.
                event.linkedEventIds().mapNotNull { checkGetOrCreateNote(it) }
            }

            is LnZapRequestEvent -> {
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            }

            is AcceptedBadgeSetEvent, is ProfileBadgesEvent -> {
                event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            }

            is BadgeAwardEvent -> {
                event.awardDefinition().map { getOrCreateAddressableNote(it) }
            }

            is PrivateDmEvent -> {
                event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            }

            is RepostEvent, is GenericRepostEvent -> {
                val repost = event as BaseRepostEvent
                listOfNotNull(
                    repost.boostedEventId()?.let { checkGetOrCreateNote(it) },
                    repost.boostedAddress()?.let { getOrCreateAddressableNote(it) },
                )
            }

            is CommunityPostApprovalEvent -> {
                event.approvedEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.approvedAddresses().map { getOrCreateAddressableNote(it) }
            }

            is ReactionEvent -> {
                event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            }

            is WakeUpEvent -> {
                // Link the referenced events so filterMissingEvents will query
                // for them when the WakeUp note is in an EventFinder subscription.
                event.eventIds().mapNotNull { checkGetOrCreateNote(it) }
            }

            is ChannelMessageEvent -> {
                event.tagsWithoutCitations().filter { it != event.channelId() }.mapNotNull { checkGetOrCreateNote(it) }
            }

            is LiveActivitiesChatMessageEvent -> {
                event.tagsWithoutCitations().filter { it != event.activity()?.toTag() }.mapNotNull { checkGetOrCreateNote(it) }
            }

            is TorrentCommentEvent -> {
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            }

            is ChatEvent -> {
                // Amethyst's own kind:9 replies carry the parent as a NIP-18
                // `q` tag (see ChatEvent.replyingTo), but the broader Marmot
                // ecosystem — WhiteNoise in particular — threads kind:9 chats
                // with a plain NIP-10 `e` tag. Accept both so inbound replies
                // from either client show their quote bubble in the feed.
                val eTagTargets =
                    event.tags
                        .filter { it.size > 1 && it[0] == "e" }
                        .map { it[1] }
                val qTagTargets = event.quotedEvents().map { it.eventId }
                (eTagTargets + qTagTargets).mapNotNull { checkGetOrCreateNote(it) }
            }

            is StreamMessageV2Event -> {
                // Buzz threads 40002s with marked root/reply e-tags (thread_tags in
                // buzz-sdk). Link both so inbound replies render their quote bubble —
                // we emit these markers on send, so we must also read them.
                listOfNotNull(
                    event.tags.buzzThreadRoot(),
                    event.tags.buzzThreadReply(),
                ).distinct().mapNotNull { checkGetOrCreateNote(it) }
            }

            else -> {
                emptyList()
            }
        }

    private fun consume(
        event: LiveActivitiesEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (note.event?.id == event.id) return false

        if (event.createdAt > (note.createdAt() ?: 0L) && (isVerified || justVerify(event))) {
            note.loadEvent(event, author, emptyList())

            val channel = getOrCreateLiveChannel(note.address)

            if (relay != null) {
                channel.addRelay(relay)
            }

            val creator = event.host()?.let { checkGetOrCreateUser(it.pubKey) } ?: author

            channel.updateChannelInfo(creator, event, note)

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: StatusEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val author = getOrCreateUser(event.pubKey)
        val note = event.toAddressableNote()
        val new = consumeBaseReplaceable(event, relay, wasVerified)

        if (new) {
            author.statusState().addStatus(note)
        }

        return new
    }

    fun Event.toNote() = getOrCreateNote(id)

    fun AddressableEvent.toAddressableNote() = getOrCreateAddressableNote(address())

    fun consume(
        event: ContactCardEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = event.toAddressableNote()
        val new = consumeBaseReplaceable(event, relay, wasVerified)

        if (new) {
            val about = checkGetOrCreateUser(event.aboutUser()) ?: return new
            about.cards().addCard(note)
        }

        return new
    }

    fun consume(
        event: OtsEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (version.event?.id == event.id) return false

        if (wasVerified || justVerify(event)) {
            if (version.event == null) {
                version.loadEvent(event, author, emptyList())
                version.flowSet?.ots?.invalidateData()
            }

            refreshNewNoteObservers(version)
            return true
        }

        return false
    }

    private fun consumeBaseReplaceable(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // TODO: Redo the Event sctructure in Quartz to avoid this check
        check(event is AddressableEvent) { "Event must be addressable: ${event.kind}" }

        val version = getOrCreateNote(event.id)
        val replaceableNote = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(replaceableNote)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            replaceableNote.addRelay(relay)
        }

        // Already processed this event.
        if (replaceableNote.event?.id == event.id) return isVerified

        return if (event.createdAt > (replaceableNote.createdAt() ?: 0L) && (isVerified || justVerify(event))) {
            // clear index from previous tags
            replaceableNote.replyTo?.forEach {
                it.removeNote(replaceableNote)
            }

            replaceableNote.loadEvent(event, author, computeReplyTo(event))

            refreshNewNoteObservers(replaceableNote)

            true
        } else {
            false
        }
    }

    fun consume(
        event: DeletionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        return if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            if (deletionIndex.add(event, wasVerified)) {
                event
                    .deleteEvents()
                    .mapNotNull { getNoteIfExists(it) }
                    .forEach { deleteNote ->
                        val deleteNoteEvent = deleteNote.event
                        if (deleteNoteEvent is AddressableEvent) {
                            val addressableNote = getAddressableNoteIfExists(deleteNoteEvent.addressTag())
                            if (addressableNote?.author?.pubkeyHex == event.pubKey && (addressableNote.createdAt() ?: 0L) <= event.createdAt) {
                                // Counts the replies
                                deleteNote(addressableNote)

                                addressables.remove(addressableNote.address)
                            }
                        }

                        // must be the same author
                        if (deleteNote.author?.pubkeyHex == event.pubKey) {
                            // reverts the add
                            deleteNote(deleteNote)
                        }
                    }

                val addressList = event.deleteAddressIds()
                val addressSet = addressList.toSet()

                addressList
                    .mapNotNull { getAddressableNoteIfExists(it) }
                    .forEach { deleteNote ->
                        // must be the same author
                        if (deleteNote.author?.pubkeyHex == event.pubKey && (deleteNote.createdAt() ?: 0L) <= event.createdAt) {
                            // Counts the replies
                            deleteNote(deleteNote)

                            addressables.remove(deleteNote.address)
                        }
                    }

                notes.forEach { _, note ->
                    val noteEvent = note.event
                    if (noteEvent is AddressableEvent &&
                        noteEvent.addressTag() in addressSet &&
                        noteEvent.pubKey == event.pubKey &&
                        noteEvent.createdAt <= event.createdAt
                    ) {
                        deleteNote(note)
                    }
                }
            }

            refreshNewNoteObservers(note)

            true
        } else {
            false
        }
    }

    override fun getAnyChannel(note: Note): Channel? = note.event?.let { getAnyChannel(it) }

    fun getAnyChannel(noteEvent: Event): Channel? =
        when (noteEvent) {
            is ChannelCreateEvent -> getPublicChatChannelIfExists(noteEvent.id)
            is ChannelMetadataEvent -> noteEvent.channelId()?.let { getPublicChatChannelIfExists(it) }
            is ChannelMessageEvent -> noteEvent.channelId()?.let { getPublicChatChannelIfExists(it) }
            is LiveActivitiesChatMessageEvent -> noteEvent.activityAddress()?.let { getLiveActivityChannelIfExists(it) }
            is LiveActivitiesEvent -> getLiveActivityChannelIfExists(noteEvent.address())
            is EphemeralChatEvent -> noteEvent.roomId()?.let { getEphemeralChatChannelIfExists(it) }
            is GeohashChatEvent -> noteEvent.geohash()?.let { getGeohashChannelIfExists(it) }
            else -> null
        }

    /**
     * NIP-09 delete of a single targeted event.
     *
     * Removal has two halves: unlinking the note from everything that points AT it
     * (its parents, channels, and the per-user report/card/status/poll indexes —
     * all handled by [unlinkAndRemove]); and dealing with the note's OWN children
     * (the notes that point at IT). The delete path and the prune path share the
     * first half and differ only on the second:
     *  - delete (here): the children are independent events and stay in the cache;
     *    [Note.detachFromChildren] only severs their back-reference so the removed
     *    shell can neither leak (held alive by a child's `replyTo`) nor be later
     *    resurrected by `computeReplyTo` as a second Note for the same id.
     *  - prune (see [unlinkAndRemove] callers): the whole child subtree is removed.
     *
     * Rumors additionally drop the envelope notes that delivered them.
     */
    private fun deleteNote(deleteNote: Note) {
        deleteEnvelopes(deleteNote)

        deleteNote.detachFromChildren()

        unlinkAndRemove(deleteNote)
    }

    /**
     * Removes the envelope notes that delivered [rumorNote]'s rumor: its
     * host (normally the kind-1059 wrap; a bare kind-13 seal otherwise)
     * and, when the host is a wrap, the seal layer it carried. Public
     * events have no envelopes and are ignored.
     */
    fun deleteEnvelopes(rumorNote: Note) {
        val host = rumorNote.rumorHost ?: return

        getNoteIfExists(host.id)?.let { hostNote ->
            (hostNote.event as? GiftWrapEvent)?.innerEventId?.let { sealId ->
                getNoteIfExists(sealId)?.let { sealNote ->
                    sealNote.clearFlow()
                    refreshDeletedNoteObservers(sealNote)
                }
                notes.remove(sealId)
            }
            hostNote.clearFlow()
            refreshDeletedNoteObservers(hostNote)
        }

        notes.remove(host.id)
        rumorNote.rumorHost = null
    }

    fun consume(
        event: RepostEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        if (relay != null) {
            getOrCreateUser(event.pubKey).addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }
        return false
    }

    fun consume(
        event: GenericRepostEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        if (relay != null) {
            getOrCreateUser(event.pubKey).addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: CommunityPostApprovalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Approval notes render their own relay list directly in community feeds
        // (there is no repost-style indirection to a replyTo for them), so without
        // this attribution the "accepted by relays" gallery line stays empty forever.
        if (relay != null) {
            getOrCreateUser(event.pubKey).addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)

            val communities = event.communityAddresses()
            val eventsApproved = computeReplyTo(event)

            val repliesTo = communities.map { getOrCreateAddressableNote(it) }

            note.loadEvent(event, author, eventsApproved)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            eventsApproved.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReactionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return true

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addReaction(note) }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LabelEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return true

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Attach NIP-32 hashtag labels (namespace #t) to the labeled events so the
            // hashtag feed can surface posts tagged by a follow and attribute the labeler.
            val hashtags = event.hashtagAssociations()
            if (hashtags.isNotEmpty()) {
                event.labeledEvents().mapNotNull { checkGetOrCreateNote(it) }.forEach { target ->
                    hashtags.forEach { hashtag -> target.addLabel(hashtag, note) }

                    // If the labeled post is already in cache, re-notify feed observers so the
                    // hashtag feed can pick it up now that a (possibly followed) user labeled it.
                    if (target.event != null) refreshNewNoteObservers(target)
                }
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReportEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val authorsReported = event.reportedAuthor().mapNotNull { checkGetOrCreateUser(it.pubkey) }
            val eventsReported =
                event.reportedPost().mapNotNull { checkGetOrCreateNote(it.eventId) } +
                    event.reportedAddresses().map { getOrCreateAddressableNote(it.address) }

            if (eventsReported.isEmpty()) {
                authorsReported.forEach { author -> author.reports().addReport(note) }
            } else {
                eventsReported.forEach { it.addReport(note) }
            }
        }

        return new
    }

    fun consume(
        event: ChannelCreateEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreatePublicChatChannel(event.id)
        val author = getOrCreateUser(event.pubKey)
        val note = getOrCreateNote(event.id)

        val isVerified =
            if (note.event == null && (wasVerified || justVerify(event))) {
                oldChannel.addNote(note, relay)
                note.loadEvent(event, author, emptyList())

                refreshNewNoteObservers(note)
                true
            } else {
                wasVerified
            }

        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return false // older data, does nothing
        }

        if ((oldChannel.creator == null || oldChannel.creator == author) && (isVerified || justVerify(event))) {
            oldChannel.updateChannelInfo(author, event, note)
        }

        return isVerified
    }

    fun consume(
        event: ChannelMetadataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId()
        if (channelId.isNullOrBlank()) return false

        // new event
        val oldChannel = checkGetOrCreatePublicChatChannel(channelId) ?: return false

        val author = getOrCreateUser(event.pubKey)
        val note = getOrCreateNote(event.id)

        val isVerified =
            if (event.createdAt > oldChannel.updatedMetadataAt) {
                if (wasVerified || justVerify(event)) {
                    oldChannel.updateChannelInfo(author, event, note)
                    true
                } else {
                    false
                }
            } else {
                wasVerified
            }

        if (note.event == null && (isVerified || justVerify(event))) {
            oldChannel.addNote(note, relay)
            note.loadEvent(event, author, emptyList())

            refreshNewNoteObservers(note)
        }

        return isVerified
    }

    fun consume(
        event: ChannelMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val channel = checkGetOrCreatePublicChatChannel(channelId)
            if (channel == null) {
                Log.w("LocalCache") { "Unable to create public chat channel for event ${event.toJson()}" }
                return false
            }

            val note = getOrCreateNote(event.id)
            channel.addNote(note, relay)
        }

        return new
    }

    fun consume(
        event: EphemeralChatEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val roomId = event.roomId() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val note = getOrCreateNote(event.id)
            val channel = getOrCreateEphemeralChannel(roomId)
            channel.addNote(note, relay)
        }

        return new
    }

    /**
     * Public geohash chat message (kind 20000). Routes into the cell's
     * [GeohashChatChannel]. Presence (kind 20001) is deliberately NOT consumed
     * here — it is an empty-content heartbeat handled by the live chat screen, so
     * it never becomes a room's "last message".
     */
    fun consume(
        event: GeohashChatEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val geohash = event.geohash() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val note = getOrCreateNote(event.id)
            val channel = getOrCreateGeohashChannel(geohash)
            channel.addNote(note, relay)
        }

        return new
    }

    /**
     * NIP-29 addressables (kinds 39000-39005) are authoritative for a group's metadata,
     * roster, roles and pins ONLY when signed by the relay's own key — the NIP-11 `self`
     * pubkey. This returns false only when we can positively tell an event is NOT relay-signed
     * (the relay advertises a `self` and the event's author differs), so a stray or malicious
     * user-published 39000/39001/… served by a lax relay can't overwrite a group's state (e.g.
     * inject itself into the admin list). When `self` isn't known yet — the NIP-11 doc hasn't
     * loaded, or the relay doesn't advertise one — we don't block, so legitimate groups still
     * populate and this never regresses a relay whose key we simply haven't fetched.
     */
    private fun isRelaySignedGroupEvent(
        event: Event,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val self =
            Amethyst.instance.nip11Cache
                .getFromCache(relay)
                .self ?: return true
        return event.pubKey == self
    }

    /**
     * NIP-29 relay-signed group metadata (kind 39000). Stored as an addressable
     * note and used to populate the [RelayGroupChannel]'s name/picture/about/
     * flags. The group is keyed by (host relay + group id): unlike NIP-C7, a
     * NIP-29 event does not carry its host relay in a tag — the host is the relay
     * that served it — so the channel can only be associated when we know the
     * serving relay (provenance). With no relay we still store the metadata.
     */
    fun consume(
        event: GroupMetadataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val new = consumeBaseReplaceable(event, relay, wasVerified)

        if (relay != null && isRelaySignedGroupEvent(event, relay)) {
            val note = getOrCreateAddressableNote(event.address())
            val channel = getOrCreateRelayGroupChannel(GroupId(event.groupId(), relay))
            (note.event as? GroupMetadataEvent)?.let { channel.updateGroupInfo(it, note) }
        }

        return new
    }

    /** NIP-29 relay-signed member list (kind 39002) → the group's roster. */
    fun consume(
        event: GroupMembersEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val new = consumeBaseReplaceable(event, relay, wasVerified)
        if (relay != null && isRelaySignedGroupEvent(event, relay)) {
            val latest = getOrCreateAddressableNote(event.address()).event as? GroupMembersEvent
            latest?.let { getOrCreateRelayGroupChannel(GroupId(it.groupId(), relay)).updateMembers(it) }
        }
        return new
    }

    /** NIP-29 relay-signed admin list (kind 39001) → the group's roster. */
    fun consume(
        event: GroupAdminsEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val new = consumeBaseReplaceable(event, relay, wasVerified)
        if (relay != null && isRelaySignedGroupEvent(event, relay)) {
            val latest = getOrCreateAddressableNote(event.address()).event as? GroupAdminsEvent
            latest?.let { getOrCreateRelayGroupChannel(GroupId(it.groupId(), relay)).updateAdmins(it) }
        }
        return new
    }

    /** NIP-29 relay-signed pinned-message list (kind 39005) → the group's pins. */
    fun consume(
        event: GroupPinnedEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val new = consumeBaseReplaceable(event, relay, wasVerified)
        if (relay != null && isRelaySignedGroupEvent(event, relay)) {
            val latest = getOrCreateAddressableNote(event.address()).event as? GroupPinnedEvent
            latest?.let { getOrCreateRelayGroupChannel(GroupId(it.groupId(), relay)).updatePinned(it) }
        }
        return new
    }

    /** NIP-29 relay-declared supported roles (kind 39003) → the group's role set. */
    fun consume(
        event: SupportedRolesEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val new = consumeBaseReplaceable(event, relay, wasVerified)
        if (relay != null && isRelaySignedGroupEvent(event, relay)) {
            val latest = getOrCreateAddressableNote(event.address()).event as? SupportedRolesEvent
            latest?.let { getOrCreateRelayGroupChannel(GroupId(it.groupId(), relay)).updateSupportedRoles(it) }
        }
        return new
    }

    /**
     * Marks the serving relay as Buzz, but only off a VERIFIED event: the mark changes
     * what the composer sends (40002 vs kind 9) and how new channels on the relay are
     * treated, so an unverifiable frame from a buggy/hostile relay must not flip it.
     * The note-has-event check is the same verification gate the attach path uses.
     */
    private fun markBuzzIfVerified(
        event: Event,
        relay: NormalizedRelayUrl?,
    ) {
        if (relay != null && getNoteIfExists(event.id)?.event != null) {
            BuzzRelayDialect.mark(relay)
        }
    }

    /**
     * Consume + channel-attach for Buzz workspace timeline kinds (stream messages,
     * diffs, system rows, forum posts, job cards, huddle lifecycle). These kinds only
     * exist on `block/buzz` relays, so their (verified) arrival IS the dialect
     * detection. Attachment goes through the SAME shared NIP-29 path kind-9 chat uses
     * — including its stray-redirect protection — so mixed vanilla/Buzz conversations
     * share one timeline and non-host strays never mint phantom channels.
     */
    private fun consumeBuzzTimelineEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        consumeRegularEvent(event, relay, wasVerified).also {
            markBuzzIfVerified(event, relay)
            attachToRelayGroupIfScoped(event, relay)
        }

    /** Store-only consume for Buzz kinds that carry no channel timeline row. */
    private fun consumeBuzzRegularEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        consumeRegularEvent(event, relay, wasVerified).also {
            markBuzzIfVerified(event, relay)
        }

    private fun consume(
        event: StreamMessageEditEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        // Buzz's own timeline set excludes 40003: an edit is an OVERLAY replacing an
        // earlier message's content, never a row of its own. Store it and record the
        // overlay (keyed by the channel's UUID, so own sends with no provenance relay
        // land too), but do NOT attach it to the timeline.
        consumeBuzzRegularEvent(event, relay, wasVerified).also {
            val target = event.editedMessage() ?: return@also
            val channelId = event.channel() ?: return@also
            val editNote = getOrCreateNote(event.id)
            if (editNote.event != null) {
                BuzzWorkspaceStates.getOrCreate(channelId).addEdit(target, editNote)
            }
        }

    private fun consume(
        event: DmCreatedEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        // A relay-signed DM confirmation (41001): store it AND record the "this UUID is a
        // DM" fact into the process-wide registry so the DM list can surface it and the
        // workspace list can exclude it. The channel timeline reuses the relay-group stack.
        consumeBuzzRegularEvent(event, relay, wasVerified).also {
            val channelId = event.dmId().ifBlank { return@also }
            // The 41001 is relay-authored, so provenance is always the workspace relay.
            val provenance = relay ?: return@also
            BuzzDmRegistry.record(
                BuzzDmConversation(channelId, event.participants(), event.createdAt, provenance),
            )
        }

    private fun consume(
        event: DmVisibilityEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        // The relay-signed, `#p`-gated per-viewer hidden-DM snapshot (30622). Store the
        // addressable event AND mirror the viewer's hidden set into the registry so a
        // hidden DM drops out of that viewer's list until it's re-opened.
        consumeBaseReplaceable(event, relay, wasVerified).also {
            val viewer = event.viewer().ifBlank { return@also }
            BuzzDmRegistry.recordHidden(viewer, event.hiddenChannels().toSet())
        }

    private fun consume(
        event: CanvasEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        // The canvas is the channel's single living document, not a chat row: track
        // only the newest revision as overlay state, never attach it to the timeline.
        consumeBuzzRegularEvent(event, relay, wasVerified).also {
            val channelId = event.channel() ?: return@also
            val note = getOrCreateNote(event.id)
            if (note.event != null) {
                BuzzWorkspaceStates.getOrCreate(channelId).updateCanvas(note)
            }
        }

    /**
     * Attach a group-scoped content event (a kind-9 chat, kind-1068 poll, …
     * carrying an `h` tag) to its [RelayGroupChannel]. NIP-29 reuses the generic
     * content kinds and scopes them with `h`, so the note is consumed normally
     * and then, when it belongs to a group and we know the serving relay, added
     * to that group's channel timeline.
     */
    private fun attachToRelayGroupIfScoped(
        event: Event,
        relay: NormalizedRelayUrl?,
    ) {
        val groupId = event.groupId() ?: return
        val note = getOrCreateNote(event.id)
        // Only attach a note we've actually loaded — never a placeholder for an
        // unverified/not-yet-seen event. This is checked here (not via the "was
        // newly consumed" flag) so the host relay's echo of an event we already
        // stored from our own send still lands in the channel.
        if (note.event == null) return

        if (relay != null) {
            val exact = GroupId(groupId, relay)
            val existing = getRelayGroupChannelIfExists(exact)
            if (existing != null) {
                // Normal arrival: the group's host-pinned filters served it, so the serving relay IS the
                // group's key and its channel already exists. Fast O(1) path — no scan.
                existing.addNote(note, relay)
            } else {
                // No channel keyed to the serving relay: this may be a stray from a NON-host relay (e.g. a
                // quoted kind-9 resolved by id). Redirect it to the group's single confirmed host rather
                // than mint a phantom channel the group's screens never read (the serving-relay hazard);
                // fall back to the serving-relay key when there is no single host (new/ambiguous group).
                val target = redirectStrayRelayGroupContent(relayGroupCandidatesFor(groupId)) ?: exact
                getOrCreateRelayGroupChannel(target).addNote(note, relay)
            }
        } else {
            // Our own optimistic send has no provenance relay, so we can't build the (groupId,
            // relay) key. Attach only when a SINGLE open channel has this group id (the room being
            // composed in). When the id is ambiguous across relays — e.g. the relay-wide "_" group
            // joined on several relays — skip: attaching to all of them bleeds the message into
            // rooms it wasn't sent to. The host relay's echo (relay != null) lands it on the right key.
            relayGroupChannels
                .filter { key, _ -> key.id == groupId }
                .singleOrNull()
                ?.addNote(note, null)
        }
    }

    /** Candidate group channels for the [redirectStrayRelayGroupContent] slow path — one scan by group id. */
    private fun relayGroupCandidatesFor(groupId: String): List<RelayGroupTargetCandidate> =
        relayGroupChannels
            .filter { key, _ -> key.id == groupId }
            .map { RelayGroupTargetCandidate(it.groupId, it.hasRelaySignedState()) }

    /**
     * Same routing as [attachToRelayGroupIfScoped] but for kind-11 threads, which
     * are kept in a separate collection from the chat timeline so the two content
     * types don't mix in one feed.
     */
    private fun attachThreadToRelayGroupIfScoped(
        event: Event,
        relay: NormalizedRelayUrl?,
    ) {
        val groupId = event.groupId() ?: return
        val note = getOrCreateNote(event.id)
        if (note.event == null) return

        if (relay != null) {
            val exact = GroupId(groupId, relay)
            val existing = getRelayGroupChannelIfExists(exact)
            if (existing != null) {
                existing.addThread(note)
            } else {
                // Same serving-relay hazard as the chat path: prefer the single confirmed host over a phantom.
                val target = redirectStrayRelayGroupContent(relayGroupCandidatesFor(groupId)) ?: exact
                getOrCreateRelayGroupChannel(target).addThread(note)
            }
        } else {
            // See attachToRelayGroupIfScoped: only attach when the group id is unambiguous.
            relayGroupChannels
                .filter { key, _ -> key.id == groupId }
                .singleOrNull()
                ?.addThread(note)
        }
    }

    fun consume(
        event: LiveActivitiesChatMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val activityAddress = event.activityAddress() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val channel = getOrCreateLiveChannel(activityAddress)
            val note = getOrCreateNote(event.id)
            channel.addNote(note, relay)
        }

        return new
    }

    /**
     * Audio-room presence (kind-10312) — addressable storage plus an
     * author-keyed entry in the room's
     * [LiveActivitiesChannel.presenceNotes] index.
     *
     * Presence is intentionally NOT added to `channel.notes`: that
     * map is dominated by chat in active rooms and feeds that care
     * about presence (Nests drawer, home live-bubble, NestsFeedLoaded)
     * iterate `presenceNotes` directly for an O(speakers) scan.
     *
     * Cross-room move handling: kind-10312 is replaceable per author,
     * but the room a presence points to (`a`-tag) can change when a
     * speaker hops between rooms. The replaceable cache only swaps
     * the addressable's content — it has no notion of which channel
     * the old version was attached to. Without explicit eviction the
     * old room would keep surfacing as "live" via the stale entry
     * until it dropped out of the freshness window. Capture the prior
     * room before replacement and, when it differs, drop the author
     * from the old channel's presence index.
     */
    fun consume(
        event: MeetingRoomPresenceEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val priorVersion = getAddressableNoteIfExists(event.address())?.event as? MeetingRoomPresenceEvent
        val priorRoomAddress = priorVersion?.interactiveRoom()?.address
        val isReplacement = priorVersion != null && event.createdAt > priorVersion.createdAt

        val new = consumeBaseReplaceable(event, relay, wasVerified)

        val roomAddress = event.interactiveRoom()?.address ?: return new
        if (roomAddress.kind != MeetingSpaceEvent.KIND) return new

        if (isReplacement && priorRoomAddress != null && priorRoomAddress != roomAddress) {
            getLiveActivityChannelIfExists(priorRoomAddress)?.removePresenceNote(event.pubKey)
        }

        val channel = getOrCreateLiveChannel(roomAddress)
        val versionNote = getOrCreateNote(event.id)
        channel.addPresenceNote(versionNote)
        if (relay != null) channel.addRelay(relay)

        return new
    }

    fun consume(
        event: LiveActivitiesRaidEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val fromAddress = event.fromAddress()
        val toAddress = event.toAddress()
        if (fromAddress == null && toAddress == null) return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val note = getOrCreateNote(event.id)
            fromAddress?.let { getOrCreateLiveChannel(it).addNote(note, relay) }
            toAddress?.let { getOrCreateLiveChannel(it).addNote(note, relay) }
        }

        return new
    }

    fun consume(
        event: LiveActivitiesClipEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val activityAddress = event.activityAddress() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val channel = getOrCreateLiveChannel(activityAddress)
            val note = getOrCreateNote(event.id)
            channel.addNote(note, relay)
        }

        return new
    }

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelHideMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelMuteUserEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = false

    fun consume(
        event: LnZapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event — still ensure it's routed into any live-activity
        // channel(s) it references. A zap that was first consumed by, e.g., the notifications
        // subscription must still appear in the stream's chat when the user opens the stream.
        if (note.event != null) {
            attachZapToLiveActivityChannel(event, note, relay)
            return false
        }

        if (!(wasVerified || justVerify(event))) return false

        val existingZapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }
        if (existingZapRequest == null || existingZapRequest.event == null) {
            // tries to add it
            event.zapRequest?.let {
                checkDeletionAndConsume(it, relay, false)
            }
        }

        val zapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }

        if (zapRequest == null || zapRequest.event !is LnZapRequestEvent) {
            Log.d("ZP") { "Zap Request not found. Unable to process Zap {${event.toJson()}}" }
            return false
        }

        // NIP-57 Appendix F validation. Resolve the recipient's lnurl from their
        // profile metadata, look up the LNURL provider's `nostrPubkey`, and check
        // the receipt against it (signer + invoice amount + lnurl tag).
        //
        // Synchronous path: cache hit, or no resolver wired (fallback to legacy
        // signature-only behavior). Failed MUST-checks drop the receipt entirely.
        //
        // Async path: cache miss + resolver available. Load the event so feeds
        // and live channels see it, but defer the `addZap` credit until the
        // resolver returns. On async MUST-fail we never credit; the receipt
        // stays in cache as a visible artifact but contributes 0 to zap totals.
        val recipientLnurl = recipientLnurl(event)
        val recipientLnurlpUrl = recipientLnurl?.let { LnurlForm.toUrl(it) }
        val cachedInfo = recipientLnurlpUrl?.let { LnurlEndpointCache.get(it) }

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = computeReplyTo(event)

        if (cachedInfo != null) {
            val result =
                LnZapReceiptValidator.validate(
                    receipt = event,
                    expectedNostrPubkey = cachedInfo.nostrPubkey,
                    expectedLnurl = recipientLnurl,
                )
            if (result is LnZapReceiptValidator.Result.Invalid &&
                result.reason != LnZapReceiptValidator.Result.Reason.MISMATCHED_LNURL
            ) {
                Log.w("ZP") { "dropping zap receipt ${event.id}: ${result.reason} ${result.detail ?: ""}" }
                return false
            }
            if (result is LnZapReceiptValidator.Result.Invalid) {
                Log.w("ZP") { "zap receipt ${event.id} has mismatched lnurl tag (accepting per SHOULD)" }
            }

            note.loadEvent(event, author, repliesTo)
            repliesTo.forEach { it.addZap(zapRequest, note) }
            attachZapToLiveActivityChannel(event, note, relay)
            refreshNewNoteObservers(note)
            return true
        }

        val resolver = lnurlEndpointResolver
        if (resolver == null || recipientLnurlpUrl == null) {
            // No resolver configured, or recipient has no lud16/lud06 on file.
            // Legacy behavior: accept the receipt on signature verification alone.
            note.loadEvent(event, author, repliesTo)
            repliesTo.forEach { it.addZap(zapRequest, note) }
            attachZapToLiveActivityChannel(event, note, relay)
            refreshNewNoteObservers(note)
            return true
        }

        // Async validation. Make the event visible immediately, but only credit
        // it to repliesTo after the resolver confirms the LNURL provider's pubkey.
        note.loadEvent(event, author, repliesTo)
        attachZapToLiveActivityChannel(event, note, relay)
        refreshNewNoteObservers(note)

        Amethyst.instance.applicationIOScope.launch {
            try {
                val info = resolver.resolve(recipientLnurlpUrl)
                if (info == null) {
                    Log.w("ZP") { "could not fetch lnurlp for ${event.id}; not crediting" }
                    return@launch
                }
                val result =
                    LnZapReceiptValidator.validate(
                        receipt = event,
                        expectedNostrPubkey = info.nostrPubkey,
                        expectedLnurl = recipientLnurl,
                    )
                if (result is LnZapReceiptValidator.Result.Invalid &&
                    result.reason != LnZapReceiptValidator.Result.Reason.MISMATCHED_LNURL
                ) {
                    Log.w("ZP") { "dropping zap receipt ${event.id}: ${result.reason} ${result.detail ?: ""}" }
                    return@launch
                }
                if (result is LnZapReceiptValidator.Result.Invalid) {
                    Log.w("ZP") { "zap receipt ${event.id} has mismatched lnurl tag (accepting per SHOULD)" }
                }
                repliesTo.forEach { it.addZap(zapRequest, note) }
            } catch (t: Throwable) {
                Log.w("ZP", "validation failed for ${event.id}", t)
            }
        }

        return true
    }

    /**
     * Look up the recipient's lnurl from their kind:0 metadata. The recipient is
     * the receipt's first `p` tag (which the LNURL provider sets to the original
     * zap target). Returns null if we have no metadata, or the user has neither
     * lud16 nor lud06.
     */
    private fun recipientLnurl(event: LnZapEvent): String? {
        val recipientPubkey = event.zappedAuthor().firstOrNull() ?: return null
        val user = getUserIfExists(recipientPubkey) ?: return null
        return user.lnAddress()?.takeIf { it.isNotBlank() }
    }

    fun consume(
        event: OnchainZapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val alreadyLoaded = note.event != null

        // `relay == null` means this event was generated locally by the signed-in user
        // and routed through `justConsumeMyOwnEvent` — see `Account.sendOnchainZap` →
        // `LocalCache.justConsumeMyOwnEvent`. Only these get the optimistic gallery
        // attachment; for incoming zaps from others, the sender-claimed `amount` tag
        // is untrusted and could mislead the viewer until the chain verifier responds.
        val isOwnEvent = relay == null
        var repliesTo: List<Note>? = null

        if (!alreadyLoaded) {
            if (!(wasVerified || justVerify(event))) return false

            // Anti-spoofing: NIP-BC requires rejecting self-zaps.
            val recipient = event.recipient() ?: return false
            if (event.pubKey.equals(recipient, ignoreCase = true)) return false

            val author = getOrCreateUser(event.pubKey)
            val resolvedRepliesTo = computeReplyTo(event)
            repliesTo = resolvedRepliesTo
            note.loadEvent(event, author, resolvedRepliesTo)

            if (isOwnEvent) {
                // Optimistic attachment for the sender's own zap: surface it on the
                // thread immediately, before the chain backend has indexed the tx.
                // Crucial because `OnchainZapSender.send` consumes the kind:8333
                // milliseconds after broadcasting the tx — the verifier would
                // otherwise return TX_NOT_FOUND and the entry would never appear
                // until a later re-verification pass.
                val txid = event.txid()
                if (txid != null) {
                    // Clamp claimedSats to a non-negative value. `amount` tag parses
                    // via toLongOrNull() with no sign check, so a malicious sender
                    // could otherwise put "-1" in the gallery as a negative-sats badge.
                    val claimedSats = (event.claimedAmountInSats() ?: 0L).coerceAtLeast(0L)
                    resolvedRepliesTo.forEach {
                        it.addOnchainZap(note, txid, claimedSats, verifiedSats = 0L, OnchainZapStatus.UNVERIFIED)
                    }
                }
            }

            refreshNewNoteObservers(note)
        }

        // Async chain verification is delegated to OnchainZapResolver, which owns the
        // in-flight gates (so two relay echoes don't double-fetch) and the chain-tip
        // polling flow used by the gallery driver. Reusing the repliesTo already
        // computed above avoids the second computeReplyTo pass on the new-event path.
        onchainZapResolver.launchVerification(event, note, repliesTo ?: computeReplyTo(event))

        return !alreadyLoaded
    }

    /**
     * Consume a NIP-61 nutzap (kind 9321). Resolves the e-tagged target
     * note(s), parses the proof amounts once, and attaches a `NutzapEntry`
     * so the reaction-row counter, the "you-already-zapped" icon highlight,
     * and the dedicated cashu gallery row all light up the same way they
     * do for lightning zaps.
     *
     * Unlike `consume(OnchainZapEvent)`, there's no async verification
     * step today — the sender-claimed proof amounts are trusted up to
     * redeem time, when the recipient's wallet verifies them against the
     * mint. Adding a wallet-side verification gate that downgrades
     * unverifiable entries would be a follow-up.
     */
    fun consume(
        event: NutzapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        if (note.event != null) return false

        if (!(wasVerified || justVerify(event))) return false

        val author = getOrCreateUser(event.pubKey)
        val repliesTo = computeReplyTo(event)
        note.loadEvent(event, author, repliesTo)

        val claimedSats = event.claimedSatsTotal()
        repliesTo.forEach { it.addNutzap(note, claimedSats) }

        refreshNewNoteObservers(note)
        return true
    }

    private fun attachZapToLiveActivityChannel(
        event: LnZapEvent,
        note: Note,
        relay: NormalizedRelayUrl?,
    ) {
        // Match zap.stream: only show zaps whose receiver is the live activity host.
        val hosts = event.zappedAuthor().toHashSet()
        if (hosts.isEmpty()) return

        // Route into every live-activity address this zap references (zap.stream uses one, but
        // a receipt could legitimately reference multiple simulcasted streams).
        event.tags
            .asSequence()
            .mapNotNull(ATag::parseAddress)
            .filter { it.kind == LiveActivitiesEvent.KIND && it.pubKeyHex in hosts }
            .distinct()
            .forEach { address ->
                getOrCreateLiveChannel(address).addNote(note, relay)
            }
    }

    fun consume(
        event: LnZapRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addZap(note, null) }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: TextNoteModificationEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            event.editedNote()?.let {
                checkGetOrCreateNote(it.eventId)?.let { editedNote ->
                    modificationCache.remove(editedNote.idHex)
                    // must update list of Notes to quickly update the user.
                    editedNote.flowSet?.edits?.invalidateData()
                }
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: PollResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val pollId = event.poll()?.eventId
        if (pollId != null) {
            val pollNote = getOrCreateNote(pollId)
            val responseNote = getOrCreateNote(event.id)

            val new = consumeRegularEvent(event, relay, wasVerified)
            if (new) {
                pollNote.pollState().addResponse(responseNote)
            }
            return new
        }

        return false
    }

    fun consume(
        event: FileStorageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        val isVerified =
            try {
                val cachePath = Amethyst.instance.nip95cache
                cachePath.mkdirs()
                val file = File(cachePath, event.id)
                if (!file.exists() && (wasVerified || justVerify(event))) {
                    FileOutputStream(file).use { stream ->
                        stream.write(event.decode())
                    }
                    Log.i(
                        "FileStorageEvent",
                        "NIP95 File received from $relay and saved to disk as $file",
                    )
                    true
                } else {
                    wasVerified
                }
            } catch (e: IOException) {
                Log.e("FileStorageEvent", "FileStorageEvent save to disk error: " + event.id, e)
                wasVerified
            }

        // Already processed this event.
        if (note.event != null) return false

        if (isVerified || justVerify(event)) {
            // this is an invalid event. But we don't need to keep the data in memory.
            val eventNoData =
                FileStorageEvent(event.id, event.pubKey, event.createdAt, event.tags, "", event.sig)

            note.loadEvent(eventNoData, author, emptyList())

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapPaymentRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // Does nothing without a response callback.
        return true
    }

    fun consume(
        event: LnZapPaymentRequestEvent,
        zappedNote: Note?,
        wasVerified: Boolean,
        relay: NormalizedRelayUrl?,
        onResponse: suspend (LnZapPaymentResponseEvent) -> Unit,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val expectedServicePubkey =
                event.walletServicePubKey() ?: run {
                    Log.w("LocalCache") { "NWC request ${event.id} has no `p` tag; cannot register for response." }
                    return false
                }

            note.loadEvent(event, author, emptyList())

            relay?.let {
                note.addRelay(relay)
            }

            zappedNote?.addZapPayment(note, null)

            paymentTracker.registerRequest(event.id, expectedServicePubkey, zappedNote, onResponse)

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapPaymentResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val requestId = event.requestId()
        val pending =
            when (val match = paymentTracker.onResponseReceived(requestId, event.pubKey)) {
                is NwcPaymentTracker.MatchResult.Matched -> {
                    match.pending
                }

                is NwcPaymentTracker.MatchResult.WrongAuthor -> {
                    // Possible spoof: a kind-23195 event from someone other than
                    // the wallet service we sent the request to. The pending
                    // entry is left in place so the real response can still
                    // resolve it; we silently drop this one.
                    Log.w("LocalCache") {
                        "Rejecting NWC response ${event.id}: expected author ${match.expected} but event was signed by ${match.actual}. " +
                            "This may be a spoofed reply — keeping the request pending for the legitimate wallet response."
                    }
                    return false
                }

                NwcPaymentTracker.MatchResult.NoMatch -> {
                    Log.w("LocalCache") {
                        "NWC response ${event.id} from ${event.pubKey} references request e=$requestId but no pending request is registered. " +
                            "The response was either delivered after timeout, the user holds a stale subscription, or the wallet service set the wrong e tag."
                    }
                    return false
                }
            }

        val zappedNote = pending.zappedNote
        val responseCallback = pending.onResponse

        val requestNote = requestId?.let { checkGetOrCreateNote(requestId) }

        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            requestNote?.let { request -> zappedNote?.addZapPayment(request, note) }

            Amethyst.instance.applicationIOScope.launch {
                responseCallback(event)
            }

            return true
        }

        return false
    }

    fun findUsersStartingWith(
        username: String,
        forAccount: Account?,
    ): List<User> {
        if (username.isBlank()) return emptyList()

        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        val dualCase =
            listOf(
                DualCase(username.lowercase(), username.uppercase()),
            )

        val finds =
            users.filter { _, user: User ->
                val metadata = user.metadataOrNull()
                if (metadata == null) {
                    user.pubkeyHex.startsWith(username, true) ||
                        user.pubkeyNpub().startsWith(username, true)
                } else {
                    (
                        metadata.anyNameOrAddressContains(dualCase) ||
                            user.pubkeyHex.startsWith(username, true) ||
                            user.pubkeyNpub().startsWith(username, true)
                    ) &&
                        (forAccount == null || (!forAccount.isHidden(user) && !metadata.anyPropertyContains(forAccount.hiddenUsers.flow.value.hiddenWordsCase)))
                }
            }

        val findsFollowing = finds.associateWith { forAccount?.isFollowing(it) == true }
        val anyNameStartsWith = finds.associateWith { it.metadataOrNull()?.anyNameStartsWith(dualCase) == true }
        val anyAddressStartsWith = finds.associateWith { it.metadataOrNull()?.anyAddressStartsWith(dualCase) == true }
        val displayNames = finds.associateWith { it.toBestDisplayName().lowercase() }

        return finds.sortedWith(
            compareBy(
                { findsFollowing[it] == false },
                { anyNameStartsWith[it] == false },
                { anyAddressStartsWith[it] == false },
                { displayNames[it] },
                { it.pubkeyHex },
            ),
        )
    }

    /**
     * Will return true if supplied note is one of events to be excluded from
     * search results.
     */
    private fun excludeNoteEventFromSearchResults(note: Note): Boolean =
        (
            note.event is GenericRepostEvent ||
                note.event is RepostEvent ||
                note.event is CommunityPostApprovalEvent ||
                note.event is ReactionEvent ||
                note.event is LnZapEvent ||
                note.event is LnZapRequestEvent ||
                note.event is FileHeaderEvent ||
                note.event is MetadataEvent ||
                note.event is ContactListEvent ||
                note.event is AppSpecificDataEvent
        )

    /**
     * Tag names whose values should not match text searches: the `client` tag
     * names the app that published the event (searching for "Amethyst" would
     * otherwise return every event posted through Amethyst), and `p`/`e`/`a`/`alt`
     * values are ids or descriptions of other events, not content of this one.
     */
    private val excludedTagNamesFromSearch =
        setOf(
            ClientTag.TAG_NAME,
            PTag.TAG_NAME,
            ETag.TAG_NAME,
            ATag.TAG_NAME,
            AltTag.TAG_NAME,
        )

    fun findNotesStartingWith(
        text: String,
        hiddenUsers: HiddenUsersState,
    ): List<Note> {
        checkNotInMainThread()

        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val note = getNoteIfExists(key)
            val noteEvent = note?.event
            val newNote =
                if (noteEvent is AddressableEvent) {
                    val addressableNote = getAddressableNoteIfExists(noteEvent.address())
                    if (addressableNote?.event?.id == note.idHex) {
                        addressableNote
                    } else {
                        note
                    }
                } else {
                    note
                }

            if ((newNote != null) && !excludeNoteEventFromSearchResults(newNote)) {
                return listOfNotNull(newNote)
            }
        }

        return notes.filter { _, note ->
            if (note.event is AddressableEvent) {
                return@filter false
            }

            if (excludeNoteEventFromSearchResults(note)) {
                return@filter false
            }

            if (note.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                note.idHex.startsWith(text, true)
            ) {
                return@filter !note.isHiddenFor(hiddenUsers.flow.value)
            }

            if (note.event?.isContentEncoded() == false) {
                return@filter if (!note.isHiddenFor(hiddenUsers.flow.value)) {
                    note.event?.content?.contains(text, true) ?: false
                } else {
                    false
                }
            }

            return@filter false
        } +
            addressables.filter { _, addressable ->
                if (excludeNoteEventFromSearchResults(addressable)) {
                    return@filter false
                }

                if (addressable.event?.tags?.tagValueContains(text, true, excludedTagNamesFromSearch) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    return@filter !addressable.isHiddenFor(hiddenUsers.flow.value)
                }

                if (addressable.event?.isContentEncoded() == false) {
                    return@filter if (!addressable.isHiddenFor(hiddenUsers.flow.value)) {
                        addressable.event?.content?.contains(text, true) ?: false
                    } else {
                        false
                    }
                }

                return@filter false
            }
    }

    fun findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> {
        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)
        if (key != null) {
            getPublicChatChannelIfExists(key)?.let {
                return listOf(it)
            }
        }

        return publicChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> {
        if (text.isBlank()) return emptyList()

        return ephemeralChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> {
        if (text.isBlank()) return emptyList()

        try {
            val parsed = Nip19Parser.uriToRoute(text)?.entity
            if (parsed is NAddress && parsed.kind == LiveActivitiesEvent.KIND) {
                return listOf(getOrCreateLiveChannel(parsed.address()))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        return liveChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun getPeopleListNotesFor(user: User): List<AddressableNote> = addressables.filter(PeopleListEvent.KIND, user.pubkeyHex)

    suspend fun findEarliestOtsForNote(
        note: Note,
        otsVerifCacheBuilder: () -> VerificationStateCache,
    ): Long? {
        checkNotInMainThread()

        var minTime: Long? = null
        val time = TimeUtils.now()

        val candidates =
            notes.mapNotNull { _, item ->
                val noteEvent = item.event
                if ((noteEvent is OtsEvent && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time))) {
                    val cachedTime = (otsVerifCacheBuilder().justCache(noteEvent) as? VerificationState.Verified)?.verifiedTime
                    if (cachedTime != null) {
                        if (minTime == null || cachedTime < (minTime ?: Long.MAX_VALUE)) {
                            minTime = cachedTime
                        }
                        null
                    } else {
                        // tries to verify again
                        noteEvent
                    }
                } else {
                    null
                }
            }

        candidates.forEach { noteEvent ->
            (otsVerifCacheBuilder().cacheVerify(noteEvent) as? VerificationState.Verified)?.verifiedTime?.let { stampedTime ->
                if (minTime == null || stampedTime < (minTime ?: Long.MAX_VALUE)) {
                    minTime = stampedTime
                }
            }
        }

        return minTime
    }

    val modificationCache = LruCache<HexKey, List<Note>>(20)

    fun cachedModificationEventsForNote(note: Note): List<Note>? = modificationCache[note.idHex]

    fun findLatestModificationForNote(note: Note): List<Note> {
        checkNotInMainThread()

        val noteAuthor = note.author ?: return emptyList()

        modificationCache[note.idHex]?.let {
            return it
        }

        val time = TimeUtils.now()

        val newNotes =
            notes
                .filter { _, item ->
                    val noteEvent = item.event

                    noteEvent is TextNoteModificationEvent && noteAuthor == item.author && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time)
                }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))

        modificationCache.put(note.idHex, newNotes)

        return newNotes
    }

    fun cleanMemory() {
        Log.d("LargeCache") { "Notes cleanup started. Current size: ${notes.size()}" }
        notes.cleanUp()
        Log.d("LargeCache") { "Notes cleanup completed. Remaining size: ${notes.size()}" }

        Log.d("LargeCache") { "Addressables cleanup started. Current size: ${addressables.size()}" }
        addressables.cleanUp()
        Log.d("LargeCache") { "Addressables cleanup completed. Remaining size: ${addressables.size()}" }

        Log.d("LargeCache") { "Users cleanup started. Current size: ${users.size()}" }
        users.cleanUp()
        Log.d("LargeCache") { "Users cleanup completed. Remaining size: ${users.size()}" }
    }

    fun cleanObservers() {
        notes.forEach { _, it -> it.clearFlow() }
        addressables.forEach { _, it -> it.clearFlow() }
    }

    fun pruneHiddenMessagesChannel(
        channel: Channel,
        account: Account,
    ) {
        val toBeRemoved = channel.pruneHiddenMessages(account)

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)

            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} hidden messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneHiddenMessages(account: Account) {
        ephemeralChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        geohashChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        liveChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        publicChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        relayGroupChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }
    }

    // 2× the 10-min `PRESENCE_FRESHNESS_WINDOW_SECONDS` used by
    // `NestsFeedFilter` so a presence still inside any feed's window
    // can never be pruned.
    private val PRESENCE_PRUNE_AGE_SECONDS = 20L * 60L

    fun pruneOldMessagesChannel(channel: Channel) {
        val toBeRemoved = channel.pruneOldMessages()

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)

            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        // Audio-room presence is keyed separately from `notes` and
        // never gets reaped by the top-N rule. Drop entries older
        // than 2× the 10-min freshness window so the index doesn't
        // grow unbounded with every author who ever heartbeat here.
        if (channel is LiveActivitiesChannel) {
            channel.pruneStalePresence(TimeUtils.now() - PRESENCE_PRUNE_AGE_SECONDS)
        }

        // A Buzz workspace's edit/canvas overlay is keyed off the channel id, outside
        // `notes`, so the top-N reap never touches it. Drop overlay entries whose target
        // message was just pruned, else they pin the edit note + author forever.
        if (channel is RelayGroupChannel) {
            BuzzWorkspaceStates.getIfExists(channel.groupId.id)?.pruneEdits(channel.notes.keys())
        }

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} old messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneOldMessages() {
        checkNotInMainThread()

        ephemeralChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        geohashChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        liveChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        publicChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        relayGroupChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        chatroomList.forEach { userHex, room ->
            // History floors are pinned per scope on first advance; null means that window never paged
            // history, so its cursors hold no position to misalign and nothing needs rewinding. Only the
            // bands strictly BELOW a floor are this window's responsibility — a pruned message newer than
            // the floor is the always-on live tail's concern, and rewinding history for it would needlessly
            // re-page (and, for a busy room straddling the floor, mis-set the boundary). Hence the per-floor
            // filter when accumulating below.
            val giftWrapFloor = room.giftWrapHistory.floor
            val accountNip04Floor = room.nip04History.floor

            room.rooms.map { key, chatroom ->
                val toBeRemoved = chatroom.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                // Newest pruned `created_at` per relay, in each window's cursor space, capped at < floor.
                // Gift wraps page by the OUTER wrap time (from the rumor-host index); NIP-04 by the event's
                // own time, and a kind:4 belongs to BOTH the account (rooms-list) and per-conversation cursor.
                val giftWrapPruned = HashMap<NormalizedRelayUrl, Long>()
                val accountNip04Pruned = HashMap<NormalizedRelayUrl, Long>()
                val roomNip04Pruned = HashMap<NormalizedRelayUrl, Long>()
                // chatroom.nip04History is lazy — only touch (allocate) it when this room actually drops a
                // kind:4 message, so rooms that never paged conversation history pay nothing.
                val roomNip04Floor = if (toBeRemoved.any { it.event is PrivateDmEvent }) chatroom.nip04History.floor else null

                toBeRemoved.forEach { note ->
                    when (val ev = note.event) {
                        is BaseDMGroupEvent ->
                            if (giftWrapFloor != null) {
                                val outerUntil = note.rumorHost?.createdAt ?: ev.createdAt
                                if (outerUntil < giftWrapFloor) note.relays.forEach { giftWrapPruned.merge(it, outerUntil, ::maxOf) }
                            }
                        is PrivateDmEvent -> {
                            val until = ev.createdAt
                            if (accountNip04Floor != null && until < accountNip04Floor) note.relays.forEach { accountNip04Pruned.merge(it, until, ::maxOf) }
                            if (roomNip04Floor != null && until < roomNip04Floor) note.relays.forEach { roomNip04Pruned.merge(it, until, ::maxOf) }
                        }
                    }

                    childrenToBeRemoved.addAll(removeIfWrap(note))
                    unlinkAndRemove(note)

                    childrenToBeRemoved.addAll(note.clearChildLinks())
                }

                unlinkAndRemove(childrenToBeRemoved)

                // Realign the windows so a relay that already paged past (or `done` below) the dropped band
                // re-requests it on the next demand-advance instead of skipping the hole.
                if (giftWrapPruned.isNotEmpty()) {
                    room.giftWrapHistory.rewindTo(giftWrapPruned)
                    Log.d("DMPagination") { "[giftwrap] window rewound after prune: ${giftWrapPruned.size} relay(s), newest pruned wrap @${giftWrapPruned.values.max()}" }
                }
                if (accountNip04Pruned.isNotEmpty()) {
                    room.nip04History.rewindTo(accountNip04Pruned)
                    Log.d("DMPagination") { "[rooms.nip04] window rewound after prune: ${accountNip04Pruned.size} relay(s), newest pruned @${accountNip04Pruned.values.max()}" }
                }
                if (roomNip04Pruned.isNotEmpty()) {
                    chatroom.nip04History.rewindTo(roomNip04Pruned)
                    Log.d("DMPagination") { "[convo.nip04] window rewound after prune of ${key.users.joinToString()}: ${roomNip04Pruned.size} relay(s), newest pruned @${roomNip04Pruned.values.max()}" }
                }

                if (toBeRemoved.size > 1) {
                    println(
                        "PRUNE: ${toBeRemoved.size} private messages from $userHex to ${key.users.joinToString()} removed. ${chatroom.messages.size} kept",
                    )
                }
            }
        }
    }

    fun removeIfWrap(note: Note): List<Note> {
        val host = note.rumorHost ?: return emptyList()

        val children = mutableListOf<Note>()
        getNoteIfExists(host.id)?.let { hostNote ->
            (hostNote.event as? GiftWrapEvent)?.innerEventId?.let { sealId ->
                getNoteIfExists(sealId)?.let { sealNote ->
                    unlinkAndRemove(sealNote)
                    children.addAll(sealNote.clearChildLinks())
                }
            }
            unlinkAndRemove(hostNote)
            children.addAll(hostNote.clearChildLinks())
        }
        note.rumorHost = null
        return children
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved =
            notes.filter { _, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent) {
                    noteEvent.createdAt <
                        (addressables.get(noteEvent.address())?.event?.createdAt ?: 0)
                } else {
                    false
                }
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = (it.event as? AddressableEvent)?.address()?.let { tag -> addressables.get(tag) }
            if (newerVersion != null) {
                it.moveAllReferencesTo(newerVersion)
            }

            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} old version of addressables removed.")
        }
    }

    fun pruneRepliesAndReactions(accounts: Set<HexKey>) {
        checkNotInMainThread()

        val toBeRemoved =
            notes.filter { _, note ->
                (
                    (note.event is TextNoteEvent && !note.isNewThread()) ||
                        note.event is ReactionEvent ||
                        note.event is LnZapEvent ||
                        note.event is LnZapRequestEvent ||
                        note.event is ReportEvent ||
                        note.event is GenericRepostEvent
                ) &&
                    note.replyTo?.any { it.flowSet?.isInUse() == true } != true &&
                    note.flowSet?.isInUse() != true &&
                    // don't delete if observing.
                    note.author?.pubkeyHex !in
                    accounts &&
                    // don't delete if it is the logged in account
                    note.event?.isTaggedUsers(accounts) !=
                    true // don't delete if it's a notification to the logged in user
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    /**
     * Unlinks [note] from everything in the cache that references it, then drops it
     * from the [notes] map and notifies observers. This is the shared "unlink from
     * above" half of removal, used by both the prune callers and [deleteNote].
     *
     * It detaches the note from:
     *  - its parent notes (their replies/reactions/zaps/boosts/reports/labels maps);
     *    because event-level reports and torrent comments both carry the target in
     *    `replyTo`, [Note.removeNote] cleans those up here too;
     *  - its channels/gatherers (`inGatherers` is authoritative — `Channel.addNote`
     *    always registers the gatherer — and `getAnyChannel` is a belt-and-suspenders
     *    resolve so a note can never linger in a channel after leaving the cache);
     *  - the per-target indexes `replyTo` does NOT reach: user-level reports and
     *    reported addresses, contact cards, statuses, and poll responses.
     *
     * It deliberately does NOT touch the note's own children: prune callers collect
     * them via [Note.clearChildLinks] and remove the subtree, while [deleteNote]
     * keeps them and severs only their back-reference. Every per-target removal is
     * idempotent, so the overlap between `replyTo` and the explicit indexes (e.g. an
     * event-level report reachable both ways) is harmless. Addressable notes are
     * dropped from the [addressables] map by the caller; this only removes from [notes].
     */
    private fun unlinkAndRemove(note: Note) {
        note.replyTo?.forEach { masterNote ->
            masterNote.removeNote(note)
        }

        note.inGatherers?.forEach { it.removeNote(note) }

        getAnyChannel(note)?.removeNote(note)

        val noteEvent = note.event

        // Quote-repost boosts are tracked outside `replyTo` (see addQuoteBoosts), so
        // detach this note from every quoted note's boosts here.
        noteEvent?.taggedQuoteIds()?.forEach { quotedId ->
            getNoteIfExists(quotedId)?.removeBoost(note)
        }

        if (noteEvent is ReportEvent) {
            noteEvent.reportedAuthor().forEach {
                getUserIfExists(it.pubkey)?.reportsOrNull()?.removeReport(note)
            }

            noteEvent.reportedPost().forEach {
                getNoteIfExists(it.eventId)?.removeReport(note)
            }

            noteEvent.reportedAddresses().forEach {
                getAddressableNoteIfExists(it.address)?.removeReport(note)
            }
        }

        if (note is AddressableNote && noteEvent is ContactCardEvent) {
            getUserIfExists(noteEvent.aboutUser())?.cardsOrNull()?.removeCard(note)
        }

        if (note is AddressableNote && noteEvent is StatusEvent) {
            note.author?.statusStateOrNull()?.removeStatus(note)
        }

        if (noteEvent is PollResponseEvent) {
            noteEvent.poll()?.eventId?.let {
                getNoteIfExists(it)?.pollStateOrNull()?.removeResponse(note)
            }
        }

        note.clearFlow()

        notes.remove(note.idHex)

        refreshDeletedNoteObservers(note)
    }

    fun unlinkAndRemove(nextToBeRemoved: List<Note>) {
        nextToBeRemoved.forEach { note -> unlinkAndRemove(note) }
    }

    fun pruneExpiredEvents() {
        checkNotInMainThread()

        val now = TimeUtils.now()
        val versionsToBeRemoved = notes.filter { _, it -> it.event?.isExpirationBefore(now) == true }
        val addressesToBeRemoved = addressables.filter { _, it -> it.event?.isExpirationBefore(now) == true }

        val childrenToBeRemoved = mutableListOf<Note>()

        versionsToBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        addressesToBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (versionsToBeRemoved.size > 1 || addressesToBeRemoved.size > 1) {
            println("PRUNE: ${versionsToBeRemoved.size} events and ${addressesToBeRemoved.size} expired.")
        }
    }

    fun pruneHiddenEvents(account: Account) {
        checkNotInMainThread()

        val childrenToBeRemoved = mutableListOf<Note>()

        val toBeRemoved =
            account.hiddenUsers.flow.value.hiddenUsers.flatMap { userHex ->
                (notes.filter { _, it -> it.event?.pubKey == userHex } + addressables.filter { _, it -> it.event?.pubKey == userHex }).toSet()
            }

        toBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
    }

    override fun markAsSeen(
        eventId: String,
        relay: NormalizedRelayUrl,
    ) {
        val note = getNoteIfExists(eventId)

        note?.event?.let { noteEvent ->
            if (noteEvent is AddressableEvent) {
                getAddressableNoteIfExists(noteEvent.address())?.addRelay(relay)
            }
        }

        note?.let { addRelayToNoteAndInners(it, relay) }
    }

    /**
     * Adds [relay] to [note] and to every already-unwrapped inner note of its
     * gift-wrap chain (wrap → seal → rumor). The chat UI renders the inner
     * rumor, so a relay recorded only on the outer envelope never surfaces as
     * an icon. Inner notes that don't exist yet are not lost: the unwrap path
     * copies the envelope's relays down via [copyRelaysFromTo] when it runs.
     */
    fun addRelayToNoteAndInners(
        note: Note,
        relay: NormalizedRelayUrl,
    ) {
        note.addRelay(relay)

        val noteEvent = note.event
        if (noteEvent is HasInnerEvent) {
            noteEvent.innerEventId?.let { innerId ->
                getNoteIfExists(innerId)?.let { addRelayToNoteAndInners(it, relay) }
            }
        }
    }

    // Observers line up here.
    val live: LocalCacheFlow = LocalCacheFlow()

    private fun refreshNewNoteObservers(newNote: Note) {
        val event = newNote.event as Event

        // Index-driven fanout: only observers whose filter narrows
        // on a field this event carries (or that registered as
        // unindexed) get woken up. The match check inside each
        // observer's `new()` still enforces negative constraints.
        for (observer in observables.candidatesFor(event)) {
            observer.new(event, newNote)
        }
        live.newNote(newNote)
    }

    private fun refreshDeletedNoteObservers(newNote: Note) {
        // Deletes don't have a filterable shape — every observer
        // might hold this note in its result set, so iterate them
        // all. The index doesn't help here.
        observables.forEach { it.remove(newNote) }
        live.removedNote(newNote)
    }

    /**
     * Resource-usage ledger hook: called with (elapsedNanos, valid) for every
     * signature verification so the app can account crypto CPU per day.
     * Wired by AppModules like [onchainBackend]; null costs nothing.
     */
    @Volatile
    var verifyMeter: ((elapsedNanos: Long, valid: Boolean) -> Unit)? = null

    fun justVerify(event: Event): Boolean {
        checkNotInMainThread()

        val meter = verifyMeter
        if (meter == null) return justVerifyInner(event)

        val start = System.nanoTime()
        val valid = justVerifyInner(event)
        meter(System.nanoTime() - start, valid)
        return valid
    }

    private fun justVerifyInner(event: Event): Boolean =
        if (!event.verify()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Event Verification Failed") { "Kind: ${event.kind} from ${dateFormatter(event.createdAt, "", "")} with message ${e.message}" }
            }
            false
        } else {
            true
        }

    fun consume(
        event: DraftWrapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = !event.isDeleted() && consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(nip19: Entity) {
        when (nip19) {
            is NSec -> {
                getOrCreateUser(nip19.toPubKeyHex())
            }

            is NPub -> {
                getOrCreateUser(nip19.hex)
            }

            is NProfile -> {
                nip19.relay.forEach { relayHint ->
                    relayHints.addKey(nip19.hex, relayHint)
                }
                getOrCreateUser(nip19.hex)
            }

            is NNote -> {
                getOrCreateNote(nip19.hex)
            }

            is NEvent -> {
                nip19.relay.forEach { relayHint ->
                    relayHints.addEvent(nip19.hex, relayHint)
                }
                val note = getOrCreateNote(nip19.hex)
                if (note.author == null) {
                    nip19.author?.let { note.author = checkGetOrCreateUser(it) }
                }
            }

            is NEmbed -> {
                justConsume(nip19.event, null, false)
            }

            is NRelay -> {}

            is NAddress -> {
                val aTag = nip19.aTag()
                nip19.relay.forEach { relayHint ->
                    relayHints.addAddress(aTag, relayHint)
                }
                getOrCreateAddressableNote(nip19.address())
            }

            else -> { }
        }
    }

    override fun justConsumeMyOwnEvent(event: Event) = justConsumeAndUpdateIndexes(event, null, true)

    fun justConsume(
        event: Event,
        relay: IRelayClient?,
        wasVerified: Boolean,
    ): Boolean {
        if (deletionIndex.hasBeenDeleted(event)) {
            // update relay with deletion event from another.
            if (relay != null) {
                deletionIndex.hasBeenDeletedBy(event)?.let { deletionEvent ->
                    getNoteIfExists(deletionEvent.id)?.let { note ->
                        if (!note.hasRelay(relay.url)) {
                            if (isDebug) {
                                Log.d("LocalCache") { "Updating ${relay.url.url} with a Deletion Event ${event.id} ${deletionEvent.id} because of ${event.toJson()} with ${deletionEvent.toJson()}" }
                            }
                            relay.sendIfConnected(EventCmd(deletionEvent))
                            note.addRelay(relay.url)
                        }
                    }
                }
            }
            return false
        }

        if (event is AddressableEvent && relay != null) {
            // updates relay with a new event.
            getAddressableNoteIfExists(event.address())?.let { note ->
                note.event?.let { existingEvent ->
                    if (existingEvent.createdAt > event.createdAt && !note.hasRelay(relay.url) && !deletionIndex.hasBeenDeleted(event) && !event.isExpired()) {
                        if (isDebug) {
                            Log.d("LocalCache") { "Updating ${relay.url.url} with a new version of ${event.kind} ${event.id} to ${existingEvent.id}" }
                        }

                        relay.sendIfConnected(EventCmd(existingEvent))
                        // only send once.
                        note.addRelay(relay.url)
                    }
                }
            }
        }

        return justConsumeAndUpdateIndexes(event, relay?.url, wasVerified)
    }

    fun checkDeletionAndConsume(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        if (!deletionIndex.hasBeenDeleted(event)) {
            justConsumeAndUpdateIndexes(event, relay, wasVerified)
        } else {
            false
        }

    private fun justConsumeAndUpdateIndexes(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // uses the internal event to avoid reprocessing cached items.
        val note =
            if (event is AddressableEvent) {
                getOrCreateAddressableNote(event.address())
            } else {
                getOrCreateNote(event.id)
            }

        val wasNew = justConsumeInnerInner(event, relay, wasVerified)

        if (wasNew) {
            updateHintIndexes(event)
            updateMintIndex(event)
            updateInGatherer(event, note)
        }

        if (relay != null) {
            note.event?.let { consumedEvent ->
                addIncomingRelayAsHintToAllRelatedEvents(consumedEvent, relay)
            }
        }

        return wasNew
    }

    fun addIncomingRelayAsHintToAllRelatedEvents(
        event: Event,
        relay: NormalizedRelayUrl,
    ) {
        relayHints.addEvent(event.id, relay)
        if (event is AddressableEvent) {
            relayHints.addAddress(event.addressTag(), relay)
        }

        if (event is EventHintProvider) {
            event.linkedEventIds().forEach {
                relayHints.addEvent(it, relay)
            }
        }
        if (event is AddressHintProvider) {
            event.linkedAddressIds().forEach {
                relayHints.addAddress(it, relay)
            }
        }
        if (event is PubKeyHintProvider) {
            event.linkedPubKeys().forEach {
                relayHints.addKey(it, relay)
            }
        }
    }

    fun updateHintIndexes(event: Event) {
        if (event is EventHintProvider) {
            event.eventHints().forEach {
                relayHints.addEvent(it.eventId, it.relay)
            }
        }
        if (event is AddressHintProvider) {
            event.addressHints().forEach {
                relayHints.addAddress(it.addressId, it.relay)
            }
        }
        if (event is PubKeyHintProvider) {
            event.pubKeyHints().forEach {
                relayHints.addKey(it.pubkey, it.relay)
            }
        }
    }

    /**
     * Feeds the Cashu mint URL directory from every event that names a
     * mint, regardless of which user authored it. Three sources today:
     *
     *  - NutzapInfoEvent (kind:10019) — every nostr user with a Cashu
     *    wallet publishes their accepted mints here, so a typical inbox
     *    of cached profiles seeds a useful starter directory.
     *  - MintRecommendationEvent (kind:38000) — explicit public vouches.
     *  - CashuMintEvent (kind:38172) — formal mint announcements.
     *
     * Called only when the event is newly consumed (mirrors
     * updateHintIndexes) so a re-emission of a cached event doesn't
     * inflate the popularity counter.
     */
    fun updateMintIndex(event: Event) {
        when (event) {
            is NutzapInfoEvent -> mintDirectory.addAll(event.mints().map { it.mintUrl })
            is MintRecommendationEvent -> mintDirectory.addAll(event.mintUrls())
            is CashuMintEvent -> event.mintUrl()?.let { mintDirectory.add(it) }
            else -> Unit
        }
    }

    fun updateInGatherer(
        event: Event,
        note: Note,
    ) {
        if (event is EventHintProvider) {
            event.linkedEventIds().forEach {
                checkGetOrCreateNote(it)?.addGatherer(note)
            }
        }
        if (event is AddressHintProvider) {
            event.linkedAddressIds().forEach {
                checkGetOrCreateAddressableNote(it)?.addGatherer(note)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun justConsumeInnerInner(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        try {
            when (event) {
                is AcceptedBadgeSetEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AdvertisedRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AppDefinitionEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AppRecommendationEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AppSpecificDataEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AttestationEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AttestationRequestEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AttestorRecommendationEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AttestorProficiencyEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is AudioHeaderEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is AudioTrackEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BadgeAwardEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is BadgeDefinitionEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BlockedRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BlossomServersEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NestsServersEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BroadcastRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BookmarkListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is OldBookmarkListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CalendarEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CalendarDateSlotEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CalendarTimeSlotEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CalendarRSVPEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CallAnswerEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CallHangupEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CallIceCandidateEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CallOfferEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CallRejectEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CallRenegotiateEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                // ============================================================
                // NIP-60 Cashu wallet + NIP-61 nutzaps
                // ============================================================
                is CashuWalletEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CashuTokenEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CashuSpendingHistoryEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CashuMintQuoteEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is NutzapInfoEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NutzapEvent -> {
                    consume(event, relay, wasVerified)
                }

                // ============================================================
                // NIP-87 Cashu mint discovery + recommendations
                // ============================================================
                // All three are kind 3xxxx (parameterized-replaceable per the
                // spec) but neither CashuMintEvent / FedimintEvent /
                // MintRecommendationEvent extends AddressableEvent in Quartz
                // today, so consumeBaseReplaceable's `check(event is
                // AddressableEvent)` would crash. Route through
                // consumeRegularEvent — downstream consumers
                // (CashuMintDirectoryState, CashuWalletState) already dedupe
                // by (pubKey, dTag) and keep the newest. Without these
                // entries the dispatch falls into the "Event Not Supported"
                // else branch and silently drops the event, so our own
                // kind:38000 thumbs-up never lands in the cache.
                is CashuMintEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is FedimintEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is MintRecommendationEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ChannelCreateEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ChannelListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ChannelHideMessageEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ChannelMessageEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ChannelMetadataEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ChannelMuteUserEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ChatMessageEncryptedFileHeaderEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ChatMessageEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ChatMessageRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ClassifiedsEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is FundraiserEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BirdexEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is BirdDetectionEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is Ps1SaveEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CommentEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CommunityDefinitionEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CommunityListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is CommunityPostApprovalEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ContactListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is DeletionEvent -> {
                    consume(event, relay, wasVerified)
                }

                is DraftWrapEvent -> {
                    consume(event, relay, wasVerified)
                }

                is EmojiPackEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is EmojiPackSelectionEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is EphemeralChatEvent -> {
                    consume(event, relay, wasVerified)
                }

                is GeohashChatEvent -> {
                    consume(event, relay, wasVerified)
                }

                is EphemeralChatListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                // NIP-51 "simple groups" list (kind 10009): the user's joined NIP-29 groups +
                // servers. Replaceable like its sibling lists; RelayGroupListState reads it from the
                // addressable cache, so it must be stored (it was silently dropped before).
                is SimpleGroupListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                // Concord private joined-communities list (kind 13302). Replaceable, self-encrypted;
                // ConcordChannelListState observes it via the addressable cache (Address(13302, me, "")),
                // so — exactly like the 10009 list above — it must be stored replaceably or the Concord
                // hub stays empty even after the event arrives.
                is ConcordCommunityListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is GroupMetadataEvent -> {
                    consume(event, relay, wasVerified)
                }

                is GroupMembersEvent -> {
                    consume(event, relay, wasVerified)
                }

                is GroupAdminsEvent -> {
                    consume(event, relay, wasVerified)
                }

                is GroupPinnedEvent -> {
                    consume(event, relay, wasVerified)
                }

                // 39003 (relay-declared roles) is durable group state like 39000/39001/39002:
                // route it onto the channel so a moderation UI can offer the relay's role set.
                is SupportedRolesEvent -> {
                    consume(event, relay, wasVerified)
                }

                // Remaining NIP-29 relay-group kinds. The relay-signed 39004 AV-participants
                // addressable is durable group state, so it's stored replaceably. The 9xxx
                // moderation actions and join/leave requests are regular one-shot events the
                // relay is authoritative for (it applies them and republishes the
                // 39000/39001/39002); we store them so they're queryable and don't fall through
                // to the "Not Supported" warning, but we don't act on them client-side.
                is GroupParticipantsEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is PutUserEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is RemoveUserEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is EditMetadataEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is DeleteEventEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is UpdatePinListEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is DeleteGroupEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CreateGroupEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CreateInviteEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is JoinRequestEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is LeaveRequestEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ExternalIdentitiesEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is GenericRepostEvent -> {
                    consume(event, relay, wasVerified)
                }

                is FhirResourceEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is FileHeaderEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ProfileGalleryEntryEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is FileServersEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is FileStorageEvent -> {
                    consume(event, relay, wasVerified)
                }

                is FileStorageHeaderEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is FollowListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is GeohashListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is GoalEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GiftWrapEvent -> {
                    // A wrap with an empty content carries no NIP-44 ciphertext and can
                    // never be unwrapped — reject it before paying for a signature check
                    // and a cache slot. Locally stripped copies (copyNoContent) are
                    // assigned straight to note.event and never pass through here.
                    if (event.content.isEmpty()) {
                        false
                    } else {
                        consumeRegularEvent(event, relay, wasVerified)
                    }
                }

                is GroupEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitIssueEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitReplyEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitPatchEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitPullRequestEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitPullRequestUpdateEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitStatusEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is GitRepositoryEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is GitRepositoryStateEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is UserGraspListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is RootSiteEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NamedSiteEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is RootNappletEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NamedNappletEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ChessGameEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is RelayFeedsListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is JesterEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is KeyPackageEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is KeyPackageRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveChessGameChallengeEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveChessGameAcceptEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveChessMoveEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveChessGameEndEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveChessDrawOfferEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is HashtagListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is FavoriteAlgoFeedsListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is HighlightEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is IndexerRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is InteractiveStoryPrologueEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is InteractiveStorySceneEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is InteractiveStoryReadingStateEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is InterestSetEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LabeledBookmarkListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LiveActivitiesEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LiveActivitiesChatMessageEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LiveActivitiesRaidEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LiveActivitiesClipEvent -> {
                    consume(event, relay, wasVerified)
                }

                is MeetingSpaceEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is MeetingRoomEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is MeetingRoomPresenceEvent -> {
                    consume(event, relay, wasVerified)
                }

                is MusicTrackEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is MusicPlaylistEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is PodcastEpisodeEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is PodcastMetadataEvent -> {
                    // Drop the known "Mock Podcast" spam flood instead of caching thousands of them.
                    if (event.isMockSpam()) {
                        false
                    } else {
                        consumeBaseReplaceable(event, relay, wasVerified)
                    }
                }

                is AuthoredPodcastsEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is FavoritePodcastsListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is Podcasting20EpisodeEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is Podcasting20TrailerEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is LnZapEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LnZapRequestEvent -> {
                    consume(event, relay, wasVerified)
                }

                is OnchainZapEvent -> {
                    consume(event, relay, wasVerified)
                }

                is NIP90StatusEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is NIP90ContentDiscoveryResponseEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is NIP90ContentDiscoveryRequestEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is NIP90UserDiscoveryResponseEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is NIP90UserDiscoveryRequestEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is LnZapPaymentRequestEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LnZapPaymentResponseEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LongTextNoteEvent -> {
                    consume(event, relay, wasVerified)
                }

                is MetadataEvent -> {
                    consume(event, relay, wasVerified)
                }

                is MuteListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NNSEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is NipTextEvent -> {
                    consume(event, relay, wasVerified)
                }

                is OtsEvent -> {
                    consume(event, relay, wasVerified)
                }

                is PictureEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is PrivateDmEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is PrivateOutboxRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ProfileBadgesEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ProxyRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is PinListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is PublicMessageEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is PeopleListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is RequestToVanishEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is CodeSnippetEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ZapPollEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ChatEvent -> {
                    consumeRegularEvent(event, relay, wasVerified).also {
                        // Attach on every arrival, not just the newly-consumed one:
                        // our own send is consumed first with a null relay, so the
                        // host relay's later echo (new == false) is what carries the
                        // provenance needed to key the channel. attach is idempotent.
                        attachToRelayGroupIfScoped(event, relay)
                    }
                }

                // ------------------------------------------------------------------
                // Buzz workspace kinds (block/buzz — the Buzz dialect of NIP-29).
                // Timeline kinds attach into the group's BuzzWorkspaceChannel; the
                // rest are stored for query/state. Kinds 9041/20001/39005/49001 are
                // absent on purpose: their numbers belong to GoalEvent,
                // GeohashPresenceEvent, GroupPinnedEvent and a non-wire audit kind.
                // ------------------------------------------------------------------

                is StreamMessageV2Event -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is StreamMessageEditEvent -> consume(event, relay, wasVerified)
                is StreamMessageDiffEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is SystemMessageEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is CanvasEvent -> consume(event, relay, wasVerified)
                is ForumPostEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is ForumCommentEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is ForumVoteEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobRequestEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobAcceptedEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobProgressEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobResultEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobCancelEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is JobErrorEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is HuddleStartedEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is HuddleParticipantJoinedEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is HuddleParticipantLeftEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)
                is HuddleEndedEvent -> consumeBuzzTimelineEvent(event, relay, wasVerified)

                // Buzz addressable/replaceable state.
                is PersonaEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is TeamEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is ManagedAgentEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is AgentProfileEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is EngramEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is WorkflowDefEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is EventReminderEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is PushLeaseEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is DmVisibilityEvent -> consume(event, relay, wasVerified)
                is WindowBoundsEvent -> consumeBaseReplaceable(event, relay, wasVerified)
                is ArchivedIdentitiesListEvent -> consumeBaseReplaceable(event, relay, wasVerified)

                // Buzz store-only regular kinds (queryable state; no timeline row yet).
                is StreamMessagePinnedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is StreamMessageBookmarkedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is StreamMessageScheduledEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is StreamReminderEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is DmCreatedEvent -> consume(event, relay, wasVerified)
                is DmOpenEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is DmAddMemberEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is DmHideEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is MemberAddedNotificationEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is MemberRemovedNotificationEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is AgentTurnMetricEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ModerationBanEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ModerationTimeoutEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ModerationUntimeoutEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ModerationResolveReportEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ProductFeedbackEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is RelayAdminAddMemberEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is RelayAdminRemoveMemberEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is RelayAdminChangeRoleEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is SetWorkspaceProfileEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ArchiveRequestEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is UnarchiveRequestEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ArchivedIdentityEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is UnarchivedIdentityEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is HuddleGuidelinesEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowTriggeredEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowStepStartedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowStepCompletedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowStepFailedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowCompletedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowFailedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowCancelledEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowApprovalRequestedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowApprovalGrantedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowApprovalDeniedEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is WorkflowTriggerEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ApprovalGrantEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ApprovalDenyEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)

                // Buzz relay-signed sidecars and audit projections: store-only, queryable.
                is AuditEntryEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is ChannelSummaryEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)
                is PresenceSnapshotEvent -> consumeBuzzRegularEvent(event, relay, wasVerified)

                // Buzz ephemeral signals: transient by definition (20000-29999) — do not
                // pollute the note store, and do NOT mark the dialect from them (they are
                // never stored, so the verified-mark gate has nothing to check).
                is TypingIndicatorEvent -> {
                    // Live "who is typing" side-effect only — record the heartbeat into the
                    // process-wide typing state (the channel view collects it) and return
                    // false so it never becomes a feed row. Own-typing is filtered in the UI.
                    event.channelId()?.let { BuzzTypingState.record(it, event.pubKey, event.createdAt, TimeUtils.now()) }
                    false
                }
                is ObserverFrameEvent -> false
                is HuddleReactionEvent -> false
                // Pairing (24134) is deliberately dialect-neutral: it flows during device
                // pairing before any workspace relationship is established.
                is PairingEvent -> false

                is PollEvent -> {
                    consumeRegularEvent(event, relay, wasVerified).also {
                        attachToRelayGroupIfScoped(event, relay)
                    }
                }

                is ThreadEvent -> {
                    consumeRegularEvent(event, relay, wasVerified).also {
                        attachThreadToRelayGroupIfScoped(event, relay)
                    }
                }

                is PollResponseEvent -> {
                    consume(event, relay, wasVerified)
                }

                is RoadEventReportEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is RoadEventConfirmationEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is RelayDiscoveryEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is RelayMonitorEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ReactionEvent -> {
                    consume(event, relay, wasVerified)
                }

                is LabelEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ContactCardEvent -> {
                    consume(event, relay, wasVerified)
                }

                is RelaySetEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is ReportEvent -> {
                    consume(event, relay, wasVerified)
                }

                is RepostEvent -> {
                    consume(event, relay, wasVerified)
                }

                is ReleaseArtifactSetEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is SealedRumorEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is SearchRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is SoftwareApplicationEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is SoftwareAssetEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is StatusEvent -> {
                    consume(event, relay, wasVerified)
                }

                is TextNoteEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is TextNoteModificationEvent -> {
                    consume(event, relay, wasVerified)
                }

                is TorrentEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is TorrentCommentEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is TrustedRelayListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is TrustProviderListEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is VideoHorizontalEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is VideoNormalEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is VideoVerticalEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is VideoShortEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is VoiceEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is VoiceReplyEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is WakeUpEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is WebBookmarkEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is WelcomeEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is WikiNoteEvent -> {
                    consume(event, relay, wasVerified)
                }

                is WorkoutRecordEvent -> {
                    consumeRegularEvent(event, relay, wasVerified)
                }

                is ExerciseTemplateEvent -> {
                    consumeBaseReplaceable(event, relay, wasVerified)
                }

                is PaymentTargetsEvent -> {
                    consume(event, relay, wasVerified)
                }

                else -> {
                    Log.w("Event Not Supported") { "From ${relay?.url}: ${event.toJson()}" }.let { false }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("LocalCache", "Cannot consume ${event.toJson()} from ${relay?.url}", e)
            false
        }

    fun hasConsumed(notificationEvent: Event): Boolean =
        if (notificationEvent is AddressableEvent) {
            val note = addressables.get(notificationEvent.address())
            val noteEvent = note?.event
            noteEvent != null && notificationEvent.createdAt <= noteEvent.createdAt
        } else {
            val note = notes.get(notificationEvent.id)
            note?.event != null
        }

    fun copyRelaysFromTo(
        from: Note,
        to: Event,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelay(it)
        }
    }

    fun copyRelaysFromTo(
        from: Note,
        to: HexKey,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelay(it)
        }
    }
}

@Stable
class LocalCacheFlow {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
    val newEventBundles = _newEventBundles.asSharedFlow() // read-only public view

    private val _deletedEventBundles = MutableSharedFlow<Set<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
    val deletedEventBundles = _deletedEventBundles.asSharedFlow() // read-only public view

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(1000, Dispatchers.IO)

    // Refreshes observers in batches.
    private val bundler2 = BundledInsert<Note>(1000, Dispatchers.IO)

    fun newNote(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }

    fun removedNote(newNote: Note) {
        bundler2.invalidateList(newNote) { bundledNewNotes ->
            _deletedEventBundles.emit(bundledNewNotes)
        }
    }
}
