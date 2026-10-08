# Searchable kinds — the authoritative implementor table

Every concrete `SearchableEvent` implementor in Quartz, with the exact `indexableContent()`
expression. **Update this file in the same PR as any change to the searchable set or to an
`indexableContent()` body** (see SKILL.md). Verified against the code 2026-10-08.

Counts: 197 concrete classes covering 198 kind values (`GitStatusEvent` spans 4 kinds;
kind 30063 is shared by two NIPs, kind 38000 by three classes and kind 38385 with other apps — see the footnotes). The kind
set is exactly `SearchableKinds.ALL` (`nip50Search/SearchableKinds.kt`). File paths are under
`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/`.

Separator legend: **NL** = `joinToString("\n")`, **SP** = `joinToString(" ")`.

| Kind | Class | Package | `indexableContent()` |
|---|---|---|---|
| 0 | MetadataEvent | nip01Core/metadata | `contactMetaData()?.let { listOfNotNull(it.name, it.displayName, it.about, it.nip05, it.lud06, it.lud16, it.website, it.picture, it.banner).joinToString(" ") } ?: ""` (SP) |
| 1 | TextNoteEvent | nip10Notes | `listOfNotNull(subject(), content)` NL |
| 5 | DeletionRequestEvent | nip09Deletions | `content` (the deletion reason) |
| 9 | ChatEvent | nipC7Chats | `content` |
| 11 | ThreadEvent | nip7DThreads | `listOfNotNull(title(), content)` NL |
| 14 | ChatMessageEvent | nip17Dm/messages | `listOfNotNull(subject(), content)` NL |
| 15 | ChatMessageEncryptedFileHeaderEvent | nip17Dm/files | `summary().orEmpty()` — `content` is the encrypted file's URL, not text |
| 20 | PictureEvent | nip68Picture | `(listOfNotNull(title(), content) + location() + imageDescriptions())` NL — `location` tag values, then each imeta's non-blank `alt` |
| 21 | VideoNormalEvent | nip71Video | inherited `RegularVideoEvent`: `(listOfNotNull(title(), content) + segmentTitles())` NL |
| 22 | VideoShortEvent | nip71Video | inherited `RegularVideoEvent`: `(listOfNotNull(title(), content) + segmentTitles())` NL |
| 24 | PublicMessageEvent | nipA4PublicMessages | `content` |
| 31 | ExternalCitationEvent | experimental/citations | inherited `CitationEvent`: `listOfNotNull(title(), summary(), content, author(), publishedBy())` NL |
| 32 | HardcopyCitationEvent | experimental/citations | `listOfNotNull(title(), chapterTitle(), summary(), content, author(), editor(), publishedIn(), publishedBy())` NL |
| 33 | PromptCitationEvent | experimental/citations | `listOfNotNull(title(), summary(), content, author(), publishedBy(), llm())` NL |
| 40 | ChannelCreateEvent | nip28PublicChat/admin | `channelInfo().let { listOfNotNull(it.name, it.about, it.picture).joinToString(" ") }` (SP) |
| 41 | ChannelMetadataEvent | nip28PublicChat/admin | same as kind 40 (SP) |
| 42 | ChannelMessageEvent | nip28PublicChat/message | `content` |
| 43 | ChannelHideMessageEvent | nip28PublicChat/admin | `reason()` — the `reason` of NIP-28's `{"reason": …}` JSON content, or the plain-text content |
| 44 | ChannelMuteUserEvent | nip28PublicChat/admin | `reason()` — as kind 43 |
| 54 | PodcastEpisodeEvent | nipF4Podcasts/episode | `listOfNotNull(title(), description(), content)` NL |
| 62 | RequestToVanishEvent | nip62RequestToVanish | `content` |
| 64 | ChessGameEvent | nip64Chess/game | `searchText().all()` NL — the PGN's `Event`, `Site`, `White`, `Black`, `Annotator`, `Opening`, `Variation`, `WhiteTeam`, `BlackTeam` tag-pair values, then its `{…}` comments; never the movetext |
| 818 | WikiMergeRequestEvent | nip54Wiki | `content` |
| 1010 | TextNoteModificationEvent | experimental/edits | `listOfNotNull(content, summary())` NL (content first) |
| 1022 | BidConfirmationEvent | nip15Marketplace/bidConfirmation | `confirmationData()?.message.orEmpty()` — the `message` field of the JSON content |
| 1063 | FileMetadataEvent | nip94FileMetadata | `listOfNotNull(summary(), content)` NL |
| 1065 | FileStorageHeaderEvent | experimental/nip95/header | `listOfNotNull(summary(), content)` NL — the caption; the base64 payload lives in the separate 1064 and is never indexed |
| 1068 | PollEvent | nip88Polls/poll | `buildString { append(content); options().forEach { append('\n').append(it.label) } }` |
| 1111 | CommentEvent | nip22Comments | `(listOf(content) + tags.hashtags())` NL |
| 1163 | ProfileGalleryEntryEvent | experimental/profileGallery | `listOfNotNull(summary())` NL |
| 1301 | WorkoutRecordEvent | experimental/fitness/workout | `listOfNotNull(title(), content)` NL |
| 1311 | LiveActivitiesChatMessageEvent | nip53LiveActivities/chat | `(listOf(content) + tags.hashtags())` NL |
| 1312 | LiveActivitiesRaidEvent | nip53LiveActivities/raid | `content` |
| 1313 | LiveActivitiesClipEvent | nip53LiveActivities/clip | `listOfNotNull(title(), content)` NL |
| 1315 | RoadEventReportEvent | experimental/roadstr/report | `content` |
| 1337 | CodeSnippetEvent | nipC0CodeSnippets | `listOfNotNull(snippetName(), snippetDescription(), content)` NL |
| 1617 | GitPatchEvent | nip34Git/patch | `content` |
| 1618 | GitPullRequestEvent | nip34Git/pr | `(listOfNotNull(subject(), content) + labels())` NL |
| 1621 | GitIssueEvent | nip34Git/issue | `(listOfNotNull(subject(), content) + topics())` NL |
| 1622 | GitReplyEvent | nip34Git/reply | `content` |
| 1624 | GitCoverNoteEvent | nip34Git/coverNote | `content` (markdown cover note) |
| 1630–1633 | GitStatusEvent | nip34Git/status | inherited `GitStatusEvent`: `content` (open/applied/closed/draft) |
| 1808 | AudioHeaderEvent | experimental/audio/header | `content` |
| 1985 | LabelEvent | nip32Labeling | `(listOf(content) + labels().map { it.label }).filter { it.isNotEmpty() }` NL |
| 2003 | TorrentEvent | nip35Torrents | `(listOfNotNull(title(), content) + fileNames())` NL — the `file` tags' paths |
| 2004 | TorrentCommentEvent | nip35Torrents | `content` |
| 2473 | BirdDetectionEvent | experimental/birdstar | `listOfNotNull(summary(), speciesName())` NL |
| 3302 | ConcordChatEditEvent | concord/cord03Channels | `content` |
| 5050 | DvmTextGenerationRequestEvent | nip90Dvms/textGeneration | `inputs().filter { it.type == "prompt" \|\| it.type == "text" }.joinToString(" ") { it.value }` (SP) |
| 5100 | DvmImageGenerationRequestEvent | nip90Dvms/imageGeneration | `listOfNotNull(prompt(), negativePrompt()).joinToString(" ")` (SP) |
| 5128 | SiteSnapshotEvent | nip5aStaticWebsites | `listOfNotNull(title(), description())` NL |
| 5129 | NappletSnapshotEvent | nip5dNapplets | `listOfNotNull(title(), description())` NL |
| 5250 | DvmTextToSpeechRequestEvent | nip90Dvms/textToSpeech | `text() ?: ""` |
| 5302 | DvmContentSearchRequestEvent | nip90Dvms/contentSearch | `searchQuery() ?: ""` |
| 5303 | DvmPeopleSearchRequestEvent | nip90Dvms/peopleSearch | `searchQuery() ?: ""` |
| 5901 | DvmOpReturnRequestEvent | nip90Dvms/opReturn | `text() ?: ""` |
| 6969 | ZapPollEvent | experimental/zapPolls | `buildString { append(content); pollOptionsArray().forEach { append('\n').append(it.descriptor) } }` |
| 7516 | GeocacheFoundLogEvent | nipCCGeocaching/foundLog | `content` |
| 8002 | ArchivedIdentityEvent | buzz/iaIdentityArchival | `content` |
| 8003 | UnarchivedIdentityEvent | buzz/iaIdentityArchival | `content` |
| 8333 | OnchainZapEvent | nipBCOnchainZaps/zap | `content` |
| 9002 | GroupEditMetadataEvent | nip29RelayGroups/moderation | `(listOfNotNull(name(), about()) + hashtags())` NL |
| 9007 | CreateGroupEvent | nip29RelayGroups/moderation | `listOfNotNull(name(), about())` NL |
| 9021 | GroupJoinRequestEvent | nip29RelayGroups/request | `content` |
| 9022 | GroupLeaveRequestEvent | nip29RelayGroups/request | `content` |
| 9035 | ArchiveRequestEvent | buzz/iaIdentityArchival | `content` |
| 9036 | UnarchiveRequestEvent | buzz/iaIdentityArchival | `content` |
| 9041 | ZapGoalEvent | nip75ZapGoals | `listOfNotNull(summary(), content)` NL |
| 9321 | NutzapEvent | nip61Nutzaps/nutzap | `content` |
| 9734 | ZapRequestEvent | nip57Zaps | `content` |
| 9735 | ZapReceiptEvent | nip57Zaps | `zapRequest?.content.orEmpty()` — indexes the **embedded 9734's** content |
| 9736 | Bolt12ZapEvent | nipB1Bolt12Zaps/zap | `content` |
| 9737 | Bolt12ZapIntentEvent | nipB1Bolt12Zaps/intent | `content` |
| 9802 | HighlightEvent | nip84Highlights | `listOfNotNull(comment(), searchableContext(), content)` NL — `searchableContext()` is the `context` tag, else a `textquoteselector`'s prefix and suffix (whitespace-collapsed, space-joined) |
| 9998 | ListHeaderEvent | experimental/decentralizedLists/header | `tags.searchableListContent()` NL — `names` (singular, plural), `titles` (singular, plural), `name`, `title`, `description`, `comments`, then in tag order every `t` value that is not `isMachineValue` and every value of every other tag (except `alt`, `client`, `imeta`) that passes `isNaturalLanguageValue` (has whitespace or non-ASCII, or is one capitalized letters-only word; never JSON, numbers, URIs of any scheme, addresses, hex ids, UUIDs, bech32) |
| 9999 | ListItemEvent | experimental/decentralizedLists/item | same as 9998 |
| 10003 | BookmarkListEvent | nip51Lists/bookmarkList | `listOfNotNull(titleOrName())` NL |
| 10100 | AgentProfileEvent | buzz/agentProfiles | `profileOrNull()?.let { listOfNotNull(it.name, it.displayName).joinToString("\n") } ?: ""` |
| 10154 | PodcastMetadataEvent | nipF4Podcasts/metadata | `listOfNotNull(title(), description())` NL |
| 11316 | CvmServerAnnouncementEvent | contextvm/cep06Announcements | `listOfNotNull(serverName(), about())` NL (class lives in `CvmAnnouncementEvents.kt`) |
| 11333 | SnoAvatarEvent | cyberspace/deck0003Sno | `nameTag().orEmpty()` — the `name` tag only; `content` is SNO geometry JSON |
| 11871 | AttestorProficiencyEvent | experimental/attestations/proficiency | `listOfNotNull(description())` NL |
| 12473 | BirdexEvent | experimental/birdstar | `(listOfNotNull(summary()) + speciesNames())` NL |
| 15128 | RootSiteEvent | nip5aStaticWebsites | `listOfNotNull(title(), description())` NL |
| 15129 | RootNappletEvent | nip5dNapplets | `listOfNotNull(title(), description())` NL |
| 30000 | FollowSetEvent | nip51Lists/followSet | `listOfNotNull(titleOrName(), description())` NL |
| 30001 | OldBookmarkListEvent | nip51Lists/bookmarkList | `listOfNotNull(title())` NL |
| 30002 | RelaySetEvent | nip51Lists/relaySets | `listOfNotNull(title(), description())` NL |
| 30003 | BookmarkSetEvent | nip51Lists/bookmarkSet | `listOfNotNull(titleOrName(), description())` NL |
| 30004 | ArticleCurationSetEvent | nip51Lists/articleCurationSet | `listOfNotNull(title(), description())` NL |
| 30005 | VideoCurationSetEvent | nip51Lists/videoCurationSet | `listOfNotNull(title(), description())` NL |
| 30006 | PictureCurationSetEvent | nip51Lists/pictureCurationSet | `listOfNotNull(title(), description())` NL |
| 30008 | AcceptedBadgeSetEvent | nip58Badges/accepted | `listOfNotNull(title(), description())` NL |
| 30009 | BadgeDefinitionEvent | nip58Badges/definition | `listOfNotNull(name(), description(), content)` NL |
| 30015 | InterestSetEvent | nip51Lists/interestSet | `(listOfNotNull(title(), description()) + publicHashtags())` NL |
| 30017 | StallEvent | nip15Marketplace/stall | `stallData()?.let { listOfNotNull(it.name, it.description).joinToString("\n") } ?: ""` |
| 30018 | ProductEvent | nip15Marketplace/product | `productData()?.let { (listOfNotNull(it.name, it.description) + categories()).joinToString("\n") } ?: ""` |
| 30019 | MarketplaceEvent | nip15Marketplace/marketplace | `marketplaceData()?.let { listOfNotNull(it.name, it.about).joinToString("\n") } ?: ""` |
| 30020 | AuctionEvent | nip15Marketplace/auction | `auctionData()?.let { (listOfNotNull(it.name, it.description) + tags.hashtags()).joinToString("\n") } ?: ""` |
| 30023 | LongFormContentEvent | nip23LongContent | `(listOfNotNull(title(), summary(), content) + topics())` NL |
| 30024 | LongFormDraftEvent | nip23LongContent/draft | `tags.longFormIndexableContent(content)` — the same as 30023: `(listOfNotNull(title(), summary(), content) + topics())` NL |
| 30030 | EmojiPackEvent | nip30CustomEmoji/pack | `(listOfNotNull(titleOrName(), description()) + publicEmojiCodes())` NL — the public `emoji` tags' shortcodes; `content` is NOT indexed (it is the NIP-44 ciphertext of the private emoji tags) |
| 30031 | StickerPackEvent | nip30CustomEmoji/stickers | `(listOfNotNull(title(), description()) + stickers().map { it.code })` NL (never `content`) |
| 30040 | PublicationIndexEvent | experimental/publications | `listOfNotNull(title(), author(), summary())` NL |
| 30041 | PublicationContentEvent | experimental/publications | `listOfNotNull(title(), content)` NL |
| 30045 | BookshelfDirectoryEvent | experimental/library | `listOfNotNull(title(), summary(), content)` NL |
| 30054 | Podcasting20EpisodeEvent | nipXXPodcasting20/episode | `(listOfNotNull(title(), description(), content) + personNames() + soundbiteTitles() + topics())` NL |
| 30055 | Podcasting20TrailerEvent | nipXXPodcasting20/trailer | `listOfNotNull(title(), content)` NL |
| 30063 | ReleaseArtifactSetEvent † | nip51Lists/releaseArtifactSet | `listOfNotNull(title(), description(), searchableReleaseNotes())` NL |
| 30066 | LiveChessMoveEvent | nip64Chess/move | `content` |
| 30068 | LiveChessDrawOfferEvent | nip64Chess/draw | `content` |
| 30142 | LearningResourceEvent | experimental/library | `(listOfNotNull(title(), alternateTitle(), summary(), alternateSummary(), content, author()) + facetLabels())` NL — `facetLabels()` = resource types, educational levels, subjects, deduplicated |
| 30175 | PersonaEvent | buzz/apPersonas | `personaOrNull()?.let { listOfNotNull(it.displayName, it.description, it.systemPrompt).joinToString("\n") } ?: ""` |
| 30176 | TeamEvent | buzz/teams | `teamOrNull()?.let { listOfNotNull(it.name, it.description, it.instructions).joinToString("\n") } ?: ""` |
| 30177 | ManagedAgentEvent | buzz/managedAgents | `agentOrNull()?.let { listOfNotNull(it.name, it.systemPrompt).joinToString("\n") } ?: ""` |
| 30178 | TeamCatalogEvent | buzz/teamCatalog | `catalogOrNull()?.let { catalog -> buildList { add(catalog.name); catalog.description?.let(::add); catalog.instructions?.let(::add); catalog.members.forEach { member -> add(member.displayName); member.systemPrompt?.let(::add) } }.joinToString("\n") } ?: ""` |
| 30267 | AppCurationSetEvent | nip51Lists/appCurationSet | `listOfNotNull(titleOrName(), description())` NL |
| 30296 | InteractiveStoryPrologueEvent | experimental/interactiveStories | inherited base: `(listOfNotNull(title(), summary(), content) + optionLabels())` NL |
| 30297 | InteractiveStorySceneEvent | experimental/interactiveStories | inherited base: `(listOfNotNull(title(), summary(), content) + optionLabels())` NL |
| 30311 | LiveActivitiesEvent | nip53LiveActivities/streaming | `listOfNotNull(title(), summary(), content)` NL |
| 30312 | MeetingSpaceEvent | nip53LiveActivities/meetingSpaces | `listOfNotNull(room(), summary(), content)` NL |
| 30313 | MeetingRoomEvent | nip53LiveActivities/meetingSpaces | `listOfNotNull(title(), summary())` NL |
| 30315 | UserStatusEvent | nip38UserStatus | `content` |
| 30382 | UserAssertionEvent | nip85TrustedAssertions/users | `(listOfNotNull(petName(), summary()) + topics())` NL — public tags only, never the NIP-44 content |
| 30392 | UserTrustedListEvent | experimental/trustedLists/users | inherited `TrustedListEvent`: `title() ?: ""` — the label only; `metric`/`d` are machine ids and `content` is a JSON echo of the membership |
| 30393 | EventTrustedListEvent | experimental/trustedLists/events | inherited `TrustedListEvent`: `title() ?: ""` |
| 30394 | AddressableTrustedListEvent | experimental/trustedLists/addressables | inherited `TrustedListEvent`: `title() ?: ""` |
| 30395 | ExternalIdTrustedListEvent | experimental/trustedLists/externalIds | inherited `TrustedListEvent`: `title() ?: ""` |
| 30402 | ClassifiedsEvent | nip99Classifieds | `(listOfNotNull(title(), summary(), content, location()) + categories())` NL |
| 30617 | GitRepositoryEvent | nip34Git/repository | `(listOfNotNull(name(), description(), content) + hashtags())` NL |
| 30620 | WorkflowDefEvent | buzz/workflow | `listOfNotNull(name(), content)` NL |
| 30621 | ProjectEvent | buzz/mpProjects | `listOfNotNull(displayName(), description())` NL |
| 30817 | NipTextEvent | experimental/nipsOnNostr | `listOfNotNull(title(), content)` NL |
| 30818 | WikiArticleEvent | nip54Wiki | `listOfNotNull(title(), summary(), content)` NL |
| 31337 | AudioTrackEvent | experimental/audio/track | `listOfNotNull(subject())` NL |
| 31871 | AttestationEvent | experimental/attestations/attestation | `content` |
| 31872 | AttestationRequestEvent | experimental/attestations/request | `content` |
| 31873 | AttestorRecommendationEvent | experimental/attestations/recommendation | `listOfNotNull(description())` NL |
| 31890 | FeedDefinitionEvent | feedDefinition | `title().orEmpty()` (now registered in `EventFactory`; before that it parsed as a plain `Event` and was never searched) |
| 31922 | CalendarDateSlotEvent | nip52Calendar/appt/day | `(listOfNotNull(title(), summary(), content) + locations() + hashtags())` NL |
| 31923 | CalendarTimeSlotEvent | nip52Calendar/appt/time | `(listOfNotNull(title(), summary(), content) + locations() + hashtags())` NL |
| 31924 | CalendarCollectionEvent | nip52Calendar/calendar | `listOfNotNull(title(), content)` NL |
| 31925 | CalendarRSVPEvent | nip52Calendar/rsvp | `content` |
| 31987 | RelayReviewEvent | experimental/ratings | `content` |
| 31990 | AppDefinitionEvent | nip89AppHandlers/definition | `appMetaData()?.let { listOfNotNull(it.name, it.username, it.displayName, it.about, it.nip05, it.lud06, it.lud16, it.website, it.picture, it.banner, it.image).joinToString(" ") } ?: ""` (SP) |
| 32176 | BlossomPieceIndexEvent | experimental/library | `listOfNotNull(title(), summary(), content)` NL |
| 32267 | SoftwareApplicationEvent | experimental/nip82SoftwareApps/application | `listOfNotNull(name(), summary(), content)` NL |
| 33331 | SnoObjectEvent | cyberspace/deck0003Sno | `nameTag().orEmpty()` — the `name` tag only; `content` is SNO geometry JSON |
| 33401 | ExerciseTemplateEvent | experimental/fitness/workout | `listOfNotNull(title(), content)` NL |
| 33402 | WorkoutTemplateEvent | experimental/fitness/workout | `listOfNotNull(title(), content)` NL |
| 33534 | RelayRoleEvent | nip43RelayMembers/roles | `listOfNotNull(label(), description())` NL |
| 33863 | FundraiserEvent | experimental/agora | `listOfNotNull(title(), content)` NL |
| 34139 | MusicPlaylistEvent | experimental/music/playlist | `listOfNotNull(title(), description(), content)` NL |
| 34235 | AddressableNormalVideoEvent | nip71Video | inherited `AddressableVideoEvent`: `(listOfNotNull(title(), content) + segmentTitles())` NL |
| 34236 | AddressableShortVideoEvent | nip71Video | inherited `AddressableVideoEvent`: `(listOfNotNull(title(), content) + segmentTitles())` NL |
| 34259 | EntityRatingEvent | experimental/ratings | `content` |
| 34550 | CommunityDefinitionEvent | nip72ModCommunities/definition | `listOfNotNull(name(), description(), rules(), content)` NL |
| 35128 | NamedSiteEvent | nip5aStaticWebsites | `listOfNotNull(title(), description())` NL |
| 35129 | NamedNappletEvent | nip5dNapplets | `listOfNotNull(title(), description())` NL |
| 36787 | MusicTrackEvent | experimental/music/track | `listOfNotNull(title(), artist(), album(), content)` NL |
| 37516 | GeocacheListingEvent | nipCCGeocaching/listing | `listOfNotNull(cacheName(), content)` NL (the `hint` is deliberately not indexed — matching a hint is spoiling it) |
| 37517 | GeocacheCurationListEvent | nipCCGeocaching/curation | `listOfNotNull(title(), description(), content)` NL |
| 38000 | MintRecommendationEvent ‡ | nip87Ecash/recommendation | `content` |
| 38000 | BallotEvent ‡ | experimental/ballots | `buildList { election()?.let { add(it) }; answers().forEach { add(it.answer) } }` NL |
| 38000 | PredictionMarketEvent ‡ | experimental/predictionMarkets | `(listOfNotNull(title(), description()) + outcomes() + listOfNotNull(resolution(), cancelReason(), socialPost()))` NL |
| 38192 | Ps1SaveEvent | experimental/ps1saves | `listOfNotNull(summary(), saveTitle(), region(), filename())` NL |
| 38383 | P2POrderEvent | nip69P2pOrderEvents | `(listOfNotNull(makerName(), currency()) + paymentMethods().orEmpty()).joinToString(" ")` (SP) |
| 38385 | MostroInfoEvent § | nip69P2pOrderEvents/mostroInfo | `(listOfNotNull(instanceName()) + fiatCurrenciesAccepted().orEmpty()).joinToString(" ")` (SP) |
| 39000 | GroupMetadataEvent | nip29RelayGroups/metadata | `(listOfNotNull(name(), about()) + hashtags())` NL |
| 39003 | GroupRolesEvent | nip29RelayGroups/metadata | `roles().flatMap { listOfNotNull(it.name, it.description) }` NL |
| 39089 | StarterPackEvent | nip51Lists/starterPack | `(listOfNotNull(title(), description()) + hashtags())` NL |
| 39092 | MediaStarterPackEvent | nip51Lists/mediaStarterPack | `listOfNotNull(title(), description())` NL |
| 39307 | TextTrackEvent | nip71Video/textTrack | `if (WebVttText.hasCues(content)) WebVttText.cueText(content) else content` — `cueText` is each cue's payload lines (NL) with tags removed and character references decoded; no header, timings, cue settings, ids, `NOTE`/`STYLE`/`REGION` blocks. Content with no `-->` timing line is not a caption file and is indexed as written |
| 39701 | WebBookmarkEvent | nipB0WebBookmarks | `(listOfNotNull(title(), description()) + hashtags())` NL |
| 39998 | AddressableListHeaderEvent | experimental/decentralizedLists/header | same as 9998 |
| 39999 | AddressableListItemEvent | experimental/decentralizedLists/item | same as 9998 |
| 40002 | StreamMessageV2Event | buzz/stream | `content` |
| 40003 | StreamMessageEditEvent | buzz/stream | `content` |
| 40006 | StreamMessageScheduledEvent | buzz/stream | `content` |
| 40007 | StreamReminderEvent | buzz/stream | `content` |
| 40008 | StreamMessageDiffEvent | buzz/stream | `description() ?: ""` — the `description` tag; the body is a unified diff and stays out |
| 40099 | SystemMessageEvent | buzz/stream | `payload()?.let { listOfNotNull(it.topic, it.purpose, it.publicReason).joinToString("\n") } ?: ""` — fields of the JSON content |
| 40100 | CanvasEvent | buzz/stream | `content` |
| 40901 | ChannelSummaryEvent | buzz/stream/sidecars | `summary()?.let { listOfNotNull(it.name, it.about, it.topic, it.purpose).joinToString("\n") } ?: ""` — fields of the JSON content |
| 42000 | ProductFeedbackEvent | buzz/moderation | `content` |
| 43001 | JobRequestEvent | buzz/jobs | `content` |
| 43002 | JobAcceptedEvent | buzz/jobs | `content` |
| 43003 | JobProgressEvent | buzz/jobs | `content` |
| 43004 | JobResultEvent | buzz/jobs | `content` |
| 43005 | JobCancelEvent | buzz/jobs | `content` |
| 43006 | JobErrorEvent | buzz/jobs | `content` |
| 45001 | ForumPostEvent | buzz/forum | `content` |
| 45003 | ForumCommentEvent | buzz/forum | `content` |
| 45010 | ArtifactEvent | buzz/arArtifacts | `listOfNotNull(title(), textBody())` NL — `textBody()` is `content` unless blank or JSON (starts with `{`/`[`) |
| 46030 | ApprovalGrantEvent | buzz/workflow | `content` |
| 46031 | ApprovalDenyEvent | buzz/workflow | `content` |
| 48106 | HuddleGuidelinesEvent | buzz/huddles | `content` |

† **Kind 30063 is shared** by NIP-51 release artifact sets and NIP-82 software releases, and
`ReleaseArtifactSetEvent` parses both. It indexes `title()` and `description()`, plus `content`
(the release notes, via `searchableReleaseNotes()`) only when it is non-blank and the event
carries the NIP-82 `i` + `version` tags — a NIP-51 set may hold encrypted private items in
`content`, which must never be indexed.

‡ **Kind 38000 is shared** by NIP-87 mint recommendations and two app formats, and `EventFactory`
picks the class by tags: `MintRecommendationEvent` when any `k` is 38172/38173 (or, with no
`k`, a non-blank `u` or an `a` to a 38172/38173 address); else `BallotEvent` on a non-blank
`election`; else `PredictionMarketEvent` on a `market`, ≥2 `outcome`s, or `type` + `end`; else
`UnrecognizedKind38000Event` — addressable (stores still key it by `d`) but unsearchable (the
spam votes that make up most of the kind). Kind-level probes (`EventFactory.probe`) answer as
`MintRecommendationEvent`. A market's `title()`/`description()` come from its `data` tag JSON,
the `title` tag, or JSON `content`, in that order; it also indexes its outcome labels, the
resolution, the cancel reason, and `content` as `socialPost()` when it is a non-blank, non-JSON post
and the market has no `description()` it would merely repeat.

§ **Kind 38385 is shared** by Mostro instance info and other apps (Paygress lease revocations,
bond-trade assignments, game scores). `EventFactory` builds `MostroInfoEvent` only when a `z` of
`info` or a `mostro_version` tag is present, else `UnrecognizedKind38385Event` — addressable but
unsearchable. Kind-level probes answer as `MostroInfoEvent`. Mostro's 38384 / 38386 and RoboSats'
31986 are split the same way, but none of their classes is searchable.

## Abstract bases (no kind of their own)

| Base class | Body | Concrete kinds |
|---|---|---|
| `CitationEvent` | `listOfNotNull(title(), summary(), content, author(), publishedBy())` NL | 31 (32 and 33 override with their extra fields) |
| `GitStatusEvent` | `content` | 1630, 1631, 1632, 1633 |
| `InteractiveStoryBaseEvent` | `(listOfNotNull(title(), summary(), content) + optionLabels())` NL — the non-blank `option` choice texts | 30296, 30297 |
| `AddressableVideoEvent` | `(listOfNotNull(title(), content) + segmentTitles())` NL — the `segment` tags' titles | 34235, 34236 |
| `RegularVideoEvent` | `(listOfNotNull(title(), content) + segmentTitles())` NL | 21, 22 |
| `TrustedListEvent` | `title() ?: ""` | 30392, 30393, 30394, 30395 |

## How to regenerate / verify this table

```bash
# The authoritative kind set (sorted, one comment per kind naming the class):
cat quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip50Search/SearchableKinds.kt
# All implementor files:
grep -rln "override fun indexableContent" quartz/src/commonMain
# For each, pair the KIND constant with the indexableContent() body.
# Searchability on the store path additionally requires EventFactory registration:
grep -n "<ClassName>" quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt
```

Two tests keep the code side from drifting: `SearchableKindsTest` (commonTest) builds every kind
0–65535 through `EventFactory` and asserts the searchable ones are exactly `SearchableKinds.ALL`,
and `IndexableContentGoldenTest` (jvmTest) records each kind's `indexableContent()` for a
tag-rich sample event in `quartz/src/jvmTest/resources/indexable-content.golden` — a CI-diffable
snapshot. Diff `SearchableKinds.kt` and the golden file at a version bump; this table is still
updated by hand.
