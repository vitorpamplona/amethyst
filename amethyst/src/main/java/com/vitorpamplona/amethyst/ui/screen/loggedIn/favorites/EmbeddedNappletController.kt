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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites

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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkUiSessionState
import androidx.privacysandbox.ui.client.view.SandboxedSdkUiSessionStateChangedListener
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.napplethost.NappletEmbedContract
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSurfaceController

/**
 * Client-side handle to an embedded nsite/napplet. Binds [NappletHostService][com.vitorpamplona.amethyst.napplethost.NappletHostService]
 * (in the keyless `:napplet` process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the
 * verified-blob WebView renders inside the main activity, and relays back/reload while receiving
 * navigation state + "allow always" notices that the trusted main-process chrome reflects.
 *
 * [params] is the bundle minted in the main process by
 * [NappletLauncher.buildLaunchParams][com.vitorpamplona.amethyst.napplet.NappletLauncher.buildLaunchParams]
 * — the verified manifest, identity, and launch token. The mirror of `EmbeddedBrowserController`.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedNappletController(
    private val appContext: Context,
    private val params: Bundle,
) : EmbeddedSurfaceController {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null
    private var pendingAdapter: SandboxedUiAdapter? = null

    private val readyState = mutableStateOf(false)
    override val ready: State<Boolean> = readyState

    /** (canGoBack) — drives the in-tab back gesture. */
    var onStateChanged: ((Boolean) -> Unit)? = null

    /** A granted "allow always" sensitive op just ran (one of NappletEmbedContract.NOTICE_*). */
    var onNotice: ((String) -> Unit)? = null

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

    fun bind() {
        val intent = Intent().setClassName(appContext, NappletEmbedContract.SERVICE_CLASS)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }

    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        // Paint the surface placeholder in the app's theme background so there's no white flash before
        // the remote WebView delivers its first frame.
        view.setBackgroundColor(params.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE))
        // Flip ready once the session opens, so callers can drop their loading placeholder.
        view.addStateChangedListener(
            object : SandboxedSdkUiSessionStateChangedListener {
                override fun onStateChanged(state: SandboxedSdkUiSessionState) {
                    if (state is SandboxedSdkUiSessionState.Active) readyState.value = true
                }
            },
        )
        pendingAdapter?.let {
            view.setAdapter(it)
            pendingAdapter = null
        }
    }

    override fun onShown() = resume()

    override fun onHidden() = pause()

    override fun teardown() = unbind()

    private fun sendCreateSession() {
        val msg =
            Message.obtain(null, NappletEmbedContract.MSG_CREATE_SESSION).apply {
                replyTo = incoming
                data = Bundle(params)
            }
        runCatching { serviceMessenger?.send(msg) }
    }

    private fun onServiceMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletEmbedContract.MSG_SESSION_READY -> {
                val coreLibInfo = msg.data?.getBundle(NappletEmbedContract.KEY_CORE_LIB_INFO) ?: return true
                val adapter = SandboxedUiAdapterFactory.createFromCoreLibInfo(coreLibInfo)
                val view = sandboxedSdkView
                if (view != null) view.setAdapter(adapter) else pendingAdapter = adapter
            }
            NappletEmbedContract.MSG_STATE -> {
                val canGoBack = msg.data?.getBoolean(NappletEmbedContract.KEY_CAN_GO_BACK, false) ?: false
                onStateChanged?.invoke(canGoBack)
            }
            NappletEmbedContract.MSG_NOTICE -> {
                val notice = msg.data?.getString(NappletEmbedContract.KEY_NOTICE) ?: return true
                onNotice?.invoke(notice)
            }
            else -> return false
        }
        return true
    }

    fun back() = send(NappletEmbedContract.MSG_BACK)

    fun reload() = send(NappletEmbedContract.MSG_RELOAD)

    /** Pause/resume the applet's JS when the tab leaves/returns to the foreground (background gating). */
    fun pause() = send(NappletEmbedContract.MSG_PAUSE)

    fun resume() = send(NappletEmbedContract.MSG_RESUME)

    private fun send(what: Int) {
        val msg = Message.obtain(null, what)
        runCatching { serviceMessenger?.send(msg) }
    }
}
