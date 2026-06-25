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
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.napplethost.NappletEmbedContract
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedImeBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedLoadStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedMagnifierProbe
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSurfaceController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ImeEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.MagnifierFrame
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.parseSelectionGeometry
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side handle to an embedded nsite/napplet. Binds [NappletHostService][com.vitorpamplona.amethyst.napplethost.NappletHostService]
 * (in the keyless `:napplet` process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the
 * verified-blob WebView renders inside the main activity, and relays back/reload while receiving
 * navigation state + "allow always" notices that the trusted main-process chrome reflects.
 *
 * [params] is the bundle minted in the main process by
 * [NappletLauncher.buildLaunchParams][com.vitorpamplona.amethyst.napplet.NappletLauncher.buildLaunchParams]
 * — the verified manifest, identity, and launch token. The mirror of `EmbeddedWebAppController`.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedNostrAppController(
    private val appContext: Context,
    private val params: Bundle,
) : EmbeddedSurfaceController,
    EmbeddedImeBridge,
    EmbeddedMagnifierProbe {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null
    private var pendingAdapter: SandboxedUiAdapter? = null

    // A single NappletHostService instance serves every embedded napplet tab, so each controller stamps
    // its own id on every message; the provider uses it to route controls/state/IME to this tab.
    private val sessionId: String = "napplet-${SESSION_SEQ.incrementAndGet()}"

    // A parked tab can be hidden (paused) before the service even binds, so the pause message is
    // dropped (no messenger yet). Remember the intent and replay it right after the session is created,
    // otherwise an applet that was never shown comes up running in the background.
    private var wantPaused = false

    /** (canGoBack) — drives the in-tab back gesture. */
    var onStateChanged: ((Boolean) -> Unit)? = null

    /** A granted "allow always" sensitive op just ran (one of NappletEmbedContract.NOTICE_*). */
    var onNotice: ((String) -> Unit)? = null

    private var hasLoadedReal = false

    /** Last known main-frame load state, so the tab layer renders the right overlay immediately. */
    override var loadStatus: EmbeddedLoadStatus = EmbeddedLoadStatus()
        private set

    /** Notified on the main thread whenever [loadStatus] changes. */
    override var onLoadStatusChanged: ((EmbeddedLoadStatus) -> Unit)? = null

    override var onImeEvent: ((ImeEvent) -> Unit)? = null

    override var onMagnifierFrame: ((MagnifierFrame) -> Unit)? = null

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
        // Drop refs so an evicted controller doesn't pin the surface view or the remote messenger.
        serviceMessenger = null
        sandboxedSdkView = null
        pendingAdapter = null
        onStateChanged = null
        onNotice = null
        onImeEvent = null
        onMagnifierFrame = null
        onLoadStatusChanged = null
    }

    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        // Paint the surface placeholder in the app's theme background so there's no white flash before
        // the remote WebView delivers its first frame.
        view.setBackgroundColor(params.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE))
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
                data = Bundle(params).apply { putString(NappletEmbedContract.KEY_SESSION_ID, sessionId) }
            }
        runCatching { serviceMessenger?.send(msg) }
        // Replay a pause that was requested before we had a messenger to send it on (parked-before-bound),
        // so a never-shown applet doesn't start running. Messenger preserves order, so PAUSE lands after
        // CREATE in the host.
        if (wantPaused) send(NappletEmbedContract.MSG_PAUSE)
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
            NappletEmbedContract.MSG_IME_EVENT -> {
                val payload = msg.data?.getString(NappletEmbedContract.KEY_IME_PAYLOAD) ?: return true
                parseImeEvent(payload)?.let { event -> onImeEvent?.invoke(event) }
            }
            NappletEmbedContract.MSG_LOAD_STATE -> {
                val isLoading = msg.data?.getBoolean(NappletEmbedContract.KEY_IS_LOADING, false) ?: false
                val failed = msg.data?.getBoolean(NappletEmbedContract.KEY_LOAD_FAILED, false) ?: false
                onLoadState(isLoading, failed)
            }
            NappletEmbedContract.MSG_MAGNIFIER_FRAME -> {
                val data = msg.data ?: return true
                val bytes = data.getByteArray(NappletEmbedContract.KEY_MAG_BYTES) ?: return true
                onMagnifierFrame?.invoke(
                    MagnifierFrame(
                        bytes = bytes,
                        width = data.getInt(NappletEmbedContract.KEY_MAG_W),
                        height = data.getInt(NappletEmbedContract.KEY_MAG_H),
                        captureMs = data.getDouble(NappletEmbedContract.KEY_MAG_CAPTURE_MS),
                        requestStampNanos = data.getLong(NappletEmbedContract.KEY_MAG_REQ_T),
                    ),
                )
            }
            else -> return false
        }
        return true
    }

    override fun sendImeOp(json: String) = send(NappletEmbedContract.MSG_IME_OP) { putString(NappletEmbedContract.KEY_IME_PAYLOAD, json) }

    override fun requestMagnifier(
        surfaceX: Float,
        surfaceY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float,
    ) = send(NappletEmbedContract.MSG_MAGNIFIER_REQUEST) {
        putFloat(NappletEmbedContract.KEY_MAG_X, surfaceX)
        putFloat(NappletEmbedContract.KEY_MAG_Y, surfaceY)
        putInt(NappletEmbedContract.KEY_MAG_BOX_W, boxWidthPx)
        putInt(NappletEmbedContract.KEY_MAG_BOX_H, boxHeightPx)
        putFloat(NappletEmbedContract.KEY_MAG_ZOOM, zoom)
        putLong(NappletEmbedContract.KEY_MAG_REQ_T, SystemClock.elapsedRealtimeNanos())
    }

    private fun parseImeEvent(payload: String): ImeEvent? {
        val o = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return when (o.optString("type")) {
            "ime.focus" ->
                ImeEvent.Focus(
                    inputType = o.optString("inputType", "text"),
                    enterKeyHint = o.optString("enterKeyHint", ""),
                    multiline = o.optBoolean("multiline", false),
                    text = o.optString("text", ""),
                    selStart = o.optInt("selStart", 0),
                    selEnd = o.optInt("selEnd", 0),
                    geometry = parseSelectionGeometry(o.optJSONObject("geom")),
                )
            "ime.blur" -> ImeEvent.Blur
            "ime.state" ->
                ImeEvent.State(
                    text = o.optString("text", ""),
                    selStart = o.optInt("selStart", 0),
                    selEnd = o.optInt("selEnd", 0),
                    geometry = parseSelectionGeometry(o.optJSONObject("geom")),
                )
            "ime.pagesel" ->
                ImeEvent.PageSelection(
                    active = o.optBoolean("active", false),
                    text = o.optString("text", ""),
                    geometry = parseSelectionGeometry(o.optJSONObject("geom")),
                )
            "ime.scroll" -> ImeEvent.Scroll(active = o.optBoolean("active", false))
            "ime.carettap" -> ImeEvent.CaretTap(geometry = parseSelectionGeometry(o.optJSONObject("geom")))
            else -> null
        }
    }

    fun back() = send(NappletEmbedContract.MSG_BACK)

    fun reload() = send(NappletEmbedContract.MSG_RELOAD)

    /** User-triggered recovery for a stuck or failed session: reload the verified content from scratch. */
    override fun retry() {
        hasLoadedReal = false
        publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
        reload()
    }

    private fun onLoadState(
        isLoading: Boolean,
        failed: Boolean,
    ) {
        if (!isLoading && !failed) hasLoadedReal = true
        publishLoadStatus(EmbeddedLoadStatus(isLoading = isLoading, failed = failed, hasLoadedReal = hasLoadedReal))
    }

    private fun publishLoadStatus(status: EmbeddedLoadStatus) {
        loadStatus = status
        onLoadStatusChanged?.invoke(status)
    }

    /** Pause/resume the applet's JS when the tab leaves/returns to the foreground (background gating). */
    fun pause() {
        wantPaused = true
        send(NappletEmbedContract.MSG_PAUSE)
    }

    fun resume() {
        wantPaused = false
        send(NappletEmbedContract.MSG_RESUME)
    }

    private inline fun send(
        what: Int,
        crossinline block: Bundle.() -> Unit = {},
    ) {
        val msg =
            Message.obtain(null, what).apply {
                data =
                    Bundle().apply {
                        putString(NappletEmbedContract.KEY_SESSION_ID, sessionId)
                        block()
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
    }

    private companion object {
        private val SESSION_SEQ = AtomicLong()
    }
}
