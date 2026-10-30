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

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.utils.Log

/**
 * Takes WebRTC away from every frame of a WebView that must not have it. The WebView's proxy covers
 * HTTP, not WebRTC: its UDP goes straight out, so a napplet or webxdc app would have a network, and a site the user
 * put on Tor would show its real address. A site on the open web keeps WebRTC; its traffic is its own.
 *
 * The napplet's own frame loses it in the prelude too ([NappletWebContract.preludePrefix]); this also
 * reaches the frames a page makes, which no prelude does.
 */
object NappletWebRtcGuard {
    private const val TAG = "NappletWebRtcGuard"

    fun isNeeded(
        profile: HostProfile,
        useTor: Boolean,
    ): Boolean = profile != HostProfile.WEBSITE || useTor

    fun install(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "This WebView can't run a script in every frame; only the napplet's own frame loses WebRTC")
            return
        }
        WebViewCompat.addDocumentStartJavaScript(webView, NappletWebContract.WEBRTC_GUARD_JS, setOf("*"))
    }
}
