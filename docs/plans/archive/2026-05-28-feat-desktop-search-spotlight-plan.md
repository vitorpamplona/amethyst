---
title: "feat: Desktop Search Spotlight + Unified Feed Header Bar"
type: feat
status: active
date: 2026-05-28
origin: docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md
---

# feat: Desktop Search Spotlight + Unified Feed Header Bar

> **Status:** shipped — Implemented as SearchSpotlight + SearchPill + feed header bar on desktop.
> _Audited 2026-06-30._


## Overview

Add a global search spotlight overlay (Cmd+F) and a unified feed header bar that combines feed tabs with a search pill. Also fix the bug where "+ Add more" feeds only works from the Home feed by moving it to the sidebar.

Three components:
1. **SearchSpotlight** — global overlay with scrim, auto-focus, recent/saved searches, live results
2. **FeedHeaderBar** — unified feed tabs (My Feed | Global | pinned custom) + search pill, used on every feed column
3. **Sidebar "+ Add Feed"** — move from broken feed header to always-accessible sidebar

(see brainstorm: docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md)

## Problem Statement

1. **No quick search** — searching requires opening a full Search column. No spotlight/command-palette UX.
2. **Feed tabs disconnected from search** — the screenshot reference shows tabs + search in a unified bar, but current feed columns have separate headers with no search pill.
3. **"+ Add more" bug** — the button to add custom feeds only works on the Home feed; from Global or custom feeds it's broken/invisible.

## Proposed Solution

### Search Spotlight (Cmd+F)

Global overlay that dims content (50% scrim), shows a centered search card (~600dp wide, 20% from top):
- **Empty state:** Recent searches + saved searches from `SearchHistoryStore`
- **Typing:** Live people + note results (reuse `AdvancedSearchBarState` with 300ms debounce)
- **Result selection:** Opens new column (deck) or navigates (single-pane)
- **"Open full search"** link at bottom → opens Search column with current query
- **Keyboard:** Escape closes, arrow keys navigate results, Enter selects

### Unified Feed Header Bar

Replaces the current `ColumnHeader` on feed-type columns:
```
┌──────────────────────────────────────────────┐
│ [My Feed] [Global] [Custom1]  🔍 Search.. ⌘F │
└──────────────────────────────────────────────┘
```
- Left: feed tabs (Following + Global always, up to 3 pinned custom feeds)
- Right: compact SearchPill that opens the spotlight
- Active tab: `primary` color indicator
- 48dp height, `surfaceContainer` background

### Sidebar "+ Add Feed"

Move from feed column header to MainSidebar's FEEDS section — always accessible.

## Technical Approach

### Implementation Phases

#### Phase 1: SearchSpotlight Composable

New file: `desktopApp/.../ui/search/SearchSpotlight.kt`

**Architecture:**
- `Dialog` with custom `Surface` (not `AlertDialog` — need full layout control)
- Scrim: `Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() })`
- Search card: `Surface(shape = shapes.large, color = surface)` centered with `600.dp` max width
- Input: `BasicTextField` with pill decoration (matching SearchScreen pattern), auto-focused via `FocusRequester`
- State: Create `AdvancedSearchBarState(scope)` scoped to spotlight lifecycle. `DisposableEffect` stops subscriptions on close.
- Results: `LazyColumn` with sections (People max 5, Notes max 5), each item clickable
- History: Read from `SearchHistoryStore` on open

**Key composables:**
```kotlin
@Composable
fun SearchSpotlight(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    account: AccountState.LoggedIn,
    searchHistoryStore: SearchHistoryStore,
    onSelectProfile: (String) -> Unit,      // pubkey hex
    onSelectNote: (String) -> Unit,          // note id hex
    onSelectHashtag: (String) -> Unit,       // tag
    onOpenFullSearch: (String) -> Unit,       // query text
    onDismiss: () -> Unit,
)
```

**Success criteria:**
- [ ] Spotlight opens centered with scrim dimming background
- [ ] Input auto-focused on open
- [ ] Recent + saved searches shown before typing
- [ ] Live people/note results appear while typing (300ms debounce)
- [ ] Selecting result calls appropriate callback and closes
- [ ] Escape closes spotlight
- [ ] Arrow key navigation through results
- [ ] "Open full search" opens Search column with query

#### Phase 2: SearchPill Composable

New file: `desktopApp/.../ui/search/SearchPill.kt`

Small reusable pill:
```kotlin
@Composable
fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```
- Pill shape (999dp corners), `surfaceContainerHigh` background
- Search icon + "Search..." + "⌘F" hint
- Height: 36dp
- Hover highlight via `hoverHighlight()` modifier

**Success criteria:**
- [ ] Renders as compact pill with search icon and shortcut hint
- [ ] Click opens spotlight
- [ ] Hover highlights

#### Phase 3: FeedHeaderBar Composable

New file: `desktopApp/.../ui/search/FeedHeaderBar.kt`

Unified header for feed columns:
```kotlin
@Composable
fun FeedHeaderBar(
    feedTabs: ImmutableList<FeedTab>,
    activeFeedId: String,
    onTabClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
)

data class FeedTab(
    val id: String,
    val label: String,
    val isBuiltIn: Boolean = false,  // Following/Global are built-in
)
```

**Layout:**
- `Row` with `surfaceContainer` background, 48dp height
- Left: scrollable `Row` of tab chips/buttons
- Right: `SearchPill` with fixed width
- Active tab: filled with `primary`, others `onSurfaceVariant`
- Divider at bottom: 1dp `outlineVariant`

**Feed tabs source:**
- "My Feed" (Following) — always first, `id = "following"`
- "Global" — always second, `id = "global"`
- Pinned custom feeds from `LocalFeedRepository.current` (max 3)

**Integration:** Replace `ColumnHeader` for `DeckColumnType.HomeFeed`, `DeckColumnType.GlobalFeed`, and `DeckColumnType.CustomFeed` in `DeckColumnContainer.kt`.

**Success criteria:**
- [ ] Feed tabs render with Following + Global + up to 3 pinned
- [ ] Active tab highlighted with primary color
- [ ] Tab click switches feed
- [ ] Search pill visible on right side
- [ ] Shown on all feed-type columns

#### Phase 4: Sidebar "+ Add Feed" + Keyboard Shortcut

**MainSidebar (DeckSidebar.kt):**
- Add "+ Add Feed" item at bottom of FEEDS section
- Click opens feed builder/drawer (existing `onOpenFeedsDrawer` callback)
- Styled as subtle text link with `+` icon

**Main.kt:**
- Add `Cmd+F` keyboard shortcut in MenuBar
- Add `showSearchSpotlight` state
- Render `SearchSpotlight` when `showSearchSpotlight` is true
- Wire result callbacks to `deckState.addColumn()` / `singlePaneState.navigate()`

**FeedScreen.kt:**
- Remove inline "+ Add more" button (now in sidebar)

**Success criteria:**
- [ ] Cmd+F opens spotlight from anywhere in the app
- [ ] "+ Add Feed" visible in sidebar FEEDS section
- [ ] "+ Add Feed" works regardless of current feed view
- [ ] Old "+ Add more" button removed from feed headers

## Acceptance Criteria

### Functional Requirements

- [ ] Cmd+F opens search spotlight overlay with scrim
- [ ] Spotlight shows recent + saved searches on open
- [ ] Live search results (people + notes) with 300ms debounce
- [ ] Selecting a profile opens it (new column in deck, navigate in single-pane)
- [ ] Selecting a note opens thread
- [ ] "Open full search" opens Search column with query
- [ ] Escape closes spotlight
- [ ] FeedHeaderBar shows feed tabs + search pill on all feed columns
- [ ] Tab switching works (Following ↔ Global ↔ Custom feeds)
- [ ] "+ Add Feed" in sidebar works from any screen
- [ ] Keyboard arrow navigation through spotlight results

### Non-Functional Requirements

- [ ] Spotlight opens in <100ms (no relay calls until user types)
- [ ] Scrim renders at 60fps
- [ ] Search results appear within 300ms of typing pause
- [ ] Compiles on all platforms (macOS, Windows, Linux)
- [ ] Spotless clean

## Dependencies

- `AdvancedSearchBarState` (commons/) — reuse, no changes needed
- `SearchHistoryStore` (desktop) — reuse, no changes needed
- `SearchFilterFactory` (desktop) — reuse for relay subscriptions
- `FeedDefinitionRepository` (commons/) — read pinned feeds for tabs
- `MainSidebar` — add "+ Add Feed" item
- `hoverHighlight()` modifier — already created in visual personality PR

## Files Affected

| File | Action |
|------|--------|
| New: `desktopApp/.../ui/search/SearchSpotlight.kt` | Spotlight overlay |
| New: `desktopApp/.../ui/search/SearchPill.kt` | Compact pill component |
| New: `desktopApp/.../ui/search/FeedHeaderBar.kt` | Unified feed tabs + search |
| `desktopApp/.../Main.kt` | Cmd+F shortcut, spotlight state, render, result callbacks |
| `desktopApp/.../ui/deck/DeckColumnContainer.kt` | Use FeedHeaderBar for feed columns |
| `desktopApp/.../ui/deck/DeckSidebar.kt` | Add "+ Add Feed" to FEEDS section |
| `desktopApp/.../ui/FeedScreen.kt` | Remove "+ Add more" if present, adapt header |
| `desktopApp/.../ui/deck/ColumnHeader.kt` | May need trailing slot for non-feed columns |

## Sources & References

### Origin

- **Brainstorm:** [docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md](docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md) — Key decisions: Cmd+F spotlight, unified FeedHeaderBar, sidebar "+ Add Feed"

### Internal References

- `SearchScreen.kt` — existing full search column (keep as-is)
- `AdvancedSearchBarState.kt` — reusable search state management
- `SearchHistoryStore.kt` — recent/saved search persistence
- `SearchFilterFactory.kt` — NIP-50 relay filter construction
- `FeedDefinitionRepository.kt` — pinned custom feeds source
- `MainSidebar (DeckSidebar.kt)` — sidebar where "+ Add Feed" moves to
