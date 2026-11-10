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

import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.OS
import org.cef.browser.CefRequestContext
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one Chromium (JCEF) the desktop runs napplets in, started the first time a napplet opens.
 *
 * jcefmaven downloads the native bundle for this OS into [installDir] on first use (a few hundred MB
 * unpacked), so the installer does not carry it; [state] reports that progress to the window.
 *
 * Chromium runs without its background networking: no component updates, field trials or Safe
 * Browsing pings leave the machine on a napplet's account, and its startup does not wait on them
 * (behind a proxy that breaks TLS it would wait forever). Every connection it makes goes through
 * [router], which refuses all of them unless an open nSite has asked for Tor or the open web.
 *
 * Its profile lives in [cacheRoot] (`Default/`): a napplet's document is an opaque-origin srcdoc that
 * cannot keep anything there, but an nSite's localStorage, IndexedDB and cookies persist under its
 * own origin, as on Android. Each account's nSite origin is its own ([NappletBlobCache.partitionOf]).
 * Web pages in the in-app browser keep theirs apart by account instead ([webContext]).
 */
class DesktopCefEngine(
    private val installDir: File,
    private val cacheRoot: File,
    /** Chromium's only way out; its route is set by whoever opens nSites. */
    val router: DesktopSocksRouter = DesktopSocksRouter(),
    /**
     * Quits the app. Once Chromium runs on macOS, JCEF takes over the app's Quit (⌘Q, the Dock) and
     * hands it here, so the app quits its usual way and shuts Chromium down in [shutdown].
     */
    private val onQuit: () -> Unit = {},
) {
    sealed interface State {
        data object Idle : State

        /**
         * Fetching or unpacking the native bundle; [percent] is 0–100 while it downloads. Whole
         * percents, so the state changes a hundred times per download, not once per chunk.
         */
        data class Downloading(
            val percent: Int,
        ) : State

        data object Starting : State

        data class Ready(
            val app: CefApp,
        ) : State

        data class Failed(
            val message: String,
            val kind: FailureKind = FailureKind.RETRY,
        ) : State
    }

    /** What the user can do about a [State.Failed]. */
    enum class FailureKind {
        /** Nothing of Chromium is loaded yet: asking again starts it from scratch. */
        RETRY,

        /** Another process's Chromium holds this profile; asking again works once it has quit. */
        PROFILE_IN_USE,

        /** Chromium got far enough to start up in this process, which it does once: only a restart helps. */
        RESTART,
    }

    /** Thrown when [profileInUse] finds another running Chromium on [cacheRoot]. */
    class ProfileInUseException(
        profile: File,
    ) : IllegalStateException("Another process's Chromium is using $profile")

    private val mutex = Mutex()
    private val stopped = AtomicBoolean(false)
    private val webContexts = ConcurrentHashMap<String, CefRequestContext>()

    /**
     * Where the in-app browser's downloads run until they are complete and the user's (see
     * [PageDownloads]): in the app's own cache, so nothing partial reaches the user's folders.
     * Emptied as Chromium starts: none runs across a restart.
     */
    val downloadsInProgress: File = File(cacheRoot, DOWNLOADS_DIR)
    private val policedContexts = HashMap<CefRequestContext, List<String>>()

    @Volatile
    private var pageScripts: List<String> = emptyList()

    /**
     * What every in-app browser page runs at document start, ahead of the page's own scripts: the
     * NIP-07 bridge ([DesktopBrowserBridge.contentScript]) and the scrollbars ([PageScrollbars]). A
     * page lives in an off-the-record context ([webContext]), where Chromium runs no extension, so the
     * page's session installs these itself (`Page.addScriptToEvaluateOnNewDocument`).
     */
    val webPageScripts: List<String> get() = pageScripts
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Starts Chromium if it isn't running (downloading it the first time) and returns it. */
    suspend fun app(): CefApp =
        mutex.withLock {
            (mutableState.value as? State.Ready)?.app ?: run {
                // A second start in this process only throws "Must be called before CefApp is initialized".
                (mutableState.value as? State.Failed)?.takeIf { it.kind == FailureKind.RESTART }?.let { throw IllegalStateException(it.message) }
                // Read here, where it may suspend: the browser's content script carries the shim.
                val shim = NappletWebResources.shimJs().decodeToString()
                pageScripts = listOf(DesktopBrowserBridge.contentScript(shim), PageScrollbars.DOCUMENT_SCRIPT)
                withContext(Dispatchers.IO) { start() }
            }
        }

    private fun start(): CefApp {
        mutableState.value = State.Starting
        var loaded = false
        return try {
            if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) throw IllegalStateException("Cannot create $cacheRoot")
            // Chromium started on a profile another Chromium holds hands its pages to that one (which
            // opens them in a window of its own) and never brings its own context up.
            if (profileInUse(cacheRoot)) throw ProfileInUseException(cacheRoot)
            if (downloadsInProgress.exists() && !downloadsInProgress.deleteRecursively()) Log.w(TAG) { "Could not empty $downloadsInProgress" }
            val builder = CefAppBuilder()
            builder.setInstallDir(installDir)
            builder.setProgressHandler { progress, percent ->
                mutableState.value =
                    when (progress) {
                        EnumProgress.DOWNLOADING -> State.Downloading(percent.toInt().coerceIn(0, 100))
                        EnumProgress.EXTRACTING -> State.Downloading(100)
                        else -> State.Starting
                    }
            }
            // Windows draw their pages natively; a napplet inside a note paints off-screen
            // (OffscreenCefBrowser), which needs this on from the start.
            builder.cefSettings.windowless_rendering_enabled = true
            builder.cefSettings.root_cache_path = cacheRoot.absolutePath
            builder.cefSettings.persist_session_cookies = false
            builder.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
            builder.addJcefArgs(
                *CHROMIUM_ARGS,
                "--proxy-server=socks5://127.0.0.1:${router.port}",
                "--proxy-bypass-list=<-loopback>",
                "--load-extension=${installGuard().absolutePath}",
            )
            val context = ContextSetup()
            builder.setAppHandler(
                object : MavenCefAppHandlerAdapter() {
                    // CEF's UI thread, the only one that may set a preference.
                    override fun onContextInitialized() = context.apply()

                    // macOS Quit, which JCEF intercepts. Left to JCEF, it disposes Chromium on the AWT
                    // thread, which macOS 27 aborts the app for (see MacCefShutdown); true keeps it ours.
                    override fun onBeforeTerminate(): Boolean {
                        onQuit()
                        return true
                    }
                },
            )
            builder.install()
            removeNotificationHelper()
            // Before build(), which loads it from this thread while the user may be clicking.
            if (OS.isMacintosh()) MacMainThread.loadJcef(installDir)
            loaded = true
            val app = builder.build()
            // JCEF initializes Chromium with the first client, and the context comes up after that. No
            // window may load before the policies are in, so a throwaway client starts it and the
            // engine waits for the context here.
            app.createClient().dispose()
            check(context.ready.await(CONTEXT_WAIT_SECONDS, TimeUnit.SECONDS)) { "Chromium's context did not start" }
            check(context.problems.isEmpty()) { "Chromium refused ${context.problems}" }
            // Chromium took SIGTERM, SIGINT and SIGHUP as it started: the JVM's shutdown hooks need them.
            ShutdownSignals.reclaim()
            app.also { mutableState.value = State.Ready(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "Chromium did not start", e)
            val kind =
                when {
                    e is ProfileInUseException -> FailureKind.PROFILE_IN_USE
                    loaded -> FailureKind.RESTART
                    else -> FailureKind.RETRY
                }
            mutableState.value = State.Failed(e.message ?: e::class.simpleName.orEmpty(), kind)
            throw e
        }
    }

    /**
     * Deletes Chromium's macOS notification helper from the bundle before Chromium starts. Started,
     * it asks macOS for permission to post notifications under JCEF's own name ("jcef Helper
     * (Alerts)"), the first time any napplet opens. Napplets are denied notifications anyway, and
     * Chromium runs without the helper. Every start checks, since a JCEF update reinstalls it.
     */
    private fun removeNotificationHelper() {
        if (!OS.isMacintosh()) return
        val helper = File(installDir, NOTIFICATION_HELPER)
        if (helper.exists() && !helper.deleteRecursively()) Log.w(TAG) { "Could not remove $helper" }
    }

    /**
     * Writes the napplet guard, fresh on every start, and returns its folder: an extension whose content
     * scripts run at document start in every frame, the ones a page makes included, which no script of
     * the page itself can reach:
     * - [NappletWebContract.WEBRTC_GUARD_JS] in the shell and everything under it (the napplet's srcdoc)
     *   and in every webxdc app's frames ([NappletWebContract.WEBXDC_HOST]): neither may have a network.
     *   nSites, on their own origins, keep WebRTC: their traffic is theirs to route.
     * - [NappletWebContract.COOKIE_GUARD_JS] everywhere under napplet.local, which is one site for every
     *   app, account and route here.
     * - [PageScrollbars.CSS] in every frame: its scrollbars, in the app's colors.
     *
     * The in-app browser's pages live in contexts this extension doesn't reach ([webContext]); they get
     * their scripts from [webPageScripts].
     */
    private fun installGuard(): File {
        val dir = File(cacheRoot, GUARD_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("Cannot create $dir")
        File(dir, "manifest.json").writeText(GUARD_MANIFEST)
        File(dir, "guard.js").writeText(NappletWebContract.WEBRTC_GUARD_JS)
        File(dir, "cookies.js").writeText(NappletWebContract.COOKIE_GUARD_JS)
        // An older start's: the browser's pages install their own scripts now.
        File(dir, "browser.js").let { if (it.exists() && !it.delete()) Log.w(TAG) { "Could not delete $it" } }
        File(dir, "scrollbars.css").writeText(PageScrollbars.CSS)
        return dir
    }

    /**
     * Sets [CHROMIUM_PREFERENCES] on the global context. Chromium's process-wide switches can't do
     * this: the WebRTC switch is ignored by CEF, and a refused preference means a policy we rely on
     * is missing, so the engine then fails to start rather than run without it.
     */
    private class ContextSetup {
        val ready = CountDownLatch(1)

        @Volatile
        var problems: List<String> = emptyList()
            private set

        fun apply() {
            try {
                val context = CefRequestContext.getGlobalContext()
                problems = CHROMIUM_PREFERENCES.mapNotNull { (name, value) -> context.setPreference(name, value)?.let { "$name: $it" } }
                problems.forEach { Log.w(TAG) { "Chromium refused a preference: $it" } }
            } finally {
                ready.countDown()
            }
        }
    }

    /**
     * Where the in-app browser's pages for [account] keep their cookies, storage and cache: one jar per
     * account and route, so a site's login or localStorage from one account is never there when another
     * opens it, and a Tor page never sends a cookie a site set on the open web. The reverse isn't kept:
     * while any Tor site is open, [DesktopNappletRoutes] sends every page through Tor (Tor always wins),
     * an open-web page included, with its jar's cookies; when the Tor site closes they go out directly
     * again, so that site can link the two visits.
     *
     * In memory, while the account is the one in use ([retainWebContexts]): JCEF can make no other kind
     * of context than Chromium's off-the-record one (it passes no cache path), so nothing a page stores
     * reaches disk. NIP-07
     * grants are the account's own and persist, so a Nostr site logs in again with a click. Chromium
     * runs no extension there: each page's session sets [CHROMIUM_PREFERENCES] on it ([applyPolicies])
     * and installs [webPageScripts] itself.
     */
    fun webContext(
        account: String,
        overTor: Boolean,
    ): CefRequestContext {
        check(state.value is State.Ready) { "Chromium is not running" }
        return webContexts.computeIfAbsent(jarOf(account, overTor)) { CefRequestContext.createContext(null) }
    }

    private fun jarOf(
        account: String,
        overTor: Boolean,
    ) = if (overTor) "$account/tor" else "$account/web"

    /**
     * Sets [CHROMIUM_PREFERENCES] on [context] the first time a page of it is created, and returns
     * what Chromium refused (a page must not load then). On Chromium's UI thread, the only one that may
     * set a preference: a page's `onAfterCreated`.
     */
    fun applyPolicies(context: CefRequestContext): List<String> =
        synchronized(policedContexts) {
            policedContexts.getOrPut(context) {
                CHROMIUM_PREFERENCES.mapNotNull { (name, value) -> context.setPreference(name, value)?.let { "$name: $it" } }.also { refused ->
                    refused.forEach { Log.w(TAG) { "Chromium refused a preference for a web context: $it" } }
                }
            }
        }

    /**
     * Drops every account's jars but [account]'s (their pages must be closed): an account switched
     * away from, logged off or deleted starts with empty ones next time. Only the account in use keeps
     * memory for its sites; the others' pages closed with the switch anyway.
     */
    fun retainWebContexts(account: String?) {
        webContexts.keys.filter { account == null || !it.startsWith("$account/") }.forEach(::forget)
    }

    private fun forget(jar: String) {
        webContexts.remove(jar)?.let { context ->
            synchronized(policedContexts) { policedContexts.remove(context) }
            runCatching { context.dispose() }.onFailure { Log.w(TAG, "Could not drop a web context", it) }
        }
    }

    /**
     * Closes Chromium's processes; called as the app exits, before the JVM does, so that jcefmaven's
     * own exit hook finds nothing left to do. Bounded: CEF's teardown waits on the AWT event thread,
     * which may already be gone, and must not keep the JVM from exiting.
     */
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) return
        val app = (mutableState.value as? State.Ready)?.app
        webContexts.keys.toList().forEach(::forget)
        if (app != null) {
            val dispose = { if (OS.isMacintosh()) MacCefShutdown.shutdown(app) else app.dispose() }
            val teardown =
                Thread({ runCatching(dispose).onFailure { Log.w(TAG, "Chromium did not shut down cleanly", it) } }, "cef-shutdown")
            teardown.isDaemon = true
            teardown.start()
            teardown.join(SHUTDOWN_WAIT_MS)
        }
        router.close()
    }

    companion object {
        private const val TAG = "DesktopCefEngine"
        private const val SHUTDOWN_WAIT_MS = 3_000L
        private const val CONTEXT_WAIT_SECONDS = 60L

        /**
         * Whether a running Chromium holds [profile]. On macOS and Linux Chromium keeps a
         * `SingletonSocket` link there to the socket it listens on for other starts; one that accepts
         * a connection is alive, while a crashed one leaves a link nobody answers. Windows keeps no
         * such link, so there it is always false.
         */
        internal fun profileInUse(profile: File): Boolean {
            val socket =
                try {
                    Files.readSymbolicLink(File(profile, "SingletonSocket").toPath())
                } catch (_: IOException) {
                    return false
                } catch (_: UnsupportedOperationException) {
                    return false
                }
            return try {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { it.connect(UnixDomainSocketAddress.of(socket)) }
                true
            } catch (_: IOException) {
                false
            }
        }

        /**
         * Profile preferences every page runs under, applied before any window loads:
         * - WebRTC sends UDP only through the proxy, which a SOCKS proxy can't carry, so a page's peer
         *   connections can't go around [router] (CSP `connect-src` doesn't cover them).
         * - Third-party cookies are blocked, as Android's WebView does by default: one profile serves
         *   every napplet and nSite, on both routes, so a cookie an embed or API sets must not follow the
         *   user from one site, or from the open web, to another. The in-app browser's jars
         *   ([webContext]) get the same.
         * - Network prediction is off (2 = never): `<link rel=dns-prefetch|preconnect>` is outside CSP,
         *   and a page with no network could spell what it read into a hostname and have it resolved.
         */
        val CHROMIUM_PREFERENCES: Map<String, Any> =
            mapOf(
                "webrtc.ip_handling_policy" to "disable_non_proxied_udp",
                "profile.cookie_controls_mode" to 1,
                "net.network_prediction_options" to 2,
            )

        private const val GUARD_DIR = "napplet-guard"
        private const val DOWNLOADS_DIR = "downloads-in-progress"

        private const val NOTIFICATION_HELPER = "jcef Helper (Alerts).app"

        // match_origin_as_fallback reaches the opaque srcdoc and about:blank frames under the shell.
        private val GUARD_MANIFEST =
            """
            {
              "manifest_version": 3,
              "name": "Amethyst napplet guard",
              "version": "1.0",
              "content_scripts": [{
                "matches": ["${NappletWebContract.ORIGIN}/*", "https://*.${NappletWebContract.WEBXDC_HOST}/*"],
                "js": ["guard.js"],
                "all_frames": true,
                "match_about_blank": true,
                "match_origin_as_fallback": true,
                "run_at": "document_start",
                "world": "MAIN"
              }, {
                "matches": ["${NappletWebContract.ORIGIN}/*", "https://*.${NappletWebContract.HOST}/*"],
                "js": ["cookies.js"],
                "all_frames": true,
                "match_about_blank": true,
                "match_origin_as_fallback": true,
                "run_at": "document_start",
                "world": "MAIN"
              }, {
                "matches": ["<all_urls>"],
                "css": ["scrollbars.css"],
                "all_frames": true,
                "match_about_blank": true,
                "match_origin_as_fallback": true,
                "run_at": "document_start"
              }]
            }
            """.trimIndent()

        private val CHROMIUM_ARGS =
            arrayOf(
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-sync",
                "--no-first-run",
                // One list: Chromium keeps only the last of a repeated switch. The guard extension is
                // loaded from the command line, which newer Chromium turns off by default.
                "--disable-features=Translate,OptimizationHints,MediaRouter,DialMediaRouteProvider,NetworkTimeServiceQuerying,DisableLoadExtensionCommandLineSwitch",
                // Thin scrollbars that show while the page scrolls and fade, as on ChromeOS and Android,
                // for whatever PageScrollbars.CSS doesn't reach, instead of the classic desktop track.
                "--enable-features=OverlayScrollbar",
                // No camera, microphone, location or notification prompts: a page asking is told no,
                // as Android's host answers.
                "--deny-permission-prompts",
                // No OS keyring prompt for a profile that stores nothing worth encrypting.
                "--password-store=basic",
                "--use-mock-keychain",
                // Game audio starts on the napplet's own Play tap; nothing autoplays from the feed.
                "--autoplay-policy=user-gesture-required",
            )
    }
}
