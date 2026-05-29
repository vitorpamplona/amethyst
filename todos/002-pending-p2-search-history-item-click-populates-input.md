---
status: pending
priority: p2
issue_id: "002"
tags: [code-review, ui, desktop, search]
---

# Tapping search history should populate input and search

## Problem Statement

Clicking a recent search item in the expanded search history currently just calls `onOpenFullSearch()` (opens full Search column). It should instead populate the search input with that query text and immediately start searching.

## Findings

- FeedScreen.kt SearchHistorySection: all history rows call `onOpenFullSearch()` on click
- Should instead: set `searchText` to the history item's text, which triggers `updateFromText()` and relay subscriptions

## Proposed Solutions

**Option A: Pass `onHistoryItemClick: (String) -> Unit` to SearchHistorySection**
- Callback sets `searchText = TextFieldValue(text)` in parent
- `LaunchedEffect(searchText.text)` triggers `updateFromText()` automatically
- Pros: clean separation. Cons: needs callback threading.

**Option B: Pass `searchText` MutableState directly**
- SearchHistorySection writes to the state directly
- Pros: simple. Cons: tight coupling.

## Acceptance Criteria

- [ ] Clicking a recent search item populates the search input with that text
- [ ] Search results start loading immediately after populating
- [ ] The search bar stays expanded (doesn't collapse)
