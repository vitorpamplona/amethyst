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

import android.os.SystemClock
import android.webkit.URLUtil
import com.vitorpamplona.quartz.utils.Log

/**
 * The ONE consent and gate for every page-initiated native file write into the shared public Downloads
 * collection, so both entry paths into [BrowserDownloads] — the `browser.download` bridge envelope and
 * the WebView `DownloadListener` — pass through exactly one gate.
 *
 * Why it lives in the `:napplet` sandbox and not in the injected script: the WebView bridge accepts
 * envelopes from any origin the user browses (`addWebMessageListener(..., setOf("*"))`), and
 * BrowserWebTools.share's own comment documents pages posting bridge envelopes directly, bypassing the
 * injected script's checks. So a hostile top frame can ask for a native file write with no gesture and
 * no consent. The gate is keyed on data the page cannot forge — the WebView-reported origin — and it is
 * deliberately NOT the broker's per-origin launch token: that token is minted automatically for any
 * logged-in origin that asks (NappletBrokerService's MSG_MINT_BROWSER_TOKEN handler shows no prompt),
 * so it is a session handle, not a consent grant.
 *
 * Three layers:
 *
 * 1. **One-shot native consent** — every page-initiated save shows [com.vitorpamplona.amethyst.commons.browser.ui.pill.DownloadPromptCard]
 *    first: the exact sanitized name, the true size (decoded base64 length, or the server's Content-Length
 *    when the page gave a network URL), and the WebView-reported origin. Nothing reaches [BrowserDownloads]
 *    until the user taps Save. The immediately-user-initiated context-menu "Download image" brushes past
 *    the prompt (that tap IS the gesture).
 * 2. **One live prompt per surface** — a second consent request while one is already live for the same
 *    surface (this full-screen window, or one embedded tab) is refused ([beginConsent]), so a page can't
 *    swap a card's name under the user's finger; the callers also answer the displaced card before showing
 *    a successor.
 * 3. **Per-origin cooldown** — after a prompt is answered, the next request from the same origin on the
 *    same surface can't flash another card for [DOWNLOAD_COOLDOWN_MS] ([shouldPrompt]).
 */
object BrowserDownloadGate {
    private const val TAG = "BrowserDownloadGate"

    /** Minimum spacing between consent prompts per origin on one surface, mirroring BrowserWebTools.share. */
    const val DOWNLOAD_COOLDOWN_MS = 1_000L

    /** Extensions an install-or-run prompt exists for. Reported (never rewritten) so the user can judge the real name. */
    val RISKY_EXTENSIONS =
        setOf(
            "apk",
            "apks",
            "apkm",
            "xapk",
            "aab",
            "jar",
            "dex",
            "so",
            "exe",
            "msi",
            "bat",
            "cmd",
            "com",
            "scr",
            "hta",
            "ps1",
            "vbs",
            "wsf",
            "dmg",
            "pkg",
            "app",
            "deb",
            "rpm",
            "appimage",
            "run",
            "html",
            "htm",
            "xhtml",
            "svg",
            "sh",
            "zsh",
            "bash",
            "command",
            "lnk",
            "url",
            "desktop",
        )

    /** Everything the consent card needs to show before a single byte is saved. */
    class Spec(
        val fileName: String,
        val sizeBytes: Long,
        val risky: Boolean,
    )

    /**
     * One surface's live consent, handed out by [beginConsent] and ended by [endConsent]. Holding it
     * is what stops a second prompt for the same surface from displacing the card under the user's
     * finger; the page has no way to observe it. Callers must [endConsent] on every answer path.
     */
    class Consent internal constructor(
        internal val surfaceKey: String,
        internal val origin: String,
    )

    /**
     * The post-consent save for already-allowed inline bytes. Kept here (and exposed as the save a
     * [com.vitorpamplona.amethyst.commons.browser.ui.pill.DownloadPromptCard] allow runs) because exactly
     * the same save is shared by the card's allow and by any caller that already holds consented bytes.
     */
    class InlineSave internal constructor(
        val fileName: String,
        val mimeType: String?,
        val bytes: ByteArray,
    ) {
        /** Writes the consented bytes into the shared Downloads collection, exactly as the card named them. */
        fun save(context: android.content.Context) = BrowserDownloads.saveInlineBytes(context, fileName, mimeType, bytes)
    }

    /**
     * Builds the post-consent inline save for a `browser.download` envelope's data URL: the sanitized
     * name and the decoded bytes, so the eventual save is exactly what the card showed. Null when the
     * URL is malformed or over [BrowserDownloads.MAX_INLINE_BYTES] — the caller treats null as a refused
     * download and shows no prompt.
     */
    fun preludeInlineSave(
        dataUrl: String,
        suggestedName: String?,
    ): InlineSave? {
        val header = dataUrl.substringBefore(',', "")
        if (!dataUrl.contains(',') || !header.startsWith("data:", ignoreCase = true) || !header.endsWith(";base64", ignoreCase = true)) return null
        val payload = dataUrl.substringAfter(',', "")
        if (payload.length > BrowserDownloads.MAX_INLINE_BYTES / 3 * 4 + 8) return null
        val bytes = runCatching { android.util.Base64.decode(payload, android.util.Base64.DEFAULT) }.getOrNull() ?: return null
        if (bytes.size > BrowserDownloads.MAX_INLINE_BYTES) return null
        // The MIME inside the data URL drives safeName's fallback extension when the page sent no name.
        val mime =
            header
                .removePrefix("data:")
                .removeSuffix(";base64")
                .substringBefore(';')
                .ifBlank { null }
        return InlineSave(BrowserDownloads.sanitize(suggestedName, mime), mime, bytes)
    }

    /** What the consent card shows for a network download, before any bytes are fetched. */
    fun networkSpec(
        fileName: String,
        sizeBytes: Long,
    ): Spec = Spec(fileName, sizeBytes.coerceAtLeast(0L), isRisky(fileName))

    /** Guesses a network download's file name the same way the eventual save does. */
    fun guessNetworkName(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ): String = BrowserDownloads.sanitize(URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType)

    /**
     * Whether a consent prompt may be shown for [origin] on [surfaceKey] right now. A `false` return
     * means a prompt for this origin on this surface was just answered and this request is silently
     * dropped — the throttle that keeps a spamming page from flashing dialogs forever.
     */
    fun shouldPrompt(
        surfaceKey: String,
        origin: String,
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastAnsweredAt[surfaceKey]?.get(origin) ?: 0L
        if (now - last < DOWNLOAD_COOLDOWN_MS) {
            Log.d(TAG) { "Download consent for $origin throttled" }
            return false
        }
        return true
    }

    /**
     * Claims the one live consent slot for [surfaceKey], or returns null when one is already live for
     * that surface — the anti-swap rule, released by [endConsent]. A `false` [shouldPrompt] caller
     * never even gets here (dropped before the slot is taken).
     */
    fun beginConsent(
        surfaceKey: String,
        origin: String,
    ): Consent? {
        if (liveSurfaces.contains(surfaceKey)) return null
        liveSurfaces.add(surfaceKey)
        return Consent(surfaceKey, origin)
    }

    /**
     * Releases [consent]'s slot and starts its per-origin cooldown. Called on every answer path —
     * Save, Don't save, dismissal, and the surface being destroyed — so a torn-down window can never
     * wedge the gate. Idempotent.
     */
    fun endConsent(consent: Consent) {
        liveSurfaces.remove(consent.surfaceKey)
        lastAnsweredAt.getOrPut(consent.surfaceKey) { HashMap() }[consent.origin] = SystemClock.elapsedRealtime()
    }

    /** Drops this surface's throttle and live-slot state — when the window/tab hosting its WebView goes away. */
    fun clearSurface(surfaceKey: String) {
        liveSurfaces.remove(surfaceKey)
        lastAnsweredAt.remove(surfaceKey)
    }

    /** Whether [fileName]'s extension is one a user should double-check before saving. */
    fun isRisky(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in RISKY_EXTENSIONS

    // Main-thread only: one live surface's consent, and per-surface then per-origin last-answered stamps.
    private val liveSurfaces = HashSet<String>()
    private val lastAnsweredAt = HashMap<String, HashMap<String, Long>>()
}
