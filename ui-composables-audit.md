# UI Composables Audit — KMP Migration Candidates

## Summary Stats

| Metric | Count |
|--------|-------|
| Total UI files in amethyst/ui/ | 990 |
| Already in commons | 30 |
| **No Android imports** (potential candidates) | **777 (78%)** |
| Blocked by LocalContext | 98 |
| Blocked by android.content.Context | 47 |
| Blocked by android.content.Intent | 26 |
| Blocked by NavController | 3 |
| Blocked by Toast | 15 |
| Blocked by ActivityResultContracts | 9 |
| Blocked by BackHandler | 12 |

## Top Android Blockers (by import frequency)

1. **android.content.Context** (47 files) — clipboard, file ops, toasts, intents
2. **android.content.Intent** (26 files) — sharing, opening URLs
3. **android.annotation.SuppressLint** (19) — usually harmless, can be replaced with `@Suppress`
4. **android.widget.Toast** (15) — needs KMP abstraction
5. **android.net.Uri** (13) — needs KMP URL type
6. **androidx.activity.compose.BackHandler** (12) — needs Compose MP equivalent
7. **android.os.Build** (10) — version checks
8. **ActivityResultContracts** (9) — camera, file picker
9. **android.Manifest** (7) — permission requests
10. **android.app.Activity** (6) — lifecycle

## ui/note/ Deep Dive (best migration candidates)

127 out of ~200 files in `ui/note/` have zero Android imports. These are pure Compose content renderers — they take data models and render UI. Top candidates:

### Tier 1 — Pure renderers, move as-is
- `ChatMessage.kt`, `PrivateMessage.kt`, `PublicMessage.kt` — message rendering
- `LongForm.kt`, `Wiki.kt`, `CodeSnippet.kt` — content types
- `Video.kt`, `PictureDisplay.kt`, `VideoDisplay.kt` — media display
- `ZapEvent.kt`, `Reaction.kt`, `Badge.kt` — event types
- `Gallery.kt`, `Thread.kt`, `LiveActivity.kt` — complex layouts
- `BlankNote.kt`, `ForkInfo.kt`, `BoostedMark.kt` — small components
- `Icons.kt`, `ZapFormatter.kt`, `Loaders.kt` — utilities
- `NIP05VerificationDisplay.kt`, `UserCompose.kt`, `UserProfilePicture.kt`
- `ReplyInformation.kt`, `DisplayHashtags.kt`, `DisplayLocation.kt`

### Tier 2 — Need minor abstraction
- Files using `@SuppressLint` — replace with `@Suppress`, then move
- Files using `BackHandler` — needs Compose MP back handler abstraction

### Tier 3 — Need platform interface
- Files using `LocalContext` (98 files) — need clipboard/intent/toast abstractions
- Files using `Intent` (26 files) — need sharing/URL opening interface

## Key Navigation Insight

Only **3 files** reference NavController directly — navigation is already well-isolated. The main navigation layer is in `ui/screen/` (route definitions, navigation graphs), not in the composable content.

## Recommended Strategy

1. **Start with ui/note/ leaf composables** — 127 files, zero Android deps, pure content renderers
2. **Batch by feature** — move all note type renderers together (messages, media, events)
3. **Create platform interfaces** for the top blockers:
   - `IPlatformClipboard` — copy to clipboard
   - `IPlatformShare` — share intent
   - `IPlatformToast` — show toast/snackbar
   - `IPlatformUri` — URL handling
4. **Leave navigation in amethyst/** — only 3 files, well-isolated
5. **Leave permission/camera/file picker in amethyst/** — deeply Android-specific

## Risk Assessment

Low risk — 78% of UI files have no Android imports. The typealias pattern used for model files works for composables too. Compose Multiplatform is the same API surface, so most `@Composable` functions work unchanged.
