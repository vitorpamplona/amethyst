---
title: "feat: App Drawer with Categories and Search (v1a)"
type: feat
status: active
date: 2026-03-30
deepened: 2026-03-30
origin: docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md
---

# App Drawer with Categories and Search (v1a)

## Enhancement Summary

**Deepened on:** 2026-03-30
**Agents used:** compose-expert, desktop-expert, kotlin-expert, architecture-strategist, performance-oracle, code-simplicity-reviewer, pattern-recognition-specialist, race-conditions-reviewer, best-practices-researcher

### Key Improvements from Deepening
1. **Eliminated `DrawerScreen` wrapper** — use `DeckColumnType` directly with `category()` extension. Single source of truth (3 reviewers agreed)
2. **Reduced from 4 files to 2** — `AppDrawer.kt` + `DeckColumnTypeExtensions.kt` in existing `ui/deck/` package
3. **Fixed critical race condition** — `if()` block instead of `AnimatedVisibility` prevents stale state on re-open
4. **Proper Desktop patterns** — `onPreviewKeyEvent` (not `onKeyEvent`), `FocusRequester` with `awaitFrame()`, `indication = null` on backdrop
5. **Added `SinglePaneState`** — mirrors `DeckState` pattern, solves state hoisting problem, prepares for v1b/v1c
6. **`derivedStateOf`** for filtered/grouped lists — prevents redundant computation on every recomposition
7. **Double-click guard** — prevents duplicate column creation

### Architectural Corrections
- No new `ui/drawer/` package — files stay in `ui/deck/` (consistent with codebase)
- No `DrawerScreen` data class — `DeckColumnType` already has `title()` and `icon()`
- No `AppDrawerState` separate file — state is simple enough to live inside `AppDrawer.kt`
- No `ScreenCategory` as separate file — `category()` is an extension function on `DeckColumnType`

---

## Overview

Replace the current `AddColumnDialog` with a full-screen App Drawer overlay — a categorized, searchable launcher for all screens in Amethyst Desktop. Phase 1 of 3 in the Desktop Navigation Overhaul (see brainstorm: `docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md`).

## Problem Statement

The current `AddColumnDialog` is a basic `AlertDialog` with 11 hardcoded items, no search, no categories, and no keyboard navigation. Additionally, 3 separate registries duplicate screen metadata (`navItems`, `COLUMN_OPTIONS`, `title()`+`icon()` extensions) — a maintenance hazard.

## Proposed Solution

A full-screen overlay triggered by **Cmd+K** that shows all available screens organized by theme categories, with instant search and keyboard navigation. Works in both Single Pane and Deck modes. Consolidates screen metadata into a single source of truth.

## Design Decisions

### Resolved from SpecFlow Analysis

| Question | Decision | Rationale |
|----------|----------|-----------|
| Parameterized types in drawer | Show **Hashtag** (inline text input after selection) and **Editor** (as "New Draft", null slug). Exclude Profile/Thread/Article | Deep-link targets, not launcher screens |
| Cmd+T in Single Pane | Treat as Cmd+K (navigate to screen) | Consistent behavior, no dead shortcuts |
| Duplicate columns (Deck) | Object types: focus existing. Parameterized: allow duplicates | Two Home columns is odd; two Hashtag columns with different tags is fine |
| Arrow key navigation | Up/Down arrows move selection, Enter confirms | Keyboard-first requirement |
| Empty categories during search | Hide entirely | Cleaner search experience |
| UI button to open drawer | Replace "+" in DeckSidebar, add button in SinglePaneLayout | Discoverability |
| Open column indicators (Deck) | Subtle dot/badge on already-open screens | Prevents accidental duplicates |
| Animation | NO `AnimatedVisibility` — use `if()` block for correctness | `AnimatedVisibility` causes stale state on re-open (critical race condition) |
| Recently-used ordering | Static category order in v1a | Defer to v1b/v1c |

### Screen Categories (14 items across 5 categories)

| Category | Icon | Screens (DeckColumnType) |
|----------|------|--------------------------|
| **Social** | `Icons.Default.Groups` | HomeFeed, Notifications, Messages, GlobalFeed |
| **Long-Form** | `Icons.AutoMirrored.Filled.Article` | Reads, Drafts, Editor*, MyHighlights |
| **Discovery** | `Icons.Default.Explore` | Search, Hashtag*, Bookmarks |
| **Identity** | `Icons.Default.Person` | MyProfile, Settings |
| **Play** | `Icons.Default.SportsEsports` | Chess |

*\* = parameterized type with secondary input*

## Technical Approach

### Architecture

```
Main.kt
├── showAppDrawer: Boolean (replaces showAddColumnDialog)
├── singlePaneState: SinglePaneState (NEW — mirrors DeckState)
├── MenuBar → Cmd+K → showAppDrawer = true (always, both modes)
├── MenuBar → Cmd+T → showAppDrawer = true (backward compat)
└── App()
    ├── if (showAppDrawer) AppDrawer(...)  ← NEW (if block, not AnimatedVisibility)
    │   ├── onNavigate: (DeckColumnType) → Unit
    │   │   ├── Single Pane: singlePaneState.navigate(type)
    │   │   └── Deck: deckState.addColumn() or focusExisting()
    │   └── onDismiss: () → Unit
    └── MainContent()
        ├── SINGLE_PANE → SinglePaneLayout(singlePaneState)
        └── DECK → DeckLayout(deckState)
```

### New Files (2 files only)

| File | Purpose |
|------|---------|
| `desktopApp/.../ui/deck/AppDrawer.kt` | Overlay composable + private state + screen item composable |
| `desktopApp/.../ui/deck/SinglePaneState.kt` | State holder for single-pane navigation (mirrors DeckState) |

### Modified Files

| File | Changes |
|------|---------|
| `DeckColumnType.kt` | Add `category()` extension. Add `LAUNCHABLE_SCREENS` list |
| `ColumnHeader.kt` | No changes — reuse existing `icon()` extension |
| `Main.kt` | Replace `showAddColumnDialog` with `showAppDrawer`. Add Cmd+K shortcut. Create `SinglePaneState`. Wire `AppDrawer` |
| `DeckSidebar.kt` | Replace "+" button callback → `onShowAppDrawer` |
| `SinglePaneLayout.kt` | Accept `SinglePaneState` instead of local `currentColumnType`. Accept `onOpenAppDrawer` callback |
| `AddColumnDialog.kt` | **Delete** after drawer works |

### Implementation Phases

#### Phase 1: DeckColumnType Extensions + SinglePaneState

**File: `DeckColumnType.kt` — add extensions:**

```kotlin
enum class ScreenCategory(val title: String, val icon: ImageVector) {
    SOCIAL("Social", Icons.Default.Groups),
    LONG_FORM("Long-Form", Icons.AutoMirrored.Filled.Article),
    DISCOVERY("Discovery", Icons.Default.Explore),
    IDENTITY("Identity", Icons.Default.Person),
    PLAY("Play", Icons.Default.SportsEsports),
}

fun DeckColumnType.category(): ScreenCategory = when (this) {
    is DeckColumnType.HomeFeed, is DeckColumnType.Notifications,
    is DeckColumnType.Messages, is DeckColumnType.GlobalFeed -> ScreenCategory.SOCIAL
    is DeckColumnType.Reads, is DeckColumnType.Drafts,
    is DeckColumnType.Editor, is DeckColumnType.MyHighlights -> ScreenCategory.LONG_FORM
    is DeckColumnType.Search, is DeckColumnType.Hashtag,
    is DeckColumnType.Bookmarks -> ScreenCategory.DISCOVERY
    is DeckColumnType.MyProfile, is DeckColumnType.Settings -> ScreenCategory.IDENTITY
    is DeckColumnType.Chess -> ScreenCategory.PLAY
    else -> ScreenCategory.SOCIAL // fallback for deep-link types
}

// Single source of truth — replaces COLUMN_OPTIONS, navItems, DRAWER_SCREENS
val LAUNCHABLE_SCREENS: List<DeckColumnType> = listOf(
    DeckColumnType.HomeFeed,
    DeckColumnType.Notifications,
    DeckColumnType.Messages,
    DeckColumnType.GlobalFeed,
    DeckColumnType.Reads,
    DeckColumnType.Drafts,
    DeckColumnType.Editor(),        // "New Draft" (null slug)
    DeckColumnType.MyHighlights,
    DeckColumnType.Search,
    DeckColumnType.Hashtag(""),     // requires input
    DeckColumnType.Bookmarks,
    DeckColumnType.MyProfile,
    DeckColumnType.Settings,
    DeckColumnType.Chess,
)

// Used by drawer to detect which items need parameter input
fun DeckColumnType.requiresInput(): Boolean = when (this) {
    is DeckColumnType.Hashtag -> true
    else -> false
}
```

**File: `SinglePaneState.kt`:**

```kotlin
// Mirrors DeckState pattern — holds current screen for single-pane mode
// Prepares for v1b (customizable nav bar) and v1c (workspaces)
class SinglePaneState {
    private val _currentScreen = MutableStateFlow<DeckColumnType>(DeckColumnType.HomeFeed)
    val currentScreen: StateFlow<DeckColumnType> = _currentScreen.asStateFlow()

    fun navigate(type: DeckColumnType) {
        _currentScreen.value = type
    }
}
```

> **Research insight (architecture-strategist):** Creating `SinglePaneState` now avoids the state-hoisting problem (drawer at `App()` level needs to set `currentColumnType` in `SinglePaneLayout`) and prepares for v1b where `pinnedScreens: StateFlow<List<DeckColumnType>>` replaces the hardcoded `navItems`.

#### Phase 2: AppDrawer Composable

**File: `AppDrawer.kt` — single file with everything:**

```kotlin
// Private state holder — not a separate file
// @Stable tells Compose all mutations go through snapshot system
@Stable
private class AppDrawerState {
    var searchQuery by mutableStateOf("")
    var selectedIndex by mutableStateOf(0)
    var hashtagInput by mutableStateOf("")
    var awaitingHashtag by mutableStateOf(false)
    private var consumed by mutableStateOf(false) // double-click guard

    // derivedStateOf caches until searchQuery changes — avoids recompute per recomposition
    val filteredScreens: List<DeckColumnType> by derivedStateOf {
        if (searchQuery.isBlank()) LAUNCHABLE_SCREENS
        else LAUNCHABLE_SCREENS.filter {
            it.title().contains(searchQuery, ignoreCase = true) ||
                it.category().title.contains(searchQuery, ignoreCase = true)
        }
    }

    // Grouped by category, empty categories hidden
    val groupedScreens: Map<ScreenCategory, List<DeckColumnType>> by derivedStateOf {
        filteredScreens.groupBy { it.category() }.filterValues { it.isNotEmpty() }
    }

    fun moveSelection(delta: Int) {
        val size = filteredScreens.size
        if (size > 0) selectedIndex = (selectedIndex + delta).coerceIn(0, size - 1)
    }

    fun select(
        screen: DeckColumnType,
        onSelectScreen: (DeckColumnType) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (consumed) return // double-click guard
        if (screen.requiresInput()) {
            awaitingHashtag = true
            hashtagInput = ""
        } else {
            consumed = true
            onSelectScreen(screen)
            onDismiss()
        }
    }

    fun confirmHashtag(
        onSelectScreen: (DeckColumnType) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (consumed || hashtagInput.isBlank()) return
        consumed = true
        onSelectScreen(DeckColumnType.Hashtag(hashtagInput.trim()))
        onDismiss()
    }
}

@Composable
fun AppDrawer(
    openColumnTypes: Set<String>,   // typeKey() of open columns (Deck mode)
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    // Fresh state every open — if() block destroys on close, no stale state
    val state = remember { AppDrawerState() }
    val searchFocusRequester = remember { FocusRequester() }

    // Auto-focus search field (awaitFrame for Desktop timing)
    LaunchedEffect(Unit) {
        awaitFrame()
        searchFocusRequester.requestFocus()
    }

    // Fullscreen scrim — follows GlobalFullscreenOverlay.kt pattern
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // onPreviewKeyEvent intercepts BEFORE TextField consumes keys
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        if (state.awaitingHashtag) { state.awaitingHashtag = false; true }
                        else { onDismiss(); true }
                    }
                    Key.DirectionDown -> { state.moveSelection(1); true }
                    Key.DirectionUp -> { state.moveSelection(-1); true }
                    Key.Enter -> {
                        if (state.awaitingHashtag) {
                            state.confirmHashtag(onSelectScreen, onDismiss); true
                        } else {
                            state.filteredScreens.getOrNull(state.selectedIndex)?.let {
                                state.select(it, onSelectScreen, onDismiss)
                            }
                            true
                        }
                    }
                    else -> false // let TextField handle typing
                }
            }
            // Click scrim to dismiss — no ripple indication
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // Content card — consume pointer events to prevent click-through to scrim
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxHeight(0.7f)
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) { awaitPointerEvent() } }
                },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
        ) {
            Column {
                // Search TextField
                TextField(
                    value = state.searchQuery,
                    onValueChange = { state.searchQuery = it; state.selectedIndex = 0 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester),
                    placeholder = { Text("Search screens...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                )

                if (state.awaitingHashtag) {
                    // Hashtag parameter input
                    HashtagInputSection(state, onSelectScreen, onDismiss)
                } else {
                    // Categorized grid — isolated composable for recomposition scoping
                    DrawerGrid(state, openColumnTypes, onSelectScreen, onDismiss)
                }
            }
        }
    }
}

// Separate composable = separate recomposition scope
// Only recomposes when filteredScreens actually changes (derivedStateOf)
@Composable
private fun DrawerGrid(
    state: AppDrawerState,
    openColumnTypes: Set<String>,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to keep selected item visible
    LaunchedEffect(state.selectedIndex) {
        listState.animateScrollToItem(
            (state.selectedIndex / 4).coerceAtLeast(0) // approximate row
        )
    }

    LazyColumn(state = listState) {
        var globalIndex = 0
        state.groupedScreens.forEach { (category, screens) ->
            stickyHeader(key = "header-${category.name}") {
                CategoryHeader(category)
            }
            val startIndex = globalIndex
            item(key = "grid-${category.name}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    screens.forEachIndexed { localIdx, screen ->
                        DrawerScreenCard(
                            type = screen,
                            isSelected = (startIndex + localIdx) == state.selectedIndex,
                            isOpen = openColumnTypes.contains(screen.typeKey()),
                            onClick = { state.select(screen, onSelectScreen, onDismiss) },
                        )
                    }
                }
            }
            globalIndex += screens.size
        }
    }
}

@Composable
private fun DrawerScreenCard(
    type: DeckColumnType,
    isSelected: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    type.icon(),
                    contentDescription = type.title(),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    type.title(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            // Open indicator dot
            if (isOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ScreenCategory) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(category.icon, category.title, Modifier.size(16.dp))
        Text(
            category.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HashtagInputSection(
    state: AppDrawerState,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text("Enter hashtag:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = state.hashtagInput,
            onValueChange = { state.hashtagInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("#bitcoin, #nostr...") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { state.awaitingHashtag = false }) { Text("Back") }
            Button(
                onClick = { state.confirmHashtag(onSelectScreen, onDismiss) },
                enabled = state.hashtagInput.isNotBlank(),
            ) { Text("Open") }
        }
    }
}
```

> **Research insights applied:**
> - **Compose expert:** `derivedStateOf` for `filteredScreens`/`groupedScreens` prevents triple recomputation per keystroke
> - **Desktop expert:** `onPreviewKeyEvent` (not `onKeyEvent`) intercepts before TextField consumes arrow/Enter keys. Pattern from `SearchScreen.kt:293`, `ChatPane.kt:729`
> - **Desktop expert:** `Box(fillMaxSize)` overlay pattern matches `GlobalFullscreenOverlay.kt` and `LightboxOverlay.kt` — NOT `Popup` or `Dialog`
> - **Race conditions (CRITICAL):** `if (showAppDrawer)` block destroys/recreates composable on each open — fresh `remember` state, no stale data. Do NOT use `AnimatedVisibility`
> - **Race conditions:** `consumed` flag prevents double-click adding duplicate columns
> - **Compose expert:** `pointerInput` consume on content Surface is more robust than `clickable(enabled = false)` for preventing click-through
> - **Kotlin expert:** `@Stable` on `AppDrawerState` tells Compose mutations go through snapshot system
> - **Pattern recognition:** Using `DeckColumnType` directly with `title()`/`icon()`/`category()` instead of wrapper eliminates data duplication

#### Phase 3: Wire into Main.kt

```kotlin
// Create SinglePaneState (mirrors DeckState)
val singlePaneState = remember { SinglePaneState() }

// Replace showAddColumnDialog with showAppDrawer
var showAppDrawer by remember { mutableStateOf(false) }

// MenuBar — Cmd+K always available (View menu)
Menu("View") {
    Item(
        "App Drawer",
        shortcut = if (isMacOS) KeyShortcut(Key.K, meta = true) else KeyShortcut(Key.K, ctrl = true),
        onClick = { showAppDrawer = !showAppDrawer },
    )
    // ... existing layout toggle, deck-only items
}
// Cmd+T also opens drawer (backward compat, both modes)
Item("Add Column", shortcut = KeyShortcut(Key.T, ...)) {
    showAppDrawer = true
}

// Collect open columns as state (not .value) for reactivity
val openColumns by deckState.columns.collectAsState()

// Render overlay — if() block, NOT AnimatedVisibility
if (showAppDrawer) {
    AppDrawer(
        openColumnTypes = if (layoutMode == LayoutMode.DECK) {
            openColumns.map { it.type.typeKey() }.toSet()
        } else emptySet(),
        onSelectScreen = { type ->
            when (layoutMode) {
                LayoutMode.SINGLE_PANE -> singlePaneState.navigate(type)
                LayoutMode.DECK -> {
                    if (deckState.hasColumnOfType(type)) {
                        deckState.focusExistingColumn(type)
                    } else {
                        deckState.addColumn(type)
                    }
                }
            }
        },
        onDismiss = { showAppDrawer = false },
    )
}
```

> **Research insight (race conditions):** Use `collectAsState()` for `openColumnTypes` rendering, not `deckState.columns.value`. Direct `.value` reads are stale for UI — Compose won't recompose when the flow emits.

#### Phase 4: Update DeckSidebar + SinglePaneLayout

**DeckSidebar.kt:**
- Replace `onShowAddColumnDialog` callback → `onShowAppDrawer`

**SinglePaneLayout.kt:**
- Accept `SinglePaneState` instead of local `currentColumnType`
- Accept `onOpenAppDrawer: () -> Unit`
- Add `IconButton(Icons.Default.Apps)` at bottom of NavigationRail
- Replace hardcoded `navItems` with `LAUNCHABLE_SCREENS.filter { it.category() != ScreenCategory.PLAY }` or a curated default list
- Read `val currentScreen by singlePaneState.currentScreen.collectAsState()`

#### Phase 5: Delete AddColumnDialog.kt + Cleanup

- Remove `AddColumnDialog.kt`
- Remove `showAddColumnDialog` from `Main.kt`
- Remove `COLUMN_OPTIONS` list
- Remove `onShowAddColumnDialog` callbacks from `DeckSidebar`, etc.
- Verify `navItems` in `SinglePaneLayout` is replaced or reads from `LAUNCHABLE_SCREENS`

### Keyboard Event Handling

```
Drawer open (onPreviewKeyEvent on outer Box):
  Escape           → awaitingHashtag ? cancel : onDismiss()
  Up Arrow         → state.moveSelection(-1)  [consumed before TextField]
  Down Arrow       → state.moveSelection(+1)  [consumed before TextField]
  Enter            → awaitingHashtag ? confirmHashtag : select highlighted
  Any printable    → falls through to TextField (not consumed)
  Backspace        → falls through to TextField (not consumed)
```

> **Desktop expert:** `onPreviewKeyEvent` fires before children process the event. Returning `true` for arrows/Enter prevents TextField from consuming them. Returning `false` for printable keys lets TextField handle typing. This exact pattern is used in `SearchScreen.kt:293` and `ChatPane.kt:729`.

## System-Wide Impact

### Interaction Graph

```
Cmd+K pressed
  → Main.kt: showAppDrawer = true
    → AppDrawer composed (fresh state via remember)
      → searchFocusRequester.requestFocus() (after awaitFrame)
      → User types → derivedStateOf recalculates filteredScreens
        → DrawerGrid recomposes (isolated scope)
      → User selects screen (consumed flag prevents double-fire)
        → onSelectScreen(DeckColumnType) fires
          → DECK: deckState.addColumn() or focusExisting()
            → DeckState.save() (debounced 500ms)
          → SINGLE_PANE: singlePaneState.navigate(type)
            → currentScreen flow emits → RootContent recomposes
        → onDismiss → showAppDrawer = false
          → AppDrawer leaves composition → state garbage collected
```

### State Lifecycle

- **No stale state risk:** `if()` block destroys composable on close. `remember` creates fresh `AppDrawerState` on each open. No `LaunchedEffect(Unit) { reset() }` needed.
- **Hashtag input cleanup:** Automatic — state destroyed on dismiss.
- **SinglePaneLayout state:** `SinglePaneState` created at `App()` level, passed down. No hoisting problem.
- **Open column indicators:** `collectAsState()` ensures reactivity.

### API Surface Parity

| Interface | Change |
|-----------|--------|
| `DeckColumnType` | Add `category()` extension, `requiresInput()` extension, `LAUNCHABLE_SCREENS` list |
| `DeckSidebar.onAddColumn` | Rename to `onShowAppDrawer` |
| `SinglePaneLayout` | Accept `SinglePaneState` + `onOpenAppDrawer` params |
| `Main.kt` MenuBar | Add Cmd+K item in "View" menu. Update Cmd+T to open drawer |
| `AddColumnDialog` | Delete |

### Registry Consolidation

| Before (3+ registries) | After (1 source of truth) |
|------------------------|--------------------------|
| `COLUMN_OPTIONS` in AddColumnDialog.kt | **Deleted** — replaced by `LAUNCHABLE_SCREENS` |
| `navItems` in SinglePaneLayout.kt | **Replaced** — reads from `LAUNCHABLE_SCREENS` |
| `title()` in DeckColumnType.kt | **Kept** — single source for labels |
| `icon()` in ColumnHeader.kt | **Kept** — single source for icons |
| `DRAWER_SCREENS` (was planned) | **Never created** — `LAUNCHABLE_SCREENS` + extensions |

## Acceptance Criteria

### Functional Requirements

- [ ] Cmd+K opens App Drawer in both Single Pane and Deck modes
- [ ] Cmd+T opens App Drawer (backward compat, both modes)
- [ ] Search field auto-focused on open
- [ ] Typing filters screens across all categories in real-time
- [ ] Empty categories hidden during search
- [ ] 14 screens across 5 categories displayed correctly
- [ ] Each screen shows correct icon (from `icon()`) and label (from `title()`)
- [ ] Clicking a screen in Single Pane navigates via `SinglePaneState`
- [ ] Clicking a screen in Deck adds column (or focuses existing for object types)
- [ ] Hashtag selection shows secondary text input, Enter confirms
- [ ] Editor selection opens new draft (null slug)
- [ ] Escape closes drawer (or cancels hashtag input)
- [ ] Clicking backdrop closes drawer
- [ ] Arrow keys move selection highlight
- [ ] Enter selects highlighted screen
- [ ] Already-open columns show indicator dot in Deck mode
- [ ] Double-click does NOT create duplicate columns
- [ ] "+" button in DeckSidebar opens App Drawer
- [ ] Apps button in SinglePaneLayout nav rail opens App Drawer
- [ ] `AddColumnDialog.kt` deleted, no remaining references
- [ ] `COLUMN_OPTIONS` and `navItems` removed/replaced

### Non-Functional Requirements

- [ ] Drawer opens in <100ms (no network calls, pure UI)
- [ ] Search filtering is instant (`derivedStateOf` caching)
- [ ] No animation (correctness > aesthetics — avoids stale state race condition)
- [ ] Follows Material3 color scheme (Surface, onSurface, primaryContainer)

## Dependencies & Risks

| Risk | Mitigation | Source |
|------|------------|--------|
| `currentColumnType` in SinglePaneLayout is local state | Create `SinglePaneState` class | architecture-strategist |
| `AnimatedVisibility` causes stale state on re-open | Use `if()` block instead | race-conditions-reviewer (CRITICAL) |
| Double-click fires `onSelectScreen` twice | `consumed` flag in `AppDrawerState` | race-conditions-reviewer |
| `clickable(enabled=false)` doesn't consume events | Use `pointerInput` consume pattern | compose-expert |
| `onKeyEvent` doesn't intercept before TextField | Use `onPreviewKeyEvent` | desktop-expert |
| Focus may not grab immediately on Desktop | `awaitFrame()` before `requestFocus()` | compose-expert |
| `deckState.columns.value` is stale for UI | Use `collectAsState()` | race-conditions-reviewer |
| `FlowRow` inside `LazyColumn` measures eagerly | Fine at 14 items; max 4 per category | performance-oracle |

## Sources & References

### Origin

- **Brainstorm document:** [docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md](docs/brainstorms/2026-03-30-desktop-navigation-overhaul-brainstorm.md)

### Internal References (Codebase Patterns)

- `GlobalFullscreenOverlay.kt` — fullscreen Box overlay pattern (z-order in MainContent)
- `LightboxOverlay.kt:202-253` — focusRequester + onKeyEvent + clickable scrim pattern
- `SearchScreen.kt:293` — `onPreviewKeyEvent` intercepting Enter/Escape before TextField
- `ChatPane.kt:729` — same `onPreviewKeyEvent` pattern
- `DeckColumnType.kt:25-111` — sealed class, `title()`, `typeKey()`
- `ColumnHeader.kt:123-142` — `icon()` extension
- `DeckState.kt` — StateFlow pattern, addColumn(), save/load persistence
- `SinglePaneLayout.kt:80-93,115` — navItems + currentColumnType (being replaced)
- `Main.kt:187,297-307,570-578` — showAddColumnDialog + shortcuts + dialog render
- `AddColumnDialog.kt:49-62` — COLUMN_OPTIONS (being deleted)

### External References

- [Compose Desktop keyboard events](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-keyboard.html)
- [Focus in Compose](https://developer.android.com/develop/ui/compose/touch-input/focus)
- [AnimatedVisibility docs](https://developer.android.com/develop/ui/compose/animation/composables-modifiers)

## Open Questions

- Should `SinglePaneState` persist last-viewed screen to `DesktopPreferences`? (nice-to-have)
- Should `ScreenCategory` live in `DeckColumnType.kt` or `ColumnHeader.kt`? (colocation question)
- Future: Cmd+K also search within content (notes, profiles)? (v2+ concern)
- Mouse hover on grid items should update `selectedIndex`? (nice keyboard+mouse hybrid UX)
