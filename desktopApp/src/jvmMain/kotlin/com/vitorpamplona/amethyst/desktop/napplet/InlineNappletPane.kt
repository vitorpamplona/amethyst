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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.napplet.NappletInlineDownloads
import com.vitorpamplona.amethyst.commons.ui.note.InlineNappletPosters
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest

/**
 * A napplet running inside a note on the desktop: an off-screen Chromium browser
 * ([OffscreenNappletView]) whose frames are drawn here like an image, so the note clips and layers
 * it with the rest of the feed, which a native window could not do.
 *
 * The page gets the pointer, and the keyboard once clicked. The wheel scrolls the page only after
 * it was clicked, and the feed otherwise. Leaving the composition closes the browser and keeps its
 * last frame as the note's poster.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InlineNappletPane(
    host: DesktopNappletHost,
    manifest: NappletManifest,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
) = InlineNappletPane(host, favoriteCoordinateOf(manifest as Event), instanceId, onStatus, modifier) { host.openInline(manifest, instanceId) }

/**
 * [InlineNappletPane] for any app a note runs: [open] starts its off-screen session, and the last
 * frame is kept as the poster under [posterKey]. A null key keeps none: the front page is private
 * and never shown as a poster, so its frames have no business in the shared cache.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InlineNappletPane(
    host: DesktopNappletHost,
    posterKey: String?,
    instanceId: String,
    onStatus: (InlineNappletStatus) -> Unit,
    modifier: Modifier,
    open: () -> Result<CefNappletSession>,
) {
    val status by rememberUpdatedState(onStatus)
    var session by remember { mutableStateOf<CefNappletSession?>(null) }

    DisposableEffect(host, posterKey, instanceId) {
        val opened = open().getOrNull()
        session = opened
        if (opened == null) status(InlineNappletStatus.FAILED)
        onDispose {
            if (opened != null) {
                val last = opened.offscreen?.keepLastFrame()
                // A session the host already closed belonged to an account that left: its screen is
                // never a poster for the next one.
                if (posterKey != null && last != null && !opened.isClosed) InlineNappletPosters.put(posterKey, last)
                host.closeInline(opened)
            }
            session = null
        }
    }

    val current = session
    val view = current?.offscreen
    if (current == null || view == null) {
        // Without a page, the box still takes the note's taps (modifier swallows them).
        Box(modifier)
        return
    }
    val state by current.state.collectAsState()
    // The note's stage draws the bar; the run is known to it by its instance id.
    LaunchedEffect(current, instanceId) { current.download.collect { NappletInlineDownloads.shared.report(instanceId, it) } }
    // Read only where it is drawn, so a new frame redraws the box instead of recomposing the pane.
    val frame = view.frame.collectAsState()
    val painted by remember(frame) { derivedStateOf { frame.value != null } }

    LaunchedEffect(state, painted) {
        val now = state
        status(
            when {
                // Tor said apart from other failures, so a caller can say why nothing is showing.
                now is CefNappletSession.State.WaitingForTor -> InlineNappletStatus.WAITING_FOR_TOR
                now is CefNappletSession.State.Failed && now.reason == CefNappletSession.Failure.ROUTE_BLOCKED -> InlineNappletStatus.NO_TOR
                now is CefNappletSession.State.Failed -> InlineNappletStatus.FAILED
                now is CefNappletSession.State.Showing && painted -> InlineNappletStatus.SHOWING
                else -> InlineNappletStatus.LOADING
            },
        )
    }

    OffscreenPage(view, modifier, wheelNeedsFocus = true)
}
