---
status: pending
priority: p2
issue_id: "003"
tags: [code-review, ui, desktop, search, keyboard]
---

# Cmd+F should be context-aware: inline on feeds, full search elsewhere

## Problem Statement

Cmd+F currently toggles `feedSearchActiveState` which only works on the Home/Feeds screen. When on Messages, Settings, Bookmarks, or other non-feed screens, Cmd+F should open the full Search column instead.

## Findings

- Main.kt MenuBar: Cmd+F sets `feedSearchActiveState.value = !feedSearchActiveState.value`
- FeedScreen reads `LocalFeedSearchActive.current` — only works when FeedScreen is visible
- On other screens (Messages, Settings, etc.), the state changes but nothing happens visually

## Proposed Solutions

**Option A: Check current screen type in Cmd+F handler**
- Read `activeColumnType` from deck/single-pane state
- If HomeFeed/GlobalFeed/CustomFeed → toggle inline search
- Otherwise → navigate to Search column

**Option B: Use two shortcuts**
- Cmd+F → always opens inline search on feed (no-op on other screens)
- Cmd+Shift+F → always opens full Search column

## Acceptance Criteria

- [ ] Cmd+F on Home/Feeds screen → inline search expands
- [ ] Cmd+F on any other screen → opens full Search column/screen
- [ ] Cmd+F on already-expanded search → collapses it
