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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType

/**
 * How a window's screens are held. The user's [NavigationLayoutType] picks one: the deck is a
 * [STACK] with columns beside it, so the model never sees it.
 */
enum class NavArrangement {
    STACK,
    TABS,
    WINDOWS,
}

/** The model a layout runs on: the deck's main screen is a plain stack. */
fun NavigationLayoutType.arrangement(): NavArrangement =
    when (this) {
        NavigationLayoutType.STACK, NavigationLayoutType.DECK -> NavArrangement.STACK
        NavigationLayoutType.TABS -> NavArrangement.TABS
        NavigationLayoutType.WINDOWS -> NavArrangement.WINDOWS
    }

/** Whether the layout shows the deck's columns beside the main screen. */
val NavigationLayoutType.showsDeck: Boolean get() = this == NavigationLayoutType.DECK

/** Asks the platform to raise the window of entry [id]; [serial] tells two asks for the same window apart. */
data class WindowFocus(
    val id: Long,
    val serial: Int,
)

/**
 * The screens of one window and the rules for moving between them: one implementation per
 * [NavArrangement] ([StackModel], [TabsModel], [WindowsModel]). Every navigation is a [NavRequest];
 * each model reads it its own way. What the navigation host draws comes from [shown], [retained]
 * and [windows]; whether a screen has a back arrow, from [canGoBack].
 */
@Stable
sealed class NavModel(
    internal val ids: EntryIds,
) {
    abstract val arrangement: NavArrangement

    /** Every screen this model holds, in its own order. */
    abstract val entries: List<NavStackEntry>

    /** The screen the main window shows on top: the top of the stack, the active tab, the main window's section. */
    abstract val top: NavStackEntry

    abstract fun open(request: NavRequest)

    /** Back from [from] (the screen on top when null): a pop, a tab or window closed. Whether anything happened. */
    abstract fun back(from: NavStackEntry? = null): Boolean

    /** Whether [entry] shows a back arrow. [wide] is false when the window is too narrow for the tab strip. */
    abstract fun canGoBack(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean

    /** Whether [entry] shows the bottom bar: tab roots and drawer screens do, pushes and screen windows don't. */
    open fun showsBar(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = isMainScreen(entry) && (entry.drawerRoot || !canGoBack(entry, wide))

    /** Whether a tab or a window's title bar names [entry] where it is shown, so its own top bar need not. */
    open fun titleShownOutside(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = false

    /** What the main window's host displays, bottom to top. */
    abstract fun shown(wide: Boolean): List<NavStackEntry>

    /**
     * Every entry whose state the main window's host keeps alive. A screen window of the stack or the
     * tabs is among them, so moving a screen out keeps its state; the windows layout's keep their own.
     */
    abstract fun retained(): List<NavStackEntry>

    /** The screens with an OS window of their own. */
    open fun windows(): List<NavStackEntry> = emptyList()

    /** Whether [entry] shows in the main window rather than in a window of its own. */
    open fun isMainScreen(entry: NavStackEntry): Boolean = true

    /** The last window asked to come to the front. */
    open val windowFocus: WindowFocus? get() = null

    /**
     * Whether a screen of this route is a composer: a task that opens over the screen that asked for
     * it, not a place of its own. Set by the host from its destinations; until then, nothing is.
     */
    var opensOver: (Route) -> Boolean = { false }

    /** The composer [entry] is, open over the main window and not yet moved out. */
    open fun isOpenOver(entry: NavStackEntry): Boolean = false

    /** Moves the composer [id] out to a window of its own. Whether it moved. */
    open fun popOut(id: Long): Boolean = false

    /** Closes the screen [id]: a tab, a screen window, a composer. Whether anything closed. */
    open fun close(id: Long): Boolean = false

    /** Takes over the screen windows another layout had open, so switching layouts keeps them, drafts and all. */
    internal open fun adoptWindows(windows: List<NavStackEntry>) {}

    /** Hands the screen [id] another route in place: the next step of a flow, not a new screen. */
    fun replace(
        id: Long,
        route: Route,
    ) {
        entries.firstOrNull { it.id == id }?.reArgue(route)
    }
}

/**
 * What the tabs and the windows share: every screen is one place of its own, opened beside the one
 * that opened it ([openerOf]), closed rather than left, and brought back with [reopenClosed]. Opening
 * a screen that is already open shows it instead, with an [attention] flash.
 */
@Stable
sealed class SingleScreenModel(
    ids: EntryIds,
    initialOpeners: Map<Long, Long>,
) : NavModel(ids) {
    /** Which screen opened which, by entry id: closing a screen returns to its opener. */
    protected val openers: SnapshotStateMap<Long, Long> = mutableStateMapOf<Long, Long>().apply { putAll(initialOpeners) }

    /** What was closed, newest last. Snapshot state, so menus reading [canReopen] follow it. */
    private val closed = mutableStateListOf<Route>()

    val canReopen: Boolean get() = closed.isNotEmpty()

    /** The last screen asked for again while already open, or opened in the background: the strip flashes it. */
    var attention by mutableStateOf<WindowFocus?>(null)
        private set

    private var focus by mutableStateOf<WindowFocus?>(null)

    override val windowFocus: WindowFocus? get() = focus

    internal fun openerMap(): Map<Long, Long> = openers.toMap()

    /** The screen that opened [entry], while it is still open. */
    fun openerOf(entry: NavStackEntry): NavStackEntry? = openers[entry.id]?.let { id -> entries.firstOrNull { it.id == id } }

    /** [entry] after the screens that opened it, oldest first. */
    fun chain(entry: NavStackEntry): List<NavStackEntry> {
        val result = ArrayDeque<NavStackEntry>()
        var current: NavStackEntry? = entry
        while (current != null && current !in result) {
            result.addFirst(current)
            current = openerOf(current)
        }
        return result.toList()
    }

    /** How many open screens [id] opened, directly or through others. */
    fun openedBy(id: Long): Int = entries.count { entry -> entry.id != id && chain(entry).any { it.id == id } }

    /** Closes the screens [id] opened, and the ones they opened, leaving [id] open. */
    fun closeOpenedBy(id: Long) {
        entries.filter { entry -> entry.id != id && chain(entry).any { it.id == id } }.forEach { close(it.id) }
    }

    /** Shows the screen [id]: the tab, the main window's section, or raises its window. */
    abstract fun activate(id: Long)

    /**
     * Closes the screen [id]; it can come back with [reopenClosed]. A screen held open
     * ([NavStackEntry.holdOpen]) stays, and its tab flashes, whichever way the close was asked for:
     * its own ×, the tab menu's bulk closes, a window's title bar or back.
     */
    override fun close(id: Long): Boolean {
        val entry = entries.firstOrNull { it.id == id } ?: return false
        if (entry.isHeldOpen()) {
            flash(id)
            return false
        }
        remove(entry)
        return true
    }

    /** Takes [entry], which is open and not held, out of the layout. */
    protected abstract fun remove(entry: NavStackEntry)

    override fun back(from: NavStackEntry?): Boolean = close((from ?: top).id)

    /** Opens the screen closed last again. */
    fun reopenClosed(): Boolean {
        val route = closed.removeLastOrNull() ?: return false
        open(NavRequest(route, forceNew = !holds(route)))
        return true
    }

    /** Whether a screen of [route] is open here. */
    protected open fun holds(route: Route): Boolean = entries.any { it.route == route }

    /** Files a closing screen for [reopenClosed] and hands the screens it opened to its own opener. */
    protected fun forget(entry: NavStackEntry) {
        val opener = openers.remove(entry.id)
        openers.entries.filter { it.value == entry.id }.map { it.key }.forEach { child ->
            if (opener != null) openers[child] = opener else openers.remove(child)
        }
        closed.add(entry.route)
        while (closed.size > MAX_CLOSED) closed.removeAt(0)
    }

    /** Remembers that [opener] opened [entry]: never for sections and drawer screens, places of their own. */
    protected fun rememberOpener(
        entry: NavStackEntry,
        opener: Long?,
        kind: NavRequest.Kind,
    ) {
        if (opener == null || kind == NavRequest.Kind.Section || kind == NavRequest.Kind.Drawer) return
        if (entries.any { it.id == opener }) openers[entry.id] = opener
    }

    protected fun flash(id: Long) {
        attention = WindowFocus(id, (attention?.serial ?: 0) + 1)
    }

    protected fun raise(id: Long) {
        focus = WindowFocus(id, (focus?.serial ?: 0) + 1)
    }

    /**
     * The open screen [request] asks for, when it should be shown rather than opened again.
     *
     * A restart (a tapped notification, a deep link) for one of the bar's sections with other
     * arguments, such as `Notification(eventId)`, is that section's tab, handed the new arguments
     * ([NavStackEntry.reArgue]), as [StackModel.newStack] does on the stack. Matching the route
     * alone opened a second Notifications tab beside the pinned one, one more per notification.
     * Only a route that is its section in another state counts ([sectionItReopens]): a section of
     * the same class can be another place, such as the bar's own Profile and someone else's.
     */
    protected fun existing(request: NavRequest): NavStackEntry? {
        if (request.forceNew) return null
        entries
            .firstOrNull { it.route == request.route || (request.kind == NavRequest.Kind.Section && it.tabRoute == request.route) }
            ?.let { return it }
        if (request.kind != NavRequest.Kind.Restart) return null
        val sectionRoute = request.route.sectionItReopens() ?: return null
        val section = entries.firstOrNull { it.tabRoute == sectionRoute } ?: return null
        section.reArgue(request.route)
        return section
    }

    companion object {
        const val MAX_CLOSED = 10
    }
}

/**
 * The bar section this route is, opened in another state (a notification to scroll to, a day of
 * the front page, an app to connect), or null when the route is a place of its own. A link to it
 * takes over that section's tab instead of opening a second one. A [Route.Profile] is not here: the
 * bar's Profile is the user's, and a link to someone else's must not replace it.
 */
internal fun Route.sectionItReopens(): Route? =
    when (this) {
        is Route.Notification -> Route.Notification()
        is Route.FrontPage -> Route.FrontPage()
        is Route.Nip46Signer -> Route.Nip46Signer()
        else -> null
    }
