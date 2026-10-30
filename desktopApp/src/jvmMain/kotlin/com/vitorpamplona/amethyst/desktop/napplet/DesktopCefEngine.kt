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
 * own origin, as on Android.
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
        ) : State
    }

    private val mutex = Mutex()
    private val stopped = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Starts Chromium if it isn't running (downloading it the first time) and returns it. */
    suspend fun app(): CefApp =
        mutex.withLock {
            (mutableState.value as? State.Ready)?.app ?: withContext(Dispatchers.IO) { start() }
        }

    private fun start(): CefApp {
        mutableState.value = State.Starting
        return try {
            if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) throw IllegalStateException("Cannot create $cacheRoot")
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
            val app = builder.build()
            // JCEF initializes Chromium with the first client, and the context comes up after that. No
            // window may load before the policies are in, so a throwaway client starts it and the
            // engine waits for the context here.
            app.createClient().dispose()
            check(context.ready.await(CONTEXT_WAIT_SECONDS, TimeUnit.SECONDS)) { "Chromium's context did not start" }
            check(context.problems.isEmpty()) { "Chromium refused ${context.problems}" }
            app.also { mutableState.value = State.Ready(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "Chromium did not start", e)
            mutableState.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
            throw e
        }
    }

    /**
     * Writes the napplet guard, fresh on every start, and returns its folder: an extension whose content
     * script ([NappletWebContract.WEBRTC_GUARD_JS]) runs at document start in the shell and every frame
     * under it, the napplet's srcdoc and any frame it makes, which no script of the page itself can
     * reach. nSites, on their own origins, keep WebRTC: their traffic is theirs to route.
     */
    private fun installGuard(): File {
        val dir = File(cacheRoot, GUARD_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("Cannot create $dir")
        File(dir, "manifest.json").writeText(GUARD_MANIFEST)
        File(dir, "guard.js").writeText(NappletWebContract.WEBRTC_GUARD_JS)
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
     * Closes Chromium's processes; called as the app exits, before the JVM does, so that jcefmaven's
     * own exit hook finds nothing left to do. Bounded: CEF's teardown waits on the AWT event thread,
     * which may already be gone, and must not keep the JVM from exiting.
     */
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) return
        val app = (mutableState.value as? State.Ready)?.app
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
         * Profile preferences every page runs under, applied before any window loads:
         * - WebRTC sends UDP only through the proxy, which a SOCKS proxy can't carry, so a page's peer
         *   connections can't go around [router] (CSP `connect-src` doesn't cover them).
         * - Third-party cookies are blocked, as Android's WebView does by default: one profile serves
         *   every account and both routes here, so a cookie an embed or API sets must not follow the
         *   user from one account, or from the open web, to another.
         */
        val CHROMIUM_PREFERENCES: Map<String, Any> =
            mapOf(
                "webrtc.ip_handling_policy" to "disable_non_proxied_udp",
                "profile.cookie_controls_mode" to 1,
            )

        private const val GUARD_DIR = "napplet-guard"

        // match_origin_as_fallback reaches the opaque srcdoc and about:blank frames under the shell.
        private val GUARD_MANIFEST =
            """
            {
              "manifest_version": 3,
              "name": "Amethyst napplet guard",
              "version": "1.0",
              "content_scripts": [{
                "matches": ["${NappletWebContract.ORIGIN}/*"],
                "js": ["guard.js"],
                "all_frames": true,
                "match_about_blank": true,
                "match_origin_as_fallback": true,
                "run_at": "document_start",
                "world": "MAIN"
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
