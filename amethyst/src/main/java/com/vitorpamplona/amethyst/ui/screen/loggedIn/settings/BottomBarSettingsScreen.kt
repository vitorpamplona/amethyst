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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarCategories
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.GroupEntryAvatar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.GroupEntryDisplay
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarCategory
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import com.vitorpamplona.amethyst.ui.navigation.bottombars.rememberFavoriteIconModel
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

/** The chat catalog items whose picker row expands to a per-item picker (favorites / joined groups). */
private val ExpandableItems =
    setOf(
        NavBarItem.BROWSER,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.RELAY_GROUPS,
        NavBarItem.CONCORD,
    )

/** Soft guidance, not a hard cap: a Material bottom bar reads best at ~5 tabs. */
private const val RECOMMENDED_SLOTS = 5

// Reveal expandable sections by unrolling straight down from the top edge (the default AnimatedVisibility
// enter also expands horizontally from the bottom-end, which reads as a diagonal slide from the top-left).
private val SectionExpand = expandVertically(expandFrom = Alignment.Top) + fadeIn()
private val SectionCollapse = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()

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
    val savedItems by bottomBarItemsFlow.collectAsStateWithLifecycle()

    // All pin/unpin/reorder logic lives in the holder (unit-tested); the composable only renders and
    // forwards events. syncFrom re-seeds when the saved list changes elsewhere without clobbering a drag.
    val state = remember { BottomBarSettingsState(savedItems) { bottomBarItemsFlow.tryEmit(it) } }
    LaunchedEffect(savedItems) { state.syncFrom(savedItems) }

    val pinned = state.pinned
    val pinnedKeys = remember(pinned) { state.pinnedKeys() }

    val expandedCategories = remember { mutableStateMapOf<Int, Boolean>() }
    val expandedItems = remember { mutableStateMapOf<NavBarItem, Boolean>() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))

        // --- The editable bar: a real preview you drag to reorder and tap ✕ to remove from. ---
        EditableBarCard(state, pinned, accountViewModel)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { state.restoreDefault() }) {
                Text(stringRes(R.string.bottom_bar_settings_restore_default))
            }
        }

        Spacer(Modifier.height(4.dp))

        // --- Available catalogue, grouped into collapsible category cards. ---
        SectionHeader(title = stringRes(R.string.bottom_bar_settings_available))

        BottomBarCategories.forEach { category ->
            CategoryCard(
                category = category,
                pinnedKeys = pinnedKeys,
                expanded = expandedCategories[category.titleRes] ?: false,
                onToggleExpand = { expandedCategories[category.titleRes] = !(expandedCategories[category.titleRes] ?: false) },
                expandedItems = expandedItems,
                accountViewModel = accountViewModel,
                onTogglePin = state::togglePin,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------------------------------
// The editable preview bar — WYSIWYG: this IS the reorder & remove surface.
// ------------------------------------------------------------------------------------------------

@Composable
private fun EditableBarCard(
    state: BottomBarSettingsState,
    pinned: List<BottomBarEntry>,
    accountViewModel: AccountViewModel,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.bottom_bar_settings_pinned),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${pinned.size} / $RECOMMENDED_SLOTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pinned.size > RECOMMENDED_SLOTS) MaterialTheme.colorScheme.error else accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (pinned.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringRes(R.string.bottom_bar_settings_pinned_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    EditableBar(state, pinned, accountViewModel)
                }
            }

            Text(
                text = stringRes(R.string.bottom_bar_settings_reorder_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EditableBar(
    state: BottomBarSettingsState,
    pinned: List<BottomBarEntry>,
    accountViewModel: AccountViewModel,
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<Int, Float>() }

    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pinned.forEachIndexed { index, entry ->
            val dragging = draggedIndex == index
            PreviewTab(
                entry = entry,
                // The first tab is where the bar lands on open, so preview it as selected.
                selected = index == 0,
                dragging = dragging,
                dragOffsetX = if (dragging) dragOffsetX else 0f,
                accountViewModel = accountViewModel,
                onRemove = { state.togglePin(entry) },
                onMeasured = { widths[index] = it },
                onDragStart = {
                    draggedIndex = index
                    dragOffsetX = 0f
                },
                onDrag = { dx ->
                    dragOffsetX += dx
                    val current = draggedIndex
                    if (current < 0) return@PreviewTab

                    if (dragOffsetX < 0 && current > 0) {
                        val leftW = widths[current - 1] ?: 0f
                        if (-dragOffsetX > leftW / 2f) {
                            state.moveTransient(current, current - 1)
                            dragOffsetX += leftW
                            draggedIndex = current - 1
                        }
                    }
                    if (dragOffsetX > 0 && current < pinned.lastIndex) {
                        val rightW = widths[current + 1] ?: 0f
                        if (dragOffsetX > rightW / 2f) {
                            state.moveTransient(current, current + 1)
                            dragOffsetX -= rightW
                            draggedIndex = current + 1
                        }
                    }
                },
                onDragEnd = {
                    draggedIndex = -1
                    dragOffsetX = 0f
                    state.commit()
                },
                onDragCancel = {
                    draggedIndex = -1
                    dragOffsetX = 0f
                },
            )
        }
    }
}

@Composable
private fun RowScope.PreviewTab(
    entry: BottomBarEntry,
    selected: Boolean,
    dragging: Boolean,
    dragOffsetX: Float,
    accountViewModel: AccountViewModel,
    onRemove: () -> Unit,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val visual = rememberPinnedVisual(entry, accountViewModel)
    val lift by animateFloatAsState(if (dragging) 1.12f else 1f, label = "tabLift")

    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .zIndex(if (dragging) 1f else 0f)
                .onGloballyPositioned { onMeasured(it.size.width.toFloat()) }
                .graphicsLayer {
                    translationX = dragOffsetX
                    scaleX = lift
                    scaleY = lift
                }.pointerInput(entry.stableKey) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.x)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            PreviewTabIcon(visual, selected, accountViewModel)
            if (selected) {
                Text(
                    text = visual.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Editing affordance: a small ✕ removes this tab. This is why the preview isn't just a mirror.
        RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun BoxScope.RemoveBadge(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Close,
            contentDescription = stringRes(R.string.bottom_bar_settings_remove),
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The icon block of a preview tab: catalog glyph, the favorite's real favicon, or the group's avatar. */
@Composable
private fun PreviewTabIcon(
    visual: PinnedVisual,
    selected: Boolean,
    accountViewModel: AccountViewModel,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .size(width = 42.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        when (visual) {
            is PinnedVisual.Glyph -> Icon(symbol = visual.icon, contentDescription = visual.label, modifier = Modifier.size(21.dp), tint = tint)
            is PinnedVisual.Favorite -> FavoriteAppIcon(app = visual.app, tint = tint, modifier = Modifier.size(21.dp), iconModel = rememberFavoriteIconModel(visual.app))
            is PinnedVisual.Avatar -> GroupEntryAvatar(visual.display, 22.dp, accountViewModel)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Available catalogue — category cards
// ------------------------------------------------------------------------------------------------

@Composable
private fun CategoryCard(
    category: NavBarCategory,
    pinnedKeys: Set<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    expandedItems: SnapshotStateMap<NavBarItem, Boolean>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Size20dp, vertical = 5.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        symbol = categoryIcon(category.titleRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringRes(category.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded, enter = SectionExpand, exit = SectionCollapse) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    category.items.forEach { item ->
                        val def = NavBarCatalog[item] ?: return@forEach
                        val entry = BottomBarEntry.BuiltIn(item)
                        if (item in ExpandableItems) {
                            ExpandableAvailableRow(
                                icon = def.icon,
                                label = stringRes(def.labelRes),
                                pinned = entry.stableKey in pinnedKeys,
                                expanded = expandedItems[item] ?: false,
                                onTogglePin = { onTogglePin(entry) },
                                onToggleExpand = { expandedItems[item] = !(expandedItems[item] ?: false) },
                            ) {
                                PickerChildren(item, pinnedKeys, accountViewModel, onTogglePin)
                            }
                        } else {
                            AvailableRow(
                                leading = { LeadingGlyph(def.icon) },
                                label = stringRes(def.labelRes),
                                pinned = entry.stableKey in pinnedKeys,
                                onToggle = { onTogglePin(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Child rows (favorites / joined groups) revealed when an expandable picker row opens. */
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
                    AvailableRow(
                        leading = { FavoriteLeading(fav) },
                        label = fav.label,
                        pinned = entry.stableKey in pinnedKeys,
                        onToggle = { onTogglePin(entry) },
                        indent = true,
                    )
                }
            }
        }

        NavBarItem.PUBLIC_CHATS -> {
            val channels by accountViewModel.account.publicChatList.flow
                .collectAsStateWithLifecycle()
            val entries = remember(channels) { channels.map { BottomBarEntry.PublicChat(it.eventId) } }
            GroupChildList(entries, pinnedKeys, accountViewModel, onTogglePin)
        }

        NavBarItem.RELAY_GROUPS -> {
            val groups by accountViewModel.account.relayGroupList.liveRelayGroupList
                .collectAsStateWithLifecycle()
            val entries =
                remember(groups) {
                    groups
                        .sortedBy { (it.name ?: it.groupId).lowercase() }
                        .map { BottomBarEntry.RelayGroup(it.groupId, it.relayUrl) }
                }
            GroupChildList(entries, pinnedKeys, accountViewModel, onTogglePin)
        }

        NavBarItem.CONCORD -> {
            val communities by accountViewModel.account.concordChannelList.liveCommunities
                .collectAsStateWithLifecycle()
            val entries = remember(communities) { communities.map { BottomBarEntry.Concord(it.id) } }
            GroupChildList(entries, pinnedKeys, accountViewModel, onTogglePin)
        }

        else -> {}
    }
}

@Composable
private fun GroupChildList(
    entries: List<BottomBarEntry>,
    pinnedKeys: Set<String>,
    accountViewModel: AccountViewModel,
    onTogglePin: (BottomBarEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyChildHint(R.string.bottom_bar_settings_no_groups)
        return
    }
    entries.forEach { entry ->
        // Read-only: the picker resolves names/avatars from cache, it must not open a REQ per row.
        val display = rememberGroupEntryDisplay(entry, accountViewModel, subscribe = false) ?: return@forEach
        AvailableRow(
            leading = { GroupEntryAvatar(display, 34.dp, accountViewModel) },
            label = display.label,
            pinned = entry.stableKey in pinnedKeys,
            onToggle = { onTogglePin(entry) },
            indent = true,
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Rows & shared bits
// ------------------------------------------------------------------------------------------------

@Composable
private fun AvailableRow(
    leading: @Composable () -> Unit,
    label: String,
    pinned: Boolean,
    onToggle: () -> Unit,
    indent: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = if (indent) 24.dp else 13.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AddPill(added = pinned, onClick = onToggle)
    }
}

@Composable
private fun ExpandableAvailableRow(
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
                .padding(start = 13.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LeadingGlyph(icon)
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
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AddPill(added = pinned, onClick = onTogglePin)
    }
    AnimatedVisibility(visible = expanded, enter = SectionExpand, exit = SectionCollapse) {
        Column { children() }
    }
}

/** Outlined "Add" that fills to "Added" once pinned — states the action and its result. */
@Composable
private fun AddPill(
    added: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    if (added) {
        Surface(shape = CircleShape, color = accent) {
            Row(
                modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Text(stringRes(R.string.bottom_bar_settings_added), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, accent),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Icon(symbol = MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = accent)
            Spacer(Modifier.width(4.dp))
            Text(stringRes(R.string.bottom_bar_settings_add), style = MaterialTheme.typography.labelLarge, color = accent)
        }
    }
}

/** A category/destination glyph in a soft accent-tinted circle. */
@Composable
private fun LeadingGlyph(icon: MaterialSymbol) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** A favorite web-app / nsite / napplet's real favicon in a tinted circle (glyph fallback). */
@Composable
private fun FavoriteLeading(app: FavoriteApp) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        FavoriteAppIcon(
            app = app,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
            iconModel = rememberFavoriteIconModel(app),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Size20dp, end = Size20dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyChildHint(textRes: Int) {
    Text(
        text = stringRes(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 13.dp, top = 6.dp, bottom = 6.dp),
    )
}

private fun categoryIcon(titleRes: Int): MaterialSymbol =
    when (titleRes) {
        R.string.bottom_bar_category_main -> MaterialSymbols.Home
        R.string.bottom_bar_category_chats -> MaterialSymbols.Group
        R.string.bottom_bar_category_you -> MaterialSymbols.AccountCircle
        R.string.bottom_bar_category_feeds -> MaterialSymbols.Subscriptions
        R.string.bottom_bar_category_apps -> MaterialSymbols.Apps
        else -> MaterialSymbols.Settings
    }

// ------------------------------------------------------------------------------------------------
// Leading/label resolution for a pinned entry (built-in glyph, favorite icon, or group avatar).
// Computed once so a group's channel is subscribed at most once per row.
// ------------------------------------------------------------------------------------------------

private sealed interface PinnedVisual {
    val label: String

    data class Glyph(
        val icon: MaterialSymbol,
        override val label: String,
    ) : PinnedVisual

    data class Favorite(
        val app: FavoriteApp,
    ) : PinnedVisual {
        override val label: String get() = app.label
    }

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
            if (app != null) PinnedVisual.Favorite(app) else PinnedVisual.Glyph(MaterialSymbols.Public, "")
        }
        is BottomBarEntry.PublicChat,
        is BottomBarEntry.RelayGroup,
        is BottomBarEntry.Concord,
        -> {
            // Read-only: the settings list resolves from cache; the live bar owns the subscription.
            val display = rememberGroupEntryDisplay(entry, accountViewModel, subscribe = false)
            if (display != null) PinnedVisual.Avatar(display) else PinnedVisual.Glyph(MaterialSymbols.Group, "")
        }
    }
