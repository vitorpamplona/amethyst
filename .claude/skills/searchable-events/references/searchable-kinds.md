# Searchable kinds — the authoritative implementor table

Every concrete `SearchableEvent` implementor in Quartz, with the exact `indexableContent()`
expression. **Update this file in the same PR as any change to the searchable set or to an
`indexableContent()` body** (see SKILL.md). Verified against the code 2026-10-04.

Counts: 139 concrete classes covering 140 kind values (`GitStatusEvent` spans 4 kinds;
kind 30063 is shared by two NIPs and kind 38000 by three classes — see the footnotes). File paths are under
`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/`.

Separator legend: **NL** = `joinToString("\n")`, **SP** = `joinToString(" ")`.

| Kind | Class | Package | `indexableContent()` |
|---|---|---|---|
| 0 | MetadataEvent | nip01Core/metadata | `contactMetaData()?.let { listOfNotNull(it.name, it.displayName, it.about, it.nip05, it.lud06, it.lud16, it.website, it.picture, it.banner).joinToString(" ") } ?: ""` (SP) |
| 1 | TextNoteEvent | nip10Notes | `listOfNotNull(subject(), content)` NL |
| 9 | ChatEvent | nipC7Chats | `content` |
| 11 | ThreadEvent | nip7DThreads | `listOfNotNull(title(), content)` NL |
| 14 | ChatMessageEvent | nip17Dm/messages | `content` |
| 20 | PictureEvent | nip68Picture | `listOfNotNull(title(), content)` NL |
| 21 | VideoNormalEvent | nip71Video | inherited `RegularVideoEvent`: `listOfNotNull(title(), content)` NL |
| 22 | VideoShortEvent | nip71Video | inherited `RegularVideoEvent`: `listOfNotNull(title(), content)` NL |
| 24 | PublicMessageEvent | nipA4PublicMessages | `content` |
| 40 | ChannelCreateEvent | nip28PublicChat/admin | `channelInfo().let { listOfNotNull(it.name, it.about, it.picture).joinToString(" ") }` (SP) |
| 41 | ChannelMetadataEvent | nip28PublicChat/admin | same as kind 40 (SP) |
| 42 | ChannelMessageEvent | nip28PublicChat/message | `content` |
| 54 | PodcastEpisodeEvent | nipF4Podcasts/episode | `listOfNotNull(title(), description(), content)` NL |
| 1010 | TextNoteModificationEvent | experimental/edits | `listOfNotNull(content, summary())` NL (content first) |
| 1063 | FileMetadataEvent | nip94FileMetadata | `listOfNotNull(summary(), content)` NL |
| 1065 | FileStorageHeaderEvent | experimental/nip95/header | `listOfNotNull(summary())` NL |
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
| 1618 | GitPullRequestEvent | nip34Git/pr | `listOfNotNull(subject(), content)` NL |
| 1621 | GitIssueEvent | nip34Git/issue | `listOfNotNull(subject(), content)` NL |
| 1622 | GitReplyEvent | nip34Git/reply | `content` |
| 1630–1633 | GitStatusEvent | nip34Git/status | `content` (open/applied/closed/draft) |
| 1808 | AudioHeaderEvent | experimental/audio/header | `content` |
| 1985 | LabelEvent | nip32Labeling | `(listOf(content) + labels().map { it.label }).filter { it.isNotEmpty() }` NL |
| 2003 | TorrentEvent | nip35Torrents | `listOfNotNull(title(), content)` NL |
| 2004 | TorrentCommentEvent | nip35Torrents | `content` |
| 2473 | BirdDetectionEvent | experimental/birdstar | `listOfNotNull(summary(), speciesName())` NL |
| 3302 | ConcordChatEditEvent | concord/cord03Channels | `content` |
| 5050 | DvmTextGenerationRequestEvent | nip90Dvms/textGeneration | `inputs().filter { it.type == "prompt" \|\| it.type == "text" }.joinToString(" ") { it.value }` (SP) |
| 5100 | DvmImageGenerationRequestEvent | nip90Dvms/imageGeneration | `listOfNotNull(prompt(), negativePrompt()).joinToString(" ")` (SP) |
| 5129 | NappletSnapshotEvent | nip5dNapplets | `listOfNotNull(title(), description())` NL |
| 5250 | DvmTextToSpeechRequestEvent | nip90Dvms/textToSpeech | `text() ?: ""` |
| 5302 | DvmContentSearchRequestEvent | nip90Dvms/contentSearch | `searchQuery() ?: ""` |
| 5303 | DvmPeopleSearchRequestEvent | nip90Dvms/peopleSearch | `searchQuery() ?: ""` |
| 6969 | ZapPollEvent | experimental/zapPolls | `buildString { append(content); pollOptionsArray().forEach { append('\n').append(it.descriptor) } }` |
| 7516 | GeocacheFoundLogEvent | nipCCGeocaching/foundLog | `content` |
| 8333 | OnchainZapEvent | nipBCOnchainZaps/zap | `content` |
| 9002 | GroupEditMetadataEvent | nip29RelayGroups/moderation | `(listOfNotNull(name(), about()) + hashtags())` NL |
| 9041 | ZapGoalEvent | nip75ZapGoals | `listOfNotNull(summary(), content)` NL |
| 9321 | NutzapEvent | nip61Nutzaps/nutzap | `content` |
| 9734 | ZapRequestEvent | nip57Zaps | `content` |
| 9735 | ZapReceiptEvent | nip57Zaps | `zapRequest?.content.orEmpty()` — indexes the **embedded 9734's** content |
| 9736 | Bolt12ZapEvent | nipB1Bolt12Zaps/zap | `content` |
| 9737 | Bolt12ZapIntentEvent | nipB1Bolt12Zaps/intent | `content` |
| 9802 | HighlightEvent | nip84Highlights | `listOfNotNull(comment(), context(), content)` NL |
| 9998 | ListHeaderEvent | experimental/decentralizedLists/header | `tags.searchableListContent()` NL — `names` (singular, plural), `titles` (singular, plural), `name`, `title`, `description`, `comments`, then in tag order every `t` value that is not `isMachineValue` and every value of every other tag (except `alt`, `client`, `imeta`) that passes `isNaturalLanguageValue` (has whitespace or non-ASCII, or is one capitalized letters-only word; never JSON, numbers, URIs of any scheme, addresses, hex ids, UUIDs, bech32) |
| 9999 | ListItemEvent | experimental/decentralizedLists/item | same as 9998 |
| 10003 | BookmarkListEvent | nip51Lists/bookmarkList | `listOfNotNull(title())` NL |
| 10100 | AgentProfileEvent | buzz/agentProfiles | `profileOrNull()?.let { listOfNotNull(it.name, it.displayName).joinToString("\n") } ?: ""` |
| 10154 | PodcastMetadataEvent | nipF4Podcasts/metadata | `listOfNotNull(title(), description())` NL |
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
| 30009 | BadgeDefinitionEvent | nip58Badges/definition | `listOfNotNull(name(), description(), content)` NL |
| 30015 | InterestSetEvent | nip51Lists/interestSet | `(listOfNotNull(title(), description()) + publicHashtags())` NL |
| 30017 | StallEvent | nip15Marketplace/stall | `stallData()?.let { listOfNotNull(it.name, it.description).joinToString("\n") } ?: ""` |
| 30018 | ProductEvent | nip15Marketplace/product | `productData()?.let { (listOfNotNull(it.name, it.description) + categories()).joinToString("\n") } ?: ""` |
| 30019 | MarketplaceEvent | nip15Marketplace/marketplace | `marketplaceData()?.let { listOfNotNull(it.name, it.about).joinToString("\n") } ?: ""` |
| 30020 | AuctionEvent | nip15Marketplace/auction | `auctionData()?.let { (listOfNotNull(it.name, it.description) + tags.hashtags()).joinToString("\n") } ?: ""` |
| 30023 | LongFormContentEvent | nip23LongContent | `listOfNotNull(title(), summary(), content)` NL |
| 30030 | EmojiPackEvent | nip30CustomEmoji/pack | `listOfNotNull(titleOrName(), description(), content)` NL |
| 30054 | Podcasting20EpisodeEvent | nipXXPodcasting20/episode | `(listOfNotNull(title(), description(), content) + topics())` NL |
| 30055 | Podcasting20TrailerEvent | nipXXPodcasting20/trailer | `listOfNotNull(title(), content)` NL |
| 30063 | ReleaseArtifactSetEvent † | nip51Lists/releaseArtifactSet | `listOfNotNull(title(), description(), content if NIP-82)` NL |
| 30175 | PersonaEvent | buzz/apPersonas | `personaOrNull()?.let { listOfNotNull(it.displayName, it.systemPrompt).joinToString("\n") } ?: ""` |
| 30176 | TeamEvent | buzz/teams | `teamOrNull()?.let { listOfNotNull(it.name, it.description, it.instructions).joinToString("\n") } ?: ""` |
| 30177 | ManagedAgentEvent | buzz/managedAgents | `agentOrNull()?.let { listOfNotNull(it.name, it.systemPrompt).joinToString("\n") } ?: ""` |
| 30267 | AppCurationSetEvent | nip51Lists/appCurationSet | `listOfNotNull(titleOrName(), description())` NL |
| 30296 | InteractiveStoryPrologueEvent | experimental/interactiveStories | inherited base: `listOfNotNull(title(), summary(), content)` NL |
| 30297 | InteractiveStorySceneEvent | experimental/interactiveStories | inherited base: `listOfNotNull(title(), summary(), content)` NL |
| 30311 | LiveActivitiesEvent | nip53LiveActivities/streaming | `listOfNotNull(title(), summary(), content)` NL |
| 30312 | MeetingSpaceEvent | nip53LiveActivities/meetingSpaces | `listOfNotNull(room(), summary(), content)` NL |
| 30313 | MeetingRoomEvent | nip53LiveActivities/meetingSpaces | `listOfNotNull(title(), summary())` NL |
| 30315 | UserStatusEvent | nip38UserStatus | `content` |
| 30382 | UserAssertionEvent | nip85TrustedAssertions/users | `(listOfNotNull(petName(), summary()) + topics())` NL — public tags only, never the NIP-44 content |
| 30392 | UserTrustedListEvent | experimental/trustedLists/users | inherited `TrustedListEvent`: `title() ?: ""` — the label only; `metric`/`d` are machine ids and `content` is a JSON echo of the membership |
| 30393 | EventTrustedListEvent | experimental/trustedLists/events | inherited `TrustedListEvent`: `title() ?: ""` |
| 30394 | AddressableTrustedListEvent | experimental/trustedLists/addressables | inherited `TrustedListEvent`: `title() ?: ""` |
| 30395 | ExternalIdTrustedListEvent | experimental/trustedLists/externalIds | inherited `TrustedListEvent`: `title() ?: ""` |
| 30402 | ClassifiedsEvent | nip99Classifieds | `listOfNotNull(title(), summary(), content)` NL |
| 30617 | GitRepositoryEvent | nip34Git/repository | `listOfNotNull(name(), description(), content)` NL |
| 30620 | WorkflowDefEvent | buzz/workflow | `listOfNotNull(name(), content)` NL |
| 30817 | NipTextEvent | experimental/nipsOnNostr | `listOfNotNull(title(), content)` NL |
| 30818 | WikiArticleEvent | nip54Wiki | `listOfNotNull(title(), summary(), content)` NL |
| 31337 | AudioTrackEvent | experimental/audio/track | `listOfNotNull(subject())` NL |
| 31871 | AttestationEvent | experimental/attestations/attestation | `content` |
| 31872 | AttestationRequestEvent | experimental/attestations/request | `content` |
| 31873 | AttestorRecommendationEvent | experimental/attestations/recommendation | `listOfNotNull(description())` NL |
| 31890 | FeedDefinitionEvent | feedDefinition | `title().orEmpty()` |
| 31922 | CalendarDateSlotEvent | nip52Calendar/appt/day | `listOfNotNull(title(), summary(), content)` NL |
| 31923 | CalendarTimeSlotEvent | nip52Calendar/appt/time | `listOfNotNull(title(), summary(), content)` NL |
| 31924 | CalendarCollectionEvent | nip52Calendar/calendar | `listOfNotNull(title(), content)` NL |
| 31925 | CalendarRSVPEvent | nip52Calendar/rsvp | `content` |
| 31990 | AppDefinitionEvent | nip89AppHandlers/definition | `appMetaData()?.let { listOfNotNull(it.name, it.username, it.displayName, it.about, it.nip05, it.lud06, it.lud16, it.website, it.picture, it.banner, it.image).joinToString(" ") } ?: ""` (SP) |
| 32267 | SoftwareApplicationEvent | experimental/nip82SoftwareApps/application | `listOfNotNull(name(), summary(), content)` NL |
| 33401 | ExerciseTemplateEvent | experimental/fitness/workout | `listOfNotNull(title(), content)` NL |
| 33863 | FundraiserEvent | experimental/agora | `listOfNotNull(title(), content)` NL |
| 34139 | MusicPlaylistEvent | experimental/music/playlist | `listOfNotNull(title(), description(), content)` NL |
| 34235 | AddressableNormalVideoEvent | nip71Video | inherited `AddressableVideoEvent`: `listOfNotNull(title(), content)` NL |
| 34236 | AddressableShortVideoEvent | nip71Video | inherited `AddressableVideoEvent`: `listOfNotNull(title(), content)` NL |
| 34550 | CommunityDefinitionEvent | nip72ModCommunities/definition | `listOfNotNull(name(), description(), rules(), content)` NL |
| 35128 | NamedSiteEvent | nip5aStaticWebsites | `listOfNotNull(title(), description())` NL |
| 35129 | NamedNappletEvent | nip5dNapplets | `listOfNotNull(title(), description())` NL |
| 36787 | MusicTrackEvent | experimental/music/track | `listOfNotNull(title(), artist(), album(), content)` NL |
| 37516 | GeocacheListingEvent | nipCCGeocaching/listing | `listOfNotNull(cacheName(), content)` NL (the `hint` is deliberately not indexed — matching a hint is spoiling it) |
| 37517 | GeocacheCurationListEvent | nipCCGeocaching/curation | `listOfNotNull(title(), description(), content)` NL |
| 38000 | MintRecommendationEvent ‡ | nip87Ecash/recommendation | `content` |
| 38000 | BallotEvent ‡ | experimental/ballots | `(listOfNotNull(election()) + answers().map { it.answer })` NL |
| 38000 | PredictionMarketEvent ‡ | experimental/predictionMarkets | `listOfNotNull(title(), description())` NL |
| 38192 | Ps1SaveEvent | experimental/ps1saves | `listOfNotNull(summary(), saveTitle(), region(), filename())` NL |
| 38383 | P2POrderEvent | nip69P2pOrderEvents | `(listOfNotNull(makerName(), currency()) + paymentMethods().orEmpty()).joinToString(" ")` (SP) |
| 39000 | GroupMetadataEvent | nip29RelayGroups/metadata | `listOfNotNull(name(), about())` NL |
| 39089 | StarterPackEvent | nip51Lists/starterPack | `listOfNotNull(title(), description())` NL |
| 39092 | MediaStarterPackEvent | nip51Lists/mediaStarterPack | `listOfNotNull(title(), description())` NL |
| 39307 | TextTrackEvent | nip71Video/textTrack | `WebVttText.cueText(content)` NL — each cue's payload lines with tags removed and character references decoded; no header, timings, cue settings, ids, `NOTE`/`STYLE`/`REGION` blocks. Content with no `-->` timing line is not a caption file and is indexed as written |
| 39701 | WebBookmarkEvent | nipB0WebBookmarks | `listOfNotNull(title(), description())` NL |
| 39998 | AddressableListHeaderEvent | experimental/decentralizedLists/header | same as 9998 |
| 39999 | AddressableListItemEvent | experimental/decentralizedLists/item | same as 9998 |
| 40002 | StreamMessageV2Event | buzz/stream | `content` |
| 40100 | CanvasEvent | buzz/stream | `content` |
| 45001 | ForumPostEvent | buzz/forum | `content` |
| 45003 | ForumCommentEvent | buzz/forum | `content` |
| 48106 | HuddleGuidelinesEvent | buzz/huddles | `content` |

† **Kind 30063 is shared** by NIP-51 release artifact sets and NIP-82 software releases, and
`ReleaseArtifactSetEvent` parses both. It indexes `title()` and `description()`, plus `content`
(the release notes) only when the event carries the NIP-82 `i` + `version` tags — a NIP-51 set
may hold encrypted private items in `content`, which must never be indexed.

‡ **Kind 38000 is shared** by NIP-87 mint recommendations and two app formats, and `EventFactory`
picks the class by tags: `MintRecommendationEvent` when any `k` is 38172/38173 (or, with no
`k`, a non-blank `u` or an `a` to a 38172/38173 address); else `BallotEvent` on a non-blank
`election`; else `PredictionMarketEvent` on a `market`, ≥2 `outcome`s, or `type` + `end`; else
`UnrecognizedKind38000Event` — addressable (stores still key it by `d`) but unsearchable (the
spam votes that make up most of the kind). Kind-level probes (`EventFactory.probe`) answer as
`MintRecommendationEvent`. A market's `title()`/`description()` come from its `data` tag JSON,
the `title` tag, or JSON `content`, in that order.

## Abstract bases (no kind of their own)

| Base class | Body | Concrete kinds |
|---|---|---|
| `InteractiveStoryBaseEvent` | `listOfNotNull(title(), summary(), content)` NL | 30296, 30297 |
| `AddressableVideoEvent` | `listOfNotNull(title(), content)` NL | 34235, 34236 |
| `RegularVideoEvent` | `listOfNotNull(title(), content)` NL | 21, 22 |
| `TrustedListEvent` | `title() ?: ""` | 30392, 30393, 30394, 30395 |

## How to regenerate / verify this table

```bash
# All implementor files:
grep -rln "override fun indexableContent" quartz/src/commonMain
# For each, pair the KIND constant with the indexableContent() body.
# Searchability on the store path additionally requires EventFactory registration:
grep -n "<ClassName>" quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/EventFactory.kt
```

A CI-diffable snapshot test (assert the set of kinds whose `EventFactory` product implements
`SearchableEvent` against a checked-in list) would make this table impossible to go stale —
suggested follow-up, not yet implemented.
