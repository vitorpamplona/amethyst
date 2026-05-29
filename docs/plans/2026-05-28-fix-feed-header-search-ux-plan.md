---
title: "fix: Feed Header Bar Layout + Inline Search UX"
type: fix
status: active
date: 2026-05-28
origin: docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md
---

# fix: Feed Header Bar Layout + Inline Search UX

## Overview

Fix 5 issues with the feed header bar and search experience introduced in the visual personality overhaul.

## Problems

| # | Issue | Root Cause |
|---|-------|-----------|
| 1 | Feed tabs take too much space, search not centered | `Row` with `SpaceBetween` pushes search to far right. Tabs + "+ More" consume all left space |
| 2 | Clicking search pill doesn't open search | `onSearchClick` callback not wired from `DeckColumnContainer` → `FeedScreen` → `FeedTabsHeader` |
| 3 | Search opens as Dialog overlay (wrong UX) | `SearchSpotlight` uses `Dialog()` which creates separate AWT window. Should be inline expansion of the pill with dropdown + background blur |
| 4 | Follow tabs can't be clicked | FilterChip `onClick` calls `onNavigateToFeed` for custom feeds, but the callback only handles `FeedSource.Filter` — other source types silently ignored |
| 5 | Header doesn't match screenshot card aesthetic | Header is a plain `Row` with padding. Should be a card-like `Surface` with rounded corners and visual separation |

## Proposed Solution

### Fix 1: Redesign FeedTabsHeader layout

**File:** `FeedScreen.kt` — `FeedTabsHeader`

New layout — compact tabs on left, search pill takes center weight, compose on right:
```
┌─────────────────────────────────────────────────────┐
│ [Following][Global][+]  🔍 Search notes...  ⌘F  ✏️ │
└─────────────────────────────────────────────────────┘
```

- Feed tabs: compact `FilterChip` with just emoji+name, `Arrangement.spacedBy(4.dp)`
- Remove "+ More" chip (moved to sidebar already)
- SearchPill: `Modifier.weight(1f)` so it fills remaining center space
- Compose button: stays on far right
- Wrap entire header in `Surface(shape = shapes.medium, color = surfaceContainer)` with 12dp padding

### Fix 2: Wire onSearchClick callback

**File:** `DeckColumnContainer.kt`

Pass `onSearchClick = { showSearchSpotlight = true }` through `FeedScreen` call sites. The `showSearchSpotlight` state is already at Main.kt level — need to pass it down as callback.

Simplest approach: add `onSearchClick` param to `MainContent` → column container → FeedScreen.

### Fix 3: Replace Dialog with inline search expansion

**File:** `SearchSpotlight.kt` → Delete. Replace with inline expansion in `SearchPill.kt`

Instead of a Dialog overlay, the SearchPill itself expands:

**Collapsed (default):**
```
🔍 Search notes, profiles...  ⌘F
```

**Expanded (on click or Cmd+F):**
```
┌────────────────────────────────────────┐
│ 🔍 [typing here...]              ⌘F  │
├────────────────────────────────────────┤
│ Recent                                 │
│  📝 bitcoin lightning                  │
│  📝 @fiatjaf                           │
├────────────────────────────────────────┤
│ Saved                                  │
│  ⭐ Nostr development                  │
└────────────────────────────────────────┘
+ background blur/dim behind dropdown
```

Implementation:
- `SearchPill` gains `expanded: Boolean` state
- When expanded: pill becomes `BasicTextField` with same shape, a `DropdownMenu` or `Popup` appears below with history/results
- Background: `Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))` rendered at the parent level when expanded
- Clicking outside or pressing Escape collapses
- `Cmd+F` sets expanded = true on the active feed column's SearchPill

### Fix 4: Fix follow tab click handlers

**File:** `FeedScreen.kt` — `FeedTabsHeader`

Current `onNavigateToFeed` callback only handles `FeedSource.Filter`. Need to also handle `FeedSource.Following` and `FeedSource.Global` by calling `onFeedModeChange` directly in the chip onClick (already done for the `when` branches — the bug is that custom feed chips with non-Filter sources silently do nothing).

Check: is `feed.source` ever something other than `Following`, `Global`, or `Filter`? If so, add handling.

### Fix 5: Card-based header design

**File:** `FeedScreen.kt` — `FeedTabsHeader`

Wrap the header Row in a `Surface`:
```kotlin
Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding, vertical = 8.dp),
) {
    Row(
        modifier = Modifier.padding(8.dp),
        ...
    ) { ... }
}
```

This gives it the same card treatment as feed items — white surface, subtle border, rounded corners.

## Implementation Phases

### Phase 1: Fix header layout + card design (Fixes 1, 5)

**FeedScreen.kt — rewrite FeedTabsHeader:**
- Wrap in `Surface` with `OutlinedCard` styling
- Compact tabs (remove "+ More")
- SearchPill with `weight(1f)` in center
- 48dp height, consistent padding

### Phase 2: Inline search expansion (Fixes 2, 3)

**Delete SearchSpotlight.kt** — replace with expanded state in SearchPill.

**New SearchPill.kt:**
```kotlin
@Composable
fun SearchPill(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenFullSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```
- Collapsed: clickable Surface pill (current design)
- Expanded: `BasicTextField` in same pill shape + `Popup`/`DropdownMenu` below with history
- Scrim: parent renders a semi-transparent overlay when `expanded = true`

**Main.kt:**
- `Cmd+F` sets a `searchExpanded` state that's passed down to the active feed
- Remove `SearchSpotlight` rendering

### Phase 3: Fix tab click handlers (Fix 4)

**FeedScreen.kt:** Audit `onNavigateToFeed` callback path. Ensure all `FeedSource` types are handled.

## Acceptance Criteria

- [ ] Feed tabs are compact, search pill fills remaining center space
- [ ] Header wrapped in card-like Surface matching screenshot aesthetic
- [ ] Clicking SearchPill expands it into an input with dropdown below
- [ ] Cmd+F expands the search pill (no separate overlay)
- [ ] Typing in expanded search shows recent + saved searches
- [ ] Clicking outside or pressing Escape collapses search
- [ ] All feed tabs (Following, Global, custom) are clickable and switch feeds
- [ ] "+ More" removed from header (already in sidebar)
- [ ] Compiles, spotless clean

## Files Affected

| File | Action |
|------|--------|
| `desktopApp/.../ui/FeedScreen.kt` | Rewrite `FeedTabsHeader` — layout, card design, tab fix |
| `desktopApp/.../ui/search/SearchPill.kt` | Add expanded state, inline BasicTextField, dropdown |
| `desktopApp/.../ui/search/SearchSpotlight.kt` | Delete (replaced by inline expansion) |
| `desktopApp/.../Main.kt` | Remove SearchSpotlight rendering, wire Cmd+F to feed search expansion |

## Sources

- Brainstorm: `docs/brainstorms/2026-05-28-feat-desktop-search-spotlight-brainstorm.md`
- Deepen research: Dialog creates separate AWT window on desktop — use Box overlay or inline instead
- Existing pattern: `LightboxOverlay.kt` uses Box overlay (not Dialog)
