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
            NavigationLayoutType.TABS -> stack.toList()
            NavigationLayoutType.WINDOWS -> stack.filter(::isSection)
        }

    /**
     * What the main host displays, bottom to top. A narrow window in tabs mode ([wide] false) shows
     * the active tab on top of the tabs that opened it, so back walks to them.
     */
    fun shown(wide: Boolean): List<NavStackEntry> =
        when (mode) {
            NavigationLayoutType.STACK -> stack.toList()
            NavigationLayoutType.TABS -> if (wide) listOf(top) else chain(top)
            NavigationLayoutType.WINDOWS -> listOf(top)
        }

    /** The screens that get a window of their own: empty unless in the windows mode. */
    fun windows(): List<NavStackEntry> = if (mode == NavigationLayoutType.WINDOWS) stack.filterNot(::isSection) else emptyList()

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
     * showing it. [tabRoute] marks a section the bar asks for, found again under that route.
     * [forceNew] opens another copy even when one is open (a fresh search).
     */
    fun open(
        route: Route,
        drawerRoot: Boolean = false,
        tabRoute: Route? = null,
        forceNew: Boolean = false,
    ): Boolean {
        if (!forceNew) {
            val existing = stack.firstOrNull { it.route == route || (tabRoute != null && it.tabRoute == tabRoute) }
            if (existing != null) {
                if (tabRoute != null) existing.tabRoute = tabRoute
                activate(existing.id)
                return true
            }
        }
        val entry = newEntry(route, tabRoute, drawerRoot)
        val at = stack.indexOfFirst { it.id == activeId }
        if (mode == NavigationLayoutType.TABS && at >= 0) {
            stack.add(at + 1, entry)
            openers[entry.id] = activeId
        } else {
            stack.add(entry)
        }
        activate(entry.id)
        return true
    }

    /** Shows the screen [id]: the tab, the main window's section, or raises the window. */
    fun activate(id: Long) {
        val entry = stack.firstOrNull { it.id == id } ?: return
        if (mode == NavigationLayoutType.WINDOWS && !isSection(entry)) {
            windowFocus = WindowFocus(id, (windowFocus?.serial ?: 0) + 1)
        } else {
            activeId = id
        }
    }

    /**
     * Closes the screen [id] (single-screen modes): the active tab moves to its opener, else to
     * its left neighbour. The last tab, or the main window's last section, is replaced by Home.
     */
    fun close(id: Long): Boolean {
        val index = stack.indexOfFirst { it.id == id }
        if (index < 0) return false
        val opener = openerOf(stack[index])
        stack.removeAt(index)
        openers.remove(id)
        // The tabs this one opened now count the tab that opened it as their opener.
        openers.entries.filter { it.value == id }.map { it.key }.forEach { child ->
            if (opener != null) openers[child] = opener.id else openers.remove(child)
        }
        if (stack.none(::isMainScreen)) stack.add(0, newEntry(Route.Home))
        if (stack.none { it.id == activeId }) {
            activeId = (opener ?: stack.getOrNull(index - 1)?.takeIf(::isMainScreen) ?: stack.first(::isMainScreen)).id
        }
        return true
    }

    /** A screen the main window can show: any tab, or a section in the windows mode. */
    private fun isMainScreen(entry: NavStackEntry): Boolean = mode != NavigationLayoutType.WINDOWS || isSection(entry)

    /** Back in a narrow tabs window: shows the tab that opened the active one, closing nothing. */
    fun back(): Boolean {
        val opener = openerOf(top) ?: return false
        activeId = opener.id
        return true
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
     * the one below it; entering windows keeps only the sections, in the main window.
     */
    fun changeMode(newMode: NavigationLayoutType) {
        if (newMode == mode) return
        if (isSingleScreen) {
            val keep = if (mode == NavigationLayoutType.TABS) chain(top) else listOf(top)
            stack.clear()
            stack.addAll(keep)
            openers.clear()
            if (stack.first().route != Route.Home) stack.add(0, newEntry(Route.Home))
        }
        mode = newMode
        savedTabs.clear()
        when (newMode) {
            NavigationLayoutType.STACK -> {}
            NavigationLayoutType.TABS -> {
                stack.zipWithNext { below, above -> openers[above.id] = below.id }
                activeId = stack.last().id
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

    private fun NavStackEntry.toSaved() = SavedEntry(route, id, tabRoute, drawerRoot, routeVersion)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun SavedEntry.toEntry() = NavStackEntry(route, id, tabRoute, drawerRoot, routeVersion)

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
