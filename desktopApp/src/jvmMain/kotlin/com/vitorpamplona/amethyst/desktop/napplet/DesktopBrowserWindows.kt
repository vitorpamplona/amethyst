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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.browser.ui.pill.OnionIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_address_hint
import com.vitorpamplona.amethyst.commons.resources.browser_pill_back
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find
import com.vitorpamplona.amethyst.commons.resources.browser_pill_forward
import com.vitorpamplona.amethyst.commons.resources.browser_pill_other_browser
import com.vitorpamplona.amethyst.commons.resources.browser_pill_reload
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_http
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_https
import com.vitorpamplona.amethyst.commons.resources.browser_pill_security_tor
import com.vitorpamplona.amethyst.commons.resources.browser_pill_stop
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_larger
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_smaller
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_value
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_forced
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_title
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_route
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import com.vitorpamplona.amethyst.desktop.platform.AppWindow
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.amethyst.desktop.platform.applyNativeWindowChrome
import com.vitorpamplona.amethyst.desktop.platform.trafficLightsInsetStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material3.Icon as VectorIcon

/**
 * One in-app browser window: Amethyst's toolbar (back, forward, reload, the address, find, zoom, the
 * onion switch and Open in browser) above Chromium's page, and the window's consent questions in
 * windows of their own above it.
 */
@Composable
internal fun BrowserWindow(
    session: CefBrowserSession,
    host: DesktopNappletHost,
) {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp), position = WindowPosition.Aligned(Alignment.Center))
    val page by session.page.collectAsState()
    val title = page.title.ifBlank { BrowserChrome.displayHost(page.url) }
    AppWindow(
        onCloseRequest = { host.closeBrowser(session) },
        state = windowState,
        title = title,
        icon = IconResources.adaptedBitmapPainter,
    ) {
        frameScope?.applyNativeWindowChrome()

        val state by session.state.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val trafficLights = if (windowState.placement == WindowPlacement.Fullscreen) 0.dp else trafficLightsInsetStart
            Column(Modifier.fillMaxSize()) {
                BrowserToolbar(session, host, leadingInset = trafficLights)
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val current = state) {
                        is CefBrowserSession.State.Showing -> SwingPanel(factory = { current.component }, modifier = Modifier.fillMaxSize())
                        is CefBrowserSession.State.Loading -> LoadingPane(host, null, BrowserChrome.displayHost(session.startUrl))
                        is CefBrowserSession.State.WaitingForTor -> WaitingPane(stringRes(Res.string.tor_waiting))
                        is CefBrowserSession.State.Failed ->
                            MessagePane(
                                when (current.reason) {
                                    CefBrowserSession.Failure.ENGINE -> stringRes(Res.string.napplet_desktop_failed_engine)
                                    CefBrowserSession.Failure.ROUTE_BLOCKED -> stringRes(Res.string.napplet_desktop_failed_route, BrowserChrome.displayHost(session.startUrl))
                                },
                                onRetry = { host.reopenBrowser(session) },
                            )
                    }
                }
            }
        }

        // The questions this window owns (the last to ask as that site, so a site open in two windows
        // asks once), each naming the site that asks, which may not be the page on screen any more.
        val owners by host.consentWindows.collectAsState()
        val apps = remember(owners, session) { owners.filterValues { it === session }.keys }
        ConsentWindows(apps, titleOf = { app -> app?.removePrefix(BROWSER_APP_PREFIX)?.let(BrowserChrome::displayHost) ?: title }, host)
    }
}

@Composable
private fun BrowserToolbar(
    session: CefBrowserSession,
    host: DesktopNappletHost,
    leadingInset: Dp,
) {
    val page by session.page.collectAsState()
    val state by session.state.collectAsState()
    val routedOverTor by session.routedOverTor.collectAsState()
    val torEnabled by host.torEnabledState.collectAsState()
    var finding by remember { mutableStateOf(false) }
    val showing = state is CefBrowserSession.State.Showing

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT).padding(start = 4.dp + leadingInset, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolbarButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.browser_pill_back), enabled = page.canGoBack) { session.goBack() }
            ToolbarButton(MaterialSymbols.AutoMirrored.ArrowForward, stringRes(Res.string.browser_pill_forward), enabled = page.canGoForward) { session.goForward() }
            if (page.isLoading) {
                ToolbarButton(MaterialSymbols.Close, stringRes(Res.string.browser_pill_stop), enabled = showing) { session.stop() }
            } else {
                ToolbarButton(MaterialSymbols.Refresh, stringRes(Res.string.browser_pill_reload), enabled = showing) { session.reload() }
            }

            AddressField(
                url = page.url,
                certificateError = page.certificateError,
                pageFocus = session.pageFocus,
                onPress = session::releaseKeyboard,
                overTor = session.useTor || routedOverTor,
                enabled = showing,
                onGo = { text ->
                    val engine =
                        SearchEngines.byId(
                            CallSessionBridge.accountViewModel
                                ?.settings
                                ?.uiSettingsFlow
                                ?.searchEngine
                                ?.value,
                        )
                    OmniboxInput.resolve(text, engine.queryPrefix)?.url?.also(session::load)
                },
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )

            ToolbarButton(MaterialSymbols.Search, stringRes(Res.string.browser_pill_find), enabled = showing) {
                finding = !finding
                if (!finding) session.stopFinding()
            }
            ToolbarButton(MaterialSymbols.Remove, stringRes(Res.string.browser_pill_text_smaller), enabled = showing && page.zoomPercent > CefNappletSession.MIN_ZOOM) {
                session.setZoom(page.zoomPercent - ZOOM_STEP)
            }
            Text(
                stringRes(Res.string.browser_pill_text_value, page.zoomPercent),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.Center,
            )
            ToolbarButton(MaterialSymbols.Add, stringRes(Res.string.browser_pill_text_larger), enabled = showing && page.zoomPercent < CefNappletSession.MAX_ZOOM) {
                session.setZoom(page.zoomPercent + ZOOM_STEP)
            }

            // The host's route, only while Tor is on: an .onion can't leave Tor.
            if (torEnabled && !OmniboxInput.isOnion(page.url)) {
                Row(
                    Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VectorIcon(
                        OnionIcon,
                        contentDescription = stringRes(Res.string.browser_pill_tor_title),
                        modifier = Modifier.size(18.dp),
                        tint = if (session.useTor || routedOverTor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringRes(
                            when {
                                session.useTor -> Res.string.browser_pill_tor_on
                                routedOverTor -> Res.string.browser_pill_tor_forced
                                else -> Res.string.browser_pill_tor_off
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Switch(checked = session.useTor, onCheckedChange = { host.setBrowserNetworkMode(session, it) })
                }
            }

            ToolbarButton(MaterialSymbols.AutoMirrored.OpenInNew, stringRes(Res.string.browser_pill_other_browser), enabled = BrowserChrome.originOf(page.url) != null) {
                DesktopBrowser.open(page.url)
            }
        }
        if (finding) {
            FindBar(onFind = session::find, onClose = {
                finding = false
                session.stopFinding()
            })
        }
    }
}

/**
 * The page's address, with how it travels (Tor, https or not) in front. Clicked, it turns into a
 * field: Enter goes to what was typed (an address, or a search for it), Esc puts the address back.
 */
@Composable
private fun AddressField(
    url: String,
    certificateError: Boolean,
    /** Ticks when the page takes the keyboard: the field gives up and shows the address again. */
    pageFocus: StateFlow<Int>,
    /** A press on the field, before Compose handles it: the page lets go of the keyboard. */
    onPress: () -> Unit,
    overTor: Boolean,
    enabled: Boolean,
    /** Goes to what was typed; returns the address it went to, or null when it went nowhere. */
    onGo: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    // Set as a click starts editing: the click's own caret placement (on press, again on release)
    // keeps the whole address selected instead, until the first edit or a moment later.
    var selectAllPending by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(url)) }
    // A new page shows its address, unless the user is typing over it.
    LaunchedEffect(url) { if (!editing) field = TextFieldValue(url) }

    val pageFocusTicks by pageFocus.collectAsState()
    LaunchedEffect(pageFocusTicks) {
        if (pageFocusTicks > 0 && editing) {
            editing = false
            field = TextFieldValue(url)
            focusManager.clearFocus()
        }
    }

    // Selected whole, so typing replaces it, as in any browser.
    fun startEditing() {
        editing = true
        field = field.copy(selection = TextRange(0, field.text.length))
    }
    LaunchedEffect(selectAllPending) {
        if (selectAllPending) {
            delay(SELECT_ALL_HOLD_MS)
            selectAllPending = false
        }
    }

    val secure = !certificateError && (url.startsWith("https://", ignoreCase = true) || OmniboxInput.isOnion(url))
    val (symbol, label) =
        when {
            certificateError -> MaterialSymbols.Warning to Res.string.browser_pill_security_http
            overTor -> MaterialSymbols.Lock to Res.string.browser_pill_security_tor
            secure -> MaterialSymbols.Lock to Res.string.browser_pill_security_https
            else -> MaterialSymbols.Warning to Res.string.browser_pill_security_http
        }
    val colors = MaterialTheme.colorScheme
    Surface(modifier.height(36.dp), shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(symbol, contentDescription = stringRes(label), modifier = Modifier.size(16.dp), tint = if (!certificateError && (secure || overTor)) colors.primary else colors.error)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = field,
                    onValueChange = { next ->
                        val placingCaret = selectAllPending && next.text == field.text
                        field = if (placingCaret) next.copy(selection = TextRange(0, next.text.length)) else next
                        if (!placingCaret) selectAllPending = false
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // A click on the address while it shows the page's (Enter went, or the field
                            // never had focus) selects it whole, so typing replaces it, as in any
                            // browser. Watched on the press itself: after Enter the field keeps focus,
                            // since Chromium's view never takes it, so a focus change can't tell.
                            // A click on the address while it shows the page's selects it whole. Watched
                            // on the press itself: after Enter the field keeps Compose's focus, so a focus
                            // change can't tell. Only marked here; changing the selection mid-press would
                            // turn the field's tap into a drag.
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type == PointerEventType.Press) {
                                            onPress()
                                            if (!editing) {
                                                editing = true
                                                selectAllPending = true
                                            }
                                        }
                                    }
                                }
                            }.onFocusChanged {
                                if (it.isFocused && !editing) {
                                    startEditing()
                                } else if (!it.isFocused && editing) {
                                    editing = false
                                    field = TextFieldValue(url)
                                }
                            }.onPreviewKeyEvent { event ->
                                when {
                                    event.type != KeyEventType.KeyDown -> false
                                    event.key == Key.Enter -> {
                                        // Done editing: the field follows the page again from here.
                                        editing = false
                                        focusManager.clearFocus()
                                        // The address it resolved to, until the page reports its own.
                                        onGo(field.text)?.let { field = TextFieldValue(it) }
                                        true
                                    }
                                    event.key == Key.Escape -> {
                                        field = TextFieldValue(url, TextRange(0, url.length))
                                        true
                                    }
                                    else -> false
                                }
                            },
                )
                if (field.text.isEmpty()) {
                    Text(stringRes(Res.string.browser_address_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

private const val BROWSER_APP_PREFIX = CefBrowserSession.BROWSER_AUTHOR + ":"

// How long after a click starts editing its own caret placement is undone.
private const val SELECT_ALL_HOLD_MS = 500L
