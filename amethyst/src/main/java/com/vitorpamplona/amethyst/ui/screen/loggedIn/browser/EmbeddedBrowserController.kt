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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.napplethost.NappletBrowserContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSurfaceController

/**
 * Client-side handle to the embedded browser. Binds [NappletBrowserService] (in the keyless `:napplet`
 * process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the remote WebView renders inside
 * the main activity, and relays chrome controls (navigate/reload/back/Tor) while receiving URL updates
 * that drive the trusted, main-process address bar.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedBrowserController(
    private val appContext: Context,
    private val proxyPort: Int,
    private val initialUseTor: Boolean,
) : EmbeddedSurfaceController {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null
    private var pendingAdapter: SandboxedUiAdapter? = null
    private var startUrl: String = "about:blank"

    /** Invoked on the main thread when the page navigates: (url, canGoBack). */
    var onUrlChanged: ((String, Boolean) -> Unit)? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                serviceMessenger = Messenger(service)
                sendCreateSession()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceMessenger = null
            }
        }

    fun bind(startUrl: String) {
        this.startUrl = startUrl
        val intent = Intent().setClassName(appContext, NappletBrowserContract.BROWSER_SERVICE_CLASS)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }

    override fun teardown() = unbind()

    /** Hands the surface view to the controller; applies the adapter if it already arrived. */
    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        pendingAdapter?.let {
            view.setAdapter(it)
            pendingAdapter = null
        }
    }

    private fun sendCreateSession() {
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_CREATE_SESSION).apply {
                replyTo = incoming
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_URL, startUrl)
                        putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort)
                        putBoolean(NappletBrowserContract.KEY_USE_TOR, initialUseTor)
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
    }

    private fun onServiceMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletBrowserContract.MSG_SESSION_READY -> {
                val coreLibInfo = msg.data?.getBundle(NappletBrowserContract.KEY_CORE_LIB_INFO) ?: return true
                val adapter = SandboxedUiAdapterFactory.createFromCoreLibInfo(coreLibInfo)
                val view = sandboxedSdkView
                if (view != null) view.setAdapter(adapter) else pendingAdapter = adapter
            }
            NappletBrowserContract.MSG_URL_CHANGED -> {
                val url = msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()
                val canGoBack = msg.data?.getBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, false) ?: false
                onUrlChanged?.invoke(url, canGoBack)
            }
            else -> return false
        }
        return true
    }

    fun navigate(url: String) = send(NappletBrowserContract.MSG_NAVIGATE) { putString(NappletBrowserContract.KEY_URL, url) }

    fun reload() = send(NappletBrowserContract.MSG_RELOAD) {}

    fun back() = send(NappletBrowserContract.MSG_BACK) {}

    fun setTor(useTor: Boolean) = send(NappletBrowserContract.MSG_SET_TOR) { putBoolean(NappletBrowserContract.KEY_USE_TOR, useTor) }

    private inline fun send(
        what: Int,
        crossinline block: Bundle.() -> Unit,
    ) {
        val msg = Message.obtain(null, what).apply { data = Bundle().apply(block) }
        runCatching { serviceMessenger?.send(msg) }
    }
}
