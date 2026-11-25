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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.model.dock.DockLayout
import com.vitorpamplona.amethyst.commons.model.dock.DockLayoutPreferences
import com.vitorpamplona.amethyst.commons.model.dock.DropTarget
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.dock.PanelState
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The dock's one source of truth while the app runs: the saved [DockLayout] (through [prefs]), the
 * panels this platform has ([panels]), and what only lasts the session: the panel shown over the
 * screen ([overlay]), the one [maximized], the drag in progress ([drag]) and requests waiting for a
 * panel (a question for the assistant).
 *
 * Every panel's content is built once, here, as movable content with its own ViewModels and saved
 * state, so it keeps its state wherever it is drawn: in a slot, over the screen, or in a window.
 * A panel is drawn in at most one place at a time.
 */
@Stable
class DockController internal constructor(
    private val prefs: DockLayoutPreferences,
    val panels: List<DockPanelSpec>,
    internal val accountViewModel: AccountViewModel,
) {
    /** The platform's panels; screens the user pinned come and go with the layout ([allIds]). */
    val ids: List<PanelId> = panels.map { it.id }

    private val byId = panels.associateBy { it.id }

    private val screenSpecs = mutableMapOf<PanelId, DockPanelSpec>()

    fun spec(id: PanelId): DockPanelSpec? = byId[id] ?: screenSpec(id)

    fun has(id: PanelId): Boolean = id in byId || (id.isScreen && id in prefs.flow.value.screens)

    /** Every panel of [layout] this platform can draw: its own, then the pinned screens in their order. */
    fun allIds(layout: DockLayout = layout()): List<PanelId> = ids + layout.ordered().filter { it.isScreen && it in layout.screens }

    private fun screenSpec(id: PanelId): DockPanelSpec? {
        if (!id.isScreen) return null
        screenSpecs[id]?.let { return it }
        val route =
            prefs.flow.value.screens[id]
                ?.let { DockLayout.decodeRoute(it) } ?: return null
        return screenPanelSpec(id, route, accountViewModel).also { screenSpecs[id] = it }
    }

    val layoutFlow: StateFlow<DockLayout> get() = prefs.flow

    val foldedAt: StateFlow<Map<PanelId, Long>> get() = prefs.foldedAt

    /** The saved layout with a place for every panel this platform has. */
    fun layout(): DockLayout = prefs.flow.value.ensure(ids)

    /** Whether the shell can dock panels right now: the window has the room, and nothing else takes it. Set by the shell. */
    var canDock by mutableStateOf(false)
        internal set

    /** The only panels the shell docks right now, where it limits them (a full-width screen); null for all. Set by the shell. */
    var dockOnly by mutableStateOf<Set<PanelId>?>(null)
        internal set

    /** Whether [id] docks right now rather than opening over the screen. */
    fun canDock(id: PanelId): Boolean = canDock && dockOnly?.contains(id) != false

    /** Whether the shell draws the strip; where it doesn't, the rail carries the panels' icons. Set by the shell. */
    var stripShown by mutableStateOf(false)
        internal set

    /** Open panels whose column the window is too narrow for right now: shown, they go over the screen. Set by the shell. */
    var foldedByWidth by mutableStateOf<Set<PanelId>>(emptySet())
        internal set

    /** Whether this platform can give a panel a window of its own. */
    var supportsWindows by mutableStateOf(false)
        internal set

    /** The panel shown over the main screen, if one is. */
    var overlay by mutableStateOf<PanelId?>(null)
        private set

    /** The panel taking the main screen's place until restored, if one is. */
    var maximized by mutableStateOf<PanelId?>(null)
        private set

    /** Bumped to bring a panel's window to the front. */
    val windowRaise = mutableStateMapOf<PanelId, Int>()

    /** Where a panel dragged out of the main window opens its window, in screen pixels. */
    val windowOrigins = mutableMapOf<PanelId, IntOffset>()

    val drag = DockDragState(this)

    /** The main window's content origin on the screen, in pixels, where the platform knows it (the desktop). */
    var windowOrigin: () -> IntOffset? = { null }

    /** [at], a point of the main window, on the screen: where a panel dropped outside the window opens its own. */
    fun screenPointOf(at: Offset): IntOffset? = windowOrigin()?.let { IntOffset(it.x + at.x.toInt(), it.y + at.y.toInt()) }

    private val requests = mutableStateMapOf<PanelId, String>()

    private var hiddenByHideAll: Set<PanelId>? = null

    /** Whether "hide all panels" folded them, so the same command brings them back. */
    var allHidden by mutableStateOf(false)
        private set

    private val movables = mutableMapOf<PanelId, @Composable (DockPanelScope) -> Unit>()

    /** Panel [id]'s content, built once, wherever it is drawn. */
    internal fun movable(id: PanelId): (@Composable (DockPanelScope) -> Unit)? {
        movables[id]?.let { return it }
        val spec = spec(id) ?: return null
        return movableContentOf { scope: DockPanelScope -> PanelHost(this, spec, scope) }.also { movables[id] = it }
    }

    private val viewModelStores = mutableMapOf<PanelId, ViewModelStore>()

    /** Each pinned screen's history, kept here so it outlives the panel's composition: a fold, a window, a dock-back. */
    private val screenStacks = mutableMapOf<PanelId, NavBackStacks>()

    /** Each panel's saved UI state (scroll positions, a half-typed message) from where it was last drawn. */
    private val savedStates = mutableMapOf<PanelId, Map<String, List<Any?>>>()

    /**
     * The places waiting to draw each panel, oldest first: the oldest draws it, the others wait. A
     * panel window and the main window are compositions of their own, so the one taking a panel
     * over waits until the other has let go of it and saved its state.
     */
    private val sites = mutableStateMapOf<PanelId, List<Any>>()

    fun update(transform: (DockLayout) -> DockLayout) {
        prefs.update { transform(it.ensure(ids)) }
        if (allHidden &&
            prefs.flow.value.states.values
                .any { it == PanelState.OPEN }
        ) {
            allHidden = false
            hiddenByHideAll = null
        }
    }

    /** Whether [id] is on screen: docked, over the screen, maximized or in its window. */
    fun isShowing(
        id: PanelId,
        layout: DockLayout = layout(),
    ): Boolean = overlay == id || maximized == id || layout.isInWindow(id) || (canDock(id) && layout.isOpen(id) && !layout.isOverlay(id) && id !in foldedByWidth)

    /** Shows [id]: docked where it was when the dock has the room, else over the screen; a window comes to the front. */
    fun show(id: PanelId) {
        if (!has(id)) return
        val layout = layout()
        when {
            layout.isInWindow(id) -> raise(id)
            canDock(id) && !layout.isOverlay(id) && id !in foldedByWidth -> {
                if (overlay == id) overlay = null
                update { it.open(id) }
            }
            else -> overlay = id
        }
    }

    /** What its strip icon or shortcut does: shows a hidden panel, folds a shown one, raises a window. */
    fun toggle(id: PanelId) {
        val layout = layout()
        when {
            maximized == id -> maximized = null
            overlay == id -> overlay = null
            layout.isInWindow(id) -> raise(id)
            canDock(id) && layout.isOpen(id) && !layout.isOverlay(id) && id !in foldedByWidth -> fold(id)
            else -> show(id)
        }
    }

    fun fold(id: PanelId) {
        if (overlay == id) overlay = null
        if (maximized == id) maximized = null
        update { it.fold(id) }
    }

    /** Closes the panel shown over the screen, if any. */
    fun dismissOverlay() {
        overlay = null
        maximized = null
    }

    /** Gives [id] a window of its own, at [screenOrigin] when it was dragged out of the main window. */
    fun popOut(
        id: PanelId,
        screenOrigin: IntOffset? = null,
    ) {
        if (!supportsWindows) return
        if (overlay == id) overlay = null
        if (maximized == id) maximized = null
        if (screenOrigin != null) windowOrigins[id] = screenOrigin else windowOrigins.remove(id)
        update { it.popOut(id) }
    }

    /** Takes [id] out of its window, back to its place in the dock (over the screen where the dock has no room). */
    fun dockBack(id: PanelId) {
        update { it.dockBack(id) }
        if (!canDock(id)) overlay = id
    }

    /** What closing a panel's window does: it folds, and shows in its window again the next time. */
    fun closeWindow(id: PanelId) {
        update { it.fold(id) }
    }

    fun raise(id: PanelId) {
        windowRaise[id] = (windowRaise[id] ?: 0) + 1
    }

    fun move(
        id: PanelId,
        target: DropTarget,
    ) {
        when (target) {
            DropTarget.Window -> popOut(id)
            DropTarget.Strip -> fold(id)
            else -> {
                if (overlay == id) overlay = null
                if (maximized == id) maximized = null
                update { layout -> layout.move(id, target)?.let { it.copy(overlayMode = it.overlayMode - id) } ?: layout }
            }
        }
    }

    fun setOverlayMode(
        id: PanelId,
        on: Boolean,
    ) {
        val wasShowing = isShowing(id)
        if (overlay == id) overlay = null
        update { it.setOverlayMode(id, on) }
        if (wasShowing) show(id)
    }

    /** Lets [id] take the main screen's place, or gives it back. */
    fun toggleMaximized(id: PanelId) {
        if (maximized == id) {
            maximized = null
            return
        }
        if (overlay == id) overlay = null
        maximized = id
    }

    /** Folds every shown panel, or brings back the ones the last call folded. */
    fun toggleHideAll() {
        val restore = hiddenByHideAll
        if (allHidden && restore != null) {
            allHidden = false
            hiddenByHideAll = null
            prefs.update { it.ensure(ids).openAll(restore) }
        } else {
            val open = layout().states.filterValues { it == PanelState.OPEN }.keys
            dismissOverlay()
            if (open.isEmpty()) return
            hiddenByHideAll = open
            prefs.update { it.ensure(ids).foldAll() }
            allHidden = true
        }
    }

    fun reset() {
        dismissOverlay()
        val pinned = layout().screens.keys
        update { DockLayout().ensure(ids) }
        pinned.forEach(::forgetScreen)
    }

    /**
     * Pins [route] as a panel and shows it; a screen pinned already is shown instead of pinned twice.
     * Returns its panel id.
     */
    fun pinScreen(route: Route): PanelId {
        val json = DockLayout.encodeRoute(route)
        val id =
            layout().screenShowing(json) ?: PanelId.newScreen().also { id ->
                update { it.pinScreen(id, json) }
            }
        // Wherever it is: its window comes to the front, an overlay-mode or narrow-window panel opens over the screen.
        show(id)
        return id
    }

    /** Takes a pinned screen out of the dock, with its ViewModels. */
    fun unpin(id: PanelId) {
        if (!id.isScreen) return
        if (overlay == id) overlay = null
        if (maximized == id) maximized = null
        update { it.unpin(id) }
        forgetScreen(id)
    }

    /**
     * Drops what the dock kept for pinned screen [id]. A panel still on screen lets go on the next
     * frame: its ViewModels are cleared then, in [release], so none is made in a store already cleared.
     */
    private fun forgetScreen(id: PanelId) {
        screenSpecs.remove(id)
        movables.remove(id)
        screenStacks.remove(id)
        savedStates.remove(id)
        if (sites[id].isNullOrEmpty()) viewModelStores.remove(id)?.clear()
    }

    /** Layouts saved by name, to go back to. */
    val savedLayouts: StateFlow<Map<String, DockLayout>> get() = prefs.saved

    /** True while the shell asks for a name to save the layout under. */
    var namingLayout by mutableStateOf(false)

    fun saveLayoutAs(name: String) = prefs.saveAs(name)

    /** Puts a saved layout back, pinned screens and all. */
    fun restoreLayout(name: String) {
        dismissOverlay()
        val before = layout().screens.keys
        prefs.restore(name)
        (before - layout().screens.keys).forEach(::forgetScreen)
    }

    fun forgetLayout(name: String) = prefs.forget(name)

    /** Hands [text] to panel [id] (a question for the assistant) and shows it. */
    fun ask(
        id: PanelId,
        text: String?,
    ) {
        if (!has(id)) return
        if (!text.isNullOrBlank()) requests[id] = text
        show(id)
    }

    /** What was asked of panel [id], once. */
    fun takeRequest(id: PanelId): String? = requests.remove(id)

    /** Whether a request waits for panel [id]: read in composition, so the panel sees it arrive. */
    fun hasRequest(id: PanelId): Boolean = requests.containsKey(id)

    internal fun storeFor(id: PanelId): ViewModelStore = viewModelStores.getOrPut(id) { ViewModelStore() }

    /** Pinned screen [id]'s history, started at [route] the first time it is drawn. */
    internal fun screenStacks(
        id: PanelId,
        route: Route,
    ): NavBackStacks = screenStacks.getOrPut(id) { NavBackStacks(route) }

    internal fun savedState(id: PanelId): Map<String, List<Any?>>? = savedStates[id]

    /** Keeps the state panel [id] leaves with, unless it left because it was taken out of the dock. */
    internal fun keepState(
        id: PanelId,
        state: Map<String, List<Any?>>,
    ) {
        if (has(id)) savedStates[id] = state
    }

    /** Whether [site] may draw panel [id]: no other place holds it, or this one has held it longest. */
    internal fun mayDraw(
        id: PanelId,
        site: Any,
    ): Boolean {
        val waiting = sites[id] ?: return true
        return waiting.isEmpty() || waiting.first() === site
    }

    internal fun claim(
        id: PanelId,
        site: Any,
    ) {
        val waiting = sites[id].orEmpty()
        if (waiting.none { it === site }) sites[id] = waiting + site
    }

    internal fun release(
        id: PanelId,
        site: Any,
    ) {
        val left = sites[id].orEmpty().filterNot { it === site }
        if (left.isNotEmpty()) {
            sites[id] = left
            return
        }
        sites.remove(id)
        // The last place drawing a panel that was unpinned meanwhile: its ViewModels go now.
        if (!has(id)) {
            savedStates.remove(id)
            viewModelStores.remove(id)?.clear()
        }
    }

    internal fun clear() {
        viewModelStores.values.forEach { it.clear() }
        viewModelStores.clear()
    }
}

/** The dock of the shell this composition is in; null outside a logged-in shell. */
val LocalDockController = staticCompositionLocalOf<DockController?> { null }

/**
 * The dock for this account's shell, with every panel's content built once. [panels] is fixed for
 * the shell's life (the platform's panels).
 */
@Composable
fun rememberDockController(
    prefs: DockLayoutPreferences,
    panels: List<DockPanelSpec>,
    supportsWindows: Boolean,
    accountViewModel: AccountViewModel,
): DockController {
    val controller = remember(prefs, panels, accountViewModel) { DockController(prefs, panels, accountViewModel) }
    controller.supportsWindows = supportsWindows
    DisposableEffect(controller) { onDispose { controller.clear() } }
    return controller
}

/**
 * One place that draws a panel: it draws once it holds the panel ([DockController.mayDraw]) and
 * shows nothing for the frame or two the place before it takes to let go.
 */
@Composable
private fun PanelHost(
    controller: DockController,
    spec: DockPanelSpec,
    scope: DockPanelScope,
) {
    val site = remember { Any() }
    DisposableEffect(site) {
        controller.claim(spec.id, site)
        onDispose { controller.release(spec.id, site) }
    }
    if (controller.mayDraw(spec.id, site)) PanelState(controller, spec, scope)
}

/**
 * A panel's own ViewModels and saved state around its content, the way a deck column has its own.
 * The saved state lives in the dock, not in this composition: it is restored where the panel is
 * drawn next (another slot, over the screen, a window of its own) and saved when it leaves.
 */
@Composable
private fun PanelState(
    controller: DockController,
    spec: DockPanelSpec,
    scope: DockPanelScope,
) {
    val owner =
        remember(spec.id) {
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = controller.storeFor(spec.id)
            }
        }
    // Kept in memory only, so any value can be saved.
    val registry = remember(spec.id) { SaveableStateRegistry(controller.savedState(spec.id)) { true } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalSaveableStateRegistry provides registry) {
        spec.content(scope)
    }
    // After the content, so it is let go of first: the content's saved values are still registered.
    DisposableEffect(registry) { onDispose { controller.keepState(spec.id, registry.performSave()) } }
}

/**
 * Draws panel [scope]'s content in the main window: the same movable content wherever the dock
 * puts it (a slot, over the screen, maximized), so it moves with all of its state. Call it in one
 * place at a time.
 */
@Composable
fun DockController.PanelContent(scope: DockPanelScope) {
    val content = movable(scope.id) ?: return
    content(scope)
}

/**
 * Draws panel [scope]'s content in a window of its own. A window is a composition with its own
 * recomposer, so the content cannot move there with its live state; but it is the same movable
 * content object the dock draws, and saved values are keyed by their place under that object: once
 * the main window has let go of the panel (and saved it), the window builds it afresh and finds
 * them (a half-typed message, a scroll position), over the panel's own ViewModels.
 */
@Composable
fun DockController.DetachedPanelContent(scope: DockPanelScope) = PanelContent(scope)
