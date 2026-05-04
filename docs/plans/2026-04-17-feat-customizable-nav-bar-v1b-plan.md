---
title: "feat: Customizable Nav Bar (v1b)"
type: feat
status: active
date: 2026-04-17
origin: docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md
---

# Customizable Nav Bar (v1b)

## Overview

Make the sidebar nav bar user-customizable. Users pin/unpin screens from the App Drawer, reorder items via drag-to-sort, and persist their layout. Applies to both SinglePaneLayout's NavigationRail and DeckSidebar's quick-launch icons.

## Problem Statement

The current `navItems` list in `SinglePaneLayout.kt` is hardcoded — 11 items, fixed order, no user control. Users can't hide screens they don't use or promote ones they use frequently. DeckSidebar has no quick-launch icons at all (just "+" and Settings). The App Drawer (v1a) made all screens discoverable but didn't solve personalization.

## Proposed Solution

1. **`PinnedNavBarState`** — state holder managing an ordered list of pinned `DeckColumnType` items, backed by `DesktopPreferences`
2. **Context menus** on drawer items ("Pin to sidebar") and nav items ("Unpin", "Move up/down")
3. **Drag-to-reorder** on nav rail items
4. **DeckSidebar** gets pinned icons between the "+" button and the spacer
5. Default pinned items = current `navItems` list (migration-safe)

## Technical Approach

### Architecture

```
DesktopPreferences
  └── pinnedNavItems: String (JSON array of typeKey strings)

PinnedNavBarState (new class)
  ├── pinnedScreens: StateFlow<List<DeckColumnType>>
  ├── pin(type) / unpin(type) / move(from, to)
  ├── save() / load() — debounced, Jackson JSON
  └── isPinned(type): Boolean

SinglePaneLayout
  └── NavigationRail reads pinnedScreens instead of hardcoded navItems

DeckSidebar
  └── Renders pinned icons between "+" and spacer

AppDrawer
  └── DrawerScreenCard gets context menu: "Pin to sidebar" / "Unpin"
```

### New Files

| File | Purpose |
|------|---------|
| `desktopApp/.../ui/deck/PinnedNavBarState.kt` | State + persistence for pinned items |

### Modified Files

| File | Changes |
|------|---------|
| `DesktopPreferences.kt` | Add `pinnedNavItems` property |
| `SinglePaneLayout.kt` | Replace hardcoded `navItems` with `PinnedNavBarState.pinnedScreens`, add drag-reorder + context menu |
| `DeckSidebar.kt` | Accept `PinnedNavBarState`, render pinned icons, context menu to unpin |
| `AppDrawer.kt` | Add `onPinScreen`/`onUnpinScreen` callbacks, context menu on `DrawerScreenCard` |
| `Main.kt` | Create `PinnedNavBarState`, pass to SinglePaneLayout/DeckSidebar/AppDrawer |

### Implementation Phases

#### Phase 1: PinnedNavBarState + Persistence

**File: `PinnedNavBarState.kt`**

```kotlin
class PinnedNavBarState(
    private val saveScope: CoroutineScope,
) {
    private val _pinnedScreens = MutableStateFlow(DEFAULT_PINNED)
    val pinnedScreens: StateFlow<List<DeckColumnType>> = _pinnedScreens.asStateFlow()

    private var saveJob: Job? = null

    fun isPinned(type: DeckColumnType): Boolean =
        _pinnedScreens.value.any { it.typeKey() == type.typeKey() }

    fun pin(type: DeckColumnType) {
        if (isPinned(type)) return
        _pinnedScreens.update { it + type }
        scheduleSave()
    }

    fun unpin(type: DeckColumnType) {
        _pinnedScreens.update { list ->
            list.filter { it.typeKey() != type.typeKey() }
        }
        scheduleSave()
    }

    fun move(fromIndex: Int, toIndex: Int) {
        _pinnedScreens.update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices) return
            current.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            save()
        }
    }

    fun save() {
        try {
            val keys = _pinnedScreens.value.map { it.typeKey() }
            DesktopPreferences.pinnedNavItems = mapper.writeValueAsString(keys)
        } catch (e: Exception) {
            println("PinnedNavBarState: save failed: ${e.message}")
        }
    }

    fun load() {
        try {
            val json = DesktopPreferences.pinnedNavItems
            if (json.isBlank()) return
            val keys: List<String> = mapper.readValue(json)
            val loaded = keys.mapNotNull { key ->
                LAUNCHABLE_SCREENS.find { it.typeKey() == key }
            }
            if (loaded.isNotEmpty()) {
                _pinnedScreens.value = loaded
            }
        } catch (e: Exception) {
            println("PinnedNavBarState: load failed: ${e.message}")
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 500L
        private val mapper = jacksonObjectMapper()

        // Matches current navItems list — zero-migration default
        val DEFAULT_PINNED: List<DeckColumnType> = listOf(
            DeckColumnType.HomeFeed,
            DeckColumnType.Reads,
            DeckColumnType.Drafts,
            DeckColumnType.MyHighlights,
            DeckColumnType.Search,
            DeckColumnType.Bookmarks,
            DeckColumnType.Messages,
            DeckColumnType.Notifications,
            DeckColumnType.MyProfile,
            DeckColumnType.Chess,
            DeckColumnType.Settings,
        )
    }
}
```

**File: `DesktopPreferences.kt` — add property:**

```kotlin
private const val KEY_PINNED_NAV_ITEMS = "pinned_nav_items"

var pinnedNavItems: String
    get() = prefs.get(KEY_PINNED_NAV_ITEMS, "")
    set(value) { prefs.put(KEY_PINNED_NAV_ITEMS, value) }
```

#### Phase 2: SinglePaneLayout — Dynamic Nav Rail

Replace hardcoded `navItems` with `PinnedNavBarState`:

```kotlin
// Before: private val navItems = listOf(NavItem(...), ...)
// After: removed. NavItem data class also removed.

@Composable
fun SinglePaneLayout(
    // ... existing params ...
    pinnedNavBarState: PinnedNavBarState,  // NEW
) {
    val currentColumnType by singlePaneState.currentScreen.collectAsState()
    val pinnedScreens by pinnedNavBarState.pinnedScreens.collectAsState()
    // ...

    NavigationRail(...) {
        pinnedScreens.forEachIndexed { index, screenType ->
            NavigationRailItem(
                selected = currentColumnType == screenType && navStack.isEmpty(),
                onClick = {
                    singlePaneState.navigate(screenType)
                    navState.clear()
                },
                icon = {
                    Icon(screenType.icon(), screenType.title(), Modifier.size(22.dp))
                },
                label = {
                    Text(
                        screenType.title(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.contextMenuOpenDetector {
                    // context menu state set here — see Phase 4
                },
            )
        }
        // "More" button (App Drawer) stays at bottom
        NavigationRailItem(
            selected = false,
            onClick = onOpenAppDrawer,
            icon = { Icon(Icons.Default.Apps, "App Drawer", Modifier.size(22.dp)) },
            label = { Text("More", ...) },
        )
        Spacer(Modifier.weight(1f))
        // ... relay health, bunker, tor indicators unchanged ...
    }
}
```

#### Phase 3: Context Menus

Desktop Compose supports `ContextMenuArea` or `CursorDropdownMenu`. Use `CursorDropdownMenu` (positioned at cursor) for right-click:

**Nav rail item context menu (SinglePaneLayout):**

```kotlin
var contextMenuTarget by remember { mutableStateOf<DeckColumnType?>(null) }
var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

// Inside NavigationRail, wrapping each item:
Box(
    modifier = Modifier.onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            contextMenuTarget = screenType
            // position from pointer
        }
    }
) {
    NavigationRailItem(...)
}

// After NavigationRail:
CursorDropdownMenu(
    expanded = contextMenuTarget != null,
    onDismissRequest = { contextMenuTarget = null },
) {
    DropdownMenuItem(
        text = { Text("Unpin from sidebar") },
        onClick = {
            contextMenuTarget?.let { pinnedNavBarState.unpin(it) }
            contextMenuTarget = null
        },
        leadingIcon = { Icon(Icons.Default.PushPin, null) },
    )
    DropdownMenuItem(
        text = { Text("Move up") },
        onClick = {
            val idx = pinnedScreens.indexOfFirst { it.typeKey() == contextMenuTarget?.typeKey() }
            if (idx > 0) pinnedNavBarState.move(idx, idx - 1)
            contextMenuTarget = null
        },
        enabled = pinnedScreens.indexOfFirst { it.typeKey() == contextMenuTarget?.typeKey() } > 0,
    )
    DropdownMenuItem(
        text = { Text("Move down") },
        onClick = {
            val idx = pinnedScreens.indexOfFirst { it.typeKey() == contextMenuTarget?.typeKey() }
            if (idx < pinnedScreens.size - 1) pinnedNavBarState.move(idx, idx + 1)
            contextMenuTarget = null
        },
        enabled = run {
            val idx = pinnedScreens.indexOfFirst { it.typeKey() == contextMenuTarget?.typeKey() }
            idx < pinnedScreens.size - 1
        },
    )
}
```

**App Drawer item context menu (AppDrawer.kt):**

```kotlin
// DrawerScreenCard gets right-click handler
@Composable
private fun DrawerScreenCard(
    type: DeckColumnType,
    isSelected: Boolean,
    isOpen: Boolean,
    isPinned: Boolean,           // NEW
    onClick: () -> Unit,
    onHover: () -> Unit,
    onTogglePin: () -> Unit,     // NEW
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .size(80.dp)
                .clickable(onClick = onClick)
                .onPointerEvent(PointerEventType.Enter) { onHover() }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                },
            // ... existing styling ...
        ) {
            // ... existing content ...
            // Add pin indicator
            if (isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    null,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        CursorDropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin from sidebar" else "Pin to sidebar") },
                onClick = {
                    onTogglePin()
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        if (isPinned) Icons.Default.PushPinSlash else Icons.Default.PushPin,
                        null,
                    )
                },
            )
        }
    }
}
```

#### Phase 4: DeckSidebar Pinned Icons

```kotlin
@Composable
fun DeckSidebar(
    pinnedNavBarState: PinnedNavBarState,  // NEW
    onAddColumn: () -> Unit,
    onNavigate: (DeckColumnType) -> Unit,  // NEW — adds column or focuses
    onOpenSettings: () -> Unit,
    // ... existing params ...
) {
    val pinnedScreens by pinnedNavBarState.pinnedScreens.collectAsState()

    Column(...) {
        Text("A", ...) // logo

        Spacer(Modifier.size(16.dp))

        IconButton(onClick = onAddColumn) {
            Icon(Icons.Default.Add, "Add Column", ...)
        }

        Spacer(Modifier.size(8.dp))

        // Pinned quick-launch icons
        pinnedScreens.take(MAX_DECK_PINNED).forEach { screenType ->
            IconButton(
                onClick = { onNavigate(screenType) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    screenType.icon(),
                    screenType.title(),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        // ... bunker, tor, settings unchanged ...
    }
}

private const val MAX_DECK_PINNED = 8 // deck sidebar is narrow, cap visible icons
```

#### Phase 5: Wire into Main.kt

```kotlin
// In App() composable:
val pinnedNavBarState = remember(appScope) {
    PinnedNavBarState(appScope).also { it.load() }
}

// Pass to SinglePaneLayout:
SinglePaneLayout(
    // ... existing ...
    pinnedNavBarState = pinnedNavBarState,
)

// Pass to DeckSidebar:
DeckSidebar(
    pinnedNavBarState = pinnedNavBarState,
    onNavigate = { type ->
        if (deckState.hasColumnOfType(type)) deckState.focusExistingColumn(type)
        else deckState.addColumn(type)
    },
    // ... existing ...
)

// Pass to AppDrawer:
if (showAppDrawer) {
    AppDrawer(
        openColumnTypes = ...,
        pinnedNavBarState = pinnedNavBarState,  // NEW
        onSelectScreen = { ... },
        onDismiss = { showAppDrawer = false },
    )
}
```

#### Phase 6: Drag-to-Reorder (Enhancement)

Use Compose `draggable` modifier on NavigationRailItems for vertical reorder:

```kotlin
// In SinglePaneLayout NavigationRail:
var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
var dragSourceIndex by remember { mutableStateOf<Int?>(null) }

pinnedScreens.forEachIndexed { index, screenType ->
    val offsetY = remember { Animatable(0f) }

    NavigationRailItem(
        modifier = Modifier
            .pointerInput(index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragSourceIndex = index
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Calculate target index from vertical offset
                        val targetIdx = (index + (dragAmount.y / ITEM_HEIGHT).roundToInt())
                            .coerceIn(0, pinnedScreens.size - 1)
                        dragTargetIndex = targetIdx
                    },
                    onDragEnd = {
                        val from = dragSourceIndex
                        val to = dragTargetIndex
                        if (from != null && to != null && from != to) {
                            pinnedNavBarState.move(from, to)
                        }
                        dragSourceIndex = null
                        dragTargetIndex = null
                    },
                    onDragCancel = {
                        dragSourceIndex = null
                        dragTargetIndex = null
                    },
                )
            }
            .graphicsLayer {
                // Visual feedback: slight scale-up on dragged item
                if (dragSourceIndex == index) {
                    scaleX = 1.1f
                    scaleY = 1.1f
                    alpha = 0.7f
                }
            },
        // ... rest of NavigationRailItem params ...
    )

    // Drop indicator line
    if (dragTargetIndex == index && dragSourceIndex != index) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
```

> **Note:** Exact drag implementation depends on NavigationRail's layout constraints. If NavigationRailItem doesn't compose well with drag gestures, fallback to context menu "Move up/down" only (Phase 3 already covers this). Drag is a nice-to-have enhancement.

## Acceptance Criteria

### Functional

- [ ] Nav rail items in SinglePaneLayout driven by `PinnedNavBarState` (not hardcoded)
- [ ] Right-click nav rail item shows "Unpin", "Move up", "Move down"
- [ ] Right-click drawer item shows "Pin to sidebar" / "Unpin from sidebar"
- [ ] Pin/unpin immediately updates nav rail
- [ ] Move up/down immediately reorders nav rail
- [ ] DeckSidebar shows pinned quick-launch icons
- [ ] Pinned items persist across app restarts (DesktopPreferences)
- [ ] Default pinned items = current navItems (zero-migration)
- [ ] "More" / App Drawer button always present at bottom of nav rail
- [ ] Drawer shows pin indicator on already-pinned items
- [ ] Works in both Single Pane and Deck modes

### Non-Functional

- [ ] Pin/unpin is instant (no network, pure state)
- [ ] Save debounced 500ms (matches DeckState pattern)
- [ ] JSON persistence uses Jackson (matches DeckState pattern)
- [ ] No new dependencies added

### Stretch (if time)

- [ ] Drag-to-reorder via long-press on nav rail items
- [ ] Keyboard shortcut Cmd+1-9 maps to pinned items (not hardcoded)

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| `NavigationRailItem` may not support drag gestures cleanly | Fallback to context menu Move up/down. Drag is Phase 6 (optional) |
| `CursorDropdownMenu` API may differ across Compose versions | Check Compose 1.7.x API; fallback to `DropdownMenu` with manual positioning |
| Right-click detection on Desktop | Use `onPointerEvent(PointerEventType.Press)` + check `event.button == PointerButton.Secondary` |
| Preferences key collision on upgrade | New key `pinned_nav_items` doesn't conflict with existing keys |
| Pinned item references stale if `DeckColumnType` sealed class changes | `load()` uses `mapNotNull` — unknown keys silently dropped, like DeckState |
| Max pinned items could overflow nav rail | SinglePaneLayout: scrollable NavigationRail. DeckSidebar: cap at 8 |

## Open Questions

- Should there be a max number of pinned items, or let the nav rail scroll?
- Should Settings always be pinned (non-removable) as an escape hatch?
- Should drag-to-reorder work in DeckSidebar too, or just SinglePaneLayout?
- Should pinned items be shared between Single Pane and Deck modes, or separate lists?
- Should Cmd+1-9 shortcuts be remapped to pinned items in v1b, or defer to v1c workspaces?
