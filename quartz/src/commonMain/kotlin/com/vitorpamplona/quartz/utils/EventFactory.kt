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
package com.vitorpamplona.quartz.utils

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
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.relationshipStatus.ContactCardEvent
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
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
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
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
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
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
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

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
                AdvertisedRelayListEvent.KIND -> AdvertisedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                AppDefinitionEvent.KIND -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                AppRecommendationEvent.KIND -> AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
                AppSpecificDataEvent.KIND -> AppSpecificDataEvent(id, pubKey, createdAt, tags, content, sig)
                AudioHeaderEvent.KIND -> AudioHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                AudioTrackEvent.KIND -> AudioTrackEvent(id, pubKey, createdAt, tags, content, sig)
                BadgeAwardEvent.KIND -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
                BadgeDefinitionEvent.KIND -> BadgeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                BadgeProfilesEvent.KIND -> BadgeProfilesEvent(id, pubKey, createdAt, tags, content, sig)
                BlockedRelayListEvent.KIND -> BlockedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomServersEvent.KIND -> BlossomServersEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomAuthorizationEvent.KIND -> BlossomAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                BroadcastRelayListEvent.KIND -> BroadcastRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                BookmarkListEvent.KIND -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarDateSlotEvent.KIND -> CalendarDateSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarEvent.KIND -> CalendarEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarTimeSlotEvent.KIND -> CalendarTimeSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarRSVPEvent.KIND -> CalendarRSVPEvent(id, pubKey, createdAt, tags, content, sig)
                ChessGameEvent.KIND -> ChessGameEvent(id, pubKey, createdAt, tags, content, sig)
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
                GitIssueEvent.KIND -> GitIssueEvent(id, pubKey, createdAt, tags, content, sig)
                GitReplyEvent.KIND -> GitReplyEvent(id, pubKey, createdAt, tags, content, sig)
                GitPatchEvent.KIND -> GitPatchEvent(id, pubKey, createdAt, tags, content, sig)
                GitRepositoryEvent.KIND -> GitRepositoryEvent(id, pubKey, createdAt, tags, content, sig)
                GoalEvent.KIND -> GoalEvent(id, pubKey, createdAt, tags, content, sig)
                HashtagListEvent.KIND -> HashtagListEvent(id, pubKey, createdAt, tags, content, sig)
                HighlightEvent.KIND -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
                HTTPAuthorizationEvent.KIND -> HTTPAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                IndexerRelayListEvent.KIND -> IndexerRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryPrologueEvent.KIND -> InteractiveStoryPrologueEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStorySceneEvent.KIND -> InteractiveStorySceneEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryReadingStateEvent.KIND -> InteractiveStoryReadingStateEvent(id, pubKey, createdAt, tags, content, sig)
                LabeledBookmarkListEvent.KIND -> LabeledBookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesChatMessageEvent.KIND -> LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesEvent.KIND -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapEvent.KIND -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentRequestEvent.KIND -> LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentResponseEvent.KIND -> LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPrivateEvent.KIND -> LnZapPrivateEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapRequestEvent.KIND -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LongTextNoteEvent.KIND -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomEvent.KIND -> MeetingRoomEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingRoomPresenceEvent.KIND -> MeetingRoomPresenceEvent(id, pubKey, createdAt, tags, content, sig)
                MeetingSpaceEvent.KIND -> MeetingSpaceEvent(id, pubKey, createdAt, tags, content, sig)
                MetadataEvent.KIND -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
                MuteListEvent.KIND -> MuteListEvent(id, pubKey, createdAt, tags, content, sig)
                NNSEvent.KIND -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
                NipTextEvent.KIND -> NipTextEvent(id, pubKey, createdAt, tags, content, sig)
                NostrConnectEvent.KIND -> NostrConnectEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90StatusEvent.KIND -> NIP90StatusEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryRequestEvent.KIND -> NIP90ContentDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryResponseEvent.KIND -> NIP90ContentDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryRequestEvent.KIND -> NIP90UserDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryResponseEvent.KIND -> NIP90UserDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                OtsEvent.KIND -> OtsEvent(id, pubKey, createdAt, tags, content, sig)
                PaymentTargetsEvent.KIND -> PaymentTargetsEvent(id, pubKey, createdAt, tags, content, sig)
                PeopleListEvent.KIND -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
                PictureEvent.KIND -> PictureEvent(id, pubKey, createdAt, tags, content, sig)
                PinListEvent.KIND -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
                PollNoteEvent.KIND -> PollNoteEvent(id, pubKey, createdAt, tags, content, sig)
                PollEvent.KIND -> PollEvent(id, pubKey, createdAt, tags, content, sig)
                PollResponseEvent.KIND -> PollResponseEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateDmEvent.KIND -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateOutboxRelayListEvent.KIND -> PrivateOutboxRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ProxyRelayListEvent.KIND -> ProxyRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                PublicMessageEvent.KIND -> PublicMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ReactionEvent.KIND -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
                ContactCardEvent.KIND -> ContactCardEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAuthEvent.KIND -> RelayAuthEvent(id, pubKey, createdAt, tags, content, sig)
                RelaySetEvent.KIND -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
                ReportEvent.KIND -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
                RepostEvent.KIND -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
                RequestToVanishEvent.KIND -> RequestToVanishEvent(id, pubKey, createdAt, tags, content, sig)
                SealedRumorEvent.KIND -> SealedRumorEvent(id, pubKey, createdAt, tags, content, sig)
                SearchRelayListEvent.KIND -> SearchRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                StatusEvent.KIND -> StatusEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteEvent.KIND -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteModificationEvent.KIND -> TextNoteModificationEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentEvent.KIND -> TorrentEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentCommentEvent.KIND -> TorrentCommentEvent(id, pubKey, createdAt, tags, content, sig)
                TrustedRelayListEvent.KIND -> TrustedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                TrustProviderListEvent.KIND -> TrustProviderListEvent(id, pubKey, createdAt, tags, content, sig)
                VideoHorizontalEvent.KIND -> VideoHorizontalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoVerticalEvent.KIND -> VideoVerticalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoNormalEvent.KIND -> VideoNormalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoShortEvent.KIND -> VideoShortEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceEvent.KIND -> VoiceEvent(id, pubKey, createdAt, tags, content, sig)
                VoiceReplyEvent.KIND -> VoiceReplyEvent(id, pubKey, createdAt, tags, content, sig)
                WikiNoteEvent.KIND -> WikiNoteEvent(id, pubKey, createdAt, tags, content, sig)
                else -> factories[kind]?.build(id, pubKey, createdAt, tags, content, sig) ?: Event(id, pubKey, createdAt, kind, tags, content, sig)
            } as T
    }
}
