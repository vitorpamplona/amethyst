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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.SectionKind
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_desc
import com.vitorpamplona.amethyst.commons.resources.browser_pill_back_to_app
import com.vitorpamplona.amethyst.commons.resources.browser_pill_close
import com.vitorpamplona.amethyst.commons.resources.browser_pill_decision_allowed
import com.vitorpamplona.amethyst.commons.resources.browser_pill_decision_blocked
import com.vitorpamplona.amethyst.commons.resources.browser_pill_left_site
import com.vitorpamplona.amethyst.commons.resources.browser_pill_permission_state
import com.vitorpamplona.amethyst.commons.resources.browser_pill_privacy
import com.vitorpamplona.amethyst.commons.resources.browser_pill_site_settings_none
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_larger
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_reset
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_smaller
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_value
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * The redesigned browser pill (see `amethyst/plans/2026-09-26-browser-ui-review.md`): a grabber at the
 * top centre that shows the page's state even collapsed, and, pulled down, a Chrome-PWA-style menu —
 * header, navigation capsule, page-action tiles, a privacy card and the console row.
 *
 * Stateless apart from which inline panel (address editor, text size) is open. Which controls appear is
 * [BrowserChrome]'s decision, so this renders web, nsite and napplet surfaces alike.
 */
@Composable
fun BrowserPill(
    ui: BrowserPillUi,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEvent: (BrowserPillEvent) -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = false,
    suggestions: List<AddressSuggestion> = emptyList(),
    clipboardUrl: String? = null,
    initiallyEditing: Boolean = false,
    initiallyTextSizeOpen: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            BrowserPillSheet(
                ui = ui,
                onEvent = { event ->
                    // Everything but in-sheet adjustments (text size) finishes the interaction.
                    if (event !is BrowserPillEvent.TextZoom) onExpandedChange(false)
                    onEvent(event)
                },
                showClose = showClose,
                suggestions = suggestions,
                clipboardUrl = clipboardUrl,
                initiallyEditing = initiallyEditing,
                initiallyTextSizeOpen = initiallyTextSizeOpen,
            )
        }
        PillHandle(ui, expanded, onExpandedChange)
    }
}

/**
 * The collapsed grabber. It tells the page's story before it's opened: the bar takes the error colour on
 * plain HTTP and the Tor accent when onion-routed, a hairline fills while the page loads, and a dot
 * appears when the console has errors.
 */
@Composable
fun PillHandle(
    ui: BrowserPillUi,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor =
        when (ui.security) {
            BrowserChrome.Security.HTTP -> MaterialTheme.colorScheme.error
            BrowserChrome.Security.TOR -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        }
    Box(
        modifier
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable { onExpandedChange(!expanded) }
            .draggable(
                orientation = Orientation.Vertical,
                state =
                    rememberDraggableState { delta ->
                        if (delta > 1f) {
                            onExpandedChange(true)
                        } else if (delta < -1f) {
                            onExpandedChange(false)
                        }
                    },
            ).padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
            val progress = ui.loadProgress
            if (progress != null) {
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(36.dp).height(2.dp).clip(CircleShape),
                    trackColor = Color.Transparent,
                    drawStopIndicator = {},
                )
            }
        }
        if (ui.consoleErrors > 0) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(start = 44.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** The expanded menu (without the grabber), for hosts that place it themselves. */
@Composable
fun BrowserPillSheet(
    ui: BrowserPillUi,
    onEvent: (BrowserPillEvent) -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = false,
    suggestions: List<AddressSuggestion> = emptyList(),
    clipboardUrl: String? = null,
    initiallyEditing: Boolean = false,
    initiallyTextSizeOpen: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    var textSizeOpen by rememberSaveable { mutableStateOf(initiallyTextSizeOpen) }
    val sections = remember(ui.chrome) { BrowserChrome.sections(ui.chrome) }
    val page = sections.firstOrNull { it.kind == SectionKind.PAGE }?.actions.orEmpty()
    val privacy = sections.firstOrNull { it.kind == SectionKind.PRIVACY }?.actions.orEmpty()
    val developer = sections.firstOrNull { it.kind == SectionKind.DEVELOPER }?.actions.orEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PillDefaults.SheetShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        // On the black dark theme a shadow doesn't show; a hairline keeps the sheet's edge off the page.
        border = PillDefaults.hairline(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = PillDefaults.SheetPadding, end = PillDefaults.SheetPadding, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                AddressEditor(
                    initialUrl = ui.chrome.url,
                    security = ui.security,
                    suggestions = suggestions,
                    clipboardUrl = clipboardUrl,
                    onGo = { onEvent(BrowserPillEvent.Navigate(it)) },
                    onCancel = { editing = false },
                )
            } else {
                PillHeader(ui, showClose, onClose = { onEvent(BrowserPillEvent.Close) })
                OriginField(
                    ui = ui,
                    onEdit = if (Action.EDIT_ADDRESS in page) ({ editing = true }) else null,
                    onLongPress = if (!ui.chrome.isSandbox) ({ onEvent(BrowserPillEvent.CopyOrigin) }) else null,
                )
                if (Action.BACK_TO_APP in page) {
                    OutOfScopeBanner(homeHost = BrowserChrome.displayHost(ui.chrome.startUrl)) {
                        onEvent(BrowserPillEvent.Action(Action.BACK_TO_APP))
                    }
                }
                NavigationCapsule(ui, onAction = { onEvent(BrowserPillEvent.Action(it)) })

                val tiles = page.filter { it != Action.BACK_TO_APP && it != Action.EDIT_ADDRESS }
                if (tiles.isNotEmpty()) {
                    TileGrid(
                        actions = tiles,
                        ui = ui,
                        textSizeOpen = textSizeOpen,
                        onAction = { action ->
                            if (action == Action.TEXT_SIZE) textSizeOpen = !textSizeOpen else onEvent(BrowserPillEvent.Action(action))
                        },
                    )
                }
                AnimatedVisibility(visible = textSizeOpen) {
                    TextSizeControl(ui.textZoom) { onEvent(BrowserPillEvent.TextZoom(it)) }
                }
                if (privacy.isNotEmpty()) {
                    PrivacyCard(ui, privacy) { onEvent(BrowserPillEvent.Action(it)) }
                }
                if (Action.CONSOLE in developer) {
                    ConsoleRow(ui) { onEvent(BrowserPillEvent.Action(Action.CONSOLE)) }
                }
            }
        }
    }
}

@Composable
private fun PillHeader(
    ui: BrowserPillUi,
    showClose: Boolean,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SiteMonogram(ui.title.ifBlank { ui.host })
        Spacer(Modifier.width(12.dp))
        Text(
            ui.title.ifBlank { ui.host },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showClose) {
            IconButton(onClick = onClose) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.browser_pill_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Chrome's out-of-scope bar, inside the menu: you're on another site now; one tap goes home. */
@Composable
private fun OutOfScopeBanner(
    homeHost: String,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            stringRes(Res.string.browser_pill_left_site, homeHost),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onBack) { Text(stringRes(Res.string.browser_pill_back_to_app)) }
    }
}

/** Page actions as tiles, four per row; toggles (desktop site, text size) fill while on. */
@Composable
private fun TileGrid(
    actions: List<Action>,
    ui: BrowserPillUi,
    textSizeOpen: Boolean,
    onAction: (Action) -> Unit,
) {
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(columns).forEach { rowActions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    ActionTile(
                        symbol = pillSymbolFor(action) ?: MaterialSymbols.Info,
                        label = stringRes(pillTileLabelFor(action)),
                        description = stringRes(pillLabelFor(action)),
                        onClick = { onAction(action) },
                        selected =
                            when (action) {
                                Action.DESKTOP_SITE -> ui.desktopSite
                                Action.TEXT_SIZE -> textSizeOpen || ui.textZoom != BrowserChrome.DEFAULT_TEXT_ZOOM
                                else -> null
                            },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep the last row's tiles the same width as the others.
                repeat(columns - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Text size: a stepped slider between a small and a large "A", the value, and a way back to 100%. */
@Composable
fun TextSizeControl(
    percent: Int,
    onChange: (Int) -> Unit,
) {
    val steps = BrowserChrome.TEXT_ZOOM_STEPS
    val index = steps.indexOfFirst { it >= percent }.let { if (it < 0) steps.lastIndex else it }
    Surface(shape = PillDefaults.CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val smaller = stringRes(Res.string.browser_pill_text_smaller)
                IconButton(
                    onClick = { onChange(BrowserChrome.stepTextZoom(percent, larger = false)) },
                    modifier = Modifier.semantics { contentDescription = smaller },
                ) {
                    Text("A", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                }
                Slider(
                    value = index.toFloat(),
                    onValueChange = { onChange(steps[it.toInt().coerceIn(0, steps.lastIndex)]) },
                    valueRange = 0f..steps.lastIndex.toFloat(),
                    steps = steps.size - 2,
                    modifier = Modifier.weight(1f),
                )
                val larger = stringRes(Res.string.browser_pill_text_larger)
                IconButton(
                    onClick = { onChange(BrowserChrome.stepTextZoom(percent, larger = true)) },
                    modifier = Modifier.semantics { contentDescription = larger },
                ) {
                    Text("A", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringRes(Res.string.browser_pill_text_value, percent),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                TextButton(onClick = { onChange(BrowserChrome.DEFAULT_TEXT_ZOOM) }, enabled = percent != BrowserChrome.DEFAULT_TEXT_ZOOM) {
                    Text(stringRes(Res.string.browser_pill_text_reset))
                }
            }
        }
    }
}

/**
 * Privacy: Tor with what it means for the site, site settings with a summary of what the site may use,
 * and, for sandboxed apps, what they can access.
 */
@Composable
private fun PrivacyCard(
    ui: BrowserPillUi,
    actions: List<Action>,
    onAction: (Action) -> Unit,
) {
    GroupCard(heading = stringRes(Res.string.browser_pill_privacy)) {
        actions.forEachIndexed { index, action ->
            if (index > 0) GroupDivider()
            when (action) {
                Action.TOR -> {
                    val on = ui.chrome.torOn == true
                    GroupRow(
                        icon = { PillActionIcon(Action.TOR, tint = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) },
                        iconContainer = if (on) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(pillLabelFor(Action.TOR)),
                        supporting = stringRes(if (on) Res.string.browser_pill_tor_on else Res.string.browser_pill_tor_off),
                        onClick = { onAction(Action.TOR) },
                    ) { Switch(checked = on, onCheckedChange = { onAction(Action.TOR) }) }
                }
                Action.SITE_SETTINGS ->
                    GroupRow(
                        icon = { PillActionIcon(Action.SITE_SETTINGS, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) },
                        iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringRes(pillLabelFor(Action.SITE_SETTINGS)),
                        supporting = permissionSummary(ui.sitePermissions),
                        onClick = { onAction(Action.SITE_SETTINGS) },
                    ) { Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Action.ACCESS_INFO ->
                    GroupRow(
                        icon = { PillActionIcon(Action.ACCESS_INFO, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 22.dp, filled = true) },
                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                        title = stringRes(pillLabelFor(Action.ACCESS_INFO)),
                        supporting = stringRes(Res.string.browser_pill_access_desc),
                        onClick = { onAction(Action.ACCESS_INFO) },
                    ) { Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> Unit
            }
        }
    }
}

@Composable
private fun permissionSummary(decisions: Map<BrowserSitePermission, BrowserSitePermission.Decision>): String {
    val answered = BrowserSitePermission.entries.mapNotNull { permission -> decisions[permission]?.takeIf { it != BrowserSitePermission.Decision.ASK }?.let { permission to it } }
    if (answered.isEmpty()) return stringRes(Res.string.browser_pill_site_settings_none)
    return answered
        .map { (permission, decision) ->
            stringRes(
                Res.string.browser_pill_permission_state,
                stringRes(permissionLabel(permission)),
                stringRes(if (decision == BrowserSitePermission.Decision.ALLOW) Res.string.browser_pill_decision_allowed else Res.string.browser_pill_decision_blocked),
            )
        }.joinToString(" · ")
}

/** The developer console: one row, with an error count badge and its on/off switch. */
@Composable
private fun ConsoleRow(
    ui: BrowserPillUi,
    onToggle: () -> Unit,
) {
    Surface(onClick = onToggle, shape = PillDefaults.CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            PillActionIcon(Action.CONSOLE, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringRes(pillLabelFor(Action.CONSOLE)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (ui.consoleErrors > 0) {
                CountBadge(ui.consoleErrors)
                Spacer(Modifier.width(12.dp))
            }
            Switch(checked = ui.consoleShowing, onCheckedChange = { onToggle() })
        }
    }
}
