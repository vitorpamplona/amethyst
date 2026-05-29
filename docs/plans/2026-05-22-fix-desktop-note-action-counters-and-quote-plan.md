---
title: "fix: Desktop note action counters, quote boost, and boost detail popup"
type: fix
status: active
date: 2026-05-22
---

# Fix Desktop Note Action Counters, Quote Boost, and Boost Detail Popup

## Problems

### 1. Counters show zero for reactions, zaps, replies, reposts

Counts are passed as **static Int/Long parameters** to `NoteActionsRow`. They're read once at render time from `note.countReactions()`, `note.zaps.size`, etc. When new events arrive via relay subscriptions, the FlowSet invalidates — triggering recomposition of FeedNoteCard — but the counts are re-read from the same snapshot. The actual issue is **timing**: interaction subscriptions (`requestInteractions()`) fire for visible notes, but events may arrive after the initial render.

Additionally, **reply subscriptions are missing** — `DesktopRelaySubscriptionsCoordinator.requestInteractions()` subscribes for kinds 7, 9735, 6 but NOT kind 1 (replies).

### 2. Quote boost does nothing

The "Quote" `DropdownMenuItem` onClick just copies a note link to clipboard. It doesn't open a compose dialog. `ComposeNoteDialog` has no `quote` parameter.

### 3. Boost long-press has no popup

Repost icon has no `onLongClick` handler — long-press does nothing. Should show who boosted.

## Technical Approach

### Phase 1: Fix counters — make them reactive

**Root cause:** FeedNoteCard reads counts once as vals, passes them as params. Even though FlowSet observations are collected, the count vals are re-read from Note's mutable properties which DO update — but only if the recomposition actually re-reads them.

**Fix:** The FlowSet observation pattern is actually correct — collecting `flowSet.reactions.stateFlow` triggers recomposition which re-reads `note.countReactions()`. The problem may be that interaction events haven't arrived yet.

**Step 1.1: Add kind 1 (replies) to interaction subscriptions**

File: `desktopApp/src/jvmMain/.../subscriptions/DesktopRelaySubscriptionsCoordinator.kt`

In `requestInteractions()`, add kind 1 to the filter list:
```kotlin
Filter(kinds = listOf(1), tags = mapOf("e" to noteIds)),  // Replies
```

**Step 1.2: Verify flow collection triggers recomposition**

In `FeedScreen.kt`, the pattern is:
```kotlin
val reactionsState by flowSet.reactions.stateFlow.collectAsState()
// ...
val reactionCount = note.countReactions()
```

This should work — `collectAsState()` triggers recomposition, which re-reads `countReactions()`. If it's not working, add `reactionsState` as a key to `remember`:
```kotlin
val reactionCount = remember(reactionsState) { note.countReactions() }
```

### Phase 2: Fix quote boost

**Step 2.1: Add `quoteOf` parameter to ComposeNoteDialog**

File: `desktopApp/src/jvmMain/.../ui/ComposeNoteDialog.kt`

Add `quoteOf: Event? = null` parameter. When set, embed `nostr:${NEvent.create(event.id, event.pubKey, event.kind, relays)}` in the initial text and add "q" tag.

**Step 2.2: Wire Quote menu item to compose dialog**

File: `desktopApp/src/jvmMain/.../ui/NoteActions.kt`

Replace clipboard copy with opening ComposeNoteDialog:
```kotlin
var quoteEvent by remember { mutableStateOf<Event?>(null) }

// In Quote DropdownMenuItem onClick:
quoteEvent = event
activePopup = ActivePopup.None

// Render dialog:
if (quoteEvent != null) {
    ComposeNoteDialog(quoteOf = quoteEvent, onDismiss = { quoteEvent = null }, ...)
}
```

**Step 2.3: Build quote event with "q" tag**

When composing, use TextNoteEvent builder with "q" tag:
```kotlin
TextNoteEvent.build(
    message = "$userMessage\nnostr:${NEvent.create(quotedEvent.id, ...)}",
    tags = arrayOf(arrayOf("q", quotedEvent.id, relayHint ?: "", quotedEvent.pubKey)),
    signer = signer,
)
```

### Phase 3: Add boost detail popup

**Step 3.1: Add long-press to repost icon**

Same `combinedClickable` pattern. Long-press sets `activePopup = ActivePopup.Boosts`.

**Step 3.2: Create BoostsPopup**

New composable showing who boosted:
```kotlin
@Composable
fun BoostsPopup(note: Note, onDismiss: () -> Unit) {
    // note.boosts: List<Note>
    // Each boost Note has .author → display name
    Popup(properties = PopupProperties(focusable = true)) {
        ElevatedCard { ... }
    }
}
```

Add `Boosts` to the `ActivePopup` sealed class.

## File Changes

| File | Changes |
|------|---------|
| `DesktopRelaySubscriptionsCoordinator.kt` | Add kind 1 to `requestInteractions()` filter |
| `FeedScreen.kt` | Ensure count reads are keyed on flow state |
| `NoteActions.kt` | Wire quote to compose dialog, add boost long-press popup, add `ActivePopup.Boosts` |
| `ComposeNoteDialog.kt` | Add `quoteOf: Event?` param, embed quote in text + "q" tag |

## Acceptance Criteria

- [ ] Reaction count updates when new reactions arrive via relay
- [ ] Zap count/amount updates when new zaps arrive
- [ ] Reply count shows and updates
- [ ] Repost count shows and updates
- [ ] Kind 1 replies subscribed in interaction filters
- [ ] Quote menu item opens ComposeNoteDialog with quoted note
- [ ] Quote posts include "q" tag and embedded nostr: URI
- [ ] Long-press repost icon shows popup with who boosted
- [ ] Empty state for boosts popup: "No reposts yet"
- [ ] Compiles, spotless, tests pass
