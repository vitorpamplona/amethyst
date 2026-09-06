# Profile page redesign: activity overview + split-out screens

_Status: proposal (design canvas + this plan). Nothing implemented yet._

Design canvas (current vs proposed, Android + Desktop):
<https://claude.ai/code/artifact/5d803271-feb4-40f0-b37f-b9826199394e>

## Why

The Android profile (`ui/screen/loggedIn/profile/`) has grown by accretion.
Reading `ProfileHeader.kt` + `DrawAdditionalInfo.kt` + `ProfileScreen.kt`
top to bottom, a visitor today gets:

| Region | Today |
| --- | --- |
| Actions | Up to 6 equal-weight `FilledTonalButton`s in one row: Message, NIP-A3 wallet, Bolt12, Edit (own), Follow/Unfollow + List (split pill). |
| Identity | Nickname `OutlinedCard`, display name + pronouns, `@name`, **two** identifier rows (short npub + copy; short nprofile + copy + QR), "last seen", NIP-05 row, website row, one row per NIP-39 identity. |
| Blocks | Payment-rail chip `FlowRow` (Lightning/CLINK/on-chain/Cashu/NIP-A3), Badges header + 44dp grid, About text, App-recommendations header + chips. |
| Tabs | 12 in a `SecondaryScrollableTabRow`: Notes, Replies, Yours, Gallery, Apps & Sites, N Following, N Followers, N Zaps, N Bookmarks, N Followed Tags, Reports, Relays. |
| Data | `UserProfileFilterAssemblerSubscription` opens every feed's filters when the screen opens (followers and zaps gated by settings), plus 13 ViewModels created eagerly in `PrepareViewModels`. |

Desktop (`desktop/ui/UserProfileScreen.kt`, ~2000 lines) has the same shape:
a `surfaceVariant` card with banner, avatar, name, full npub, about, five
metadata rows and counts, then 11 tabs, all in a single 720dp reading column.

Problems this causes:

1. **No hierarchy.** Payment, follow, message, edit and list actions all look
   the same. Identifiers are shown twice. Six grey metadata rows compete with
   the bio.
2. **Tabs as storage.** Follow counts live inside tab labels. Rarely used
   destinations (Reports, Relays, Followed Tags) cost the same horizontal
   space as Notes.
3. **Everything loads at once.** Opening a profile subscribes to notes,
   replies, gallery, bookmarks, pinned, apps, mutual, follows, followers,
   zaps, reports and relays for a user the visitor may only glance at.

## Proposal

### 1. The profile screen becomes an *overview*, not a tabbed container

The screen is one scrollable column with three parts:

**Header (identity + primary actions)**

- Banner 140dp; top-bar icons (back, share, more) are translucent scrims on
  the image rather than opaque discs.
- Avatar 88dp overlapping the banner, ring in `background` colour in both
  themes (today the light theme draws a black ring).
- Action row, right of the avatar: **Follow** (filled `Button`, the only
  primary), **Message** and **Zap** as 40dp outlined icon buttons. Own
  profile: **Edit profile** replaces Follow. Everything else (add to list,
  copy hex / nprofile, QR, edit nickname, block, report, payment targets,
  Bolt12 offers) moves into the `⋮` sheet (`UserProfileDropDownMenu`) or the
  Zap sheet.
- Identity block: name + pronouns; one line `✓ nip05 · Active 2h ago`
  (the NIP-05 identifier replaces the `@name` row when they match; fall back
  to `@name` when there is no NIP-05); the private nickname/petname as a
  small tinted chip (lock icon) instead of a full-width card; about text;
  **one wrapping meta row** for website + NIP-39 identities; **one npub chip**
  with copy + QR (hex and nprofile move to the menu).
- **Stats row**: `1,234 Following · 5,678 Followers · 12 Relays`, each
  tappable. This replaces the Follows / Followers / Relays tabs.

**Activity index**

Every former tab becomes a 56dp row: leading icon, title, a *live summary*
(count, recency, top items), trailing chevron. A few rows carry a small
inline preview:

| Row | Summary | Inline preview |
| --- | --- | --- |
| Notes | `312 notes · 1 pinned · last 2h ago` | pinned note + latest note (compact, text only) |
| Replies | `48 replies this month · last 40m ago` | none |
| Media | `96 photos and videos` | 4 thumbnails, horizontal strip |
| Zaps received | `42k sats from 87 people` | 3 stacked zapper avatars |
| Between you two (Yours) | `3 of your notes mention X` | none |
| Bookmarks | `7 public bookmarks` | none |
| Followed tags | `3 hashtags` | first chips |
| Badges | `12 accepted` | 3 thumbs + `+N` |
| Apps & sites | `Recommends A, B, C · 2 sites` | none |
| Payment methods | `Lightning · CLINK · On-chain · Cashu · +2` | none (row opens the send-payment sheet) |
| Reports | `2 reports from people you follow` | none; only shown when > 0, warning tint |

Rows with a zero count are hidden except Notes/Replies/Media (always shown so
the screen has a stable shape) and Reports (shown only when non-zero).

**Dedicated screens**

Each row opens its own screen (`Route.ProfileNotes(pubkey)`,
`Route.ProfileReplies`, `Route.ProfileMedia`, `Route.ProfileZaps`,
`Route.ProfileMutual`, `Route.ProfileBookmarks`, `Route.ProfileTags`,
`Route.ProfileBadges` (exists), `Route.ProfileApps`, `Route.ProfileRelays`,
`Route.ProfileReports`, `Route.ProfileFollows`, `Route.ProfileFollowers`).
Each is a **separate, standalone screen**: there is no shared tabbed
container and no chip row linking siblings. A screen is a compact top bar
(back, 32dp avatar, screen title, `name · count` subtitle, search where the
list is long, `⋮`) over the existing feed composable for that former tab, and
it owns its own `FeedViewModel` and filter-assembler subscription, created
when the screen opens and torn down when it pops.

### 2. Desktop

Mouse-first two-column layout inside a 1000dp column: banner spans the top;
left column (300dp, sticky) holds identity, actions, meta, npub chip, stats
and payment summary; right column holds the Activity index as a 2-column grid
of tiles (Notes and Media span both columns for their previews). Clicking a
tile swaps the right column for that feed and the screen header becomes a
breadcrumb (`Vitor Pamplona › Notes`).

## Data model

Introduce a `ProfileOverviewViewModel` in `commons` (`viewmodels/`) exposing a
single `StateFlow<ProfileOverview>`:

```kotlin
data class ProfileOverview(
    val notes: SectionSummary,      // count, lastCreatedAt, pinned: List<Note>, latest: List<Note>
    val replies: SectionSummary,
    val media: MediaSummary,        // count, thumbs: List<Note>
    val zaps: ZapSummary,           // totalSats, zapperCount, topZappers: List<User>
    val mutual: CountSummary,
    val bookmarks: CountSummary,
    val tags: List<String>,
    val badges: BadgeSummary,       // count, first: List<Note>
    val apps: AppsSummary,
    val payments: PaymentSummary,   // rails available (lightning, clink, onchain, cashu, NIP-A3 targets)
    val relays: CountSummary,
    val reports: ReportSummary,     // count from follows, count total
    val following: Int, val followers: Int?,
)
```

Sources already exist and are reused, not duplicated:

- Counts: `UserProfileFollowsUserFeedViewModel.followCount`,
  `FollowersTabHeader` count flow, `UserProfileZapsViewModel.totalReceivedZaps`,
  `BookmarkTabHeader` / `FollowedTagsTabHeader` / `ReportsTabHeader` counts,
  relay list from `User.relays` (NIP-65 note).
- Previews: `UserProfilePinnedNotesFeedFilter` (pinned),
  `UserProfileNewThreadFeedFilter` limited to 2, `UserProfileGalleryFeedFilter`
  limited to 4, `UserProfileZapsViewModel` top zappers.
- Payment rails: the same resolvers `DisplayPaymentRailChips` uses
  (`rememberProfileClinkOffer`, `cashuWalletState.peekNutzapFunding`,
  `PaymentTargetsEvent`, `Bolt12OfferListEvent`).

Relay traffic on open shrinks to: kind 0 / 10002 / 3 / 10000 (already),
`limit: 3` kind-1 notes, `limit: 4` media, the addressable lists (pinned,
badges, apps, payment targets, bookmarks, tags), and NIP-45 `count` for
replies / zaps where relays support it (fall back to "—" when they don't).
The per-tab `UserProfile*FilterSubAssembler`s move out of
`UserProfileFilterAssembler` into each dedicated screen's own
`ComposeSubscriptionManager` subscription, so a feed loads only when its
screen is open.

## Implementation phases

1. **Commons**: `ProfileOverviewViewModel` + `ProfileOverview` state;
   `ProfileSectionRow` / `ProfileStatsRow` / `ProfileKeyChip` composables in
   `commons/.../ui/profile/` (shared by Android and Desktop).
2. **Android**: new `ProfileOverviewScreen` replacing the body of
   `ProfileScreen`; `ProfileHeader` rewritten to the header above; each
   `Tab*` composable becomes its own screen (one route, one file, one
   ViewModel, one subscription) under a shared `ProfileFeedScaffold(title,
   subtitle, feed)`; `UserProfileFilterAssembler` split per screen.
3. **Desktop**: `UserProfileScreen` split into `ProfileIdentityColumn` +
   `ProfileActivityGrid` + per-feed content; delete the 11-tab row.
4. **Cleanup**: remove `Bolt12PayButton`, `PaymentButton` and `ListButton`
   from the action row (their entry points move to the `⋮` sheet and Zap
   sheet); remove the hex/nprofile rows; delete the
   `showProfileFollowersFeed` / `showProfileZapReceivedFeed` toggles or
   repurpose them to hide the corresponding rows.

## Open questions

- Should "Between you two" also surface DM threads (needs the chatroom
  lookup), or stay notes-only as the current Mutual filter?
- Own profile: should the Activity index show private counts (drafts,
  private bookmarks) that visitors never see?
- Desktop: keep the 720dp reading column for the feed screens, or let them
  use the full 1000dp two-column width?
