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
package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.JavaScriptReplyProxy
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletPageBridge
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.napplet.session.NappletTrustedBridge
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.lang.ref.WeakReference

/**
 * One launch in the keyless `:napplet` process: the shared [NappletSessionCore] over a
 * [WebViewNappletEngine], with the core's trusted half across the IPC ([NappletIpcBridge]) in the main
 * process's broker service, which runs the shared routing for this launch token. The same for the
 * full-screen [NappletHostActivity] and each embedded [NappletHostService] tab; they keep only their
 * screens, chrome and lifecycle.
 *
 * Even a full WebView/renderer escape into this process yields no secret: the keys live only in the main
 * process, and every brokered operation is still gated by user consent there.
 */
class NappletSandboxSession(
    /** For files, assets and the links the page opens. */
    private val context: Context,
    val config: NappletSandboxLaunch,
    themedContext: Context,
    client: NappletWebViewClient,
    chrome: NappletWebChromeClient,
    /** Sends to the broker, or queues while it isn't bound yet. */
    toBroker: (Message) -> Unit,
    /** Sends to the broker only if it is bound (a release that would never leave a queue). */
    toBoundBroker: (Message) -> Unit,
    private val host: Host,
    /** The embedded surfaces have no keyboard of their own: the shim relays the focused field. */
    imeProxy: Boolean = false,
) {
    /** What the host draws for the launch; every callback runs on the main thread. */
    interface Host {
        /** The shell is about to load: the host mounts [webView]. */
        fun onLoadShell(webView: WebView) {}

        /** The launch moved on (loading, waiting for Tor, showing, failed). */
        fun onState(state: NappletSessionState) {}

        /** How far the download the page waits on has come, or (null) that it ended. */
        fun onDownload(progress: NappletDownloadProgress?) {}

        /** A shell message past the gate, before the core sees it; true when the host took it (keys, the keyboard). */
        fun onPageMessage(
            type: String?,
            envelope: JsonObject,
            raw: String,
        ): Boolean = false

        /** A broker reply for the page, with its id back in: the host binds keys, tells of acts. */
        fun onReply(result: JsonObject) {}

        /** Whether the process route is Tor right now, for a launch with a network of its own. */
        fun onRoute(usesTor: Boolean) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The proxy that answers the shell's own document; set by its first message.
    private var replyProxy: JavaScriptReplyProxy? = null

    /**
     * This launch's own reply channel: the broker keys the launch's routing by it, so a retry (a new
     * session) is a new launch there. It MUST NOT hold the session strongly: a [Messenger] sent over IPC is
     * a binder, and while the main process holds it ART keeps a JNI global reference to its [Handler] here,
     * which would pin session, WebView and host past their end. See [NappletBrowserActivity.replyMessenger].
     */
    val replyMessenger = Messenger(WeakReplyHandler(this))

    private class WeakReplyHandler(
        session: NappletSandboxSession,
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(session)

        override fun handleMessage(msg: Message) {
            ref.get()?.onBrokerMessage(msg)
        }
    }

    private val ipc = NappletIpcBridge(config.launchToken, replyMessenger, toBroker, toBoundBroker)

    val plan = config.plan()

    private val route = WebViewNappletRoute(this, config.proxyPort)

    val engine: WebViewNappletEngine =
        WebViewNappletEngine(
            themedContext = themedContext,
            webViewProfile = config.webViewProfile,
            client = client,
            chrome = chrome,
            onBridgeMessage = ::onShellMessage,
            onLoadShell = { host.onLoadShell(it) },
        )

    val webView: WebView get() = engine.webView

    val core: NappletSessionCore =
        NappletSessionCore(
            launch = config.launch,
            plan = plan,
            route = route,
            files = { androidNappletFiles(config, it, context.cacheDir) },
            shellHtml = { readContractAsset(NappletWebResources.SHELL_HTML_PATH) },
            shimJs = { readContractAsset(NappletWebResources.SHIM_JS_PATH).decodeToString() },
            links = Links(),
            trusted = { _, deliver -> ipc.also { it.deliver = deliver } },
            imeProxy = imeProxy,
            // Posted to the proxy of the shell's own document, never evaluated in whatever the top frame holds.
            toPage = { batch -> main.post { replyProxy?.let { proxy -> batch.forEach { runCatching { proxy.postMessage(it) } } } } },
        ).also { core ->
            core.engine = engine
            client.core = core
            client.engine = engine
            chrome.core = core
        }

    /** Whether the core gave up on this launch: a retry builds a new session. */
    val isFailed: Boolean get() = core.state.value is NappletSessionState.Failed

    /** Starts the launch: route, files, entry point, guards, then the shell. */
    fun start() {
        uiScope.launch { core.state.collect { host.onState(it) } }
        // The first value is the null before any download.
        uiScope.launch { core.download.drop(1).collect { host.onDownload(it) } }
        // An open-web nSite still goes through Tor while another surface needs it: the host says so.
        if (plan.usesNetwork) WebViewProxyPolicy.observeRoute(this) { host.onRoute(it) }
        core.start()
    }

    /** Whether the user is looking at the page: its acts for them are held while they aren't. */
    fun setAttended(isAttended: Boolean) = core.setAttended(isAttended)

    /** The broker (re)bound: one that restarted knows nothing about who is watching. */
    fun onBrokerConnected() = ipc.reportAttended()

    // A broker answer for this launch, from its reply Messenger.
    private fun onBrokerMessage(msg: Message): Boolean = ipc.onBrokerMessage(msg) { host.onReply(it) }

    /** Posts [payload] to the shell straight away (a key action, the keyboard): no broker involved. */
    fun postToShell(payload: String) {
        runCatching { replyProxy?.postMessage(payload) }
    }

    /** Ends the launch: the broker forgets it, the route relaxes, the files and the WebView go. */
    fun close() {
        uiScope.cancel()
        replyProxy = null
        core.close()
    }

    private fun onShellMessage(
        raw: String,
        isMainFrame: Boolean,
        origin: String,
        proxy: JavaScriptReplyProxy,
    ) {
        if (core.isClosed) return
        replyProxy = proxy
        // The applet sends a full upstream envelope {type, id, ...}; anything else is dropped.
        val envelope = parseJsonObjectOrNull(raw) ?: return
        if (host.onPageMessage(NappletPageBridge.typeOf(raw), envelope, raw)) return
        core.onShellMessageFromOrigin(raw, isMainFrame, origin)
    }

    private fun readContractAsset(path: String): ByteArray =
        // This process skips app init (to stay key-free), so the compose-resources context is never set
        // and `Res.readBytes` would crash: the contract's bytes come straight from the APK's assets.
        context.assets.open(NappletWebResources.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    /**
     * Where a tapped link goes. A front page's own links open in Amethyst itself: a `nostr:` VIEW aimed at
     * this package, so no other Nostr client on the device can answer it. Everything else goes to the
     * system browser.
     */
    private inner class Links : NappletLinkOpener {
        override fun openExternal(url: String) {
            main.post { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }

        override fun openNostr(uri: String) {
            main.post { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}

/**
 * The trusted half of a sandbox launch, across the IPC: each request the page may send now goes to the
 * broker service under the launch token (the sandbox never states its own coordinate, so a compromised
 * `:napplet` process can only ever act as the napplet it was launched as), and each answer comes back to
 * the launch's reply Messenger. The broker runs the shared routing for the token.
 */
internal class NappletIpcBridge(
    private val launchToken: String,
    private val replyTo: Messenger,
    private val toBroker: (Message) -> Unit,
    private val toBoundBroker: (Message) -> Unit,
) : NappletTrustedBridge {
    /** The page side's queue: set by the core when it builds this. */
    lateinit var deliver: (String) -> Unit

    // Correlation id for id-less fire-and-forget messages (inc.emit, keys.unregisterAction) so they still
    // reach the broker. Any reply is harmless: the page has nothing to correlate.
    private var fireSeq = 0
    private var attended = false
    private var closed = false

    override fun handle(payload: String) {
        if (closed) return
        val id = NappletPageBridge.idOf(payload)?.ifEmpty { null } ?: "fire-${fireSeq++}"
        toBroker(
            message(NappletIpc.MSG_REQUEST) {
                putString(NappletIpc.KEY_REQUEST_ID, id)
                putString(NappletIpc.KEY_PAYLOAD, payload)
            },
        )
    }

    override fun setAttended(isAttended: Boolean) {
        if (closed) return
        attended = isAttended
        reportAttended()
    }

    /** Tells the broker whether this launch is being looked at, which gates decrypting its relay reads. */
    fun reportAttended() {
        if (closed) return
        toBroker(message(NappletIpc.MSG_SET_ATTENDED) { putBoolean(NappletIpc.KEY_ATTENDED, attended) })
    }

    /**
     * Asks the broker to drop every reference it holds to [replyTo] (this launch's routing, its
     * subscriptions, channels and foreground lease). A retained Messenger is a binder and would pin this
     * surface (and its WebView) for the life of the `:napplet` process. Sent only while bound: a queued
     * release would never leave.
     */
    override fun close() {
        if (closed) return
        closed = true
        toBoundBroker(message(NappletIpc.MSG_RELEASE_CLIENT) {})
    }

    fun onBrokerMessage(
        msg: Message,
        onReply: (JsonObject) -> Unit,
    ): Boolean {
        val data = msg.data ?: return true
        if (closed) return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                // The broker's {type:"...result", ok, ...}; the correlation id goes back in for the shim.
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", id)
                onReply(result)
                deliver(result.toString())
            }
            // A subscription push (relay.event/relay.eose), keyed by subId, not a request id: as it is.
            NappletIpc.MSG_PUSH -> deliver(data.getString(NappletIpc.KEY_PAYLOAD) ?: return true)
            else -> return false
        }
        return true
    }

    private fun message(
        what: Int,
        fill: Bundle.() -> Unit,
    ): Message =
        Message.obtain(null, what).apply {
            replyTo = this@NappletIpcBridge.replyTo
            data =
                Bundle().apply {
                    putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                    fill()
                }
        }
}
