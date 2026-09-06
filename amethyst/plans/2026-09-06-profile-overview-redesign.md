# Profile redesign: overview screen + one screen per former tab

_Status: proposal (design canvas + this plan). Nothing implemented yet._

Design canvas (current vs proposed, Android + Desktop):
<https://claude.ai/code/artifact/5d803271-feb4-40f0-b37f-b9826199394e>

## 1. Goal

Turn `Route.Profile` from a header + 12-tab pager into a single scrolling
**overview**: an identity header, one primary action, tappable stats, and an
**activity index** where every former tab is a row with a live summary.
Every row opens its **own screen** (own route, own ViewModel, own relay
subscription). There is no shared tab container and no chip strip linking
siblings; a screen is a plain feed under a compact bar.

Non-goals: changing what any feed contains (filters stay), changing the edit
profile flow, changing the badges / app-recommendation management screens
(`Route.ProfileBadges`, `Route.ProfileAppRecommendations` already exist as
separate screens and stay).

## 2. Inventory of what exists today and what happens to it

### Android `ui/screen/loggedIn/profile/`

| File | Today | Fate |
| --- | --- | --- |
| `ProfileScreen.kt` | `ProfileScreen` → `PrepareViewModels` (13 ViewModels) → `RenderSurface` (nested-scroll collapsing header) → `RenderScreen` (`ProfileHeader` + `SecondaryScrollableTabRow` + `HorizontalPager`) | **Rewrite** as `ProfileOverviewScreen`. Delete `RenderSurface`, `RenderScreen`, `ProfileTab`, `ViewedTabRef`, `CreateAndRenderTabs`, `CreateAndRenderPages`, `UpdateThreadsAndRepliesWhenBlockUnblock` (moves to the Notes/Replies screens). |
| `header/ProfileHeader.kt` | banner + 100dp avatar + `ProfileActions` + `DrawAdditionalInfo` + divider | **Rewrite** (§4). `ZoomableUserPicture`, `ProfilePictureWithUploadOverlay`, `ProfilePictureUploadButton` are kept as-is. |
| `header/DrawBanner.kt` | 150dp banner, zoom + long-press copy | Keep; height param becomes 140dp. |
| `header/ProfileActions.kt` | Message, `PaymentButton`, `Bolt12PayButton`, Edit, Follow/Unfollow + `ListButton` | **Rewrite**: Follow (filled) or Edit, Message, Zap. |
| `header/DrawAdditionalInfo.kt` | nickname card, name, `@name`, npub row, nprofile row, last seen, NIP-05, website, identities, payment chips, badges, about, app recommendations | **Split**: identity block stays in header (§4); badges / apps / payment become overview rows (§5). `DisplayLastSeen`, `DisplayNip05ProfileStatus`, `getIdentityClaimIcon/Description` kept. |
| `header/UserNicknameCard.kt` | `OutlinedCard` above the name | **Rewrite** as `UserNicknameChip` (same `EditNicknameDialog` on tap). |
| `header/ProfilePaymentRailChips.kt`, `header/DisplayPaymentTargets.kt` | chip `FlowRow` in header | Keep the composables; they render inside the **Payment methods sheet** (§5) instead of the header. `ProfilePaymentChip` / `PaymentTargetPill` unchanged. |
| `header/PaymentButton.kt`, `header/Bolt12PayButton.kt` | tonal 50dp buttons + dialogs | Remove the buttons; keep `PaymentTargetsDialog`, `Bolt12OffersDialog`, `Bolt12NwcAmountDialog` reachable from the Payment methods sheet. |
| `header/MessageButton.kt`, `header/EditButton.kt`, `header/DisplayFollowUnfollowButton.kt`, `FollowButtons.kt` | tonal buttons, split pill | Restyle: `FollowButton` → filled `Button`; `MessageButton` → 40dp `OutlinedIconButton`; `ListButton` removed from the row (its route `Route.PeopleListManagement` moves to the `⋮` menu). |
| `header/ProfileTopBar.kt` | opaque 30dp discs on the banner | Restyle to 36dp translucent scrims; add Share action. |
| `header/UserProfileDropDownMenu.kt` | copy id, share, edit nickname, block, reports | Add: Copy hex, Copy nprofile, Show QR, Add to list, Payment targets, Bolt12 offers. |
| `header/badges/DisplayBadges.kt` | header section + `AllBadgesSheet` | `RenderProfileBadgeStrip` no longer used in header; `AllBadgesSheet`, `BadgeThumb`, `LoadDefinitionForAward` reused by the Badges row / screen. |
| `header/apps/DisplayAppRecommendations.kt` | header section | Section removed from header; `AppRecommendationChip` reused in the Apps & sites screen header. |
| `header/identity/UserExternalIdentitiesViewModel.kt` | kind 10011 identities | Keep. |
| `newthreads/TabNotesNewThreads.kt` + `dal/` | pinned + notes feed | Becomes `ProfileNotesScreen`. Filter + ViewModel unchanged. |
| `conversations/TabNotesConversations.kt` + `dal/` | replies feed | Becomes `ProfileRepliesScreen`. |
| `mutual/TabMutualConversations.kt` + `dal/` | "Yours" | Becomes `ProfileMutualScreen` (label "Between you two"). |
| `gallery/TabGallery.kt` + `ProfileGalleryFeed.kt` + `dal/` | media grid | Becomes `ProfileMediaScreen`. |
| `apps/TabApps.kt` + `dal/` | nsites / napplets published | Becomes `ProfileAppsScreen`; app recommendations render as a header block on the same screen. |
| `follows/TabFollows.kt`, `FollowTabHeader.kt` + `dal/` | user list + count | Becomes `ProfileFollowsScreen`; count feeds the stats row. |
| `followers/TabFollowers.kt`, `FollowersTabHeader.kt` + `dal/` | user list + count (NIP-85 or local) | Becomes `ProfileFollowersScreen`; count feeds the stats row. |
| `zaps/TabReceivedZaps.kt`, `ZapTabHeader.kt`, `ZapNoteCompose.kt` + `dal/` | zap totals per user | Becomes `ProfileZapsScreen`; `totalReceivedZaps` + top 3 feed the row. |
| `bookmarks/TabBookmarks.kt`, `BookmarkTabHeader.kt` + `dal/` | bookmark feed + count | Becomes `ProfileBookmarksScreen`. |
| `hashtags/TabFollowedTags.kt`, `FollowedTagsTabHeader.kt` | hashtag list | Becomes `ProfileTagsScreen`. |
| `pinnedNotes/TabPinnedNotes.kt`, `PinnedNotesTabHeader.kt` + `dal/` | (not in the tab list; used by Notes) | Keep `dal/`; delete the unused `TabPinnedNotes` / header. |
| `reports/TabReports.kt`, `ReportsTabHeader.kt` + `dal/` | report notes | Becomes `ProfileReportsScreen`. |
| `relays/TabRelays.kt`, `RelaysTabHeader.kt`, `RelayFeedView.kt`, `RelayFeedViewModel.kt` | relay list with manual lifecycle | Becomes `ProfileRelaysScreen`; the `DisposableEffect` lifecycle dance stays inside it. |
| `payment/ProfileClinkOfferResolver.kt` | CLINK offer | Keep. |

### Shared relay layer `commons/relayClient/profile/`

| File | Today | Fate |
| --- | --- | --- |
| `UserProfileFilterAssembler.kt` | one `ComposeSubscriptionManager<UserProfileQueryState>` grouping Metadata + Posts + Media + Followers + Zaps sub-assemblers | **Split** into `UserProfileOverviewFilterAssembler` (Metadata sub-assembler only, plus a new small-limit `UserProfilePreviewFilterSubAssembler`, §6) and per-screen assemblers. |
| `UserProfileQueryState` | `(user, loadFollowers, loadZapsReceived)` | Becomes `ProfileQueryState(user)`; the two booleans disappear (each screen subscribes only to its own thing). |
| `UserProfileFilterAssemblerSubscription.kt` | one composable | One composable per assembler (`UserProfileOverviewSubscription`, `UserProfilePostsSubscription`, `UserProfileMediaSubscription`, `UserProfileFollowersSubscription`, `UserProfileZapsSubscription`). |
| `UserProfileMetadataFilterSubAssembler.kt` (+ `FilterUserProfileMetadata`, `FilterUserProfileLists`, `FilterUserProfileLastSeen`) | kind 0/10002/3/8/30008, the list kinds (10003/30001/30000/30003/10015/31989/NIP-A3), and a no-kind "last seen" filter | Keep; this is the overview's subscription. |
| `UserProfilePostsFilterSubAssembler.kt` (+ `FilterUserProfilePosts`, limit 500 ×2 filters per relay) | notes, replies, apps kinds | Keep; used by Notes, Replies, Mutual screens. |
| `UserProfileMediaFilterSubAssembler.kt` (+ `FilterUserProfileMedia`) | media kinds | Keep; Media screen. |
| `UserProfileFollowersFilterSubAssembler.kt` (+ `FilterUserProfileFollowers`) | kind 3 `p`-tagging user | Keep; Followers screen. |
| `UserProfileZapsFilterSubAssembler.kt` (+ `FilterUserProfileZapReceived`, limit 1000) | 9735 / onchain / bolt12 zaps | Keep; Zaps screen. |
| `RelaySubscriptionsCoordinator.profile` (Android, `service/relayClient/reqCommand/`) | one instance | Becomes six instances (`profileOverview`, `profilePosts`, `profileMedia`, `profileFollowers`, `profileZaps`; reports already load via `FilterReportsToKey` in `relayClient/user/watchers`). |

### Navigation (Android)

| Symbol | Today | Fate |
| --- | --- | --- |
| `Route.Profile(id)` (`navigation/routes/Routes.kt:546`) | the tabbed screen | Stays the overview. Every existing caller (`RouteMaker.routeFor(user)`, `authorRouteFor`, `NavBarItem` bottom-bar Profile tab, `MainActivity` npub/nprofile deep links) keeps working unchanged. |
| `AppNavigation.kt:637` `composableFromEndArgs<Route.Profile>` | | Same entry; add 13 sibling registrations (§7). |

### Desktop

| Symbol | Today | Fate |
| --- | --- | --- |
| `desktop/ui/UserProfileScreen.kt` (1995 lines) | header row + card + 11 tabs + inline tab bodies, all subscriptions declared inline | **Split** (§8). |
| `desktop/ui/profile/ProfileInfoCard.kt`, `ProfileZapRow.kt`, `RelayRowCard.kt`, `GalleryTab.kt` | | Keep; reused by the new screens. |
| `DeckColumnType.Profile`, `DesktopScreen.UserProfile` (pushed via `navState.push`) | | Keep; add `DesktopScreen.ProfileSection(pubkey, section)` (§8). |
| `desktop/feeds/DesktopProfileFeedFilter` (+ `jvmTest/.../DesktopProfileFeedFilterTest`) | notes / replies predicate | Keep; test unchanged. |

### Settings

`UiSettings.showProfileZapReceivedFeed` / `showProfileFollowersFeed` gated
tabs and their subscriptions. They now gate the **rows** (and nothing loads
for a hidden row). `showProfileBadges` / `showProfileAppRecommendations` gate
the Badges and Apps rows. `ProfileUiSettingsScreen` copy changes from
"feed" / "tab" to "section". No new setting.

## 3. Information architecture

```
Route.Profile(pubkey)                         overview (this plan §4–§5)
├─ Route.ProfileNotes(pubkey)                 pinned + notes feed
├─ Route.ProfileReplies(pubkey)               replies feed
├─ Route.ProfileMedia(pubkey)                 media grid
├─ Route.ProfileZaps(pubkey)                  zaps received, per zapper
├─ Route.ProfileMutual(pubkey)                "Between you two"
├─ Route.ProfileBookmarks(pubkey)             public bookmarks feed
├─ Route.ProfileTags(pubkey)                  followed hashtags
├─ Route.ProfileBadgesOf(pubkey)              accepted badges (list; own profile links to Route.ProfileBadges to manage)
├─ Route.ProfileApps(pubkey)                  app recommendations + published nsites/napplets
├─ Route.ProfileFollows(pubkey)               following list
├─ Route.ProfileFollowers(pubkey)             followers list
├─ Route.ProfileRelays(pubkey)                relay list
└─ Route.ProfileReports(pubkey)               report notes
   Payment methods                            bottom sheet, not a route (§5)
```

All 13 are `@Serializable data class X(val id: String) : Route()` next to
`Route.Profile` and registered with `composableFromEndArgs` right after it in
`AppNavigation.kt`. Back from any of them returns to the overview.

## 4. Header spec (Android; Desktop mirrors it in a column)

Top to bottom, inside `ProfileHeader`:

1. **Banner** 140dp (`DrawBanner`). `ProfileTopBar` draws back / share / `⋮`
   as 36dp circles with `Color.Black.copy(alpha = 0.32f)` fill and white
   icons, over the banner, transparent container.
2. **Avatar row**: 88dp `ProfilePictureWithUploadOverlay` with a 3dp ring in
   `colorScheme.background` (replace `LightProfilePictureBorder`'s black
   ring; dark theme already uses the background), overlapping the banner by
   44dp. Right side, bottom-aligned, `Arrangement.spacedBy(8.dp)`:
   - Message: 40dp `OutlinedIconButton`, `ic_dm`.
   - Zap: 40dp `OutlinedIconButton`, `MaterialSymbols.Bolt` tinted
     `BitcoinOrange`; opens the Payment methods sheet (§5). Hidden when the
     profile has no rail at all.
   - Follow / Unfollow / Follow back: filled `Button`, `ButtonBorder` shape,
     40dp tall. Own profile: `OutlinedButton` "Edit profile" →
     `Route.EditProfile`. Hidden user: `ShowUserButton` as today.
3. **Identity**: `Column(verticalArrangement = spacedBy(6.dp))`
   - Name line: `CreateTextWithEmoji` 22sp bold + pronouns 14sp
     `placeholderText` + `DrawPlayName`.
   - Handle line, 14sp `onSurfaceVariant`: NIP-05 (`ObserveAndRenderNIP05VerifiedSymbol`
     + `DisplayNip05ProfileStatus` text) when verified, else `@name` when
     present; then ` · ` + `DisplayLastSeen` ("Active 2h ago").
   - `UserNicknameChip` (only when the account has a petname/summary):
     28dp `secondaryContainer` pill, lock icon, `petName · summary`,
     tap → `EditNicknameDialog`.
   - About: `TranslatableRichTextViewer` as today, 15sp.
   - Meta `FlowRow(horizontalArrangement = spacedBy(16.dp), verticalArrangement = spacedBy(6.dp))`:
     website (Link icon, `LongPressCopyText`), one item per NIP-39 identity
     (existing icon + `identity.identity`). No separate rows.
   - Key row, `FlowRow(spacedBy(6.dp))` of two 32dp `surfaceContainer`
     pills plus a QR button. Both identifiers stay one tap away because they
     serve different jobs: **npub** (`pubkeyDisplayHex()` + copy of
     `pubkeyNpub()`) is what friends paste into apps and sites that only take
     an npub when setting up an account; **nprofile** (`toNProfile()`
     shortened with `toShortDisplay(6)` + copy of the full value) carries the
     relay hints, so it is the better one to share and the one the QR
     (`Route.QRDisplay`) encodes. Only the raw **hex** leaves the header and
     joins the `⋮` menu as `Copy hex`.
4. **Stats row**: three `TextButton`-like tappable groups, 16sp bold number +
   13sp label: Following (`UserProfileFollowsUserFeedViewModel.followCount`)
   → `Route.ProfileFollows`; Followers (`observeUserContactCardsFollowerCount`,
   falling back to the local `followerCount` exactly as `FollowersTabHeader`
   does) → `Route.ProfileFollowers`; Relays (size of the NIP-65 list) →
   `Route.ProfileRelays`. Gated rows hide their stat.
5. `HorizontalDivider`.

Removed from the header entirely: the hex row, per-identity rows,
payment chip `FlowRow`, Badges section, Apps section, the wallet / Bolt12 /
List buttons.

## 5. Overview rows (the activity index)

One shared composable, `ProfileSectionRow(icon, title, summary, trailing?,
preview?, onClick)`: 56dp min height, 36dp `surfaceContainer` rounded
leading icon, 15sp/500 title, 13sp `onSurfaceVariant` summary (1 line,
ellipsis), chevron. `preview` renders below, indented 64dp.

| Row | Summary (strings) | Preview | Data | Opens | Hidden when |
| --- | --- | --- | --- | --- | --- |
| Notes | `profile_row_notes_summary`: "N notes · P pinned · last %s" (N only when a NIP-45 count is available, else "P pinned · last %s") | pinned note (first of `UserProfilePinnedNotesFeedFilter`) + latest note (first of `UserProfileNewThreadFeedFilter`), compact text-only cards | pinned filter, threads filter, `lastCreatedAt` | `ProfileNotes` | never |
| Replies | "last reply %s" (+ NIP-45 count when available) | none | `UserProfileConversationsFeedFilter` first item | `ProfileReplies` | never |
| Media | "N photos and videos" (local count) | 4 × 96dp thumbs, horizontal, `LazyRow` | `UserProfileGalleryFeedFilter` take 4 | `ProfileMedia` | never |
| Zaps received | "%s sats from N people" | 3 stacked 24dp avatars | `UserProfileZapsViewModel.totalReceivedZaps`, `receivedZapAmountsByUser.take(3)` | `ProfileZaps` | `!showProfileZapReceivedFeed` |
| Between you two | "N of your notes mention X" | none | `UserProfileMutualFeedFilter` size | `ProfileMutual` | own profile |
| Bookmarks | "N public bookmarks" | none | `observeUserBookmarks` size (as `BookmarkTabHeader`) | `ProfileBookmarks` | 0 |
| Followed tags | "N hashtags" | first 3 chips | `observeUserTagFollows` | `ProfileTags` | 0 |
| Badges | "N accepted" | 3 × 28dp `BadgeThumb` + `+N` | flow from `DisplayBadges.WatchAndRenderBadgeList` | `AllBadgesSheet` (tap) / `Route.ProfileBadges` (own, settings icon) | 0 or `!showProfileBadges` |
| Apps & sites | "Recommends A, B, C · N published sites" | none | `UserAppRecommendationsFeedViewModel` + `UserProfileAppsFeedFilter` | `ProfileApps` | both empty and not own profile; `!showProfileAppRecommendations` hides the recommendation half |
| Payment methods | "Lightning · CLINK · On-chain · Cashu · +N more" | none | the same inputs `DisplayPaymentRailChips` computes | **Payment methods sheet**: `ModalBottomSheet` containing `RailAndTargetChips` + Bolt12 offers rows | no rail |
| Relays | (stats row instead; row exists only on Desktop tiles) | | | | |
| Reports | "N reports from people you follow" (local count, `ReportsTabHeader` logic) | none, warning-tinted icon | `UserProfileReportFeedViewModel.followerCount` | `ProfileReports` | 0 |

The **Zap** button in the header opens the same Payment methods sheet.

### `ProfileOverviewViewModel` (commons, `viewmodels/profile/`)

CLI-safe (no Compose UI), constructed with `(user: User, account:
IAccount-equivalent, cache: ICacheProvider)`. Exposes one
`StateFlow<ProfileOverview>` built with `combine` over:

- pinned notes flow (existing `PinListEvent` addressable note flow),
- latest thread / latest reply: `cache` observers filtered by author, `take(1)`,
- media: gallery filter `take(4)`,
- zaps: reuse `UserProfileZapsViewModel`'s aggregation, moved to commons as
  `ZapsReceivedAggregator` so both the row and the Zaps screen share it,
- counts: bookmarks, tags, badges, reports, following, followers, relays.

```kotlin
@Immutable
data class ProfileOverview(
    val notes: NotesSummary,       // pinned: List<Note>, latest: Note?, count: Int?
    val replies: RepliesSummary,   // latest: Note?, count: Int?
    val media: MediaSummary,       // thumbs: List<Note>, count: Int
    val zaps: ZapSummary,          // totalSats: BigDecimal, zapperCount: Int, top: List<User>
    val mutualCount: Int,
    val bookmarkCount: Int,
    val tags: List<String>,
    val badges: BadgeSummary,      // count: Int, first: List<ETag>
    val apps: AppsSummary,         // recommended: List<String>, publishedCount: Int
    val payments: PaymentSummary,  // rails: List<PaymentRail>
    val reportCount: Int,
    val following: Int, val followers: Int?, val relays: Int,
)
```

Android keeps the existing per-tab ViewModels for the **screens**; the
overview does not instantiate them. `PrepareViewModels` goes away.

## 6. Relay traffic

Opening the overview subscribes to:

1. `UserProfileMetadataFilterSubAssembler` (unchanged): kind 0, 10002, 3,
   8/30008 badges, the list kinds, last-seen.
2. New `UserProfilePreviewFilterSubAssembler` (`FilterUserProfilePreview`):
   per outbox relay, `kinds = UserProfilePostKinds1, authors = [user],
   limit = 5` and `kinds = UserProfileMediaKinds, limit = 4`. Enough for the
   previews; the full 500-limit posts filter no longer runs on open.
3. NIP-45 counts (optional, best-effort): `INostrClient.count(...)` from
   `nip01Core/relay/client/accessories/NostrClientCountExt.kt` for notes,
   replies and zaps on the user's outbox relays; render the number only when
   at least one relay answers, otherwise omit the count from the summary.
   Never block the row on it.

Each screen subscribes to its own assembler via its own
`LifecycleAwareKeyDataSourceSubscription`, torn down on pop:

| Screen | Subscription | ViewModel |
| --- | --- | --- |
| Notes, Replies, Mutual | `UserProfilePostsSubscription` | `UserProfileNewThreadsFeedViewModel` (+ pinned), `UserProfileConversationsFeedViewModel`, `UserProfileMutualFeedViewModel` |
| Media | `UserProfileMediaSubscription` | `UserProfileGalleryFeedViewModel` |
| Zaps | `UserProfileZapsSubscription` | `UserProfileZapsViewModel` |
| Followers | `UserProfileFollowersSubscription` | `UserProfileFollowersUserFeedViewModel` |
| Follows, Bookmarks, Tags, Badges, Apps, Reports | overview metadata is enough (lists + reports already arrive via metadata / `FilterReportsToKey`); `EventFinderFilterAssemblerSubscription` for badge definitions | existing ViewModels |
| Relays | `RelayFeedViewModel.subscribeTo` as today | `RelayFeedViewModel` |

## 7. Android navigation and screens

- `Routes.kt`: add the 13 routes after `Route.Profile`.
- `AppNavigation.kt`: 13 `composableFromEndArgs<Route.ProfileX> { ProfileXScreen(it.id, accountViewModel, nav) }`.
- `RouteMaker.kt`: `fun routeForProfileSection(user, section)` helper used by
  the rows (keeps the `Route` construction in one place).
- New `ProfileFeedScaffold(baseUser, title, subtitle, actions, nav, content)`
  in `profile/ProfileFeedScaffold.kt`: `Scaffold` with a `TopAppBar` showing
  back, 32dp `ClickableUserPicture`, title, `name · count` subtitle, optional
  search / `⋮`, then `content()`. Tapping the avatar/name pops to the
  overview.
- 13 screen files, one per package that already exists
  (`newthreads/ProfileNotesScreen.kt`, `conversations/ProfileRepliesScreen.kt`,
  …). Each creates its ViewModel with `viewModel(key = pubkey + name,
  factory = …)` (same factories as today), places its subscription, and
  wraps the existing `Tab*` body. The `Tab*` composables are renamed to
  `ProfileXFeed` (body only) so the diff stays a move.
- Bottom bar: `NavBarItem` Profile tab still resolves to `Route.Profile`;
  re-tap scrolls the overview to top (same `scrollState.animateScrollTo(0)`).
- `ProfileOverviewScreen` layout: `Scaffold(topBar = ProfileTopBar,
  bottomBar = AppBottomBar)` → `LazyColumn` (header as first item, one item
  per row). No nested-scroll header collapsing any more (`RenderSurface`
  deleted), which also removes the `NestedScrollConnection` hack.

## 8. Desktop

- `DesktopScreen.ProfileSection(pubKeyHex, section: ProfileSection)` where
  `ProfileSection` is a commons enum (`NOTES, REPLIES, MEDIA, ZAPS, MUTUAL,
  BOOKMARKS, TAGS, BADGES, APPS, FOLLOWS, FOLLOWERS, RELAYS, REPORTS`), pushed
  onto the column's `navState` exactly like `DesktopScreen.Thread`.
- `UserProfileScreen.kt` splits into:
  - `profile/ProfileIdentityColumn.kt` (300dp, sticky): avatar, name, NIP-05,
    Follow + Message + Zap, nickname chip, about, meta list, key chip, stats,
    payment summary line. Reuses `FollowButtonRegion` and the `⋮` menu
    already in the file.
  - `profile/ProfileActivityGrid.kt`: 2-column `LazyVerticalGrid` of tiles
    (Notes and Media span 2). Same `ProfileOverview` state from the commons
    ViewModel.
  - `profile/ProfileSectionScreen.kt`: breadcrumb header
    (`name › Section`), then the existing per-tab body (`FeedNoteCard` list,
    `GalleryTab`, `UserSearchCard` lists, `RelayRowCard`, `ProfileZapRow`).
  - The inline `rememberSubscription` blocks move with their tab body into
    `ProfileSectionScreen`; only the metadata / identities / contact-list
    subscriptions stay on the overview.
- Two-column layout at 1000dp inside `ReadingColumn(maxWidth = 1000.dp)`;
  below 900dp window width the identity column stacks above the grid.
- "Reads" and "Highlights", which exist only on Desktop today, become
  `ProfileSection.READS` / `HIGHLIGHTS` tiles (Desktop only until Android has
  the equivalent feeds).

## 9. Strings

New keys in `commons/.../composeResources/values/strings.xml` (Crowdin picks
them up): `profile_row_notes`, `profile_row_notes_summary`,
`profile_row_replies`, `profile_row_replies_summary`, `profile_row_media`,
`profile_row_media_summary`, `profile_row_zaps`, `profile_row_zaps_summary`,
`profile_row_mutual`, `profile_row_mutual_summary`, `profile_row_bookmarks`,
`profile_row_bookmarks_summary`, `profile_row_tags`, `profile_row_tags_summary`,
`profile_row_badges`, `profile_row_badges_summary`, `profile_row_apps`,
`profile_row_apps_summary`, `profile_row_payments`,
`profile_row_payments_summary_more`, `profile_row_reports`,
`profile_row_reports_summary`, `profile_stat_following`,
`profile_stat_followers`, `profile_stat_relays`, `profile_section_activity`,
`profile_edit`, `profile_copy_hex`, `profile_add_to_list`
(`copy_npub_to_clipboard` and `copy_nprofile_to_clipboard` already exist). `mutual` ("Yours") is retired once the Android
tab is gone; `profile_badges_header` / `profile_apps_header` stay for the
management screens.

## 10. Tests

- `commons/src/commonTest/.../viewmodels/profile/ProfileOverviewReducerTest`:
  pure-function tests for the summary derivation (pinned + latest selection,
  media `take(4)`, zap top-3 ordering and totals, "hidden when" rules,
  count-absent rendering).
- `commons/src/commonTest/.../relayClient/profile/FilterUserProfilePreviewTest`:
  the preview filter emits `limit = 5` posts and `limit = 4` media per outbox
  relay and nothing without relays.
- `desktopApp/src/jvmTest/.../filters/DesktopProfileFeedFilterTest`
  unchanged (predicate untouched).
- Android: `ProfileOverviewScreenTest` (Compose UI test, `androidTest`) that
  a Notes row tap navigates to `Route.ProfileNotes`, and that the overview
  registers **only** the overview subscription (assert on
  `RelaySubscriptionsCoordinator` active keys).
- Manual: open a profile with no relay hints, a hidden user, own profile,
  a profile with 0 media / 0 badges, and confirm the Zap sheet on a profile
  with only NIP-A3 targets.

## 11. Delivery order (each step is one PR, app stays shippable after each)

1. **Commons state**: `ProfileSection` enum, `ProfileOverview` +
   `ProfileOverviewViewModel`, `ZapsReceivedAggregator` moved from Android,
   `FilterUserProfilePreview` + sub-assembler, assembler split into
   `UserProfileOverviewFilterAssembler` + per-screen assemblers (Android's
   `RelaySubscriptionsCoordinator` wires all of them; the old combined
   assembler is deleted in the same PR so nothing depends on both). Tests
   from §10 for commons.
2. **Android screens**: 13 routes + `ProfileFeedScaffold` + 13 screens
   wrapping the renamed `Tab*` bodies, each with its own subscription.
   `ProfileScreen` still shows the tabs but its `CreateAndRenderTabs` calls
   `nav.nav(Route.ProfileX)` instead of paging, so the split is live before
   the header changes.
3. **Android overview**: new `ProfileHeader`, `ProfileSectionRow`, stats row,
   Payment methods sheet, `ProfileOverviewScreen` replaces `ProfileScreen`'s
   body; delete `RenderSurface` / pager / tab code, `PrepareViewModels`,
   `TabPinnedNotes`, the wallet / Bolt12 / List buttons from the action row,
   `UserNicknameCard`. Settings copy update. Compose UI test.
4. **Desktop**: `ProfileIdentityColumn`, `ProfileActivityGrid`,
   `ProfileSectionScreen`, `DesktopScreen.ProfileSection`; `UserProfileScreen`
   shrinks to composition of those three.
5. **Cleanup**: remove dead strings, run `./tools/material-symbols-subset/subset.sh`
   if any new `MaterialSymbol` codepoint was introduced (candidates:
   `Reply`, `PhotoLibrary`, `Tag`, `Flag`, `Dns` — check `MaterialSymbols.kt`
   first), `./gradlew spotlessApply`, update this plan's status and move it
   to `archive/`.

## 12. Risks

- **Counts without NIP-45.** Most relays do not implement COUNT; the copy
  must read well without numbers ("last note 2h ago"). Never show a local
  cache size as if it were a total.
- **Followers count** already has the NIP-85 / local fallback; the local
  path needs the Followers subscription, which the overview no longer opens.
  Show "—" until the user opens the Followers screen, or use the NIP-85
  contact-card number only. Decide in step 3.
- **Deep links** into a specific section (`nostr:` URLs only address the
  profile) are unaffected; the AppFunctions verbs that map to profile feeds
  (`docs/…/appfunctions-screens-as-verbs`) keep pointing at the filters, not
  the screens.
- **Baseline profile / macrobenchmarks** do not exercise the profile screen
  (checked `baselineprofile/` and `benchmark/`), so no benchmark script
  changes.
- **Desktop deck columns** are 400dp wide by default; the two-column layout
  only applies to the focused / reading-width presentation, the narrow column
  gets the stacked layout.

## 13. Open questions

- Should "Between you two" also surface DM threads (needs the chatroom
  lookup), or stay notes-only as the current Mutual filter?
- Own profile: should the Activity index show private counts (drafts,
  private bookmarks) that visitors never see?
- Followers stat: NIP-85 number only, or open the Followers subscription on
  the overview when no trusted assertion exists?
- Desktop: keep the 720dp reading column for the section screens, or let
  them use the full 1000dp width?
