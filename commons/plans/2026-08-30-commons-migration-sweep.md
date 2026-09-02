# Full sweep: `amethyst/` → `:commons` migration candidates

> **Execution status (updated 2026-08-30, same branch):** Waves 0-1 are DONE
> on this branch — the 12 shim deletions, the 38-file relayClient batch
> (with `AccountScopedQuery` generalized to `IAccount`), the okhttp stack →
> `commons/service/http`, 37 model/service singles, the
> `IFeedTopNavFilter` → `ICacheProvider` signature fix, `TopFilter`
> extracted out of `AccountSettings.kt`, and 34 topNavFeeds files reunited
> with their commons half. All app/desktop/cli targets compile.
> Corrections found while executing are folded into the sections below;
> the biggest one: **import-graph analysis under-counts blockers** —
> same-package files use `Account`/`LocalCache`/each other *without
> imports* (extension receivers included), so several "clean" files
> (AccountMarmotActions, EventBroadcaster, ParticipantListBuilder,
> UnexpectedCrashSaver, the Blossom fetchers) are actually
> Account/LocalCache-coupled and stayed. The `ui/theme` + `ui/layouts`
> batch also does NOT move mechanically: `Theme.kt` is Android-coupled
> (Activity, UiModeManager, app font/theme prefs enums) and the layouts
> sit on app-side theme constants + `stringRes` — that whole cluster
> belongs to the strings/theme wave.
>
> **Refined `LocalCache` recipe (next big step, needs maintainer input):**
> the move-group is `LocalCache` + `AntiSpamFilter` (android LruCache →
> androidx.collection) + `CachePruner` + `CacheSearch` + `MiniFhir` +
> `OnchainZapResolver`, into commons **jvmAndroid** (which legalizes its
> `java.io.File` NIP-95 spill as-is). Seams to cut: `Amethyst.instance`
> (2 sites → injected scope), app `isDebug` (2 → settable flag),
> `checkNotInMainThread` (→ settable hook or expect), `ui.note.dateFormatter`
> (1 log line). The open design question: `CachePruner`/`CacheSearch` call
> `Account.isFollowing(...)` and read `account.hiddenUsers.flow.value.
> hiddenWordsCase` — `IAccount` already has `isHidden`/`hiddenWordsCase`
> but lacks `isFollowing`, so either `IAccount` grows it (DesktopIAccount
> must implement) or the pruner/search take a narrower ISP interface.

**Date:** 2026-08-30
**Scope:** every Kotlin source file in the `amethyst` Android module
(`amethyst/src/main`, 2,347 files), audited for "can and should move to
`:commons`", cross-checked against what `commons/` and `desktopApp/` already
contain.

## Method

1. **Static classification** of all 2,347 files by imports:
   - *Android-dirty* = imports `android.*`, `com.google.*`, or `androidx.*`
     other than the KMP-safe set (`compose`, `lifecycle`, `annotation`,
     `paging`, `collection`), or uses `androidx.navigation`.
   - *R-dirty* = imports `com.vitorpamplona.amethyst.R` or references
     `R.string`/`R.drawable`/`R.raw` (incl. via `stringRes`).
2. **Transitive closure** over the module-internal import graph: a file is
   **Tier A** iff it and everything it references inside the module are
   clean of both. **Tier B** = clean once its strings move to commons
   Compose resources.
3. **Six deep area audits** (model, service, service/relayClient+okhttp,
   ui shared components, ui/screen, napplet/connectedApps/misc) verifying
   verdicts file-by-file and hunting for desktopApp reimplementations.

## Headline numbers

| Metric | Count |
|---|---|
| Files in `amethyst/src/main` | 2,347 |
| Directly Android/R-clean | 1,324 |
| **Tier A — transitively clean, movable now** | **509** |
| Tier B — movable once their strings migrate | +21 |
| Files that genuinely touch an Android API | ~14% of `ui/` |
| ViewModels app-side vs migrated to commons | 138 vs 11 |
| Strings migrated to commons Compose resources | 152 / 4,417 |

The dominant blockers are **not Android APIs**. They are five hub types that
are themselves Android-import-free but still live in the app module, plus the
string-resource bridge:

| Blocker | Files it transitively blocks | Nature |
|---|---|---|
| `model.Account` | 254 | Android-clean god-object (184 vals, 218 funs) — decompose, don't move |
| `ui.screen.loggedIn.AccountViewModel` | 180 | genuinely Android-coupled (9 android imports, 36 `R` refs) — shrink, don't move |
| `ui.navigation.navs.INav` / `routes.Route` | 173 | platform nav — correctly stays |
| `model.LocalCache` | 115 | **Android-clean**; only 3 trivial dirty deps |
| `ui.stringRes` (`StringResourceCache`) | ~210 direct users | needs a Compose-resources twin |
| `model.TopFilter` (declared inside `AccountSettings.kt`!) | 39 | clean sealed class in the wrong file |

## The five highest-leverage edits

Each of these is small and unlocks a large batch:

1. **`service/relays/EOSE.kt` → finish the move.** 4 of its 6 declarations
   are already typealiases into `commons/relays`; `SincePerRelayMap`
   (`androidx.collection.LruCache` — KMP-safe) and `EOSEAccountKey` remain.
   Moving them **unblocks ~145 `subassemblies/` filter files at once** (97%
   of that layer is already Tier A).
2. **`IFeedTopNavFilter` signature fix.** `model/topNavFeeds/IFeedTopNavFilter.kt:34,36`
   hard-codes `cache: LocalCache` in `toPerRelayFlow`/`startValue`, poisoning
   all 23 `*TopNavFilter` implementors. Change to the existing commons
   `ICacheProvider` port → the app half of `topNavFeeds` reunites with the
   25-file half already in `commons/model/topNavFeeds/`.
3. **Move `LocalCache` (3,980 lines).** It's `object LocalCache : ILocalCache,
   ICacheProvider, Dao` with zero Android imports. Its entire dirty-dep set:
   `MainThreadChecker` (Looper → `expect` no-op on JVM), a `dateFormatter`
   log line, `Amethyst.instance` scope (inject), `BundledInsert` (commons
   `BundledUpdate` already exists), NIP-95 `java.io.File` blob spill
   (`expect` sink), and hoisting the inline `ILocalCache` interface.
   **This deletes `DesktopLocalCache.kt` (1,173 lines) — the single largest
   duplication in the repo.**
4. **Extract `TopFilter` + a `ListBackupStore` port out of `AccountSettings.kt`.**
   `AccountSettings` (1,900 lines) mixes the `backupXList`/`updateXList`
   persistence port with nav-shape types (`NavBarItem`,
   `DrawerItemVisibility`) and the `TopFilter` sealed class (line 102).
   The port + `ICacheProvider` unlock all of `model/nip51Lists` (31 files)
   and ~18 one-file `model/nipNN` packages — the commons copies of
   `Nip65RelayListState` etc. prove the recipe is purely mechanical
   (`LocalCache` → `ICacheProvider`, drop `AccountSettings`).
5. **A Compose-resources `stringRes` twin.** `ui/StringResourceCache.kt`
   defines the six `stringRes()` overloads + `painterRes` on
   `android.util.LruCache` + `R` ids. commons already has the translated
   `composeResources` pipeline (152 strings so far). This is the gate on
   ~210 otherwise-clean composables/ViewModels.

## MOVE-NOW batches (no prerequisites)

These are Tier A today; grouped as reviewable PRs. Verified per-file by the
area audits.

### Batch 1 — relay client (highest confidence, zero risk)
- `service/relayClient/eoseManagers/`: `PerUserEoseManager`,
  `PerUserAndFollowListEoseManager`, `PerUniqueIdEoseManager`,
  `AccountScopedSingleSubNoEoseCacheEoseManager` → `commons/relayClient/eoseManagers`
  (they extend commons `BaseEoseManager` already; deps `User`,
  `SincePerRelayMap` are commons or move with Batch 1).
- `reqCommand/account/` pure `filterXxx()` functions (~9 files:
  `FilterDraftsAndReportsFromKey`, `FilterBasicAccountInfoFromKeys`,
  `FilterBookmarksAndReportsFromKey`, `FilterFollowsAndMutesFromKey`,
  `FilterLastPostsFromKey`, `FilterAccountInfoAndListsFromKey`,
  `FilterNotificationsToPubkey`, `FilterCashuHistoryToPubkey`) + the
  account EOSE managers that consume them → `commons/relayClient/account`.
- `reqCommand/channel/` filters + watchers (`FilterChannelMetadata*`,
  `FilterLiveStreamUpdatesByAddress`, watcher sub-assemblers) →
  `commons/relayClient/channel`.
- `reqCommand/nwc/` (FilterNWCPaymentsFromRequests, NWCPaymentFilterAssembler,
  NWCPaymentWatcherSubAssembler) → `commons/relayClient/nip47WalletConnect`.
- `searchCommand/subassemblies/SearchPeopleByName`, `SearchPostsByText` →
  `commons/relayClient/search`. **Desktop's `SearchFilterFactory.kt:56`
  literally comments "ported from Android SearchPostsByText".**
- `chatDelivery/`, `speedLogger/` → commonMain; `diagnostics/` → jvmAndroid.
- `TorCircuitHealthTracker` → `commons/relays/health`.
- `authCommand/model` (9 of 11 files) → **merge into** the existing
  `commons/relayClient` auth types (`AuthApprovalPolicy`/`AuthApprovalRequests`
  are a thinner second implementation of the same concern — reconcile, don't
  copy). `DataStoreRelayAuthPermissionStore` stays as the Android actual.

### Batch 2 — the ~145 `ui/screen/**/subassemblies/` filter files
Pure `fun filterXByY(relay, …): List<RelayBasedFilter>` functions, 97%
Tier A, already importing `commons.relayClient.subscriptions.ExplainedFilter`.
Sole prerequisite: `SincePerRelayMap` (Batch 1). Desktop reimplements 34 of
them by hand in `desktop/subscriptions/FilterBuilders.kt` (742 lines).

### Batch 3 — okhttp stack → `commons/service/http` (jvmAndroid)
16 of 20 files in `service/okhttp/` move as-is: `IHttpClientManager`,
`DualHttpClientManager(+ForRelays)`, `OkHttpClientFactory(+ForRelays)`, all 8
interceptors, both event listeners, `OnionLocationCache`, `OkHttpDebugLogging`.
Plus `model/privacyOptions/` (4 of 5: role-based client builders,
`ProxiedSocketFactory`). okhttp3 is an established commons jvmAndroid dep
(BlossomClient, lnurl, UrlPreview) with no shared client manager today.
- `EncryptionKeyCache` needs `android.util.LruCache` → KMP cache first.
- `IsEmulator` stays (androidMain actual).
- `OkHttpWebSocket` is production-dead (only androidTest usages) and
  duplicates quartz's `BasicOkHttpWebSocket` — migrate the tests, delete.
- **Closes a real desktop gap**: `desktop/network/DesktopHttpClient.kt`
  re-implements the dual direct/SOCKS client but is missing every
  interceptor (onion-location, Blossom auth, encrypted blobs).

### Batch 4 — theme, layouts, feed shell (UI quick wins, no string work)
- `ui/theme/` (Theme.kt 780 lines, Shape.kt, Type.kt, Color.kt) →
  `commons/ui/theme`. commons' theme package is only a fragment (no
  ColorScheme/Typography/Shapes); **desktop re-declares the entire palette in
  `desktop/platform/PlatformColorScheme.kt` — two divergent Amethyst palettes
  ship today.**
- `ui/layouts/`: `NoteComposeLayout` (374), `RepostLayout`, `ScreenLayout`
  are 100% clean; `LeftPictureLayout`, `ChatHeaderLayout`,
  `SlimListItemLayout` need only content-description strings.
  `DisappearingScaffold` → SPLIT (drop the `AccountViewModel` param for a
  slot; its nested-scroll half is already in commons).
- `ui/feeds/` (12 of 14): `FeedStates`, `RefresheableBox`, `WatchScrollToTop`,
  `RememberForeverStates`, `ChannelFeedContentState`,
  `WatchLifecycleAndUpdateModel` have zero blockers;
  `FeedEmpty/Error/Loading/UserBlockedFeed` need 2-3 strings each. Desktop
  hand-rolls its own empty/error/loading states.
- `ui/components/` 20 zero-blocker files: the `Clickable*` family,
  `M3ActionDialog`, `OutlinedThinPaddingTextField`, `SlidingCarousel`,
  `AudioWaveformReadOnly`, `LatexEquation`, `FileAttachmentCard`,
  `pdf/PdfFetcher`, and `toasts/` (6 of 9 — despite the name it's a Compose
  snackbar queue, not Android Toast).
- `navigation/topbars/` chrome (`ShorterTopAppBar`, `TopBarWithBackButton`,
  `ActionTopBar`, `AmethystClickableIcon`) → `commons/ui/layouts`.

### Batch 5 — model + misc clean files
- `model/` root, 14 files: `HomeFeedType`, `VideoPostKind`, `HashtagIcon`,
  `ConcordInviteResult`, `NoteEditOverlays`, `PrivateChatroomReadState`,
  `RelayGroupContentRouting`, `MutedPublicChats`, `ParticipantListBuilder`,
  `LargeSoftCacheAddressExt`, `Dao`, `EventBroadcaster` →
  `commons/model`; `AccountMarmotActions`, `AccountRelayGroupActions` →
  `commons/actions`.
- `model/nip64Chess/ChessAction` → `commons/nip64Chess`.
- `model/nip03Timestamp` (4 of 6: OTS settings, explorer endpoints,
  verification, resolver builder) → `commons/model/nip03Timestamp`.
- `model/marmot/InMemoryMlsGroupStateStore` → `commons/marmot`.
- `model/nip47WalletConnect/NwcInfoCache` → `commons/model/nip47WalletConnect`.
- `ui/dal/AdditiveComplexFeedFilter` (6-line abstract class, zero imports) →
  `commons/ui/feeds`; `DefaultFeedOrderEvent` likewise.
- napplet clean half: `NappletRelayCleartext` (pure NIP-04/44 → quartz or
  `commons/napplet/protocol`), `NappletLaunchRegistry`,
  `NappletNotificationStore`, `NappletIdentityWatch` → `commons/napplet`
  (jvmAndroid).
- `service/namecoin/NamecoinNameService` → `commons/service/namecoin`.
  **Desktop's `DesktopNamecoinNameService.kt` says "Same functionality as the
  Android NamecoinNameService… mirrors AppModules#buildNamecoinBackend
  exactly"** and is the superset — merge into it, not alongside it.
- `service/images/` fetchers (7 of 11: `Base64Fetcher`, `BlurHashFetcher`,
  `ThumbHashFetcher`, `ProfilePictureFetcher`, `BlossomFetcher`,
  `BlossomReadAuthFetcher`, `DeferredDeleteFileSystem`) →
  `commons/service/image` (jvmAndroid). Desktop clones four of them
  file-for-file; `commons/{blurhash,thumbhash,base64Image}` actuals already
  exist to sit under them.
- `service/resourceusage/` 13 of 18 files (`UsageKeys`, `UsageSummary`,
  `ResourceUsageStore`, `ResourceUsageAccountant`, `ResourceUsageAlerts`,
  `RefCountedSession`, `MeteringNostrSigner`, `BatteryDrainSampler`,
  `ResourceUsageReportAssembler`, time integrators, `RelayUsageListener`) →
  `commons/service/resourceusage` (only `SystemClock` → `TimeUtils` swaps).
- `service/cashu/` → `commons/cashu` (rest of the wallet is already there);
  `CachedCashuParser`/`MeltProcessor` need the KMP LRU.
- Small singles: `PodcastRemoteContent` → `commons/podcasts`;
  `BuzzInviteMinter` → `commons/actions` or `commons/buzz`;
  `WritingAssistant` (pure interface) → commonMain; `DetectedWorkout` +
  `WorkoutMerger` (197 lines of merge logic) → `commons/workouts`;
  `ConnectivityStatus` + `ConnectivityManager` flow plumbing → commonMain
  with the Android `ConnectivityFlow` as actual; crashreports logic
  (`UnexpectedCrashSaver`, `CrashReportCache`, `DevReportContact`) →
  jvmAndroid (desktop currently has *no* crash reporting);
  `ScheduledPostWorkGate` → `commons/scheduledposts` (desktop duplicates the
  drain gating); `PowJobStore`/`PowJobRestorer` → `commons/service/pow`
  (desktop lacks PoW persistence entirely);
  `service/uploads/` clean half (`ImageDownloader`, `MediaUploadResult`,
  `MediaMimeTypes`, `SuspendableConfirmation`, `blossom/*`, `hls/*` builders,
  `nip96/ServerInfoRetriever`) → `commons/service/upload` (jvmAndroid).
- `ui/tor/` non-service half (8 files: `TorSettings(+Flow)`,
  `TorServiceStatus`, `TorBackend`, `ArtiGuardState`, `TorPreferencesPort`,
  `TorManager`, `TorDialogViewModel`) → fold into existing `commons/tor`.
- `ui/screen` root strays: `AccountState`, `UserFeedState`,
  `DebouncedPublisher`, `relays/common/RelaySuggestionState`,
  `embed/SelectionUiState`.
- `ui/screen/loggedIn/discover/` is 67% Tier A — near-wholesale move of its
  datasource + subassemblies layers.

### Delete-only (shims whose move already happened)
`model/Note.kt`, `model/User.kt`, `model/HashtagIcon.kt`,
`model/torState/TorRelaySettings.kt`, `model/torState/TorRelayEvaluation.kt`,
`ui/dal/FeedFilters.kt`, `ui/dal/ChangesFlowFilter.kt`, `ui/feeds/FeedStates.kt`
(partly), `service/BundledUpdates.kt`, `service/relays/EOSE.kt` (after
Batch 1), `ui/screen/FeedViewModel.kt`,
`chats/privateDM/dal/ChatroomFeedViewModel.kt` + `ChatroomFeedFilter.kt`,
`threadview/dal/LevelFeedViewModel.kt`, `reqCommand/user/UserFinderShims.kt`,
`reqCommand/event/EventFinderShims.kt` — 13+ typealias re-export files.
Rewrite importers to the commons FQNs and delete.

Also: adopt `commons/util/toTimeAgo` and **delete
`ui/note/TimeAgoFormatter.kt`** (377 lines on `android.text.format.DateUtils`
— the app and desktop currently render time-ago with two divergent
formatters).

## MOVE-AFTER (blocked, with the named blocker)

| Area | Files | Blocker(s) | Target |
|---|---|---|---|
| `ui/screen/**/dal/` feed filters + thin VMs | ~114 | `Account` (106 of 109 block on it alone), `LocalCache` | `commons/ui/feeds` + `commons/viewmodels` |
| `ui/screen/**/datasource/` assemblers | ~168 | `AccountViewModel`, `TopFilter`, app-side eoseManagers | `commons/relayClient` |
| `model/nip51Lists/` state classes | 18 | `ICacheProvider` swap + `ListBackupStore` port | `commons/model/nip51Lists` |
| `model/topNavFeeds/` | 37 | the `IFeedTopNavFilter` signature edit | `commons/model/topNavFeeds` |
| ~18 one-file `model/nipNN` state pkgs (`nip17Dms`, `nip65RelayList`, `nipB7Blossom`, `edits`, `localRelays`, `zap`, `buzz`, `algoFeeds`, `trustedAssertions`, …) | ~25 | same two ports; **four already have commons twins — reconcile-and-delete, don't copy** (`nip65RelayList`, `nipB7Blossom`, `nip30CustomEmojis`, `nip72Communities`) | matching `commons/model/nipNN` |
| `model/serverList`, `model/nip01UserMetadata`, `model/nip02FollowLists` | 17 | `LocalCache` | `commons/model/…`; replaces desktop's hand-rolled `DesktopAccountRelays` (250 lines) |
| `AntiSpamFilter`, `MediaAspectRatioCache`, `UrlCachedPreviewer`, `Nip11CachedRetriever` | 5 | KMP `LruCache` expect (or quartz `LargeCache`) | `commons/model`, `commons/util` |
| `relays/` list-editing VMs (17 `*RelayListViewModel`) | ~20 | 2-3 `R.string` toasts each | `commons/viewmodels`; desktop hand-rolls all seven relay editors today |
| `wallet/WalletViewModel` (750 lines, zero android imports) | 1 | 19 `R.string` refs + `Amethyst` singleton | `commons/viewmodels` |
| `SearchBarViewModel` (435) | 1 | `Account` + `Route` (make route-building a callback) | `commons/viewmodels` (its `SearchBarState` is already there) |
| `chess/ChessViewModelNew` | 3 | trivial — **desktop's `DesktopChessViewModelNew.kt` is line-for-line the same class** | `commons/nip64Chess` |
| `ui/note/types/` kind renderers | ~89 | `AccountViewModel` threading (81 files) + strings; introduce a `NoteRenderContext`/callback bundle | `commons/ui/note` (22 files there prove the pattern) |
| `ReactionsRow` (2,602 lines) + `note/buttons/` | ~10 | 30 strings, 1 stray `Context` import | `commons/ui/note` — **desktop has zero reactions/zaps UI today** |
| `UsernameDisplay`, `NIP05VerificationDisplay`, `UserProfilePicture` | 3 | strings; merge avatar stack into commons `UserAvatar` | `commons/ui/note` |
| `RichTextViewer` family | ~8 | strings; commons `ui/richtext` exists and desktop already uses it — reconcile the app's fork | `commons/ui` |
| notifications state (`CardFeedContentState`, `CardFeedState`, `NotificationSummaryState`, `OpenPollsState`) | 6 | `Account`; commons `ui/notifications/CardFeedState` is the other half of a half-done move | `commons/ui/notifications` |
| 6 structurally-identical `*MetadataViewModel`s (lists, followPacks, bookmarkgroups, emojipacks, interestSets, nip28 channel) | 6 | one shared error-enum extraction (8 `R.string` + `Context` each) | `commons/viewmodels` |
| uploads core (`UploadOrchestrator` 525, `MediaCompressor`, `MetadataStripper`) | ~8 | `Uri`/`ContentResolver` → stream core; `Int` string-res errors → typed enum; promote commons' jvmMain orchestrator to jvmAndroid | `commons/service/upload` — today **two independent orchestrators** exist |
| `ZapPaymentHandler` (580) / `V4VPaymentHandler` (295) | 2 | split zap-split math + NWC/LNURL sequencing (shareable) from `Context`/string error surface | `commons/model/nip57Zaps` / `commons/onchain` |
| playback policy leaves (`SimultaneousPlaybackCalculator`, `VideoViewedPositionCache`, `AutoReplayLimiter`, `HlsLivenessCache`, `LowLatencyHlsStripper`, `websocket/Wss*`) | ~7 | extract from the 71-file media3 package | `commons/service/playback` |
| push logic (`PushWrapDecryptor`, `RegisterAccounts`) | 2 | none real — just entangled with FCM glue | `commons/service/push` |
| `napplet/NappletLiveSubscriptions`, `gateways/AccountIdentityReader`, `NappletManifestLookup` | 3 | `Account` / inject cache | `commons/napplet` |
| `ui/broadcast/` banners | 3 | strings + theme + `AccountViewModel`; logic already in `commons/service/broadcast` | `commons/service/broadcast/ui` |
| post composers (`ShortNotePostViewModel` 2,046, `*NewMessageViewModel`, HLS/music authoring) | ~20 | deep: uploads core + location + `StringProvider` + `Amethyst` singleton | last wave |

## STAY (correctly platform-native)

- **Navigation shell & screens**: `*Screen.kt`, `*TopBar.kt`, `New*Button.kt`,
  `INav`/`Route`/`RouteMaker`/`AppNavigation`, drawer/bottom-bar,
  `AccountScreen`/`AccountSessionManager`/`LoggedInPage`, `loggedOff/`,
  `settings/` screens (~480 files import INav/Route — by design).
- **Process/DI roots**: `Amethyst.kt`, `AppModules.kt`, `EncryptedStorage`,
  `LocalPreferences`, `DebugUtils`, `model/accountsCache`,
  `model/preferences/` (the one genuinely-Android model package: DataStore/
  Keystore actuals — but define their ports in commons).
- **Media & capture**: `service/playback/` (media3/MediaSession/PiP — desktop's
  player is a genuinely different implementation, not a port),
  `ui/actions/uploads` pickers/camera/voice, `creators/` capture files,
  Zoomable/PDF viewers, `GifVideoView`.
- **Android services**: notifications (channels/FCM/TileService), calendar
  (WorkManager), location (`LocationManager` — `LocationGeoHash` caches could
  move), tts, cast (SDK), nests foreground service, foreground/eventCache/
  priority/logging (Choreographer/StrictMode instrumentation), workouts'
  Health Connect half.
- **napplet broker & sandbox** (Messenger IPC, WebView, consent activities),
  `connectedApps/` (the shareable signer/nip46 halves are already in commons;
  what remains is DataStore actuals + activities), `favorites/` registries
  (models already extracted to `commons/favorites`).
- **Flavor sources** `src/play/` + `src/fdroid/` — they exist precisely to
  encode the Play-vs-FOSS split; only interfaces they implement may move.
- **`StringResourceCache`, `SafeImeInsets`, `NwcResponseMessages`** — the
  Android halves of i18n/IME bridges.

## Desktop duplication catalog (the "should" evidence)

| Desktop file | Hand-rolled copy of |
|---|---|
| `cache/DesktopLocalCache.kt` (1,173) | `model/LocalCache.kt` (3,980) |
| `model/DesktopIAccount.kt` (539) | the ~14% of `Account.kt` a second front end needs |
| `subscriptions/FilterBuilders.kt` (742) | ~34 `ui/screen/**/subassemblies/` filter fns |
| `subscriptions/SearchFilterFactory.kt` | `searchCommand/subassemblies/SearchPostsByText` (self-documented port) |
| `subscriptions/{FeedSubscription,ProfileSubscription,FilterDMs,ChessSubscription}.kt` | reqCommand watchers / gift-wrap / profile filters |
| `feeds/DesktopFeedFilters.kt` (404) + `DesktopFeedViewModel.kt` | `ui/screen/**/dal/` feed filters |
| `model/DesktopAccountRelays.kt` (250) | `model/serverList/` + `model/nip01UserMetadata/` relay states |
| `model/{DesktopHiddenUsersState,DesktopDmRelayState,BlossomServers}.kt` | `nip51Lists/HiddenUsersState`, `nip17Dms/*`, `nipB7Blossom/*` |
| `chess/DesktopChessViewModelNew.kt` (203) | `chess/ChessViewModelNew.kt` (215) — line-for-line |
| `service/namecoin/DesktopNamecoinNameService.kt` | `service/namecoin/NamecoinNameService.kt` (says so in its KDoc) |
| `service/images/Desktop{Base64,BlurHash,ThumbHash}Fetcher.kt` | `service/images/` fetchers |
| `network/DesktopHttpClient.kt` | `DualHttpClientManagerForRelays` (minus all interceptors — a live behavior gap) |
| `platform/PlatformColorScheme.kt` | `ui/theme/Theme.kt` palette |
| `ui/relay/*Editor.kt` (7 files) | `relays/*RelayListViewModel` edit logic |
| `ui/notifications/NotificationGroup.kt` | `notifications/` MultiSetCard grouping |
| `ui/thread/` (784) | threadview dal + UI |
| `ui/search/` (1,495) + `search/DesktopRelayUserSearchDelegate.kt` | `SearchBarViewModel` |
| `network/RelayConnectionManager.kt::RelayMetrics` | `speedLogger/` telemetry subset |
| Desktop has **no** reactions/zaps row, **no** crash reporting, **no** PoW persistence | gaps that sharing closes for free |

## Recommended sequence

1. **Wave 0 (mechanical, this branch's follow-ups):** delete the 13+ typealias
   shims; land Batches 1-5 above (~350 files) — no refactoring required.
2. **Wave 1 (three small edits, huge fan-out):** finish `EOSE.kt`;
   `IFeedTopNavFilter` → `ICacheProvider`; add KMP `LruCache` expect.
   Unlocks subassemblies (145), topNavFeeds (23), caches (5).
3. **Wave 2 (ports):** extract `TopFilter` + `ListBackupStore` from
   `AccountSettings`; move `LocalCache` behind its 3 tiny expects.
   Unlocks nip51Lists, the nipNN state packages, serverList/userMetadata,
   `ui/screen/**/dal/` — and deletes `DesktopLocalCache`.
4. **Wave 3 (strings):** build the Compose-resources `stringRes` twin, then
   migrate strings feature-by-feature with their composables (theme, layouts,
   feed shell first — they need no strings at all and can go in Wave 0).
5. **Wave 4 (decompose the god-objects):** grow `IAccount` and migrate
   `Account`'s six concern groups (relay-set derivation, nip51 state, the
   30-feed-type × 2 field explosion → a `Map<FeedType, FollowListPair>`,
   relay-auth policy, buzz state, the 218 action methods → `commons/actions`)
   until app-side `Account` is only composition wiring. Shrink
   `AccountViewModel` the same way. This is what unblocks the remaining
   ~400 `datasource/` + `ui/note/types/` files.
6. **Wave 5 (deep platform seams):** uploads core (`Uri`→stream + typed
   errors), zap payment handlers, playback policy extraction, post composers.

## jvmAndroid audit: what the migration parked there, and what can promote to commonMain

Everything below landed in `commons/src/jvmAndroid` (or `androidMain`) during
Waves 0-1. For each file: the exact JVM-only API pinning it there, and whether
the repo already has a KMP replacement. Replacements referenced:
`KmpLock`/`withLock` (commons/util), `TimeUtils.nowMillis()` (quartz),
`kotlin.concurrent.atomics` (stdlib KMP, already used in quartz BLE),
`quartz/utils/concurrent/ConcurrentMap` + `ConcurrentSet` (expect/actual),
`RandomInstance` (quartz secure random), `kotlin.time.TimeSource.Monotonic`
(used in quartz RelayProber), okio (KMP), kotlinx-serialization +
`commons/util/JsonTreeUtils`, and the `PlatformImage` expect (android + jvm +
ios actuals in commons/blurhash).

### Tier 1 — promotable to commonMain by moving the file (no code change)

> Executed on this branch: the 7 auth files, `EncryptionKeyCache` and
> `HttpClientEnvironment` now live in commonMain (verified against JVM, iOS
> and `verifyKmpPurity`). `NWCPaymentWatcherSubAssembler` turned out to use
> `NWCPaymentQueryState` from its pinned same-package sibling — reclassified
> to Tier 2.

| File | JVM pin | Note |
|---|---|---|
| `relayClient/auth/` `RelayAuthPermissionCache`, `RelayAuthPermissionLedger`, `RelayAuthSessionGrants`, `InMemoryRelayAuthPermissionStore`, `RelayAuthVenues`, `RelayAuthPurposeDeriver`, `RelayAuthFirstParty` (7 files) | none | Zero JVM imports; no deps on the two pinned siblings. Wave 0b parked the whole group conservatively. |
| `relayClient/nip47WalletConnect/NWCPaymentWatcherSubAssembler` | none directly — but uses `NWCPaymentQueryState`, declared *same-package* in the pinned assembler | Reclassified to Tier 2: moves with `NWCPaymentFilterAssembler`. |
| `service/http/EncryptionKeyCache` | none | androidx.collection LruCache is commonMain-safe. |
| `service/http/HttpClientEnvironment` | none | Plain object; conceptually pairs with the factories but nothing pins it. |

### Tier 2 — promotable with mechanical one-line swaps (replacement exists in-repo)

> Executed on this branch, with a concurrency review per file rather than
> blind swaps. Locks that guarded nothing were removed instead of ported:
> `RelayAuthPromptBus` is now lock-free (`ConcurrentMap.getOrPut` +
> identity-check ownership), and `NappletLaunchRegistry` dropped all three
> `@Synchronized` by replacing its JVM-only access-ordered LinkedHashMap
> with `androidx.collection.LruCache` (internally synchronized,
> access-ordered cap — same semantics). Locks that protect real multi-field
> invariants stayed as `KmpLock` deliberately: `ChatDeliveryTracker` (its
> hot OK path was already lock-free via volatile immutable maps; CAS-ing
> the three coordinated structures would copy maps per write — GC churn for
> zero contention win), `NWCPaymentFilterAssembler` (debounce set + job
> swapped atomically), `NappletNotificationStore` (per-coordinate ordered
> buckets), `DeferredDeleteFileSystem` (pending-set membership must decide
> deletion atomically). `java.util.concurrent` atomics/CHM became stdlib
> `kotlin.concurrent.atomics` + quartz `ConcurrentMap` (which grew
> `putIfAbsent`/`remove(key,value)`/`clear` for the leader-follower caches).
> Extra pins found while executing: speedLogger used `kotlin.concurrent.timer`
> and `::class.java` (the tick now runs on a cancellable coroutine scope —
> the old daemon timer outlived `destroy()`); `OnionLocationCache`,
> `BlossomReadAuthTokenProvider`, `DmRelayDiagnosticsLogger`,
> `NappletNotificationStore` swapped to `TimeUtils.nowMillis()`.
> **Bonus promote:** `service/upload/BlossomAuth` (quartz-only imports).
> **Two reclassified to Tier 4:** `NappletIdentityWatch` (depends on
> `NappletProtocolJson`, pinned by `java.util.Base64`) and
> `NamecoinNameService` (quartz `ElectrumXClient` is jvmAndroid).

| File | JVM pin | KMP replacement |
|---|---|---|
| `relayClient/auth/RelayAuthPromptBus` | `synchronized()` | `KmpLock.withLock` |
| `relayClient/auth/ListWithUniqueSetCache` | `AtomicReference` | `kotlin.concurrent.atomics.AtomicReference` |
| `relayClient/chatDelivery/ChatDeliveryTracker` | `@Volatile` + `synchronized()` ×9 | `KmpLock` + stdlib atomics |
| `relayClient/speedLogger/FrameStat`, `KindGroup` | `AtomicInteger` | `kotlin.concurrent.atomics.AtomicInt` |
| `relayClient/speedLogger/RelaySpeedLogger` | none (blocked by the two above) | moves with them |
| `relayClient/diagnostics/DmRelayDiagnosticsLogger` | `System.currentTimeMillis` | `TimeUtils.nowMillis()` |
| `relayClient/nip47WalletConnect/NWCPaymentFilterAssembler` | `@Volatile` + `synchronized()` | `KmpLock` + atomics |
| `model/nip47WalletConnect/NwcInfoCache` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `marmot/InMemoryMlsGroupStateStore` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `napplet/NappletIdentityWatch` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `napplet/NappletLaunchRegistry` | `SecureRandom`, `@Synchronized` | `RandomInstance.bytes()`, `KmpLock` |
| `napplet/NappletNotificationStore` | `ConcurrentHashMap`, `AtomicLong`, `synchronized()`, millis | `ConcurrentMap` + atomics + `KmpLock` + `TimeUtils` |
| `service/namecoin/NamecoinNameService` | `@Volatile` ×1 | atomics (verify the injected resolver ifaces are commonMain) |
| `service/http/BlossomReadAuthTokenProvider` | `ConcurrentHashMap`, millis | `ConcurrentMap` + `TimeUtils.nowMillis()` |
| `service/http/OnionLocationCache` | `ConcurrentHashMap`, `TimeUnit`, millis | `ConcurrentMap` + plain-ms TTL + `TimeUtils` |
| `service/image/DeferredDeleteFileSystem` | `java.io.IOException`, `synchronized()` | already okio-based → `okio.IOException` + `KmpLock` |

### Tier 3 — promotable with a small refactor

| File | JVM pin | Path |
|---|---|---|
| `relayClient/diagnostics/BootRelayDiagnostics` | `kotlin.concurrent.thread`, `ConcurrentHashMap`, atomics, millis | swap thread → coroutine launch; rest is Tier-2 swaps |
| `actions/BuzzInviteMinter` | Jackson `ObjectMapper`, OkHttp call | Jackson → kotlinx-serialization (`JsonTreeUtils` landed in commonMain); inject a `suspend (url, body) -> String` fetch or Ktor client |
| `podcasts/PodcastRemoteContent` | OkHttp call | inject a fetch function or Ktor client |
| `[androidMain]` `Base64Fetcher`, `BlurHashFetcher`, `ThumbHashFetcher` | android `Bitmap` bridge (`toAndroidBitmap`) | coil3 core is KMP and `PlatformImage` has android/jvm/ios actuals — add an expect `PlatformImage → coil3.Image` bridge; deletes desktop's three clone fetchers |

### Tier 4 — blocked by a dependency that must move first

| File | Blocker |
|---|---|
| `scheduledposts/ScheduledPostWorkGate` | `ScheduledPostStore` (pre-existing jvmAndroid: Jackson + `java.io.File`) — store needs kotlinx-serialization + okio first |
| `model/cache/LargeSoftCacheAddressExt` | `LargeSoftCache` (`WeakReference`, `ConcurrentSkipListMap`) — soft/weak-reference caching has no KMP equivalent; a native actual would change eviction semantics. Long-term expect/actual candidate. |
| `model/nip03Timestamp/BitcoinExplorerEndpoint`, `OtsSettings`, `TorAwareOkHttpOtsResolverBuilder` | quartz's own OTS explorer/calendar clients are OkHttp-only (`quartz…nip03Timestamp.okhttp.*`) — quartz needs a KMP (Ktor) OTS transport before these can follow |

### Tier 5 — stays jvmAndroid by design (typed against OkHttp / java.net)

`service/http/`: `IHttpClientManager`, `IRoleBasedHttpClientBuilder`,
`DualHttpClientManager(+ForRelays)`, `OkHttpClientFactory(+ForRelays)`,
`Empty/SingleRoleBasedHttpClientBuilder`, `ProxiedSocketFactory`, and the
interceptor/listener set (`BlossomReadAuth`, `EncryptedBlob`,
`LocalBlossomCacheRedirect`, `OnionLocation`, `OnionUrlRewrite`,
`DefaultContentType`, `Logging`, `DnsInvalidatingEventListener`,
`MediaCallEventListener`, `OkHttpDebugLogging`), plus
`service/image/BlossomReadAuthFetcher` (coil-network-okhttp).

These are the OkHttp engine itself: `okhttp3.Interceptor`/`EventListener`
types, `java.net.Proxy`/`Socket`. The KMP path is a Ktor-client rewrite
(Ktor 3.5.2 is already in the catalog, server-side only today), but quartz's
relay websockets are equally OkHttp-bound on jvmAndroid, so an iOS transport
story has to land in quartz first; rewriting commons alone buys nothing.
Revisit when quartz grows a non-JVM socket/HTTP engine.

**Score:** of the 59 files parked, 11 move with zero code change, 17 with
one-line in-repo swaps, 6 with small refactors, 5 wait on a dependency, and
20 are the OkHttp engine that should stay until quartz has a KMP transport.

## Corrections to `commons/ARCHITECTURE.md` found during the sweep

- §2 is stale: `commons/service` also holds `pow/`, `georelay/`, `broadcast/`;
  several service-ish concerns live as top-level packages
  (`scheduledposts`, `cashu`, `podcasts`, `audio`, `connectedApps`,
  `favorites`, `napplet`, `browser`) — the `service/` vs top-level split
  deserves a stated rule.
- `commons/model/nip02FollowList` (singular) vs app `model/nip02FollowLists`
  (plural) — reconcile on the quartz name when merging.

## Session handoff — state as of 2026-09-01 (PR #4025)

Everything below is the live state for whoever picks this up next; the
sections above are the original audit and stay as reference.

### What is DONE and pushed (branch `claude/amethyst-commons-migration-hm8vgm`)

- **Waves 0 + 1** (~145 files moved to `:commons`, 12 typealias shims
  deleted, importers rewritten repo-wide). See the batch tables above.
- **jvmAndroid promotion Tiers 1–3** (the 5-tier table above, annotated
  per-tier): ~28 more files promoted to commonMain, incl. the diagnostics
  stack (BootRelayDiagnostics, RelaySpeedLogger, DmRelayDiagnosticsLogger),
  the image fetchers (Base64/BlurHash/ThumbHash via the new
  `CoilImageBridge` expect/actual, three Desktop clones deleted), and the
  Tier-2 concurrency review (2 locks removed, 4 kept as `KmpLock` with
  documented invariants; quartz `ConcurrentMap` gained
  `putIfAbsent`/`remove(k,v)`/`clear`).
- **Merged main twice**; second merge brought the #4026 Shorts rename, and
  commit `5cf47f6f` dropped the 30 orphaned `route_video`/`new_short`
  translation entries (15 locales × 2 keys) that were failing
  `:amethyst:lintFdroidBenchmark` on main and every branch. Main is still
  red until that cleanup lands there — cherry-picking `5cf47f6f` fixes it.
- **CI fully green** on head `5cf47f6f`; PR #4025 mergeable_state clean.

### Audit findings (2026-09-01 review) — 5 of 6 FIXED on the branch

Findings 1, 2, 3, 5 and 6 below are fixed and covered where testable
(`TopFilterSerialNameTest` in commons commonTest pins the pre-move serial
names and legacy-JSON decoding on JVM **and** iOS). The one still open:

- **Baseline profile stale (finding 4)** — `baseline-prof.txt` has ~251
  rules naming pre-move classes; regenerating requires the
  `:baselineprofile` macrobenchmark on a device, which this environment
  cannot run. Regenerate before or shortly after release.

Original findings, for reference:

1. **[ship-blocker] `TopFilter` serial names changed** — the move to
   `commons.model.topNavFeeds` changed every subclass's kotlinx default
   serial name; persisted per-tab feed-filter prefs (written by
   `JsonMapper.toJson`, read via `parseTopFilterOrDefault` which swallows
   decode errors) silently reset for every user on upgrade. Fix: add
   `@SerialName("com.vitorpamplona.amethyst.model.TopFilter.…")` (old FQNs)
   to each subclass.
2. **`HttpClientEnvironment.isEmulator` set too late** — `Amethyst.onCreate`
   builds `AppModules` (which eagerly constructs both OkHttp factories and
   their dispatchers) before setting the flag; the emulator branch is dead.
   Set the flag before `AppModules(this)`, or read it lazily.
3. **`@Contextual Address` in `TopFilter` has no iOS serializer** — the
   nativeMain `Address` actual is not `@Serializable` and JsonMapper
   registers no contextual serializer; serializing address-carrying filters
   throws on iOS. Annotate the native actual or register a serializer.
4. **Baseline profile stale** — `baseline-prof.txt` has ~251 rules naming
   pre-move classes; regenerate via `:baselineprofile` (cold-start wins
   regress until then).
5. **`NappletLaunchRegistry` in commons breaches the documented napplet
   sandbox boundary** (CLAUDE.md says the broker-side registry stays in
   `:amethyst` so `:nappletHost` cannot import it). Move it back or update
   the boundary doc + add a guard.
6. **`PlatformImage.toSkiaBitmap()` duplicated** verbatim in
   `CoilImageBridge.jvm.kt` and `.ios.kt` — hoist to a shared Skiko source
   set.

### Next work, in recommended order (needs maintainer go-ahead per item)

- **Tier 4 unlocks** (small, mechanical): `NappletProtocolJson`
  `java.util.Base64` → `kotlin.io.encoding.Base64` (frees
  `NappletIdentityWatch`); `ScheduledPostStore` Jackson+`java.io.File` →
  kotlinx-serialization+okio (frees `ScheduledPostWorkGate`).
  `LargeSoftCache` stays parked (needs a WeakReference expect/actual).
- **Wave 2: LocalCache move-group** (~200 files behind it) — **blocked on a
  design decision**: `CachePruner`/`CacheSearch` call
  `Account.isFollowing(...)`; either `IAccount` grows it (DesktopIAccount
  must implement) or they take a narrower interface (recommended).
- **Wave 3: strings bridge** — **BUILT (2026-09-01)**. The pieces:
  - `commons/ui/StringRes.kt` (commonMain): `stringRes(StringResource)`,
    formatted + plural variants, `loadStringRes`/`loadPluralStringRes` for
    non-composable scopes. Thin delegates over compose-resources — no extra
    cache (the library caches parsed locale files process-wide).
  - App-side `ui/StringResourceCache.kt` gained `stringRes(StringResource)`
    overloads delegating to the bridge, so ONE `stringRes` import serves
    mixed files: migrating a key is just `R.string.x` → `Res.string.x`
    (+ the two `commons.resources` imports). No aliasing, no churn.
  - `tools/strings-migrate/migrate.py <keys…>`: moves keys from app res to
    commons composeResources across all 57 locales byte-for-byte (Crowdin
    covers both trees with the same mapping — see crowdin.yml). It refuses
    keys with bare `%s`/`%d` (compose-resources needs positional `%1$s`).
  - Exemplar shipped: `profile_banner` (the ui/layouts cluster's only
    string blocker) migrated + all 7 call-site files repointed.
  - **Bulk migration executed (2026-09-01, commit 7c6a18ee):** all 2,565
    mechanically-safe keys (composable-only call sites, no XML refs, only
    `%N$s`/`%N$d` args, no markup) moved to commons; 568 app files
    repointed. The app keeps 1,866 keys that are genuinely Android-bound:
    ctx-based call sites (adapt to `loadStringRes` to free them),
    `Int`-typed id storage (maps/`when`s over resource ids), `@string/`
    XML references, and exotic format specifiers.
  - **Remaining Wave-3 work:** free the ctx-based keys by adapting call
    sites, then move the now-unblocked composables. Note the layouts cluster ALSO needs theme
    constants (`DividerThickness`, `Size55Modifier`, …) hoisted from app
    `ui/theme/Shape.kt` into `commons/ui/theme/Sizes.kt`, plus
    `painterRes`/`TimeAgo`/`NewItemsBubble` decisions — the audit's
    "strings-only" tally under-counted transitive deps.
- **Wave 4: Account/AccountViewModel decomposition** — long-tail.

### Environment notes for the next session (hard-won)

- Run gradle tests and pushes with `LC_ALL=C.UTF-8 LANG=C.UTF-8` (a POSIX
  locale breaks an em-dash test-report filename).
- One gradle invocation at a time; concurrent runs die on the project lock.
- Pre-push hook runs `:quartz:jvmTest :commons:jvmTest :nestsClient:jvmTest
  :quic:jvmTest :amethyst:testPlayDebugUnitTest :cli:test` (not geode); a
  PreToolUse hook blocks pushes when spotlessApply reformatted files —
  commit first, then push.
- `:commons:compileKotlinIosArm64` is the cheap local gate for iOS breakage
  (CI's test-quartz-ios compiles commons for iosSimulatorArm64). If the
  Kotlin/Native toolchain corrupts (dangling `liblto_plugin.so` symlink),
  delete the extracted dirs under `/root/.konan/dependencies` and let
  gradle re-extract.
- stdlib atomics have no `incrementAndFetch()` here — use `addAndFetch(1)`;
  `withLock {}` can't assign outer `val`s — restructure to lambda-return.
