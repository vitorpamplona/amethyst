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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the system file picker on behalf of an **embedded** WebView surface.
 *
 * The two embedded providers ([NappletBrowserService][com.vitorpamplona.amethyst.napplethost.NappletBrowserService]
 * and [NappletHostService][com.vitorpamplona.amethyst.napplethost.NappletHostService]) host their
 * WebView in the keyless `:napplet` process as a windowless Service, so when a page taps
 * `<input type="file">` there is no Activity there to start a picker from. They send the *description*
 * of the request over Messenger instead; this holds it here in the main process and launches
 * [WebFileChooserActivity], which runs the picker (and the camera, and the CAMERA permission prompt
 * that a `capture` input needs) and reports back to the caller, which relays the URIs to the sandbox.
 *
 * Mirrors [NappletConsentCoordinator]: the pending request is keyed by a one-time token so the
 * throwaway Activity carries nothing but that token. Every request completes exactly once — a
 * dismissed picker resolves to null, which is what releases the page's file input.
 *
 * URI read grants are per-UID, so the `content://` URIs granted to this process are readable by the
 * WebView in `:napplet` without any re-granting.
 */
object WebFileChooserCoordinator {
    /** The request as it arrived from the sandbox, plus where to send the answer. */
    class Pending(
        val acceptTypes: List<String>,
        val allowMultiple: Boolean,
        val captureEnabled: Boolean,
        val pageTitle: String?,
        val onResult: (Array<String>?) -> Unit,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    /**
     * Shows a picker for [acceptTypes] and calls [onResult] with the picked URIs as strings, or null
     * when nothing was chosen. [onResult] always runs, including when no picker host could be started
     * at all — the page's file input is waiting on it.
     */
    fun request(
        context: Context,
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        captureEnabled: Boolean,
        pageTitle: String?,
        onResult: (Array<String>?) -> Unit,
    ) {
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(acceptTypes, allowMultiple, captureEnabled, pageTitle, onResult)

        val launch =
            Intent(context, WebFileChooserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TOKEN, token)

        runCatching { context.startActivity(launch) }
            .onFailure { e ->
                Log.w(TAG, "Could not start the file chooser host", e)
                complete(token, null)
            }
    }

    /** Called by [WebFileChooserActivity] to learn what to ask the user for. */
    fun pendingFor(token: String): Pending? = pending[token]

    /** Called by [WebFileChooserActivity] with the outcome; null = nothing chosen. Resolves at most once. */
    fun complete(
        token: String,
        uris: Array<String>?,
    ) {
        pending.remove(token)?.onResult?.invoke(uris)
    }

    const val EXTRA_TOKEN = "web_file_chooser_token"

    private const val TAG = "WebFileChooser"
}
