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
package com.vitorpamplona.amethyst.ui.navigation.navs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * One screen on the back stack. Two entries for the same [route] are still two screens (a profile
 * opened twice), so identity is the [id], never the route; [contentKey] is what Navigation 3 keys
 * the screen's saved UI state and ViewModels by.
 *
 * The two root flags are state because they can be set on an entry already on screen: tapping the
 * tab the user is standing on marks it a tab root in place.
 */
@Stable
class NavStackEntry(
    val route: Route,
    val id: Long,
    tabRoot: Boolean = false,
    drawerRoot: Boolean = false,
) {
    /** Reached from the bottom bar or rail: no back arrow, and tab switches fade instead of slide. */
    var tabRoot by mutableStateOf(tabRoot)

    /** Opened from the navigation drawer: it can pop, but keeps the bottom bar like a tab root. */
    var drawerRoot by mutableStateOf(drawerRoot)

    val contentKey: String get() = "nav-$id"

    override fun equals(other: Any?): Boolean = other is NavStackEntry && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "NavStackEntry($id, $route, tabRoot=$tabRoot, drawerRoot=$drawerRoot)"
}

/**
 * The app's back stack, owned by the app (Navigation 3 renders whatever this holds).
 *
 * [stack] is what is on screen, bottom to top; it is never empty and normally starts at
 * [Route.Home]. [savedTabs] holds the root entry of every bottom-bar tab the user left: a tab
 * survives being left — its ViewModels and scroll position — because its entry stays here, and so
 * stays in the list whose state Navigation 3 keeps alive. Nothing *above* a tab root is ever saved:
 * switching tabs drops it first, so returning to a tab always lands on the tab itself.
 */
@Stable
class NavBackStacks private constructor(
    initialStack: List<NavStackEntry>,
    initialSavedTabs: Map<Route, NavStackEntry>,
    private var nextId: Long,
) {
    constructor(start: Route = Route.Home) : this(listOf(NavStackEntry(start, 0)), emptyMap(), 1)

    val stack: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialStack) }

    val savedTabs: SnapshotStateMap<Route, NavStackEntry> = mutableStateMapOf<Route, NavStackEntry>().apply { putAll(initialSavedTabs) }

    val top: NavStackEntry get() = stack.last()

    val topRoute: Route get() = top.route

    /** Every entry whose state must stay alive: the visible stack, then the tabs the user left. */
    fun retained(): List<NavStackEntry> = stack + savedTabs.values

    private fun newEntry(
        route: Route,
        tabRoot: Boolean = false,
        drawerRoot: Boolean = false,
    ) = NavStackEntry(route, nextId++, tabRoot, drawerRoot)

    /** Pushes [route], unless it is already the screen on top. Returns whether it pushed. */
    fun push(
        route: Route,
        drawerRoot: Boolean = false,
    ): Boolean {
        if (topRoute == route) return false
        stack.add(newEntry(route, drawerRoot = drawerRoot))
        return true
    }

    /** Pops the top screen; the last one is never popped. Returns whether it popped. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /**
     * Opens [route] as the start of a fresh stretch of history: drops the most recent copy of
     * [route] and everything above it, then shows [route] once. When the screen left on top is the
     * same destination with other arguments, it is replaced rather than stacked under the new one,
     * and the replacement keeps its place: a tab root stays a tab root, a drawer screen a drawer one.
     */
    fun newStack(route: Route) {
        val existing = stack.indexOfLast { it.route == route }
        if (existing >= 0) removeFrom(existing)
        if (stack.isNotEmpty() && topRoute::class == route::class) {
            val replaced = top
            stack[stack.lastIndex] = newEntry(route, tabRoot = replaced.tabRoot, drawerRoot = replaced.drawerRoot)
        } else {
            stack.add(newEntry(route))
        }
    }

    /** Drops the most recent screen of [klass] and everything above it, then pushes [route]. */
    fun <T : Route> popUpTo(
        route: Route,
        klass: KClass<T>,
    ) {
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
        popPushesAboveTabRoot()

        // Dropping those pushes is often the whole job: re-tapping the tab the user is inside, and
        // every tap of Home from somewhere inside Home, end here.
        if (topRoute == route) {
            top.tabRoot = true
            return
        }

        val homeIndex = stack.indexOfFirst { it.route == Route.Home }
        if (homeIndex >= 0) {
            // Only a tab root can sit above Home now; keep it, with its state, for the next visit.
            while (stack.lastIndex > homeIndex) {
                val left = stack.removeAt(stack.lastIndex)
                if (left.tabRoot) savedTabs[left.route] = left
            }
        }

        if (topRoute == route) {
            top.tabRoot = true
            return
        }

        stack.add(savedTabs.remove(route)?.also { it.tabRoot = true } ?: newEntry(route, tabRoot = true))
    }

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
            ),
        )

    @Serializable
    private class SavedEntry(
        val route: Route,
        val id: Long,
        val tabRoot: Boolean,
        val drawerRoot: Boolean,
    )

    @Serializable
    private class Saved(
        val stack: List<SavedEntry>,
        val savedTabs: List<SavedEntry>,
        val nextId: Long,
    )

    private fun NavStackEntry.toSaved() = SavedEntry(route, id, tabRoot, drawerRoot)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun SavedEntry.toEntry() = NavStackEntry(route, id, tabRoot, drawerRoot)

        /** Restores what [encode] wrote, or null when it can't be read (an app update renamed a route). */
        fun decode(encoded: String): NavBackStacks? =
            runCatching {
                val saved = json.decodeFromString(Saved.serializer(), encoded)
                if (saved.stack.isEmpty()) return null
                NavBackStacks(
                    initialStack = saved.stack.map { it.toEntry() },
                    initialSavedTabs = saved.savedTabs.map { it.toEntry() }.associateBy { it.route },
                    nextId = saved.nextId,
                )
            }.getOrNull()
    }
}
