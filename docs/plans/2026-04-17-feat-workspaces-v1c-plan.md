---
title: "feat: Workspaces (v1c)"
type: feat
status: active
date: 2026-04-17
origin: docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md
---

# Workspaces (v1c)

## Overview

Named layout presets users can create, switch between, and customize. Each workspace stores a layout mode (Deck/SinglePane) + column configuration. Switching destroys current layout and rebuilds from saved config. Max 9 workspaces (Cmd+K then 1-9).

## Problem Statement

Users have different workflows (social browsing, writing, reading) that require different column layouts. Currently they must manually add/remove columns each time they want to change context. No way to save or recall a layout configuration.

## Proposed Solution

Workspace system with:
- Default presets: Social (Deck: Home+Notifs+DMs), Writing (Deck: Editor+Drafts+Reads), Reading (SinglePane: Reads)
- Custom workspace creation: Material icon + name + layout mode + column config
- Workspace picker in App Drawer footer (Cmd+K opens drawer, bottom shows workspaces)
- Cmd+K then 1-9 quick-switches to workspace N
- Edit/delete workspaces (min 1 must remain)
- Persistence via DesktopPreferences + Jackson JSON

## Technical Approach

### Architecture

```
Main.kt (Window level)
├── workspaceManager: WorkspaceManager (created once, persisted)
├── deckState: DeckState (rebuilt on workspace switch)
├── singlePaneState: SinglePaneState (rebuilt on workspace switch)
├── layoutMode: LayoutMode (set by active workspace)
└── App()
    ├── if (showAppDrawer) AppDrawer(...)
    │   └── WorkspaceBar (footer) ← NEW
    │       ├── workspace chips (icon + name, click to switch)
    │       ├── "+" button → WorkspaceEditorDialog
    │       └── long-press/right-click → edit/delete
    └── MainContent()
        ├── SINGLE_PANE → SinglePaneLayout(singlePaneState)
        └── DECK → DeckLayout(deckState)
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| WorkspaceManager at Window level (not App) | Survives key() app rebuilds, same level as deckState |
| Destroy+rebuild on switch | No background state = simpler, no memory bloat |
| Jackson JSON for persistence | Matches existing DeckState.save()/load() pattern |
| Workspace stores column type keys + widths | Reuses DeckState serialization format exactly |
| Max 9 workspaces | Matches Cmd+K+1-9 shortcut range |
| Material icons stored as string name | Serializable, resolve at render time via reflection/map |

### Data Model

```kotlin
// desktopApp/.../ui/deck/Workspace.kt

data class Workspace(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val iconName: String,          // Material icon name, e.g. "Groups", "Edit", "MenuBook"
    val layoutMode: LayoutMode,
    val columns: List<WorkspaceColumn>,  // only used in DECK mode
    val singlePaneScreen: String? = null, // typeKey for SINGLE_PANE mode
) {
    data class WorkspaceColumn(
        val typeKey: String,
        val param: String? = null,
        val width: Float = 400f,
    )
}

// Icon registry — map icon names to ImageVectors
object WorkspaceIcons {
    private val icons: Map<String, ImageVector> = mapOf(
        "Groups" to Icons.Default.Groups,
        "Edit" to Icons.Default.Edit,
        "MenuBook" to Icons.Default.MenuBook,
        "Home" to Icons.Default.Home,
        "Chat" to Icons.Default.Chat,
        "Search" to Icons.Default.Search,
        "SportsEsports" to Icons.Default.SportsEsports,
        "Bookmark" to Icons.Default.Bookmark,
        "Explore" to Icons.Default.Explore,
        "Person" to Icons.Default.Person,
        "Star" to Icons.Default.Star,
        "Favorite" to Icons.Default.Favorite,
        "Work" to Icons.Default.Work,
        "Code" to Icons.Default.Code,
    )

    val availableNames: List<String> = icons.keys.sorted()

    fun resolve(name: String): ImageVector = icons[name] ?: Icons.Default.Groups
}
```

### WorkspaceManager

```kotlin
// desktopApp/.../ui/deck/WorkspaceManager.kt

class WorkspaceManager(
    private val saveScope: CoroutineScope,
) {
    private val mapper = jacksonObjectMapper()

    private val _workspaces = MutableStateFlow(DEFAULT_WORKSPACES)
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    val activeWorkspace: Workspace
        get() = _workspaces.value[_activeIndex.value.coerceIn(_workspaces.value.indices)]

    fun switchTo(
        index: Int,
        deckState: DeckState,
        singlePaneState: SinglePaneState,
        onLayoutModeChanged: (LayoutMode) -> Unit,
    ) {
        if (index !in _workspaces.value.indices) return
        // Save current workspace's column state before switching
        saveCurrentState(deckState, singlePaneState)
        _activeIndex.value = index
        val workspace = _workspaces.value[index]
        // Apply workspace config
        onLayoutModeChanged(workspace.layoutMode)
        applyWorkspace(workspace, deckState, singlePaneState)
        scheduleSave()
    }

    private fun saveCurrentState(deckState: DeckState, singlePaneState: SinglePaneState) {
        val idx = _activeIndex.value
        val current = _workspaces.value[idx]
        val updated = when (current.layoutMode) {
            LayoutMode.DECK -> current.copy(
                columns = deckState.columns.value.map { col ->
                    Workspace.WorkspaceColumn(
                        typeKey = col.type.typeKey(),
                        param = when (col.type) {
                            is DeckColumnType.Profile -> col.type.pubKeyHex
                            is DeckColumnType.Thread -> col.type.noteId
                            is DeckColumnType.Hashtag -> col.type.tag
                            else -> null
                        },
                        width = col.width,
                    )
                },
            )
            LayoutMode.SINGLE_PANE -> current.copy(
                singlePaneScreen = singlePaneState.currentScreen.value.typeKey(),
            )
        }
        _workspaces.update { list ->
            list.toMutableList().also { it[idx] = updated }
        }
    }

    private fun applyWorkspace(workspace: Workspace, deckState: DeckState, singlePaneState: SinglePaneState) {
        when (workspace.layoutMode) {
            LayoutMode.DECK -> {
                // Rebuild DeckState from workspace columns
                deckState.loadFromWorkspace(workspace.columns)
            }
            LayoutMode.SINGLE_PANE -> {
                val typeKey = workspace.singlePaneScreen ?: "home"
                val type = DeckState.parseTypeKey(typeKey)
                if (type != null) singlePaneState.navigate(type)
            }
        }
    }

    fun addWorkspace(workspace: Workspace): Boolean {
        if (_workspaces.value.size >= MAX_WORKSPACES) return false
        _workspaces.update { it + workspace }
        scheduleSave()
        return true
    }

    fun updateWorkspace(id: String, name: String, iconName: String) {
        _workspaces.update { list ->
            list.map { if (it.id == id) it.copy(name = name, iconName = iconName) else it }
        }
        scheduleSave()
    }

    fun deleteWorkspace(id: String) {
        if (_workspaces.value.size <= 1) return
        val idx = _workspaces.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        _workspaces.update { it.filter { w -> w.id != id } }
        if (_activeIndex.value >= _workspaces.value.size) {
            _activeIndex.value = _workspaces.value.size - 1
        }
        scheduleSave()
    }

    // -- Persistence --

    private var saveJob: Job? = null

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(500L)
            save()
        }
    }

    fun save() {
        try {
            val data = mapOf(
                "activeIndex" to _activeIndex.value,
                "workspaces" to _workspaces.value.map { ws ->
                    mapOf(
                        "id" to ws.id,
                        "name" to ws.name,
                        "iconName" to ws.iconName,
                        "layoutMode" to ws.layoutMode.name,
                        "singlePaneScreen" to ws.singlePaneScreen,
                        "columns" to ws.columns.map { col ->
                            mapOf(
                                "typeKey" to col.typeKey,
                                "param" to col.param,
                                "width" to col.width,
                            )
                        },
                    )
                },
            )
            DesktopPreferences.workspaces = mapper.writeValueAsString(data)
        } catch (e: Exception) {
            println("WorkspaceManager: failed to save: ${e.message}")
        }
    }

    fun load() {
        try {
            val json = DesktopPreferences.workspaces
            if (json.isBlank()) return
            val data: Map<String, Any?> = mapper.readValue(json)
            val activeIdx = (data["activeIndex"] as? Number)?.toInt() ?: 0
            val wsList = (data["workspaces"] as? List<Map<String, Any?>>)?.mapNotNull { entry ->
                val id = entry["id"] as? String ?: return@mapNotNull null
                val name = entry["name"] as? String ?: return@mapNotNull null
                val iconName = entry["iconName"] as? String ?: "Groups"
                val layoutMode = try {
                    LayoutMode.valueOf(entry["layoutMode"] as? String ?: "DECK")
                } catch (e: Exception) { LayoutMode.DECK }
                val singlePaneScreen = entry["singlePaneScreen"] as? String
                val columns = (entry["columns"] as? List<Map<String, Any?>>)?.mapNotNull { col ->
                    val typeKey = col["typeKey"] as? String ?: return@mapNotNull null
                    Workspace.WorkspaceColumn(
                        typeKey = typeKey,
                        param = col["param"] as? String,
                        width = (col["width"] as? Number)?.toFloat() ?: 400f,
                    )
                } ?: emptyList()
                Workspace(id, name, iconName, layoutMode, columns, singlePaneScreen)
            } ?: return
            if (wsList.isNotEmpty()) {
                _workspaces.value = wsList
                _activeIndex.value = activeIdx.coerceIn(wsList.indices)
            }
        } catch (e: Exception) {
            println("WorkspaceManager: failed to load: ${e.message}")
        }
    }

    companion object {
        const val MAX_WORKSPACES = 9

        val DEFAULT_WORKSPACES = listOf(
            Workspace(
                id = "default-social",
                name = "Social",
                iconName = "Groups",
                layoutMode = LayoutMode.DECK,
                columns = listOf(
                    Workspace.WorkspaceColumn("home"),
                    Workspace.WorkspaceColumn("notifications"),
                    Workspace.WorkspaceColumn("messages"),
                ),
            ),
            Workspace(
                id = "default-writing",
                name = "Writing",
                iconName = "Edit",
                layoutMode = LayoutMode.DECK,
                columns = listOf(
                    Workspace.WorkspaceColumn("editor"),
                    Workspace.WorkspaceColumn("drafts"),
                    Workspace.WorkspaceColumn("reads"),
                ),
            ),
            Workspace(
                id = "default-reading",
                name = "Reading",
                iconName = "MenuBook",
                layoutMode = LayoutMode.SINGLE_PANE,
                columns = emptyList(),
                singlePaneScreen = "reads",
            ),
        )
    }
}
```

### DeckState Changes

Add `loadFromWorkspace()` and expose `parseTypeKey()`:

```kotlin
// DeckState.kt — additions

fun loadFromWorkspace(columns: List<Workspace.WorkspaceColumn>) {
    val loaded = columns.mapNotNull { col ->
        val type = parseColumnType(
            mapOf("type" to col.typeKey, "param" to col.param)
        ) ?: return@mapNotNull null
        DeckColumn(
            type = type,
            width = col.width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH),
        )
    }
    _columns.value = if (loaded.isNotEmpty()) loaded else DEFAULT_COLUMNS
    _focusedColumnIndex.value = 0
}

companion object {
    // Make parseColumnType accessible for WorkspaceManager
    fun parseTypeKey(typeKey: String, param: String? = null): DeckColumnType? {
        return parseColumnType(mapOf("type" to typeKey, "param" to param))
    }
}
```

### DesktopPreferences Changes

```kotlin
// DesktopPreferences.kt — add workspace key

private const val KEY_WORKSPACES = "workspaces"

var workspaces: String
    get() = prefs.get(KEY_WORKSPACES, "")
    set(value) { prefs.put(KEY_WORKSPACES, value) }
```

### Workspace Switching in Main.kt

```kotlin
// Main.kt — Window level, alongside existing deckState

val workspaceManager = remember {
    WorkspaceManager(deckScope).also { it.load() }
}

// On first load, apply active workspace to deckState + layoutMode
LaunchedEffect(Unit) {
    val ws = workspaceManager.activeWorkspace
    layoutMode = ws.layoutMode
    DesktopPreferences.layoutMode = ws.layoutMode.name
    // deckState.load() already ran — only override if workspace data exists
    if (DesktopPreferences.workspaces.isNotBlank()) {
        workspaceManager.switchTo(
            workspaceManager.activeIndex.value,
            deckState, singlePaneState,
            onLayoutModeChanged = { mode ->
                layoutMode = mode
                DesktopPreferences.layoutMode = mode.name
            },
        )
    }
}

// Pass to AppDrawer
if (showAppDrawer) {
    AppDrawer(
        openColumnTypes = ...,
        onSelectScreen = { ... },
        onDismiss = onDismissAppDrawer,
        workspaceManager = workspaceManager,  // NEW
        onSwitchWorkspace = { index ->         // NEW
            workspaceManager.switchTo(
                index, deckState, singlePaneState,
                onLayoutModeChanged = { mode ->
                    layoutMode = mode
                    DesktopPreferences.layoutMode = mode.name
                },
            )
            showAppDrawer = false
        },
    )
}
```

### App Drawer — Workspace Bar (footer)

```kotlin
// AppDrawer.kt — add WorkspaceBar at bottom of drawer Surface

@Composable
fun AppDrawer(
    openColumnTypes: Set<String>,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Int) -> Unit,
) {
    // ... existing state, scrim, Surface ...
    Column {
        // Search TextField (existing)
        TextField(...)

        // Screen grid (existing) — weight(1f) to push workspace bar to bottom
        if (state.awaitingHashtag) {
            HashtagInputSection(...)
        } else {
            Box(Modifier.weight(1f)) {
                DrawerGrid(...)
            }
        }

        // Workspace bar — footer
        HorizontalDivider()
        WorkspaceBar(
            workspaceManager = workspaceManager,
            onSwitchWorkspace = onSwitchWorkspace,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun WorkspaceBar(
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val workspaces by workspaceManager.workspaces.collectAsState()
    val activeIndex by workspaceManager.activeIndex.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingWorkspace by remember { mutableStateOf<Workspace?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Workspaces",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        workspaces.forEachIndexed { index, workspace ->
            WorkspaceChip(
                workspace = workspace,
                isActive = index == activeIndex,
                index = index + 1,  // 1-based for shortcut hint
                onClick = { onSwitchWorkspace(index) },
                onEditRequest = { editingWorkspace = workspace },
            )
        }

        if (workspaces.size < WorkspaceManager.MAX_WORKSPACES) {
            IconButton(
                onClick = { showEditor = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.Add, "Add workspace", Modifier.size(18.dp))
            }
        }
    }

    // Editor dialog
    if (showEditor || editingWorkspace != null) {
        WorkspaceEditorDialog(
            existing = editingWorkspace,
            onSave = { name, iconName, layoutMode ->
                if (editingWorkspace != null) {
                    workspaceManager.updateWorkspace(editingWorkspace!!.id, name, iconName)
                } else {
                    workspaceManager.addWorkspace(
                        Workspace(
                            name = name,
                            iconName = iconName,
                            layoutMode = layoutMode,
                            columns = if (layoutMode == LayoutMode.DECK) {
                                DeckState.DEFAULT_COLUMNS.map {
                                    Workspace.WorkspaceColumn(it.type.typeKey())
                                }
                            } else emptyList(),
                            singlePaneScreen = if (layoutMode == LayoutMode.SINGLE_PANE) "home" else null,
                        )
                    )
                }
                showEditor = false
                editingWorkspace = null
            },
            onDelete = editingWorkspace?.let { ws ->
                if (workspaces.size > 1) {
                    { workspaceManager.deleteWorkspace(ws.id); editingWorkspace = null }
                } else null
            },
            onDismiss = { showEditor = false; editingWorkspace = null },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WorkspaceChip(
    workspace: Workspace,
    isActive: Boolean,
    index: Int,
    onClick: () -> Unit,
    onEditRequest: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) { event ->
                // Right-click / secondary button → edit
                if (event.button?.isSecondary == true) onEditRequest()
            },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (isActive) 8.dp else 2.dp,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                WorkspaceIcons.resolve(workspace.iconName),
                workspace.name,
                modifier = Modifier.size(16.dp),
            )
            Text(
                workspace.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            // Shortcut hint
            Text(
                "$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
```

### Workspace Editor Dialog

```kotlin
// AppDrawer.kt or new WorkspaceEditorDialog.kt

@Composable
private fun WorkspaceEditorDialog(
    existing: Workspace?,
    onSave: (name: String, iconName: String, layoutMode: LayoutMode) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(existing?.iconName ?: "Groups") }
    var layoutMode by remember { mutableStateOf(existing?.layoutMode ?: LayoutMode.DECK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Workspace" else "New Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Icon picker — grid of available icons
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WorkspaceIcons.availableNames.forEach { iconName ->
                        IconButton(
                            onClick = { selectedIcon = iconName },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                WorkspaceIcons.resolve(iconName),
                                iconName,
                                modifier = Modifier.size(20.dp),
                                tint = if (iconName == selectedIcon) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                // Layout mode toggle (only for new workspaces)
                if (existing == null) {
                    Text("Layout", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = layoutMode == LayoutMode.DECK,
                            onClick = { layoutMode = LayoutMode.DECK },
                            label = { Text("Deck") },
                        )
                        FilterChip(
                            selected = layoutMode == LayoutMode.SINGLE_PANE,
                            onClick = { layoutMode = LayoutMode.SINGLE_PANE },
                            label = { Text("Single Pane") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedIcon, layoutMode) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
```

### Keyboard Shortcut: Cmd+K then 1-9

```kotlin
// AppDrawer.kt — add to onPreviewKeyEvent block

// Inside the existing onPreviewKeyEvent handler:
Key.One -> { onSwitchWorkspace(0); true }
Key.Two -> { onSwitchWorkspace(1); true }
Key.Three -> { onSwitchWorkspace(2); true }
Key.Four -> { onSwitchWorkspace(3); true }
Key.Five -> { onSwitchWorkspace(4); true }
Key.Six -> { onSwitchWorkspace(5); true }
Key.Seven -> { onSwitchWorkspace(6); true }
Key.Eight -> { onSwitchWorkspace(7); true }
Key.Nine -> { onSwitchWorkspace(8); true }
```

Number keys only fire when the search field is empty (check `state.searchQuery.isBlank()`) to avoid blocking number input in search.

### Implementation Phases

#### Phase 1: Data Model + WorkspaceManager + Persistence

**Files:**
- **New:** `desktopApp/.../ui/deck/Workspace.kt` — `Workspace` data class, `WorkspaceIcons` object
- **New:** `desktopApp/.../ui/deck/WorkspaceManager.kt` — state management, save/load
- **Modify:** `desktopApp/.../DesktopPreferences.kt` — add `workspaces` key
- **Modify:** `desktopApp/.../ui/deck/DeckState.kt` — add `loadFromWorkspace()`, expose `parseTypeKey()`

**Tests:**
- WorkspaceManager save/load round-trip
- WorkspaceManager.switchTo() correctness
- Max workspace limit enforced
- Delete last workspace prevented
- Default workspaces created on first load

#### Phase 2: Workspace Bar UI in App Drawer

**Files:**
- **Modify:** `desktopApp/.../ui/deck/AppDrawer.kt` — add WorkspaceBar footer, WorkspaceChip, WorkspaceEditorDialog
- **Modify:** `AppDrawer` signature — add `workspaceManager` + `onSwitchWorkspace` params

**Tests:**
- Manual: workspace chips render correctly
- Manual: active workspace highlighted
- Manual: "+" button shows editor dialog

#### Phase 3: Wire into Main.kt + Keyboard Shortcuts

**Files:**
- **Modify:** `desktopApp/.../Main.kt` — create WorkspaceManager, pass to AppDrawer, handle switchTo
- **Modify:** `desktopApp/.../ui/deck/AppDrawer.kt` — add 1-9 key handling in onPreviewKeyEvent

**Tests:**
- Manual: Cmd+K opens drawer, pressing 1/2/3 switches workspace
- Manual: layout mode changes on switch
- Manual: columns rebuild on switch
- Persistence survives app restart

#### Phase 4: Auto-save Current Workspace on Column Changes

**Files:**
- **Modify:** `desktopApp/.../Main.kt` — LaunchedEffect on deckState.columns to auto-save active workspace
- **Modify:** `desktopApp/.../ui/deck/WorkspaceManager.kt` — add `updateCurrentColumns()` for live tracking

```kotlin
// Main.kt — auto-save column changes to active workspace
val currentColumns by deckState.columns.collectAsState()
LaunchedEffect(currentColumns) {
    if (layoutMode == LayoutMode.DECK) {
        workspaceManager.saveCurrentState(deckState, singlePaneState)
    }
}
```

#### Phase 5: Migration + Backward Compatibility

- If `DesktopPreferences.workspaces` is empty but `DesktopPreferences.deckColumns` has data:
  - Create "Social" workspace from existing deck columns
  - Set as active workspace
  - Existing layout preserved on first upgrade

```kotlin
// WorkspaceManager.load() — migration path
fun load() {
    val json = DesktopPreferences.workspaces
    if (json.isBlank()) {
        // Migrate: check if legacy deckColumns exist
        val legacyJson = DesktopPreferences.deckColumns
        if (legacyJson.isNotBlank()) {
            // Create workspace from existing config
            _workspaces.value = listOf(
                Workspace(
                    id = "migrated-social",
                    name = "Social",
                    iconName = "Groups",
                    layoutMode = try {
                        LayoutMode.valueOf(DesktopPreferences.layoutMode)
                    } catch (e: Exception) { LayoutMode.DECK },
                    columns = emptyList(), // will be populated by DeckState.load()
                ),
            ) + DEFAULT_WORKSPACES.drop(1) // add Writing + Reading presets
            save()
        }
        return
    }
    // ... normal load path
}
```

### New/Modified Files Summary

| File | Action | Description |
|------|--------|-------------|
| `ui/deck/Workspace.kt` | **New** | Data class + WorkspaceIcons |
| `ui/deck/WorkspaceManager.kt` | **New** | State, persistence, switching |
| `ui/deck/AppDrawer.kt` | **Modify** | Add WorkspaceBar footer, editor dialog, 1-9 keys |
| `ui/deck/DeckState.kt` | **Modify** | Add `loadFromWorkspace()`, expose `parseTypeKey()` |
| `DesktopPreferences.kt` | **Modify** | Add `workspaces` key |
| `Main.kt` | **Modify** | Create WorkspaceManager, wire switching, auto-save |
| `WorkspaceManagerTest.kt` | **New** | Unit tests for save/load/switch/limits |

## Acceptance Criteria

- [ ] 3 default workspaces ship on fresh install: Social, Writing, Reading
- [ ] Clicking workspace chip in App Drawer footer switches workspace
- [ ] Switching destroys current layout and rebuilds from saved config
- [ ] Layout mode (Deck/SinglePane) changes with workspace
- [ ] Cmd+K opens drawer, then 1-9 switches to workspace N
- [ ] Number keys only fire when search field is empty
- [ ] Users can create custom workspaces (name + icon + layout mode)
- [ ] Users can edit workspace name/icon (right-click chip)
- [ ] Users can delete workspaces (min 1 must remain)
- [ ] Max 9 workspaces enforced (add button hidden at limit)
- [ ] Column changes auto-saved to active workspace
- [ ] Workspaces persist across app restarts (DesktopPreferences)
- [ ] Existing users' deck column config migrated to first workspace
- [ ] Active workspace index persisted and restored
- [ ] Workspace switching < 200ms (destroy + rebuild, no animation)

## Dependencies & Risks

| Risk | Mitigation |
|------|------------|
| DeckState.load() reads from DesktopPreferences.deckColumns — conflicts with workspace persistence | WorkspaceManager owns column config; DeckState.load() only used for migration |
| Cmd+K+1-9 conflicts with typing numbers in search | Guard: only intercept digits when `searchQuery.isBlank()` |
| Users lose unsaved column changes on workspace switch | Auto-save via LaunchedEffect on deckState.columns |
| Jackson string limit in Preferences API (8192 bytes per key) | 9 workspaces with 5 columns each ~ 3KB JSON, well within limit |
| Right-click detection on different platforms | Use `onPointerEvent(PointerEventType.Press)` with button check; fall back to long-press if needed |
| WorkspaceManager + DeckState both calling save() | DeckState continues saving to `deckColumns` for backward compat; WorkspaceManager saves to `workspaces` key |

## Open Questions

- Should workspace switching have a brief crossfade transition, or instant swap?
- Should new custom workspaces start empty or copy current layout?
- Should workspace chips show in the sidebar (below nav items) in addition to the drawer footer?
- Should Cmd+K then number keys require a modifier (Shift?) to disambiguate from search input?
- When editing a workspace, should users be able to change its layout mode (Deck <-> SinglePane)?
- Should there be workspace-specific relay configurations in a future version?
- Should the "Writing" default workspace include Editor with a null slug or omit it?
