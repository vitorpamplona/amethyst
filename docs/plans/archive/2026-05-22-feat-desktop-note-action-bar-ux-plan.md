---
title: "feat: Desktop note action bar — long-press details + right-click customize"
type: feat
status: active
date: 2026-05-22
origin: docs/brainstorms/2026-05-21-note-action-bar-ux-brainstorm.md
deepened: 2026-05-22
---

# Desktop Note Action Bar — Long-Press Details + Right-Click Customize

> **Status:** shipped — Long-press/right-click note action gestures shipped in desktop NoteActions.kt.
> _Audited 2026-06-30._


## Enhancement Summary

**Deepened on:** 2026-05-22
**Sections enhanced:** 4
**Research agents used:** compose-expert, desktop-expert, compose-modifier-and-layout-style, compose-side-effects

### Key Improvements from Research
1. Use `sealed class ActivePopup` for mutually exclusive popup state
2. Use `DropdownMenu` for simple option lists (emoji picker, repost), `Popup` for rich content (zap/reaction details)
3. Preserve ripple by explicitly passing `indication = ripple(bounded = false, radius = 16.dp)` when replacing `IconButton`
4. Mark `ZapReceipt` as `@Immutable`, consider `ImmutableList` for list params

### New Considerations Discovered
- `Popup` creates a separate AWT window on JVM — always pass `PopupProperties(focusable = true)` for dismiss-on-click-outside
- Modifier chain order: `combinedClickable` before `onPointerEvent` (ripple wraps full area)
- Skip popup animations on desktop — instant feels right, `fadeIn(tween(100))` at most
- Metadata loading in popup: key `LaunchedEffect` on note ID, not popup visibility

## Overview

Add long-press popups and right-click customization to the desktop note action bar. Currently click = action and right-click = custom zap dialog. After this change, every action icon supports three gestures: click (action), long-press (view details), right-click (customize).

(see brainstorm: docs/brainstorms/2026-05-21-note-action-bar-ux-brainstorm.md)

## Interaction Model

| Action | Click | Long Press (~500ms) | Right-Click |
|--------|-------|---------------------|-------------|
| Reply | Open reply composer | Open thread | — |
| Like | React with + | Floating popup: who reacted + emoji | Emoji picker (DropdownMenu) |
| Repost | Repost | — | Quote/Fork (DropdownMenu) |
| Zap | Quick zap 21 sats | Floating popup: zap receipts | Custom zap dialog (existing) |
| Bookmark | Public/private dialog | — | — |

## Technical Approach

### Phase 1: Long-Press Detection + Zap Receipts Popup

**Goal:** Add long-press to zap icon showing floating zap receipts popup.

#### Step 1.1: Pass `Note` to NoteActionsRow

Currently `NoteActionsRow` receives `zapReceipts: List<ZapReceipt> = emptyList()` — callers pass empty lists. The actual zap/reaction data lives on the `Note` object.

**Change:** Add `note: Note? = null` parameter to `NoteActionsRow`.

**Files:**
- `desktopApp/src/jvmMain/.../ui/NoteActions.kt` — add `note: Note? = null` param
- `desktopApp/src/jvmMain/.../ui/FeedScreen.kt` — pass `note` (already available)
- Others keep default null (BookmarksScreen, ReadsScreen, ArticleReaderScreen)

#### Step 1.2: Popup State as Sealed Class

Replace individual boolean states with a single sealed class to ensure mutual exclusivity:

```kotlin
sealed class ActivePopup {
    data object None : ActivePopup()
    data object ZapReceipts : ActivePopup()
    data object Reactions : ActivePopup()
    data object EmojiPicker : ActivePopup()
    data object RepostOptions : ActivePopup()
}
var activePopup by remember { mutableStateOf<ActivePopup>(ActivePopup.None) }
```

#### Step 1.3: Replace `IconButton` with `combinedClickable` Box

Replace the zap `IconButton` with `Box` + `combinedClickable`. Preserve ripple explicitly.

```kotlin
Box(
    modifier = Modifier
        .size(32.dp)
        .combinedClickable(
            onClick = { /* quick zap */ },
            onLongClick = { activePopup = ActivePopup.ZapReceipts },
            indication = ripple(bounded = false, radius = 16.dp),
            interactionSource = remember { MutableInteractionSource() },
        )
        .onPointerEvent(PointerEventType.Press) { pointerEvent ->
            if (pointerEvent.buttons.isSecondaryPressed) {
                showZapDialog = true
            }
        },
    contentAlignment = Alignment.Center,
) {
    Icon(Zap, ...)
}
```

**Research insight:** `combinedClickable` before `onPointerEvent` in chain — ripple wraps full area, right-click handler sits inside.

#### Step 1.4: Zap Receipts Floating Popup

New composable using `Popup` + `ElevatedCard` (rich scrollable content):

```kotlin
@Composable
fun ZapReceiptsPopup(
    note: Note,
    localCache: DesktopLocalCache,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, -popupHeightPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true), // required for desktop dismiss
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 300.dp)
                    .padding(12.dp),
            ) {
                // Header: total sats
                // Sorted receipts: sender name + amount + message
            }
        }
    }
}
```

**Data access from Note:**
```kotlin
val zapEntries = note.zaps.mapNotNull { (request, receipt) ->
    val sender = request.author?.toBestDisplayName()
        ?: request.event?.pubKey?.take(8)
        ?: return@mapNotNull null
    val amount = (receipt?.event as? LnZapEvent)?.amount?.toLong()
        ?: return@mapNotNull null
    Triple(sender, amount, request.event?.content?.ifBlank { null })
}.sortedByDescending { it.second }
```

**Metadata loading:** Use `LaunchedEffect(note.idHex)` to fetch unknown sender metadata. Coroutine auto-cancels when popup leaves composition.

**Empty state:** If `zapEntries` is empty, show "No zaps yet" text.

### Phase 2: Reactions Popup

#### Step 2.1: `combinedClickable` for Like Icon

Same pattern as zap — click = react, long-press = `activePopup = ActivePopup.Reactions`.

#### Step 2.2: Reactions Floating Popup

Same `Popup` + `ElevatedCard` pattern. Content grouped by emoji:

```kotlin
@Composable
fun ReactionsPopup(
    note: Note,
    onDismiss: () -> Unit,
) {
    // note.reactions: Map<String, List<Note>>
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp)) {
                // Header: total count
                note.reactions.forEach { (emoji, reactionNotes) ->
                    // Section: emoji + list of sender names
                }
            }
        }
    }
}
```

**Research insight:** Use `derivedStateOf` for the grouped reaction view to avoid recomposition on every upstream emission.

### Phase 3: Right-Click Emoji Picker

#### Step 3.1: Add right-click to Like Icon

Add `onPointerEvent` secondary press → `activePopup = ActivePopup.EmojiPicker`.

#### Step 3.2: Emoji Picker as DropdownMenu

Use `DropdownMenu` (not raw `Popup`) — matches existing `AccountSwitcherDropdown` pattern. Simple option list.

```kotlin
DropdownMenu(
    expanded = activePopup is ActivePopup.EmojiPicker,
    onDismissRequest = { activePopup = ActivePopup.None },
) {
    listOf("+", "\u2764\ufe0f", "\ud83e\udd19", "\ud83d\udd25", "\ud83d\udc40", "\ud83d\ude02").forEach { emoji ->
        DropdownMenuItem(
            text = { Text(emoji, fontSize = 20.sp) },
            onClick = {
                activePopup = ActivePopup.None
                onReact(emoji)
            },
        )
    }
}
```

### Phase 4: Right-Click Repost Options

#### Step 4.1: Repost DropdownMenu

Right-click on repost icon → `DropdownMenu` with Quote / Fork options:

```kotlin
DropdownMenu(
    expanded = activePopup is ActivePopup.RepostOptions,
    onDismissRequest = { activePopup = ActivePopup.None },
) {
    DropdownMenuItem(text = { Text("Repost") }, onClick = { /* repost */ })
    DropdownMenuItem(text = { Text("Quote") }, onClick = { /* quote */ })
}
```

---

## File Changes Summary

| File | Changes |
|------|---------|
| `NoteActions.kt` | Add `note: Note?` param, `ActivePopup` sealed class, replace `IconButton` with `combinedClickable` for zap/like/repost, add `ZapReceiptsPopup`, `ReactionsPopup`, emoji picker, repost options. Mark `ZapReceipt` as `@Immutable`. |
| `FeedScreen.kt` | Pass `note` to `NoteActionsRow` |

## Acceptance Criteria

- [ ] Long-press (~500ms) on zap icon shows floating popup with zap receipts (sender, amount, message)
- [ ] Long-press on like icon shows floating popup with reactions grouped by emoji
- [ ] Long-press on reply icon opens thread (same as click)
- [ ] Right-click on zap icon opens custom zap dialog (existing behavior preserved)
- [ ] Right-click on like icon opens emoji picker (DropdownMenu)
- [ ] Right-click on repost icon shows Quote/Fork options (DropdownMenu)
- [ ] Single click still works for all actions (zap, react, repost, reply, bookmark)
- [ ] Popups are mutually exclusive (opening one closes others)
- [ ] Popups dismiss on click outside (`PopupProperties(focusable = true)`)
- [ ] Popups are scrollable when content exceeds 300dp
- [ ] No crash when long-pressing on notes with 0 zaps/reactions (empty state)
- [ ] Ripple preserved on all action icons
- [ ] Compiles, spotless clean, tests pass

## Implementation Order

1. **Phase 1** — Zap long-press popup (highest value, proves the pattern)
2. **Phase 2** — Reactions popup (same pattern, different data)
3. **Phase 3** — Emoji picker (small addition)
4. **Phase 4** — Repost options (nice-to-have)

## Technical Notes from Research

### Modifier Chain Order
```
Modifier
    .size(32.dp)                    // identity: fixed touch target
    .combinedClickable(...)          // identity: click + long-press
    .onPointerEvent(Press) { ... }   // identity: right-click
```
`combinedClickable` first (provides ripple/indication), `onPointerEvent` after.

### Popup vs DropdownMenu Decision
| Content Type | API |
|-------------|-----|
| Rich scrollable content (zap receipts, reactions) | `Popup` + `ElevatedCard` |
| Simple option list (emoji picker, repost options) | `DropdownMenu` + `DropdownMenuItem` |

### Desktop-Specific
- `PopupProperties(focusable = true)` is **required** on JVM desktop for click-outside dismiss
- `Popup` creates separate AWT window — can extend beyond parent window bounds (good)
- Skip animations or use `fadeIn(tween(100))` at most — desktop users expect crisp/instant

### Side Effects in Popups
- Metadata loading: `LaunchedEffect(note.idHex)` — auto-cancels when popup leaves composition
- No `DisposableEffect` needed unless opening relay subscriptions
- Use `derivedStateOf` for grouped reaction view

## Sources

- **Origin brainstorm:** [docs/brainstorms/2026-05-21-note-action-bar-ux-brainstorm.md](docs/brainstorms/2026-05-21-note-action-bar-ux-brainstorm.md) — interaction model, popup style, per-action behavior
- Android `ReactionsRow.kt` — `combinedClickable`, `Popup`, animation patterns
- Android `Note.kt` — `reactions: Map<String, List<Note>>`, `zaps: Map<Note, Note?>`
- Desktop `NoteActions.kt` — `onPointerEvent` right-click, `ZapReceiptsDialog`
- Desktop `AccountSwitcherDropdown.kt` — `DropdownMenu` scroll/height pattern
- Desktop `ChatBubbleLayout.kt:139` — `combinedClickable` proven on JVM desktop
