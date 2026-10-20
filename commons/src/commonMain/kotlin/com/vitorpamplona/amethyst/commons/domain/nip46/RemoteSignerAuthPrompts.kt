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
package com.vitorpamplona.amethyst.commons.domain.nip46

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NIP-46 auth challenges waiting for the user. A remote signer that needs the user to
 * authenticate somewhere else first (nsec.app) answers a request with `result: "auth_url"` and
 * the address in `error`. NIP-46 asks the client to show that address and to keep listening for
 * the real answer under the same request id, which `RemoteSignerManager` already does.
 *
 * Requests reach the signer from the login screen and from background work (decrypting, signing
 * a reaction), so the address is queued here and the shared UI shows it with a button to open
 * it, rather than the app switching to a browser on its own.
 */
class RemoteSignerAuthPrompts {
    private val _pending = MutableStateFlow<List<String>>(emptyList())

    /** The addresses waiting to be shown, oldest first. */
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    /** Pass as a signer's `onAuthUrl`. Repeats and anything that is not a web page are dropped. */
    fun report(url: String) {
        if (!isWebPage(url)) {
            // Not the address itself: it can carry a session token.
            Log.w(TAG) { "Ignored a remote signer auth_url that is not an http(s) page" }
            return
        }
        _pending.update { if (url in it || it.size >= MAX_PENDING) it else it + url }
    }

    /** The user opened or closed the prompt for [url]. */
    fun dismiss(url: String) = _pending.update { it - url }

    companion object {
        private const val TAG = "RemoteSignerAuthPrompts"

        /** Prompts kept behind the one on screen; a signer that challenges every request can't stack more. */
        private const val MAX_PENDING = 5

        /**
         * The address comes from the remote signer, and the app hands it to the platform to open.
         * Only a web page qualifies: `intent:`, `file:`, `javascript:` or an app's own scheme
         * would run something other than a browser.
         */
        fun isWebPage(url: String): Boolean {
            if (url.any { it.isWhitespace() || it.isISOControl() }) return false
            val lower = url.lowercase()
            val rest =
                when {
                    lower.startsWith("https://") -> lower.removePrefix("https://")
                    lower.startsWith("http://") -> lower.removePrefix("http://")
                    else -> return false
                }
            return rest.isNotEmpty() && rest[0] != '/'
        }
    }
}
