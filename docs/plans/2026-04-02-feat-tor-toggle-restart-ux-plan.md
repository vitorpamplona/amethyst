---
title: "feat: Tor toggle restart UX — confirmation dialog + app rebuild"
type: feat
status: active
date: 2026-04-02
deepened: 2026-04-02
---

# feat: Tor toggle restart UX

## Enhancement Summary

**Deepened on:** 2026-04-02
**Agents:** Cleanup verifier, Simplicity reviewer, Compose Desktop edge cases

### Key Improvements
1. **No new files** — inline AlertDialog, merge restart into `onSettingsChanged`
2. **DisposableEffect for TorManager** — critical: stop Tor daemon on unmount to prevent process leak
3. **Restart on ALL settings changes** — simpler than mode-only distinction, and toggle changes don't propagate at runtime either
4. **key() confirmed safe** — well-understood Compose pattern, scopes cancel, remember blocks reset

---

## Overview

When user changes any Tor setting, show confirmation dialog. On confirm: save prefs → `appRestartKey++` → Compose unmounts App tree → remounts fresh. Window stays open, user stays logged in.

## Problem Statement

Toggling Tor at runtime breaks relay subscriptions. TCP connections switch but `DesktopRelaySubscriptionsCoordinator` doesn't re-subscribe. Hot-toggle requires deep quartz changes. Simpler: rebuild app state via `key()`.

## Implementation

### Phase 1: Add `appRestartKey` + TorManager DisposableEffect

**File: `Main.kt`**

At Window level (~line 187):
```kotlin
var appRestartKey by remember { mutableStateOf(0) }
```

Wrap `App(...)` call (~line 413):
```kotlin
key(appRestartKey) {
    App(...)
}
```

**CRITICAL: Add DisposableEffect for TorManager inside App:**
```kotlin
DisposableEffect(torManager) {
    onDispose {
        torManager.stopSync()
        activeTorManager = null
    }
}
```
Without this, old Tor daemon keeps running → two Tor processes → port conflicts.

**Merge restart into onSettingsChanged** in CompositionLocalProvider:
```kotlin
TorState(
    status = currentTorStatus,
    settings = torSettings,
    onSettingsChanged = { newSettings ->
        torSettings = newSettings
        DesktopTorPreferences.save(newSettings)
        torTypeFlow.value = newSettings.torType
        externalPortFlow.value = newSettings.externalSocksPort
        // Restart app to apply Tor changes
        appRestartKey++
    },
)
```

No separate `onRestartRequired` callback needed.

### Phase 2: Add confirmation dialog to TorSettingsSection

**File: `TorSettingsSection.kt`**

Before applying changes, show inline confirmation:
```kotlin
var pendingSettings by remember { mutableStateOf<TorSettings?>(null) }

// Mode selector onClick:
onClick = {
    pendingSettings = currentSettings.copy(torType = torType)
}

// Confirmation dialog:
pendingSettings?.let { settings ->
    AlertDialog(
        onDismissRequest = { pendingSettings = null },
        title = { Text("Restart Required") },
        text = { Text("Changing Tor settings requires restarting. Your session will be briefly interrupted.") },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChanged(settings)
                pendingSettings = null
            }) { Text("Restart") }
        },
        dismissButton = {
            TextButton(onClick = { pendingSettings = null }) { Text("Cancel") }
        },
    )
}
```

### Phase 3: Add confirmation to TorSettingsDialog Save

**File: `TorSettingsDialog.kt`**

Same pattern — Save button shows confirmation before calling `onSettingsChanged`:
```kotlin
var showRestartConfirm by remember { mutableStateOf(false) }

// Save button:
TextButton(onClick = { showRestartConfirm = true }) { Text("Save") }

if (showRestartConfirm) {
    AlertDialog(
        onDismissRequest = { showRestartConfirm = false },
        title = { Text("Restart Required") },
        text = { Text("Tor changes require restarting. Proceed?") },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChanged(editSettings)
                onDismiss()
            }) { Text("Restart") }
        },
        dismissButton = {
            TextButton(onClick = { showRestartConfirm = false }) { Text("Cancel") }
        },
    )
}
```

### Phase 4: Cleanup dead code

Remove from `Main.kt`:
- Any reconnect-related code (torReconnectJob, evictConnections calls)
- Debug logging (TorReconnect log lines)

Remove `evictConnections()` from `DesktopHttpClient.kt` (YAGNI — add back when needed).

## What Survives the key() Restart

| Component | Survives? | Why |
|-----------|-----------|-----|
| AccountManager | Yes | Outside App, at Window level |
| DeckState (columns) | Yes | Outside App, at Window level |
| Window position/size | Yes | windowState is outside App |
| Login session | Yes | AccountManager persists |
| Tor settings | Yes | Saved to java.util.prefs before restart |
| CoroutineScope | No — recreated | DisposableEffect cancels old |
| TorManager | No — recreated | DisposableEffect stops daemon |
| RelayManager | No — recreated | DisposableEffect disconnects |
| Subscriptions | No — recreated | DisposableEffect clears |
| Coil ImageLoader | Yes | Process-global singleton, not in Compose tree |

## Acceptance Criteria

- [ ] Any Tor setting change shows "Restart Required" dialog
- [ ] Cancel reverts — no settings change, no restart
- [ ] Confirm saves prefs + rebuilds app (window stays open)
- [ ] User stays logged in after rebuild
- [ ] Column layout preserved after rebuild
- [ ] Relays connected with correct mode after rebuild
- [ ] Feed loads after rebuild
- [ ] Old Tor daemon stopped before new one starts (no process leak)
- [ ] Shield icon correct after rebuild

## Files Changed

| File | Change |
|------|--------|
| `Main.kt` | Add `appRestartKey`, `key()` wrapper, `DisposableEffect` for TorManager, restart in `onSettingsChanged` |
| `TorSettingsSection.kt` | Confirmation dialog before applying mode change |
| `TorSettingsDialog.kt` | Confirmation dialog before Save |

**3 files changed, 0 new files, ~40 lines added.**
