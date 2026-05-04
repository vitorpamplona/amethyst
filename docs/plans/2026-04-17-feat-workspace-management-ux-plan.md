---
title: "feat: Workspace Management UX"
type: feat
status: active
date: 2026-04-17
origin: docs/brainstorms/2026-04-17-workspace-management-ux-brainstorm.md
---

# Workspace Management UX

## Summary

Add full workspace CRUD to the App Drawer: two-tab layout (Screens/Workspaces), unified search, workspace cards with editor dialog, Cmd+S save, and layout mode auto-switching on workspace change.

## Current State

| Component | File | Notes |
|-----------|------|-------|
| `AppDrawer` | `deck/AppDrawer.kt` | Single-purpose: screen grid + bottom `WorkspaceBar` (chip strip) |
| `AppDrawerState` | `deck/AppDrawer.kt` | Filters `LAUNCHABLE_SCREENS` only, no workspace awareness |
| `WorkspaceManager` | `deck/WorkspaceManager.kt` | Full CRUD, persistence via Jackson+`DesktopPreferences.workspaces` |
| `Workspace` | `deck/Workspace.kt` | Data class w/ `WorkspaceColumn`, `WorkspaceIcons` registry |
| `DeckState` | `deck/DeckState.kt` | `loadFromWorkspace()` exists, handles column loading |
| `SinglePaneState` | `deck/SinglePaneState.kt` | `navigate(type)` — no workspace integration yet |
| `LayoutMode` | `Main.kt:120` | Enum `SINGLE_PANE`/`DECK`, stored as `var` in `App()` composable |
| `WorkspaceBar` | `deck/AppDrawer.kt` | Bottom strip of `FilterChip`s — will be replaced |
| Keyboard shortcuts | `Main.kt` MenuBar | Cmd+K=drawer, Cmd+N=note, Cmd+D+Shift=deck toggle, Cmd+T=add col, Cmd+W=close col |

### Key Observation: layoutMode Threading

`layoutMode` is a `var` inside `App()` (Main.kt:217). The `onSwitchWorkspace` callback in the AppDrawer currently only calls `deckState.loadFromWorkspace()` — it does NOT change `layoutMode`. This must be fixed.

## Architecture

### Phase 1: AppDrawer Tab System + Workspace Cards

**Files modified:** `AppDrawer.kt`

#### 1a. Extend `AppDrawerState` for tab + unified search

```kotlin
enum class DrawerTab { SCREENS, WORKSPACES }

@Stable
private class AppDrawerState(
    private val workspaceManager: WorkspaceManager,
) {
    var searchQuery by mutableStateOf("")
    var selectedIndex by mutableStateOf(0)
    var activeTab by mutableStateOf(DrawerTab.SCREENS)
    var hashtagInput by mutableStateOf("")
    var awaitingHashtag by mutableStateOf(false)
    private var consumed by mutableStateOf(false)

    // -- Screens filtering (existing) --
    val filteredScreens: List<DeckColumnType> by derivedStateOf {
        if (searchQuery.isBlank()) LAUNCHABLE_SCREENS
        else LAUNCHABLE_SCREENS.filter {
            it.title().contains(searchQuery, ignoreCase = true) ||
                it.category().title.contains(searchQuery, ignoreCase = true)
        }
    }

    // -- Workspace filtering (new) --
    val filteredWorkspaces: List<Workspace> by derivedStateOf {
        val all = workspaceManager.workspaces.value
        if (searchQuery.isBlank()) all
        else all.filter { ws ->
            ws.name.contains(searchQuery, ignoreCase = true) ||
                ws.columns.any { col ->
                    col.typeKey.contains(searchQuery, ignoreCase = true)
                }
        }
    }

    // True when search is active — show unified results regardless of tab
    val isSearching: Boolean by derivedStateOf { searchQuery.isNotBlank() }

    // Unified: workspace results + screen results
    // Workspaces come first; selectedIndex spans both lists
    val totalResultCount: Int by derivedStateOf {
        if (isSearching) filteredWorkspaces.size + filteredScreens.size
        else filteredScreens.size // tab-based navigation uses per-tab indexing
    }

    // ... moveSelection, select, confirmHashtag remain the same
}
```

**Key design:** When `searchQuery` is non-empty, the drawer ignores tabs and renders unified results (workspaces at top, screens below). When empty, tabs control what's visible.

#### 1b. Two-tab header using `PrimaryTabRow` + `Tab`

```kotlin
// Inside AppDrawer, between search TextField and content
if (!state.isSearching) {
    PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
        DrawerTab.entries.forEach { tab ->
            Tab(
                selected = state.activeTab == tab,
                onClick = { state.activeTab = tab },
                text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}
```

#### 1c. Replace `WorkspaceBar` with full Workspaces tab

The bottom `WorkspaceBar` is removed. The Workspaces tab renders full workspace cards:

```kotlin
@Composable
private fun WorkspacesGrid(
    state: AppDrawerState,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Workspace) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaces by workspaceManager.workspaces.collectAsState()
    val activeIndex by workspaceManager.activeIndex.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Workspace?>(null) }

    LazyColumn(Modifier.padding(8.dp)) {
        itemsIndexed(workspaces) { index, ws ->
            WorkspaceCard(
                workspace = ws,
                isActive = index == activeIndex,
                onSwitch = {
                    val switched = workspaceManager.switchTo(index)
                    if (switched != null) {
                        onSwitchWorkspace(switched)
                        onDismiss()
                    }
                },
                onEdit = { editTarget = ws; showEditor = true },
                onDelete = { workspaceManager.deleteWorkspace(ws.id) },
                canDelete = workspaces.size > 1,
            )
        }
        // "+" card
        item {
            AddWorkspaceCard(onClick = { editTarget = null; showEditor = true })
        }
    }

    if (showEditor) {
        WorkspaceEditorDialog(
            initial = editTarget,
            onSave = { ws ->
                if (editTarget != null) workspaceManager.updateWorkspace(ws)
                else workspaceManager.addWorkspace(ws)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}
```

#### 1d. Workspace card layout

```kotlin
@Composable
private fun WorkspaceCard(
    workspace: Workspace,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onSwitch),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isActive) 8.dp else 2.dp,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(WorkspaceIcons.resolve(workspace.iconName), workspace.name, Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(workspace.name, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Layout mode badge
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (workspace.layoutMode == LayoutMode.DECK) "Deck" else "Single",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                // Column preview
                Text(
                    workspace.columns.joinToString(", ") { it.typeKey },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Active indicator
            if (isActive) {
                Icon(Icons.Default.Check, "Active", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp))
            }
        }
    }
}
```

### Phase 2: Workspace Editor Dialog

**Files modified:** new composable in `AppDrawer.kt` (or extract to `WorkspaceEditorDialog.kt` if large)

```kotlin
@Composable
fun WorkspaceEditorDialog(
    initial: Workspace?,
    onSave: (Workspace) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "Home") }
    var layoutMode by remember { mutableStateOf(initial?.layoutMode ?: LayoutMode.DECK) }
    var columns by remember {
        mutableStateOf(initial?.columns ?: listOf(Workspace.WorkspaceColumn("home")))
    }
    var singlePaneScreen by remember { mutableStateOf(initial?.singlePaneScreen) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Workspace" else "New Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Icon picker (grid of Material icons)
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkspaceIcons.availableNames.forEach { iName ->
                        IconButton(onClick = { iconName = iName }) {
                            Icon(
                                WorkspaceIcons.resolve(iName), iName,
                                tint = if (iName == iconName)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Layout mode toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Layout:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = layoutMode == LayoutMode.SINGLE_PANE,
                            onClick = { layoutMode = LayoutMode.SINGLE_PANE },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Single Pane") }
                        SegmentedButton(
                            selected = layoutMode == LayoutMode.DECK,
                            onClick = { layoutMode = LayoutMode.DECK },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Deck") }
                    }
                }
                // Deck: column list
                if (layoutMode == LayoutMode.DECK) {
                    DeckColumnEditor(columns = columns, onColumnsChange = { columns = it })
                }
                // Single Pane: screen picker
                if (layoutMode == LayoutMode.SINGLE_PANE) {
                    SinglePaneScreenPicker(
                        selected = singlePaneScreen,
                        onSelect = { singlePaneScreen = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Workspace(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { "Untitled" },
                            iconName = iconName,
                            layoutMode = layoutMode,
                            columns = columns,
                            singlePaneScreen = singlePaneScreen,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

#### DeckColumnEditor (add/remove columns from LAUNCHABLE_SCREENS)

```kotlin
@Composable
private fun DeckColumnEditor(
    columns: List<Workspace.WorkspaceColumn>,
    onColumnsChange: (List<Workspace.WorkspaceColumn>) -> Unit,
) {
    Column {
        Text("Columns", style = MaterialTheme.typography.labelMedium)
        columns.forEachIndexed { idx, col ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(col.typeKey, Modifier.weight(1f))
                IconButton(onClick = {
                    onColumnsChange(columns.toMutableList().apply { removeAt(idx) })
                }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        // Add column dropdown
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = true }) { Text("+ Add Column") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LAUNCHABLE_SCREENS.filter { !it.requiresInput() }.forEach { screen ->
                DropdownMenuItem(
                    text = { Text(screen.title()) },
                    onClick = {
                        onColumnsChange(columns + Workspace.WorkspaceColumn(screen.typeKey()))
                        expanded = false
                    },
                )
            }
        }
    }
}
```

### Phase 3: Unified Search Results

When `state.isSearching` is true, replace the tab content with a unified results view:

```kotlin
// In AppDrawer content area
if (state.isSearching) {
    UnifiedSearchResults(state, workspaceManager, onSwitchWorkspace, onSelectScreen, onDismiss)
} else {
    when (state.activeTab) {
        DrawerTab.SCREENS -> DrawerGrid(state, openColumnTypes, pinnedNavBarState, onSelectScreen, onDismiss)
        DrawerTab.WORKSPACES -> WorkspacesGrid(state, workspaceManager, onSwitchWorkspace, onDismiss)
    }
}
```

```kotlin
@Composable
private fun UnifiedSearchResults(
    state: AppDrawerState,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Workspace) -> Unit,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    val activeIndex by workspaceManager.activeIndex.collectAsState()
    val workspaces by workspaceManager.workspaces.collectAsState()
    LazyColumn(Modifier.padding(8.dp)) {
        // Workspace results first
        if (state.filteredWorkspaces.isNotEmpty()) {
            stickyHeader { Text("Workspaces", style = MaterialTheme.typography.titleSmall, ...) }
            items(state.filteredWorkspaces) { ws ->
                val wsIdx = workspaces.indexOf(ws)
                WorkspaceCard(
                    workspace = ws,
                    isActive = wsIdx == activeIndex,
                    onSwitch = {
                        val switched = workspaceManager.switchTo(wsIdx)
                        if (switched != null) { onSwitchWorkspace(switched); onDismiss() }
                    },
                    onEdit = { /* open editor */ },
                    onDelete = { workspaceManager.deleteWorkspace(ws.id) },
                    canDelete = workspaces.size > 1,
                )
            }
        }
        // Screen results below
        if (state.filteredScreens.isNotEmpty()) {
            stickyHeader { Text("Screens", style = MaterialTheme.typography.titleSmall, ...) }
            // Render as FlowRow grid per existing pattern
        }
    }
}
```

### Phase 4: Layout Mode Auto-Switching

**Files modified:** `Main.kt`, `AppDrawer.kt`

Current problem: `onSwitchWorkspace` in Main.kt only calls `deckState.loadFromWorkspace()`. It ignores the workspace's `layoutMode`.

#### Fix: Add `onLayoutModeChange` callback to `AppDrawer`

```kotlin
// AppDrawer signature change
@Composable
fun AppDrawer(
    openColumnTypes: Set<String>,
    pinnedNavBarState: PinnedNavBarState,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Workspace) -> Unit,  // handles layout mode + columns
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
)
```

No signature change needed — the `Workspace` already carries `layoutMode`. Fix the callback in Main.kt:

```kotlin
// Main.kt — inside AppDrawer call
onSwitchWorkspace = { ws ->
    // Switch layout mode
    layoutMode = ws.layoutMode
    DesktopPreferences.layoutMode = ws.layoutMode.name
    // Load columns or single pane screen
    when (ws.layoutMode) {
        LayoutMode.DECK -> deckState.loadFromWorkspace(ws.columns)
        LayoutMode.SINGLE_PANE -> {
            val screenKey = ws.singlePaneScreen ?: ws.columns.firstOrNull()?.typeKey ?: "home"
            val type = DeckState.parseColumnTypeFromKey(screenKey)
            if (type != null) singlePaneState.navigate(type)
        }
    }
},
```

**Note:** `DeckState.parseColumnType` is currently `private`. Need to expose a public helper:

```kotlin
// DeckState companion
fun parseColumnTypeFromKey(typeKey: String, param: String? = null): DeckColumnType? {
    return parseColumnType(mapOf("type" to typeKey, "param" to param))
}
```

### Phase 5: Cmd+S Save Current Layout

**Files modified:** `Main.kt` (MenuBar)

Add to File menu:

```kotlin
Item(
    "Save as Workspace",
    shortcut = if (isMacOS) KeyShortcut(Key.S, meta = true)
               else KeyShortcut(Key.S, ctrl = true),
    onClick = {
        val columns = deckState.columns.value.map { col ->
            Workspace.WorkspaceColumn(
                typeKey = col.type.typeKey(),
                param = when (col.type) {
                    is DeckColumnType.Hashtag -> col.type.tag
                    is DeckColumnType.Editor -> col.type.draftSlug
                    is DeckColumnType.Article -> col.type.addressTag
                    else -> null
                },
                width = col.width,
            )
        }
        val ws = Workspace(
            name = "Workspace ${workspaceManager.workspaces.value.size + 1}",
            iconName = "Star",
            layoutMode = layoutMode,
            columns = columns,
            singlePaneScreen = if (layoutMode == LayoutMode.SINGLE_PANE)
                singlePaneState.currentScreen.value.typeKey() else null,
        )
        workspaceManager.addWorkspace(ws)
        // Optionally show editor dialog for naming
        showWorkspaceEditor = true
        pendingWorkspace = ws
    },
    enabled = workspaceManager.workspaces.value.size < WorkspaceManager.MAX_WORKSPACES,
)
```

### Phase 6: Enter on Workspace in Search

Keyboard navigation in unified search must span both workspace and screen results. The `selectedIndex` in `AppDrawerState` indexes a combined list.

```kotlin
// In AppDrawerState
fun selectCurrent(
    onSelectScreen: (DeckColumnType) -> Unit,
    onSwitchWorkspace: (Workspace) -> Unit,
    onDismiss: () -> Unit,
) {
    if (consumed) return
    if (!isSearching) {
        // Tab-based: only screens use Enter
        filteredScreens.getOrNull(selectedIndex)?.let { select(it, onSelectScreen, onDismiss) }
        return
    }
    // Unified: workspaces first, then screens
    val wsCount = filteredWorkspaces.size
    if (selectedIndex < wsCount) {
        consumed = true
        onSwitchWorkspace(filteredWorkspaces[selectedIndex])
        onDismiss()
    } else {
        val screenIdx = selectedIndex - wsCount
        filteredScreens.getOrNull(screenIdx)?.let { select(it, onSelectScreen, onDismiss) }
    }
}
```

## File Change Summary

| File | Changes |
|------|---------|
| `AppDrawer.kt` | Add `DrawerTab` enum, refactor `AppDrawerState` for tabs+unified search, add `WorkspacesGrid`, `WorkspaceCard`, `AddWorkspaceCard`, `UnifiedSearchResults`, remove `WorkspaceBar` |
| `AppDrawer.kt` or new `WorkspaceEditorDialog.kt` | `WorkspaceEditorDialog`, `DeckColumnEditor`, `SinglePaneScreenPicker` |
| `Main.kt` | Fix `onSwitchWorkspace` to handle `layoutMode` change, add Cmd+S menu item |
| `DeckState.kt` | Expose `parseColumnTypeFromKey()` as public companion fun |
| `SinglePaneState.kt` | No changes needed (already has `navigate()`) |
| `Workspace.kt` | No changes needed |
| `WorkspaceManager.kt` | No changes needed |
| `DesktopPreferences.kt` | No changes needed |

## Implementation Order

1. **Phase 4 first** — Fix layoutMode on workspace switch (critical bug fix, smallest change)
2. **Phase 1a-1b** — Tab system in AppDrawerState
3. **Phase 1c-1d** — Workspace cards (replace WorkspaceBar)
4. **Phase 2** — Workspace editor dialog
5. **Phase 3** — Unified search
6. **Phase 6** — Keyboard navigation for unified search
7. **Phase 5** — Cmd+S save

## Acceptance Criteria

- [ ] App Drawer shows two tabs: "Screens" and "Workspaces" when search is empty
- [ ] Workspaces tab shows cards with: icon, name, layout mode badge, column preview, active indicator, edit/delete buttons
- [ ] "+" card at end of workspaces list creates new workspace via editor dialog
- [ ] Workspace editor dialog allows: name, icon picker, layout mode toggle, column selection (Deck) or screen picker (Single Pane)
- [ ] Typing in search bar shows unified results: workspaces at top (larger cards), screens below
- [ ] Unified search works regardless of active tab
- [ ] Enter on workspace result switches to that workspace
- [ ] Enter on screen result opens/adds that screen (existing behavior)
- [ ] Workspace switch changes `layoutMode` and persists it to `DesktopPreferences`
- [ ] Workspace switch in Deck mode loads columns via `deckState.loadFromWorkspace()`
- [ ] Workspace switch in Single Pane mode navigates via `singlePaneState.navigate()`
- [ ] Cmd+S (Ctrl+S on Linux/Windows) saves current layout as new workspace
- [ ] Delete button disabled when only one workspace remains
- [ ] Keyboard up/down navigation works in unified search results (spanning workspace + screen items)
- [ ] `DeckState.parseColumnTypeFromKey()` exposed as public companion function
- [ ] `WorkspaceBar` removed from bottom of drawer
- [ ] `spotlessApply` passes

## Unanswered Questions

- Should Cmd+S immediately save with auto-generated name, or open editor dialog for naming? (Plan assumes: save then optionally open editor)
- Should "Save current" button also appear in Workspaces tab, or just Cmd+S? (Brainstorm says both)
- Should we support Hashtag columns in workspace editor? They require input — may need inline text field per hashtag column

---

## Deepening Review

### P1: Bugs / Phantom APIs / Impossible Patterns

1. **`SegmentedButton` / `SingleChoiceSegmentedButtonRow` availability** — These are Material3 experimental APIs. Must annotate with `@OptIn(ExperimentalMaterial3Api::class)`. Verified they exist in M3 1.2+ which this project uses. **Fix: Add opt-in annotation to `WorkspaceEditorDialog`.**

2. **`AssistChip` in `WorkspaceCard`** — `AssistChip` requires `onClick` parameter, but we're using it as a read-only badge. Should use `SuggestionChip` instead, or just a `Surface` with `Text` for a non-interactive badge. **Fix: Replace with a styled `Surface` + `Text` badge:**
   ```kotlin
   Surface(
       shape = RoundedCornerShape(4.dp),
       color = MaterialTheme.colorScheme.secondaryContainer,
   ) {
       Text(
           if (workspace.layoutMode == LayoutMode.DECK) "Deck" else "Single",
           modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
           style = MaterialTheme.typography.labelSmall,
       )
   }
   ```

3. **`DeckState.parseColumnTypeFromKey` exposure** — The plan says "expose as public companion fun" but `parseColumnType` takes `Map<String, Any?>`. The new public wrapper correctly wraps it. No issue, but ensure the wrapper handles the `param` for Hashtag columns (e.g., `parseColumnTypeFromKey("hashtag", "bitcoin")`). **Verified: the existing private `parseColumnType` already handles this correctly.**

4. **`filteredWorkspaces` reads `workspaceManager.workspaces.value` directly** — This is a `StateFlow.value` snapshot inside a `derivedStateOf`. It won't recompose when workspaces change because `derivedStateOf` doesn't track `StateFlow`. **Fix: `AppDrawerState` must accept the workspace list as a parameter from a `collectAsState()` call, or the `derivedStateOf` must be computed at the composable level.** Best approach: pass `workspaces: List<Workspace>` into state methods instead of reading `.value`:
   ```kotlin
   // At composable level:
   val allWorkspaces by workspaceManager.workspaces.collectAsState()
   // Then filter in derivedStateOf based on that snapshot:
   val filteredWorkspaces by remember(allWorkspaces, state.searchQuery) {
       derivedStateOf {
           if (state.searchQuery.isBlank()) allWorkspaces
           else allWorkspaces.filter { ws -> ws.name.contains(state.searchQuery, ignoreCase = true) }
       }
   }
   ```
   This is a **critical reactivity bug** — without this fix, workspace CRUD won't update the drawer.

5. **Cmd+S conflict risk** — No existing Cmd+S shortcut in the app. However, Cmd+S is universally expected to "save" in text fields / editors. If the user is typing in the Editor column's compose area, Cmd+S would trigger "Save as Workspace" instead of saving the note draft. **Mitigation:** Use `Cmd+Shift+S` instead, which is the standard "Save As" shortcut and avoids conflicts. Or scope Cmd+S to only fire when no text field has focus. **Recommendation: Change to `Cmd+Shift+S`.**

### P2: Over-engineering / YAGNI / Simplification

1. **`showWorkspaceEditor` + `pendingWorkspace` state in Main.kt for Cmd+S** — This adds two state vars to Main.kt just for the save flow. Simpler: Cmd+S saves immediately with auto-generated name, shows a snackbar with "Undo" / "Rename" action. Defer editor-on-save to a polish pass.

2. **`SinglePaneScreenPicker` in editor** — For v1, a simple dropdown of `LAUNCHABLE_SCREENS` is sufficient. No need for a fancy picker component.

3. **Unified search keyboard navigation spanning workspace+screen results** — The combined index approach works but adds complexity. For v1, consider: arrow keys only work within the active tab or within the active section of unified results. Simpler: treat workspaces and screens as two separate groups, Tab key switches between groups.

4. **`DeckColumnEditor` drag-to-reorder** — Brainstorm explicitly defers this. Good.

### P3: Style / Naming / Minor

1. **`DrawerTab` enum** — Consider naming `AppDrawerTab` to avoid collision with `Tab` composable in imports.
2. **`totalResultCount`** — Used for bounds checking but never displayed. Name could be `resultCount`.
3. **Column preview shows raw `typeKey`** — e.g., "home, notifications, messages". Should show display names. Fix: use `DeckState.parseColumnTypeFromKey(col.typeKey)?.title() ?: col.typeKey` for human-readable names.
4. **`WorkspaceEditorDialog` size** — `AlertDialog` may be too small for the icon picker + column editor. Consider using a custom `Dialog` with fixed size instead.
