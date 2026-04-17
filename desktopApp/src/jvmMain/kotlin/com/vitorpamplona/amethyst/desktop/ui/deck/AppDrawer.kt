/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.LayoutMode
import kotlinx.coroutines.delay

// -- Screen categories --

enum class ScreenCategory(
    val title: String,
    val icon: ImageVector,
) {
    SOCIAL("Social", Icons.Default.Groups),
    LONG_FORM("Long-Form", Icons.AutoMirrored.Filled.Article),
    DISCOVERY("Discovery", Icons.Default.Explore),
    IDENTITY("Identity", Icons.Default.Person),
    PLAY("Play", Icons.Default.SportsEsports),
}

// -- Extensions on DeckColumnType --

fun DeckColumnType.category(): ScreenCategory =
    when (this) {
        DeckColumnType.HomeFeed,
        DeckColumnType.Notifications,
        DeckColumnType.Messages,
        DeckColumnType.GlobalFeed,
        -> ScreenCategory.SOCIAL

        DeckColumnType.Reads,
        DeckColumnType.Drafts,
        is DeckColumnType.Editor,
        DeckColumnType.MyHighlights,
        -> ScreenCategory.LONG_FORM

        DeckColumnType.Search,
        is DeckColumnType.Hashtag,
        DeckColumnType.Bookmarks,
        -> ScreenCategory.DISCOVERY

        DeckColumnType.MyProfile,
        DeckColumnType.Settings,
        -> ScreenCategory.IDENTITY

        DeckColumnType.Chess -> ScreenCategory.PLAY

        // Deep-link types — not in LAUNCHABLE_SCREENS but need a category for exhaustiveness
        is DeckColumnType.Profile,
        is DeckColumnType.Thread,
        is DeckColumnType.Article,
        -> ScreenCategory.SOCIAL
    }

fun DeckColumnType.requiresInput(): Boolean =
    when (this) {
        is DeckColumnType.Hashtag -> true
        else -> false
    }

val LAUNCHABLE_SCREENS: List<DeckColumnType> =
    listOf(
        DeckColumnType.HomeFeed,
        DeckColumnType.Notifications,
        DeckColumnType.Messages,
        DeckColumnType.GlobalFeed,
        DeckColumnType.Reads,
        DeckColumnType.Drafts,
        DeckColumnType.Editor(),
        DeckColumnType.MyHighlights,
        DeckColumnType.Search,
        DeckColumnType.Hashtag(""),
        DeckColumnType.Bookmarks,
        DeckColumnType.MyProfile,
        DeckColumnType.Settings,
        DeckColumnType.Chess,
    )

// -- Tabs --

enum class AppDrawerTab {
    SCREENS,
    WORKSPACES,
}

// -- State --

@Stable
private class AppDrawerState {
    var searchQuery by mutableStateOf("")
    var selectedIndex by mutableStateOf(0)
    var activeTab by mutableStateOf(AppDrawerTab.SCREENS)
    var hashtagInput by mutableStateOf("")
    var awaitingHashtag by mutableStateOf(false)
    private var consumed by mutableStateOf(false)

    val filteredScreens: List<DeckColumnType> by derivedStateOf {
        if (searchQuery.isBlank()) {
            LAUNCHABLE_SCREENS
        } else {
            LAUNCHABLE_SCREENS.filter {
                it.title().contains(searchQuery, ignoreCase = true) ||
                    it.category().title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedScreens: Map<ScreenCategory, List<DeckColumnType>> by derivedStateOf {
        filteredScreens.groupBy { it.category() }.filterValues { it.isNotEmpty() }
    }

    val isSearching: Boolean by derivedStateOf { searchQuery.isNotBlank() }

    fun filteredWorkspaces(allWorkspaces: List<Workspace>): List<Workspace> =
        if (searchQuery.isBlank()) {
            allWorkspaces
        } else {
            allWorkspaces.filter { ws ->
                ws.name.contains(searchQuery, ignoreCase = true) ||
                    ws.columns.any { col ->
                        val displayName =
                            DeckState.parseColumnTypeFromKey(col.typeKey, col.param)?.title()
                                ?: col.typeKey
                        displayName.contains(searchQuery, ignoreCase = true)
                    }
            }
        }

    fun moveSelection(delta: Int) {
        val size = filteredScreens.size
        if (size > 0) selectedIndex = (selectedIndex + delta).coerceIn(0, size - 1)
    }

    fun moveSelectionUnified(
        delta: Int,
        wsCount: Int,
    ) {
        val total = wsCount + filteredScreens.size
        if (total > 0) selectedIndex = (selectedIndex + delta).coerceIn(0, total - 1)
    }

    fun select(
        screen: DeckColumnType,
        onSelectScreen: (DeckColumnType) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (consumed) return
        if (screen.requiresInput()) {
            awaitingHashtag = true
            hashtagInput = ""
        } else {
            consumed = true
            onSelectScreen(screen)
            onDismiss()
        }
    }

    fun selectCurrent(
        filteredWs: List<Workspace>,
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
        val wsCount = filteredWs.size
        if (selectedIndex < wsCount) {
            consumed = true
            onSwitchWorkspace(filteredWs[selectedIndex])
            onDismiss()
        } else {
            val screenIdx = selectedIndex - wsCount
            filteredScreens.getOrNull(screenIdx)?.let { select(it, onSelectScreen, onDismiss) }
        }
    }

    fun confirmHashtag(
        onSelectScreen: (DeckColumnType) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (consumed || hashtagInput.isBlank()) return
        consumed = true
        onSelectScreen(DeckColumnType.Hashtag(hashtagInput.removePrefix("#").trim()))
        onDismiss()
    }
}

// -- Composables --

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(
    openColumnTypes: Set<String>,
    pinnedNavBarState: PinnedNavBarState,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Workspace) -> Unit,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { AppDrawerState() }
    val searchFocusRequester = remember { FocusRequester() }
    val allWorkspaces by workspaceManager.workspaces.collectAsState()
    val filteredWs by remember(allWorkspaces, state.searchQuery) {
        derivedStateOf { state.filteredWorkspaces(allWorkspaces) }
    }

    LaunchedEffect(Unit) {
        delay(50)
        searchFocusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            if (state.awaitingHashtag) {
                                state.awaitingHashtag = false
                            } else {
                                onDismiss()
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            if (state.isSearching) {
                                state.moveSelectionUnified(1, filteredWs.size)
                            } else {
                                state.moveSelection(1)
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            if (state.isSearching) {
                                state.moveSelectionUnified(-1, filteredWs.size)
                            } else {
                                state.moveSelection(-1)
                            }
                            true
                        }

                        Key.Enter -> {
                            if (state.awaitingHashtag) {
                                state.confirmHashtag(onSelectScreen, onDismiss)
                            } else {
                                state.selectCurrent(
                                    filteredWs,
                                    onSelectScreen,
                                    onSwitchWorkspace,
                                    onDismiss,
                                )
                            }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(0.7f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* consume click — prevent propagation to scrim */ },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
        ) {
            Column {
                TextField(
                    value = state.searchQuery,
                    onValueChange = {
                        state.searchQuery = it
                        state.selectedIndex = 0
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester),
                    placeholder = { Text("Search screens and workspaces...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                )

                if (state.awaitingHashtag) {
                    HashtagInputSection(state, onSelectScreen, onDismiss)
                } else if (state.isSearching) {
                    Box(Modifier.weight(1f)) {
                        UnifiedSearchResults(
                            state = state,
                            filteredWs = filteredWs,
                            workspaceManager = workspaceManager,
                            onSwitchWorkspace = onSwitchWorkspace,
                            onSelectScreen = onSelectScreen,
                            openColumnTypes = openColumnTypes,
                            pinnedNavBarState = pinnedNavBarState,
                            onDismiss = onDismiss,
                        )
                    }
                } else {
                    // Tab header
                    PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
                        AppDrawerTab.entries.forEach { tab ->
                            Tab(
                                selected = state.activeTab == tab,
                                onClick = { state.activeTab = tab },
                                text = {
                                    Text(
                                        tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                    )
                                },
                            )
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        when (state.activeTab) {
                            AppDrawerTab.SCREENS -> {
                                DrawerGrid(
                                    state,
                                    openColumnTypes,
                                    pinnedNavBarState,
                                    onSelectScreen,
                                    onDismiss,
                                )
                            }

                            AppDrawerTab.WORKSPACES -> {
                                WorkspacesGrid(
                                    workspaceManager = workspaceManager,
                                    onSwitchWorkspace = {
                                        onSwitchWorkspace(it)
                                        onDismiss()
                                    },
                                    onDismiss = onDismiss,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DrawerGrid(
    state: AppDrawerState,
    openColumnTypes: Set<String>,
    pinnedNavBarState: PinnedNavBarState,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedIndex) {
        val approxItem = (state.selectedIndex / 4).coerceAtLeast(0)
        if (approxItem < listState.layoutInfo.totalItemsCount) {
            listState.animateScrollToItem(approxItem)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(top = 8.dp),
    ) {
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
                            isPinned = pinnedNavBarState.isPinned(screen),
                            onClick = { state.select(screen, onSelectScreen, onDismiss) },
                            onTogglePin = {
                                if (pinnedNavBarState.isPinned(screen)) {
                                    pinnedNavBarState.unpin(screen)
                                } else {
                                    pinnedNavBarState.pin(screen)
                                }
                            },
                            onHover = { state.selectedIndex = startIndex + localIdx },
                        )
                    }
                }
            }
            globalIndex += screens.size
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DrawerScreenCard(
    type: DeckColumnType,
    isSelected: Boolean,
    isOpen: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onHover: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier =
                Modifier
                    .size(80.dp)
                    .clickable(onClick = onClick)
                    .onPointerEvent(PointerEventType.Enter) { onHover() }
                    .onPointerEvent(PointerEventType.Press) { event ->
                        if (event.buttons.isSecondaryPressed &&
                            event.changes.any { it.pressed && !it.previousPressed }
                        ) {
                            showMenu = true
                        }
                    },
            shape = RoundedCornerShape(12.dp),
            tonalElevation = if (isSelected) 8.dp else 2.dp,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
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
                // Open indicator (top-end dot)
                if (isOpen) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                // Pinned indicator (top-start dot)
                if (isPinned) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    )
                }
            }
        }

        // Context menu for pin/unpin
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (PinnedNavBarState.isPinnable(type)) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Unpin from sidebar" else "Pin to sidebar") },
                    onClick = {
                        onTogglePin()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ScreenCategory) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
    val hashtagFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        hashtagFocusRequester.requestFocus()
    }

    Column(Modifier.padding(16.dp)) {
        Text("Enter hashtag:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.hashtagInput,
            onValueChange = { state.hashtagInput = it.removePrefix("#") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(hashtagFocusRequester),
            placeholder = { Text("bitcoin, nostr...") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { state.awaitingHashtag = false }) { Text("Back") }
            Button(
                onClick = { state.confirmHashtag(onSelectScreen, onDismiss) },
                enabled = state.hashtagInput.isNotBlank(),
            ) {
                Text("Open")
            }
        }
    }
}

// -- Workspaces Tab --

@Composable
private fun WorkspacesGrid(
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
                    }
                },
                onEdit = {
                    editTarget = ws
                    showEditor = true
                },
                onDelete = { workspaceManager.deleteWorkspace(ws.id) },
                canDelete = workspaces.size > 1,
            )
        }
        // "+" card
        item {
            AddWorkspaceCard(
                onClick = {
                    editTarget = null
                    showEditor = true
                },
                enabled = workspaces.size < WorkspaceManager.MAX_WORKSPACES,
            )
        }
    }

    if (showEditor) {
        WorkspaceEditorDialog(
            initial = editTarget,
            onSave = { ws ->
                if (editTarget != null) {
                    workspaceManager.updateWorkspace(ws)
                } else {
                    workspaceManager.addWorkspace(ws)
                }
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onSwitch),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isActive) 8.dp else 2.dp,
        color =
            if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                WorkspaceIcons.resolve(workspace.iconName),
                workspace.name,
                Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(workspace.name, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Layout mode badge — non-interactive Surface+Text
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
                }
                // Column preview with display names
                Text(
                    workspace.columns.joinToString(", ") { col ->
                        DeckState.parseColumnTypeFromKey(col.typeKey, col.param)?.title()
                            ?: col.typeKey
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Active indicator
            if (isActive) {
                Icon(
                    Icons.Default.Check,
                    "Active",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
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

@Composable
private fun AddWorkspaceCard(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Add, "Add workspace", Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "New Workspace",
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
}

// -- Workspace Editor Dialog --

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceEditorDialog(
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
                // Icon picker
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkspaceIcons.availableNames.forEach { iName ->
                        IconButton(onClick = { iconName = iName }) {
                            Icon(
                                WorkspaceIcons.resolve(iName),
                                iName,
                                tint =
                                    if (iName == iconName) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
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
                        ) {
                            Text("Single Pane")
                        }
                        SegmentedButton(
                            selected = layoutMode == LayoutMode.DECK,
                            onClick = { layoutMode = LayoutMode.DECK },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text("Deck")
                        }
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
                            id =
                                initial?.id ?: java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            name = name.ifBlank { "Untitled" },
                            iconName = iconName,
                            layoutMode = layoutMode,
                            columns = columns,
                            singlePaneScreen = singlePaneScreen,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeckColumnEditor(
    columns: List<Workspace.WorkspaceColumn>,
    onColumnsChange: (List<Workspace.WorkspaceColumn>) -> Unit,
) {
    Column {
        Text("Columns", style = MaterialTheme.typography.labelMedium)
        columns.forEachIndexed { idx, col ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DeckState.parseColumnTypeFromKey(col.typeKey, col.param)?.title() ?: col.typeKey,
                    Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (columns.size > 1) {
                            onColumnsChange(columns.toMutableList().apply { removeAt(idx) })
                        }
                    },
                    enabled = columns.size > 1,
                ) {
                    Icon(Icons.Default.Close, "Remove")
                }
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

@Composable
private fun SinglePaneScreenPicker(
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle =
        selected?.let { DeckState.parseColumnTypeFromKey(it)?.title() } ?: "Select screen"

    Column {
        Text("Screen", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { expanded = true }) { Text(selectedTitle) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LAUNCHABLE_SCREENS.filter { !it.requiresInput() }.forEach { screen ->
                DropdownMenuItem(
                    text = { Text(screen.title()) },
                    onClick = {
                        onSelect(screen.typeKey())
                        expanded = false
                    },
                )
            }
        }
    }
}

// -- Unified Search Results --

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedSearchResults(
    state: AppDrawerState,
    filteredWs: List<Workspace>,
    workspaceManager: WorkspaceManager,
    onSwitchWorkspace: (Workspace) -> Unit,
    onSelectScreen: (DeckColumnType) -> Unit,
    openColumnTypes: Set<String>,
    pinnedNavBarState: PinnedNavBarState,
    onDismiss: () -> Unit,
) {
    val activeIndex by workspaceManager.activeIndex.collectAsState()
    val allWorkspaces by workspaceManager.workspaces.collectAsState()

    LazyColumn(Modifier.padding(8.dp)) {
        // Workspace results first
        if (filteredWs.isNotEmpty()) {
            stickyHeader {
                Text(
                    "Workspaces",
                    style = MaterialTheme.typography.titleSmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(filteredWs) { idx, ws ->
                val wsIdx = allWorkspaces.indexOf(ws)
                val isHighlighted = idx == state.selectedIndex
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    tonalElevation = if (isHighlighted) 8.dp else 0.dp,
                ) {
                    WorkspaceCard(
                        workspace = ws,
                        isActive = wsIdx == activeIndex,
                        onSwitch = {
                            val switched = workspaceManager.switchTo(wsIdx)
                            if (switched != null) {
                                onSwitchWorkspace(switched)
                                onDismiss()
                            }
                        },
                        onEdit = { /* not supported in search results */ },
                        onDelete = { workspaceManager.deleteWorkspace(ws.id) },
                        canDelete = allWorkspaces.size > 1,
                    )
                }
            }
        }
        // Screen results below
        if (state.filteredScreens.isNotEmpty()) {
            stickyHeader {
                Text(
                    "Screens",
                    style = MaterialTheme.typography.titleSmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    val wsOffset = filteredWs.size
                    state.filteredScreens.forEachIndexed { localIdx, screen ->
                        DrawerScreenCard(
                            type = screen,
                            isSelected = (wsOffset + localIdx) == state.selectedIndex,
                            isOpen = openColumnTypes.contains(screen.typeKey()),
                            isPinned = pinnedNavBarState.isPinned(screen),
                            onClick = { state.select(screen, onSelectScreen, onDismiss) },
                            onTogglePin = {
                                if (pinnedNavBarState.isPinned(screen)) {
                                    pinnedNavBarState.unpin(screen)
                                } else {
                                    pinnedNavBarState.pin(screen)
                                }
                            },
                            onHover = { state.selectedIndex = wsOffset + localIdx },
                        )
                    }
                }
            }
        }
    }
}
