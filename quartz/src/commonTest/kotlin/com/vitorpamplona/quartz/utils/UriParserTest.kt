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
package com.vitorpamplona.quartz.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The contract every actual of [UriParser] has to keep.
 *
 * The interesting case is the *opaque* URI -- a scheme followed by anything but `/`. The JVM and
 * Android actual delegates to `java.net.URI`, which declares such a URI to have no query at all
 * and folds `dlnwc?value=...` into one scheme-specific part, while the hand-rolled actuals look
 * for `?` wherever it sits. That disagreement is what made a wallet-connect deep link report
 * "that uri was invalid" on Android while the `//` spelling of the same link worked.
 */
class UriParserTest {
    private val nwc = "nostr+walletconnect://abc?relay=wss://r.example&secret=s1"

    @Test
    fun readsTheQueryOfAnOpaqueUri() {
        val parser = UriParser("amethyst+walletconnect:dlnwc?value=${encode(nwc)}")

        assertEquals(nwc, parser.getQueryParameter("value")?.firstOrNull())
    }

    /** The spelling that already worked, so the rescue above cannot have broken it. */
    @Test
    fun readsTheQueryOfAHierarchicalUri() {
        val parser = UriParser("amethyst+walletconnect://dlnwc?value=${encode(nwc)}")

        assertEquals(nwc, parser.getQueryParameter("value")?.firstOrNull())
    }

    /** No scheme at all, which is how the callback arrives from some wallets. */
    @Test
    fun readsTheQueryOfASchemelessUri() {
        val parser = UriParser("dlnwc?value=${encode(nwc)}")

        assertEquals(nwc, parser.getQueryParameter("value")?.firstOrNull())
    }

    @Test
    fun opaqueUriWithoutAQueryHasNoParameters() {
        val parser = UriParser("cashu:sometoken")

        assertNull(parser.getQueryParameter("value"))
        assertEquals(emptySet(), parser.queryParameterNames())
    }

    @Test
    fun namesEveryParameterOfAnOpaqueUri() {
        val parser = UriParser("bunker:pubkeyhex?relay=wss%3A%2F%2Fr.example&secret=s1")

        assertEquals(setOf("relay", "secret"), parser.queryParameterNames())
        assertEquals("wss://r.example", parser.getQueryParameter("relay")?.firstOrNull())
        assertEquals("s1", parser.getQueryParameter("secret")?.firstOrNull())
    }

    /** A fragment is parsed off an opaque URI by every actual already; keep it that way. */
    @Test
    fun keepsTheFragmentOfAnOpaqueUri() {
        val parser = UriParser("scheme:thing?a=1#b=2")

        assertEquals("1", parser.getQueryParameter("a")?.firstOrNull())
        assertEquals(mapOf("b" to "2"), parser.fragments())
    }

    private fun encode(value: String) =
        value
            .replace("%", "%25")
            .replace("+", "%2B")
            .replace(":", "%3A")
            .replace("/", "%2F")
            .replace("?", "%3F")
            .replace("&", "%26")
            .replace("=", "%3D")
}
