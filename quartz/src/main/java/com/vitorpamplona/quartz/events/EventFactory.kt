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
package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.toHexKey

class EventFactory {
    companion object {
        fun create(
            id: String,
            pubKey: String,
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
            sig: String,
        ) = when (kind) {
            AdvertisedRelayListEvent.KIND ->
                AdvertisedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
            AppDefinitionEvent.KIND -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            AppRecommendationEvent.KIND ->
                AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
            AudioHeaderEvent.KIND -> AudioHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            AudioTrackEvent.KIND -> AudioTrackEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeAwardEvent.KIND -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeDefinitionEvent.KIND -> BadgeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeProfilesEvent.KIND -> BadgeProfilesEvent(id, pubKey, createdAt, tags, content, sig)
            BookmarkListEvent.KIND -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarDateSlotEvent.KIND ->
                CalendarDateSlotEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarEvent.KIND -> CalendarEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarTimeSlotEvent.KIND ->
                CalendarTimeSlotEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarRSVPEvent.KIND -> CalendarRSVPEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelCreateEvent.KIND -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelHideMessageEvent.KIND ->
                ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelListEvent.KIND -> ChannelListEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMessageEvent.KIND -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMetadataEvent.KIND -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMuteUserEvent.KIND -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
            ChatMessageEvent.KIND -> {
                if (id.isBlank()) {
                    ChatMessageEvent(
                        Event.generateId(pubKey, createdAt, kind, tags, content).toHexKey(),
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
            ClassifiedsEvent.KIND -> ClassifiedsEvent(id, pubKey, createdAt, tags, content, sig)
            CommunityDefinitionEvent.KIND ->
                CommunityDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            CommunityListEvent.KIND -> CommunityListEvent(id, pubKey, createdAt, tags, content, sig)
            CommunityPostApprovalEvent.KIND ->
                CommunityPostApprovalEvent(id, pubKey, createdAt, tags, content, sig)
            ContactListEvent.KIND -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
            DeletionEvent.KIND -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)
            DirectMessageRelayListEvent.KIND -> DirectMessageRelayListEvent(id, pubKey, createdAt, tags, content, sig)
            DraftEvent.KIND -> DraftEvent(id, pubKey, createdAt, tags, content, sig)
            EmojiPackEvent.KIND -> EmojiPackEvent(id, pubKey, createdAt, tags, content, sig)
            EmojiPackSelectionEvent.KIND ->
                EmojiPackSelectionEvent(id, pubKey, createdAt, tags, content, sig)
            FileHeaderEvent.KIND -> FileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            FileServersEvent.KIND -> FileServersEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageEvent.KIND -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageHeaderEvent.KIND ->
                FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            FhirResourceEvent.KIND -> FhirResourceEvent(id, pubKey, createdAt, tags, content, sig)
            GenericRepostEvent.KIND -> GenericRepostEvent(id, pubKey, createdAt, tags, content, sig)
            GiftWrapEvent.KIND -> GiftWrapEvent(id, pubKey, createdAt, tags, content, sig)
            GitIssueEvent.KIND -> GitIssueEvent(id, pubKey, createdAt, tags, content, sig)
            GitReplyEvent.KIND -> GitReplyEvent(id, pubKey, createdAt, tags, content, sig)
            GitPatchEvent.KIND -> GitPatchEvent(id, pubKey, createdAt, tags, content, sig)
            GitRepositoryEvent.KIND -> GitRepositoryEvent(id, pubKey, createdAt, tags, content, sig)
            GoalEvent.KIND -> GoalEvent(id, pubKey, createdAt, tags, content, sig)
            HighlightEvent.KIND -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
            HTTPAuthorizationEvent.KIND ->
                HTTPAuthorizationEvent(id, pubKey, createdAt, tags, content, sig)
            LiveActivitiesChatMessageEvent.KIND ->
                LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
            LiveActivitiesEvent.KIND -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapEvent.KIND -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentRequestEvent.KIND ->
                LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentResponseEvent.KIND ->
                LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPrivateEvent.KIND -> LnZapPrivateEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapRequestEvent.KIND -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LongTextNoteEvent.KIND -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            MetadataEvent.KIND -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
            MuteListEvent.KIND -> MuteListEvent(id, pubKey, createdAt, tags, content, sig)
            NNSEvent.KIND -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
            OtsEvent.KIND -> OtsEvent(id, pubKey, createdAt, tags, content, sig)
            PeopleListEvent.KIND -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
            PinListEvent.KIND -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
            PollNoteEvent.KIND -> PollNoteEvent(id, pubKey, createdAt, tags, content, sig)
            PrivateDmEvent.KIND -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
            ReactionEvent.KIND -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
            RecommendRelayEvent.KIND -> RecommendRelayEvent(id, pubKey, createdAt, tags, content, sig)
            RelayAuthEvent.KIND -> RelayAuthEvent(id, pubKey, createdAt, tags, content, sig)
            RelaySetEvent.KIND -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
            ReportEvent.KIND -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
            RepostEvent.KIND -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
            SealedGossipEvent.KIND -> SealedGossipEvent(id, pubKey, createdAt, tags, content, sig)
            StatusEvent.KIND -> StatusEvent(id, pubKey, createdAt, tags, content, sig)
            TextNoteEvent.KIND -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            TextNoteModificationEvent.KIND -> TextNoteModificationEvent(id, pubKey, createdAt, tags, content, sig)
            VideoHorizontalEvent.KIND -> VideoHorizontalEvent(id, pubKey, createdAt, tags, content, sig)
            VideoVerticalEvent.KIND -> VideoVerticalEvent(id, pubKey, createdAt, tags, content, sig)
            VideoViewEvent.KIND -> VideoViewEvent(id, pubKey, createdAt, tags, content, sig)
            WikiNoteEvent.KIND -> WikiNoteEvent(id, pubKey, createdAt, tags, content, sig)
            else -> Event(id, pubKey, createdAt, kind, tags, content, sig)
        }
    }
}
