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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import com.vitorpamplona.amethyst.commons.model.dock.DockLayout
import com.vitorpamplona.amethyst.commons.model.dock.DropTarget
import com.vitorpamplona.amethyst.commons.model.dock.PanelId

/** Where a dragged panel would land, and the part of the window to light up for it. */
@Immutable
data class DropHit(
    val target: DropTarget,
    val highlight: Rect?,
)

/**
 * A panel being dragged by its header or strip icon. The dock's slots, columns, strip and the main
 * screen register their bounds (in the main window's coordinates) while they are drawn; the drag
 * asks which of them is under the pointer. A panel dragged from its own window reports the pointer
 * here too, translated into the main window ([moveFromOutside]), so the same zones light up.
 */
@Stable
class DockDragState internal constructor(
    private val controller: DockController,
) {
    var panel by mutableStateOf<PanelId?>(null)
        private set

    var pointer by mutableStateOf(Offset.Zero)
        private set

    var hit by mutableStateOf<DropHit?>(null)
        private set

    /** Whether the drag started in a panel's own window rather than in the main one. */
    var fromWindow by mutableStateOf(false)
        private set

    /** Where the dock's parts are drawn, registered by each as it lays out. */
    internal val zones = DropZones()

    internal val slots get() = zones.slots
    internal val columns get() = zones.columns
    internal var strip: Rect?
        get() = zones.strip
        set(value) {
            zones.strip = value
        }
    internal var center: Rect?
        get() = zones.center
        set(value) {
            zones.center = value
        }
    internal var window: Size
        get() = zones.window
        set(value) {
            zones.window = value
        }
    internal var edgePx: Float
        get() = zones.edgePx
        set(value) {
            zones.edgePx = value
        }

    val active: Boolean get() = panel != null

    fun start(
        id: PanelId,
        at: Offset,
    ) {
        panel = id
        fromWindow = false
        moveTo(at)
    }

    fun moveTo(at: Offset) {
        val id = panel ?: return
        pointer = at
        hit = dockHitTest(id, at, controller.layout(), zones, canWindow = controller.supportsWindows && !fromWindow)
    }

    /**
     * A drag from panel [id]'s own window: [at] is the pointer in the main window's coordinates, or
     * null while it is not over the main window.
     */
    fun moveFromOutside(
        id: PanelId,
        at: Offset?,
    ) {
        if (panel != id) {
            panel = id
            fromWindow = true
        }
        if (at == null) {
            hit = null
            pointer = Offset(-1f, -1f)
        } else {
            moveTo(at)
        }
    }

    /** Drops the panel where it is; returns whether it landed in the dock or a window. */
    fun end(screenOrigin: () -> IntOffset? = { null }): Boolean {
        val id = panel ?: return false
        val landed = hit
        panel = null
        hit = null
        val wasFromWindow = fromWindow
        fromWindow = false
        if (landed == null) return false
        when (landed.target) {
            DropTarget.Window -> controller.popOut(id, screenOrigin())
            DropTarget.Strip -> controller.fold(id)
            else -> {
                if (wasFromWindow) controller.update { it.dockBack(id) }
                controller.move(id, landed.target)
            }
        }
        return true
    }

    fun cancel() {
        panel = null
        hit = null
        fromWindow = false
    }
}

/** The bounds of the dock's parts in the main window, in pixels, as the drag tests them. */
class DropZones {
    val slots = mutableMapOf<Pair<Int, Int>, Rect>()
    val columns = mutableMapOf<Int, Rect>()
    var strip: Rect? = null
    var center: Rect? = null
    var window: Size = Size.Zero

    /** How close to a column's edge the pointer must be to open a new column there. */
    var edgePx: Float = 48f
}

/**
 * What is under [at], in the order a user reads the zones: out of the window (its own window),
 * the strip (fold), a column's edge (a new column), a slot's top or bottom quarter (above or
 * below it), the rest of a slot (its tabs), and the main screen's right edge (a first column).
 * A target the layout would refuse (past its limits) is no target.
 */
internal fun dockHitTest(
    id: PanelId,
    at: Offset,
    layout: DockLayout,
    zones: DropZones,
    canWindow: Boolean,
): DropHit? {
    val window = zones.window
    val strip = zones.strip
    val center = zones.center
    val edgePx = zones.edgePx
    val columns = zones.columns
    val slots = zones.slots
    val inside = at.x >= 0 && at.y >= 0 && at.x <= window.width && at.y <= window.height
    if (!inside) return if (canWindow) DropHit(DropTarget.Window, null) else null

    strip?.let { if (it.contains(at)) return DropHit(DropTarget.Strip, it) }

    val ordered = columns.entries.sortedBy { it.value.left }
    ordered.forEachIndexed { i, (column, rect) ->
        if (!rect.contains(at)) return@forEachIndexed
        if (at.x < rect.left + edgePx) {
            return valid(id, layout, DropTarget.NewColumn(column), Rect(rect.left, rect.top, rect.left + edgePx / 2, rect.bottom))
        }
        if (i == ordered.lastIndex && at.x > rect.right - edgePx) {
            return valid(id, layout, DropTarget.NewColumn(column + 1), Rect(rect.right - edgePx / 2, rect.top, rect.right, rect.bottom))
        }
        slots.entries.firstOrNull { (key, slot) -> key.first == column && slot.contains(at) }?.let { (key, slot) ->
            val quarter = slot.height / 4
            return when {
                at.y < slot.top + quarter -> valid(id, layout, DropTarget.Split(key.first, key.second, below = false), Rect(slot.left, slot.top, slot.right, slot.top + slot.height / 2))
                at.y > slot.bottom - quarter -> valid(id, layout, DropTarget.Split(key.first, key.second, below = true), Rect(slot.left, slot.top + slot.height / 2, slot.right, slot.bottom))
                else -> valid(id, layout, DropTarget.Tab(key.first, key.second), slot)
            }
        }
    }

    center?.let { rect ->
        if (rect.contains(at) && at.x > rect.right - edgePx * 2) {
            // Left of the first shown column, or a column of its own when none shows.
            val first = ordered.firstOrNull()?.key ?: 0
            return valid(id, layout, DropTarget.NewColumn(first), Rect(rect.right - edgePx, rect.top, rect.right, rect.bottom))
        }
    }
    return null
}

private fun valid(
    id: PanelId,
    layout: DockLayout,
    target: DropTarget,
    highlight: Rect,
): DropHit? = if (layout.canMove(id, target)) DropHit(target, highlight) else null
