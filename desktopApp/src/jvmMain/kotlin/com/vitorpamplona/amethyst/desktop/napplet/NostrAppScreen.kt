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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_fetch
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_route
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the app of one [Route.NostrApp] screen (an app opened from a card or a favorite, or one pinned
 * to the bar). Scoped to the screen's back-stack entry, so the
 * napplet or nSite runs as long as the screen exists, in sight or not, and closes when it does.
 */
internal class NostrAppViewModel(
    private val host: DesktopNappletHost,
    private val route: Route.NostrApp,
) : ViewModel() {
    private val mutableSession = MutableStateFlow<CefNappletSession?>(null)

    /** The app; replaced when it starts again (a retry, a move to or from Tor). Null until it starts. */
    val session: StateFlow<CefNappletSession?> = mutableSession.asStateFlow()

    private val mutableRefused = MutableStateFlow(false)

    /** Its event isn't a napplet or nSite this desktop can run, or nobody is logged in. */
    val refused: StateFlow<Boolean> = mutableRefused.asStateFlow()

    /** Starts the app once its event is known; later calls (a newer version of the event) change nothing. */
    fun start(event: Event) {
        if (mutableSession.value != null || mutableRefused.value) return
        host
            .openScreen(event, route.coordinate)
            .onSuccess { mutableSession.value = it }
            .onFailure { mutableRefused.value = true }
    }

    fun retry() {
        val current = mutableSession.value ?: return
        host.reopenScreen(current)?.let { mutableSession.value = it }
    }

    fun setNetworkMode(useTor: Boolean) {
        val current = mutableSession.value ?: return
        host.setScreenNetworkMode(current, useTor)?.let { mutableSession.value = it }
    }

    override fun onCleared() {
        mutableSession.value?.let(host::closeScreen)
    }
}

/**
 * A napplet or an nSite as a screen: the sandbox toolbar (back, forward, reload, what it can access,
 * find, zoom, the onion switch for a site) above the app, which Chromium paints off-screen and this
 * draws, so it sits wherever the navigation layout puts the screen. Its consent questions open in
 * windows of their own above the app. A screen restored before its event arrived waits for it.
 */
@Composable
fun NostrAppScreen(
    route: Route.NostrApp,
    nav: INav,
    host: DesktopNappletHost,
) {
    val viewModel = viewModel(key = route.coordinate) { NostrAppViewModel(host, route) }
    val session by viewModel.session.collectAsState()
    val refused by viewModel.refused.collectAsState()

    // The app's event, from the cache, or fetched from relays while the screen waits for it.
    val note = remember(route.coordinate) { LocalCache.checkGetOrCreateAddressableNote(route.coordinate) }
    if (note != null && session == null && !refused) {
        if (note.event == null) EventFinderFilterAssemblerSubscription(note)
        val state by note
            .flow()
            .metadata.stateFlow
            .collectAsState()
        LaunchedEffect(state.note.event) { state.note.event?.let(viewModel::start) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val current = session
        when {
            current != null -> NostrApp(current, viewModel, nav, host)
            note == null || refused -> MessagePane(stringRes(Res.string.napplet_desktop_open_failed), onRetry = { nav.popBack() })
            else -> LoadingPane(host, null, route.coordinate.substringAfter(':').substringAfter(':'))
        }
    }
}

@Composable
private fun NostrApp(
    session: CefNappletSession,
    viewModel: NostrAppViewModel,
    nav: INav,
    host: DesktopNappletHost,
) {
    val view = session.offscreen ?: return
    // Out of sight, the app barely paints.
    DisposableEffect(session) {
        session.setVisible(true)
        onDispose { session.setVisible(false) }
    }

    var showAccess by remember { mutableStateOf(false) }
    val state by session.state.collectAsState()
    val title = session.launch.title
    Column(Modifier.fillMaxSize()) {
        SandboxToolbar(
            session,
            host,
            // Below the window's own controls where they draw over the app (macOS's traffic lights).
            Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets),
            onAccessInfo = { showAccess = true },
            onNetworkMode = viewModel::setNetworkMode,
            onLeave = if (nav.canPop()) ({ nav.popBack() }) else null,
        )
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val current = state) {
                is CefNappletSession.State.Showing -> OffscreenPage(view, Modifier.fillMaxSize(), wheelNeedsFocus = false)
                is CefNappletSession.State.Loading -> {
                    val download by session.download.collectAsState()
                    LoadingPane(host, download, title)
                }
                is CefNappletSession.State.WaitingForTor -> WaitingPane(stringRes(Res.string.tor_waiting))
                is CefNappletSession.State.Failed ->
                    MessagePane(
                        when (current.reason) {
                            CefNappletSession.Failure.UNREACHABLE -> stringRes(Res.string.napplet_desktop_failed_fetch, title)
                            CefNappletSession.Failure.ENGINE -> stringRes(Res.string.napplet_desktop_failed_engine)
                            CefNappletSession.Failure.ROUTE_BLOCKED -> stringRes(Res.string.napplet_desktop_failed_route, title)
                        },
                        onRetry = viewModel::retry,
                    )
            }
        }
    }

    // Its own windows, above the app's: one question at a time, the oldest first.
    ConsentWindows(setOf(session.launch.identity.coordinate), titleOf = { title }, host)
    if (showAccess) AccessWindow(session, host, parent = null, onDone = { showAccess = false })
}
