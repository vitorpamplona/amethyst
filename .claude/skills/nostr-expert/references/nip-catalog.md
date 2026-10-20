# Quartz package catalog

Every top-level package under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/`
and the event classes it defines, keyed by their `const val KIND`. Generated from the
tree by `scripts/nip-catalog.py`; regenerate it after adding a package or a kind
instead of editing by hand:

```bash
python3 .claude/skills/nostr-expert/scripts/nip-catalog.py \
  quartz/src/commonMain/kotlin/com/vitorpamplona/quartz \
  > .claude/skills/nostr-expert/references/nip-catalog.md
```

"No event kinds" means the package holds tags, crypto, codecs or client helpers only.
The same kind under two classes is real (tag-dispatched kinds such as 30301/38000) —
grep the class. `ls` of the package root stays the ground truth, and
`scripts/nip-lookup.sh <NN>` lists one package's files.

## Standard NIP packages (`nipXX*`)

| Package | Event classes (kind) |
|---|---|
| `nip01Core/` | MetadataEvent 0 |
| `nip02FollowList/` | ContactListEvent 3 |
| `nip03Timestamp/` | OtsEvent 1040 |
| `nip04Dm/` | EncryptedDmEvent 4 |
| `nip05DnsIdentifiers/` | (no event kinds: tags/utilities) |
| `nip06KeyDerivation/` | (no event kinds: tags/utilities) |
| `nip09Deletions/` | DeletionRequestEvent 5 |
| `nip10Notes/` | TextNoteEvent 1 |
| `nip11RelayInfo/` | (no event kinds: tags/utilities) |
| `nip13Pow/` | (no event kinds: tags/utilities) |
| `nip14Subject/` | (no event kinds: tags/utilities) |
| `nip15Marketplace/` | BidEvent 1021, BidConfirmationEvent 1022, StallEvent 30017, ProductEvent 30018, MarketplaceEvent 30019, AuctionEvent 30020 |
| `nip17Dm/` | ChatMessageEvent 14, ChatMessageEncryptedFileHeaderEvent 15, DmRelayListEvent 10050 |
| `nip18Reposts/` | RepostEvent 6, GenericRepostEvent 16 |
| `nip19Bech32/` | (no event kinds: tags/utilities) |
| `nip21UriScheme/` | (no event kinds: tags/utilities) |
| `nip22Comments/` | CommentEvent 1111 |
| `nip23LongContent/` | LongFormContentEvent 30023, LongFormDraftEvent 30024 |
| `nip25Reactions/` | ReactionEvent 7, ExternalReactionEvent 17 |
| `nip28PublicChat/` | ChannelCreateEvent 40, ChannelMetadataEvent 41, ChannelMessageEvent 42, ChannelHideMessageEvent 43, ChannelMuteUserEvent 44, PublicChatListEvent 10005 |
| `nip29RelayGroups/` | GroupPutUserEvent 9000, GroupRemoveUserEvent 9001, GroupEditMetadataEvent 9002, GroupDeleteEventEvent 9005, CreateGroupEvent 9007, DeleteGroupEvent 9008, GroupCreateInviteEvent 9009, GroupUpdatePinListEvent 9010, GroupJoinRequestEvent 9021, GroupLeaveRequestEvent 9022, GroupMetadataEvent 39000, GroupAdminsEvent 39001, GroupMembersEvent 39002, GroupRolesEvent 39003, GroupParticipantsEvent 39004, GroupPinnedEvent 39005 |
| `nip30CustomEmoji/` | EmojiListEvent 10030, EmojiPackEvent 30030, StickerPackEvent 30031 |
| `nip31Alts/` | (no event kinds: tags/utilities) |
| `nip32Labeling/` | LabelEvent 1985 |
| `nip34Git/` | GitPatchEvent 1617, GitPullRequestEvent 1618, GitPullRequestUpdateEvent 1619, GitIssueEvent 1621, GitReplyEvent 1622, GitCoverNoteEvent 1624, CiManualTriggerEvent 9840, CiJobResultEvent 9841, CiWorkflowResultEvent 9842, CiServiceRequestEvent 9843, CiServiceStopEvent 9844, UserGraspListEvent 10317, CiCoordinatorAdvertisementEvent 19843, CiRequestReadinessListEvent 19844, CiSecretUpdateEvent 29846, GitRepositoryEvent 30617, GitRepositoryStateEvent 30618, CiWorkflowProgressEvent 39842, CiRepositoryStatusEvent 39844 |
| `nip35Torrents/` | TorrentEvent 2003, TorrentCommentEvent 2004 |
| `nip36SensitiveContent/` | (no event kinds: tags/utilities) |
| `nip37Drafts/` | PrivateOutboxRelayListEvent 10013, DraftWrapEvent 31234 |
| `nip38UserStatus/` | UserStatusEvent 30315 |
| `nip39ExtIdentities/` | ExternalIdentitiesEvent 10011 |
| `nip40Expiration/` | (no event kinds: tags/utilities) |
| `nip42RelayAuth/` | RelayAuthEvent 22242 |
| `nip43RelayMembers/` | RelayAddMemberEvent 8000, RelayRemoveMemberEvent 8001, RelayMembershipListEvent 13534, RelayJoinRequestEvent 28934, RelayInviteRequestEvent 28935, RelayLeaveRequestEvent 28936, RelayRoleEvent 33534 |
| `nip44Encryption/` | (no event kinds: tags/utilities) |
| `nip45Count/` | (no event kinds: tags/utilities) |
| `nip46RemoteSigner/` | NostrConnectEvent 24133 |
| `nip47WalletConnect/` | NwcInfoEvent 13194, NwcRequestEvent 23194, NwcResponseEvent 23195, NwcNotificationEvent 23197 |
| `nip48ProxyTags/` | (no event kinds: tags/utilities) |
| `nip49PrivKeyEnc/` | (no event kinds: tags/utilities) |
| `nip50Search/` | SearchRelayListEvent 10007 |
| `nip51Lists/` | MuteListEvent 10000, PinListEvent 10001, BookmarkListEvent 10003, BlockedRelayListEvent 10006, SimpleGroupListEvent 10009, FavoriteRelayListEvent 10012, InterestListEvent 10015, GitAuthorListEvent 10017, GitRepositoryListEvent 10018, MediaFollowListEvent 10020, FavoriteFollowSetsListEvent 10021, GeohashListEvent 10081, IndexerRelayListEvent 10086, ProxyRelayListEvent 10087, BroadcastRelayListEvent 10088, TrustedRelayListEvent 10089, FavoriteAlgoFeedsListEvent 10090, GoodWikiAuthorListEvent 10101, GoodWikiRelayListEvent 10102, FollowSetEvent 30000, OldBookmarkListEvent 30001, RelaySetEvent 30002, BookmarkSetEvent 30003, ArticleCurationSetEvent 30004, VideoCurationSetEvent 30005, PictureCurationSetEvent 30006, KindMuteSetEvent 30007, InterestSetEvent 30015, ReleaseArtifactSetEvent 30063, AppCurationSetEvent 30267, StarterPackEvent 39089, MediaStarterPackEvent 39092 |
| `nip52Calendar/` | CalendarDateSlotEvent 31922, CalendarTimeSlotEvent 31923, CalendarCollectionEvent 31924, CalendarRSVPEvent 31925 |
| `nip53LiveActivities/` | LiveActivitiesChatMessageEvent 1311, LiveActivitiesRaidEvent 1312, LiveActivitiesClipEvent 1313, NestsServersEvent 10112, MeetingRoomPresenceEvent 10312, LiveActivitiesEvent 30311, MeetingSpaceEvent 30312, MeetingRoomEvent 30313 |
| `nip54Wiki/` | WikiMergeRequestEvent 818, WikiMergeAcceptanceEvent 819, WikiArticleEvent 30818, WikiRedirectEvent 30819 |
| `nip56Reports/` | ReportEvent 1984 |
| `nip57Zaps/` | PrivateZapEvent 9733, ZapRequestEvent 9734, ZapReceiptEvent 9735 |
| `nip58Badges/` | BadgeAwardEvent 8, ProfileBadgesEvent 10008, AcceptedBadgeSetEvent 30008, BadgeDefinitionEvent 30009 |
| `nip59Giftwrap/` | SealEvent 13, GiftWrapEvent 1059, EphemeralGiftWrapEvent 21059 |
| `nip5aStaticWebsites/` | SiteSnapshotEvent 5128, RootSiteEvent 15128, NamedSiteEvent 35128 |
| `nip5dNapplets/` | NappletSnapshotEvent 5129, RootNappletEvent 15129, NamedNappletEvent 35129 |
| `nip60Cashu/` | CashuMintQuoteEvent 7374, CashuTokenEvent 7375, CashuSpendingHistoryEvent 7376, CashuWalletEvent 17375 |
| `nip61Nutzaps/` | NutzapEvent 9321, NutzapInfoEvent 10019 |
| `nip62RequestToVanish/` | RequestToVanishEvent 62 |
| `nip64Chess/` | JesterProtocol 30, ChessGameEvent 64, LiveChessGameChallengeEvent 30064, LiveChessGameAcceptEvent 30065, LiveChessMoveEvent 30066, LiveChessGameEndEvent 30067, LiveChessDrawOfferEvent 30068 |
| `nip65RelayList/` | AdvertisedRelayListEvent 10002 |
| `nip66RelayMonitor/` | RelayMonitorEvent 10166, RelayProbeWriteTest 20166, RelayDiscoveryEvent 30166 |
| `nip68Picture/` | PictureEvent 20 |
| `nip69P2pOrderEvents/` | MostroDevFeePaymentEvent 8383, RoboSatsCoordinatorRatingEvent 31986, P2POrderEvent 38383, MostroUserRatingEvent 38384, MostroInfoEvent 38385, MostroDisputeEvent 38386 |
| `nip70ProtectedEvts/` | (no event kinds: tags/utilities) |
| `nip71Video/` | VideoNormalEvent 21, VideoShortEvent 22, VideoViewEvent 22236, AddressableNormalVideoEvent 34235, AddressableShortVideoEvent 34236, TextTrackEvent 39307 |
| `nip72ModCommunities/` | CommunityPostApprovalEvent 4550, CommunityListEvent 10004, CommunityDefinitionEvent 34550, CommunityRulesEvent 34551 |
| `nip73ExternalIds/` | (no event kinds: tags/utilities) |
| `nip75ZapGoals/` | ZapGoalEvent 9041 |
| `nip77Negentropy/` | (no event kinds: tags/utilities) |
| `nip78AppData/` | AppDataEvent 78, AppSpecificDataEvent 30078 |
| `nip7DThreads/` | ThreadEvent 11 |
| `nip84Highlights/` | HighlightEvent 9802 |
| `nip85TrustedAssertions/` | TrustProviderListEvent 10040, UserAssertionEvent 30382, EventAssertionEvent 30383, AddressableAssertionEvent 30384, ExternalIdAssertionEvent 30385 |
| `nip86RelayManagement/` | (no event kinds: tags/utilities) |
| `nip87Ecash/` | MintRecommendationEvent 38000, CashuMintEvent 38172, FedimintEvent 38173 |
| `nip88Polls/` | PollResponseEvent 1018, PollEvent 1068 |
| `nip89AppHandlers/` | AppRecommendationEvent 31989, AppDefinitionEvent 31990 |
| `nip90Dvms/` | DvmTextExtractionRequestEvent 5000, DvmSummarizationRequestEvent 5001, DvmTranslationRequestEvent 5002, DvmTextGenerationRequestEvent 5050, DvmImageGenerationRequestEvent 5100, DvmVideoConversionRequestEvent 5200, DvmVideoTranslationRequestEvent 5201, DvmImageToVideoRequestEvent 5202, DvmTextToSpeechRequestEvent 5250, DvmContentDiscoveryRequestEvent 5300, DvmUserDiscoveryRequestEvent 5301, DvmContentSearchRequestEvent 5302, DvmPeopleSearchRequestEvent 5303, DvmEventCountRequestEvent 5400, DvmMalwareScanRequestEvent 5500, DvmEventTimestampingRequestEvent 5900, DvmOpReturnRequestEvent 5901, DvmEventPublishScheduleRequestEvent 5905, DvmEventPowDelegationRequestEvent 5970, DvmTextExtractionResponseEvent 6000, DvmSummarizationResponseEvent 6001, DvmTranslationResponseEvent 6002, DvmTextGenerationResponseEvent 6050, DvmImageGenerationResponseEvent 6100, DvmVideoConversionResponseEvent 6200, DvmVideoTranslationResponseEvent 6201, DvmImageToVideoResponseEvent 6202, DvmTextToSpeechResponseEvent 6250, DvmContentDiscoveryResponseEvent 6300, DvmUserDiscoveryResponseEvent 6301, DvmContentSearchResponseEvent 6302, DvmPeopleSearchResponseEvent 6303, DvmEventCountResponseEvent 6400, DvmMalwareScanResponseEvent 6500, DvmEventTimestampingResponseEvent 6900, DvmOpReturnResponseEvent 6901, DvmEventPublishScheduleResponseEvent 6905, DvmEventPowDelegationResponseEvent 6970, DvmStatusEvent 7000, DvmHeartbeatEvent 11998 |
| `nip92IMeta/` | (no event kinds: tags/utilities) |
| `nip94FileMetadata/` | FileMetadataEvent 1063 |
| `nip96FileStorage/` | FileServersEvent 10096 |
| `nip98HttpAuth/` | HTTPAuthorizationEvent 27235 |
| `nip99Classifieds/` | ClassifiedsEvent 30402, ProductCollectionEvent 30405, ShippingOptionEvent 30406, ProductReviewEvent 31555 |
| `nipA0VoiceMessages/` | VoiceEvent 1222, VoiceReplyEvent 1244 |
| `nipA3PaymentTargets/` | PaymentTargetsEvent 10133 |
| `nipA4PublicMessages/` | PublicMessageEvent 24 |
| `nipACWebRtcCalls/` | CallOfferEvent 25050, CallAnswerEvent 25051, CallIceCandidateEvent 25052, CallHangupEvent 25053, CallRejectEvent 25054, CallRenegotiateEvent 25055 |
| `nipB0WebBookmarks/` | WebBookmarkEvent 39701 |
| `nipB1Bolt12Zaps/` | Bolt12ZapEvent 9736, Bolt12ZapIntentEvent 9737, Bolt12OfferListEvent 10058 |
| `nipB7Blossom/` | BlossomServersEvent 10063, BlossomAuthorizationEvent 24242 |
| `nipBCOnchainZaps/` | OnchainZapEvent 8333 |
| `nipBEBle/` | (no event kinds: tags/utilities) |
| `nipC0CodeSnippets/` | CodeSnippetEvent 1337 |
| `nipC7Chats/` | ChatEvent 9 |
| `nipCCGeocaching/` | GeocacheFoundLogEvent 7516, GeocacheVerificationEvent 7517, GeocacheListingEvent 37516, GeocacheCurationListEvent 37517 |
| `nipF4Podcasts/` | PodcastEpisodeEvent 54, FavoritePodcastsListEvent 10054, AuthoredPodcastsEvent 10064, PodcastMetadataEvent 10154 |
| `nipFERelayOverHttp/` | (no event kinds: tags/utilities) |
| `nipXXPodcasting20/` | Podcasting20EpisodeEvent 30054, Podcasting20TrailerEvent 30055 |
| `nipXXPrivateNoteStorage/` | PnsEvent 1080 |
| `nipXXPushNotifications/` | PushRegistrationEvent 3079, PushDeregistrationEvent 3080, PushPreferencesEvent 3083 |

## Non-NIP protocol families

| Package | Event classes (kind) |
|---|---|
| `buzz/` | ArchivedIdentityEvent 8002, UnarchivedIdentityEvent 8003, RelayAdminAddMemberEvent 9030, RelayAdminRemoveMemberEvent 9031, RelayAdminChangeRoleEvent 9032, SetWorkspaceProfileEvent 9033, ArchiveRequestEvent 9035, UnarchiveRequestEvent 9036, ModerationBanEvent 9040, ModerationUnbanEvent 9041, ModerationTimeoutEvent 9042, ModerationUntimeoutEvent 9043, ModerationResolveReportEvent 9044, AgentProfileEvent 10100, ArchivedIdentitiesListEvent 13535, PresenceUpdateEvent 20001, TypingIndicatorEvent 20002, PairingEvent 24134, ObserverFrameEvent 24200, HuddleReactionEvent 24810, EngramEvent 30174, PersonaEvent 30175, TeamEvent 30176, ManagedAgentEvent 30177, TeamCatalogEvent 30178, EventReminderEvent 30300, PushLeaseEvent 30350, WorkflowDefEvent 30620, ProjectEvent 30621, DmVisibilityEvent 30622, ThreadSummaryEvent 39005, WindowBoundsEvent 39006, ThreadWindowBoundsEvent 39007, StreamMessageV2Event 40002, StreamMessageEditEvent 40003, StreamMessagePinnedEvent 40004, StreamMessageBookmarkedEvent 40005, StreamMessageScheduledEvent 40006, StreamReminderEvent 40007, StreamMessageDiffEvent 40008, SystemMessageEvent 40099, CanvasEvent 40100, ChannelSummaryEvent 40901, PresenceSnapshotEvent 40902, DmCreatedEvent 41001, DmOpenEvent 41010, DmAddMemberEvent 41011, DmHideEvent 41012, ProductFeedbackEvent 42000, JobRequestEvent 43001, JobAcceptedEvent 43002, JobProgressEvent 43003, JobResultEvent 43004, JobCancelEvent 43005, JobErrorEvent 43006, MemberAddedNotificationEvent 44100, MemberRemovedNotificationEvent 44101, AgentTurnMetricEvent 44200, ForumPostEvent 45001, ForumVoteEvent 45002, ForumCommentEvent 45003, ArtifactEvent 45010, ArtifactRemovalEvent 45011, WorkflowTriggeredEvent 46001, WorkflowStepStartedEvent 46002, WorkflowStepCompletedEvent 46003, WorkflowStepFailedEvent 46004, WorkflowCompletedEvent 46005, WorkflowFailedEvent 46006, WorkflowCancelledEvent 46007, WorkflowApprovalRequestedEvent 46010, WorkflowApprovalGrantedEvent 46011, WorkflowApprovalDeniedEvent 46012, WorkflowTriggerEvent 46020, ApprovalGrantEvent 46030, ApprovalDenyEvent 46031, AuditEntryEvent 48001, HuddleStartedEvent 48100, HuddleParticipantJoinedEvent 48101, HuddleParticipantLeftEvent 48102, HuddleEndedEvent 48103, HuddleLivenessEvent 48104, HuddleGuidelinesEvent 48106, MediaUploadEvent 49001 |
| `concord/` | ConcordChatEditEvent 3302, ControlEditionEvent 3308, ConcordWebxdc 3310, ConcordCommunityListEvent 13302, ConcordInviteListEvent 13303, VoicePresence 23313, ConcordBrokerToken 27235, ConcordInviteBundleEvent 33301, ConcordCommunityListFragmentEvent 33302 |
| `contextvm/` | (no event kinds: tags/utilities) |
| `cordn/` | (no event kinds: tags/utilities) |
| `cyberspace/` | SnoShardEvent 3330, SnoAvatarEvent 11333, CyberspaceBagEvent 33330, SnoObjectEvent 33331 |
| `feedDefinition/` | FeedDefinitionEvent 31890 |
| `kinds/` | (no event kinds: tags/utilities) |
| `lightning/` | (no event kinds: tags/utilities) |
| `marmot/` | LegacyKeyPackageEvent 443, WelcomeEvent 444, GroupEvent 445, NotificationRequestEvent 446, TokenRequestEvent 447, TokenListEvent 448, TokenRemovalEvent 449, AccountIdentityProofV2 450, PushOwnerProof 451, AgentTextStreamStart 1200, KeyPackageRelayListEvent 10051, KeyPackageEvent 30443 |
| `openagents/` | ExtensionReleaseEvent 3184, CapabilityDiscoveryEvent 30180, ExtensionListingEvent 30184 |
| `podcasts/` | (no event kinds: tags/utilities) |

## `experimental/` (draft / unmerged NIPs)

| Package | Event classes (kind) |
|---|---|
| `experimental/agora/` | FundraiserEvent 33863 |
| `experimental/attestations/` | AttestorProficiencyEvent 11871, AttestationEvent 31871, AttestationRequestEvent 31872, AttestorRecommendationEvent 31873 |
| `experimental/audio/` | AudioHeaderEvent 1808, AudioTrackEvent 31337 |
| `experimental/ballots/` | BallotEvent 38000 |
| `experimental/birdstar/` | BirdDetectionEvent 2473, BirdexEvent 12473 |
| `experimental/bitchat/` | GeohashChatEvent 20000, GeohashPresenceEvent 20001 |
| `experimental/bounties/` | BountyAddValueEvent 1 |
| `experimental/citations/` | ExternalCitationEvent 31, HardcopyCitationEvent 32, PromptCitationEvent 33 |
| `experimental/clink/` | OfferEvent 21001, DebitEvent 21002, ManageEvent 21003 |
| `experimental/decentralizedLists/` | ListHeaderEvent 9998, ListItemEvent 9999, AddressableListHeaderEvent 39998, AddressableListItemEvent 39999 |
| `experimental/decoupling/` | EncryptionKeyRequestEvent 4454, EncryptionKeyTransferEvent 4455, EncryptionKeyListEvent 10044 |
| `experimental/edits/` | TextNoteModificationEvent 1010 |
| `experimental/ephemChat/` | EphemeralChatListEvent 10023, EphemeralChatEvent 23333 |
| `experimental/fitness/` | WorkoutRecordEvent 1301, ExerciseTemplateEvent 33401, WorkoutTemplateEvent 33402 |
| `experimental/forks/` | (no event kinds: tags/utilities) |
| `experimental/graperank/` | (no event kinds: tags/utilities) |
| `experimental/inlineMetadata/` | (no event kinds: tags/utilities) |
| `experimental/interactiveStories/` | InteractiveStoryPrologueEvent 30296, InteractiveStorySceneEvent 30297, InteractiveStoryReadingStateEvent 30298 |
| `experimental/kanban/` | KanbanBoardEvent 30301, KanbanCardEvent 30302 |
| `experimental/library/` | BookshelfDirectoryEvent 30045, LearningResourceEvent 30142, BlossomPieceIndexEvent 32176 |
| `experimental/medical/` | FhirResourceEvent 82 |
| `experimental/music/` | MusicPlaylistEvent 34139, MusicTrackEvent 36787 |
| `experimental/nests/` | AdminCommandEvent 4312 |
| `experimental/nip82SoftwareApps/` | SoftwareAssetEvent 3063, SoftwareApplicationEvent 32267 |
| `experimental/nip95/` | FileStorageEvent 1064, FileStorageHeaderEvent 1065 |
| `experimental/nipsOnNostr/` | NipTextEvent 30817 |
| `experimental/nns/` | NNSEvent 30053 |
| `experimental/notifications/` | WakeUpEvent 23903 |
| `experimental/postingStreak/` | PostingStreakEvent 13473 |
| `experimental/predictionMarkets/` | PredictionMarketEvent 38000 |
| `experimental/profileGallery/` | ProfileGalleryEntryEvent 1163 |
| `experimental/profileTheme/` | ActiveProfileThemeEvent 16767, ThemeDefinitionEvent 36767 |
| `experimental/ps1saves/` | Ps1SaveEvent 38192 |
| `experimental/publications/` | PublicationIndexEvent 30040, PublicationContentEvent 30041 |
| `experimental/ratings/` | RelayReviewEvent 31987, EntityRatingEvent 34259 |
| `experimental/roadstr/` | RoadEventReportEvent 1315, RoadEventConfirmationEvent 1316 |
| `experimental/topEight/` | TopEightEvent 18678 |
| `experimental/trustedLists/` | UserTrustedListEvent 30392, EventTrustedListEvent 30393, AddressableTrustedListEvent 30394, ExternalIdTrustedListEvent 30395 |
| `experimental/videoCollaboration/` | VideoCollaborationEvent 34238 |
| `experimental/walletScrutiny/` | AssetBundleEvent 9401, BuildVerificationEvent 30301 |
| `experimental/zapPolls/` | ZapPollEvent 6969 |
| `experimental/zapstore/` | IdentityProofEvent 30509 |
