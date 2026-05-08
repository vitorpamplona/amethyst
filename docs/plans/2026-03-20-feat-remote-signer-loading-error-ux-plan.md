---
title: "feat: Remote Signer Loading & Error UX"
type: feat
status: active
date: 2026-03-20
origin: docs/brainstorms/2026-03-20-remote-signer-ux-brainstorm.md
---

# feat: Remote Signer Loading & Error UX

## Overview

Add proper loading indicators and error handling for all NIP-46 remote signer (bunker) operations. Currently, signing timeouts crash as AWT system popup. Fix with a lightweight `SigningState` holder + catch-all helper, reusing existing UI patterns (inline `CircularProgressIndicator`, snackbar feedback).

## Problem Statement

- 9 signing call sites — only zaps and note publishing have error handling
- Reactions, reposts, bookmarks crash on 30s timeout (`TimedOutException`)
- No loading feedback while waiting for signer approval
- Exceptions propagate to AWT unhandled exception handler

## Proposed Solution

Two lightweight layers (see brainstorm: `docs/brainstorms/2026-03-20-remote-signer-ux-brainstorm.md`):

### Layer 1: SigningState

Per-component state holder. Tracks Idle/Pending/Error — no Success state (existing UI like filled heart already handles success). Uses version counter to prevent race conditions on auto-reset.

**File:** `desktopApp/.../ui/signing/SigningState.kt`

```kotlin
@Stable
class SigningState {
    var state by mutableStateOf<SigningOpState>(SigningOpState.Idle)
        private set
    private var version = 0

    suspend fun <T> execute(
        operation: String,
        block: suspend () -> T,
    ): T? {
        val myVersion = ++version
        state = SigningOpState.Pending(operation, System.currentTimeMillis())
        return try {
            val result = withContext(Dispatchers.IO) { block() }
            state = SigningOpState.Idle
            result
        } catch (e: SignerExceptions.TimedOutException) {
            setError(myVersion, "Signer timed out")
            null
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            setError(myVersion, "Signing rejected")
            null
        } catch (e: SignerExceptions) {
            setError(myVersion, e.message ?: "Signing failed")
            null
        }
    }

    private suspend fun setError(v: Int, message: String) {
        state = SigningOpState.Error(message)
        delay(5000)
        if (version == v) state = SigningOpState.Idle
    }

    fun reset() { state = SigningOpState.Idle }
}

sealed class SigningOpState {
    data object Idle : SigningOpState()
    data class Pending(val operation: String, val startTimeMs: Long) : SigningOpState()
    data class Error(val message: String) : SigningOpState()
}
```

Key design decisions from deepening:
- **Version counter** prevents race condition where auto-reset overwrites a new Pending state
- **`withContext(IO)`** wraps block — signer.sign() does network I/O, shouldn't block Main
- **`@Stable`** annotation for Compose recomposition efficiency
- **No `Success` state** — existing UI (filled heart, colored repost icon) already handles success
- **No `retry` lambda** — tapping the button again = retry
- **No `onError` callback** — snackbar feedback added at call site via existing `onZapFeedback` pattern

### Layer 2: UI Integration

No new composables — reuse existing patterns:
- **Loading:** Inline `CircularProgressIndicator(Modifier.size(16.dp))` (already used for zap loading)
- **Error in feed:** Reuse `onZapFeedback(ZapFeedback.Error(message))` → existing snackbar in Main.kt
- **Error in dialogs:** Button turns red (`errorContainer`), shows error text, resets after 5s
- **Elapsed time in dialogs:** `SigningStatusBar` at popup bottom (only for ComposeNoteDialog, not action buttons)

Per-button composable wrapping to prevent full-row recomposition:

```kotlin
@Composable
fun SigningAwareButton(
    signingState: SigningState,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val s = signingState.state) {
        is SigningOpState.Pending -> {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        is SigningOpState.Error -> {
            Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.error, modifier = modifier)
        }
        else -> {
            IconButton(onClick = onClick, modifier = modifier.size(32.dp)) {
                Icon(icon, contentDescription, Modifier.size(20.dp))
            }
        }
    }
}
```

### SigningStatusBar (ComposeNoteDialog only)

Shows "Waiting for signer approval... (12s)" anchored to dialog bottom with rounded corners. Only used in ComposeNoteDialog — not for action buttons.

```kotlin
@Composable
fun SigningStatusBar(opState: SigningOpState, modifier: Modifier = Modifier)
```

## Implementation

### Phase 1: SigningState + UI Components in Commons

- Create `commons/src/commonMain/.../ui/signing/SigningState.kt`
- Create `commons/src/commonMain/.../ui/signing/SigningAwareButton.kt`
- Create `commons/src/commonMain/.../ui/signing/SigningStatusBar.kt`
- Verify `commons` has dependency on quartz (for `SignerExceptions`) — already does

### Phase 2: Wire all call sites

**NoteActions.kt** — reaction, repost, bookmark buttons:
- Each gets its own `remember { SigningState() }`
- Replace current icon rendering with `SigningAwareButton`
- On error, also call `onZapFeedback(ZapFeedback.Error(message))` for snackbar
- Keep existing `isLiked`, `isReposted` booleans — they track liked/reposted STATUS, not signing progress (different concerns)

**ComposeNoteDialog.kt** — post button:
- Add `SigningState` for signing, replace `isPosting` boolean
- Add `SigningStatusBar` at dialog bottom
- Button: spinner while pending, red + error text on failure

**UserProfileScreen.kt** — follow/unfollow + profile edit:
- Wrap existing signing calls in `signingState.execute()`
- Keep existing `FollowState` for follow status tracking

**BookmarksScreen.kt** — bookmark decryption:
- Wrap decryption in `signingState.execute()` to catch signer errors

## Acceptance Criteria

- [x] `SigningState` class with version-counter race protection
- [x] `SigningAwareButton` composable for action buttons
- [x] `SigningStatusBar` for ComposeNoteDialog
- [x] All 9 signing call sites wrapped — no more AWT crash dialogs
- [x] Inline spinner while signing pending
- [x] Error feedback via existing snackbar (feed) + red button state (dialogs)
- [x] Elapsed time shown in ComposeNoteDialog status bar
- [x] `isLiked`/`isReposted` booleans kept for status tracking (not conflated with signing)
- [x] `./gradlew :desktopApp:compileKotlin` passes
- [x] `./gradlew spotlessApply` clean

## Files Modified

| File | Change |
|------|--------|
| `commons/.../ui/signing/SigningState.kt` | **New** in commons/commonMain — ~40 LOC state holder (reusable across Android + Desktop) |
| `commons/.../ui/signing/SigningAwareButton.kt` | **New** in commons/commonMain — ~25 LOC per-button composable |
| `commons/.../ui/signing/SigningStatusBar.kt` | **New** in commons/commonMain — ~30 LOC dialog status bar |
| `desktopApp/.../ui/NoteActions.kt` | Wire SigningState to reaction/repost/bookmark |
| `desktopApp/.../ui/ComposeNoteDialog.kt` | Wire SigningState to post + add status bar |
| `desktopApp/.../ui/UserProfileScreen.kt` | Wire SigningState to follow/unfollow + edit |
| `desktopApp/.../ui/BookmarksScreen.kt` | Wire SigningState to bookmark decryption |

**Placement rationale:** `SigningState`, `SigningAwareButton`, `SigningStatusBar` use only Compose Multiplatform APIs (`mutableStateOf`, `CircularProgressIndicator`, Material3) and `SignerExceptions` from quartz. No platform-specific code. Placing in `commons/commonMain/` makes them reusable by Android app too.

## Sources & References

- **Origin:** [docs/brainstorms/2026-03-20-remote-signer-ux-brainstorm.md](docs/brainstorms/2026-03-20-remote-signer-ux-brainstorm.md)
- `desktopApp/.../ui/NoteActions.kt:538-624` — existing zap loading pattern
- `desktopApp/.../ui/NoteActions.kt:98-116` — ZapFeedback sealed class (reuse for signing errors)
- `quartz/.../nip01Core/signers/SignerExceptions.kt` — all exception types
- `quartz/.../nip46RemoteSigner/signer/NostrSignerRemote.kt:289` — exception conversion, 30s timeout

### Deepening Findings Applied

| Agent | Finding | Applied |
|-------|---------|---------|
| **Coroutines** | Race condition on delay reset | Version counter pattern |
| **Coroutines** | block() needs IO dispatcher | `withContext(Dispatchers.IO)` wraps block |
| **Compose** | Needs `@Stable` annotation | Added to SigningState |
| **Compose** | Full row recomposes on single button change | Extract `SigningAwareButton` per-button composable |
| **Compose** | Don't conflate signing state with liked/reposted status | Keep existing booleans, SigningState only tracks signing |
| **Simplicity** | No `Success` state needed (UI handles it) | Removed |
| **Simplicity** | No `retry` lambda needed (tap = retry) | Removed |
| **Simplicity** | Reuse existing `ZapFeedback.Error` + snackbar for errors | Yes, no new feedback mechanism |
| **Simplicity** | No separate `SigningProgressIndicator` composable | Use inline `CircularProgressIndicator` in `SigningAwareButton` |
