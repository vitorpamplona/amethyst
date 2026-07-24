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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.utils.UriParser
import com.vitorpamplona.quartz.utils.UrlEncoder

/**
 * NWC-07 deep-link pairing conventions.
 *
 * These deep links let a NWC **client** (e.g. Amethyst) and a NWC **wallet** app
 * installed on the *same device* pair without QR codes or manual copy/paste:
 *
 * 1. The client opens `nostrnwc://connect?appname=…&appicon=…&callback=…` (or the
 *    app-scoped `nostrnwc+{app}://connect` variant to target a specific wallet).
 * 2. The wallet creates a connection and opens the client's `callback` URI with
 *    the resulting `nostr+walletconnect://…` pairing code in a `value` parameter.
 * 3. The client parses `value` with [Nip47WalletConnect.parse] and stores it.
 *
 * The pairing code carried in `value` is exactly the connection string this module
 * already understands — this deep link is only the transport used to obtain one.
 *
 * All URI parameters MUST be URI-encoded (NWC-07).
 */
object Nip47DeepLink {
    const val SCHEME = "nostrnwc"
    const val HOST = "connect"

    /**
     * A parsed `nostrnwc://connect` request (wallet side).
     */
    class ConnectRequest(
        val callback: String,
        val appName: String? = null,
        val appIcon: String? = null,
    )

    /**
     * Builds the outgoing deep link a NWC client opens to ask a wallet app on the
     * same device to create a connection.
     *
     * @param callback the URI scheme the wallet must open to return the pairing code
     * @param appName human-readable name of the requesting client
     * @param appIcon URL of the requesting client's icon
     * @param walletAppName optional wallet selector; when set, targets
     *   `nostrnwc+{walletAppName}://connect` instead of the generic
     *   `nostrnwc://connect`.
     */
    fun buildConnectUri(
        callback: String,
        appName: String? = null,
        appIcon: String? = null,
        walletAppName: String? = null,
    ): String {
        val scheme = if (walletAppName.isNullOrBlank()) SCHEME else "$SCHEME+$walletAppName"
        val params =
            buildList {
                appName?.let { add("appname=" + UrlEncoder.encode(it)) }
                appIcon?.let { add("appicon=" + UrlEncoder.encode(it)) }
                add("callback=" + UrlEncoder.encode(callback))
            }
        return "$scheme://$HOST?" + params.joinToString("&")
    }

    /**
     * Parses an incoming `nostrnwc://connect` (or `nostrnwc+{app}://connect`)
     * request. Returns null when the URI is not a NWC connect deep link or has no
     * callback.
     */
    fun parseConnectUri(uri: String): ConnectRequest? {
        val parser = UriParser(uri)
        val scheme = parser.scheme() ?: return null
        if (scheme != SCHEME && !scheme.startsWith("$SCHEME+")) return null

        val callback = parser.getQueryParameter("callback")?.firstOrNull() ?: return null
        return ConnectRequest(
            callback = callback,
            appName = parser.getQueryParameter("appname")?.firstOrNull(),
            appIcon = parser.getQueryParameter("appicon")?.firstOrNull(),
        )
    }

    /**
     * Builds the callback URI a wallet opens to return a pairing code to the
     * client (wallet side). The pairing code is placed in a `value` parameter.
     */
    fun buildCallbackUri(
        callback: String,
        pairingCode: String,
    ): String {
        val separator = if (callback.contains('?')) '&' else '?'
        return callback + separator + "value=" + UrlEncoder.encode(pairingCode)
    }

    /**
     * Extracts the `nostr+walletconnect://…` pairing code returned by a wallet in
     * a callback deep link (client side). Returns null when the `value` parameter
     * is absent.
     */
    fun parseCallbackValue(uri: String): String? = UriParser(uri).getQueryParameter("value")?.firstOrNull()
}
