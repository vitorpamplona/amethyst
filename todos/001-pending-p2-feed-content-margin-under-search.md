---
status: pending
priority: p2
issue_id: "001"
tags: [code-review, ui, desktop, search]
---

# Feed content needs more margin below search bar

## Problem Statement

When the search bar is expanded, feed items below get partially hidden by the expanded search card. The spacer height (60dp) that reserves space for the header is not enough when the search card expands with history/results.

## Findings

- FeedScreen.kt line 587: `Spacer(Modifier.height(60.dp))` reserves space for the collapsed header only
- When search expands, the card grows downward but the feed content doesn't shift
- Feed items near the top get obscured by the expanded search card + scrim

## Proposed Solutions

**Option A: Dynamic spacer based on search state**
- When `searchActive`, increase spacer to match expanded card height (~300dp)
- Pros: exact spacing. Cons: needs to track card height dynamically.

**Option B: Add extra bottom padding to expanded card**
- Feed items already have their own padding. Just ensure the search card's expanded area doesn't overlap.
- Pros: simple. Cons: may not cover all cases.

## Acceptance Criteria

- [ ] Feed items below the search bar are fully visible when search is collapsed
- [ ] When search expands, feed content is not obscured by the expanded card
- [ ] Smooth transition when expanding/collapsing
