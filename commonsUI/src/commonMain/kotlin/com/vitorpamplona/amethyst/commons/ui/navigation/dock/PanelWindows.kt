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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.dock.DockView
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.navigation_window_title
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * What the platform needs to draw a panel's window: [id] the panel ([kind] its name, to size it like
 * the last time), its [title], [raise] a number that changes whenever it must come to the front,
 * [origin] where it was dropped when dragged out of the main window (screen pixels), and [onClose],
 * which folds the panel (it opens in its window again the next time it is shown).
 */
@Immutable
data class PanelWindowSpec(
    val id: PanelId,
    val kind: String,
    val title: String,
    val raise: Int?,
    val origin: IntOffset?,
    val onClose: () -> Unit,
)

/**
 * Every panel popped out of the dock, each in a window of its own, declared inside the main window's
 * composition like the screen windows, so they share the account and the theme. A panel keeps its
 * ViewModels on the way out and back (see [DetachedPanelContent]).
 */
@Composable
fun PanelWindows(
    controller: DockController,
    accountViewModel: AccountViewModel,
    nav: INav,
    root: AppRoot,
) {
    if (!controller.supportsWindows) return
    val layout by controller.layoutFlow.collectAsStateWithLifecycle()
    val panelNav = remember(nav) { DockPanelNav(nav) }
    controller.panels.forEach { spec ->
        if (!layout.isInWindow(spec.id)) return@forEach
        key(spec.id) {
            val window =
                PanelWindowSpec(
                    id = spec.id,
                    kind = "panel:" + spec.id.kind,
                    title = stringRes(Res.string.navigation_window_title, stringRes(spec.title)),
                    raise = controller.windowRaise[spec.id],
                    origin = controller.windowOrigins[spec.id],
                    onClose = { controller.closeWindow(spec.id) },
                )
            root.PanelWindow(window) { headerDrag ->
                PanelWindowContent(controller, spec, accountViewModel, panelNav, root, headerDrag)
            }
        }
    }
}

@Composable
private fun PanelWindowContent(
    controller: DockController,
    spec: DockPanelSpec,
    accountViewModel: AccountViewModel,
    panelNav: INav,
    root: AppRoot,
    headerDrag: Modifier,
) {
    val scope = remember(spec.id, accountViewModel, panelNav) { DockPanelScope(spec.id, PanelPlacement.WINDOW, accountViewModel, panelNav, controller) }
    // Laid out by the window's own size, as a screen window is.
    CompositionLocalProvider(LocalScreenLayout provides root.rememberScreenLayoutSpec()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                PanelHeader(controller, listOf(spec.id), spec.id, scope, DockView.Empty, dragModifier = headerDrag)
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) { controller.DetachedPanelContent(scope) }
            }
        }
    }
}
