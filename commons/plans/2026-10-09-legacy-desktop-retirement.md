# Retiring the legacy desktop app: what each legacy-only feature becomes

> **Status:** decided 2026-10-09 (maintainer), nothing built yet. Supersedes the
> "Legacy-only feature" table in [2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md)
> step 8d.

The legacy desktop app (`./gradlew :desktopApp:runLegacy`, everything in `desktopApp` outside
`desktop/app/`) stays runnable until its desktop-only features have a home in the shared UI. When
this list is done, delete everything outside `desktop/app/` that `desktop/app/` does not reach, and
the `runLegacy` task.

Android often has a better version of the same idea (its NIP-85 web of trust is far more than
the legacy app's follows-of-follows count). So for each feature, the legacy implementation was
compared with what Android and the shared modules already do, and the maintainer chose to discard
it, port only the missing parts, or rebuild it on the shared code. Nothing below ports legacy UI
as-is: the legacy screens are not shared screens and cannot be reused.

## Decisions

| # | Legacy feature | Decision |
|---|---|---|
| 1 | Messages privacy lock | **Port, and extend.** New shared settings screen; Android gets it too. |
| 2 | Hashtag-spam filter, WoT badge | **Discard.** The shared max-hashtag filter and the NIP-85 score chip cover them. |
| 3a | Live-now bar | **Discard.** The shared Home live bubbles are a superset. |
| 3b | Relay metrics dashboard | **Discard the dashboard; port the latency engine and its display.** |
| 4 | Spotlight and advanced search | **Discard.** The shared search screen is a superset; the Spotlight was never wired. |
| 5 | Custom feed builder | **Rebuild in shared code, for Android too.** Feeds appear in the top-bar feed picker; stored in our own per-account structure. |
| 6a | Upload compression preview | **Rebuild in the shared composer:** image preview before posting; the quality slider sets resolution. |
| 6b | GIF picker (NIP-94 search) | **Discard.** Replace with a NIP-30 custom-emoji picker button. |
| 7 | Deck and workspaces | **Rebuild on the shared shell, with workspaces, and migrate legacy users' decks.** |

### 1. Privacy lock

The state machine is already shared and tested (`commons/privacylock`: `PrivacyLockState`,
`InactivityTimer`, `LockScope`, backoff), and so are the gates (`commonsUI/ui/privacylock`:
`MessagesLockGate`, `WalletLockGate`, `LockScreen`, `DeviceAuthenticator`). Nothing calls them:
Android has no app lock at all (the only authentication is revealing the nsec on the key-backup
screen), and the new desktop does not wire them.

- A new **Privacy lock** settings screen in `commonsUI` with three independent switches, **all off
  by default**:
  - **Lock the entire app.** On start and after the idle timeout.
  - **Lock messages.** The Messages screens and chat rooms.
  - **Lock the wallet.** The wallet screens.
- An idle timeout (1, 5, 15 or 60 minutes, or never; default 5) that applies to whichever locks are on.
  Leaving a locked screen re-locks it, as the legacy app does.
- `LockScope` grows an app-wide scope beside Messages and Wallet.
- Unlocking:
  - **Android:** biometrics or the device credential, through the existing `DeviceAuthenticator`.
  - **Desktop:** an app password. The legacy `PasswordHasher` (PBKDF2-SHA256, 600k iterations, legacy
    hashes still verify) moves to `commons/jvmMain`, and `DeviceAuthenticator.jvm` stops approving
    unconditionally. Set, change and clear the password from the settings screen (desktop only).
- The lockout backoff (5 failures, then 30 s doubling to 5 min, persisted) is shown on the shared
  `LockScreen`.
- Keep: blurring wallet amounts while the desktop window is unfocused, when the wallet lock is on.
- Drop:
  - The first-run banners.
  - `DmRedactionLevel` (DM text in notifications). It was stored but no notifier ever read it.

### 2. Hashtag spam, WoT badge

- Discard both, with no ports.
- They go away with the legacy app:
  - `WoTService`, `OutboxDispatcher` (about 800 lines that crawl every follow's kind 3),
    `LocalWoTService`.
  - `HashtagSpamCheck`, `HashtagSpamSettings`, `CollapsedSpamNote`, `LocalSpamExemptKeys`.
- Before deleting them, check that nothing outside the legacy app uses `OutboxDispatcher`.

### 3. Live bar, relay metrics

- Live-now bar: discard. There will be no viewer-count tiebreak in `HomeLiveFilter`. The commons
  `LiveActivitySorting` helper is dead code once the legacy app is gone.
- Relay metrics: discard `RelayDashboardScreen`, `RelayMetricsTab`, `RelayMetricCard`,
  `RelayDetailPanel` and `RelayStatusCard`, and the desktop-only `RelayMetrics` counter, which
  overlaps quartz `RelayStat`. Port two things into the shared app:
  1. Build `RelayLatencyTracker` and `RelayHealthStore` (commons `relays/health/`, today only built
     by the legacy `Main.kt`) in both apps' modules. Persistence goes behind a shared settings
     store, replacing `PreferencesRelayHealthPersistence`.
  2. Show the p50 times (OK-ack, EOSE, first result) and a "Slow ×N" chip (`classifySlowRelays`,
     against the cohort median) on the shared `RelayStatusRow`, and a latency section on
     `RelayInformationScreen`.
- Not ported:
  - The unhealthy-relay banner and popup.
  - "Last event N ago".
  - "Reconnect all".

### 4. Search

- Discard the legacy search: `SearchSpotlight`, `AdvancedSearchPanel`, the legacy `SearchScreen`,
  `SearchHistoryStore` and the search-relay editor.
- Also delete commons `AdvancedSearchBarState`, which only the legacy app uses.
- No quick-search popup and no saved-search UI for now.

### 5. Custom feeds

- The shared app gets a feed builder:
  - Users can make **as many feeds as they like**, and every one appears in the **top-bar feed picker**
    beside Follows, Global, lists, interest sets and DVM feeds.
  - A feed combines authors, hashtags, kinds and relays (AND), with excluded authors and excluded
    keywords. The legacy `FeedBuilderDialog` already does this; there is a name and an emoji too.
- Model and storage:
  - Reuse the commons model in `feeds/custom/` (`FeedDefinition`, `FeedBuilderState`, the
    serializer), which today only the legacy app uses.
  - Store the feeds in **our own per-account data structure**, not as kind 31890 events
    (maintainer, 2026-10-09). Whether other clients' 31890 definitions would help or hinder is an
    open question; publishing and importing them can come later.
- Filters:
  - A new top-nav filter type (`IFeedTopNavFilter` under `commons/model/topNavFeeds/`) runs a
    custom feed. Relay REQ filters and client-side excludes go through the shared relay client and
    feed filters; the legacy `DesktopCustomFeedFilter` and `createCustomFeedSubscription` are not
    reused.
  - Resolve the name clash: commonsUI `TopNavFilterState.kt` declares its own `FeedDefinition`.
- Editor: a shared create/edit sheet over `FeedBuilderState`, reached from the feed picker
  ("New feed…") with edit and delete on each custom feed.
- Migration: legacy desktop feeds are local JSON in java.util.prefs (`LocalFeedProvider`). On the
  first start of the new desktop, copy them into the new store for the account that made them.

### 6. Composer

Compression (images; video previews would take too long):

- **A preview before posting.** Show the re-encoded image itself, not just its size, so quality and
  artifacts can be judged. The preview also shows the new size and dimensions next to the
  original's, and tapping or clicking it compares the original with the compressed version.
  Compression is not only about size.
  - The libraries cannot estimate a result, so the preview really compresses the image in the
    background (Android: Zelory; desktop: `ImageReencoder`) and is cached per image and setting.
  - Both apps need a platform hook beside `MediaUploader` to produce it.
- **The quality slider sets the resolution too.** Today Android shrinks every image to 640 px wide
  whatever the setting (`MediaCompressor.compressImage`, `default(width = 640, …)`); the slider only
  changes the JPEG quality. The new desktop copies that, while the legacy desktop uploaded at
  1920 px.
  - Each step will set both the quality and a maximum size, for example 640 / 1280 / 1920 px.
  - Desktop gets extra steps for large screens, for example 2560 px and the original size.
- **No remembered default.** Each post starts from the default, because each post has a different
  goal.
- Shared `ImageVideoDescription` already has alt text, AI alt, the sensitive flag, the server choice,
  H.265 and GIF→MP4; those stay.

GIF picker:

- Discard the legacy NIP-94 GIF search (`GifPickerPanel`, `GifResult`, the `gifRelays` pref) and the
  unicode emoji grid (and with it `kodein-emoji`).
- Add a **custom-emoji picker** button to the shared composers (notes and DMs, both apps). It shows a
  grid of the user's NIP-30 emoji (their emoji list and saved emoji sets) and inserts
  `:shortcode:`. Today, typing `:word` suggests from the same data.

### 7. Deck and workspaces

Rebuild on the shared shell. The legacy deck (about 5,000 lines) renders legacy screens and does
not port.

- **A Deck mode of `ScreenLayoutSpec`.** It is opt-in in settings and only offered on windows about
  1,200 dp or wider: desktop, Chromebooks and Android tablets in landscape.
- `MultiPaneShell` keeps the main screen on the left and puts extra columns to its right, in a
  horizontal scroll with draggable dividers. The notification side panel becomes the default first
  column.
- Each column is a `Route` with its own back stack, through a `ColumnNav : INav` wrapper (like
  `TwoPaneNav`), so every shared screen works as a column.
- Columns can be added, closed, moved and resized. The pure resize and fit logic in the legacy
  `DeckState` moves to `commons`. An "add column" picker lists Route types.
- **Workspaces:** named layouts, each a column list with widths, switched from the shell. They are
  stored per account with kotlinx-serialization (the old storage was Jackson in `DesktopPreferences`).
- Desktop keyboard shortcuts in `DesktopMenuBar`:
  - Ctrl/Cmd+T to add a column, +W to close it.
  - +Shift+←/→ to move a column.
  - +1…9 to focus a column.
  - +Shift+S to save a workspace.
- **Migration:** `LegacyDesktopAccountImport` also imports `deckColumns` and the workspaces. Each
  legacy `DeckColumnType` maps to a `Route`:
  - A custom-feed column maps to the migrated custom feed (item 5).
  - A type with no shared screen is dropped, and the user is told.

## Order

Small and independent first; the deck last, since it builds on item 5 (custom-feed columns):

1. The relay latency engine and display (3b).
2. The privacy lock (1).
3. Compression preview and resolution steps (6a), then the emoji picker (6b).
4. Custom feeds (5).
5. The deck and workspaces, with migration (7).
6. Delete the legacy app: discarded features (2, 3a, 4, 6b's GIF search) go with it.
