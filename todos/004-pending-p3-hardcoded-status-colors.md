---
status: pending
priority: p3
issue_id: "004"
tags: [code-review, theming, desktop]
---

# Hardcoded status colors should use theme tokens

## Problem Statement

32 inline Color() constructors across 10 desktop files use hardcoded RGB values for status indicators (green/red/amber). These don't adapt to dark/light mode properly.

## Findings

Key files: RelayStatusCard.kt, TorStatusIndicator.kt, LoginProgressSteps.kt, MediaServerSettings.kt, DevSettingsSection.kt, NewKeyWarningCard.kt, ProfileInfoCard.kt

Common hardcoded values:
- `Color(0xFF4CAF50)` green — should use a semantic success color
- `Color(0xFFF44336)` red — should use `MaterialTheme.colorScheme.error`
- `Color(0xFFFFB300)` amber — should use a semantic warning color
- `Color.Red` / `Color.Green` — bare Material colors

## Proposed Solutions

Extract semantic status colors as ColorScheme extensions:
```kotlin
val ColorScheme.statusSuccess: Color get() = if (isLight) Color(0xFF339900) else Color(0xFF99cc33)
val ColorScheme.statusError: Color get() = error
val ColorScheme.statusWarning: Color get() = if (isLight) Color(0xFFC09B14) else Color(0xFFE1C419)
```

## Acceptance Criteria

- [ ] No bare Color.Red/Green/Yellow in desktop UI files
- [ ] Status colors adapt properly to dark/light mode
