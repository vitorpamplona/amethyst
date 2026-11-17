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

/**
 * The tabs layout: every screen is a tab of its own, never pushed onto, with no back arrow; it is
 * closed instead. [tabs] holds them all in strip order, pinned tabs first. A new screen opens right
 * after the tab that opened it ([openerOf]) and closing a tab shows its opener. Sections (Home and
 * the bar's places) open as pinned tabs, one of each. A tab can move out to a window of its own
 * ([setDetached]) and back.
 *
 * In a window too narrow for the strip the active tab shows on top of the tabs that opened it, so
 * back walks to them ([shown] with `wide` false).
 *
 * A composer ([opensOver]) is not a tab: it opens over the tab it was opened from ([overlays]),
 * shows while that tab is the active one, back closes it, and [popOut] moves it to a window of its
 * own. A link followed from one opens its tab in the background, so the composer stays in front;
 * a tab activated from elsewhere (a notification, a deep link) covers it until its tab is back.
 */
@Stable
class TabsModel(
    ids: EntryIds,
    initialTabs: List<NavStackEntry>,
    initialActiveId: Long,
    initialOpeners: Map<Long, Long> = emptyMap(),
    initialPinned: Set<Long> = emptySet(),
    initialDetached: Set<Long> = emptySet(),
    initialOverlays: List<NavStackEntry> = emptyList(),
) : SingleScreenModel(ids, initialOpeners) {
    /** Every tab, the moved-out ones included, in strip order. */
    val tabs: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialTabs) }

    private val pinned = mutableStateMapOf<Long, Boolean>().apply { initialPinned.forEach { put(it, true) } }

    private val detached = mutableStateMapOf<Long, Boolean>().apply { initialDetached.forEach { put(it, true) } }

    private var activeId by mutableStateOf(initialActiveId)

    /** The composers open over the tabs, newest last. Each one's opener is the tab it was opened from. */
    val overlays: SnapshotStateList<NavStackEntry> = mutableStateListOf<NavStackEntry>().apply { addAll(initialOverlays) }

    override val arrangement: NavArrangement get() = NavArrangement.TABS

    override val entries: List<NavStackEntry> get() = tabs

    override val top: NavStackEntry get() = tabs.firstOrNull { it.id == activeId && isMainScreen(it) } ?: tabs.first(::isMainScreen)

    /** The tab strip: every tab not moved out, pinned ones first. */
    fun strip(): List<NavStackEntry> = tabs.filter(::isMainScreen)

    fun isPinned(entry: NavStackEntry): Boolean = pinned[entry.id] == true

    override fun isMainScreen(entry: NavStackEntry): Boolean = detached[entry.id] != true

    override fun windows(): List<NavStackEntry> = tabs.filter { !isMainScreen(it) }

    override fun canGoBack(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = entry in overlays || (!wide && isMainScreen(entry) && openerOf(entry)?.takeIf(::isMainScreen) != null)

    /** The strip names a tab, when the window is wide enough for it; a moved-out tab's title bar names it. */
    override fun titleShownOutside(
        entry: NavStackEntry,
        wide: Boolean,
    ): Boolean = entry !in overlays && (wide || !isMainScreen(entry))

    override fun isOpenOver(entry: NavStackEntry): Boolean = entry in overlays

    override fun shown(wide: Boolean): List<NavStackEntry> = (if (wide) listOf(top) else chain(top).filter(::isMainScreen)) + overlaysOver(top)

    /** The composers open over [tab]: the ones opened from it, and any whose tab is no longer in the strip. */
    fun overlaysOver(tab: NavStackEntry): List<NavStackEntry> =
        overlays.filter { overlay ->
            val opener = openers[overlay.id]
            opener == tab.id || tabs.none { it.id == opener && isMainScreen(it) }
        }

    override fun holds(route: Route): Boolean = super.holds(route) || overlays.any { it.route == route }

    /** Every tab, the moved-out ones too: a window draws its tab with the state the main window kept, so moving it loses nothing. */
    override fun retained(): List<NavStackEntry> = tabs + overlays

    override fun open(request: NavRequest) {
        // A request a composer makes: its flow, a link in it, or another composer.
        val overlay = request.origin?.let { origin -> overlays.firstOrNull { it.id == origin } }
        if (request.kind is NavRequest.Kind.Replace) {
            if (overlay != null) replaceOverlay(overlay, request.route) else replace(request.origin ?: top.id, request.route)
            return
        }
        if (request.kind == NavRequest.Kind.Push && opensOver(request.route)) {
            openOver(request, overlay)
            return
        }
        if (overlay != null) {
            val from = openers[overlay.id]
            when (request.kind) {
                // A link while writing: its tab opens behind the composer, which stays in front.
                NavRequest.Kind.Push -> open(request.copy(origin = from, background = true))
                // A place of its own: the composer's work is done.
                else -> {
                    // Unless its post is still waiting for the signer: then it stays, behind the place.
                    if (!overlay.isHeldOpen()) closeOverlay(overlay)
                    open(request.copy(origin = from))
                }
            }
            return
        }
        existing(request)?.let { open ->
            if (request.kind == NavRequest.Kind.Section) open.tabRoute = request.route
            if (!request.background) show(open.id, request)
            flash(open.id)
            return
        }
        val section = request.kind == NavRequest.Kind.Section
        val entry = ids.entry(request.route, tabRoute = if (section) request.route else null, drawerRoot = request.kind == NavRequest.Kind.Drawer)
        val opener = request.origin ?: activeId
        if (section) {
            pinned[entry.id] = true
            tabs.add(pinnedCount(), entry)
        } else {
            // Right after the tab that opened it, or after the active tab when a window opened it.
            val at = tabs.indexOfFirst { it.id == opener && isMainScreen(it) }.takeIf { it >= 0 } ?: tabs.indexOfFirst { it.id == activeId }
            tabs.add(if (at >= 0) maxOf(at + 1, pinnedCount()) else tabs.size, entry)
        }
        rememberOpener(entry, opener, request.kind)
        if (request.background) flash(entry.id) else show(entry.id, request)
    }

    /**
     * Activates [id] for [request]. A tab asked for from a moved-out window also raises the main
     * window, which may be behind that window or hidden; asked for from the main window, it is
     * already in front.
     */
    private fun show(
        id: Long,
        request: NavRequest,
    ) {
        activate(id)
        val fromWindow = request.origin?.let { origin -> tabs.firstOrNull { it.id == origin } }?.let { !isMainScreen(it) } == true
        if (fromWindow && isMainScreen(tabs.first { it.id == id })) raise(id)
    }

    /** Opens the composer [request] asks for over the tabs, or brings the one already open for it to the front. */
    private fun openOver(
        request: NavRequest,
        from: NavStackEntry?,
    ) {
        // Over a tab in the strip: one moved out to a window has no composers of its own, they open
        // over the main window's active tab, which comes forward.
        val fromWindow = request.origin?.let { origin -> tabs.firstOrNull { it.id == origin } }?.let { !isMainScreen(it) } == true
        val opener = from?.let { openers[it.id] } ?: request.origin?.takeIf { origin -> tabs.any { it.id == origin && isMainScreen(it) } } ?: activeId
        val open = if (request.forceNew) null else overlays.firstOrNull { it.route == request.route }
        val entry = open ?: ids.entry(request.route)
        // The one already open comes to the front of the tab that asked for it.
        overlays.remove(entry)
        overlays.add(entry)
        openers[entry.id] = opener
        if (fromWindow) raise(activeId)
    }

    /** The composer [overlay]'s flow goes on as [route] (a created group): a tab of its own, beside the one it was opened from. */
    private fun replaceOverlay(
        overlay: NavStackEntry,
        route: Route,
    ) {
        val from = openers[overlay.id]
        closeOverlay(overlay, remember = false)
        open(NavRequest(route, origin = from))
    }

    private fun closeOverlay(
        overlay: NavStackEntry,
        remember: Boolean = true,
    ): Boolean {
        if (!overlays.remove(overlay)) return false
        if (remember) forget(overlay) else openers.remove(overlay.id)
        return true
    }

    /** Back closes the composer over the active tab first, then the tab. */
    override fun back(from: NavStackEntry?): Boolean {
        val overlay = if (from == null) overlaysOver(top).lastOrNull() else overlays.firstOrNull { it == from }
        if (overlay != null) return close(overlay.id)
        return close((from ?: top).id)
    }

    /** Closes the tab, window or composer [id]; one held open ([NavStackEntry.holdOpen]) stays. */
    override fun close(id: Long): Boolean {
        val overlay = overlays.firstOrNull { it.id == id } ?: return super.close(id)
        return !overlay.isHeldOpen() && closeOverlay(overlay)
    }

    /** Moves the composer [id] out of the main window, as a tab moved out to a window of its own. */
    override fun popOut(id: Long): Boolean {
        val overlay = overlays.firstOrNull { it.id == id } ?: return false
        overlays.remove(overlay)
        tabs.add(overlay)
        detached[id] = true
        raise(id)
        return true
    }

    /** Carries screen windows over from another layout: each stays a window, as a tab moved out. */
    override fun adoptWindows(windows: List<NavStackEntry>) {
        windows.filter { window -> tabs.none { it.id == window.id } }.forEach { window ->
            tabs.add(window)
            detached[window.id] = true
        }
    }

    override fun activate(id: Long) {
        val entry = tabs.firstOrNull { it.id == id } ?: return
        if (isMainScreen(entry)) activeId = id else raise(id)
    }

    /**
     * Closes the tab (or moved-out window) [entry]: the active tab moves to its opener, else to
     * its left neighbour. The last tab is replaced by Home.
     */
    override fun remove(entry: NavStackEntry) {
        val index = tabs.indexOf(entry)
        val opener = openerOf(entry)
        tabs.removeAt(index)
        pinned.remove(entry.id)
        detached.remove(entry.id)
        forget(entry)
        if (tabs.none(::isMainScreen)) {
            tabs.add(0, ids.entry(Route.Home, tabRoute = Route.Home).also { pinned[it.id] = true })
        }
        if (tabs.none { it.id == activeId && isMainScreen(it) }) {
            activeId = (opener?.takeIf(::isMainScreen) ?: tabs.getOrNull(index - 1)?.takeIf(::isMainScreen) ?: tabs.first(::isMainScreen)).id
        }
    }

    /** Closes every tab but [id] and the pinned ones. */
    fun closeOthers(id: Long) {
        strip().filter { it.id != id && !isPinned(it) }.forEach { close(it.id) }
        activate(id)
    }

    /** Closes the unpinned tabs after [id] in the strip. */
    fun closeToTheRight(id: Long) {
        val strip = strip()
        val at = strip.indexOfFirst { it.id == id }
        if (at < 0) return
        strip.drop(at + 1).filter { !isPinned(it) }.forEach { close(it.id) }
    }

    /** Pins or unpins the tab [id], moving it to the end of the pinned tabs or just after them. */
    fun setPinned(
        id: Long,
        pin: Boolean,
    ) {
        val entry = tabs.firstOrNull { it.id == id } ?: return
        if (isPinned(entry) == pin) return
        tabs.remove(entry)
        if (pin) pinned[id] = true else pinned.remove(id)
        tabs.add(pinnedCount(), entry)
    }

    /** Moves the tab [id] [delta] places along the strip, staying among the pinned or the unpinned tabs. */
    fun move(
        id: Long,
        delta: Int,
    ) {
        val strip = strip()
        val entry = strip.firstOrNull { it.id == id } ?: return
        if (delta == 0) return
        val group = strip.filter { isPinned(it) == isPinned(entry) }
        val target = group.getOrNull((group.indexOf(entry) + delta).coerceIn(0, group.lastIndex)) ?: return
        if (target == entry) return
        tabs.remove(entry)
        val at = tabs.indexOf(target)
        tabs.add(if (delta > 0) at + 1 else at, entry)
    }

    /** Shows the tab [delta] places from the active one, wrapping around: Ctrl+Tab and Ctrl+Shift+Tab. */
    fun cycle(delta: Int) {
        val strip = strip()
        if (strip.size < 2) return
        val at = strip.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        activate(strip[(at + delta).mod(strip.size)].id)
    }

    /** Opens another copy of the tab [id], next to it. */
    fun duplicate(id: Long) {
        val entry = tabs.firstOrNull { it.id == id } ?: return
        open(NavRequest(entry.route, origin = id, forceNew = true))
    }

    /**
     * Moves the tab [id] out to a window of its own, or ([out] false) back into the strip. A composer
     * moved out goes back over the active tab instead: it is never a tab.
     */
    fun setDetached(
        id: Long,
        out: Boolean,
    ) {
        val entry = tabs.firstOrNull { it.id == id } ?: return
        if (!isMainScreen(entry) == out) return
        if (!out && opensOver(entry.route)) {
            tabs.remove(entry)
            detached.remove(id)
            openers[id] = activeId
            overlays.add(entry)
            raise(activeId)
            return
        }
        if (out && tabs.count(::isMainScreen) <= 1) {
            // The strip never empties: Home takes the moved tab's place.
            tabs.add(0, ids.entry(Route.Home, tabRoute = Route.Home).also { pinned[it.id] = true })
        }
        if (out) {
            detached[id] = true
            pinned.remove(id)
            if (activeId == id) activeId = (openerOf(entry)?.takeIf(::isMainScreen) ?: tabs.first(::isMainScreen)).id
            raise(id)
        } else {
            detached.remove(id)
            activate(id)
        }
    }

    private fun pinnedCount() = tabs.count { isPinned(it) && isMainScreen(it) }

    internal val activeTabId: Long get() = activeId

    internal fun pinnedIds(): Set<Long> = pinned.keys.toSet()

    internal fun detachedIds(): Set<Long> = detached.keys.toSet()

    companion object {
        /**
         * Turns [stack] into tabs: each screen a tab opened by the one below it, the top one active,
         * Home and the bar's places pinned at the start.
         */
        fun fromStack(
            ids: EntryIds,
            stack: List<NavStackEntry>,
            opensOver: (Route) -> Boolean = { false },
        ): TabsModel {
            // Composers stay open over the tabs, opened by the screen below them; none becomes a tab.
            val composers = stack.filter { opensOver(it.route) }
            val composerOpeners =
                composers.mapNotNull { composer ->
                    stack.take(stack.indexOf(composer)).lastOrNull { it !in composers }?.let { composer.id to it.id }
                }
            return fromScreens(ids, stack.filterNot { it in composers }, composers, composerOpeners.toMap()).also { it.opensOver = opensOver }
        }

        private fun fromScreens(
            ids: EntryIds,
            stack: List<NavStackEntry>,
            composers: List<NavStackEntry>,
            composerOpeners: Map<Long, Long>,
        ): TabsModel {
            val sections = stack.filter { it.tabRoot || it.route == Route.Home }
            // Sections and drawer screens are places of their own, opened by nothing (as rememberOpener files them).
            val openers =
                stack
                    .zipWithNext { below, above -> above to below }
                    .filter { (above, _) -> above !in sections && !above.drawerRoot }
                    .associate { (above, below) -> above.id to below.id }
            return TabsModel(
                ids = ids,
                initialTabs = sections + stack.filterNot { it in sections },
                initialActiveId = stack.last().id,
                initialOpeners = openers + composerOpeners,
                initialPinned = sections.map { it.id }.toSet(),
                initialOverlays = composers,
            )
        }
    }
}
