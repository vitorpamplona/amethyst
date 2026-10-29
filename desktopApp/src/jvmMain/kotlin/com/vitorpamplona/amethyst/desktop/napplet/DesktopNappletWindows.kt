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

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.vitorpamplona.amethyst.commons.browser.ui.pill.AccessInfoSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.OnionIcon
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.oldestAccountGroup
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentCard
import com.vitorpamplona.amethyst.commons.napplet.ui.labelRes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_napplet
import com.vitorpamplona.amethyst.commons.resources.browser_pill_access_nsite
import com.vitorpamplona.amethyst.commons.resources.browser_pill_back
import com.vitorpamplona.amethyst.commons.resources.browser_pill_close
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find_close
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find_hint
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find_next
import com.vitorpamplona.amethyst.commons.resources.browser_pill_find_previous
import com.vitorpamplona.amethyst.commons.resources.browser_pill_forward
import com.vitorpamplona.amethyst.commons.resources.browser_pill_reload
import com.vitorpamplona.amethyst.commons.resources.browser_pill_stop
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_larger
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_smaller
import com.vitorpamplona.amethyst.commons.resources.browser_pill_text_value
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_forced
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_title
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_engine_downloading
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_engine_starting
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_fetch
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_route
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_fetching
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_permission_title
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_retry
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.BatchedConsentCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConnectCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConsentCard
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.platform.AppWindow
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import androidx.compose.material3.Icon as VectorIcon

/**
 * Every open napplet's and nSite's window. Composed inside the main window, so each one gets the
 * app's theme and the same composition locals. Also keeps the napplets' `theme` in step with the
 * app's colors.
 */
@Composable
fun DesktopNappletWindows(host: DesktopNappletHost) {
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(colors.background, colors.onBackground, colors.primary) {
        host.updateTheme(NappletThemeColors(colors.background.hex(), colors.onBackground.hex(), colors.primary.hex()))
    }

    val sessions by host.sessions.collectAsState()
    for (session in sessions) {
        key(session) { NappletWindow(session, host) }
    }
}

@Composable
private fun NappletWindow(
    session: DesktopNappletSession,
    host: DesktopNappletHost,
) {
    val windowState = rememberWindowState(size = DpSize(1024.dp, 760.dp), position = WindowPosition.Aligned(Alignment.Center))
    var showAccess by remember { mutableStateOf(false) }
    AppWindow(
        onCloseRequest = { host.close(session) },
        state = windowState,
        title = session.launch.title,
        icon = IconResources.adaptedBitmapPainter,
    ) {
        val state by session.state.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                // The trusted chrome, drawn by Amethyst above the page, which can't draw over it.
                SandboxToolbar(session, host, onAccessInfo = { showAccess = true })
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val current = state) {
                        // Chromium's own window, embedded: it draws over Compose, so nothing else goes here.
                        is DesktopNappletSession.State.Showing -> SwingPanel(factory = { current.component }, modifier = Modifier.fillMaxSize())
                        is DesktopNappletSession.State.Loading -> LoadingPane(host, session.launch.title)
                        is DesktopNappletSession.State.WaitingForTor -> WaitingPane(stringRes(Res.string.tor_waiting))
                        is DesktopNappletSession.State.Failed ->
                            MessagePane(
                                when (current.reason) {
                                    DesktopNappletSession.Failure.UNREACHABLE -> stringRes(Res.string.napplet_desktop_failed_fetch, session.launch.title)
                                    DesktopNappletSession.Failure.ENGINE -> stringRes(Res.string.napplet_desktop_failed_engine)
                                    DesktopNappletSession.Failure.ROUTE_BLOCKED -> stringRes(Res.string.napplet_desktop_failed_route, session.launch.title)
                                },
                                onRetry = { host.reopen(session) },
                            )
                    }
                }
            }
        }

        // Its own window, above Chromium's: one question at a time, the oldest first.
        ConsentWindows(session, host)

        if (showAccess) AccessWindow(session, host, onDone = { showAccess = false })
    }
}

/**
 * Back, forward, reload, the page's name and posture (which opens what it can access), find, zoom,
 * for an nSite while Tor is on the onion switch that moves it between Tor and the open web, and
 * Close, as Android's pill has.
 */
@Composable
private fun SandboxToolbar(
    session: DesktopNappletSession,
    host: DesktopNappletHost,
    onAccessInfo: () -> Unit,
) {
    val page by session.page.collectAsState()
    val routedOverTor by session.routedOverTor.collectAsState()
    var finding by remember { mutableStateOf(false) }
    val isWebsite = session.launch.profile == HostProfile.WEBSITE

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolbarButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.browser_pill_back), enabled = page.canGoBack) { session.goBack() }
            ToolbarButton(MaterialSymbols.AutoMirrored.ArrowForward, stringRes(Res.string.browser_pill_forward), enabled = page.canGoForward) { session.goForward() }
            val state by session.state.collectAsState()
            val showing = state is DesktopNappletSession.State.Showing
            if (page.isLoading) {
                ToolbarButton(MaterialSymbols.Close, stringRes(Res.string.browser_pill_stop), enabled = showing) { session.stop() }
            } else {
                ToolbarButton(MaterialSymbols.Refresh, stringRes(Res.string.browser_pill_reload), enabled = showing) { session.reload() }
            }

            Row(
                Modifier.weight(1f).clickable(onClick = onAccessInfo).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MaterialSymbols.Shield, contentDescription = stringRes(Res.string.browser_pill_access), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(session.launch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringRes(if (isWebsite) Res.string.browser_pill_access_nsite else Res.string.browser_pill_access_napplet),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            ToolbarButton(MaterialSymbols.Search, stringRes(Res.string.browser_pill_find)) {
                finding = !finding
                if (!finding) session.stopFinding()
            }
            ToolbarButton(MaterialSymbols.Remove, stringRes(Res.string.browser_pill_text_smaller), enabled = page.zoomPercent > DesktopNappletSession.MIN_ZOOM) {
                session.setZoom(page.zoomPercent - ZOOM_STEP)
            }
            Text(
                stringRes(Res.string.browser_pill_text_value, page.zoomPercent),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 40.dp),
                textAlign = TextAlign.Center,
            )
            ToolbarButton(MaterialSymbols.Add, stringRes(Res.string.browser_pill_text_larger), enabled = page.zoomPercent < DesktopNappletSession.MAX_ZOOM) {
                session.setZoom(page.zoomPercent + ZOOM_STEP)
            }

            // Only an nSite has a route of its own to choose, and only while Tor is on.
            if (isWebsite && host.torEnabled) {
                val useTor = session.useTor
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VectorIcon(
                        OnionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (useTor || routedOverTor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        Text(stringRes(Res.string.browser_pill_tor_title), style = MaterialTheme.typography.labelMedium)
                        Text(
                            stringRes(
                                when {
                                    useTor -> Res.string.browser_pill_tor_on
                                    routedOverTor -> Res.string.browser_pill_tor_forced
                                    else -> Res.string.browser_pill_tor_off
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp),
                        )
                    }
                    Switch(checked = useTor, onCheckedChange = { host.setNetworkMode(session, it) })
                }
            }
            TextButton(onClick = { host.close(session) }) { Text(stringRes(Res.string.browser_pill_close)) }
        }
        if (finding) {
            FindBar(session, onClose = {
                finding = false
                session.stopFinding()
            })
        }
    }
}

@Composable
private fun FindBar(
    session: DesktopNappletSession,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Chromium's view holds the keyboard; the field takes it as the bar opens.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                session.find(it)
            },
            placeholder = { Text(stringRes(Res.string.browser_pill_find_hint)) },
            singleLine = true,
            modifier =
                Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter -> {
                            session.find(query, forward = !event.isShiftPressed, findNext = true)
                            true
                        }
                        event.key == Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                },
        )
        ToolbarButton(MaterialSymbols.KeyboardArrowUp, stringRes(Res.string.browser_pill_find_previous), enabled = query.isNotEmpty()) {
            session.find(query, forward = false, findNext = true)
        }
        ToolbarButton(MaterialSymbols.KeyboardArrowDown, stringRes(Res.string.browser_pill_find_next), enabled = query.isNotEmpty()) {
            session.find(query, forward = true, findNext = true)
        }
        ToolbarButton(MaterialSymbols.Close, stringRes(Res.string.browser_pill_find_close), onClick = onClose)
    }
}

@Composable
private fun ToolbarButton(
    symbol: MaterialSymbol,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(symbol, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** What this napplet or site may ask for, in plain language, as Android's pill shows it. */
@Composable
private fun AccessWindow(
    session: DesktopNappletSession,
    host: DesktopNappletHost,
    onDone: () -> Unit,
) {
    val isWebsite = session.launch.profile == HostProfile.WEBSITE
    val capabilities =
        session.launch.declared
            .map { stringRes(it.labelRes()) }
            .sorted()
    DialogWindow(
        onCloseRequest = onDone,
        state = rememberDialogState(size = DpSize(440.dp, 660.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = stringRes(Res.string.browser_pill_access),
        icon = IconResources.adaptedBitmapPainter,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AccessInfoSheet(
                title = session.launch.title,
                isWebsite = isWebsite,
                capabilities = capabilities,
                torOn = if (isWebsite && host.torEnabled) session.useTor else null,
                // The per-app permission screen is Android's for now.
                onManagePermissions = null,
                onDone = onDone,
            )
        }
    }
}

/**
 * The oldest question this window's app is waiting on, of any kind, in a window of its own above
 * Chromium's. Several signatures waiting at once for the same account are shown together, as on
 * Android.
 */
@Composable
private fun ConsentWindows(
    session: DesktopNappletSession,
    host: DesktopNappletHost,
) {
    val coordinate = session.launch.identity.coordinate
    val capabilities by host.consent.capabilities.pending
        .collectAsState()
    val connects by host.consent.connects.pending
        .collectAsState()
    val signerOps by host.consent.signerOps.pending
        .collectAsState()
    val mySignerOps = signerOps.filter { it.app == coordinate }
    val question = (capabilities + connects + signerOps).filter { it.app == coordinate }.minByOrNull { it.order } ?: return
    // Best-effort note preview of what would be signed; without it the card shows the JSON.
    val accountViewModel = CallSessionBridge.accountViewModel

    key(question) {
        ConsentWindow(session.launch.title, onClose = { question.dismiss() }) {
            when (val info = question.info) {
                is NappletConsentInfo -> NappletConsentCard(info, onDecision = { host.consent.capabilities.answer(question.token, it) })
                is SignerConnectInfo ->
                    SignerConnectCard(
                        info = info,
                        onConnect = { host.consent.connects.answer(question.token, AppConnectResult.Connected(it)) },
                        onBlock = { host.consent.connects.answer(question.token, AppConnectResult.Blocked) },
                        onCancel = { host.consent.connects.answer(question.token, AppConnectResult.Cancelled) },
                    )
                is SignerConsentInfo -> {
                    val batch = mySignerOps.oldestAccountGroup()
                    if (batch.size == 1) {
                        SignerConsentCard(info, accountViewModel, onGrant = { host.consent.signerOps.answer(question.token, it) })
                    } else {
                        BatchedConsentCard(
                            pending = batch,
                            accountViewModel = accountViewModel,
                            onResolve = { tokens, grant -> tokens.forEach { host.consent.signerOps.answer(it, grant) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentWindow(
    appTitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(460.dp, 680.dp), position = WindowPosition.Aligned(Alignment.Center)),
        title = stringRes(Res.string.napplet_desktop_permission_title, appTitle),
        icon = IconResources.adaptedBitmapPainter,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}

@Composable
private fun LoadingPane(
    host: DesktopNappletHost,
    title: String,
) {
    val engine by host.engine.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val current = engine) {
                is DesktopCefEngine.State.Downloading -> {
                    LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringRes(Res.string.napplet_desktop_engine_downloading, "${current.percent}%"), textAlign = TextAlign.Center)
                }
                DesktopCefEngine.State.Starting -> {
                    CircularProgressIndicator()
                    Text(stringRes(Res.string.napplet_desktop_engine_starting), textAlign = TextAlign.Center)
                }
                else -> {
                    CircularProgressIndicator()
                    Text(stringRes(Res.string.napplet_desktop_fetching, title), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun WaitingPane(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(message, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MessagePane(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 480.dp))
            Button(onClick = onRetry) { Text(stringRes(Res.string.napplet_desktop_retry)) }
        }
    }
}

private const val ZOOM_STEP = 10

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
