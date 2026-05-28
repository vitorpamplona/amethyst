---
status: pending
priority: p3
issue_id: "005"
tags: [code-review, theming, desktop]
---

# ~40 inline RoundedCornerShape() calls should use MaterialTheme.shapes

## Problem Statement

The desktopApp module has ~40 inline `RoundedCornerShape()` calls with values 4dp, 8dp, 10dp, 12dp, 16dp. These should map to `MaterialTheme.shapes.*` tokens for consistency with the unified Amethyst shape system.

## Findings

Common clusters:
- `8.dp` (~20 occurrences) → `MaterialTheme.shapes.small`
- `12.dp` (~5 occurrences) → `MaterialTheme.shapes.medium`
- `16.dp` (~4 occurrences) → `MaterialTheme.shapes.large`
- `100.dp` / `999.dp` → pill shapes (acceptable as-is)

## Acceptance Criteria

- [ ] All 8dp corner shapes use MaterialTheme.shapes.small
- [ ] All 12dp corner shapes use MaterialTheme.shapes.medium
- [ ] All 16dp corner shapes use MaterialTheme.shapes.large
