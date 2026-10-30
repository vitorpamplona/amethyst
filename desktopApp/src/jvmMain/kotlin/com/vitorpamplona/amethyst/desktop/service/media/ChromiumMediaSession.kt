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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.desktop.napplet.DesktopCefEngine
import com.vitorpamplona.amethyst.desktop.napplet.OffscreenNappletView
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.OffscreenCefBrowser
import org.cef.callback.CefCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
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
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.InputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * A video or audio file the native engines can't decode, played by the app's Chromium instead: a
 * page with a `<video>` (or, for [audioOnly], an `<audio>`) in an off-screen browser. A video's
 * frames [view] hands to the note like an in-note napplet's; [clock] reports where either is.
 *
 * Chromium decodes VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 (no H.264, HEVC or AAC), which covers
 * what AVFoundation on macOS can't play: WebM. The page and the video are served from an internal
 * origin by this class, and the video's bytes come from [MediaRelay], so they take the same route
 * (Tor or not) and land in the same disk cache as the native engine's; Chromium itself never reaches
 * the network for it.
 */
class ChromiumMediaSession(
    private val engine: DesktopCefEngine,
    private val url: String,
    /** Whether the video starts without sound, as the app's own player would. */
    private val muted: Boolean,
    private val audioOnly: Boolean = false,
    /** Where it starts, in milliseconds. */
    private val startMillis: Long = 0L,
) {
    data class Clock(
        val currentMillis: Long = 0L,
        val durationMillis: Long = 0L,
        val paused: Boolean = true,
        val waiting: Boolean = true,
        val ended: Boolean = false,
    )

    sealed interface State {
        data object Loading : State

        data object Playing : State

        data class Failed(
            val reason: String,
        ) : State
    }

    val view = OffscreenNappletView()

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutableRatio = MutableStateFlow<Float?>(null)

    /** The video's width over its height, once Chromium has read it. */
    val ratio: StateFlow<Float?> = mutableRatio.asStateFlow()

    private val mutableClock = MutableStateFlow(Clock())
    val clock: StateFlow<Clock> = mutableClock.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Any()
    private var client: CefClient? = null
    private var browser: CefBrowser? = null
    private var created = false
    private var closed = false

    // A close asked for before Chromium made the browser runs once it has (see CefNappletSession).
    private var pendingTeardown: (() -> Unit)? = null

    fun open() {
        scope.launch {
            try {
                val app = engine.app()
                synchronized(lifecycle) {
                    if (closed) return@launch
                    val client = app.createClient()
                    this@ChromiumMediaSession.client = client
                    installHandlers(client)
                    browser =
                        OffscreenCefBrowser(client, PAGE_URL, null, FRAME_RATE, view).also {
                            view.browser = it
                            it.createImmediately()
                        }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Chromium did not open $url", e)
                mutableState.value = State.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    private fun installHandlers(client: CefClient) {
        client.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean = request.url != PAGE_URL

                override fun getResourceRequestHandler(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef,
                ): CefResourceRequestHandler = pageHandler
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onAfterCreated(browser: CefBrowser) {
                    val teardown =
                        synchronized(lifecycle) {
                            created = true
                            pendingTeardown.also { pendingTeardown = null }
                        }
                    teardown?.let { SwingUtilities.invokeLater(it) }
                }

                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String,
                    targetFrameName: String?,
                ): Boolean = true
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(
                    browser: CefBrowser,
                    frame: CefFrame,
                    httpStatusCode: Int,
                ) {
                    // Chromium lets a page start a video with sound only from the user's own input.
                    // The user already asked for this one in the note, so a click on the page's
                    // start layer passes that request on.
                    if (frame.isMain) SwingUtilities.invokeLater(::clickToStart)
                }
            },
        )
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    browser: CefBrowser,
                    level: CefSettings.LogSeverity,
                    message: String,
                    source: String,
                    line: Int,
                ): Boolean {
                    when {
                        message.startsWith(PLAYING) -> mutableState.value = State.Playing
                        message.startsWith(CLOCK) -> parseClock(message.removePrefix(CLOCK))?.let { mutableClock.value = it }
                        message.startsWith(SIZE) -> {
                            val (w, h) =
                                message
                                    .removePrefix(SIZE)
                                    .trim()
                                    .split(' ')
                                    .map { it.toIntOrNull() ?: 0 } + listOf(0, 0)
                            if (w > 0 && h > 0) mutableRatio.value = w.toFloat() / h
                        }
                        message.startsWith(FAILED) -> {
                            val reason = message.removePrefix(FAILED).trim()
                            Log.w(TAG) { "Chromium can't play $url either: $reason" }
                            mutableState.value = State.Failed(reason)
                        }
                    }
                    return true
                }
            },
        )
    }

    /**
     * Pauses or resumes the video, as a feed does with the card that leaves or takes the middle of
     * the screen. The first click already gave the page the user's go-ahead, so it may play again.
     */
    fun setPlaying(playing: Boolean) {
        wantsPlaying = playing
        val browser = synchronized(lifecycle) { browser } ?: return
        browser.executeJavaScript("window.setWanted($playing);", PAGE_URL, 0)
    }

    // Whether the video should play once the page has it; the page reads it when it is served.
    @Volatile private var wantsPlaying = true

    private fun parseClock(fields: String): Clock? {
        val parts = fields.trim().split(' ')
        if (parts.size < 5) return null
        val current = parts[0].toDoubleOrNull() ?: return null
        val duration = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        return Clock((current * 1000).toLong(), (duration * 1000).toLong(), parts[2] == "1", parts[3] == "1", parts[4] == "1")
    }

    /** Moves to [millis] from the start. */
    fun seekTo(millis: Long) = run("v.currentTime=${millis / 1000.0};")

    /** [volume] from 0 to 1. */
    fun setVolume(volume: Float) = run("v.volume=${volume.coerceIn(0f, 1f)};v.muted=false;")

    fun setRate(rate: Float) = run("v.playbackRate=$rate;")

    private fun run(script: String) {
        val browser = synchronized(lifecycle) { browser } ?: return
        browser.executeJavaScript("(function(){const v=document.getElementById('v');$script})();", PAGE_URL, 0)
    }

    private fun clickToStart() {
        val x = 1
        val y = 1
        val press = MouseEvent(EVENT_SOURCE, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1)
        view.mouse(press, MouseEvent.MOUSE_PRESSED, x, y)
        val release = MouseEvent(EVENT_SOURCE, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1)
        view.mouse(release, MouseEvent.MOUSE_RELEASED, x, y)
    }

    private val pageHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): CefResourceHandler =
                when (request.url) {
                    PAGE_URL -> PageHandler(page(muted, wantsPlaying, if (audioOnly) "audio" else "video", startMillis))
                    MEDIA_URL -> RelayedMediaHandler()
                    else -> PageHandler(null)
                }
        }

    /** The page itself, or a 404 for anything else on the origin. */
    private class PageHandler(
        private val html: ByteArray?,
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
            response.status = if (html != null) 200 else 404
            response.mimeType = "text/html"
            responseLength.set(html?.size ?: 0)
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            val body = html ?: return false.also { bytesRead.set(0) }
            val n = minOf(bytesToRead, body.size - offset)
            if (n <= 0) {
                bytesRead.set(0)
                return false
            }
            System.arraycopy(body, offset, dataOut, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }
    }

    /**
     * The video, fetched through [MediaRelay] with Chromium's own range, so seeking works. Reads
     * happen off Chromium's IO thread: a slow Tor circuit would otherwise stall every page.
     */
    private inner class RelayedMediaHandler : CefResourceHandlerAdapter() {
        @Volatile private var response: Response? = null
        private var body: InputStream? = null

        // A chunk read ahead, waiting for Chromium to ask for it.
        private var chunk: ByteArray? = null
        private var chunkSize = 0
        private var chunkOffset = 0
        private var ended = false

        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            val relayed = MediaRelay.relayUrlFor(url) ?: return false
            val range = request.getHeaderByName("Range")?.takeIf { it.isNotEmpty() }
            scope.launch {
                try {
                    val call =
                        Request
                            .Builder()
                            .url(relayed)
                            .apply { range?.let { header("Range", it) } }
                            .build()
                    response = loopbackHttp.newCall(call).execute().also { body = it.body.byteStream() }
                } catch (e: Exception) {
                    Log.d(TAG) { "Relay of $url for Chromium failed: ${e.message}" }
                } finally {
                    callback.Continue()
                }
            }
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            val upstream = this.response
            if (upstream == null) {
                response.status = 502
                responseLength.set(0)
                return
            }
            response.status = upstream.code
            response.statusText = upstream.message
            response.mimeType = upstream.header("Content-Type")?.substringBefore(';')?.trim() ?: "video/webm"
            FORWARDED_HEADERS.forEach { name -> upstream.header(name)?.let { response.setHeaderByName(name, it, true) } }
            val length = upstream.body.contentLength()
            responseLength.set(if (length in 0..Int.MAX_VALUE) length.toInt() else -1)
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            synchronized(this) {
                val ready = chunk
                if (ready != null) {
                    val n = minOf(bytesToRead, chunkSize - chunkOffset)
                    System.arraycopy(ready, chunkOffset, dataOut, 0, n)
                    chunkOffset += n
                    if (chunkOffset >= chunkSize) chunk = null
                    bytesRead.set(n)
                    return true
                }
                if (ended || body == null) {
                    bytesRead.set(0)
                    cancel()
                    return false
                }
            }
            // Nothing read ahead: read the next chunk off this thread, and have Chromium ask again.
            scope.launch {
                val buffer = ByteArray(bytesToRead.coerceAtLeast(MIN_CHUNK))
                val n = runCatching { body?.read(buffer) ?: -1 }.getOrDefault(-1)
                synchronized(this@RelayedMediaHandler) {
                    if (n <= 0) {
                        ended = true
                    } else {
                        chunk = buffer
                        chunkSize = n
                        chunkOffset = 0
                    }
                }
                callback.Continue()
            }
            bytesRead.set(0)
            return true
        }

        override fun cancel() {
            synchronized(this) {
                ended = true
                chunk = null
            }
            runCatching { response?.close() }
        }
    }

    fun close() {
        val client: CefClient?
        val browser: CefBrowser?
        val made: Boolean
        synchronized(lifecycle) {
            if (closed) return
            closed = true
            client = this.client
            browser = this.browser
            this.client = null
            this.browser = null
            made = created
        }
        view.browser = null
        scope.cancel()
        val teardown = {
            runCatching {
                // The page goes first, so its sound stops at once whatever Chromium then does.
                browser?.loadURL("about:blank")
                browser?.setCloseAllowed()
                browser?.close(true)
                client?.dispose()
            }.onFailure { Log.w(TAG, "Video browser did not close cleanly", it) }
            Unit
        }
        if (browser != null && !made) {
            val now =
                synchronized(lifecycle) {
                    if (created) {
                        true
                    } else {
                        pendingTeardown = teardown
                        false
                    }
                }
            if (!now) return
        }
        if (SwingUtilities.isEventDispatchThread()) teardown() else SwingUtilities.invokeLater(teardown)
    }

    companion object {
        private const val TAG = "ChromiumMediaSession"
        private const val FRAME_RATE = 30
        private const val MIN_CHUNK = 64 * 1024
        private const val PLAYING = "amethyst-video-playing"
        private const val FAILED = "amethyst-video-failed"
        private const val SIZE = "amethyst-video-size"
        private const val CLOCK = "amethyst-media-clock"

        private val ORIGIN = "https://video.${NappletWebContract.HOST}"
        private val PAGE_URL = "$ORIGIN/"
        private val MEDIA_URL = "$ORIGIN/media"

        private val FORWARDED_HEADERS = listOf("Content-Type", "Content-Range", "Accept-Ranges", "Content-Length")

        // Every event forwarded to Chromium needs a source; it reads only the event's fields.
        private val EVENT_SOURCE = Canvas()

        // Only ever talks to MediaRelay on loopback, which does the real fetching and routing.
        private val loopbackHttp by lazy {
            OkHttpClient
                .Builder()
                .proxy(Proxy.NO_PROXY)
                .readTimeout(2, TimeUnit.MINUTES)
                .build()
        }

        /** Whether Chromium may be worth a try for [url]: a plain http(s) file, not a stream. */
        fun canTry(url: String): Boolean = (url.startsWith("https://") || url.startsWith("http://")) && !isHlsMedia(url, null)

        /**
         * A black page with the video and Chromium's own controls. A transparent layer over it takes
         * the first click (see clickToStart), sets the sound, starts the video if it is [wanted] by
         * then, and goes away. A page opened ahead of time ([wanted] false) buffers and shows its
         * first frame until setWanted(true).
         */
        private fun page(
            muted: Boolean,
            wanted: Boolean,
            tag: String,
            startMillis: Long,
        ): ByteArray =
            """
            <!doctype html><meta charset="utf-8">
            <style>
              html,body{margin:0;height:100%;background:#000;overflow:hidden}
              video{width:100%;height:100%;object-fit:contain;display:block}
              #start{position:fixed;inset:0;z-index:1}
            </style>
            <$tag id="v" controls playsinline preload="auto" src="/media"></$tag>
            <div id="start"></div>
            <script>
              const v = document.getElementById('v');
              const start = document.getElementById('start');
              v.addEventListener('playing', () => console.log('$PLAYING'));
              v.addEventListener('loadedmetadata', () => {
                if ($startMillis > 0) v.currentTime = ${startMillis / 1000.0};
                console.log('$SIZE ' + (v.videoWidth || 0) + ' ' + (v.videoHeight || 0));
              });
              let waiting = true;
              const clock = () => console.log('$CLOCK ' + v.currentTime + ' ' + v.duration + ' ' + (v.paused ? 1 : 0) + ' ' + (waiting ? 1 : 0) + ' ' + (v.ended ? 1 : 0));
              v.addEventListener('waiting', () => { waiting = true; clock(); });
              v.addEventListener('playing', () => { waiting = false; clock(); });
              v.addEventListener('canplay', () => { waiting = false; clock(); });
              ['timeupdate', 'durationchange', 'pause', 'play', 'ended', 'seeked'].forEach(e => v.addEventListener(e, clock));
              v.addEventListener('error', () => console.log('$FAILED ' + (v.error ? v.error.code + ' ' + v.error.message : '')));
              let wanted = $wanted;
              let activated = false;
              const go = () => v.play().catch(e => { v.muted = true; v.play().catch(() => {}); });
              window.setWanted = (w) => {
                wanted = w;
                if (!activated) return;
                if (w) go(); else v.pause();
              };
              start.addEventListener('click', () => {
                start.remove();
                activated = true;
                v.muted = $muted;
                if (wanted) go();
              });
            </script>
            """.trimIndent().toByteArray()
    }
}
