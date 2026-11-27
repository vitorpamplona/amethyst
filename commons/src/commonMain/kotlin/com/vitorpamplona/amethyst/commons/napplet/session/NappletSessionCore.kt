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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.frontpage.PaperLinks
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/** Where one launch is, for its screen. */
sealed interface NappletSessionState {
    data object Loading : NappletSessionState

    /** It goes through Tor, and waits for Tor to come up before anything is fetched. */
    data object WaitingForTor : NappletSessionState

    /** The shell is loading or loaded in the engine. */
    data object Showing : NappletSessionState

    data class Failed(
        val reason: NappletFailure,
    ) : NappletSessionState
}

enum class NappletFailure {
    /** No server returned the signed files. */
    UNREACHABLE,

    /** The browser engine didn't start, or can't guard the launch (it refuses to run without). */
    ENGINE,

    /** Tor is on and isn't running: nothing may leave until it is. */
    ROUTE_BLOCKED,

    /** The browser's web content process died under the page. */
    RENDERER_GONE,
}

/**
 * The one piece each host writes per browser engine: everything [NappletSessionCore] asks of the
 * browser. The engine calls back into the core for what the browser asks of it
 * ([NappletSessionCore.onShellMessage], [NappletSessionCore.classifyRequest],
 * [NappletSessionCore.serve], [NappletSessionCore.onNavigation], [NappletSessionCore.onPopup],
 * [NappletSessionCore.onConsoleMessage], [NappletSessionCore.onRendererGone]). Each adapter owns its
 * own threading.
 */
interface NappletWebEngine {
    /**
     * Makes [guard]'s script run at document start in every frame the launch loads, before any page
     * script. False when the engine can't: the core then refuses a launch that requires it.
     */
    fun installGuard(guard: NappletGuard): Boolean

    /**
     * Markup the shell's head starts with, before anything there runs: the engine's bridge object when
     * the browser doesn't inject [NappletWebContract.BRIDGE_NAME] itself. Empty otherwise.
     */
    fun bridgeHead(): String = ""

    /**
     * Makes the browser and loads [shellUrl] in its top frame. Called once, after the route is in
     * effect, the entry point resolved and the guards installed. False when it didn't (closed meanwhile).
     */
    suspend fun loadShell(shellUrl: String): Boolean

    /** Runs [js] in the shell's top frame. */
    fun evaluateInShell(js: String)

    /**
     * Tears the browser down, after the core released everything else. The engine first drops the
     * app's frame ([NappletWebContract.DROP_APP_FRAME_JS]) so the page's timers and sound end at once.
     */
    fun close()
}

/**
 * The network route a launch claims. A host with one route per process (Android's WebView, Desktop's
 * SOCKS router) files a claim; a host that proxies per data store (iOS) sets the store's proxy.
 */
interface NappletRoutePort {
    /** Whether Tor is up now. */
    val torRunning: Boolean

    /** For a launch with a network of its own: put its traffic on Tor or the open web. False fails closed. */
    suspend fun claim(useTor: Boolean): Boolean

    /** For a launch without one whose blobs go through Tor: waits for Tor. False fails closed. */
    suspend fun awaitTor(): Boolean

    /** The launch's traffic is over (it closed, or shows a failure). */
    fun release()
}

/** Where a link the user clicked goes, outside the launch. */
interface NappletLinkOpener {
    /** The system browser (or the in-app one). */
    fun openExternal(url: String)

    /** A nostr: link a front page linked to, opened in the app. */
    fun openNostr(uri: String)
}

/**
 * One open napplet, nSite, webxdc app or front page, apart from its browser engine: the launch's
 * route, files and state, the guards it requires, which requests and navigations its frames may make,
 * the bridge to the broker ([NappletPageBridge] + [NappletSessionRouting]), attendance, and the
 * ordered teardown. A host adapts its browser through [NappletWebEngine] and keeps only what is the
 * engine's own (views, input, zoom, find).
 *
 * The trust model is Android's (`nappletHost`): the top frame is only ever the shell, served from
 * memory with the shell CSP; a napplet runs as a srcdoc in an `allow-scripts` iframe with no network;
 * a site runs at its own origin under napplet.local and talks to the web through the claimed route;
 * only the shell may reach the bridge; every request is decided by the broker.
 */
class NappletSessionCore(
    val launch: NappletLaunch,
    val plan: NappletLaunchPlan,
    private val route: NappletRoutePort,
    /** The launch's files ([com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore], webxdc, paper), fetched per [plan]. */
    private val files: (NappletLaunchPlan) -> NappletFiles,
    private val shellHtml: suspend () -> ByteArray,
    private val shimJs: suspend () -> String,
    private val links: NappletLinkOpener,
    /**
     * Makes the launch's trusted half, which answers the page through `deliver`: [NappletSessionRouting]
     * in-process ([inProcess]), or the IPC to Android's broker service.
     */
    trusted: (scope: CoroutineScope, deliver: (String) -> Unit) -> NappletTrustedBridge,
    /**
     * Whether the launch is attended as soon as it shows. Desktop's windows are; a host that knows when
     * its surface is on screen (iOS, Android) says so through [setAttended] instead.
     */
    private val attendedWhenShown: Boolean = false,
    /**
     * Whether the shim relays the focused text field to the host's keyboard: a surface drawn in another
     * process (Android's embedded tabs) has none of its own.
     */
    private val imeProxy: Boolean = false,
    /**
     * Hands a batch of payloads to the shell's own document, for a browser whose bridge can answer the
     * document that called it (Android's reply proxy). Null runs [NappletPageBridge.deliveryScript] through
     * [NappletWebEngine.evaluateInShell].
     */
    toPage: ((List<String>) -> Unit)? = null,
    /** After the launch's teardown, before the engine's: a host trims its shared cache. */
    private val onClosed: () -> Unit = {},
    private val clock: () -> Long = NappletPageBridge.monotonicMillis(),
) {
    /** Lets the host engine call back in; set once, before [start]. */
    lateinit var engine: NappletWebEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = KmpLock()

    private val mutableState = MutableStateFlow<NappletSessionState>(NappletSessionState.Loading)
    val state: StateFlow<NappletSessionState> = mutableState.asStateFlow()

    private val mutableDownload = MutableStateFlow<NappletDownloadProgress?>(null)

    /** The download the page waits on while [NappletSessionState.Loading], for the loading screen's bar. */
    val download: StateFlow<NappletDownloadProgress?> = mutableDownload.asStateFlow()

    @Volatile
    private var closed = false

    /** Whether [close] ran: the user stopped it, or the host did for an account that left. */
    val isClosed: Boolean get() = closed

    @Volatile
    private var server: NappletSiteServer? = null

    // The server's files, for close(): a webxdc archive still downloading stops with the launch.
    @Volatile
    private var openFiles: NappletFiles? = null

    private val page: NappletPageBridge =
        NappletPageBridge(scope, toTrusted = { routing.handle(it) }, evaluateInShell = { engine.evaluateInShell(it) }, clock = clock, toPage = toPage)

    private val routing: NappletTrustedBridge = trusted(scope, page::deliver)

    private val coordinate get() = launch.identity.coordinate

    /** Starts the launch: route, files, entry point, guards, then the engine's shell. */
    fun start() {
        scope.launch {
            try {
                open()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "$coordinate did not open", e)
                mutableState.value = NappletSessionState.Failed(NappletFailure.ENGINE)
            }
        }
    }

    private suspend fun open() {
        // With Tor on, nothing is fetched until Tor runs, so no request leaves early or around it: a
        // site's own traffic, or a napplet's blobs. Android fails closed the same way.
        val needsTor = plan.needsTor
        if (needsTor && !route.torRunning) mutableState.value = NappletSessionState.WaitingForTor
        val routed = if (plan.usesNetwork) route.claim(plan.useTor) else !needsTor || route.awaitTor()
        // Closed while it waited: its claim was filed after close() released it.
        if (closed) {
            if (plan.usesNetwork) route.release()
            return
        }
        if (!routed) {
            Log.w(TAG) { "Tor is not running; not opening $coordinate" }
            fail(NappletFailure.ROUTE_BLOCKED)
            return
        }
        mutableState.value = NappletSessionState.Loading
        val blobs = files(plan)
        openFiles = blobs
        // Closed while the files were made: close() may have missed them.
        if (closed) blobs.close()
        val server =
            NappletSiteServer(
                blobs = blobs,
                shellHtml = shellHtml(),
                shimJs = shimJs(),
                appOrigin = plan.appOrigin,
                profile = launch.profile,
                declaredDomains = launch.declaredDomains,
                imeProxy = imeProxy,
                shellHead = plan.shellHead(engine.bridgeHead()),
                origins = plan.origins,
            )
        this.server = server
        // The entry point must resolve before anything shows: a missing site is a clear message, not a
        // blank page.
        val watch = scope.launch { server.download.collect { mutableDownload.value = it } }
        val available =
            try {
                server.isAvailable(plan.entryPath)
            } finally {
                // Joined first: a collector mid-write on another thread would put its value back after this null.
                withContext(NonCancellable) {
                    watch.cancelAndJoin()
                    mutableDownload.value = null
                }
            }
        if (!available) {
            Log.w(TAG) { "No Blossom server returned $coordinate's files" }
            fail(NappletFailure.UNREACHABLE)
            return
        }
        for (guard in plan.guards) {
            if (engine.installGuard(guard)) continue
            if (guard == NappletGuard.WEBRTC) {
                // A page that must not have a network would have one around the proxy: it doesn't run.
                Log.w(TAG) { "The browser can't take WebRTC away; not opening $coordinate" }
                fail(NappletFailure.ENGINE)
                return
            }
            Log.w(TAG) { "The browser can't install the $guard guard for $coordinate" }
        }
        if (closed || !engine.loadShell(plan.origins.shellUrl)) return
        if (attendedWhenShown) setAttended(true)
        mutableState.value = NappletSessionState.Showing
    }

    /** A launch showing its failure has no traffic: its claim must not keep holding the route for others. */
    private fun fail(reason: NappletFailure) {
        if (plan.usesNetwork) route.release()
        mutableState.value = NappletSessionState.Failed(reason)
    }

    /**
     * Whether the user is looking at the launch. Requests that act for them are held while they aren't,
     * and the broker's decrypting reads wait for them.
     */
    fun setAttended(isAttended: Boolean) {
        if (closed) return
        routing.setAttended(isAttended)
        page.setAttended(isAttended)
    }

    // ---- the engine's questions ----

    /** A message the shell posted to the bridge, from a browser that reports the frame's URL. False when refused. */
    fun onShellMessage(
        raw: String,
        isMainFrame: Boolean,
        frameUrl: String?,
    ): Boolean {
        if (!NappletBridgeGate.acceptsUrl(isMainFrame, frameUrl, plan.origins)) {
            Log.w(TAG) { "Refused a bridge call from ${frameUrl?.take(64)} (not the shell)" }
            return false
        }
        page.receive(raw)
        return true
    }

    /** [onShellMessage], from a browser that reports the frame's security origin. */
    fun onShellMessageFromOrigin(
        raw: String,
        isMainFrame: Boolean,
        frameOrigin: String?,
    ): Boolean {
        if (!NappletBridgeGate.acceptsOrigin(isMainFrame, frameOrigin, plan.origins)) {
            Log.w(TAG) { "Refused a bridge call from ${frameOrigin?.take(64)} (not the shell)" }
            return false
        }
        page.receive(raw)
        return true
    }

    fun classifyRequest(
        url: String,
        isImage: Boolean,
    ): NappletRequestKind = NappletRequestPolicy.classify(url, launch.profile, isImage, plan.origins)

    /**
     * The answer to a [NappletRequestKind.SERVE] request, or null when there is none (the engine
     * answers 404: from an internal origin a request can't go anywhere else). [acceptsHtml] is whether
     * it is a document navigation, [mainFrameNavigation] whether it loads the top frame's document
     * (the only request the shell is served to).
     */
    suspend fun serve(
        method: String,
        url: String,
        acceptsHtml: Boolean,
        mainFrameNavigation: Boolean,
    ): NappletSiteServer.Response? =
        try {
            server?.serve(method, url, acceptsHtml, mainFrameNavigation)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not serve $url", e)
            null
        }

    /** A navigation in [frame]: true lets it load. A link the user clicked is opened outside here. */
    fun onNavigation(
        url: String,
        frame: NappletFrame,
        userGesture: Boolean,
    ): Boolean =
        when (val decision = NappletNavigationPolicy.decide(url, frame, userGesture, launch.profile, plan.origins)) {
            NappletNavigation.Allow -> true
            NappletNavigation.Cancel -> false
            is NappletNavigation.OpenLink -> {
                openLink(decision.url)
                false
            }
        }

    /** A popup the page asked for: never a window of the launch's own. */
    fun onPopup(url: String) {
        (NappletNavigationPolicy.popup(url, launch.profile) as? NappletNavigation.OpenLink)?.let { openLink(it.url) }
    }

    /**
     * A link the user clicked. A front page's own links (a citation, a byline, a stream, a listing)
     * open in the app; everything else goes outside. A webxdc app opens none.
     */
    fun openLink(url: String) {
        if (!launch.profile.opensLinks) return
        val nostr = if (launch.profile == HostProfile.PAPER) PaperLinks.toNostrUri(url) else null
        if (nostr != null) links.openNostr(nostr) else links.openExternal(url)
    }

    /** A console line from the page: the shell's refusal to run without the WebRTC guard fails the launch. */
    fun onConsoleMessage(message: String) {
        if (message.contains(NappletWebContract.WEBRTC_PRESENT)) {
            Log.w(TAG) { "The napplet guard did not load; not starting $coordinate" }
            mutableState.value = NappletSessionState.Failed(NappletFailure.ENGINE)
        }
    }

    /** The web content process died: the bridge closes (late replies are dropped) and the screen offers a retry. */
    fun onRendererGone() {
        if (closed) return
        Log.w(TAG) { "The page of $coordinate is gone" }
        page.close()
        routing.close()
        fail(NappletFailure.RENDERER_GONE)
    }

    /** The app's current colors, to a launch that declared THEME. */
    fun pushTheme(colors: NappletThemeColors) {
        if (NappletCapability.THEME in launch.declared) page.deliver(NappletProtocolJson.encodeThemeChanged(colors.background, colors.text, colors.primary))
    }

    /**
     * Ends the launch: the bridge, files, subscriptions, INC, intents, attendance and route go first,
     * then the engine's browser.
     */
    fun close() {
        lock.withLock {
            if (closed) return
            closed = true
        }
        page.close()
        openFiles?.close()
        routing.close()
        if (plan.usesNetwork) route.release()
        scope.cancel()
        onClosed()
        if (::engine.isInitialized) engine.close()
    }

    companion object {
        private const val TAG = "NappletSessionCore"

        /**
         * A launch whose trusted half runs here, next to the page (Desktop, iOS): [NappletSessionRouting]
         * over [broker], keyed by the host's surface [owner] in the shared [services].
         */
        fun <K : Any> inProcess(
            /** The host's surface, which keys the shared [services]. */
            owner: K,
            launch: NappletLaunch,
            /** The account that opened it: the launch keeps signing as this account, whoever is active later. */
            accountPubKey: HexKey,
            plan: NappletLaunchPlan,
            account: Account?,
            broker: NappletBroker,
            services: NappletSessionServices<K>,
            route: NappletRoutePort,
            files: (NappletLaunchPlan) -> NappletFiles,
            shellHtml: suspend () -> ByteArray,
            shimJs: suspend () -> String,
            links: NappletLinkOpener,
            /** NAP-FS's picker and file bytes; null, or a launch that didn't declare FS, gets no FS session. */
            filePort: NappletFilePort?,
            attendedWhenShown: Boolean = false,
            onClosed: () -> Unit = {},
            clock: () -> Long = NappletPageBridge.monotonicMillis(),
        ): NappletSessionCore =
            NappletSessionCore(
                launch = launch,
                plan = plan,
                route = route,
                files = files,
                shellHtml = shellHtml,
                shimJs = shimJs,
                links = links,
                trusted = { scope, deliver ->
                    // NAP-FS: the files the user picked for this launch, gone when it closes.
                    val fsSession = if (filePort != null && NappletCapability.FS in launch.declared) NappletFsSession(filePort) else null
                    NappletSessionRouting(owner, launch, accountPubKey, account, broker, services, fsSession, scope, send = deliver)
                },
                attendedWhenShown = attendedWhenShown,
                onClosed = onClosed,
                clock = clock,
            )
    }
}
