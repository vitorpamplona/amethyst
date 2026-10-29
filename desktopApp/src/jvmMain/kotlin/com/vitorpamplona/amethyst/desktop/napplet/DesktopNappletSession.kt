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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.Component
import java.net.URI
import java.util.Base64

/**
 * One open napplet window: its Chromium browser, the trusted shell it loads, and the bridge between
 * that shell and the shared [NappletBroker].
 *
 * The trust model is Android's (`nappletHost`), on CEF:
 * - The browser loads only [NappletWebContract.SHELL_URL], served from memory with the shell CSP.
 *   Every other request is cancelled before it reaches the network; the napplet's own document is a
 *   `srcdoc` in a `sandbox="allow-scripts"` iframe (an opaque origin) with the locked app CSP
 *   (`connect-src 'none'`), built from the sha256-verified artifact by the shared
 *   [NappletWebContract.injectPrelude].
 * - The bridge is CEF's message router, which Chromium injects into every frame. Only a query from
 *   the main frame at the shell URL is accepted, so the napplet's iframe, which can see the
 *   function, gets nothing from calling it.
 * - Every request is decided by [NappletRequestRouter] and the broker, as on Android.
 */
class DesktopNappletSession(
    val launch: DesktopNappletLaunch,
    /** The launching account, for live relay subscriptions; null leaves them unopened. */
    private val account: Account?,
    private val broker: NappletBroker,
    private val runtime: DesktopNappletRuntime,
) {
    sealed interface State {
        data object Loading : State

        data class Showing(
            val component: Component,
        ) : State

        data class Failed(
            val reason: Failure,
        ) : State
    }

    enum class Failure {
        /** No server returned the signed artifact. */
        UNREACHABLE,

        /** Chromium did not download or start. */
        ENGINE,
    }

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null
    private var router: CefMessageRouter? = null
    private var shell: ByteArray = ByteArray(0)

    @Volatile
    private var closed = false

    fun start() {
        scope.launch {
            try {
                val artifact = runtime.blobs.resolve(launch) as? StaticSiteResolution.Resolved
                if (artifact == null) {
                    Log.w(TAG) { "No Blossom server returned ${launch.identity.coordinate}'s artifact" }
                    mutableState.value = State.Failed(Failure.UNREACHABLE)
                    return@launch
                }
                shell = buildShell(artifact.bytes)
                val app = runtime.engine.app()
                if (closed) return@launch
                val client = app.createClient()
                this@DesktopNappletSession.client = client
                installHandlers(client)
                val browser = client.createBrowser(NappletWebContract.SHELL_URL, false, false)
                this@DesktopNappletSession.browser = browser
                runtime.attendance.set(this@DesktopNappletSession, true)
                mutableState.value = State.Showing(browser.uiComponent)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Napplet ${launch.identity.coordinate} did not open", e)
                mutableState.value = State.Failed(Failure.ENGINE)
            }
        }
    }

    private suspend fun buildShell(artifact: ByteArray): ByteArray {
        val shim = NappletWebContract.shimJs().decodeToString()
        val injected = NappletWebContract.injectPrelude(artifact, shim, launch.declaredDomains, locked = true)
        val encoded = Base64.getEncoder().encodeToString(injected)
        val appId = "n" + sha256("${launch.identity.authorPubKey}:${launch.identity.identifier}".encodeToByteArray()).toHexKey().take(31)
        return NappletWebContract
            .shellHtml()
            .decodeToString()
            .replace(NappletWebContract.APP_ORIGIN_PLACEHOLDER, NappletWebContract.appOrigin(appId))
            .replace(NappletWebContract.APP_SANDBOX_PLACEHOLDER, "allow-scripts")
            .replace(
                NappletWebContract.APP_BOOTSTRAP_PLACEHOLDER,
                "var b=atob('$encoded'),u=new Uint8Array(b.length);" +
                    "for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);" +
                    "iframe.srcdoc=new TextDecoder('utf-8').decode(u);",
            ).replaceFirst("<head>", "<head>$BRIDGE_SCRIPT")
            .encodeToByteArray()
    }

    private fun installHandlers(client: CefClient) {
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(QUERY_FUNCTION, QUERY_CANCEL_FUNCTION))
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser,
                    frame: CefFrame,
                    queryId: Long,
                    request: String,
                    persistent: Boolean,
                    callback: CefQueryCallback,
                ): Boolean {
                    if (!frame.isMain || frame.url != NappletWebContract.SHELL_URL) {
                        Log.w(TAG) { "Refused a bridge call from ${frame.url.take(64)} (not the shell)" }
                        callback.failure(403, "Not the napplet shell")
                        return true
                    }
                    callback.success("")
                    handle(request)
                    return true
                }
            },
            true,
        )
        client.addMessageRouter(router)
        this.router = router

        client.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    if (isInternal(request.url) || isLocal(request.url)) return false
                    // A link out leaves through the system browser, and only when the user clicked it.
                    if (userGesture && isWebLink(request.url)) DesktopBrowser.open(request.url)
                    return true
                }

                override fun getResourceRequestHandler(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef,
                ): CefResourceRequestHandler? = if (isLocal(request.url)) null else resourceHandler
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String,
                    targetFrameName: String?,
                ): Boolean {
                    if (isWebLink(targetUrl)) DesktopBrowser.open(targetUrl)
                    return true
                }
            },
        )
    }

    /** Serves the shell; refuses everything else before it leaves the machine. */
    private val resourceHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): Boolean = !isInternal(request.url)

            override fun getResourceHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): CefResourceHandler =
                if (request.url == NappletWebContract.SHELL_URL && request.method.equals("GET", ignoreCase = true)) {
                    BytesResourceHandler(200, shell, "text/html", mapOf("Content-Security-Policy" to NappletWebContract.shellCsp("'self'")))
                } else {
                    BytesResourceHandler(404, ByteArray(0), "text/plain", emptyMap())
                }
        }

    private fun handle(payload: String) {
        if (closed) return
        scope.launch {
            val id = runCatching { NappletProtocolJson.readId(payload) }.getOrNull()
            when (val outcome = NappletRequestRouter.route(broker, launch.identity, launch.declared, payload)) {
                is NappletRequestRouter.Outcome.Reply -> send(withId(outcome.payload, id))
                is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach(::send)
                is NappletRequestRouter.Outcome.OpenSubscription ->
                    runtime.liveSubscriptions.open(this@DesktopNappletSession, outcome.subId, outcome.filters, account, ::send)
                is NappletRequestRouter.Outcome.CloseSubscription -> runtime.liveSubscriptions.close(this@DesktopNappletSession, outcome.subId)
                // The inc bus is not offered on desktop (INC is not a supported NAP domain).
                else -> {}
            }
        }
    }

    /** Tells a napplet that declared THEME about the app's current colors. */
    fun pushTheme(colors: NappletThemeColors) {
        if (NappletCapability.THEME in launch.declared) send(NappletProtocolJson.encodeThemeChanged(colors.background, colors.text, colors.primary))
    }

    private fun withId(
        payload: String,
        id: String?,
    ): String {
        if (id == null) return payload
        val result = parseJsonObjectOrNull(payload) ?: return payload
        return result.withString("id", id).toString()
    }

    private fun send(payload: String) {
        if (closed) return
        val literal = JsonPrimitive(payload).toString()
        browser?.executeJavaScript("window.${NappletWebContract.BRIDGE_NAME}.onmessage&&window.${NappletWebContract.BRIDGE_NAME}.onmessage({data:$literal});", NappletWebContract.SHELL_URL, 0)
    }

    fun close() {
        if (closed) return
        closed = true
        runtime.liveSubscriptions.closeAllFor(this)
        runtime.attendance.forget(this)
        scope.cancel()
        val client = client
        val browser = browser
        val router = router
        this.client = null
        this.browser = null
        this.router = null
        runCatching {
            browser?.close(true)
            if (client != null && router != null) client.removeMessageRouter(router)
            router?.dispose()
            client?.dispose()
        }.onFailure { Log.w(TAG, "Napplet browser did not close cleanly", it) }
    }

    private class BytesResourceHandler(
        private val status: Int,
        private val body: ByteArray,
        private val mimeType: String,
        private val headers: Map<String, String>,
    ) : CefResourceHandlerAdapter() {
        private var offset = 0

        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            response.status = status
            response.mimeType = mimeType
            headers.forEach { (name, value) -> response.setHeaderByName(name, value, true) }
            responseLength.set(body.size)
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            if (offset >= body.size) {
                bytesRead.set(0)
                return false
            }
            val n = minOf(bytesToRead, body.size - offset)
            System.arraycopy(body, offset, dataOut, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }
    }

    companion object {
        private const val TAG = "DesktopNappletSession"
        private const val QUERY_FUNCTION = "__amethystNappletQuery"
        private const val QUERY_CANCEL_FUNCTION = "__amethystNappletQueryCancel"

        /**
         * The object the shared shell expects at [NappletWebContract.BRIDGE_NAME]: `postMessage(String)`
         * out to the host, `onmessage({data})` in. The router's query function is captured and
         * removed from the shell's window, so nothing loaded later in the shell finds it there.
         */
        private val BRIDGE_SCRIPT =
            "<script>(function(){var q=window.$QUERY_FUNCTION;try{delete window.$QUERY_FUNCTION;}catch(_){}" +
                "window.${NappletWebContract.BRIDGE_NAME}={onmessage:null,postMessage:function(s){" +
                "q({request:String(s),persistent:false,onSuccess:function(){},onFailure:function(){}});}};})();</script>"

        private fun isInternal(url: String): Boolean = runCatching { NappletWebContract.isInternalHost(URI(url).host) && url.startsWith("https://") }.getOrDefault(false)

        private fun isWebLink(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

        /** The napplet's srcdoc document and the data/blob URLs a page makes itself: none leave the machine. */
        private fun isLocal(url: String): Boolean = url == "about:srcdoc" || url == "about:blank" || url.startsWith("data:") || url.startsWith("blob:")
    }
}
