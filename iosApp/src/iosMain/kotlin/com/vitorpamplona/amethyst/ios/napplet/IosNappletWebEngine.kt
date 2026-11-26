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

import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.session.NappletFrame
import com.vitorpamplona.amethyst.commons.napplet.session.NappletGuard
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletNavigationPolicy
import com.vitorpamplona.amethyst.commons.napplet.session.NappletPageBridge
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestKind
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestPolicy
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletWebEngine
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.buffer
import okio.use
import platform.CoreGraphics.CGRectZero
import platform.Foundation.HTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.dataWithBytes
import platform.Foundation.valueForHTTPHeaderField
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentRuleListStore
import platform.WebKit.WKContentWorld
import platform.WebKit.WKDataDetectorTypeNone
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKMediaCaptureType
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKNavigationTypeLinkActivated
import platform.WebKit.WKPermissionDecision
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerWithReplyProtocol
import platform.WebKit.WKSecurityOrigin
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * What the page may ask of the user through `alert`, `confirm` and `prompt`. Each answer must be
 * given exactly once; the engine answers for a page that isn't on screen without asking.
 */
interface NappletJsDialogs {
    fun alert(
        message: String,
        done: () -> Unit,
    )

    fun confirm(
        message: String,
        done: (Boolean) -> Unit,
    )

    fun prompt(
        message: String,
        defaultText: String?,
        done: (String?) -> Unit,
    )
}

/**
 * The WebKit adapter ([NappletWebEngine]) of [NappletSessionCore]: one WKWebView per launch, holding
 * only what is WebKit's own. The core decides everything else (route, files, guards, which requests
 * and navigations go, the bridge, attendance, teardown); this translates.
 *
 * - **Serving.** The shell and the app's files come from a `WKURLSchemeHandler` on
 *   [NappletWebContract.NAPPLET_SCHEME] (WebKit won't hand `https` to one), streamed from the core's
 *   server. The shell is served only to the top-frame navigation this engine let through, so a no-cors
 *   fetch of it from a frame reads a 404 (the shared server's rule). Every response turns DNS prefetching off.
 * - **Second layer behind the CSP.** WebKit never shows a scheme handler an `http(s)` request, so the
 *   shared request policy is compiled into a `WKContentRuleList` ([NappletRequestPolicy.contentRules]):
 *   a napplet's frames can reach nothing but our own hosts. A list that doesn't compile fails the launch.
 * - **Bridge.** The message handler lives in a named content world no page script can see. The shell's
 *   page-world bridge object ([bridgeHead]) hands each message to that world's relay through a DOM
 *   event on the shell's document, which a frame of another origin can't reach; the core accepts only
 *   messages from the main frame at the shell's origin.
 * - **Guards** are page-world user scripts at document start in every frame, sub-frames included.
 * - **Navigation** goes through the shared policy; WebKit names no parent frame, so a frame at the
 *   app's origin is the app's own ([NappletNavigationPolicy.frameByOrigin]). The user's gesture is a
 *   link activation. The top frame only ever holds the shell. Popups never get a window.
 * - No link previews, data detectors, swipe navigation, camera, microphone, motion sensors, file
 *   picker or downloads; the inspector only in a debug build.
 *
 * Threads: WebKit calls in on the main thread and is called on it ([loadShell], [evaluateInShell] and
 * [close] hop there); the server's bytes are read off it.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalNativeApi::class)
class IosNappletWebEngine(
    private val plan: NappletLaunchPlan,
    /** The launch's data store, asked for once, on the main thread, as the web view is made. */
    private val dataStore: () -> WKWebsiteDataStore,
    /** Safari's Web Inspector may attach: debug builds only. */
    private val inspectable: Boolean = Platform.isDebugBinary,
) : NappletWebEngine {
    /** Set once, before the core starts. */
    lateinit var core: NappletSessionCore<*>

    /** Shows the page's dialogs; null answers them without asking. */
    @Volatile
    var dialogs: NappletJsDialogs? = null

    /** Whether the page is on screen: a hidden page's dialogs are answered "cancel" without showing. */
    @Volatile
    var attended: Boolean = false

    private val mutableWebView = MutableStateFlow<WKWebView?>(null)

    /** The browser, once [loadShell] made it; null again after [close]. Main thread. */
    val webView: StateFlow<WKWebView?> = mutableWebView.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = KmpLock()
    private val guards = ArrayList<NappletGuard>()

    @Volatile
    private var closed = false

    @Volatile
    private var loaded = false

    private val world: WKContentWorld = WKContentWorld.worldWithName(WORLD_NAME)

    private val schemeHandler = SchemeHandler(this)
    private val bridgeHandler = BridgeHandler(this)
    private val navigation = NavigationDelegate(this)
    private val ui = UiDelegate(this)

    // Main thread only: the scheme tasks in flight, and whether the top frame's next document is the
    // shell this engine asked for.
    private val tasks = HashMap<WKURLSchemeTaskProtocol, Job>()
    private var shellNavigationPending = false

    // The compiled request filter this launch holds in WebKit's rule store, given back on close.
    private var ruleId: String? = null

    // ---- NappletWebEngine ----

    // A user script runs in every frame of every document from its start, before the page's own: WebKit
    // always takes one. False only once the browser exists (the scripts were fixed when it was made).
    override fun installGuard(guard: NappletGuard): Boolean =
        lock.withLock {
            if (closed || loaded) return false
            guards.add(guard)
            true
        }

    /**
     * The bridge object the shell expects at [NappletWebContract.BRIDGE_NAME]: `postMessage(String)`
     * fires [OUT_EVENT] on the shell's document, which the relay in [world] hands to native; replies
     * come back through `onmessage({data})` ([evaluateInShell]).
     */
    override fun bridgeHead(): String =
        "<script>(function(){var d=document;window.${NappletWebContract.BRIDGE_NAME}={onmessage:null,postMessage:function(s){" +
            "d.dispatchEvent(new CustomEvent('$OUT_EVENT',{detail:String(s)}));}};})();</script>"

    override suspend fun loadShell(shellUrl: String): Boolean {
        // Compiled before the browser exists, so not one request goes out unfiltered. Fails closed: a
        // launch whose list didn't compile doesn't run (the core shows the engine failure).
        val json = NappletRequestPolicy.contentRuleListJson(NappletRequestPolicy.contentRules(plan.profile, plan.appOrigin, plan.origins))
        val id = ruleListId(json)
        withContext(Dispatchers.Main) {
            acquireRules(id)
            ruleId = id
        }
        val rules = compileContentRules(json, id)
        checkNotNull(rules) { "The request filter did not compile" }
        return withContext(Dispatchers.Main) {
            val installed =
                lock.withLock {
                    if (closed) return@withContext false
                    loaded = true
                    guards.toList()
                }
            val view = WKWebView(CGRectZero.readValue(), configuration(installed, rules))
            view.navigationDelegate = navigation
            view.UIDelegate = ui
            view.allowsLinkPreview = false
            // Back goes through the page's history from the app's own back gesture, which asks [goBack].
            view.allowsBackForwardNavigationGestures = false
            view.inspectable = inspectable
            view.scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
            mutableWebView.value = view
            shellNavigationPending = true
            view.loadRequest(NSURLRequest(NSURL(string = shellUrl)))
            true
        }
    }

    private fun configuration(
        installed: List<NappletGuard>,
        rules: WKContentRuleList,
    ): WKWebViewConfiguration =
        WKWebViewConfiguration().apply {
            websiteDataStore = dataStore()
            setURLSchemeHandler(schemeHandler, plan.origins.scheme)
            preferences.javaScriptCanOpenWindowsAutomatically = false
            preferences.fraudulentWebsiteWarningEnabled = true
            defaultWebpagePreferences.allowsContentJavaScript = plan.profile.runsScripts
            dataDetectorTypes = WKDataDetectorTypeNone
            allowsInlineMediaPlayback = true
            limitsNavigationsToAppBoundDomains = false
            val content = userContentController
            for (guard in installed) {
                content.addUserScript(WKUserScript(scriptOf(guard), WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, false, WKContentWorld.pageWorld))
            }
            content.addUserScript(WKUserScript(RELAY_JS, WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, true, world))
            content.addScriptMessageHandlerWithReply(bridgeHandler, world, HANDLER_NAME)
            content.addContentRuleList(rules)
        }

    override fun evaluateInShell(js: String) =
        onMain {
            Log.d(TAG) { "bridge -> ${js.length} bytes" }
            if (!closed) mutableWebView.value?.evaluateJavaScript(js, null, WKContentWorld.pageWorld, null)
        }

    override fun close() {
        lock.withLock { closed = true }
        scope.cancel()
        onMain {
            ruleId?.let(::releaseRules)
            ruleId = null
            val view = mutableWebView.value
            mutableWebView.value = null
            tasks.clear()
            if (view == null) {
                detach()
                return@onMain
            }
            // The page goes first: dropping the app's frame from the trusted shell ends its document,
            // timers and sound at once, whenever WebKit lets go of the view itself.
            view.evaluateJavaScript(NappletWebContract.DROP_APP_FRAME_JS, null, WKContentWorld.pageWorld) { _, _ -> teardown(view) }
        }
    }

    private fun teardown(view: WKWebView) {
        view.stopLoading()
        view.navigationDelegate = null
        view.UIDelegate = null
        view.configuration.userContentController.let {
            it.removeAllScriptMessageHandlers()
            it.removeAllUserScripts()
            it.removeAllContentRuleLists()
        }
        // Nothing of the shell keeps running in a view the system hasn't freed yet.
        view.loadHTMLString("", null)
        view.removeFromSuperview()
        detach()
    }

    // The handlers and delegates WebKit keeps let go of the engine, so no cycle through them holds it.
    private fun detach() {
        schemeHandler.engine = null
        bridgeHandler.engine = null
        navigation.engine = null
        ui.engine = null
    }

    // ---- what the host's screen asks ----

    /** Steps back in the page's history; false when there is none (the screen then closes). Main thread. */
    fun goBack(): Boolean {
        val view = mutableWebView.value ?: return false
        if (!view.canGoBack) return false
        view.goBack()
        return true
    }

    // ---- WebKit's questions ----

    private fun startTask(task: WKURLSchemeTaskProtocol) {
        val request = task.request
        val url = request.URL?.absoluteString
        if (closed || url == null) {
            task.didFailWithError(NSError(ERROR_DOMAIN, -1, null))
            return
        }
        val method = request.HTTPMethod ?: "GET"
        // A document navigation asks for HTML; a script, an image or a fetch doesn't.
        val acceptsHtml = request.valueForHTTPHeaderField("Accept")?.contains(NappletSiteServer.MIME_HTML, ignoreCase = true) == true
        val isShell = url == plan.origins.shellUrl
        // The shell is the top frame's document once, when this engine loaded it: the server answers any
        // other request for it (a frame's no-cors fetch) with a 404.
        val serveShell = isShell && shellNavigationPending && request.mainDocumentURL?.absoluteString == url
        if (serveShell) shellNavigationPending = false
        val refused = core.classifyRequest(url, isImage = false) != NappletRequestKind.SERVE
        tasks[task] =
            scope.launch {
                val response =
                    if (refused) {
                        NappletSiteServer.notFound()
                    } else {
                        try {
                            core.serve(method, url, acceptsHtml, mainFrameNavigation = serveShell) ?: NappletSiteServer.notFound()
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Could not serve $url", e)
                            NappletSiteServer.unavailable()
                        }
                    }
                stream(task, url, response)
            }
    }

    private suspend fun stream(
        task: WKURLSchemeTaskProtocol,
        url: String,
        response: NappletSiteServer.Response,
    ) {
        try {
            val headers = HashMap<Any?, Any?>()
            headers["Content-Type"] = response.contentType
            headers["Content-Length"] = response.length.toString()
            response.headers.forEach { (name, value) -> headers[name] = value }
            headers["X-DNS-Prefetch-Control"] = "off"
            val http = NSHTTPURLResponse(NSURL(string = url), response.status.toLong(), "HTTP/1.1", headers)
            Log.d(TAG) { "served ${response.status} ${url.take(80)}" }
            if (!onTask(task) { it.didReceiveResponse(http) }) return
            response.open().buffer().use { source ->
                val buffer = ByteArray(CHUNK)
                while (true) {
                    val n = source.read(buffer, 0, CHUNK)
                    if (n <= 0) break
                    val data = buffer.toNSData(n)
                    if (!onTask(task) { it.didReceiveData(data) }) return
                }
            }
            withContext(Dispatchers.Main) { if (tasks.remove(task) != null) task.didFinish() }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not stream $url", e)
            withContext(Dispatchers.Main) { if (tasks.remove(task) != null) task.didFailWithError(NSError(ERROR_DOMAIN, -2, null)) }
        }
    }

    // Calls [task] on the main thread unless WebKit stopped it: calling a stopped task raises.
    private suspend fun onTask(
        task: WKURLSchemeTaskProtocol,
        call: (WKURLSchemeTaskProtocol) -> Unit,
    ): Boolean =
        withContext(Dispatchers.Main) {
            if (task in tasks) {
                call(task)
                true
            } else {
                false
            }
        }

    private fun stopTask(task: WKURLSchemeTaskProtocol) {
        tasks.remove(task)?.cancel()
    }

    private fun onBridgeMessage(message: WKScriptMessage): Boolean {
        val raw = message.body as? String ?: return false
        val frame = message.frameInfo
        val accepted = core.onShellMessageFromOrigin(raw, frame.isMainFrame(), originOf(frame.securityOrigin))
        if (accepted) Log.d(TAG) { "bridge <- ${NappletPageBridge.typeOf(raw)}" }
        return accepted
    }

    private fun decideNavigation(action: WKNavigationAction): Boolean {
        val url = action.request.URL?.absoluteString ?: return false
        if (action.shouldPerformDownload) return false
        val target = action.targetFrame
        if (target == null) {
            // A new window: never one of the launch's own.
            core.onPopup(url)
            return false
        }
        val frame = NappletNavigationPolicy.frameByOrigin(target.isMainFrame(), originOf(target.securityOrigin), plan.appOrigin)
        // The top frame holds the shell and nothing else, not even another of our own hosts.
        if (frame == NappletFrame.SHELL && NappletRequestPolicy.isInternal(url, plan.origins) && url != plan.origins.shellUrl) return false
        val allowed = core.onNavigation(url, frame, userGesture = action.navigationType == WKNavigationTypeLinkActivated)
        if (allowed && frame == NappletFrame.SHELL && url == plan.origins.shellUrl) shellNavigationPending = true
        return allowed
    }

    private fun onShellLoaded(view: WKWebView) {
        if (NappletGuard.WEBRTC !in plan.guards) return
        // The runtime half of the guard check, as the console report is on other hosts: the shell's own
        // check stopped it if WebRTC is there, and this fails the launch.
        view.evaluateJavaScript("typeof RTCPeerConnection+','+typeof webkitRTCPeerConnection", null, WKContentWorld.pageWorld) { result, _ ->
            if (result != "undefined,undefined") core.onConsoleMessage(NappletWebContract.WEBRTC_PRESENT)
        }
    }

    private fun onRendererGone() = core.onRendererGone()

    private fun alert(
        message: String,
        done: () -> Unit,
    ) {
        val shown = dialogs?.takeIf { attended && !closed }
        if (shown == null) done() else shown.alert(message, done)
    }

    private fun confirm(
        message: String,
        done: (Boolean) -> Unit,
    ) {
        val shown = dialogs?.takeIf { attended && !closed }
        if (shown == null) done(false) else shown.confirm(message, done)
    }

    private fun prompt(
        message: String,
        defaultText: String?,
        done: (String?) -> Unit,
    ) {
        val shown = dialogs?.takeIf { attended && !closed }
        if (shown == null) done(null) else shown.prompt(message, defaultText, done)
    }

    private class SchemeHandler(
        @Volatile var engine: IosNappletWebEngine?,
    ) : NSObject(),
        WKURLSchemeHandlerProtocol {
        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            startURLSchemeTask: WKURLSchemeTaskProtocol,
        ) {
            val engine = engine
            if (engine == null) {
                startURLSchemeTask.didFailWithError(NSError(ERROR_DOMAIN, -1, null))
                return
            }
            engine.startTask(startURLSchemeTask)
        }

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            stopURLSchemeTask: WKURLSchemeTaskProtocol,
        ) {
            engine?.stopTask(stopURLSchemeTask)
        }
    }

    private class BridgeHandler(
        @Volatile var engine: IosNappletWebEngine?,
    ) : NSObject(),
        WKScriptMessageHandlerWithReplyProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage,
            replyHandler: (Any?, String?) -> Unit,
        ) {
            val accepted = engine?.onBridgeMessage(didReceiveScriptMessage) == true
            replyHandler(null, if (accepted) null else "refused")
        }
    }

    private class NavigationDelegate(
        @Volatile var engine: IosNappletWebEngine?,
    ) : NSObject(),
        WKNavigationDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val allowed = engine?.decideNavigation(decidePolicyForNavigationAction) == true
            decisionHandler(if (allowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        }

        // Shown inline or not at all: nothing here downloads.
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationResponse: WKNavigationResponse,
            decisionHandler: (WKNavigationResponsePolicy) -> Unit,
        ) {
            val inline = engine != null && decidePolicyForNavigationResponse.canShowMIMEType
            decisionHandler(if (inline) WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow else WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
        }

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            didFinishNavigation: WKNavigation?,
        ) {
            engine?.onShellLoaded(webView)
        }

        override fun webView(
            webView: WKWebView,
            didFailProvisionalNavigation: WKNavigation?,
            withError: NSError,
        ) {
            if (withError.code != CANCELLED) Log.w(TAG) { "The shell did not load: ${withError.localizedDescription}" }
        }

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            engine?.onRendererGone()
        }
    }

    private class UiDelegate(
        @Volatile var engine: IosNappletWebEngine?,
    ) : NSObject(),
        WKUIDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            createWebViewWithConfiguration: WKWebViewConfiguration,
            forNavigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures,
        ): WKWebView? {
            forNavigationAction.request.URL
                ?.absoluteString
                ?.let { engine?.core?.onPopup(it) }
            return null
        }

        override fun webView(
            webView: WKWebView,
            runJavaScriptAlertPanelWithMessage: String,
            initiatedByFrame: WKFrameInfo,
            completionHandler: () -> Unit,
        ) {
            engine?.alert(runJavaScriptAlertPanelWithMessage, completionHandler) ?: completionHandler()
        }

        override fun webView(
            webView: WKWebView,
            runJavaScriptConfirmPanelWithMessage: String,
            initiatedByFrame: WKFrameInfo,
            completionHandler: (Boolean) -> Unit,
        ) {
            engine?.confirm(runJavaScriptConfirmPanelWithMessage, completionHandler) ?: completionHandler(false)
        }

        override fun webView(
            webView: WKWebView,
            runJavaScriptTextInputPanelWithPrompt: String,
            defaultText: String?,
            initiatedByFrame: WKFrameInfo,
            completionHandler: (String?) -> Unit,
        ) {
            engine?.prompt(runJavaScriptTextInputPanelWithPrompt, defaultText, completionHandler) ?: completionHandler(null)
        }

        // A napplet has no camera, microphone or motion sensors, whatever the app itself may use.
        override fun webView(
            webView: WKWebView,
            requestMediaCapturePermissionForOrigin: WKSecurityOrigin,
            initiatedByFrame: WKFrameInfo,
            type: WKMediaCaptureType,
            decisionHandler: (WKPermissionDecision) -> Unit,
        ) = decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)

        override fun webView(
            webView: WKWebView,
            requestDeviceOrientationAndMotionPermissionForOrigin: WKSecurityOrigin,
            initiatedByFrame: WKFrameInfo,
            decisionHandler: (WKPermissionDecision) -> Unit,
        ) = decisionHandler(WKPermissionDecision.WKPermissionDecisionDeny)
    }

    companion object {
        private const val TAG = "IosNappletWebEngine"
        private const val ERROR_DOMAIN = "NappletScheme"
        private const val CHUNK = 64 * 1024
        private const val CANCELLED = -999L
        private const val RULE_LIST_PREFIX = "amethyst-napplet-"

        /** The content world the bridge lives in: no page script, in any frame, sees its handler. */
        const val WORLD_NAME = "amethyst-napplet"

        /** The bridge's message handler, in [WORLD_NAME]. */
        const val HANDLER_NAME = "amethystNapplet"

        /** The DOM event the shell's bridge object fires on its own document. */
        const val OUT_EVENT = "amethyst-napplet-out"

        /**
         * In [WORLD_NAME], top frame only: what the shell's bridge object fires goes to native. A frame
         * of another origin can't fire events on the shell's document.
         */
        val RELAY_JS: String =
            "(function(){var h=window.webkit&&window.webkit.messageHandlers&&window.webkit.messageHandlers.$HANDLER_NAME;if(!h)return;" +
                "document.addEventListener('$OUT_EVENT',function(e){var d=e.detail;if(typeof d==='string')h.postMessage(d);},true);})();"

        fun scriptOf(guard: NappletGuard): String =
            when (guard) {
                NappletGuard.WEBRTC -> NappletWebContract.WEBRTC_GUARD_JS
                NappletGuard.COOKIES -> NappletWebContract.COOKIE_GUARD_JS
            }

        /** A frame's security origin as a page writes it (`napplet://napplet.local`); an opaque one is `null`. */
        fun originOf(origin: WKSecurityOrigin): String {
            val scheme = origin.protocol
            val host = origin.host
            if (scheme.isEmpty() || host.isEmpty()) return "null"
            val port = origin.port
            return if (port > 0) "$scheme://$host:$port" else "$scheme://$host"
        }

        /** The rule store's name for [json]'s list: by its own hash, so a launch with the same rules shares it. */
        fun ruleListId(json: String): String = RULE_LIST_PREFIX + sha256(json.encodeToByteArray()).toHexKey().take(24)

        // Main thread: how many open launches use each compiled list. A napplet's list names its own
        // origin, so every napplet the user opens adds one; the last launch on a list removes it.
        private val ruleUses = HashMap<String, Int>()

        private fun acquireRules(id: String) {
            ruleUses[id] = (ruleUses[id] ?: 0) + 1
        }

        private fun releaseRules(id: String) {
            val left = (ruleUses[id] ?: 1) - 1
            if (left > 0) {
                ruleUses[id] = left
                return
            }
            ruleUses.remove(id)
            WKContentRuleListStore.defaultStore()?.removeContentRuleListForIdentifier(id) { error ->
                if (error != null) Log.d(TAG) { "Could not remove the request filter $id: ${error.localizedDescription}" }
            }
        }

        /**
         * Removes the compiled lists no open launch uses: what launches that ended in a crash or a kill
         * left in WebKit's store. Main thread.
         */
        fun sweepContentRules() {
            val store = WKContentRuleListStore.defaultStore() ?: return
            store.getAvailableContentRuleListIdentifiers { ids ->
                ids
                    ?.filterIsInstance<String>()
                    ?.filter { it.startsWith(RULE_LIST_PREFIX) && it !in ruleUses }
                    ?.forEach { id -> store.removeContentRuleListForIdentifier(id) { _ -> } }
            }
        }

        /** The identifiers of this app's lists in WebKit's rule store, for a test. Main thread. */
        suspend fun storedContentRuleIds(): List<String> =
            suspendCancellableCoroutine { cont ->
                val store = WKContentRuleListStore.defaultStore()
                if (store == null) {
                    cont.resume(emptyList())
                } else {
                    store.getAvailableContentRuleListIdentifiers { ids -> cont.resume(ids?.filterIsInstance<String>()?.filter { it.startsWith(RULE_LIST_PREFIX) }.orEmpty()) }
                }
            }

        /** [json] as a compiled `WKContentRuleList` stored as [id], or null when WebKit refused it. */
        suspend fun compileContentRules(
            json: String,
            id: String = ruleListId(json),
        ): WKContentRuleList? =
            withContext(Dispatchers.Main) {
                val store = WKContentRuleListStore.defaultStore() ?: return@withContext null
                suspendCancellableCoroutine { cont ->
                    store.compileContentRuleListForIdentifier(id, json) { list, error ->
                        if (error != null) Log.w(TAG) { "The request filter did not compile: ${error.localizedDescription}" }
                        cont.resume(list)
                    }
                }
            }

        private fun ByteArray.toNSData(length: Int): NSData = usePinned { NSData.dataWithBytes(it.addressOf(0), length.toULong()) }

        private fun onMain(block: () -> Unit) {
            if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
        }
    }
}
