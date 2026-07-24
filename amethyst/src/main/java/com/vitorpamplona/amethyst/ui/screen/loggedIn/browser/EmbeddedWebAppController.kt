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
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.amethyst.napplethost.NappletBrowserContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleLogEntry
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
 * Client-side handle to the embedded browser. Binds [NappletBrowserService] (in the keyless `:napplet`
 * process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the remote WebView renders inside
 * the main activity, and relays chrome controls (navigate/reload/back/Tor) while receiving URL updates
 * that drive the trusted, main-process address bar.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedWebAppController(
    private val appContext: Context,
    private val proxyPort: Int,
    private val initialUseTor: Boolean,
    private val backgroundColor: Int,
    private val themeType: String = "SYSTEM",
) : EmbeddedSurfaceController,
    EmbeddedImeBridge,
    EmbeddedMagnifierProbe,
    ConsoleBridge {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null
    private var pendingAdapter: SandboxedUiAdapter? = null

    /**
     * True once this controller's adapter has actually been handed to a [SandboxedSdkView]. An adapter can
     * only ever serve ONE view: when that view is disposed, privacysandbox closes the remote session and the
     * sandbox destroys its WebView, so the adapter is dead. See [attachView] for why this matters.
     */
    private var adapterDelivered = false
    private var startUrl: String = "about:blank"

    private var hasLoadedReal = false
    private var blankRecovered = false

    /** Last known main-frame load state, so the tab layer renders the right overlay immediately. */
    override var loadStatus: EmbeddedLoadStatus = EmbeddedLoadStatus()
        private set

    /** Notified on the main thread whenever [loadStatus] changes. */
    override var onLoadStatusChanged: ((EmbeddedLoadStatus) -> Unit)? = null

    /** JavaScript console output received from the embedded WebView, capped at [MAX_CONSOLE_LOGS] entries. */
    override val consoleLogs = mutableStateListOf<ConsoleLogEntry>()

    override fun clearConsoleLogs() = consoleLogs.clear()

    // A single NappletBrowserService instance serves every embedded browser tab, so each controller
    // stamps its own id on every message; the provider uses it to route controls/updates to this tab.
    // Re-minted whenever the remote session is re-created (see [attachView]), so a late close() from the
    // previous view can never reap the replacement.
    private var sessionId: String = "browser-${SESSION_SEQ.incrementAndGet()}"

    /** Invoked on the main thread when the page navigates: (url, canGoBack). */
    var onUrlChanged: ((String, Boolean) -> Unit)? = null

    override var onImeEvent: ((ImeEvent) -> Unit)? = null

    // SPIKE (magnifier #4, option B): provider-side capture round-trip. Removed once the loupe lands.
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
        // Drop refs so an evicted controller doesn't pin the surface view or the remote messenger.
        serviceMessenger = null
        sandboxedSdkView = null
        pendingAdapter = null
        adapterDelivered = false
        onUrlChanged = null
        onImeEvent = null
        onMagnifierFrame = null
        onLoadStatusChanged = null
        consoleLogs.clear()
    }

    override fun teardown() = unbind()

    /**
     * Hands the surface view to the controller; applies the adapter if it already arrived, and re-arms the
     * remote session when this controller is being re-used by a *second* view.
     *
     * A warm controller outlives the composition (it lives in the process-scoped [EmbeddedTabHost]), but its
     * [SandboxedSdkView] does not: an account switch rebuilds the whole logged-in subtree, disposing every
     * surface. That disposal makes privacysandbox close the remote session, which destroys the sandbox's
     * WebView — so the adapter this controller already handed out is dead and cannot be given to the fresh
     * view. A [SandboxedSdkView] with no adapter never builds a ContentView/SurfaceView and paints nothing
     * but its background colour, forever (the load overlay's retry can't help — it re-navigates a WebView
     * that no longer exists).
     *
     * So when a new view attaches after the adapter was already delivered, ask the sandbox for a brand new
     * session; the [NappletBrowserContract.MSG_SESSION_READY] reply arms this view. The sandbox stamps the
     * CURRENT account's storage profile on that new session (see [sendCreateSession]), so re-arming can
     * never resurrect the previous account's cookie jar.
     */
    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        // Paint the surface placeholder in the app's theme background so there's no white flash before
        // the remote WebView delivers its first frame.
        view.setBackgroundColor(backgroundColor)
        val adapter = pendingAdapter
        when {
            adapter != null -> {
                pendingAdapter = null
                adapterDelivered = true
                view.setAdapter(adapter)
            }
            // No adapter in hand and one was already spent on a previous (now disposed) view: the session
            // behind it is gone, so this view would stay blank forever. Re-create it.
            adapterDelivered -> {
                // Mint a FRESH session id. The disposed view's Session.close() reaches the sandbox
                // asynchronously (it posts to the sandbox's main thread) and was measured landing ~1 s
                // AFTER this create: reusing the id let that late close reap the session we had just asked
                // for — a new WebView was built, destroyed, and the surface stayed black. A new id makes
                // the stale close target only the corpse it belongs to.
                sessionId = "browser-${SESSION_SEQ.incrementAndGet()}"
                adapterDelivered = false
                sendCreateSession()
            }
            // else: the first session is still in flight; MSG_SESSION_READY will arm this view.
        }
    }

    private fun sendCreateSession() {
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_CREATE_SESSION).apply {
                replyTo = incoming
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_SESSION_ID, sessionId)
                        putString(NappletBrowserContract.KEY_URL, startUrl)
                        putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort)
                        putBoolean(NappletBrowserContract.KEY_USE_TOR, initialUseTor)
                        putInt(NappletBrowserContract.KEY_BG_COLOR, backgroundColor)
                        putString(NappletBrowserContract.KEY_THEME, themeType)
                        // Opaque per-account storage partition, so an embedded site can't carry one
                        // npub's session into another. Derived here (the sandbox never sees the pubkey).
                        putString(NappletBrowserContract.KEY_WEBVIEW_PROFILE, NappletWebViewProfiles.current())
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
                if (view != null) {
                    adapterDelivered = true
                    view.setAdapter(adapter)
                } else {
                    pendingAdapter = adapter
                }
            }
            NappletBrowserContract.MSG_URL_CHANGED -> {
                val url = msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()
                val canGoBack = msg.data?.getBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, false) ?: false
                onUrlChanged?.invoke(url, canGoBack)
            }
            NappletBrowserContract.MSG_IME_EVENT -> {
                val payload = msg.data?.getString(NappletBrowserContract.KEY_IME_PAYLOAD) ?: return true
                parseImeEvent(payload)?.let { event -> onImeEvent?.invoke(event) }
            }
            NappletBrowserContract.MSG_LOAD_STATE -> {
                val isLoading = msg.data?.getBoolean(NappletBrowserContract.KEY_IS_LOADING, false) ?: false
                val failed = msg.data?.getBoolean(NappletBrowserContract.KEY_LOAD_FAILED, false) ?: false
                val loadedUrl = msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()
                onLoadState(isLoading, failed, loadedUrl)
            }
            NappletBrowserContract.MSG_CONSOLE_LOG -> {
                val level = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_LEVEL) ?: "LOG"
                val message = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_MESSAGE).orEmpty()
                val source = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_SOURCE).orEmpty()
                val line = msg.data?.getInt(NappletBrowserContract.KEY_CONSOLE_LINE, 0) ?: 0
                if (consoleLogs.size >= MAX_CONSOLE_LOGS) consoleLogs.removeAt(0)
                consoleLogs.add(ConsoleLogEntry(level, message, source, line))
            }
            NappletBrowserContract.MSG_MAGNIFIER_FRAME -> {
                val data = msg.data ?: return true
                val bytes = data.getByteArray(NappletBrowserContract.KEY_MAG_BYTES) ?: return true
                onMagnifierFrame?.invoke(
                    MagnifierFrame(
                        bytes = bytes,
                        width = data.getInt(NappletBrowserContract.KEY_MAG_W),
                        height = data.getInt(NappletBrowserContract.KEY_MAG_H),
                        captureMs = data.getDouble(NappletBrowserContract.KEY_MAG_CAPTURE_MS),
                        requestStampNanos = data.getLong(NappletBrowserContract.KEY_MAG_REQ_T),
                    ),
                )
            }
            else -> return false
        }
        return true
    }

    fun navigate(url: String) = send(NappletBrowserContract.MSG_NAVIGATE) { putString(NappletBrowserContract.KEY_URL, url) }

    fun reload() = send(NappletBrowserContract.MSG_RELOAD) {}

    /**
     * User-triggered recovery for a stuck, blank, or failed session: reload the canonical [startUrl] from
     * scratch. Unlike [reload] (which re-fetches whatever the WebView currently shows — `about:blank` for a
     * session that never got its URL), this re-navigates to the favorite's real URL.
     */
    override fun retry() {
        blankRecovered = false
        hasLoadedReal = false
        publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
        navigate(startUrl)
    }

    private fun onLoadState(
        isLoading: Boolean,
        failed: Boolean,
        loadedUrl: String,
    ) {
        // A favorite whose session settled on about:blank never received its real URL (a warm session built
        // before the URL was wired through). Re-navigate once to the canonical URL — reload() can't fix this
        // because it would just reload about:blank. Scoped to a real startUrl, so the generic browser's
        // intentional about:blank new-tab page is left alone.
        if (!isLoading && !failed && loadedUrl.isBlankPage() && !startUrl.isBlankPage() && !blankRecovered) {
            blankRecovered = true
            publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
            navigate(startUrl)
            return
        }
        if (!isLoading && !failed && !loadedUrl.isBlankPage()) hasLoadedReal = true
        publishLoadStatus(EmbeddedLoadStatus(isLoading = isLoading, failed = failed, hasLoadedReal = hasLoadedReal))
    }

    private fun publishLoadStatus(status: EmbeddedLoadStatus) {
        loadStatus = status
        onLoadStatusChanged?.invoke(status)
    }

    private fun String.isBlankPage() = isEmpty() || this == "about:blank"

    fun back() = send(NappletBrowserContract.MSG_BACK) {}

    fun setTor(useTor: Boolean) = send(NappletBrowserContract.MSG_SET_TOR) { putBoolean(NappletBrowserContract.KEY_USE_TOR, useTor) }

    override fun sendImeOp(json: String) = send(NappletBrowserContract.MSG_IME_OP) { putString(NappletBrowserContract.KEY_IME_PAYLOAD, json) }

    // Stamp the client send time so the reply can be matched / stale frames dropped (same-process clock).
    override fun requestMagnifier(
        surfaceX: Float,
        surfaceY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float,
    ) = send(NappletBrowserContract.MSG_MAGNIFIER_REQUEST) {
        putFloat(NappletBrowserContract.KEY_MAG_X, surfaceX)
        putFloat(NappletBrowserContract.KEY_MAG_Y, surfaceY)
        putInt(NappletBrowserContract.KEY_MAG_BOX_W, boxWidthPx)
        putInt(NappletBrowserContract.KEY_MAG_BOX_H, boxHeightPx)
        putFloat(NappletBrowserContract.KEY_MAG_ZOOM, zoom)
        putLong(NappletBrowserContract.KEY_MAG_REQ_T, SystemClock.elapsedRealtimeNanos())
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

    private inline fun send(
        what: Int,
        crossinline block: Bundle.() -> Unit,
    ) {
        val msg =
            Message.obtain(null, what).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_SESSION_ID, sessionId)
                        block()
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
    }

    private companion object {
        private val SESSION_SEQ = AtomicLong()
        private const val MAX_CONSOLE_LOGS = 200
    }
}
