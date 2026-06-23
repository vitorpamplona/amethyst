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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.napplet.WebUrlNetworkRegistry

/**
 * Reusable, chrome-free embedded-browser surface. The page renders in the keyless `:napplet` process
 * and is streamed here (the key-holding main process) as a [SandboxedSdkView] surface — see
 * [EmbeddedBrowserController]. This composable draws **only** the surface; any chrome (an address bar,
 * a back button, a title) is the caller's to add. That's what lets the same surface back a bottom-bar
 * tab, a full-screen single-app activity, and the browser launcher without duplicating the IPC glue.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedBrowserSurface(
    controller: EmbeddedBrowserController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx -> SandboxedSdkView(ctx).also { controller.attachView(it) } },
        modifier = modifier,
    )
}

/**
 * Remembers an [EmbeddedBrowserController] bound to [startUrl] for the lifetime of this composition and
 * unbinds it on dispose. [onUrlChanged] is kept fresh across recompositions so callers can pass a
 * capturing lambda without leaking a stale closure.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun rememberBrowserController(
    startUrl: String,
    onUrlChanged: (url: String, canGoBack: Boolean) -> Unit,
): EmbeddedBrowserController {
    val context = LocalContext.current
    val proxyPort = remember { Amethyst.instance.torManager.activePortOrNull.value ?: -1 }
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    val controller =
        remember {
            // Honor this site's remembered Tor choice on first load (some servers reject Tor exits).
            val initialUseTor = proxyPort > 0 && WebUrlNetworkRegistry.useTor(startUrl)
            EmbeddedBrowserController(context.applicationContext, proxyPort, initialUseTor, backgroundColor)
        }

    // Keep the callback current without re-binding the service.
    SideEffect { controller.onUrlChanged = onUrlChanged }

    DisposableEffect(Unit) {
        controller.bind(startUrl)
        onDispose { controller.unbind() }
    }

    return controller
}
