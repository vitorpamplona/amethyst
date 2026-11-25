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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.CHAT_UNREAD_CAP
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.model.dock.DockLayout
import com.vitorpamplona.amethyst.commons.model.dock.DockView
import com.vitorpamplona.amethyst.commons.model.dock.DropTarget
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_drop_above
import com.vitorpamplona.amethyst.commons.resources.dock_drop_below
import com.vitorpamplona.amethyst.commons.resources.dock_drop_fold
import com.vitorpamplona.amethyst.commons.resources.dock_drop_new_column
import com.vitorpamplona.amethyst.commons.resources.dock_drop_tab
import com.vitorpamplona.amethyst.commons.resources.dock_drop_window
import com.vitorpamplona.amethyst.commons.resources.dock_panel_waiting
import com.vitorpamplona.amethyst.commons.resources.dock_toggle_panel
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.layouts.DockStripWidth
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlin.math.roundToInt

/**
 * The strip at the window's right edge, as an IDE's tool window bar: one icon per panel, lit while
 * the panel shows, badged while it is folded (cards that arrived, an approval waiting). Clicking an
 * icon shows or folds its panel, a right click opens the panel's menu, and dragging an icon drags
 * its panel into the dock. Dropping a panel on the strip folds it. At its foot, [onPinScreen] pins
 * the current screen as a panel and a menu keeps named layouts.
 */
@Composable
internal fun DockStrip(
    controller: DockController,
    panels: List<PanelId>,
    view: DockView,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    onPinScreen: (() -> Unit)? = null,
) {
    val drag = controller.drag
    DisposableEffect(Unit) { onDispose { drag.strip = null } }
    val folds by controller.foldedAt.collectAsStateWithLifecycle()
    val dropping = drag.hit?.target == DropTarget.Strip
    Column(
        modifier
            .width(DockStripWidth)
            .fillMaxHeight()
            .background(if (dropping) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .onGloballyPositioned { drag.strip = it.boundsInRoot() }
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End))
            // The first icon on the line of the panels' headers and the main screen's top bar.
            .padding(top = (PanelHeaderHeight - StripButtonSize) / 2 - 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        panels.forEach { id ->
            val spec = controller.spec(id) ?: return@forEach
            StripIcon(controller, spec, showing = controller.showingIn(id, view), foldedAt = folds[id], view, accountViewModel)
        }
        Spacer(Modifier.weight(1f))
        DockStripFooter(controller, onPinScreen)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StripIcon(
    controller: DockController,
    spec: DockPanelSpec,
    showing: Boolean,
    foldedAt: Long?,
    view: DockView,
    accountViewModel: AccountViewModel,
) {
    val colors = MaterialTheme.colorScheme
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val density = LocalDensity.current
    val badge = if (showing) PanelBadge.None else spec.badge(PanelBadgeScope(accountViewModel, foldedAt))
    val title = spec.label(accountViewModel)
    val label =
        when (badge) {
            PanelBadge.Attention -> stringRes(Res.string.dock_panel_waiting, title)
            else -> stringRes(Res.string.dock_toggle_panel, title)
        }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(badge == PanelBadge.Attention) {
        if (badge == PanelBadge.Attention) {
            repeat(2) {
                flash.animateTo(1f, tween(220))
                flash.animateTo(0f, tween(220))
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        HoverTooltip(label) {
            Box(
                Modifier
                    .padding(vertical = 2.dp)
                    .size(StripButtonSize)
                    .clip(RoundedCornerShape(8.dp))
                    // Quiet by default, as an IDE's tool window bar: a faint wash for the shown panel
                    // and under the pointer, a soft flash when something waits.
                    .background(
                        when {
                            flash.value > 0f -> colors.primary.copy(alpha = 0.22f * flash.value)
                            showing -> colors.onSurface.copy(alpha = 0.08f)
                            hovered -> colors.onSurface.copy(alpha = 0.05f)
                            else -> Color.Transparent
                        },
                    ).hoverable(interaction)
                    .pointerInput(spec.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    val at = event.changes.first().position
                                    menuAt =
                                        with(density) {
                                            IntOffset(
                                                at.x
                                                    .toDp()
                                                    .value
                                                    .roundToInt(),
                                                at.y
                                                    .toDp()
                                                    .value
                                                    .roundToInt(),
                                            )
                                        }
                                }
                            }
                        }
                    }.dockDragSource(controller, spec.id)
                    .clickable(interactionSource = interaction, indication = null) { controller.toggle(spec.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = spec.icon,
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint =
                        when {
                            showing -> colors.onSurface
                            hovered -> colors.onSurface.copy(alpha = 0.85f)
                            else -> colors.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                )
                StripBadge(badge, Modifier.align(Alignment.TopEnd))
            }
        }
        val placement =
            when {
                controller.maximized == spec.id -> PanelPlacement.MAXIMIZED
                controller.overlay == spec.id -> PanelPlacement.OVERLAY
                controller.layout().isInWindow(spec.id) -> PanelPlacement.WINDOW
                else -> PanelPlacement.DOCKED
            }
        PanelMenu(controller, spec.id, placement, view, expanded = menuAt != null, onDismiss = { menuAt = null }, offset = menuAt?.let { IntOffset(it.x - 160, it.y - 40) })
    }
}

/** The size of a strip button; the strip is this plus a little air. */
private val StripButtonSize = 30.dp

/** A strip icon's badge, kept small: a dot when something waits, a compact count of what arrived. */
@Composable
private fun StripBadge(
    badge: PanelBadge,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    when (badge) {
        PanelBadge.None -> {}
        PanelBadge.Attention ->
            Box(modifier.padding(4.dp).size(6.dp).background(colors.primary, CircleShape))
        is PanelBadge.Count ->
            Text(
                text = if (badge.count > CHAT_UNREAD_CAP) "$CHAT_UNREAD_CAP+" else badge.count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                color = colors.onPrimary,
                modifier =
                    modifier
                        .offset(x = 3.dp, y = (-1).dp)
                        .background(colors.primary.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(horizontal = 3.dp),
            )
    }
}

/**
 * The panels' icons in the navigation rail, for a window too narrow for the dock: each opens its
 * panel over the screen.
 */
@Composable
fun DockRailItems(accountViewModel: AccountViewModel) {
    val controller = LocalDockController.current ?: return
    if (controller.stripShown) return
    val layout by controller.layoutFlow.collectAsStateWithLifecycle()
    val folds by controller.foldedAt.collectAsStateWithLifecycle()
    HorizontalDivider(Modifier.width(40.dp).padding(vertical = 4.dp))
    controller.allIds(layout).forEach { id ->
        val spec = controller.spec(id) ?: return@forEach
        val showing = controller.overlay == spec.id || controller.maximized == spec.id || layout.isInWindow(spec.id)
        val badge = if (showing) PanelBadge.None else spec.badge(PanelBadgeScope(accountViewModel, folds[spec.id]))
        val label = stringRes(Res.string.dock_toggle_panel, spec.label(accountViewModel))
        NavigationRailItem(
            selected = showing,
            onClick = { controller.toggle(spec.id) },
            icon = {
                BadgedBox(
                    badge = {
                        when (badge) {
                            is PanelBadge.Count -> Badge { Text(if (badge.count > CHAT_UNREAD_CAP) "$CHAT_UNREAD_CAP+" else badge.count.toString()) }
                            PanelBadge.Attention -> Badge()
                            PanelBadge.None -> {}
                        }
                    },
                ) {
                    Icon(symbol = spec.icon, contentDescription = label)
                }
            },
        )
    }
}

/**
 * The panel shown over the main screen (an Overlay panel, or any panel on a window too narrow for
 * the dock), or the one maximized in the main screen's place. A click on the dimmed screen or Esc
 * puts an overlay away.
 */
@Composable
internal fun DockOverlayLayer(
    controller: DockController,
    layout: DockLayout,
    accountViewModel: AccountViewModel,
    panelNav: INav,
) {
    val id = controller.maximized ?: controller.overlay ?: return
    val spec = controller.spec(id) ?: return
    val placement = if (controller.maximized == id) PanelPlacement.MAXIMIZED else PanelPlacement.OVERLAY
    val scope = remember(id, placement, accountViewModel, panelNav) { DockPanelScope(id, placement, accountViewModel, panelNav, controller) }
    val width = layout.locate(id)?.let { layout.columns[it.column].width } ?: spec.defaultWidth
    Box(Modifier.fillMaxSize()) {
        if (placement == PanelPlacement.OVERLAY) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.dismissOverlay() },
            )
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .then(if (placement == PanelPlacement.MAXIMIZED) Modifier.fillMaxWidth() else Modifier.width(width.dp))
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            if (placement == PanelPlacement.MAXIMIZED) controller.toggleMaximized(id) else controller.dismissOverlay()
                            true
                        } else {
                            false
                        }
                    },
            color = MaterialTheme.colorScheme.background,
            shadowElevation = if (placement == PanelPlacement.OVERLAY) 8.dp else 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                PanelHeader(controller, listOf(id), id, scope, DockView.Empty)
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) { controller.PanelContent(scope) }
            }
        }
    }
}

/**
 * What a drag shows over the whole window: the zone the panel would land in, lit, and the panel's
 * name under the pointer with where it would go.
 */
@Composable
internal fun DockDragOverlay(controller: DockController) {
    val drag = controller.drag
    val id = drag.panel ?: return
    if (drag.fromWindow && drag.hit == null) return
    val spec = controller.spec(id) ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        drag.hit?.highlight?.let { rect ->
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset((rect.left - origin.x).roundToInt(), (rect.top - origin.y).roundToInt()) }
                        .size(rect.width.toDp(), rect.height.toDp())
                        .background(colors.primary.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .border(2.dp, colors.primary, RoundedCornerShape(6.dp)),
                )
            }
        }
        val where =
            when (val target = drag.hit?.target) {
                null -> null
                is DropTarget.Tab -> Res.string.dock_drop_tab
                is DropTarget.Split -> if (target.below) Res.string.dock_drop_below else Res.string.dock_drop_above
                is DropTarget.NewColumn -> Res.string.dock_drop_new_column
                DropTarget.Strip -> Res.string.dock_drop_fold
                DropTarget.Window -> Res.string.dock_drop_window
            }
        Surface(
            modifier = Modifier.offset { IntOffset((drag.pointer.x - origin.x + 14).roundToInt(), (drag.pointer.y - origin.y + 14).roundToInt()) },
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbol = spec.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.primary)
                Text(spec.label(controller.accountViewModel), style = MaterialTheme.typography.labelLarge)
                if (where != null) {
                    Spacer(Modifier.size(2.dp).background(colors.outline, CircleShape))
                    Text(stringRes(where), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.alpha(0.9f))
                }
            }
        }
    }
}
