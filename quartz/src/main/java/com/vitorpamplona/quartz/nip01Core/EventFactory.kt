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
package com.vitorpamplona.quartz.nip01Core

import com.vitorpamplona.quartz.blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.audio.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip71Video.VideoViewEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip89AppHandlers.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

class EventFactory {
    companion object {
        val factories: MutableMap<Int, (HexKey, HexKey, Long, Array<Array<String>>, String, HexKey) -> Event> = mutableMapOf()

        fun create(
            id: String,
            pubKey: String,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
            sig: String,
        ): Event =
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
                BlossomServersEvent.KIND -> BlossomServersEvent(id, pubKey, createdAt, tags, content, sig)
                BlossomAuthorizationEvent.KIND -> BlossomAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                BookmarkListEvent.KIND -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarDateSlotEvent.KIND -> CalendarDateSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarEvent.KIND -> CalendarEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarTimeSlotEvent.KIND -> CalendarTimeSlotEvent(id, pubKey, createdAt, tags, content, sig)
                CalendarRSVPEvent.KIND -> CalendarRSVPEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelCreateEvent.KIND -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelHideMessageEvent.KIND -> ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelListEvent.KIND -> ChannelListEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMessageEvent.KIND -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMetadataEvent.KIND -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
                ChannelMuteUserEvent.KIND -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
                ChatMessageEncryptedFileHeaderEvent.KIND -> {
                    if (id.isBlank()) {
                        ChatMessageEncryptedFileHeaderEvent(
                            Event.generateId(pubKey, createdAt, kind, tags, content),
                            pubKey,
                            createdAt,
                            tags,
                            content,
                            sig,
                        )
                    } else {
                        ChatMessageEncryptedFileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                    }
                }
                ChatMessageEvent.KIND -> {
                    if (id.isBlank()) {
                        ChatMessageEvent(
                            Event.generateId(pubKey, createdAt, kind, tags, content),
                            pubKey,
                            createdAt,
                            tags,
                            content,
                            sig,
                        )
                    } else {
                        ChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                    }
                }
                ChatMessageRelayListEvent.KIND -> ChatMessageRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ClassifiedsEvent.KIND -> ClassifiedsEvent(id, pubKey, createdAt, tags, content, sig)
                CommentEvent.KIND -> CommentEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityDefinitionEvent.KIND -> CommunityDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityListEvent.KIND -> CommunityListEvent(id, pubKey, createdAt, tags, content, sig)
                CommunityPostApprovalEvent.KIND -> CommunityPostApprovalEvent(id, pubKey, createdAt, tags, content, sig)
                ContactListEvent.KIND -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
                DeletionEvent.KIND -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)
                DraftEvent.KIND -> DraftEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiPackEvent.KIND -> EmojiPackEvent(id, pubKey, createdAt, tags, content, sig)
                EmojiPackSelectionEvent.KIND -> EmojiPackSelectionEvent(id, pubKey, createdAt, tags, content, sig)
                FileHeaderEvent.KIND -> FileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                ProfileGalleryEntryEvent.KIND -> ProfileGalleryEntryEvent(id, pubKey, createdAt, tags, content, sig)
                FileServersEvent.KIND -> FileServersEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageEvent.KIND -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
                FileStorageHeaderEvent.KIND -> FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
                FhirResourceEvent.KIND -> FhirResourceEvent(id, pubKey, createdAt, tags, content, sig)
                GenericRepostEvent.KIND -> GenericRepostEvent(id, pubKey, createdAt, tags, content, sig)
                GiftWrapEvent.KIND -> GiftWrapEvent(id, pubKey, createdAt, tags, content, sig)
                GitIssueEvent.KIND -> GitIssueEvent(id, pubKey, createdAt, tags, content, sig)
                GitReplyEvent.KIND -> GitReplyEvent(id, pubKey, createdAt, tags, content, sig)
                GitPatchEvent.KIND -> GitPatchEvent(id, pubKey, createdAt, tags, content, sig)
                GitRepositoryEvent.KIND -> GitRepositoryEvent(id, pubKey, createdAt, tags, content, sig)
                GoalEvent.KIND -> GoalEvent(id, pubKey, createdAt, tags, content, sig)
                HighlightEvent.KIND -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
                HTTPAuthorizationEvent.KIND -> HTTPAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryPrologueEvent.KIND -> InteractiveStoryPrologueEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStorySceneEvent.KIND -> InteractiveStorySceneEvent(id, pubKey, createdAt, tags, content, sig)
                InteractiveStoryReadingStateEvent.KIND -> InteractiveStoryReadingStateEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesChatMessageEvent.KIND -> LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                LiveActivitiesEvent.KIND -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapEvent.KIND -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentRequestEvent.KIND -> LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPaymentResponseEvent.KIND -> LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapPrivateEvent.KIND -> LnZapPrivateEvent(id, pubKey, createdAt, tags, content, sig)
                LnZapRequestEvent.KIND -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
                LongTextNoteEvent.KIND -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                MetadataEvent.KIND -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
                MuteListEvent.KIND -> MuteListEvent(id, pubKey, createdAt, tags, content, sig)
                NNSEvent.KIND -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
                com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent.KIND ->
                    com.vitorpamplona.quartz.nip46RemoteSigner
                        .NostrConnectEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90StatusEvent.KIND -> NIP90StatusEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryRequestEvent.KIND -> NIP90ContentDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90ContentDiscoveryResponseEvent.KIND -> NIP90ContentDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryRequestEvent.KIND -> NIP90UserDiscoveryRequestEvent(id, pubKey, createdAt, tags, content, sig)
                NIP90UserDiscoveryResponseEvent.KIND -> NIP90UserDiscoveryResponseEvent(id, pubKey, createdAt, tags, content, sig)
                OtsEvent.KIND -> OtsEvent(id, pubKey, createdAt, tags, content, sig)
                PeopleListEvent.KIND -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
                PictureEvent.KIND -> PictureEvent(id, pubKey, createdAt, tags, content, sig)
                PinListEvent.KIND -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
                PollNoteEvent.KIND -> PollNoteEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateDmEvent.KIND -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
                PrivateOutboxRelayListEvent.KIND -> PrivateOutboxRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                ReactionEvent.KIND -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
                RelationshipStatusEvent.KIND -> RelationshipStatusEvent(id, pubKey, createdAt, tags, content, sig)
                RelayAuthEvent.KIND -> RelayAuthEvent(id, pubKey, createdAt, tags, content, sig)
                RelaySetEvent.KIND -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
                ReportEvent.KIND -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
                RepostEvent.KIND -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
                SealedGossipEvent.KIND -> SealedGossipEvent(id, pubKey, createdAt, tags, content, sig)
                SearchRelayListEvent.KIND -> SearchRelayListEvent(id, pubKey, createdAt, tags, content, sig)
                StatusEvent.KIND -> StatusEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteEvent.KIND -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
                TextNoteModificationEvent.KIND -> TextNoteModificationEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentEvent.KIND -> TorrentEvent(id, pubKey, createdAt, tags, content, sig)
                TorrentCommentEvent.KIND -> TorrentCommentEvent(id, pubKey, createdAt, tags, content, sig)
                VideoHorizontalEvent.KIND -> VideoHorizontalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoVerticalEvent.KIND -> VideoVerticalEvent(id, pubKey, createdAt, tags, content, sig)
                VideoViewEvent.KIND -> VideoViewEvent(id, pubKey, createdAt, tags, content, sig)
                WikiNoteEvent.KIND -> WikiNoteEvent(id, pubKey, createdAt, tags, content, sig)
                else -> {
                    factories[kind]?.let {
                        return it(id, pubKey, createdAt, tags, content, sig)
                    }

                    Event(id, pubKey, createdAt, kind, tags, content, sig)
                }
            }
    }
}
