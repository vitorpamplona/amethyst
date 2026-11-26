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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

/**
 * One panel the dock can hold, the way a tool window is declared in an IDE: its [icon] and [title]
 * for the strip and its header, the width a new column of it starts at, and what it draws.
 *
 * - [hidesOn]: screens that already show what the panel shows (the notification feed on the
 *   Notifications screen). There the panel steps out of the dock.
 * - [onTitleClick]: what clicking its name in the header does (open the full screen).
 * - [badge]: what its strip icon carries: a count of what arrived while it was folded, or a dot
 *   when it waits for the user.
 * - [actions]: buttons of its own in the header, before the dock's.
 * - [besideFullWidth]: stays docked on the screens that want the whole width (`FullWidthRoutes`),
 *   where other panels open over the screen: a panel that works beside them (the device panel
 *   beside a coding chat).
 * - [content]: the panel itself, below the header. It keeps its state wherever the dock draws it.
 */
@Immutable
class DockPanelSpec(
    val id: PanelId,
    val icon: MaterialSymbol,
    val title: StringResource,
    val defaultWidth: Float,
    /** The name shown when it is not just [title]: a pinned screen names itself. */
    val name: (@Composable (AccountViewModel) -> String)? = null,
    val hidesOn: Set<KClass<out Route>> = emptySet(),
    val onTitleClick: ((INav) -> Unit)? = null,
    val badge: @Composable (PanelBadgeScope) -> PanelBadge = { PanelBadge.None },
    val actions: @Composable RowScope.(DockPanelScope) -> Unit = {},
    val besideFullWidth: Boolean = false,
    val content: @Composable (DockPanelScope) -> Unit,
)

/** The panel's name as the strip, its header and its window show it. */
@Composable
fun DockPanelSpec.label(accountViewModel: AccountViewModel): String = name?.invoke(accountViewModel) ?: stringRes(title)

/** What a strip icon carries. */
@Immutable
sealed interface PanelBadge {
    data object None : PanelBadge

    /** Something waits for the user (an approval). The strip flashes it once when it appears. */
    data object Attention : PanelBadge

    data class Count(
        val count: Int,
    ) : PanelBadge
}

/** What a badge is computed from: when the panel was folded, in epoch seconds, if it is. */
@Immutable
class PanelBadgeScope(
    val accountViewModel: AccountViewModel,
    val foldedAt: Long?,
)

/** Where a panel is drawn right now. */
enum class PanelPlacement { DOCKED, OVERLAY, MAXIMIZED, WINDOW }

/**
 * What a panel's [DockPanelSpec.content] gets: its account, a navigation that opens screens in the
 * main window ([nav]), the dock, and where it is drawn.
 */
@Stable
class DockPanelScope(
    val id: PanelId,
    val placement: PanelPlacement,
    val accountViewModel: AccountViewModel,
    val nav: INav,
    val dock: DockController,
)
