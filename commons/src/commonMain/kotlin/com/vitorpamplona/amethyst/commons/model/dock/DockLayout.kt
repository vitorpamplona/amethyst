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
package com.vitorpamplona.amethyst.commons.model.dock

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

/**
 * Names a panel of the dock: the notification feed, the assistant, the devices. A string rather than
 * an enum, so a layout saved by a build with a panel this one lacks still decodes: that panel keeps
 * its place and is left out of what is drawn.
 */
@Immutable
@Serializable
@JvmInline
value class PanelId(
    val kind: String,
) {
    companion object {
        val NOTIFICATIONS = PanelId("notifications")
        val ASSISTANT = PanelId("assistant")
        val DEVICES = PanelId("devices")
    }
}

/** Whether a panel is docked and showing, folded to its icon in the strip, or in a window of its own. */
@Serializable
enum class PanelState { OPEN, FOLDED, WINDOW }

/**
 * Panels stacked top to bottom share a column's height by [weight]. Several panels in one slot are
 * its tabs; [active] is the one shown.
 */
@Immutable
@Serializable
data class DockSlot(
    val panels: List<PanelId>,
    val active: PanelId? = null,
    val weight: Float = 1f,
)

/** One column of the dock, [width] dp wide (null: its first panel's default). */
@Immutable
@Serializable
data class DockColumn(
    val slots: List<DockSlot>,
    val width: Float? = null,
)

/** Where a dragged panel goes. Indexes are into [DockLayout.columns] and their slots. */
sealed interface DropTarget {
    /** Shares the slot, as one of its tabs. */
    data class Tab(
        val column: Int,
        val slot: Int,
    ) : DropTarget

    /** A slot of its own above or [below] the slot. */
    data class Split(
        val column: Int,
        val slot: Int,
        val below: Boolean,
    ) : DropTarget

    /** A column of its own, inserted at [index]. */
    data class NewColumn(
        val index: Int,
    ) : DropTarget

    /** Folds it to the strip, keeping its place. */
    data object Strip : DropTarget

    /** A window of its own. */
    data object Window : DropTarget
}

/** A panel's place: column, slot and tab. */
data class PanelLocation(
    val column: Int,
    val slot: Int,
    val tab: Int,
)

/** What the shell draws: the columns that hold an open panel, each with only its open slots. */
@Immutable
data class DockView(
    val columns: List<ColumnView>,
) {
    @Immutable
    data class ColumnView(
        val index: Int,
        val width: Float?,
        val slots: List<SlotView>,
    )

    @Immutable
    data class SlotView(
        val index: Int,
        val tabs: List<PanelId>,
        val active: PanelId,
        val weight: Float,
    )

    val isEmpty: Boolean get() = columns.isEmpty()

    companion object {
        val Empty = DockView(emptyList())
    }
}

/**
 * The dock to the right of the main screen, as Android Studio's tool windows are: columns of slots,
 * each slot holding one or more panels as tabs. Every panel the app knows has a place here even while
 * folded or in a window, so showing it again puts it back where it was. A plain value: every change
 * returns a new layout, which [DockLayoutPreferences] keeps on the device.
 *
 * Panels in [overlayMode] are never docked: shown, they slide over the main screen.
 */
@Immutable
@Serializable
data class DockLayout(
    val columns: List<DockColumn> = emptyList(),
    val states: Map<PanelId, PanelState> = emptyMap(),
    val overlayMode: Set<PanelId> = emptySet(),
) {
    fun contains(id: PanelId): Boolean = locate(id) != null

    fun stateOf(id: PanelId): PanelState = states[id] ?: PanelState.FOLDED

    fun isOpen(id: PanelId): Boolean = stateOf(id) == PanelState.OPEN

    fun isInWindow(id: PanelId): Boolean = stateOf(id) == PanelState.WINDOW

    fun isOverlay(id: PanelId): Boolean = id in overlayMode

    fun locate(id: PanelId): PanelLocation? {
        columns.forEachIndexed { c, column ->
            column.slots.forEachIndexed { s, slot ->
                val t = slot.panels.indexOf(id)
                if (t >= 0) return PanelLocation(c, s, t)
            }
        }
        return null
    }

    /** Every placed panel, left to right and top to bottom: the strip's order. */
    fun ordered(): List<PanelId> = columns.flatMap { column -> column.slots.flatMap { it.panels } }

    /**
     * Gives each of [ids] a place if it has none: [open] ones start open, the rest folded. A panel in
     * [ownColumn] starts in a column of its own, beside the others (a phone-shaped screen wants the
     * height); the others stack under the first column's panels, then become tabs of its last slot.
     */
    fun ensure(
        ids: List<PanelId>,
        open: Set<PanelId> = DEFAULT_OPEN,
        ownColumn: Set<PanelId> = DEFAULT_OWN_COLUMN,
    ): DockLayout {
        var layout = this
        ids.forEach { id ->
            if (layout.contains(id)) return@forEach
            val cols = layout.columns.toMutableList()
            val slot = DockSlot(listOf(id), id)
            when {
                cols.isEmpty() -> cols.add(DockColumn(listOf(slot)))
                id in ownColumn && cols.size < MAX_COLUMNS -> cols.add(DockColumn(listOf(slot)))
                cols[0].slots.size < MAX_SLOTS -> cols[0] = cols[0].copy(slots = cols[0].slots + slot)
                else -> {
                    val last = cols[0].slots.last()
                    cols[0] = cols[0].copy(slots = cols[0].slots.dropLast(1) + last.copy(panels = last.panels + id))
                }
            }
            val state = if (id in open) PanelState.OPEN else PanelState.FOLDED
            layout = layout.copy(columns = cols, states = layout.states + (id to (layout.states[id] ?: state)))
        }
        return layout
    }

    /** Shows [id] where it last was, as its slot's tab on top. */
    fun open(id: PanelId): DockLayout {
        val at = locate(id) ?: return this
        return withState(id, PanelState.OPEN).mapSlot(at.column, at.slot) { it.copy(active = id) }
    }

    fun fold(id: PanelId): DockLayout = withState(id, PanelState.FOLDED)

    fun toggle(id: PanelId): DockLayout = if (isOpen(id)) fold(id) else open(id)

    fun popOut(id: PanelId): DockLayout = withState(id, PanelState.WINDOW)

    /** Out of its window, back where it was. */
    fun dockBack(id: PanelId): DockLayout = open(id).copy(overlayMode = overlayMode - id)

    /** Folds every open panel; [openAll] brings back the ones a caller kept. */
    fun foldAll(): DockLayout = copy(states = states.mapValues { (_, state) -> if (state == PanelState.OPEN) PanelState.FOLDED else state })

    /** Opens each of [ids] that is folded, as hiding every panel and bringing them back does. */
    fun openAll(ids: Collection<PanelId>): DockLayout = ids.fold(this) { layout, id -> if (layout.stateOf(id) == PanelState.FOLDED) layout.open(id) else layout }

    fun setOverlayMode(
        id: PanelId,
        on: Boolean,
    ): DockLayout = if (on) copy(overlayMode = overlayMode + id, states = states + (id to PanelState.FOLDED)) else copy(overlayMode = overlayMode - id)

    fun resizeColumn(
        index: Int,
        width: Float,
    ): DockLayout {
        if (index !in columns.indices) return this
        return copy(columns = columns.mapIndexed { i, c -> if (i == index) c.copy(width = width.coerceIn(COLUMN_WIDTH_RANGE)) else c })
    }

    /** Sets the weights of two neighbouring slots of [column], as a divider between them is dragged. */
    fun resizeSlots(
        column: Int,
        upper: Int,
        lower: Int,
        upperWeight: Float,
        lowerWeight: Float,
    ): DockLayout {
        val col = columns.getOrNull(column) ?: return this
        if (upper !in col.slots.indices || lower !in col.slots.indices) return this
        val slots =
            col.slots.mapIndexed { i, slot ->
                when (i) {
                    upper -> slot.copy(weight = upperWeight.coerceAtLeast(MIN_SLOT_WEIGHT))
                    lower -> slot.copy(weight = lowerWeight.coerceAtLeast(MIN_SLOT_WEIGHT))
                    else -> slot
                }
            }
        return copy(columns = columns.mapIndexed { i, c -> if (i == column) c.copy(slots = slots) else c })
    }

    /** Whether [id] can go to [target]: false when it would pass the dock's limits or the target is gone. */
    fun canMove(
        id: PanelId,
        target: DropTarget,
    ): Boolean = move(id, target) != null

    /**
     * [id] moved to [target], and shown there; null when the move would pass [MAX_COLUMNS] or
     * [MAX_SLOTS], or names a column or slot that is not there. Slots and columns left empty go.
     */
    fun move(
        id: PanelId,
        target: DropTarget,
    ): DockLayout? {
        val from = locate(id) ?: return null
        when (target) {
            DropTarget.Strip -> return fold(id)
            DropTarget.Window -> return popOut(id)
            is DropTarget.Tab -> if (target.column == from.column && target.slot == from.slot) return open(id)
            else -> {}
        }

        val cols = columns.map { MutableColumn(it) }.toMutableList()
        val original = cols[from.column].slots[from.slot].panels[from.tab]
        val moved = Ref(id)
        when (target) {
            is DropTarget.Tab -> {
                val slot = cols.getOrNull(target.column)?.slots?.getOrNull(target.slot) ?: return null
                slot.panels.add(moved)
                slot.active = id
            }
            is DropTarget.Split -> {
                val column = cols.getOrNull(target.column) ?: return null
                val near = column.slots.getOrNull(target.slot) ?: return null
                val at = if (target.below) target.slot + 1 else target.slot
                column.slots.add(at, MutableSlot(mutableListOf(moved), id, near.weight))
            }
            is DropTarget.NewColumn -> {
                if (target.index !in 0..cols.size) return null
                cols.add(target.index, MutableColumn(mutableListOf(MutableSlot(mutableListOf(moved), id, 1f)), null))
            }
            else -> return null
        }

        // The old place goes, by identity: the new one holds an equal id.
        cols.forEach { column -> column.slots.forEach { slot -> slot.panels.removeAll { it === original } } }
        cols.forEach { column -> column.slots.removeAll { it.panels.isEmpty() } }
        cols.removeAll { it.slots.isEmpty() }

        if (cols.size > MAX_COLUMNS || cols.any { it.slots.size > MAX_SLOTS }) return null

        return copy(columns = cols.map { it.freeze() }, states = states + (id to PanelState.OPEN))
    }

    /**
     * The open, docked panels among [available] (the ones this platform can draw), as the shell draws
     * them: empty slots and columns left out, each slot's shown tab chosen.
     */
    fun view(available: Set<PanelId>): DockView {
        fun shown(id: PanelId) = id in available && isOpen(id) && id !in overlayMode
        val visible =
            columns.mapIndexedNotNull { c, column ->
                val slots =
                    column.slots.mapIndexedNotNull { s, slot ->
                        val tabs = slot.panels.filter(::shown)
                        if (tabs.isEmpty()) {
                            null
                        } else {
                            val active = slot.active?.takeIf { it in tabs } ?: tabs.first()
                            DockView.SlotView(s, tabs, active, slot.weight)
                        }
                    }
                if (slots.isEmpty()) null else DockView.ColumnView(c, column.width, slots)
            }
        return if (visible.isEmpty()) DockView.Empty else DockView(visible)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    private fun withState(
        id: PanelId,
        state: PanelState,
    ): DockLayout = if (states[id] == state) this else copy(states = states + (id to state))

    private fun mapSlot(
        column: Int,
        slot: Int,
        transform: (DockSlot) -> DockSlot,
    ): DockLayout =
        copy(
            columns =
                columns.mapIndexed { c, col ->
                    if (c != column) col else col.copy(slots = col.slots.mapIndexed { s, it -> if (s == slot) transform(it) else it })
                },
        )

    private class Ref(
        val id: PanelId,
    )

    private class MutableSlot(
        val panels: MutableList<Ref>,
        var active: PanelId?,
        val weight: Float,
    ) {
        constructor(slot: DockSlot) : this(slot.panels.map { Ref(it) }.toMutableList(), slot.active, slot.weight)

        fun freeze(): DockSlot {
            val ids = panels.map { it.id }
            return DockSlot(ids, active?.takeIf { it in ids } ?: ids.first(), weight)
        }
    }

    private class MutableColumn(
        val slots: MutableList<MutableSlot>,
        val width: Float?,
    ) {
        constructor(column: DockColumn) : this(column.slots.map { MutableSlot(it) }.toMutableList(), column.width)

        fun freeze() = DockColumn(slots.map { it.freeze() }, width)
    }

    companion object {
        /** Columns the dock may have, and slots each column may stack. */
        const val MAX_COLUMNS = 3
        const val MAX_SLOTS = 3

        /** How far the user can drag a dock column, in dp. */
        val COLUMN_WIDTH_RANGE = 260f..720f

        /** No slot shrinks past this share of what its neighbour gives up. */
        const val MIN_SLOT_WEIGHT = 0.15f

        val DEFAULT_OPEN = setOf(PanelId.NOTIFICATIONS)
        val DEFAULT_OWN_COLUMN = setOf(PanelId.DEVICES)

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        /** The saved layout, or null when it does not decode (the caller starts from the default). */
        fun fromJson(value: String): DockLayout? =
            try {
                json.decodeFromString(serializer(), value)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
