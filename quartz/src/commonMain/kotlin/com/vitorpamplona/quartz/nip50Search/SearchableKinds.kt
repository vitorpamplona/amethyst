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
package com.vitorpamplona.quartz.nip50Search

/**
 * Every kind [com.vitorpamplona.quartz.utils.EventFactory] builds a [SearchableEvent] for.
 *
 * The list exists because the question "does this kind carry text worth matching?" was being
 * answered by hand in four places at once — a relay allowlist, a local denylist, a search-token
 * alias table, and this interface — and the four disagreed. [SearchableEvent] is the only one of
 * them that is a fact about the protocol rather than a guess about it, so it is the one the
 * others should be derived from, and this is that fact in a form the rest of the codebase can
 * read.
 *
 * ## Why it is written out rather than computed
 *
 * There is no reflection in common code and no registry to walk: [com.vitorpamplona.quartz.utils.EventFactory]
 * is a `when` over ~400 kinds. Computing this would mean building one event per kind across the
 * whole 16-bit space on every startup. So the answer is recorded here and `SearchableKindsTest`
 * does the sweep instead — it builds every kind from 0 to 65535 and asserts this list is exactly
 * the set that came back searchable. A kind added to Quartz therefore lands as a failing test
 * naming the number, not as a silent gap.
 *
 * ## "EventFactory builds", not "the class implements"
 *
 * These are not the same set, and the difference is a real defect rather than a subtlety: a class
 * can implement [SearchableEvent] and never be registered in the factory, in which case its events
 * parse as a plain [com.vitorpamplona.quartz.nip01Core.core.Event] and nothing ever calls its
 * `indexableContent()`. `FeedDefinitionEvent` (31890) is in exactly that state, along with eleven
 * other unregistered event classes. Since an unregistered kind cannot be searched no matter what
 * it declares, the reachable set is the one worth listing — but the unreachable ones are a bug in
 * the factory, not a decision.
 */
object SearchableKinds {
    /** Sorted ascending. */
    val ALL =
        listOf(
            0, // MetadataEvent
            1, // TextNoteEvent
            9, // ChatEvent
            11, // ThreadEvent
            14, // ChatMessageEvent
            20, // PictureEvent
            21, // VideoNormalEvent
            22, // VideoShortEvent
            24, // PublicMessageEvent
            31, // ExternalCitationEvent
            32, // HardcopyCitationEvent
            33, // PromptCitationEvent
            40, // ChannelCreateEvent
            41, // ChannelMetadataEvent
            42, // ChannelMessageEvent
            54, // PodcastEpisodeEvent
            818, // WikiMergeRequestEvent
            1010, // TextNoteModificationEvent
            1063, // FileHeaderEvent
            1065, // FileStorageHeaderEvent
            1068, // PollEvent
            1111, // CommentEvent
            1163, // ProfileGalleryEntryEvent
            1301, // WorkoutRecordEvent
            1311, // LiveActivitiesChatMessageEvent
            1312, // LiveActivitiesRaidEvent
            1313, // LiveActivitiesClipEvent
            1315, // RoadEventReportEvent
            1337, // CodeSnippetEvent
            1617, // GitPatchEvent
            1618, // GitPullRequestEvent
            1621, // GitIssueEvent
            1622, // GitReplyEvent
            1630, // GitStatusOpenEvent
            1631, // GitStatusAppliedEvent
            1632, // GitStatusClosedEvent
            1633, // GitStatusDraftEvent
            1808, // AudioHeaderEvent
            1985, // LabelEvent
            2003, // TorrentEvent
            2004, // TorrentCommentEvent
            2473, // BirdDetectionEvent
            3302, // ConcordChatEditEvent
            5050, // NIP90TextGenerationRequestEvent
            5100, // NIP90ImageGenerationRequestEvent
            5129, // NappletSnapshotEvent
            5250, // NIP90TextToSpeechRequestEvent
            5302, // NIP90ContentSearchRequestEvent
            5303, // NIP90PeopleSearchRequestEvent
            6969, // ZapPollEvent
            8333, // OnchainZapEvent
            9002, // EditMetadataEvent
            9041, // GoalEvent
            9321, // NutzapEvent
            9734, // LnZapRequestEvent
            9735, // LnZapEvent
            9736, // Bolt12ZapEvent
            9737, // Bolt12ZapIntentEvent
            9802, // HighlightEvent
            10003, // BookmarkListEvent
            10100, // AgentProfileEvent
            10154, // PodcastMetadataEvent
            11871, // AttestorProficiencyEvent
            12473, // BirdexEvent
            15128, // RootSiteEvent
            15129, // RootNappletEvent
            30000, // PeopleListEvent
            30001, // OldBookmarkListEvent
            30002, // RelaySetEvent
            30003, // LabeledBookmarkListEvent
            30004, // ArticleCurationSetEvent
            30005, // VideoCurationSetEvent
            30006, // PictureCurationSetEvent
            30009, // BadgeDefinitionEvent
            30015, // InterestSetEvent
            30017, // StallEvent
            30018, // ProductEvent
            30019, // MarketplaceEvent
            30020, // AuctionEvent
            30023, // LongTextNoteEvent
            30030, // EmojiPackEvent
            30040, // PublicationIndexEvent
            30041, // PublicationContentEvent
            30045, // BookshelfDirectoryEvent
            30054, // Podcasting20EpisodeEvent
            30055, // Podcasting20TrailerEvent
            30063, // ReleaseArtifactSetEvent
            30142, // LearningResourceEvent
            30175, // PersonaEvent
            30176, // TeamEvent
            30177, // ManagedAgentEvent
            30267, // AppCurationSetEvent
            30296, // InteractiveStoryPrologueEvent
            30297, // InteractiveStorySceneEvent
            30311, // LiveActivitiesEvent
            30312, // MeetingSpaceEvent
            30313, // MeetingRoomEvent
            30315, // StatusEvent
            30382, // ContactCardEvent
            30392, // UserTrustedListEvent
            30393, // EventTrustedListEvent
            30394, // AddressableTrustedListEvent
            30395, // ExternalIdTrustedListEvent
            30402, // ClassifiedsEvent
            30617, // GitRepositoryEvent
            30620, // WorkflowDefEvent
            30817, // NipTextEvent
            30818, // WikiNoteEvent
            31337, // AudioTrackEvent
            31871, // AttestationEvent
            31872, // AttestationRequestEvent
            31873, // AttestorRecommendationEvent
            31922, // CalendarDateSlotEvent
            31923, // CalendarTimeSlotEvent
            31924, // CalendarEvent
            31925, // CalendarRSVPEvent
            31987, // RelayReviewEvent
            31990, // AppDefinitionEvent
            32176, // BlossomPieceIndexEvent
            32267, // SoftwareApplicationEvent
            33401, // ExerciseTemplateEvent
            33863, // FundraiserEvent
            34139, // MusicPlaylistEvent
            34235, // VideoHorizontalEvent
            34236, // VideoVerticalEvent
            34259, // EntityRatingEvent
            34550, // CommunityDefinitionEvent
            35128, // NamedSiteEvent
            35129, // NamedNappletEvent
            36787, // MusicTrackEvent
            38000, // MintRecommendationEvent
            38192, // Ps1SaveEvent
            38383, // P2POrderEvent
            39000, // GroupMetadataEvent
            39089, // FollowListEvent
            39092, // MediaStarterPackEvent
            39701, // WebBookmarkEvent
            40002, // StreamMessageV2Event
            40100, // CanvasEvent
            45001, // ForumPostEvent
            45003, // ForumCommentEvent
            48106, // HuddleGuidelinesEvent
        )
}
