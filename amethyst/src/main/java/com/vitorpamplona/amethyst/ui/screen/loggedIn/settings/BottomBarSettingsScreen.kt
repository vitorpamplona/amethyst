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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow

@Composable
@Preview(device = "spec:width=2100px,height=2340px,dpi=440")
fun BottomBarSettingsScreenPreview() {
    ThemeComparisonRow {
        BottomBarSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun BottomBarSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.bottom_bar_settings), nav)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BottomBarSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun BottomBarSettingsContent(accountViewModel: AccountViewModel) {
    val bottomBarItemsFlow = accountViewModel.settings.uiSettingsFlow.bottomBarItems
    // Favorite apps appear in the SAME list as the built-in destinations, so they can be pinned and
    // drag-reordered together. Re-seed when the favorites set changes (e.g. a favorite was deleted).
    val favorites by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    var items by remember(favorites) { mutableStateOf(initialRows(bottomBarItemsFlow.value, favorites)) }

    fun save(newItems: List<Row>) {
        items = newItems
        bottomBarItemsFlow.tryEmit(
            newItems.filter { it.pinned }.map { it.entry },
        )
    }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Float>() }
    val isDragging = draggedItemIndex >= 0
    val scrollState = remember { ScrollState(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !isDragging),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringRes(R.string.bottom_bar_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp, start = Size20dp, end = Size20dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = Size20dp, end = Size20dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                    save(initialRows(DefaultBottomBarEntries, favorites))
                },
            ) {
                Text(stringRes(R.string.bottom_bar_settings_restore_default))
            }
        }

        items.forEachIndexed { index, row ->
            val display = rowDisplay(row.entry, favorites)
            val rowIsDragging = draggedItemIndex == index
            val targetElevation = if (rowIsDragging) 8f else 0f
            val animatedElevation by animateFloatAsState(
                targetValue = targetElevation,
                label = "dragElevation",
            )

            NavBarItemCard(
                icon = display.icon,
                label = display.label,
                isDragging = rowIsDragging,
                canDrag = row.pinned,
                dragOffsetY = if (rowIsDragging) dragOffset else 0f,
                elevation = animatedElevation,
                pinned = row.pinned,
                onTogglePinned = {
                    val newItems = items.toMutableList()
                    val toggled = row.copy(pinned = !row.pinned)
                    newItems.removeAt(index)
                    val insertIndex =
                        if (toggled.pinned) {
                            newItems.indexOfFirst { !it.pinned }.let { if (it < 0) newItems.size else it }
                        } else {
                            val firstUnpinned = newItems.indexOfFirst { !it.pinned }
                            if (firstUnpinned < 0) newItems.size else firstUnpinned
                        }
                    newItems.add(insertIndex, toggled)
                    save(newItems)
                },
                onMeasured = { height ->
                    itemHeights[index] = height
                },
                onDragStart = {
                    draggedItemIndex = index
                    dragOffset = 0f
                },
                onDrag = { dragAmount ->
                    dragOffset += dragAmount

                    val currentIndex = draggedItemIndex
                    if (currentIndex < 0) return@NavBarItemCard

                    // Can only swap among pinned items (row.pinned == true).
                    if (dragOffset < 0 && currentIndex > 0 && items[currentIndex - 1].pinned) {
                        val aboveHeight = itemHeights[currentIndex - 1] ?: 0f
                        if (-dragOffset > aboveHeight / 2f) {
                            val newItems = items.toMutableList()
                            val temp = newItems[currentIndex - 1]
                            newItems[currentIndex - 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            items = newItems

                            val h1 = itemHeights[currentIndex]
                            val h2 = itemHeights[currentIndex - 1]
                            if (h1 != null) itemHeights[currentIndex - 1] = h1
                            if (h2 != null) itemHeights[currentIndex] = h2

                            dragOffset += aboveHeight
                            draggedItemIndex = currentIndex - 1
                        }
                    }

                    if (dragOffset > 0 &&
                        currentIndex < items.lastIndex &&
                        items[currentIndex + 1].pinned
                    ) {
                        val belowHeight = itemHeights[currentIndex + 1] ?: 0f
                        if (dragOffset > belowHeight / 2f) {
                            val newItems = items.toMutableList()
                            val temp = newItems[currentIndex + 1]
                            newItems[currentIndex + 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            items = newItems

                            val h1 = itemHeights[currentIndex]
                            val h2 = itemHeights[currentIndex + 1]
                            if (h1 != null) itemHeights[currentIndex + 1] = h1
                            if (h2 != null) itemHeights[currentIndex] = h2

                            dragOffset -= belowHeight
                            draggedItemIndex = currentIndex + 1
                        }
                    }
                },
                onDragEnd = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                    save(items)
                },
                onDragCancel = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                },
                modifier =
                    Modifier
                        .zIndex(if (rowIsDragging) 1f else 0f),
            )

            val nextIsFirstUnpinned =
                index < items.lastIndex && row.pinned && !items[index + 1].pinned
            if (nextIsFirstUnpinned) {
                SectionDivider(R.string.bottom_bar_settings_available)
            } else if (index < items.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class Row(
    val entry: BottomBarEntry,
    val pinned: Boolean,
)

/** Display (icon + label) for an entry, resolving built-ins via the catalog and favorites via the registry. */
private class RowDisplay(
    val icon: MaterialSymbol,
    val label: String,
)

@Composable
private fun rowDisplay(
    entry: BottomBarEntry,
    favorites: List<FavoriteApp>,
): RowDisplay =
    when (entry) {
        is BottomBarEntry.BuiltIn -> {
            val def = NavBarCatalog[entry.item]
            if (def != null) RowDisplay(def.icon, stringRes(def.labelRes)) else RowDisplay(MaterialSymbols.Apps, "")
        }
        is BottomBarEntry.Favorite -> {
            val app = favorites.firstOrNull { it.id == entry.favoriteId }
            val icon = if (app is FavoriteApp.NostrApp) MaterialSymbols.Apps else MaterialSymbols.Public
            RowDisplay(icon, app?.label ?: "")
        }
    }

/**
 * Builds the row list: [pinned] entries first (in saved order, dropping any that no longer resolve —
 * e.g. a deleted favorite), then every still-available entry — built-in destinations and the user's
 * favorite apps — in the available section.
 */
private fun initialRows(
    pinned: List<BottomBarEntry>,
    favorites: List<FavoriteApp>,
): List<Row> {
    val available: List<BottomBarEntry> =
        NavBarCatalog.keys.map { BottomBarEntry.BuiltIn(it) } + favorites.map { BottomBarEntry.Favorite(it.id) }
    val availableSet = available.toSet()

    val pinnedRows = pinned.filter { it in availableSet }.map { Row(it, pinned = true) }
    val unpinnedRows = available.filter { it !in pinned }.map { Row(it, pinned = false) }
    return pinnedRows + unpinnedRows
}

@Composable
private fun SectionDivider(titleRes: Int) {
    Text(
        text = stringRes(titleRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp, start = Size20dp, end = Size20dp),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))
}

@Composable
private fun NavBarItemCard(
    icon: MaterialSymbol,
    label: String,
    isDragging: Boolean,
    canDrag: Boolean,
    dragOffsetY: Float,
    elevation: Float,
    pinned: Boolean,
    onTogglePinned: () -> Unit,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onMeasured(coordinates.size.height.toFloat())
                }.graphicsLayer {
                    translationY = dragOffsetY
                    shadowElevation = elevation
                    if (isDragging) {
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }.padding(vertical = 8.dp, horizontal = Size20dp)
                .then(
                    if (canDrag) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NavBarIconBox(icon, label)

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Switch(
                checked = pinned,
                onCheckedChange = { onTogglePinned() },
            )

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (canDrag) {
                    Icon(
                        MaterialSymbols.DragIndicator,
                        contentDescription = stringRes(R.string.bottom_bar_settings_reorder),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarIconBox(
    icon: MaterialSymbol,
    label: String,
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
