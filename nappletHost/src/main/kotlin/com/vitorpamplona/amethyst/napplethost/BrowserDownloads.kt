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

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Saves what a page downloads — `<a download>`, `Content-Disposition: attachment`, `data:` URLs, and
 * `blob:` URLs (which only the page can read, so the browser-extras script hands their bytes over) — into
 * the system Downloads collection, the way Chrome does.
 *
 * Page-initiated saves (both the `browser.download` bridge envelope and the WebView `DownloadListener`)
 * reach the write sinks here only through [BrowserDownloadGate]'s one-shot consent; the
 * immediately-user-initiated "Download image" context-menu action is the one exception.
 *
 * Network downloads follow the page's own route: through the Tor SOCKS proxy when the site is on Tor (OkHttp
 * leaves SOCKS hosts unresolved, so even DNS goes through Tor), directly otherwise. They carry the page's
 * cookies from its own per-account storage profile and its user agent, so a logged-in download works.
 */
object BrowserDownloads {
    private const val TAG = "BrowserDownloads"

    /** Cap for bytes a page hands over for a blob:/data: download (they travel as base64 over the bridge). */
    const val MAX_INLINE_BYTES = 25 * 1024 * 1024

    private val io = Executors.newSingleThreadExecutor { Thread(it, "napplet-downloads").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /**
     * The immediately-user-initiated download (the long-press "Download image" context-menu item): that
     * tap IS the gesture, so this brushes past the consent prompt. The transfer itself runs on a
     * background thread.
     */
    fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookie: String?,
        proxyPort: Int,
    ) {
        val app = context.applicationContext
        if (url.startsWith("data:", ignoreCase = true)) {
            saveDataUrl(app, url, null)
            return
        }
        if (!isHttp(url)) return
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        toast(app, app.getString(CommonsR.string.browser_download_started, name))
        io.execute {
            runNetworkDownload(app, url, name, userAgent, mimeType, cookie, proxyPort)
        }
    }

    /**
     * A WebView `DownloadListener` hit — a PAGE-initiated save (a `<a download>` navigation or
     * `Content-Disposition: attachment`), so it routes through the one consent gate exactly like the
     * `browser.download` bridge envelope. [cookieHolder] is invoked (main-thread, on the WebView's own
     * profile) when cookies are needed; the transfer itself only runs when the user allows — nothing is
     * fetched until then.
     */
    fun downloadWithConsent(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        cookieHolder: () -> String?,
        userAgent: String?,
        proxyPort: Int,
        showPrompt: (fileName: String, sizeBytes: Long, risky: Boolean, consent: Consent) -> Unit,
    ) {
        val app = context.applicationContext
        if (url.startsWith("data:", ignoreCase = true)) {
            // A data: URL from the listener is the same forgeable surface as a bridge envelope: gate it.
            val save = BrowserDownloadGate.preludeInlineSave(url, null) ?: return
            showPrompt(save.fileName, save.bytes.size.toLong(), BrowserDownloadGate.isRisky(save.fileName), Consent { save.save(app) })
            return
        }
        if (!isHttp(url)) return
        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        io.execute {
            // Ask the server for the exact size up front (fast-timed-out HEAD): a host that won't say
            // leaves it -1 and the card shows the name alone. The GET itself stays unbounded — a large
            // file over Tor takes minutes; only this probe is bounded.
            val size = runCatching { probeContentLength(url, userAgent, cookieHolder(), proxyPort) }.getOrDefault(-1L)
            main.post {
                showPrompt(
                    sanitize(guessedName, mimeType),
                    size,
                    BrowserDownloadGate.isRisky(guessedName),
                    Consent {
                        io.execute { runNetworkDownload(app, url, guessedName, userAgent, mimeType, cookieHolder(), proxyPort) }
                    },
                )
            }
        }
    }

    /**
     * The action the consent card's Save button runs: the transfer fires only on an explicit allow, so
     * bytes are never pulled into a prompt closure before the user has said yes.
     */
    fun interface Consent {
        fun run()
    }

    private fun isHttp(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    /** The actual transfer + toasts; runs on [io]. Shared by the consent prompt's allow and the context menu. */
    private fun runNetworkDownload(
        app: Context,
        url: String,
        name: String,
        userAgent: String?,
        mimeType: String?,
        cookie: String?,
        proxyPort: Int,
    ) {
        val ok =
            runCatching {
                val request = request(url, userAgent, cookie).get().build()
                val client = client(proxyPort)
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val type = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: response.body.contentType()?.let { "${it.type}/${it.subtype}" }
                    write(app, name, type) { out -> response.body.byteStream().use { it.copyTo(out) } }
                }
            }.onFailure { Log.w(TAG, "Download failed for $url", it) }
                .getOrDefault(false)
        toast(app, app.getString(if (ok) CommonsR.string.browser_download_saved else CommonsR.string.browser_download_failed, name))
    }

    /** The Content-Length a HEAD to [url] reports, or -1 when the server won't say / the probe fails. */
    private fun probeContentLength(
        url: String,
        userAgent: String?,
        cookie: String?,
        proxyPort: Int,
    ): Long {
        val request = request(url, userAgent, cookie).head().build()
        // A small-BODY-time probe (not the unbounded transfer the eventual GET gets). A server that
        // answers slowly leaves the size unknown rather than holding the prompt; a server that breaks
        // on HEAD simply never fills it in.
        val client =
            NappletBlobHttp
                .client(proxyPort)
                .newBuilder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.contentLength()
        }
    }

    /** The OkHttp client for transfers through [proxyPort]: no end-to-end call timeout, as documented above. */
    private fun client(proxyPort: Int) =
        NappletBlobHttp
            .client(proxyPort)
            .newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .build()

    private fun request(
        url: String,
        userAgent: String?,
        cookie: String?,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .apply {
                userAgent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                cookie?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }

    /** Saves a `data:` URL (`data:[mime][;base64],payload`). */
    fun saveDataUrl(
        context: Context,
        dataUrl: String,
        suggestedName: String?,
    ) {
        val app = context.applicationContext
        val header = dataUrl.substringBefore(',', "")
        val payload = dataUrl.substringAfter(',', "")
        val mime = header.removePrefix("data:").substringBefore(';').ifBlank { "application/octet-stream" }
        val bytes =
            runCatching {
                if (header.endsWith(";base64", ignoreCase = true)) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    URLDecoder.decode(payload, "UTF-8").toByteArray()
                }
            }.getOrNull() ?: return
        saveBytes(app, suggestedName, mime, bytes)
    }

    /** Saves bytes a page handed over (a `blob:` download, via the browser-extras script). */
    fun saveBytes(
        context: Context,
        suggestedName: String?,
        mimeType: String?,
        bytes: ByteArray,
    ) {
        if (bytes.size > MAX_INLINE_BYTES) return
        val app = context.applicationContext
        val name = safeName(suggestedName, mimeType)
        io.execute {
            val ok = runCatching { write(app, name, mimeType) { it.write(bytes) } }.getOrDefault(false)
            toast(app, app.getString(if (ok) CommonsR.string.browser_download_saved else CommonsR.string.browser_download_failed, name))
        }
    }

    /**
     * Saves bytes a page handed over AFTER native consent: [name] is the already-sanitized,
     * already-approved file name and [bytes] were decoded (and bounded) before the prompt, so this is
     * exactly what the user saw on the consent card. Runs on the downloads executor and toasts the
     * outcome, like every other path into [write].
     */
    fun saveInlineBytes(
        context: Context,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ) {
        if (bytes.size > MAX_INLINE_BYTES) return
        val app = context.applicationContext
        io.execute {
            val ok = runCatching { write(app, name, mimeType) { it.write(bytes) } }.getOrDefault(false)
            toast(app, app.getString(if (ok) CommonsR.string.browser_download_saved else CommonsR.string.browser_download_failed, name))
        }
    }

    /**
     * The plain file name the sink would use for a page's suggestion: without path parts or control
     * characters, else "download" + the MIME's extension. Exposed so a consent prompt can show — and
     * approve — the exact name [write] will store, before any bytes move.
     */
    fun sanitize(
        suggested: String?,
        mimeType: String?,
    ): String = safeName(suggested, mimeType)

    /**
     * Writes into the public Downloads collection (Android 10+, no permission needed), or into the app's
     * own Downloads folder on older versions, where writing the shared one would need a storage permission
     * the app doesn't hold.
     */
    private fun write(
        context: Context,
        name: String,
        mimeType: String?,
        body: (OutputStream) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    mimeType?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            return try {
                resolver.openOutputStream(uri)?.use(body) ?: error("No output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        dir.mkdirs()
        File(dir, name).outputStream().use(body)
        return true
    }

    /** A plain filename: the page's suggestion without path parts, else "download" + the MIME's extension. */
    private fun safeName(
        suggested: String?,
        mimeType: String?,
    ): String {
        val base =
            suggested
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.replace(Regex("[\\u0000-\\u001f:*?\"<>|]"), "_")
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        if (base != null) return base.take(120)
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "download.$ext" else "download"
    }

    private fun toast(
        context: Context,
        text: String,
    ) = main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
