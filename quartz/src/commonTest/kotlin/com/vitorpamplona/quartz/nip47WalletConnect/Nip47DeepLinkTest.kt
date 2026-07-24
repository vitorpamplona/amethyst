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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip47DeepLinkTest {
    private val nwcUri =
        "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
            "?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5"

    @Test
    fun testBuildConnectUri() {
        val uri =
            Nip47DeepLink.buildConnectUri(
                callback = "amethystnwc://callback",
                appName = "Amethyst",
                appIcon = "https://amethyst.social/icon.png",
            )

        assertTrue(uri.startsWith("nostrnwc://connect?"))
        // All params URI-encoded.
        assertTrue(uri.contains("appname=Amethyst"))
        assertTrue(uri.contains("appicon=https%3A%2F%2Famethyst.social%2Ficon.png"))
        assertTrue(uri.contains("callback=amethystnwc%3A%2F%2Fcallback"))
    }

    @Test
    fun testBuildConnectUriWithWalletSelector() {
        val uri =
            Nip47DeepLink.buildConnectUri(
                callback = "amethystnwc://callback",
                appName = "Amethyst",
                walletAppName = "alby",
            )

        assertTrue(uri.startsWith("nostrnwc+alby://connect?"))
    }

    @Test
    fun testConnectUriRoundTrip() {
        val uri =
            Nip47DeepLink.buildConnectUri(
                callback = "amethystnwc://callback",
                appName = "Amethyst",
                appIcon = "https://amethyst.social/icon.png",
            )

        val parsed = Nip47DeepLink.parseConnectUri(uri)
        assertNotNull(parsed)
        assertEquals("amethystnwc://callback", parsed.callback)
        assertEquals("Amethyst", parsed.appName)
        assertEquals("https://amethyst.social/icon.png", parsed.appIcon)
    }

    @Test
    fun testParseConnectUriRejectsNonNwcScheme() {
        assertNull(Nip47DeepLink.parseConnectUri("https://example.com/connect?callback=x"))
    }

    @Test
    fun testParseConnectUriRequiresCallback() {
        assertNull(Nip47DeepLink.parseConnectUri("nostrnwc://connect?appname=Amethyst"))
    }

    @Test
    fun testCallbackRoundTrip() {
        val callbackUri = Nip47DeepLink.buildCallbackUri("amethystnwc://callback", nwcUri)

        // The pairing code must be URI-encoded inside the value param.
        assertTrue(callbackUri.contains("value=nostr%2Bwalletconnect"))

        val value = Nip47DeepLink.parseCallbackValue(callbackUri)
        assertEquals(nwcUri, value)

        // And the returned value parses as a normal NWC connection URI.
        val config = Nip47WalletConnect.parse(value!!)
        assertEquals("b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4", config.pubKeyHex)
    }

    @Test
    fun testBuildCallbackUriWhenCallbackAlreadyHasQuery() {
        val callbackUri = Nip47DeepLink.buildCallbackUri("myapp://cb?foo=bar", nwcUri)
        assertTrue(callbackUri.contains("myapp://cb?foo=bar&value="))
        assertEquals(nwcUri, Nip47DeepLink.parseCallbackValue(callbackUri))
    }

    @Test
    fun testParseCallbackValueAbsent() {
        assertNull(Nip47DeepLink.parseCallbackValue("amethystnwc://callback"))
    }
}
