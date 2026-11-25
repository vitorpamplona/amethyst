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

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vitorpamplona.amethyst.commons.model.dock.DockView
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.ui.layouts.PaneSplitter
import com.vitorpamplona.amethyst.commons.ui.layouts.verticalResizeCursor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The dock's docked columns, left to right, each [widths] dp wide (a column fitted to 0 is left out:
 * the window is too narrow for it right now). Each column leads with the splitter that resizes it,
 * and stacks its slots with dividers that share its height between them.
 */
@Composable
internal fun DockColumns(
    controller: DockController,
    view: DockView,
    widths: List<Float>,
    accountViewModel: AccountViewModel,
    panelNav: INav,
) {
    Row(Modifier.fillMaxHeight()) {
        view.columns.forEachIndexed { i, column ->
            val width = widths.getOrNull(i) ?: 0f
            if (width <= 0f) return@forEachIndexed
            val currentWidth by rememberUpdatedState(width)
            // Dragging the line left widens the column.
            PaneSplitter(onDrag = { delta -> controller.update { it.resizeColumn(column.index, currentWidth - delta) } })
            DockColumnPane(controller, column, view, Modifier.width(width.dp), accountViewModel, panelNav)
        }
    }
}

@Composable
private fun DockColumnPane(
    controller: DockController,
    column: DockView.ColumnView,
    view: DockView,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    panelNav: INav,
) {
    val drag = controller.drag
    DisposableEffect(column.index) { onDispose { drag.columns.remove(column.index) } }
    var heightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val current by rememberUpdatedState(column)
    Column(
        modifier
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height }
            .onGloballyPositioned { drag.columns[column.index] = it.boundsInRoot() },
    ) {
        column.slots.forEachIndexed { i, slot ->
            if (i > 0) {
                SlotSplitter { deltaDp ->
                    val slots = current.slots
                    val upper = slots.getOrNull(i - 1) ?: return@SlotSplitter
                    val lower = slots.getOrNull(i) ?: return@SlotSplitter
                    val heightDp = with(density) { heightPx.toDp().value }
                    if (heightDp <= 0f) return@SlotSplitter
                    val total = slots.sumOf { it.weight.toDouble() }.toFloat()
                    val dw = deltaDp / heightDp * total
                    controller.update { it.resizeSlots(current.index, upper.index, lower.index, upper.weight + dw, lower.weight - dw) }
                }
            }
            DockSlotPane(controller, column.index, slot, view, Modifier.weight(slot.weight).fillMaxWidth(), accountViewModel, panelNav)
        }
    }
}

@Composable
private fun DockSlotPane(
    controller: DockController,
    column: Int,
    slot: DockView.SlotView,
    view: DockView,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    panelNav: INav,
) {
    val drag = controller.drag
    val key = column to slot.index
    DisposableEffect(key) { onDispose { drag.slots.remove(key) } }
    val scope = remember(slot.active, accountViewModel, panelNav) { DockPanelScope(slot.active, PanelPlacement.DOCKED, accountViewModel, panelNav, controller) }
    Column(
        modifier
            .onGloballyPositioned { drag.slots[key] = it.boundsInRoot() }
            // Shift+Esc folds the panel the focus is in, as it hides a tool window in an IDE.
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && event.isShiftPressed) {
                    controller.fold(slot.active)
                    true
                } else {
                    false
                }
            },
    ) {
        PanelHeader(controller, slot.tabs, slot.active, scope, view)
        HorizontalDivider(thickness = DividerThickness)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            controller.PanelContent(scope)
        }
    }
}

/** The line between two stacked slots, dragged up and down to share the column's height. [onDrag] gets dp, positive down. */
@Composable
private fun SlotSplitter(onDrag: (Float) -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val density = LocalDensity.current
    // Takes a divider's height in the layout but can be grabbed a few dp above and below it.
    Layout(
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalResizeCursor()
                    .hoverable(interaction)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, amount ->
                            change.consume()
                            currentOnDrag(with(density) { amount.toDp().value })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        },
        modifier = Modifier.fillMaxWidth().zIndex(1f),
    ) { measurables, constraints ->
        val grab = 9.dp.roundToPx()
        val line = DividerThickness.roundToPx().coerceAtLeast(1)
        val placeable = measurables.first().measure(Constraints.fixed(constraints.maxWidth, grab))
        layout(constraints.maxWidth, line) { placeable.place(0, (line - grab) / 2) }
    }
}

/** Whether [id] has any part of the dock on screen right now, for the strip's selection. */
internal fun DockController.showingIn(
    id: PanelId,
    view: DockView,
): Boolean = overlay == id || maximized == id || layout().isInWindow(id) || view.columns.any { c -> c.slots.any { it.active == id } }
