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
package com.vitorpamplona.amethyst.commons.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * One screen on the back stack. Two entries for the same [route] are still two screens (a profile
 * opened twice), so identity is the [id], never the route; [contentKey] is what Navigation 3 keys
 * the screen's saved UI state and ViewModels by.
 *
 * Everything else is state because it can change on an entry already on screen: [NavBackStacks.newStack]
 * hands the screen on top new arguments in place, and tapping the tab the user is standing on marks
 * it a tab root.
 */
@Stable
class NavStackEntry(
    route: Route,
    val id: Long,
    tabRoute: Route? = null,
    drawerRoot: Boolean = false,
    routeVersion: Int = 0,
    pinned: Boolean = false,
    detached: Boolean = false,
) {
    /** What this screen shows. Replaced in place when the same destination is reopened with other arguments. */
    var route by mutableStateOf(route)
        private set

    /**
     * How many times [route] was replaced in place. The screen is built anew for each version, with
     * ViewModels and saved UI state of its own, so the previous arguments' ViewModels are cleared
     * instead of living on in the entry until it is popped (which, for a tab root, is never).
     */
    var routeVersion by mutableStateOf(routeVersion)
        private set

    /** Hands this screen other arguments, as a new version of it. */
    internal fun reArgue(newRoute: Route) {
        if (newRoute == route) return
        route = newRoute
        routeVersion++
    }

    /**
     * The bottom-bar route this entry is the root of, or null when it is not a tab root. Kept apart
     * from [route] because the two can diverge — a deep link reopens the Notifications tab as
     * `Notification(eventId)` — while the tab must still be found again under the route the bar asks for.
     */
    var tabRoute by mutableStateOf(tabRoute)

    /** Reached from the bottom bar or rail: no back arrow, and tab switches fade instead of slide. */
    val tabRoot: Boolean get() = tabRoute != null

    /** Opened from the navigation drawer: it can pop, but keeps the bottom bar like a tab root. */
    var drawerRoot by mutableStateOf(drawerRoot)

    /** A tab kept at the start of the strip, drawn as its icon, and left alone by the bulk closes. */
    var pinned by mutableStateOf(pinned)

    /** A tab moved out to a window of its own (tabs layout). */
    var detached by mutableStateOf(detached)

    val contentKey: String get() = "nav-$id"

    override fun equals(other: Any?): Boolean = other is NavStackEntry && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "NavStackEntry($id, $route, v$routeVersion, tabRoute=$tabRoute, drawerRoot=$drawerRoot)"
}

/**
 * The app's back stack, owned by the app (Navigation 3 renders whatever this holds).
 *
 * In the [NavigationLayoutType.STACK] [mode], [stack] is what is on screen, bottom to top; it is
 * never empty and normally starts at [Route.Home]. [savedTabs] holds the root entry of every
 * bottom-bar tab the user left: a tab survives being left — its ViewModels and scroll position —
 * because its entry stays here, and so stays in the list whose state Navigation 3 keeps alive.
 * Nothing *above* a tab root is ever saved: switching tabs drops it first, so returning to a tab
 * always lands on the tab itself.
 *
 * In the single-screen modes every entry of [stack] is one screen of its own, never pushed onto:
 * - [NavigationLayoutType.TABS]: [stack] is the tab strip, in order, and [top] is the active tab.
 *   A new screen opens as a tab right after the one that opened it ([openerOf]); closing a tab
 *   goes back to its opener.
 * - [NavigationLayoutType.WINDOWS]: the sections (Home and the bar's tabs, see [isSection]) are the
 *   main window's screens, [top] being the one it shows; every other entry is a window ([windows]).
 *
 * Opening a screen that is already open focuses it instead ([windowFocus] for a window).
 */
@Stable
class NavBackStacks private constructor(
    initialStack: List<NavStackEntry>,
    initialSavedTabs: Map<Route, NavStackEntry>,
    private var nextId: Long,
    initialMode: NavigationLayoutType = NavigationLayoutType.STACK,
    initialActiveId: Long = -1,
    initialOpeners: Map<Long, Long> = emptyMap(),
) {
    constructor(start: Route = Route.Home) : this(listOf(NavStackEntry(start, 0)), emptyMap(), 1)

    val stack: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialStack) }

    val savedTabs: SnapshotStateMap<Route, NavStackEntry> = mutableStateMapOf<Route, NavStackEntry>().apply { putAll(initialSavedTabs) }

    /** How screens open; change it with [changeMode]. */
    var mode by mutableStateOf(initialMode)
        private set

    /** The screen shown in a single-screen mode: the active tab, or the main window's section. */
    private var activeId by mutableStateOf(initialActiveId)

    /** Which tab opened which, by entry id: closing a tab returns to its opener. */
    private val openers: SnapshotStateMap<Long, Long> = mutableStateMapOf<Long, Long>().apply { putAll(initialOpeners) }

    /** The last window asked to come to the front, so the platform can raise it. */
    var windowFocus by mutableStateOf<WindowFocus?>(null)
        private set

    /** The last tab asked for again while already open: the strip flashes it so the user sees where it went. */
    var attention by mutableStateOf<WindowFocus?>(null)
        private set

    /** What was closed, newest last, for [reopenClosed]. */
    private val closed = ArrayDeque<Route>()

    val canReopen: Boolean get() = closed.isNotEmpty()

    val isSingleScreen: Boolean get() = mode != NavigationLayoutType.STACK

    val top: NavStackEntry
        get() =
            if (isSingleScreen) {
                stack.firstOrNull { it.id == activeId } ?: stack.last()
            } else {
                stack.last()
            }

    val topRoute: Route get() = top.route

    /** Every entry whose state must stay alive in this host. Windows keep theirs in their own. */
    fun retained(): List<NavStackEntry> =
        when (mode) {
            NavigationLayoutType.STACK -> stack + savedTabs.values
            else -> stack.filter(::isMainScreen)
        }

    /**
     * What the main host displays, bottom to top. A narrow window in tabs mode ([wide] false) shows
     * the active tab on top of the tabs that opened it, so back walks to them.
     */
    fun shown(wide: Boolean): List<NavStackEntry> =
        when (mode) {
            NavigationLayoutType.STACK -> stack.toList()
            NavigationLayoutType.TABS -> if (wide) listOf(top) else chain(top).filter(::isMainScreen)
            NavigationLayoutType.WINDOWS -> listOf(top)
        }

    /** The screens that get a window of their own: every screen but the sections in the windows mode, the tabs moved out in the tabs mode. */
    fun windows(): List<NavStackEntry> = if (isSingleScreen) stack.filterNot(::isMainScreen) else emptyList()

    /** The tab strip, in order: pinned tabs first. Empty unless in the tabs mode. */
    fun tabs(): List<NavStackEntry> = if (mode == NavigationLayoutType.TABS) stack.filter(::isMainScreen) else emptyList()

    /** A screen the main window shows: a tab not moved out, or a section in the windows mode. */
    fun isMainScreen(entry: NavStackEntry): Boolean =
        when (mode) {
            NavigationLayoutType.STACK -> true
            NavigationLayoutType.TABS -> !entry.detached
            NavigationLayoutType.WINDOWS -> isSection(entry)
        }

    /** A screen the bar, rail or drawer reach as a top-level place: Home and the bar's tabs. */
    fun isSection(entry: NavStackEntry): Boolean = entry.tabRoot || entry.route == Route.Home

    /** The tab that opened [entry], while it is still open. */
    fun openerOf(entry: NavStackEntry): NavStackEntry? = openers[entry.id]?.let { id -> stack.firstOrNull { it.id == id } }

    /** [entry] after the tabs that opened it, oldest first. */
    fun chain(entry: NavStackEntry): List<NavStackEntry> {
        val result = ArrayDeque<NavStackEntry>()
        var current: NavStackEntry? = entry
        while (current != null && current !in result) {
            result.addFirst(current)
            current = openerOf(current)
        }
        return result.toList()
    }

    private fun newEntry(
        route: Route,
        tabRoute: Route? = null,
        drawerRoot: Boolean = false,
    ) = NavStackEntry(route, nextId++, tabRoute, drawerRoot)

    /** Pushes [route], unless it is already the screen on top. Returns whether it pushed. */
    fun push(
        route: Route,
        drawerRoot: Boolean = false,
    ): Boolean {
        if (isSingleScreen) return open(route, drawerRoot = drawerRoot)
        if (topRoute == route) return false
        stack.add(newEntry(route, drawerRoot = drawerRoot))
        return true
    }

    /** Pops the top screen; the last one is never popped. Returns whether it popped. */
    fun pop(): Boolean {
        // A tab closes; the main window's sections never pop (a window closes through [close]).
        if (isSingleScreen) return mode == NavigationLayoutType.TABS && close(top.id)
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /**
     * Opens [route] as the start of a fresh stretch of history: drops the most recent copy of
     * [route] and everything above it, then shows [route] once. When the screen left on top is the
     * same destination with other arguments, that screen takes the new arguments in place rather
     * than stacking a second copy — the same entry, so its root flags carry over and nothing
     * animates, as Navigation 2's single-top launch behaved. Its content is a new
     * [NavStackEntry.routeVersion], though: the previous arguments' ViewModels are cleared.
     */
    fun newStack(route: Route) {
        if (isSingleScreen) {
            open(route)
            return
        }
        val existing = stack.indexOfLast { it.route == route }
        if (existing >= 0) removeFrom(existing)
        if (stack.isNotEmpty() && topRoute::class == route::class) {
            top.reArgue(route)
        } else {
            stack.add(newEntry(route))
        }
    }

    /** Drops the most recent screen of [klass] and everything above it, then pushes [route]. */
    fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
        if (isSingleScreen) {
            replace(top.id, route)
            return
        }
        val existing = stack.indexOfLast { klass.isInstance(it.route) }
        if (existing >= 0) removeFrom(existing)
        stack.add(newEntry(route))
    }

    /**
     * A bottom-bar or rail tap: lands on [route]'s tab itself, never on something pushed on top of a
     * tab. The tab being left keeps its root (and so its state) in [savedTabs]; [Route.Home] stays at
     * the bottom so back from any tab returns to Home, and back from Home leaves the app.
     */
    fun switchTab(route: Route) {
        if (isSingleScreen) {
            open(route, tabRoute = route)
            return
        }
        popPushesAboveTabRoot()

        // Dropping those pushes is often the whole job: re-tapping the tab the user is inside, and
        // every tap of Home from somewhere inside Home, end here.
        if (isTab(top, route)) {
            top.tabRoute = route
            return
        }

        val homeIndex = stack.indexOfFirst { it.route == Route.Home }
        if (homeIndex >= 0) {
            // Only a tab root can sit above Home now; keep it, with its state, for the next visit,
            // filed under the route the bar will ask for it by.
            while (stack.lastIndex > homeIndex) {
                val left = stack.removeAt(stack.lastIndex)
                left.tabRoute?.let { savedTabs[it] = left }
            }
        }

        if (isTab(top, route)) {
            top.tabRoute = route
            return
        }

        stack.add(savedTabs.remove(route)?.also { it.tabRoute = route } ?: newEntry(route, tabRoute = route))
    }

    /** Whether [entry] is [route]'s tab: the screen itself, or the tab root the bar opened as [route]. */
    private fun isTab(
        entry: NavStackEntry,
        route: Route,
    ) = entry.route == route || entry.tabRoute == route

    /**
     * Drops every screen the user pushed on top of the tab root they are in (or on top of Home,
     * which is a tab root the user may never have tapped because the app starts there).
     */
    private fun popPushesAboveTabRoot() {
        while (stack.size > 1) {
            val top = top
            if (top.tabRoot || top.route == Route.Home) return
            stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * Opens [route] as a screen of its own (single-screen modes), or focuses the one already
     * showing it. [tabRoute] marks a section the bar asks for, found again under that route; a
     * section opens as a pinned tab. A tab opens right after its opener ([openerId], else the active
     * tab); [background] leaves the active tab where it is. [forceNew] opens another copy even when
     * one is open (a fresh search, a duplicate).
     */
    fun open(
        route: Route,
        drawerRoot: Boolean = false,
        tabRoute: Route? = null,
        forceNew: Boolean = false,
        background: Boolean = false,
        openerId: Long? = null,
    ): Boolean {
        if (!forceNew) {
            val existing = stack.firstOrNull { it.route == route || (tabRoute != null && it.tabRoute == tabRoute) }
            if (existing != null) {
                if (tabRoute != null) existing.tabRoute = tabRoute
                // Shown where it already is, flashed so the user sees where it went.
                if (!background) activate(existing.id)
                flash(existing.id)
                return true
            }
        }
        val entry = newEntry(route, tabRoute, drawerRoot)
        val opener = openerId ?: activeId
        if (mode == NavigationLayoutType.TABS) {
            if (tabRoute != null) {
                entry.pinned = true
                stack.add(pinnedCount(), entry)
            } else {
                val at = stack.indexOfFirst { it.id == opener && isMainScreen(it) }.takeIf { it >= 0 } ?: stack.indexOfFirst { it.id == activeId }
                stack.add(if (at >= 0) maxOf(at + 1, pinnedCount()) else stack.size, entry)
            }
        } else {
            stack.add(entry)
        }
        // A section or a drawer screen is a place of its own, not something the active tab opened.
        if (tabRoute == null && !drawerRoot && stack.any { it.id == opener }) openers[entry.id] = opener
        if (!background) activate(entry.id) else flash(entry.id)
        return true
    }

    private fun pinnedCount() = stack.count { it.pinned && isMainScreen(it) }

    private fun flash(id: Long) {
        attention = WindowFocus(id, (attention?.serial ?: 0) + 1)
    }

    /** Shows the screen [id]: the tab, the main window's section, or raises the window. */
    fun activate(id: Long) {
        val entry = stack.firstOrNull { it.id == id } ?: return
        if (isMainScreen(entry)) {
            activeId = id
            // A section asked for from a screen window brings the main window forward.
            if (mode == NavigationLayoutType.WINDOWS) windowFocus = WindowFocus(id, (windowFocus?.serial ?: 0) + 1)
        } else {
            windowFocus = WindowFocus(id, (windowFocus?.serial ?: 0) + 1)
        }
    }

    /**
     * Closes the screen [id] (single-screen modes): the active tab moves to its opener, else to
     * its left neighbour. The last tab, or the main window's last section, is replaced by Home.
     * The closed screen can come back with [reopenClosed].
     */
    fun close(id: Long): Boolean {
        val index = stack.indexOfFirst { it.id == id }
        if (index < 0) return false
        val closing = stack[index]
        val opener = openerOf(closing)
        stack.removeAt(index)
        openers.remove(id)
        // The tabs this one opened now count the tab that opened it as their opener.
        openers.entries.filter { it.value == id }.map { it.key }.forEach { child ->
            if (opener != null) openers[child] = opener.id else openers.remove(child)
        }
        closed.addLast(closing.route)
        while (closed.size > MAX_CLOSED) closed.removeFirst()
        if (stack.none(::isMainScreen)) stack.add(0, newEntry(Route.Home, tabRoute = if (mode == NavigationLayoutType.TABS) Route.Home else null).also { it.pinned = mode == NavigationLayoutType.TABS })
        if (stack.none { it.id == activeId && isMainScreen(it) }) {
            activeId = (opener?.takeIf(::isMainScreen) ?: stack.getOrNull(index - 1)?.takeIf(::isMainScreen) ?: stack.first(::isMainScreen)).id
        }
        return true
    }

    /** Opens the screen closed last again, as a new tab or window. */
    fun reopenClosed(): Boolean {
        val route = closed.removeLastOrNull() ?: return false
        return open(route, forceNew = stack.none { it.route == route })
    }

    /** Closes every tab but [id] and the pinned ones. */
    fun closeOthers(id: Long) {
        tabs().filter { it.id != id && !it.pinned }.forEach { close(it.id) }
        activate(id)
    }

    /** Closes the unpinned tabs after [id] in the strip. */
    fun closeToTheRight(id: Long) {
        val tabs = tabs()
        val at = tabs.indexOfFirst { it.id == id }
        if (at < 0) return
        tabs.drop(at + 1).filter { !it.pinned }.forEach { close(it.id) }
    }

    /** Closes the screens [id] opened, and the ones they opened, leaving [id] open. */
    fun closeOpenedBy(id: Long) {
        val descendants = stack.filter { entry -> entry.id != id && chain(entry).any { it.id == id } }
        descendants.forEach { close(it.id) }
    }

    /** The screens [id] opened that are still open, directly or through others. */
    fun openedBy(id: Long): Int = stack.count { entry -> entry.id != id && chain(entry).any { it.id == id } }

    /** Pins or unpins the tab [id], moving it to the end of the pinned tabs or just after them. */
    fun setPinned(
        id: Long,
        pinned: Boolean,
    ) {
        val entry = stack.firstOrNull { it.id == id } ?: return
        if (entry.pinned == pinned) return
        stack.remove(entry)
        entry.pinned = pinned
        stack.add(pinnedCount(), entry)
    }

    /** Moves the tab [id] [delta] places along the strip, staying among the pinned or the unpinned tabs. */
    fun move(
        id: Long,
        delta: Int,
    ) {
        val tabs = tabs()
        val from = tabs.indexOfFirst { it.id == id }
        if (from < 0 || delta == 0) return
        val entry = tabs[from]
        val group = tabs.filter { it.pinned == entry.pinned }
        val inGroup = group.indexOf(entry)
        val target = group.getOrNull((inGroup + delta).coerceIn(0, group.lastIndex)) ?: return
        if (target == entry) return
        stack.remove(entry)
        val at = stack.indexOf(target)
        stack.add(if (delta > 0) at + 1 else at, entry)
    }

    /** Shows the tab [delta] places from the active one, wrapping around: Ctrl+Tab and Ctrl+Shift+Tab. */
    fun cycle(delta: Int) {
        val tabs = tabs()
        if (tabs.size < 2) return
        val at = tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        activate(tabs[(at + delta).mod(tabs.size)].id)
    }

    /** Opens another copy of the screen [id], next to it. */
    fun duplicate(id: Long) {
        val entry = stack.firstOrNull { it.id == id } ?: return
        open(entry.route, forceNew = true, openerId = id)
    }

    /** Moves the tab [id] out to a window of its own, or ([detached] false) back into the strip. */
    fun setDetached(
        id: Long,
        detached: Boolean,
    ) {
        if (mode != NavigationLayoutType.TABS) return
        val entry = stack.firstOrNull { it.id == id } ?: return
        if (entry.detached == detached) return
        if (detached && stack.count(::isMainScreen) <= 1) {
            // The strip never empties: Home takes the moved tab's place.
            stack.add(0, newEntry(Route.Home, tabRoute = Route.Home).also { it.pinned = true })
        }
        entry.detached = detached
        if (detached) {
            entry.pinned = false
            if (activeId == id) activeId = (openerOf(entry)?.takeIf(::isMainScreen) ?: stack.first(::isMainScreen)).id
            windowFocus = WindowFocus(id, (windowFocus?.serial ?: 0) + 1)
        } else {
            activate(id)
        }
    }

    /** Hands the screen [id] another route in place: the next step of a flow, not a new screen. */
    fun replace(
        id: Long,
        route: Route,
    ) {
        stack.firstOrNull { it.id == id }?.reArgue(route)
    }

    /**
     * Switches how screens open. Leaving a single-screen mode keeps the active screen and the tabs
     * that opened it as a stack; entering tabs turns each screen of the stack into a tab opened by
     * the one below it, with Home and the bar's tabs pinned; entering windows keeps only the
     * sections, in the main window.
     */
    fun changeMode(newMode: NavigationLayoutType) {
        if (newMode == mode) return
        if (isSingleScreen) {
            val keep = if (mode == NavigationLayoutType.TABS) chain(top).filter(::isMainScreen) else listOf(top)
            stack.clear()
            stack.addAll(keep)
            openers.clear()
            keep.forEach {
                it.pinned = false
                it.detached = false
            }
            if (stack.first().route != Route.Home) stack.add(0, newEntry(Route.Home))
        }
        mode = newMode
        savedTabs.clear()
        closed.clear()
        when (newMode) {
            NavigationLayoutType.STACK -> {}
            NavigationLayoutType.TABS -> {
                stack.zipWithNext { below, above -> openers[above.id] = below.id }
                activeId = stack.last().id
                stack.filter(::isSection).forEach { it.pinned = true }
                val pinned = stack.filter { it.pinned }
                stack.removeAll(pinned)
                stack.addAll(0, pinned)
            }
            NavigationLayoutType.WINDOWS -> {
                stack.removeAll { !isSection(it) }
                if (stack.isEmpty()) stack.add(newEntry(Route.Home))
                activeId = stack.last().id
            }
        }
    }

    /** Removes [index] and everything above it; every caller then adds a screen, so the stack never stays empty. */
    private fun removeFrom(index: Int) {
        while (stack.lastIndex >= index) stack.removeAt(stack.lastIndex)
    }

    fun encode(): String =
        json.encodeToString(
            Saved.serializer(),
            Saved(
                stack = stack.map { it.toSaved() },
                savedTabs = savedTabs.values.map { it.toSaved() },
                nextId = nextId,
                mode = mode,
                activeId = activeId,
                openers = openers.map { listOf(it.key, it.value) },
            ),
        )

    @Serializable
    private class SavedEntry(
        val route: Route,
        val id: Long,
        val tabRoute: Route? = null,
        val drawerRoot: Boolean = false,
        val routeVersion: Int = 0,
        val pinned: Boolean = false,
        val detached: Boolean = false,
    )

    @Serializable
    private class Saved(
        val stack: List<SavedEntry>,
        val savedTabs: List<SavedEntry>,
        val nextId: Long,
        val mode: NavigationLayoutType = NavigationLayoutType.STACK,
        val activeId: Long = -1,
        val openers: List<List<Long>> = emptyList(),
    )

    private fun NavStackEntry.toSaved() = SavedEntry(route, id, tabRoute, drawerRoot, routeVersion, pinned, detached)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val MAX_CLOSED = 10

        private fun SavedEntry.toEntry() = NavStackEntry(route, id, tabRoute, drawerRoot, routeVersion, pinned, detached)

        /** Restores what [encode] wrote, or null when it can't be read (an app update renamed a route). */
        fun decode(encoded: String): NavBackStacks? =
            runCatching {
                val saved = json.decodeFromString(Saved.serializer(), encoded)
                if (saved.stack.isEmpty()) return null
                NavBackStacks(
                    initialStack = saved.stack.map { it.toEntry() },
                    // Filed under the route the bar asks for, as switchTab files them: a tab re-argued
                    // in place by newStack no longer matches its own route.
                    initialSavedTabs = saved.savedTabs.map { it.toEntry() }.associateBy { it.tabRoute ?: it.route },
                    nextId = saved.nextId,
                    initialMode = saved.mode,
                    initialActiveId = saved.activeId,
                    initialOpeners = saved.openers.filter { it.size == 2 }.associate { it[0] to it[1] },
                )
            }.getOrNull()
    }
}

/** Asks the platform to raise the window of entry [id]; [serial] tells two asks for the same window apart. */
data class WindowFocus(
    val id: Long,
    val serial: Int,
)
