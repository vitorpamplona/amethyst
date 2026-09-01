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

/**
 * Cross-target contract for [UrlEncoder].
 *
 * This is not a style preference — [UrlEncoder.encode] builds strings that leave the
 * device. `TorrentEvent` puts it in magnet links, `Nip54InlineMetadata` in inline
 * metadata, and `Nip47DeepLink` in the `callback`/`appname`/`value` parameters of NWC
 * deep links. If Android encodes a title one way and iOS another, the two clients emit
 * different bytes for the same event, and a wallet that round-trips a deep link built
 * on one platform can fail on the other.
 *
 * The JVM/Android actual is `java.net.URLEncoder`/`URLDecoder` with UTF-8, so that is
 * the reference every other target has to match. The expectations below are its
 * `application/x-www-form-urlencoded` rules, which differ from plain RFC 3986
 * percent-encoding in exactly the three places a generic library gets "wrong": space,
 * `*` and `~`.
 */
class UrlEncoderTest {
    @Test
    fun keepsTheUnreservedSet() {
        // URLEncoder's dontNeedEncoding set is alphanumerics plus these four, and only
        // these four. Note `*` survives and `~` does not — the opposite of RFC 3986.
        assertEquals("abcXYZ019", UrlEncoder.encode("abcXYZ019"))
        assertEquals("-_.*", UrlEncoder.encode("-_.*"))
    }

    @Test
    fun encodesSpaceAsPlus() {
        // Form encoding, not %20.
        assertEquals("hello+world", UrlEncoder.encode("hello world"))
        assertEquals("a+b+c", UrlEncoder.encode("a b c"))
    }

    @Test
    fun encodesTildeAndTheOtherSubDelimiters() {
        assertEquals("%7E", UrlEncoder.encode("~"))
        assertEquals("%21", UrlEncoder.encode("!"))
        assertEquals("%27", UrlEncoder.encode("'"))
        assertEquals("%28%29", UrlEncoder.encode("()"))
        assertEquals("%24%2C%3B", UrlEncoder.encode("$,;"))
    }

    @Test
    fun encodesUriPunctuationWithUppercaseHex() {
        assertEquals("%3A%2F%2F", UrlEncoder.encode("://"))
        assertEquals("%2B", UrlEncoder.encode("+"))
        assertEquals("%3F%26%3D", UrlEncoder.encode("?&="))
        assertEquals("%40%23%25", UrlEncoder.encode("@#%"))
    }

    @Test
    fun encodesNonAsciiAsUtf8() {
        assertEquals("%C3%A9", UrlEncoder.encode("é"))
        assertEquals("caf%C3%A9", UrlEncoder.encode("café"))
        assertEquals("%E2%82%AC", UrlEncoder.encode("€"))
        // Outside the BMP: a surrogate pair has to encode as one 4-byte sequence.
        assertEquals("%F0%9F%98%80", UrlEncoder.encode("😀"))
    }

    @Test
    fun decodesPlusAsSpace() {
        assertEquals("hello world", UrlEncoder.decode("hello+world"))
        assertEquals("hello world", UrlEncoder.decode("hello%20world"))
    }

    @Test
    fun decodesPercentEscapes() {
        assertEquals("://", UrlEncoder.decode("%3A%2F%2F"))
        assertEquals("~", UrlEncoder.decode("%7E"))
        assertEquals("café", UrlEncoder.decode("caf%C3%A9"))
        assertEquals("€", UrlEncoder.decode("%E2%82%AC"))
        assertEquals("😀", UrlEncoder.decode("%F0%9F%98%80"))
        // Lowercase hex decodes the same as uppercase.
        assertEquals("é", UrlEncoder.decode("%c3%a9"))
    }

    @Test
    fun leavesUnescapedTextAlone() {
        assertEquals("plain", UrlEncoder.decode("plain"))
        assertEquals("-_.*~", UrlEncoder.decode("-_.*~"))
    }

    @Test
    fun roundTripsTheStringsThisIsActuallyUsedFor() {
        // A torrent title (TorrentEvent) and a tracker URL.
        val title = "Big Buck Bunny (2008) [1080p] ~ 60% done!"
        assertEquals(title, UrlEncoder.decode(UrlEncoder.encode(title)))

        val tracker = "udp://tracker.example.org:1337/announce"
        assertEquals("udp%3A%2F%2Ftracker.example.org%3A1337%2Fannounce", UrlEncoder.encode(tracker))
        assertEquals(tracker, UrlEncoder.decode(UrlEncoder.encode(tracker)))

        // An NWC pairing code (Nip47DeepLink.buildCallbackUri puts this in `value=`).
        val pairing =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
                "?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5"
        assertEquals(pairing, UrlEncoder.decode(UrlEncoder.encode(pairing)))

        // A callback deep link (Nip47DeepLink.parseConnectUri reads this back).
        val callback = "amethystnwc://callback"
        assertEquals("amethystnwc%3A%2F%2Fcallback", UrlEncoder.encode(callback))
        assertEquals(callback, UrlEncoder.decode(UrlEncoder.encode(callback)))
    }

    @Test
    fun roundTripsEveryAsciiCharacter() {
        val ascii = (0..127).map { it.toChar() }.joinToString("")
        assertEquals(ascii, UrlEncoder.decode(UrlEncoder.encode(ascii)))
    }
}
