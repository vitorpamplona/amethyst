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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.theme.AmethystSwitch
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * A top **pull-down sheet** for a running app surface. Collapsed it's just a small grabber centered at
 * the very top edge — out of the corner where a site puts its own avatar/menu. Pull it down (or tap) to
 * reveal the page's controls: route over Tor, reload, "what it can access" (sandboxed apps), and open
 * full screen. The page can't draw over it (the surface is z-ordered below this layer).
 *
 * @param onConsole When non-null, a "Console (N)" row is shown so the user can toggle the log
 *   panel from the pull-down sheet. [consoleCount] is the number of messages already captured.
 */
@Composable
fun TopControlSheet(
    chrome: EmbeddedTabChrome,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    consoleCount: Int = 0,
    consoleShowing: Boolean = false,
    onConsole: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // No tonal elevation. Material 3 only recolors a Surface whose color is EXACTLY
                // colorScheme.surface — it swaps in surfaceColorAtElevation(), which blends surfaceTint
                // (= primary, Amethyst's purple) over the surface. On the light theme that near-white +
                // purple mix reads as a pink/lilac cast instead of the plain background the sheet should
                // have. Keep the drop shadow (shadowElevation) to lift the sheet off the page below it.
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (chrome.isSandbox) MaterialSymbols.Security else MaterialSymbols.Public,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (chrome.isSandbox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            chrome.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                    chrome.torOn?.let { torOn ->
                        SheetSwitchItem(
                            symbol = MaterialSymbols.Lock,
                            label = stringResource(if (torOn) R.string.favorite_app_network_tor else R.string.favorite_app_network_open),
                            checked = torOn,
                            onToggle = { chrome.onToggleTor() },
                        )
                    }
                    SheetItem(MaterialSymbols.Refresh, stringResource(R.string.browser_reload)) {
                        onExpandedChange(false)
                        chrome.onReload()
                    }
                    chrome.onInfo?.let { info ->
                        SheetItem(MaterialSymbols.Info, stringResource(R.string.favorite_app_access_show)) {
                            onExpandedChange(false)
                            info()
                        }
                    }
                    chrome.onPermissions?.let { openPermissions ->
                        SheetItem(MaterialSymbols.Tune, stringResource(R.string.napplet_manage_permissions)) {
                            onExpandedChange(false)
                            openPermissions()
                        }
                    }
                    SheetItem(MaterialSymbols.OpenInFull, stringResource(R.string.favorite_app_open_window)) {
                        onExpandedChange(false)
                        chrome.onOpenFull()
                    }
                    chrome.onFavorite?.let { toggleFavorite ->
                        SheetItem(
                            if (chrome.isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder,
                            stringResource(if (chrome.isFavorite) R.string.favorite_app_remove else R.string.favorite_app_add),
                        ) {
                            onExpandedChange(false)
                            toggleFavorite()
                        }
                    }
                    onConsole?.let { showConsole ->
                        SheetSwitchItem(
                            symbol = MaterialSymbols.Code,
                            label =
                                if (consoleCount > 0) {
                                    stringResource(CommonsR.string.browser_console_title, consoleCount)
                                } else {
                                    stringResource(CommonsR.string.browser_console_title_short)
                                },
                            checked = consoleShowing,
                            onToggle = {
                                onExpandedChange(false)
                                showConsole()
                            },
                        )
                    }
                }
            }
        }

        // The grabber: a small rounded bar centered at the top edge. It is the ONLY touch target the sheet
        // draws — the rest of the top strip stays transparent so page taps pass straight through to the
        // surface below. Pull down to open, up to close; tapping toggles.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
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
                    ).padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            Spacer(
                Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
private fun SheetItem(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SheetSwitchItem(
    symbol: MaterialSymbol,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            // Same row rhythm as [SheetItem] so every entry lines up; the Switch is taller but the
            // padding (the inter-item spacing) is identical.
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AmethystSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}
