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
package com.vitorpamplona.amethyst.ios.napplet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.session.NappletFailure
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletConsentDialogs
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletFetchingPane
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletMessagePane
import com.vitorpamplona.amethyst.commons.napplet.ui.NappletWaitingPane
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_engine
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_failed_fetch
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.napplet_failed_page_gone
import com.vitorpamplona.amethyst.commons.resources.napplet_failed_route_device
import com.vitorpamplona.amethyst.commons.resources.napplet_js_dialog_ok
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UITextField
import platform.UIKit.UIViewController

/**
 * Owns the napplet of one [Route.NostrApp] screen, scoped to the screen's back-stack entry: it runs
 * for as long as the screen exists, in sight or not (unattended while hidden), and closes with it.
 * The desktop's `NostrAppViewModel`.
 */
internal class IosNostrAppViewModel(
    private val host: IosNappletHost,
    private val route: Route.NostrApp,
) : ViewModel() {
    private val mutableSession = MutableStateFlow<IosNappletSession?>(null)

    /** The app; replaced when it starts again (a retry). Null until it starts. */
    val session: StateFlow<IosNappletSession?> = mutableSession.asStateFlow()

    private val mutableRefused = MutableStateFlow(false)

    /** Its event isn't a napplet this app can run, or nobody is logged in. */
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

    override fun onCleared() {
        mutableSession.value?.let(host::closeScreen)
    }
}

/**
 * A napplet as a screen: the shared top bar above the app's WKWebView, with the shared panes while
 * it loads, waits for Tor or fails, and its consent questions in the shared cards over it. Back steps
 * through the page's history first, then leaves, as Android's host does.
 */
@Composable
fun IosNostrAppScreen(
    route: Route.NostrApp,
    nav: INav,
    host: IosNappletHost,
    accountViewModel: AccountViewModel,
) {
    val viewModel = viewModel(key = route.coordinate) { IosNostrAppViewModel(host, route) }
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

    val colors = MaterialTheme.colorScheme
    LaunchedEffect(colors.background, colors.onBackground, colors.primary) {
        host.updateTheme(NappletThemeColors(colors.background.hex(), colors.onBackground.hex(), colors.primary.hex()))
    }

    val title = session?.launch?.title ?: route.coordinate.substringAfter(':').substringAfter(':')
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopBarWithBackButton(title, nav)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val current = session
                when {
                    current != null -> NostrApp(current, viewModel, nav, host, accountViewModel)
                    note == null || refused -> NappletMessagePane(stringRes(Res.string.napplet_desktop_open_failed), onRetry = null)
                    else -> NappletFetchingPane(null, title)
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun NostrApp(
    session: IosNappletSession,
    viewModel: IosNostrAppViewModel,
    nav: INav,
    host: IosNappletHost,
    accountViewModel: AccountViewModel,
) {
    // Attended while the screen is in the composition and the app is in front: hidden, its requests
    // that act for the user wait, and its dialogs are answered without showing.
    val lifecycle by LocalLifecycleOwner.current.lifecycle
        .currentStateAsState()
    val inFront = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(session, inFront) {
        session.setAttended(inFront)
        onDispose { session.setAttended(false) }
    }

    val ok = stringRes(Res.string.napplet_js_dialog_ok)
    val cancel = stringRes(Res.string.cancel)
    DisposableEffect(session, ok, cancel) {
        session.dialogs = AlertDialogs(session.launch.title, ok, cancel)
        session.onNostrLink = { uri -> uriToRoute(uri, accountViewModel.account)?.let(nav::nav) }
        onDispose { session.dialogs = null }
    }

    val state by session.state.collectAsState()
    val webView by session.webView.collectAsState()
    val title = session.launch.title

    PlatformBackHandler(enabled = state is NappletSessionState.Showing) {
        if (!session.goBack()) nav.popBack()
    }

    when (val current = state) {
        is NappletSessionState.Showing -> {
            val view = webView
            if (view != null) {
                UIKitView(
                    factory = { view },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.removeFromSuperview() },
                    properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
                )
            }
        }
        is NappletSessionState.Loading -> {
            val download by session.download.collectAsState()
            NappletFetchingPane(download, title)
        }
        is NappletSessionState.WaitingForTor -> NappletWaitingPane(stringRes(Res.string.tor_waiting))
        is NappletSessionState.Failed ->
            when (current.reason) {
                NappletFailure.UNREACHABLE -> NappletMessagePane(stringRes(Res.string.napplet_desktop_failed_fetch, title), onRetry = viewModel::retry)
                NappletFailure.ENGINE -> NappletMessagePane(stringRes(Res.string.napplet_desktop_failed_engine), onRetry = viewModel::retry)
                NappletFailure.RENDERER_GONE -> NappletMessagePane(stringRes(Res.string.napplet_failed_page_gone, title), onRetry = viewModel::retry)
                NappletFailure.ROUTE_BLOCKED -> NappletMessagePane(stringRes(Res.string.napplet_failed_route_device, title), onRetry = viewModel::retry)
            }
    }

    // One question at a time, the oldest first, over the app.
    val app = session.launch.identity.coordinate
    NappletConsentDialogs(host.consent, apps = { it == app }, accountViewModel = accountViewModel)
}

/**
 * The page's `alert`, `confirm` and `prompt`, as the system's alerts over the app, titled with the
 * app's name so a page can't pass one off as Amethyst's own. Android's full-screen host shows its
 * WebView's default dialogs the same way.
 */
private class AlertDialogs(
    private val title: String,
    private val ok: String,
    private val cancel: String,
) : NappletJsDialogs {
    override fun alert(
        message: String,
        done: () -> Unit,
    ) = present(message, done, onCancel = null) {}

    override fun confirm(
        message: String,
        done: (Boolean) -> Unit,
    ) = present(message, { done(true) }, onCancel = { done(false) }) {}

    override fun prompt(
        message: String,
        defaultText: String?,
        done: (String?) -> Unit,
    ) {
        var field: UITextField? = null
        present(message, { done(field?.text ?: "") }, onCancel = { done(null) }) { alert ->
            alert.addTextFieldWithConfigurationHandler {
                it?.text = defaultText
                field = it
            }
        }
    }

    private fun present(
        message: String,
        onOk: () -> Unit,
        onCancel: (() -> Unit)?,
        configure: (UIAlertController) -> Unit,
    ) {
        val presenter = topViewController()
        if (presenter == null) {
            if (onCancel != null) onCancel() else onOk()
            return
        }
        val alert = UIAlertController.alertControllerWithTitle(title, message, UIAlertControllerStyleAlert)
        configure(alert)
        if (onCancel != null) alert.addAction(UIAlertAction.actionWithTitle(cancel, UIAlertActionStyleCancel) { onCancel() })
        alert.addAction(UIAlertAction.actionWithTitle(ok, UIAlertActionStyleDefault) { onOk() })
        presenter.presentViewController(alert, animated = true, completion = null)
    }

    @Suppress("DEPRECATION")
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }
}

// The color as the `#RRGGBB` a page's CSS reads.
private fun Color.hex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
