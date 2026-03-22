# Brainstorm: Stacked Messages Layout in Multi-Deck

**Date:** 2026-03-19
**Status:** Ready for planning

## What We're Building

Replace the side-by-side split-pane Messages layout (contact list + chat) with a stacked navigation in deck columns. Clicking a conversation navigates from the contact list to the chat view; a back arrow returns to the list. Single-pane (non-deck) mode keeps the current split layout.

## Why This Approach

In multi-deck mode, columns can be 350-400dp wide. The current split layout allocates 280dp to the contact list, leaving only 70-120dp for the chat pane — unusable. A stacked layout gives the full column width to whichever view is active.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Layout mode | Always stacked in deck columns | Simplest, works at any column width |
| Single-pane mode | Keep split layout | Plenty of horizontal space in single-pane |
| Back navigation | Back arrow in chat header | Discoverable; Escape key already works |
| State management | `selectedRoom` in `ChatroomListState` already controls this | No new state needed — just show list when null, chat when selected |

## Architecture

### Current Flow (DesktopMessagesScreen.kt)
```
Row {
    ConversationListPane(280dp)  // always visible
    VerticalDivider
    ChatPane(flex)               // or EmptyState
}
```

### New Flow (Deck Mode)
```
// When selectedRoom == null:
ConversationListPane(full width)

// When selectedRoom != null:
Column {
    BackArrow + ChatroomHeader
    ChatPane(full width)
}
```

### Implementation Approach

`DesktopMessagesScreen` already has `selectedRoom` state. The change is layout-only:

1. Add a `compactMode: Boolean` parameter to `DesktopMessagesScreen`
2. In deck mode (`compactMode = true`): show either list OR chat, not both
3. In single-pane mode (`compactMode = false`): keep current split Row
4. Add back arrow to `ChatPane` header when in compact mode
5. `clearSelection()` → back to list (already exists)

### What Changes

| Component | Change |
|-----------|--------|
| `DesktopMessagesScreen` | Add `compactMode` param; conditional layout (Row vs when/else) |
| `ChatPane` | Add `onBack` callback + back arrow in header when provided |
| `RootContent` | Pass `compactMode = true` to DesktopMessagesScreen |
| `SinglePaneLayout` `RootContent` | Pass `compactMode = false` |
| `ConversationListPane` | No changes — already works at any width |

### Edge Cases

- **Keyboard nav**: Escape already calls `clearSelection()` — works as back
- **New DM dialog**: Opens over whichever view is active — no change needed
- **Receiving DM while in list**: Room appears/updates in list normally
- **Receiving DM while in chat**: Messages appear in real-time — no change

## Open Questions

None — all decisions resolved.
