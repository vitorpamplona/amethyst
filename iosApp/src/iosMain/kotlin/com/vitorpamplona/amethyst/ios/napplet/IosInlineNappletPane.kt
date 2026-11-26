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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.vitorpamplona.amethyst.commons.napplet.NappletInlineDownloads
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.napplet.ui.inlineStatusOf
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * An app running inside a note on iOS (a napplet, a webxdc app), or the front page: the session's
 * WKWebView in the note, on the same shared core and engine as an app screen, the counterpart of
 * Android's `InlineNappletView` and Desktop's `InlineNappletPane`.
 *
 * In a feed the page gets taps and the feed keeps vertical drags (the interop is cooperative). It is
 * attended while it is composed with the app in front, and leaving the composition closes it. Its
 * JS dialogs are answered "cancel" without showing, as Android's embedded runs do, and it has no file
 * picker.
 */
@Composable
fun IosInlineNappletPane(
    host: IosNappletHost,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    open: () -> Result<IosNappletSession>,
) {
    val status by rememberUpdatedState(onStatus)
    var session by remember { mutableStateOf<IosNappletSession?>(null) }

    DisposableEffect(host, instanceId) {
        val opened = open().getOrNull()
        session = opened
        if (opened == null) status(InlineNappletStatus.FAILED)
        onDispose {
            if (opened != null) host.closeInline(opened)
            session = null
        }
    }

    val current = session
    if (current == null) {
        Box(modifier)
        return
    }
    // Each session's own: a new one (Retry, another edition) must not inherit the last one's state.
    key(current) { SessionPane(current, instanceId, onStatus, modifier, takesTouches = false) }
}

/**
 * [IosInlineNappletPane] for a session something else holds open ([IosHeldPaperSession], the front
 * page's): leaving the composition hides the page instead of closing it, so the reader comes back to
 * it as they left it, scrolled where they were. [session] is the open result: a failure says FAILED.
 */
@Composable
fun IosHeldNappletPane(
    session: Result<IosNappletSession>,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    takesTouches: Boolean,
) {
    val status by rememberUpdatedState(onStatus)
    val current = session.getOrNull()
    if (current == null) {
        LaunchedEffect(session) { status(InlineNappletStatus.FAILED) }
        Box(modifier)
        return
    }
    key(current) { SessionPane(current, instanceId, onStatus, modifier, takesTouches) }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun SessionPane(
    session: IosNappletSession,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    // A page that fills the screen gets every touch at once; inside a feed, the feed's drags come first.
    takesTouches: Boolean,
) {
    val status by rememberUpdatedState(onStatus)
    val state by session.state.collectAsState()
    val painted by session.loaded.collectAsState()
    val webView by session.webView.collectAsState()

    // The note's stage draws the bar; the run is known to it by its instance id.
    LaunchedEffect(instanceId) { session.download.collect { NappletInlineDownloads.shared.report(instanceId, it) } }
    LaunchedEffect(state, painted) { status(inlineStatusOf(state, painted)) }

    // Nobody is looking while the app is in the background: the page stops acting for the user.
    val lifecycle by LocalLifecycleOwner.current.lifecycle
        .currentStateAsState()
    val inFront = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(session, inFront) {
        session.setAttended(inFront)
        onDispose { session.setAttended(false) }
    }

    val view = webView
    if (state !is NappletSessionState.Showing || view == null) {
        Box(modifier)
        return
    }
    UIKitView(
        factory = { view },
        modifier = modifier,
        onRelease = { it.removeFromSuperview() },
        properties =
            UIKitInteropProperties(
                interactionMode = if (takesTouches) UIKitInteropInteractionMode.NonCooperative else UIKitInteropInteractionMode.Cooperative(),
                isNativeAccessibilityEnabled = true,
            ),
    )
}

/**
 * The front page's session, held by its screen's back-stack entry rather than by the composition, as
 * Desktop's `HeldPaperSession`: a tab the reader leaves keeps the page (and its scroll) until they come
 * back. Closed when the entry goes, when another edition or a Retry replaces it, or by the host when
 * the account leaves, after which the next [open] starts a new one. Main thread.
 */
internal class IosHeldPaperSession(
    private val host: IosNappletHost,
) : ViewModel() {
    private class Held(
        val key: String,
        val session: IosNappletSession,
    )

    private var held: Held? = null

    /** The open session for this edition: the held one when it still shows the same page, else a new one. */
    fun open(
        instanceId: String,
        html: String,
        title: String,
    ): Result<IosNappletSession> {
        val key = "$instanceId|$title|${html.length}|${html.hashCode()}"
        held?.let {
            if (it.key == key && !it.session.isClosed) return Result.success(it.session)
            host.closeInline(it.session)
        }
        held = null
        return host.openInlinePaper(html, title, instanceId).onSuccess { held = Held(key, it) }
    }

    override fun onCleared() {
        held?.let { host.closeInline(it.session) }
        held = null
    }
}
