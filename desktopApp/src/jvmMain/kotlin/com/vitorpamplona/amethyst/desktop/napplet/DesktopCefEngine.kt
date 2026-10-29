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
import java.io.File

/**
 * The one Chromium (JCEF) the desktop runs napplets in, started the first time a napplet opens.
 *
 * jcefmaven downloads the native bundle for this OS into [installDir] on first use (a few hundred MB
 * unpacked), so the installer does not carry it; [state] reports that progress to the window.
 *
 * Chromium runs without its background networking: no component updates, field trials or Safe
 * Browsing pings leave the machine on a napplet's account, and its startup does not wait on them
 * (behind a proxy that breaks TLS it would wait forever). Nothing is cached to disk: a napplet's
 * document is an opaque-origin srcdoc that cannot keep anything, and its storage is the broker's.
 */
class DesktopCefEngine(
    private val installDir: File,
    private val cacheRoot: File,
) {
    sealed interface State {
        data object Idle : State

        /** Fetching or unpacking the native bundle; [percent] is 0–100 while it downloads. */
        data class Downloading(
            val percent: Float,
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
                        EnumProgress.DOWNLOADING -> State.Downloading(percent.coerceIn(0f, 100f))
                        EnumProgress.EXTRACTING -> State.Downloading(100f)
                        else -> State.Starting
                    }
            }
            builder.cefSettings.windowless_rendering_enabled = false
            builder.cefSettings.root_cache_path = cacheRoot.absolutePath
            builder.cefSettings.persist_session_cookies = false
            builder.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
            builder.addJcefArgs(*CHROMIUM_ARGS)
            builder.setAppHandler(object : MavenCefAppHandlerAdapter() {})
            builder.build().also { mutableState.value = State.Ready(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "Chromium did not start", e)
            mutableState.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
            throw e
        }
    }

    /**
     * Closes Chromium's processes; called once, as the app exits. Bounded: CEF's teardown waits on
     * the AWT event thread, which may already be gone, and must not keep the JVM from exiting.
     */
    fun shutdown() {
        val app = (mutableState.value as? State.Ready)?.app ?: return
        val teardown =
            Thread({ runCatching { app.dispose() }.onFailure { Log.w(TAG, "Chromium did not shut down cleanly", it) } }, "cef-shutdown")
        teardown.isDaemon = true
        teardown.start()
        teardown.join(SHUTDOWN_WAIT_MS)
    }

    companion object {
        private const val TAG = "DesktopCefEngine"
        private const val SHUTDOWN_WAIT_MS = 3_000L

        private val CHROMIUM_ARGS =
            arrayOf(
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-sync",
                "--no-first-run",
                "--disable-features=Translate,OptimizationHints,MediaRouter,DialMediaRouteProvider,NetworkTimeServiceQuerying",
                // Chromium never needs the network here: the shell is served from memory and blobs come
                // through the app. Its own services (account checks, search prefetch, DNS over HTTPS)
                // ignore the flags above, so every request goes to a proxy that does not exist,
                // loopback included.
                "--proxy-server=socks5://127.0.0.1:9",
                "--proxy-bypass-list=<-loopback>",
                // No OS keyring prompt for a profile that stores nothing worth encrypting.
                "--password-store=basic",
                "--use-mock-keychain",
                // Game audio starts on the napplet's own Play tap; nothing autoplays from the feed.
                "--autoplay-policy=user-gesture-required",
            )
    }
}
