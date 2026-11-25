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
package com.vitorpamplona.amethyst.commons.ui.navigation.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.DockView
import com.vitorpamplona.amethyst.commons.model.dock.DropTarget
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_back_to_dock
import com.vitorpamplona.amethyst.commons.resources.dock_fold
import com.vitorpamplona.amethyst.commons.resources.dock_maximize
import com.vitorpamplona.amethyst.commons.resources.dock_move_down
import com.vitorpamplona.amethyst.commons.resources.dock_move_left
import com.vitorpamplona.amethyst.commons.resources.dock_move_new_column
import com.vitorpamplona.amethyst.commons.resources.dock_move_right
import com.vitorpamplona.amethyst.commons.resources.dock_move_tab_above
import com.vitorpamplona.amethyst.commons.resources.dock_move_up
import com.vitorpamplona.amethyst.commons.resources.dock_open_in_window
import com.vitorpamplona.amethyst.commons.resources.dock_panel_options
import com.vitorpamplona.amethyst.commons.resources.dock_reset_layout
import com.vitorpamplona.amethyst.commons.resources.dock_restore_size
import com.vitorpamplona.amethyst.commons.resources.dock_show_over_screen
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/** The header of a slot, an overlay or a panel window: as tall as the main screen's top bar, so they line up. */
internal val PanelHeaderHeight = 48.dp

/** How far a press travels before it drags its panel instead of clicking: about a finger's slop, as tabs use. */
private val DockDragSlop = 8.dp

/**
 * A panel's header: its icon and name, or its slot's tabs when several share it; the panel's own
 * buttons; the dock's menu; and the button that puts it away (fold it, close the overlay, restore
 * it, or put it back in the dock from its window). Dragging the header drags the panel; in a panel
 * window, [dragModifier] is the platform's (it moves the window and docks it when dropped on the
 * main one).
 */
@Composable
internal fun PanelHeader(
    controller: DockController,
    tabs: List<PanelId>,
    active: PanelId,
    scope: DockPanelScope,
    view: DockView,
    modifier: Modifier = Modifier,
    dragModifier: Modifier? = null,
) {
    val spec = controller.spec(active) ?: return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(PanelHeaderHeight)
            .then(dragModifier ?: Modifier.dockDragSource(controller, active))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tabs.size <= 1) {
            Icon(symbol = spec.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onBackground)
            Spacer(Modifier.width(10.dp))
            val onTitle = spec.onTitleClick
            Box(Modifier.weight(1f)) {
                Text(
                    text = stringRes(spec.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onTitle != null) Modifier.clickable { onTitle(scope.nav) } else Modifier,
                )
            }
        } else {
            // The slot's tabs take the room the buttons leave, and scroll past it.
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { id ->
                    val tab = controller.spec(id) ?: return@forEach
                    PanelTab(controller, tab, selected = id == active)
                }
            }
        }
        spec.actions(this, scope)
        PanelMenuButton(controller, active, scope.placement, view)
        PutAwayButton(controller, active, scope.placement)
    }
}

@Composable
private fun PanelTab(
    controller: DockController,
    spec: DockPanelSpec,
    selected: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val title = stringRes(spec.title)
    Row(
        Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .dockDragSource(controller, spec.id)
            .clickable { controller.show(spec.id) }
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 160.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = spec.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PutAwayButton(
    controller: DockController,
    id: PanelId,
    placement: PanelPlacement,
) {
    val (symbol, label, action) =
        when (placement) {
            PanelPlacement.DOCKED -> Triple(MaterialSymbols.AutoMirrored.KeyboardArrowRight, Res.string.dock_fold) { controller.fold(id) }
            PanelPlacement.OVERLAY -> Triple(MaterialSymbols.Close, Res.string.dock_fold) { controller.dismissOverlay() }
            PanelPlacement.MAXIMIZED -> Triple(MaterialSymbols.CloseFullscreen, Res.string.dock_restore_size) { controller.toggleMaximized(id) }
            PanelPlacement.WINDOW -> Triple(MaterialSymbols.DockToRight, Res.string.dock_back_to_dock) { controller.dockBack(id) }
        }
    val text = stringRes(label)
    HoverTooltip(text) {
        IconButton(onClick = action, modifier = Modifier.size(32.dp)) {
            Icon(symbol = symbol, contentDescription = text, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PanelMenuButton(
    controller: DockController,
    id: PanelId,
    placement: PanelPlacement,
    view: DockView,
) {
    var open by remember { mutableStateOf(false) }
    val spec = controller.spec(id) ?: return
    val label = stringRes(Res.string.dock_panel_options, stringRes(spec.title))
    Box {
        HoverTooltip(label) {
            IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
                Icon(symbol = MaterialSymbols.MoreVert, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PanelMenu(controller, id, placement, view, expanded = open, onDismiss = { open = false })
    }
}

/** One action of a panel's menu. */
internal class PanelAction(
    val label: StringResource,
    val symbol: MaterialSymbol,
    val checked: Boolean? = null,
    val divides: Boolean = false,
    val run: () -> Unit,
)

/**
 * What a panel's menu offers where it is [placement]: moves to its neighbours in the dock (each only
 * where the dock's limits allow it), how it is shown, and resetting the whole dock.
 */
internal fun panelActions(
    controller: DockController,
    id: PanelId,
    placement: PanelPlacement,
    view: DockView,
): List<PanelAction> {
    val layout = controller.layout()
    val actions = mutableListOf<PanelAction>()
    val at = layout.locate(id)
    if (placement == PanelPlacement.DOCKED && at != null) {
        val vc = view.columns.indexOfFirst { it.index == at.column }
        val column = view.columns.getOrNull(vc)
        val vs = column?.slots?.indexOfFirst { it.index == at.slot } ?: -1

        fun offer(
            label: StringResource,
            symbol: MaterialSymbol,
            target: DropTarget,
        ) {
            if (layout.canMove(id, target)) actions += PanelAction(label, symbol) { controller.move(id, target) }
        }
        if (column != null && vs >= 0) {
            if (vs > 0) {
                offer(Res.string.dock_move_up, MaterialSymbols.ArrowUpward, DropTarget.Split(at.column, column.slots[vs - 1].index, below = false))
                offer(Res.string.dock_move_tab_above, MaterialSymbols.Tab, DropTarget.Tab(at.column, column.slots[vs - 1].index))
            }
            if (vs < column.slots.lastIndex) {
                offer(Res.string.dock_move_down, MaterialSymbols.ArrowDownward, DropTarget.Split(at.column, column.slots[vs + 1].index, below = true))
            }
            view.columns.getOrNull(vc - 1)?.let { left ->
                offer(Res.string.dock_move_left, MaterialSymbols.SplitscreenBottom, DropTarget.Split(left.index, left.slots.last().index, below = true))
            }
            view.columns.getOrNull(vc + 1)?.let { right ->
                offer(Res.string.dock_move_right, MaterialSymbols.SplitscreenBottom, DropTarget.Split(right.index, right.slots.last().index, below = true))
            }
            val alone =
                column.slots.size == 1 && column.slots[0].tabs.size == 1 && layout.columns[at.column].slots.size == 1 && layout.columns[at.column]
                    .slots[0]
                    .panels.size == 1
            if (!alone) offer(Res.string.dock_move_new_column, MaterialSymbols.ViewColumn, DropTarget.NewColumn(at.column + 1))
        }
    }
    val first = actions.size
    if (placement != PanelPlacement.WINDOW) {
        actions += PanelAction(Res.string.dock_show_over_screen, MaterialSymbols.RightPanelOpen, checked = layout.isOverlay(id), divides = first > 0) { controller.setOverlayMode(id, !layout.isOverlay(id)) }
        if (controller.supportsWindows) actions += PanelAction(Res.string.dock_open_in_window, MaterialSymbols.SelectWindow) { controller.popOut(id) }
        if (placement == PanelPlacement.MAXIMIZED) {
            actions += PanelAction(Res.string.dock_restore_size, MaterialSymbols.CloseFullscreen) { controller.toggleMaximized(id) }
        } else {
            actions += PanelAction(Res.string.dock_maximize, MaterialSymbols.OpenInFull) { controller.toggleMaximized(id) }
        }
        if (controller.isShowing(id, layout)) actions += PanelAction(Res.string.dock_fold, MaterialSymbols.AutoMirrored.KeyboardArrowRight) { controller.fold(id) }
    } else {
        actions += PanelAction(Res.string.dock_back_to_dock, MaterialSymbols.DockToRight, divides = first > 0) { controller.dockBack(id) }
    }
    actions += PanelAction(Res.string.dock_reset_layout, MaterialSymbols.RestartAlt, divides = true) { controller.reset() }
    return actions
}

/** A panel's menu, from its header's button or a right click on its strip icon. */
@Composable
internal fun PanelMenu(
    controller: DockController,
    id: PanelId,
    placement: PanelPlacement,
    view: DockView,
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: IntOffset? = null,
) {
    if (!expanded) return
    val actions = remember(id, placement, view, controller.layoutFlow.value, controller.overlay, controller.maximized) { panelActions(controller, id, placement, view) }
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = offset?.let { DpOffset(it.x.dp, it.y.dp) } ?: DpOffset.Zero,
    ) {
        actions.forEach { action ->
            if (action.divides) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringRes(action.label)) },
                leadingIcon = { Icon(symbol = action.symbol, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon =
                    action.checked?.let { checked ->
                        { if (checked) Icon(symbol = MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    },
                onClick = {
                    onDismiss()
                    action.run()
                },
            )
        }
    }
}

/**
 * Drags panel [id] by this element: past [DockDragSlop] the press becomes a drag (the click it
 * started as is cancelled), the dock lights up where it would land, and releasing drops it there, or
 * in a window of its own when released outside the main window. Only the primary button drags.
 */
internal fun Modifier.dockDragSource(
    controller: DockController,
    id: PanelId,
): Modifier {
    val origin = OriginHolder()
    return this
        .onGloballyPositioned { origin.value = it.positionInRoot() }
        .pointerInput(controller, id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val slop = DockDragSlop.toPx()
                var travel = Offset.Zero
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        if (dragging) controller.drag.cancel()
                        break
                    }
                    if (!change.pressed) {
                        if (dragging) {
                            change.consume()
                            val at = origin.value + change.position
                            controller.drag.end { controller.screenPointOf(at) }
                        }
                        break
                    }
                    travel += change.positionChange()
                    if (!dragging && travel.getDistance() >= slop) {
                        dragging = true
                        controller.drag.start(id, origin.value + change.position)
                    }
                    if (dragging) {
                        change.consume()
                        controller.drag.moveTo(origin.value + change.position)
                    }
                }
            }
        }
}

private class OriginHolder {
    var value: Offset = Offset.Zero
}
