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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import androidx.privacysandbox.ui.core.SessionData
import androidx.privacysandbox.ui.provider.AbstractSandboxedUiAdapter
import java.util.concurrent.Executor

/**
 * Exposes the verified-blob napplet/nsite WebView (built by [NappletHostService]) as a
 * `SandboxedUiAdapter`. The `androidx.privacysandbox.ui` machinery wraps the returned view in a
 * SurfaceControlViewHost and ships its surface to the main app's `SandboxedSdkView`; only pixels +
 * input cross the process boundary. Mirror of [NappletBrowserUiAdapter] for the embedded-tab host.
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletHostUiAdapter(
    private val service: NappletHostService,
    private val sessionId: String,
) : AbstractSandboxedUiAdapter() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun openSession(
        context: Context,
        sessionData: SessionData,
        initialWidth: Int,
        initialHeight: Int,
        isZOrderOnTop: Boolean,
        clientExecutor: Executor,
        client: SandboxedUiAdapter.SessionClient,
    ) {
        // WebView creation must run on the main thread; openSession is called on a binder thread.
        mainHandler.post {
            runCatching {
                val webView = service.createHostWebView(context, sessionId)
                // FrameLayout.LayoutParams (a MarginLayoutParams) — the SurfaceControlViewHost container
                // measures children with measureChildWithMargins, which casts to MarginLayoutParams.
                webView.layoutParams = FrameLayout.LayoutParams(initialWidth, initialHeight)
                HostSession(sessionId, webView, service)
            }.onSuccess { session -> clientExecutor.execute { client.onSessionOpened(session) } }
                .onFailure { t -> clientExecutor.execute { client.onSessionError(t) } }
        }
    }
}

/** A single embedded napplet/nsite session: the WebView is the rendered view; close tears it down. */
@RequiresApi(Build.VERSION_CODES.R)
private class HostSession(
    private val sessionId: String,
    private val webView: WebView,
    private val service: NappletHostService,
) : SandboxedUiAdapter.Session {
    override val view: View get() = webView

    override val signalOptions: Set<String> = emptySet()

    override fun notifySessionRendered(supportedSignalOptions: Set<String>) {
        // No-op: signalOptions is empty, so there are no supported signals to report back on.
    }

    override fun notifyResized(
        width: Int,
        height: Int,
    ) {
        webView.layoutParams = FrameLayout.LayoutParams(width, height)
        webView.requestLayout()
    }

    override fun notifyZOrderChanged(isZOrderOnTop: Boolean) {
        // No-op: the WebView's z-order within the SurfaceControlViewHost surface is fixed.
    }

    override fun notifyConfigurationChanged(configuration: Configuration) {
        // No-op: the WebView handles configuration changes itself; the session needs no extra action.
    }

    override fun notifyUiChanged(uiContainerInfo: Bundle) {
        // No-op: no host-side reaction is needed to UI-container geometry updates.
    }

    override fun close() {
        // The library may call close() off the main thread; WebView.destroy() (and the tabs mutation)
        // must run on the main thread.
        Handler(Looper.getMainLooper()).post { service.onSessionClosed(sessionId) }
    }
}
