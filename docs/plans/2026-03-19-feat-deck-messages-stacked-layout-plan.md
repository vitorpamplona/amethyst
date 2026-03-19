---
title: "feat: Stacked messages layout in deck columns"
type: feat
status: completed
date: 2026-03-19
deepened: 2026-03-19
origin: docs/brainstorms/2026-03-19-deck-messages-stacked-layout-brainstorm.md
---

# feat: Stacked Messages Layout in Deck Columns

## Enhancement Summary

**Deepened on:** 2026-03-19
**Files to change:** 4
**Approach:** Add `compactMode` flag, conditional layout in `DesktopMessagesScreen`

### Key Implementation Details
1. `ConversationListPane` width is hardcoded at line 122 (`Modifier.width(280.dp)`) — remove it, let caller control width
2. `ChatPane` header (lines 211-231) uses `ChatroomHeader`/`GroupChatroomHeader` — wrap with `Row` adding back arrow
3. `DesktopMessagesScreen` already has `selectedRoom` state and `clearSelection()` — stacked nav is pure layout change
4. Keyboard Escape handling already at line 102 calls `listState.clearSelection()` — works as-is for back nav

---

## Overview

Replace the side-by-side split-pane Messages layout with stacked navigation in deck columns. Full-width contact list OR full-width chat — clicking a conversation navigates to chat, back arrow returns to list. Single-pane mode keeps the current split layout.

## Problem Statement

In multi-deck mode, columns are 350-400dp wide. The current layout allocates 280dp to `ConversationListPane` (line 122) and the remaining 70-120dp to `ChatPane` — unusable.

(see brainstorm: `docs/brainstorms/2026-03-19-deck-messages-stacked-layout-brainstorm.md`)

---

## Step 1: Make ConversationListPane width flexible

**File:** `desktopApp/.../ui/chats/ConversationListPane.kt` (line 119-123)

**Current code:**
```kotlin
Column(
    modifier =
        modifier
            .width(280.dp)       // ← hardcoded, breaks compact mode
            .fillMaxHeight()
```

**Change:** Remove the hardcoded width from the composable. The caller controls width via the `modifier` parameter.

```kotlin
Column(
    modifier =
        modifier
            .fillMaxHeight()
```

**Call sites:**
- Split mode (DesktopMessagesScreen): passes no modifier → add `Modifier.width(280.dp)` at call site
- Compact mode: passes `Modifier.fillMaxWidth()` → uses full column width

### Research Insights

- `ConversationListPane` already accepts `modifier: Modifier = Modifier` (line 95) — just unused for width
- The keyboard nav (`onPreviewKeyEvent` at line 126) and `LazyColumn` (line 243) work at any width
- `ConversationCard` (line 268) uses `Modifier.fillMaxWidth()` — adapts automatically

---

## Step 2: Add `onBack` to ChatPane header

**File:** `desktopApp/.../ui/chats/ChatPane.kt` (lines 136-144, 211-231)

**Current signature:**
```kotlin
fun ChatPane(
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
    feedViewModel: ChatroomFeedViewModel,
    messageState: ChatNewMessageState,
    dmBroadcastStatus: DmBroadcastStatus = DmBroadcastStatus.Idle,
    onNavigateToProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

**Add:** `onBack: (() -> Unit)? = null` parameter.

**Current header (lines 211-231):**
```kotlin
// Header
if (isGroup) {
    GroupChatroomHeader(
        users = users,
        onClick = { users.firstOrNull()?.let { onNavigateToProfile(it.pubkeyHex) } },
    )
} else {
    users.firstOrNull()?.let { user ->
        ChatroomHeader(
            user = user,
            onClick = { onNavigateToProfile(user.pubkeyHex) },
        )
    } ?: run {
        Text(
            text = roomKey.users.firstOrNull()?.take(20) ?: "Unknown",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(10.dp),
        )
    }
}
```

**New header:** Wrap in a `Row` with conditional back arrow:

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(),
) {
    if (onBack != null) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to conversations",
            )
        }
    }

    Box(modifier = Modifier.weight(1f)) {
        // Existing header content (ChatroomHeader / GroupChatroomHeader / fallback)
    }
}
```

### Research Insights

- `Icons.AutoMirrored.Filled.ArrowBack` is already available in material-icons-extended
- `ChatroomHeader` and `GroupChatroomHeader` are shared composables from commons — don't modify them
- The `Row` wrapper doesn't affect the existing divider at line 233 (`HorizontalDivider()`)

---

## Step 3: Refactor DesktopMessagesScreen layout

**File:** `desktopApp/.../ui/chats/DesktopMessagesScreen.kt`

**Current:** Single `Row` layout (lines 92-168) with `ConversationListPane` + `VerticalDivider` + `ChatPane`/`EmptyState`.

**Change:** Add `compactMode: Boolean = false` parameter. Extract existing Row into a private `SplitMessagesContent` composable. Add a new `CompactMessagesContent` for stacked mode.

```kotlin
@Composable
fun DesktopMessagesScreen(
    account: IAccount,
    cacheProvider: ICacheProvider,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    compactMode: Boolean = false,  // NEW
    onNavigateToProfile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState = remember(account) {
        ChatroomListState(account, cacheProvider, relayManager, localCache, scope)
    }
    val selectedRoom by listState.selectedRoom.collectAsState()
    val listFocusRequester = remember { FocusRequester() }
    var showNewDmDialog by remember { mutableStateOf(false) }

    // Keyboard shortcuts (shared between modes)
    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val isModifier = if (isMacOS) event.isMetaPressed else event.isCtrlPressed
        when {
            event.key == Key.Escape -> {
                listState.clearSelection()
                true
            }
            event.key == Key.N && isModifier && event.isShiftPressed -> {
                showNewDmDialog = true
                true
            }
            else -> false
        }
    }

    if (compactMode) {
        CompactMessagesContent(
            selectedRoom = selectedRoom,
            listState = listState,
            account = account,
            cacheProvider = cacheProvider,
            scope = scope,
            onNavigateToProfile = onNavigateToProfile,
            listFocusRequester = listFocusRequester,
            showNewDmDialog = showNewDmDialog,
            onShowNewDm = { showNewDmDialog = true },
            keyHandler = keyHandler,
        )
    } else {
        SplitMessagesContent(
            selectedRoom = selectedRoom,
            listState = listState,
            account = account,
            cacheProvider = cacheProvider,
            scope = scope,
            onNavigateToProfile = onNavigateToProfile,
            listFocusRequester = listFocusRequester,
            keyHandler = keyHandler,
            onShowNewDm = { showNewDmDialog = true },
        )
    }

    // New DM dialog (shared)
    if (showNewDmDialog) { /* existing NewDmDialog code */ }
}
```

**CompactMessagesContent:**
```kotlin
@Composable
private fun CompactMessagesContent(
    selectedRoom: ChatroomKey?,
    listState: ChatroomListState,
    account: IAccount,
    cacheProvider: ICacheProvider,
    scope: CoroutineScope,
    onNavigateToProfile: (String) -> Unit,
    listFocusRequester: FocusRequester,
    showNewDmDialog: Boolean,
    onShowNewDm: () -> Unit,
    keyHandler: Modifier,
) {
    Box(modifier = Modifier.fillMaxSize().then(keyHandler)) {
        val currentRoom = selectedRoom
        if (currentRoom != null) {
            // Full-width chat with back arrow
            val feedViewModel = remember(currentRoom) {
                ChatroomFeedViewModel(currentRoom, account, cacheProvider)
            }
            val messageState = remember(currentRoom) {
                ChatNewMessageState(account, cacheProvider, scope)
            }
            val broadcastStatus = if (account is DesktopIAccount) {
                account.dmSendTracker.status.collectAsState().value
            } else DmBroadcastStatus.Idle

            ChatPane(
                roomKey = currentRoom,
                account = account,
                cacheProvider = cacheProvider,
                feedViewModel = feedViewModel,
                messageState = messageState,
                dmBroadcastStatus = broadcastStatus,
                onNavigateToProfile = onNavigateToProfile,
                onBack = { listState.clearSelection() },
            )
        } else {
            // Full-width contact list
            ConversationListPane(
                state = listState,
                selectedRoom = selectedRoom,
                onConversationSelected = { listState.selectRoom(it) },
                onNewConversation = onShowNewDm,
                focusRequester = listFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

**SplitMessagesContent:** Extract existing `Row` code verbatim from current `DesktopMessagesScreen`, adding `Modifier.width(280.dp)` to the `ConversationListPane` call.

---

## Step 4: Wire compactMode from deck

**File:** `desktopApp/.../ui/deck/DeckColumnContainer.kt` (line 206-214)

```kotlin
DeckColumnType.Messages -> {
    DesktopMessagesScreen(
        account = iAccount,
        cacheProvider = localCache,
        relayManager = relayManager,
        localCache = localCache,
        compactMode = true,   // ← ADD THIS
        onNavigateToProfile = onNavigateToProfile,
    )
}
```

`SinglePaneLayout` — no change needed, `compactMode` defaults to `false`.

---

## Edge Cases

| Scenario | Behavior | Verified by |
|----------|----------|-------------|
| Escape in chat | `clearSelection()` → back to list | Existing keyboard handler (line 102) |
| Escape in list | No-op (already no selection) | Same handler, `clearSelection()` on null is safe |
| New DM dialog in compact chat | Dialog opens over chat, selecting user switches to that chat | `showNewDmDialog` is shared state |
| Receiving DM while viewing list | Chatroom list updates via 2s polling | `ChatroomListState.refreshRooms()` |
| Receiving DM while in chat | Messages appear real-time via `ChatroomFeedViewModel` | No change needed |
| Back arrow + drag-drop | Drag-drop zone is on the ChatPane Column, unaffected by back arrow Row | Separate modifier chain |

---

## Acceptance Criteria

- [x] Deck Messages column shows full-width contact list (no split)
- [x] Clicking conversation navigates to full-width chat view
- [x] Back arrow visible in chat header (compact mode only)
- [x] Clicking back arrow returns to contact list
- [x] Escape key still returns to contact list
- [x] Single-pane mode unchanged (split layout preserved)
- [x] Keyboard navigation (up/down/enter) still works in contact list
- [x] New DM dialog still works in both modes
- [x] ConversationListPane uses full width in compact mode

## Files Changed

| File | Change | Lines affected |
|------|--------|---------------|
| `ConversationListPane.kt` | Remove hardcoded `width(280.dp)` | Line 122 |
| `ChatPane.kt` | Add `onBack` param, wrap header in Row with back arrow | Lines 136-144, 211-231 |
| `DesktopMessagesScreen.kt` | Add `compactMode`, extract `SplitMessagesContent`/`CompactMessagesContent` | Major refactor |
| `DeckColumnContainer.kt` | Pass `compactMode = true` | Line ~208 |

## Sources

- **Brainstorm:** `docs/brainstorms/2026-03-19-deck-messages-stacked-layout-brainstorm.md`
- `DesktopMessagesScreen.kt:75-213` — current split-pane layout
- `ChatPane.kt:136-144` — current signature; `211-231` — header section
- `ConversationListPane.kt:119-122` — hardcoded width; `95` — modifier param
- `DeckColumnContainer.kt:206-214` — Messages deck routing
