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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
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

// -- State --

@Stable
private class AppDrawerState {
    var searchQuery by mutableStateOf("")
    var selectedIndex by mutableStateOf(0)
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

    fun moveSelection(delta: Int) {
        val size = filteredScreens.size
        if (size > 0) selectedIndex = (selectedIndex + delta).coerceIn(0, size - 1)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppDrawer(
    openColumnTypes: Set<String>,
    pinnedNavBarState: PinnedNavBarState,
    onSelectScreen: (DeckColumnType) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { AppDrawerState() }
    val searchFocusRequester = remember { FocusRequester() }

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
                            state.moveSelection(1)
                            true
                        }

                        Key.DirectionUp -> {
                            state.moveSelection(-1)
                            true
                        }

                        Key.Enter -> {
                            if (state.awaitingHashtag) {
                                state.confirmHashtag(onSelectScreen, onDismiss)
                            } else {
                                state.filteredScreens.getOrNull(state.selectedIndex)?.let {
                                    state.select(it, onSelectScreen, onDismiss)
                                }
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
                    placeholder = { Text("Search screens...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                )

                if (state.awaitingHashtag) {
                    HashtagInputSection(state, onSelectScreen, onDismiss)
                } else {
                    DrawerGrid(state, openColumnTypes, pinnedNavBarState, onSelectScreen, onDismiss)
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
                        // Right-click opens context menu
                        if (event.changes.any { it.pressed && event.button?.index == 2 }) {
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
        androidx.compose.material3.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (PinnedNavBarState.isPinnable(type)) {
                androidx.compose.material3.DropdownMenuItem(
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
