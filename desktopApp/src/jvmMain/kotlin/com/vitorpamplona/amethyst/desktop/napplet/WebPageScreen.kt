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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.browser.ui.pill.OnionIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_address_hint
import com.vitorpamplona.amethyst.commons.resources.browser_download_ask
import com.vitorpamplona.amethyst.commons.resources.browser_download_ask_size
import com.vitorpamplona.amethyst.commons.resources.browser_download_done
import com.vitorpamplona.amethyst.commons.resources.browser_download_show_folder
import com.vitorpamplona.amethyst.commons.resources.browser_download_stopped
import com.vitorpamplona.amethyst.commons.resources.browser_downloading
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
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_route
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.material3.Icon as VectorIcon

/**
 * Owns the page of one [Route.WebPage] screen. Scoped to the screen's back-stack entry, so Chromium
 * runs as long as the screen exists, in sight or not (a tab behind another, a screen under the one on
 * top), and closes when the screen does.
 */
internal class WebPageViewModel(
    private val host: DesktopNappletHost,
    route: Route.WebPage,
) : ViewModel() {
    private val opened = host.openBrowser(route)

    private val mutableSession = MutableStateFlow(opened.getOrNull())

    /** The page; replaced when it is started again (a retry, a move to or from Tor). Null when none could start. */
    val session: StateFlow<CefBrowserSession?> = mutableSession.asStateFlow()

    fun retry() {
        val current = mutableSession.value ?: return
        host.reopenBrowser(current)?.let { mutableSession.value = it }
    }

    fun setNetworkMode(useTor: Boolean) {
        val current = mutableSession.value ?: return
        host.setBrowserNetworkMode(current, useTor)?.let { mutableSession.value = it }
    }

    override fun onCleared() {
        mutableSession.value?.let(host::closeBrowser)
    }
}

/**
 * A web page as a screen: Amethyst's toolbar (back, forward, reload, the address, find, zoom, the
 * onion switch and Open in browser) above the page, which Chromium paints off-screen and this draws,
 * so it sits wherever the navigation layout puts the screen. Its consent questions open in windows of
 * their own above the app.
 */
@Composable
fun WebPageScreen(
    route: Route.WebPage,
    nav: INav,
    host: DesktopNappletHost,
) {
    val viewModel = viewModel(key = route.id) { WebPageViewModel(host, route) }
    val session by viewModel.session.collectAsState()
    val current = session
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (current == null) {
            // Nobody to sign as, or not a web page: there is nothing to show here.
            MessagePane(stringRes(Res.string.napplet_desktop_failed_engine), onRetry = { nav.popBack() })
            return@Surface
        }
        WebPage(current, viewModel, nav, host)
    }
}

@Composable
private fun WebPage(
    session: CefBrowserSession,
    viewModel: WebPageViewModel,
    nav: INav,
    host: DesktopNappletHost,
) {
    // Out of sight, the page barely paints.
    DisposableEffect(session) {
        session.setVisible(true)
        onDispose { session.setVisible(false) }
    }
    // A window the page opened is a screen of its own, placed by the layout like any other.
    LaunchedEffect(session, nav) { session.popups.collect { nav.nav(it) } }

    val state by session.state.collectAsState()
    var pageFocus by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        BrowserToolbar(session, host, nav, pageFocus, onNetworkMode = viewModel::setNetworkMode)
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val current = state) {
                is CefBrowserSession.State.Showing ->
                    OffscreenPage(session.view, Modifier.fillMaxSize(), wheelNeedsFocus = false, onFocusChanged = { if (it) pageFocus++ })
                is CefBrowserSession.State.Loading -> LoadingPane(host, null, BrowserChrome.displayHost(session.startUrl))
                is CefBrowserSession.State.WaitingForTor -> WaitingPane(stringRes(Res.string.tor_waiting))
                is CefBrowserSession.State.Failed ->
                    when (current.reason) {
                        CefBrowserSession.Failure.ENGINE -> EngineFailurePane(host.engine, onRetry = viewModel::retry)
                        CefBrowserSession.Failure.ROUTE_BLOCKED ->
                            MessagePane(stringRes(Res.string.napplet_desktop_failed_route, BrowserChrome.displayHost(session.startUrl)), onRetry = viewModel::retry)
                    }
            }
        }
        DownloadsBar(session)
    }

    // The questions this page owns (the last to ask as that site, so a site open twice asks once),
    // each naming the site that asks, which may not be the page on screen any more.
    val owners by host.consentWindows.collectAsState()
    val apps = remember(owners, session) { owners.filterValues { it === session }.keys }
    val page by session.page.collectAsState()
    ConsentWindows(
        apps,
        titleOf = { app -> app?.removePrefix(BROWSER_APP_PREFIX)?.let(BrowserChrome::displayHost) ?: BrowserChrome.displayHost(page.url) },
        host,
    )
}

@Composable
private fun BrowserToolbar(
    session: CefBrowserSession,
    host: DesktopNappletHost,
    nav: INav,
    pageFocus: Int,
    onNetworkMode: (Boolean) -> Unit,
) {
    val page by session.page.collectAsState()
    val state by session.state.collectAsState()
    val routedOverTor by session.routedOverTor.collectAsState()
    val torEnabled by host.torEnabledState.collectAsState()
    var finding by remember { mutableStateOf(false) }
    val showing = state is CefBrowserSession.State.Showing

    // The page is a screen, so it may get a narrow deck column or a phone-sized window: the address
    // keeps its room, and what can go goes first (the Tor caption, then the zoom and Open in browser).
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val roomy = maxWidth >= ROOMY_TOOLBAR
        val narrow = maxWidth < NARROW_TOOLBAR
        Column(Modifier.fillMaxWidth()) {
            Row(
                // Below the window's own controls where they draw over the app (macOS's traffic lights).
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .height(TOOLBAR_HEIGHT)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // The page's history first; past its first page, back leaves the screen where the layout
                // has a screen to go back to (a tab or a window closes instead).
                val canLeave = nav.canPop()
                ToolbarButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.browser_pill_back), enabled = page.canGoBack || canLeave) {
                    if (page.canGoBack) session.goBack() else nav.popBack()
                }
                ToolbarButton(MaterialSymbols.AutoMirrored.ArrowForward, stringRes(Res.string.browser_pill_forward), enabled = page.canGoForward) { session.goForward() }
                if (page.isLoading) {
                    ToolbarButton(MaterialSymbols.Close, stringRes(Res.string.browser_pill_stop), enabled = showing) { session.stop() }
                } else {
                    ToolbarButton(MaterialSymbols.Refresh, stringRes(Res.string.browser_pill_reload), enabled = showing) { session.reload() }
                }

                AddressField(
                    url = page.url,
                    certificateError = page.certificateError,
                    pageFocus = pageFocus,
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
                if (!narrow) {
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
                        if (roomy) {
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
                        }
                        Switch(checked = session.useTor, onCheckedChange = onNetworkMode)
                    }
                }

                if (!narrow) {
                    ToolbarButton(MaterialSymbols.AutoMirrored.OpenInNew, stringRes(Res.string.browser_pill_other_browser), enabled = BrowserChrome.originOf(page.url) != null) {
                        DesktopBrowser.open(page.url)
                    }
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
    pageFocus: Int,
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

    LaunchedEffect(pageFocus) {
        if (pageFocus > 0 && editing) {
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

// Wide enough for the Tor caption beside the switch; narrower than this, the address keeps its room.
private val ROOMY_TOOLBAR = 960.dp

// Below this the toolbar keeps navigation, the address, find and the onion switch.
private val NARROW_TOOLBAR = 620.dp

/**
 * The page's downloads under it, newest last: a question first (nothing is written until Save), then
 * its progress, then where it went. A finished one stays until dismissed.
 */
@Composable
private fun DownloadsBar(session: CefBrowserSession) {
    val downloads by session.downloads.collectAsState()
    if (downloads.isEmpty()) return
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        downloads.forEach { download ->
            key(download.id) {
                HorizontalDivider()
                DownloadRow(
                    download,
                    onSave = { session.saveDownload(download.id) },
                    onCancel = { session.cancelDownload(download.id) },
                    onDismiss = { session.dismissDownload(download.id) },
                    onShowFolder = { folder -> scope.launch(Dispatchers.IO) { BrowserDownloads.showFolder(folder) } },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: BrowserDownload,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShowFolder: (File) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (download.state == BrowserDownload.State.DOWNLOADING) MaterialSymbols.Downloading else MaterialSymbols.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val text =
                when (download.state) {
                    BrowserDownload.State.ASKING ->
                        if (download.totalBytes > 0) {
                            stringRes(Res.string.browser_download_ask_size, download.name, formatFileSize(download.totalBytes), download.host)
                        } else {
                            stringRes(Res.string.browser_download_ask, download.name, download.host)
                        }
                    BrowserDownload.State.DOWNLOADING -> stringRes(Res.string.browser_downloading, download.name)
                    BrowserDownload.State.DONE -> stringRes(Res.string.browser_download_done, download.name)
                    BrowserDownload.State.STOPPED -> stringRes(Res.string.browser_download_stopped, download.name)
                }
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when (download.state) {
                BrowserDownload.State.ASKING -> {
                    TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
                    Button(onClick = onSave) { Text(stringRes(Res.string.save)) }
                }
                BrowserDownload.State.DOWNLOADING -> TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
                BrowserDownload.State.DONE -> {
                    download.file?.parentFile?.let { folder ->
                        TextButton(onClick = { onShowFolder(folder) }) { Text(stringRes(Res.string.browser_download_show_folder)) }
                    }
                    DismissButton(onDismiss)
                }
                BrowserDownload.State.STOPPED -> DismissButton(onDismiss)
            }
        }
        if (download.state == BrowserDownload.State.DOWNLOADING) {
            // A size the server didn't give has no percentage to show.
            if (download.totalBytes > 0) {
                LinearProgressIndicator(progress = { download.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
        Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.dismiss), modifier = Modifier.size(18.dp))
    }
}
