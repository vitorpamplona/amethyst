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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarCategories
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarEntries
import com.vitorpamplona.amethyst.ui.navigation.bottombars.GroupEntryAvatar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.GroupEntryDisplay
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import com.vitorpamplona.amethyst.ui.navigation.bottombars.rememberGroupEntryDisplay
import com.vitorpamplona.amethyst.ui.navigation.bottombars.stableKey
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

/** The chat catalog items whose picker row expands to a per-item picker (favorites / joined groups). */
private val ExpandableItems =
    setOf(
        NavBarItem.BROWSER,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.RELAY_GROUPS,
        NavBarItem.CONCORD,
    )

@Composable
fun BottomBarSettingsContent(accountViewModel: AccountViewModel) {
    val bottomBarItemsFlow = accountViewModel.settings.uiSettingsFlow.bottomBarItems
    val savedItems by bottomBarItemsFlow.collectAsStateWithLifecycle()

    // A local, drag-mutable copy of the pinned list. Re-seeded whenever the saved list changes from
    // elsewhere (e.g. a favorite/group got pinned from its picker row, or Restore Default ran).
    var pinned by remember(savedItems) { mutableStateOf(savedItems) }

    fun save(newItems: List<BottomBarEntry>) {
        pinned = newItems
        bottomBarItemsFlow.tryEmit(newItems)
    }

    val pinnedKeys = remember(pinned) { pinned.map { it.stableKey }.toSet() }

    fun togglePin(entry: BottomBarEntry) {
        if (entry.stableKey in pinnedKeys) {
            save(pinned.filter { it.stableKey != entry.stableKey })
        } else {
            save(pinned + entry)
        }
    }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Float>() }
    val isDragging = draggedItemIndex >= 0
    val scrollState = remember { ScrollState(0) }

    val expandedCategories = remember { mutableStateMapOf<Int, Boolean>() }
    val expandedItems = remember { mutableStateMapOf<NavBarItem, Boolean>() }

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
                    save(DefaultBottomBarEntries)
                },
            ) {
                Text(stringRes(R.string.bottom_bar_settings_restore_default))
            }
        }

        // --- Pinned section: the current bottom bar, drag-reorderable. ---
        SectionDivider(R.string.bottom_bar_settings_pinned)

        pinned.forEachIndexed { index, entry ->
            val rowIsDragging = draggedItemIndex == index
            val targetElevation = if (rowIsDragging) 8f else 0f
            val animatedElevation by animateFloatAsState(
                targetValue = targetElevation,
                label = "dragElevation",
            )

            PinnedEntryCard(
                entry = entry,
                accountViewModel = accountViewModel,
                isDragging = rowIsDragging,
                dragOffsetY = if (rowIsDragging) dragOffset else 0f,
                elevation = animatedElevation,
                onUnpin = { togglePin(entry) },
                onMeasured = { height -> itemHeights[index] = height },
                onDragStart = {
                    draggedItemIndex = index
                    dragOffset = 0f
                },
                onDrag = { dragAmount ->
                    dragOffset += dragAmount

                    val currentIndex = draggedItemIndex
                    if (currentIndex < 0) return@PinnedEntryCard

                    if (dragOffset < 0 && currentIndex > 0) {
                        val aboveHeight = itemHeights[currentIndex - 1] ?: 0f
                        if (-dragOffset > aboveHeight / 2f) {
                            val newItems = pinned.toMutableList()
                            val temp = newItems[currentIndex - 1]
                            newItems[currentIndex - 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            pinned = newItems

                            val h1 = itemHeights[currentIndex]
                            val h2 = itemHeights[currentIndex - 1]
                            if (h1 != null) itemHeights[currentIndex - 1] = h1
                            if (h2 != null) itemHeights[currentIndex] = h2

                            dragOffset += aboveHeight
                            draggedItemIndex = currentIndex - 1
                        }
                    }

                    if (dragOffset > 0 && currentIndex < pinned.lastIndex) {
                        val belowHeight = itemHeights[currentIndex + 1] ?: 0f
                        if (dragOffset > belowHeight / 2f) {
                            val newItems = pinned.toMutableList()
                            val temp = newItems[currentIndex + 1]
                            newItems[currentIndex + 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            pinned = newItems

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
                    save(pinned)
                },
                onDragCancel = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                },
            )

            if (index < pinned.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))
            }
        }

        if (pinned.isEmpty()) {
            Text(
                text = stringRes(R.string.bottom_bar_settings_pinned_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = Size20dp),
            )
        }

        // --- Available section: the full catalog, grouped into collapsible categories. ---
        Spacer(modifier = Modifier.height(8.dp))

        BottomBarCategories.forEach { category ->
            val expanded = expandedCategories[category.titleRes] ?: false
            CategoryHeader(
                titleRes = category.titleRes,
                expanded = expanded,
                onToggle = { expandedCategories[category.titleRes] = !expanded },
            )
            AnimatedVisibility(visible = expanded) {
                Column {
                    category.items.forEach { item ->
                        val def = NavBarCatalog[item] ?: return@forEach
                        val entry = BottomBarEntry.BuiltIn(item)
                        if (item in ExpandableItems) {
                            ExpandablePickerRow(
                                icon = def.icon,
                                label = stringRes(def.labelRes),
                                pinned = entry.stableKey in pinnedKeys,
                                expanded = expandedItems[item] ?: false,
                                onTogglePin = { togglePin(entry) },
                                onToggleExpand = { expandedItems[item] = !(expandedItems[item] ?: false) },
                            ) {
                                PickerChildren(item, pinnedKeys, accountViewModel, ::togglePin)
                            }
                        } else {
                            SimpleAvailableRow(
                                icon = def.icon,
                                label = stringRes(def.labelRes),
                                pinned = entry.stableKey in pinnedKeys,
                                onToggle = { togglePin(entry) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The joined-groups (or favorites) child rows revealed when an expandable picker row opens. */
@Composable
private fun PickerChildren(
    item: NavBarItem,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    when (item) {
        NavBarItem.BROWSER -> {
            val favorites by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
            if (favorites.isEmpty()) {
                EmptyChildHint(R.string.bottom_bar_settings_no_favorites)
            } else {
                favorites.forEach { fav ->
                    val entry = BottomBarEntry.Favorite(fav.id)
                    ChildRow(
                        leading = { FavoriteChildIcon(fav) },
                        label = fav.label,
                        pinned = entry.stableKey in pinnedKeys,
                        onToggle = { onTogglePin(entry) },
                    )
                }
            }
        }

        NavBarItem.PUBLIC_CHATS -> {
            val channels by accountViewModel.account.publicChatList.flow
                .collectAsStateWithLifecycle()
            val sorted = remember(channels) { channels.map { BottomBarEntry.PublicChat(it.eventId) } }
            if (sorted.isEmpty()) {
                EmptyChildHint(R.string.bottom_bar_settings_no_groups)
            } else {
                sorted.forEach { entry -> GroupChildRow(entry, pinnedKeys, accountViewModel, onTogglePin) }
            }
        }

        NavBarItem.RELAY_GROUPS -> {
            val groups by accountViewModel.account.relayGroupList.liveRelayGroupList
                .collectAsStateWithLifecycle()
            val sorted =
                remember(groups) {
                    groups
                        .sortedBy { (it.name ?: it.groupId).lowercase() }
                        .map { BottomBarEntry.RelayGroup(it.groupId, it.relayUrl) }
                }
            if (sorted.isEmpty()) {
                EmptyChildHint(R.string.bottom_bar_settings_no_groups)
            } else {
                sorted.forEach { entry -> GroupChildRow(entry, pinnedKeys, accountViewModel, onTogglePin) }
            }
        }

        NavBarItem.CONCORD -> {
            val communities by accountViewModel.account.concordChannelList.liveCommunities
                .collectAsStateWithLifecycle()
            val sorted = remember(communities) { communities.map { BottomBarEntry.Concord(it.id) } }
            if (sorted.isEmpty()) {
                EmptyChildHint(R.string.bottom_bar_settings_no_groups)
            } else {
                sorted.forEach { entry -> GroupChildRow(entry, pinnedKeys, accountViewModel, onTogglePin) }
            }
        }

        else -> {}
    }
}

@Composable
private fun GroupChildRow(
    entry: BottomBarEntry,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    val display = rememberGroupEntryDisplay(entry, accountViewModel) ?: return
    ChildRow(
        leading = { GroupEntryAvatar(display, 28.dp, accountViewModel) },
        label = display.label,
        pinned = entry.stableKey in pinnedKeys,
        onToggle = { onTogglePin(entry) },
    )
}

@Composable
private fun FavoriteChildIcon(app: FavoriteApp) {
    val icon = if (app is FavoriteApp.NostrApp) MaterialSymbols.Apps else MaterialSymbols.Public
    NavBarIconBox(icon, app.label)
}

@Composable
private fun CategoryHeader(
    titleRes: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(top = 16.dp, bottom = 6.dp, start = Size20dp, end = Size20dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))
}

@Composable
private fun SimpleAvailableRow(
    icon: MaterialSymbol,
    label: String,
    pinned: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = Size20dp),
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
        Switch(checked = pinned, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ExpandablePickerRow(
    icon: MaterialSymbol,
    label: String,
    pinned: Boolean,
    expanded: Boolean,
    onTogglePin: () -> Unit,
    onToggleExpand: () -> Unit,
    children: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 8.dp, horizontal = Size20dp),
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
        Icon(
            symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = stringRes(R.string.bottom_bar_settings_expand),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = pinned, onCheckedChange = { onTogglePin() })
    }
    AnimatedVisibility(visible = expanded) {
        Column { children() }
    }
}

@Composable
private fun ChildRow(
    leading: @Composable () -> Unit,
    label: String,
    pinned: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 44.dp, top = 6.dp, end = Size20dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = pinned, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun EmptyChildHint(textRes: Int) {
    Text(
        text = stringRes(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 44.dp, top = 6.dp, end = Size20dp, bottom = 6.dp),
    )
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
private fun PinnedEntryCard(
    entry: BottomBarEntry,
    accountViewModel: AccountViewModel,
    isDragging: Boolean,
    dragOffsetY: Float,
    elevation: Float,
    onUnpin: () -> Unit,
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
                .graphicsLayer {
                    translationY = dragOffsetY
                    shadowElevation = elevation
                    if (isDragging) {
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }.onGloballyPositioned { coordinates ->
                    onMeasured(coordinates.size.height.toFloat())
                }.padding(vertical = 8.dp, horizontal = Size20dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                },
    ) {
        val visual = rememberPinnedVisual(entry, accountViewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (visual) {
                is PinnedVisual.Glyph -> NavBarIconBox(visual.icon, visual.label)
                is PinnedVisual.Avatar -> GroupEntryAvatar(visual.display, 28.dp, accountViewModel)
            }

            Text(
                text = visual.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Switch(
                checked = true,
                onCheckedChange = { onUnpin() },
            )

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
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

/** Resolved leading + label for a pinned entry, computed once so a group's channel is subscribed once. */
private sealed interface PinnedVisual {
    val label: String

    data class Glyph(
        val icon: MaterialSymbol,
        override val label: String,
    ) : PinnedVisual

    data class Avatar(
        val display: GroupEntryDisplay,
    ) : PinnedVisual {
        override val label: String get() = display.label
    }
}

@Composable
private fun rememberPinnedVisual(
    entry: BottomBarEntry,
    accountViewModel: AccountViewModel,
): PinnedVisual =
    when (entry) {
        is BottomBarEntry.BuiltIn -> {
            val def = NavBarCatalog[entry.item]
            PinnedVisual.Glyph(def?.icon ?: MaterialSymbols.Apps, def?.let { stringRes(it.labelRes) } ?: "")
        }
        is BottomBarEntry.Favorite -> {
            val favorites by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
            val app = favorites.firstOrNull { it.id == entry.favoriteId }
            val icon = if (app is FavoriteApp.NostrApp) MaterialSymbols.Apps else MaterialSymbols.Public
            PinnedVisual.Glyph(icon, app?.label ?: "")
        }
        is BottomBarEntry.PublicChat,
        is BottomBarEntry.RelayGroup,
        is BottomBarEntry.Concord,
        -> {
            val display = rememberGroupEntryDisplay(entry, accountViewModel)
            if (display != null) PinnedVisual.Avatar(display) else PinnedVisual.Glyph(MaterialSymbols.Group, "")
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
