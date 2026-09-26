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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.SectionKind
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.napplethost.BrowserChromeLabels
import com.vitorpamplona.amethyst.ui.stringRes
import androidx.compose.material3.Icon as Material3Icon
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * A top **pull-down pill** for a running app surface. Collapsed it's just a small grabber centered at the
 * very top edge — out of the corner where a site puts its own avatar/menu. Pulled down (or tapped) it shows a
 * Chrome-PWA-style app menu: the page title with its origin and connection badge (tap for page info), the
 * icon row (back · forward · reload/stop · star · share), then the menu rows and the Privacy and Developer
 * groups. The page can't draw over it (the surface is z-ordered below this layer).
 *
 * The layout comes from [BrowserChrome] and the icons/labels from [BrowserChromeLabels] — both shared with
 * the full-screen browser's native sheet — so an action looks and sits the same in either.
 *
 * @param onConsole When non-null, CONSOLE toggles the log panel through it ([consoleCount] messages so far).
 * @param onFind When non-null, FIND_IN_PAGE opens the find bar through it.
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
    onFind: (() -> Unit)? = null,
) {
    val state = remember(chrome.state, onConsole) { if (onConsole == null) chrome.state.copy(hasConsole = false) else chrome.state }
    var editingAddress by remember(expanded) { mutableStateOf(false) }

    fun act(action: Action) {
        when (action) {
            Action.CONSOLE -> onConsole?.invoke()
            Action.FIND_IN_PAGE -> onFind?.invoke()
            else -> chrome.onAction(action)
        }
    }

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
                    SheetHeader(
                        chrome = chrome,
                        state = state,
                        editingAddress = editingAddress,
                        onOriginTap = {
                            onExpandedChange(false)
                            chrome.onOriginTap()
                        },
                        onCopy = { chrome.onAction(Action.COPY_LINK) },
                        onNavigate = { text ->
                            editingAddress = false
                            onExpandedChange(false)
                            chrome.onNavigate(text)
                        },
                    )
                    IconActionRow(state, chrome.isFavorite) { action ->
                        onExpandedChange(false)
                        act(action)
                    }
                    HorizontalDivider()
                    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
                    Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                        BrowserChrome.sections(state).forEachIndexed { index, section ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                            sectionTitle(section.kind)?.let {
                                Text(
                                    it.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
                                )
                            }
                            section.actions.forEach { action ->
                                when {
                                    action == Action.TEXT_SIZE -> TextSizeRow(chrome.textZoom, chrome.onTextZoom)
                                    BrowserChromeLabels.isToggle(action) ->
                                        SheetSwitchItem(
                                            action = action,
                                            label = rowLabel(action, state, chrome.isFavorite, consoleCount),
                                            checked =
                                                when (action) {
                                                    Action.TOR -> state.torOn == true
                                                    Action.DESKTOP_SITE -> chrome.desktopSite
                                                    else -> consoleShowing
                                                },
                                        ) {
                                            onExpandedChange(false)
                                            act(action)
                                        }
                                    else ->
                                        SheetItem(action, rowLabel(action, state, chrome.isFavorite, consoleCount)) {
                                            if (action == Action.EDIT_ADDRESS) {
                                                editingAddress = true
                                            } else {
                                                onExpandedChange(false)
                                                act(action)
                                            }
                                        }
                                }
                            }
                        }
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
private fun sectionTitle(kind: SectionKind): String? =
    when (kind) {
        SectionKind.PAGE -> null
        SectionKind.PRIVACY -> stringRes(CommonsR.string.browser_section_privacy)
        SectionKind.DEVELOPER -> stringRes(CommonsR.string.browser_section_developer)
    }

@Composable
private fun rowLabel(
    action: Action,
    state: BrowserChrome.State,
    isFavorite: Boolean,
    consoleCount: Int,
): String =
    when {
        action == Action.BACK_TO_APP -> stringRes(CommonsR.string.browser_action_back_to_app, BrowserChrome.displayHost(state.startUrl))
        action == Action.CONSOLE && consoleCount > 0 -> stringRes(CommonsR.string.browser_console_title, consoleCount)
        else -> stringRes(BrowserChromeLabels.labelFor(action, isFavorite = isFavorite, torOn = state.torOn == true))
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetHeader(
    chrome: EmbeddedTabChrome,
    state: BrowserChrome.State,
    editingAddress: Boolean,
    onOriginTap: () -> Unit,
    onCopy: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val security = BrowserChrome.security(state)
    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolOrDrawable(
            BrowserChromeLabels.securitySymbol(security),
            BrowserChromeLabels.securityDrawable(security),
            tint = if (state.isSandbox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 20,
        )
        Spacer(Modifier.width(12.dp))
        if (editingAddress) {
            AddressField(state.url, onNavigate, Modifier.weight(1f))
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = onOriginTap, onLongClick = if (state.isSandbox) null else onCopy),
            ) {
                Text(chrome.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val securityLabel = stringRes(BrowserChromeLabels.securityLabel(security))
                Text(
                    if (state.isSandbox) securityLabel else BrowserChrome.displayHost(state.url) + "  ·  " + securityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The rarely used editable address, swapped in for the origin chip by "Edit address". */
@Composable
private fun AddressField(
    url: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(url, TextRange(0, url.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = field,
        onValueChange = { field = it },
        modifier = modifier.focusRequester(focus),
        singleLine = true,
        placeholder = { Text(stringRes(CommonsR.string.browser_address_hint)) },
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
        keyboardActions =
            KeyboardActions(onGo = {
                field.text
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(onNavigate)
            }),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun IconActionRow(
    state: BrowserChrome.State,
    isFavorite: Boolean,
    onAction: (Action) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        BrowserChrome.iconRow(state).forEach { action ->
            val enabled = BrowserChrome.isEnabled(state, action)
            val favorite = action == Action.FAVORITE && isFavorite
            IconButton(onClick = { onAction(action) }, enabled = enabled) {
                Icon(
                    BrowserChromeLabels.symbolFor(action)!!,
                    contentDescription = stringRes(BrowserChromeLabels.labelFor(action, isFavorite = isFavorite)),
                    modifier = Modifier.size(24.dp),
                    tint =
                        when {
                            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            favorite -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    filled = favorite,
                )
            }
        }
    }
}

@Composable
private fun SymbolOrDrawable(
    symbol: MaterialSymbol?,
    drawable: Int?,
    tint: Color,
    size: Int,
) {
    if (drawable != null) {
        Material3Icon(painterResource(drawable), contentDescription = null, modifier = Modifier.size(size.dp), tint = tint)
    } else if (symbol != null) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(size.dp), tint = tint)
    }
}

@Composable
private fun SheetItem(
    action: Action,
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
        SymbolOrDrawable(BrowserChromeLabels.symbolFor(action), BrowserChromeLabels.drawableFor(action), MaterialTheme.colorScheme.onSurfaceVariant, 22)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SheetSwitchItem(
    action: Action,
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
        SymbolOrDrawable(BrowserChromeLabels.symbolFor(action), BrowserChromeLabels.drawableFor(action), MaterialTheme.colorScheme.onSurfaceVariant, 22)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TextSizeRow(
    textZoom: Int,
    onTextZoom: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.FormatSize, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(stringRes(CommonsR.string.browser_action_text_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onTextZoom(BrowserChrome.stepTextZoom(textZoom, larger = false)) }) {
            Icon(MaterialSymbols.Remove, contentDescription = stringRes(CommonsR.string.browser_action_text_smaller))
        }
        Text(
            stringRes(CommonsR.string.browser_action_text_size_value, textZoom),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        IconButton(onClick = { onTextZoom(BrowserChrome.stepTextZoom(textZoom, larger = true)) }) {
            Icon(MaterialSymbols.Add, contentDescription = stringRes(CommonsR.string.browser_action_text_larger))
        }
    }
}
