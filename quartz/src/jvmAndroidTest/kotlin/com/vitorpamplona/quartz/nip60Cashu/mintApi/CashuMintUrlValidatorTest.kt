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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSRF guard for `token.mint`. Every URL below arrives from a pasted/posted
 * Cashu token, so it is attacker controlled: the only ones we may ever contact
 * are https to a public host, and http to a .onion.
 */
class CashuMintUrlValidatorTest {
    private fun allow(url: String) {
        val result = CashuMintUrlValidator.validatedBaseUrl(url)
        assertEquals(url.trimEnd('/'), result)
    }

    private fun reject(url: String) {
        assertThrows("expected $url to be refused", MintUrlException::class.java) {
            CashuMintUrlValidator.validatedBaseUrl(url)
        }
    }

    // ------------------------------------------------------------------
    // Allowed
    // ------------------------------------------------------------------

    @Test
    fun httpsPublicHostIsAllowed() {
        allow("https://mint.minibits.cash/Bitcoin")
        allow("https://8333.space:3338")
        allow("https://mint.example.com/")
        allow("https://8.8.8.8")
    }

    @Test
    fun onionOverHttpIsAllowed() {
        allow("http://cashuxyzabcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrs.onion")
        allow("http://mint.somewhere.onion:3338/v1")
    }

    @Test
    fun onionOverHttpsIsAllowed() {
        allow("https://cashuxyzabcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrs.onion")
    }

    // ------------------------------------------------------------------
    // Rejected — scheme
    // ------------------------------------------------------------------

    @Test
    fun httpToPublicHostIsRejected() {
        reject("http://mint.example.com")
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        reject("file:///etc/passwd")
        reject("ftp://mint.example.com")
        reject("data://text/plain,hello")
        reject("content://com.android.provider/x")
        reject("mint.example.com")
        reject("")
    }

    // ------------------------------------------------------------------
    // Rejected — private / local destinations
    // ------------------------------------------------------------------

    @Test
    fun loopbackIsRejected() {
        reject("http://127.0.0.1")
        reject("https://127.0.0.1:3338")
        reject("http://127.0.0.1:8080/v1/info")
        reject("https://localhost:3338")
        reject("http://[::1]")
        reject("https://[::1]:3338")
    }

    @Test
    fun privateRangesAreRejected() {
        reject("http://192.168.1.5")
        reject("https://192.168.1.5")
        reject("https://10.0.0.7:3338")
        reject("https://172.16.4.4")
        reject("https://172.31.255.255")
    }

    @Test
    fun linkLocalAndMetadataEndpointsAreRejected() {
        reject("http://169.254.169.254")
        reject("https://169.254.169.254/latest/meta-data/")
        reject("https://[fe80::1]")
    }

    @Test
    fun uniqueLocalAndUnspecifiedAreRejected() {
        reject("https://[fc00::1]")
        reject("https://[fd12:3456:789a::1]")
        reject("https://0.0.0.0")
        reject("https://[::]")
    }

    @Test
    fun ipv4MappedIpv6BypassIsRejected() {
        reject("https://[::ffff:127.0.0.1]")
        reject("https://[::ffff:192.168.1.5]")
        reject("https://[::ffff:7f00:1]")
        reject("https://[::127.0.0.1]")
    }

    @Test
    fun inetAtonSpellingsOfLoopbackAreRejected() {
        reject("https://2130706433") // decimal 127.0.0.1
        reject("https://0177.0.0.1") // octal
        reject("https://0x7f.0.0.1") // hex
        reject("https://0x7f000001")
        reject("https://127.1") // 2-part form
    }

    @Test
    fun trailingDotHostnameIsRejected() {
        reject("https://localhost.")
        reject("https://127.0.0.1.")
    }

    @Test
    fun userInfoCannotDisguiseTheHost() {
        reject("https://mint.example.com@127.0.0.1/v1/info")
        reject("https://mint.example.com:pass@192.168.1.5/")
    }

    // ------------------------------------------------------------------
    // The user's own wallet mint exception
    // ------------------------------------------------------------------

    @Test
    fun privateHostIsAllowedWhenTheUserConfiguredTheMint() {
        assertEquals(
            "http://192.168.1.5:3338",
            CashuMintUrlValidator.validatedBaseUrl("http://192.168.1.5:3338", userConfigured = true),
        )
        assertEquals(
            "https://127.0.0.1:3338",
            CashuMintUrlValidator.validatedBaseUrl("https://127.0.0.1:3338", userConfigured = true),
        )
    }

    @Test
    fun userConfiguredStillRefusesNonHttpSchemes() {
        assertThrows(MintUrlException::class.java) {
            CashuMintUrlValidator.validatedBaseUrl("file:///etc/passwd", userConfigured = true)
        }
    }

    // ------------------------------------------------------------------
    // The client refuses at construction, before any request is issued
    // ------------------------------------------------------------------

    @Test
    fun mintHttpClientRefusesAtConstruction() {
        assertThrows(MintUrlException::class.java) {
            MintHttpClient("http://127.0.0.1:3338") { throw AssertionError("must not build an http client") }
        }
    }

    @Test
    fun mintHttpClientAcceptsPublicHttps() {
        MintHttpClient("https://mint.example.com/") { throw AssertionError("no request expected") }
    }

    // ------------------------------------------------------------------
    // Parser units — kept so a future "modernization" of the IP parsing
    // can't silently reopen a bypass.
    // ------------------------------------------------------------------

    @Test
    fun ipv4ParserHandlesInetAtonForms() {
        assertEquals(0x7F000001L, CashuMintUrlValidator.parseIpv4("127.0.0.1"))
        assertEquals(0x7F000001L, CashuMintUrlValidator.parseIpv4("2130706433"))
        assertEquals(0x7F000001L, CashuMintUrlValidator.parseIpv4("0177.0.0.1"))
        assertEquals(0x7F000001L, CashuMintUrlValidator.parseIpv4("0x7f000001"))
        assertEquals(0x7F000001L, CashuMintUrlValidator.parseIpv4("127.1"))
        assertEquals(null, CashuMintUrlValidator.parseIpv4("mint.example.com"))
        assertEquals(null, CashuMintUrlValidator.parseIpv4("256.0.0.1"))
    }

    @Test
    fun ipv6ParserHandlesCompressionAndEmbeddedIpv4() {
        assertTrue(CashuMintUrlValidator.parseIpv6("::1")!!.let { it[15].toInt() == 1 && it.take(15).all { b -> b.toInt() == 0 } })
        assertEquals(16, CashuMintUrlValidator.parseIpv6("2001:db8::1")!!.size)
        assertEquals(16, CashuMintUrlValidator.parseIpv6("::ffff:127.0.0.1")!!.size)
        assertEquals(null, CashuMintUrlValidator.parseIpv6("::1::2"))
        assertEquals(null, CashuMintUrlValidator.parseIpv6("gggg::1"))
    }

    @Test
    fun publicIpv6IsAllowed() {
        allow("https://[2001:db8::1]")
        allow("https://[2606:4700:4700::1111]:3338")
    }
}
