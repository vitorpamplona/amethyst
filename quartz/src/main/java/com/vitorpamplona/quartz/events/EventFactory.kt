package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.toHexKey

class EventFactory {
    companion object {
        fun create(
            id: String,
            pubKey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String,
            sig: String
        ) = when (kind) {
            AdvertisedRelayListEvent.kind -> AdvertisedRelayListEvent(id, pubKey, createdAt, tags, content, sig)
            AppDefinitionEvent.kind -> AppDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            AppRecommendationEvent.kind -> AppRecommendationEvent(id, pubKey, createdAt, tags, content, sig)
            AudioHeaderEvent.kind -> AudioHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            AudioTrackEvent.kind -> AudioTrackEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeAwardEvent.kind -> BadgeAwardEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeDefinitionEvent.kind -> BadgeDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            BadgeProfilesEvent.kind -> BadgeProfilesEvent(id, pubKey, createdAt, tags, content, sig)
            BookmarkListEvent.kind -> BookmarkListEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarEvent.kind -> CalendarEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarDateSlotEvent.kind -> CalendarDateSlotEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarTimeSlotEvent.kind -> CalendarTimeSlotEvent(id, pubKey, createdAt, tags, content, sig)
            CalendarRSVPEvent.kind -> CalendarRSVPEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelCreateEvent.kind -> ChannelCreateEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelHideMessageEvent.kind -> ChannelHideMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMessageEvent.kind -> ChannelMessageEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMetadataEvent.kind -> ChannelMetadataEvent(id, pubKey, createdAt, tags, content, sig)
            ChannelMuteUserEvent.kind -> ChannelMuteUserEvent(id, pubKey, createdAt, tags, content, sig)
            ChatMessageEvent.kind -> {
                if (id.isBlank()) {
                    val id = Event.generateId(pubKey, createdAt, kind, tags, content).toHexKey()
                    ChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                } else {
                    ChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
                }
            }
            ClassifiedsEvent.kind -> ClassifiedsEvent(id, pubKey, createdAt, tags, content, sig)
            CommunityDefinitionEvent.kind -> CommunityDefinitionEvent(id, pubKey, createdAt, tags, content, sig)
            CommunityPostApprovalEvent.kind -> CommunityPostApprovalEvent(id, pubKey, createdAt, tags, content, sig)
            ContactListEvent.kind -> ContactListEvent(id, pubKey, createdAt, tags, content, sig)
            DeletionEvent.kind -> DeletionEvent(id, pubKey, createdAt, tags, content, sig)

            EmojiPackEvent.kind -> EmojiPackEvent(id, pubKey, createdAt, tags, content, sig)
            EmojiPackSelectionEvent.kind -> EmojiPackSelectionEvent(id, pubKey, createdAt, tags, content, sig)
            SealedGossipEvent.kind -> SealedGossipEvent(id, pubKey, createdAt, tags, content, sig)

            FileHeaderEvent.kind -> FileHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageEvent.kind -> FileStorageEvent(id, pubKey, createdAt, tags, content, sig)
            FileStorageHeaderEvent.kind -> FileStorageHeaderEvent(id, pubKey, createdAt, tags, content, sig)
            GenericRepostEvent.kind -> GenericRepostEvent(id, pubKey, createdAt, tags, content, sig)
            GiftWrapEvent.kind -> GiftWrapEvent(id, pubKey, createdAt, tags, content, sig)
            HighlightEvent.kind -> HighlightEvent(id, pubKey, createdAt, tags, content, sig)
            LiveActivitiesEvent.kind -> LiveActivitiesEvent(id, pubKey, createdAt, tags, content, sig)
            LiveActivitiesChatMessageEvent.kind -> LiveActivitiesChatMessageEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapEvent.kind -> LnZapEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentRequestEvent.kind -> LnZapPaymentRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPaymentResponseEvent.kind -> LnZapPaymentResponseEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapPrivateEvent.kind -> LnZapPrivateEvent(id, pubKey, createdAt, tags, content, sig)
            LnZapRequestEvent.kind -> LnZapRequestEvent(id, pubKey, createdAt, tags, content, sig)
            LongTextNoteEvent.kind -> LongTextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            MetadataEvent.kind -> MetadataEvent(id, pubKey, createdAt, tags, content, sig)
            NNSEvent.kind -> NNSEvent(id, pubKey, createdAt, tags, content, sig)
            PeopleListEvent.kind -> PeopleListEvent(id, pubKey, createdAt, tags, content, sig)
            PinListEvent.kind -> PinListEvent(id, pubKey, createdAt, tags, content, sig)
            PollNoteEvent.kind -> PollNoteEvent(id, pubKey, createdAt, tags, content, sig)
            PrivateDmEvent.kind -> PrivateDmEvent(id, pubKey, createdAt, tags, content, sig)
            ReactionEvent.kind -> ReactionEvent(id, pubKey, createdAt, tags, content, sig)
            RecommendRelayEvent.kind -> RecommendRelayEvent(id, pubKey, createdAt, tags, content, sig)
            RelaySetEvent.kind -> RelaySetEvent(id, pubKey, createdAt, tags, content, sig)
            ReportEvent.kind -> ReportEvent(id, pubKey, createdAt, tags, content, sig)
            RepostEvent.kind -> RepostEvent(id, pubKey, createdAt, tags, content, sig)
            StatusEvent.kind -> StatusEvent(id, pubKey, createdAt, tags, content, sig)
            TextNoteEvent.kind -> TextNoteEvent(id, pubKey, createdAt, tags, content, sig)
            else -> Event(id, pubKey, createdAt, kind, tags, content, sig)
        }
    }
}
