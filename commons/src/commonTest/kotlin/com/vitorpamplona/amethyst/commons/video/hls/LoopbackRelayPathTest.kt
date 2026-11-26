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
package com.vitorpamplona.amethyst.commons.video.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopbackRelayPathTest {
    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun roundTripsTheWholeOriginalUrl() {
        val original = "https://cdn.example/live/high/index.m3u8?token=a%20b&x=1#frag"
        val path = LoopbackRelayPath.encode(secret, "stream1", original)
        assertTrue(path.startsWith("/$secret/stream1/"), path)
        assertTrue(path.endsWith("/index.m3u8"), path)
        // Nothing in it a URI parser would read as a separator, so the player resolves nothing.
        val encoded = path.split('/')[3]
        assertTrue(encoded.all { it.isLetterOrDigit() || it == '-' || it == '_' }, encoded)
        assertEquals(LoopbackRelayPath.Target("stream1", original), LoopbackRelayPath.decode(secret, path))
        // A query the player adds is not part of the target.
        assertEquals(LoopbackRelayPath.Target("stream1", original), LoopbackRelayPath.decode(secret, "$path?_HLS_msn=10"))
    }

    @Test
    fun refusesPathsThatAreNotOurs() {
        val path = LoopbackRelayPath.encode(secret, "s", "http://a.example/x.ts")
        assertNull(LoopbackRelayPath.decode("another0secret", path), "another secret")
        assertNull(LoopbackRelayPath.decode(secret, "/$secret/s"), "no original")
        assertNull(LoopbackRelayPath.decode(secret, "/$secret/s/!!!/x.ts"), "not base64url")
        assertNull(LoopbackRelayPath.decode(secret, "/$secret/bad.stream/aHR0cDovL2E/x"), "not a stream token")
        // file:///etc/hosts
        assertNull(LoopbackRelayPath.decode(secret, "/$secret/s/ZmlsZTovLy9ldGMvaG9zdHM/hosts"), "not http(s)")
    }

    @Test
    fun namesTheFileByItsLastSegment() {
        assertEquals("seg-1_a.ts", LoopbackRelayPath.fileNameOf("http://a.example/p/seg-1_a.ts?x=/y.mp4"))
        assertEquals("index.m3u8", LoopbackRelayPath.fileNameOf("https://a.example/index.m3u8#t=1"))
        assertEquals("media", LoopbackRelayPath.fileNameOf("https://a.example/"))
        assertEquals("media", LoopbackRelayPath.fileNameOf("https://a.example"))
        // Escapes lose their percent signs, never become separators.
        assertEquals("a20b3Fc.ts", LoopbackRelayPath.fileNameOf("https://a.example/a%20b%3Fc.ts"))
    }

    @Test
    fun tokensAreLettersAndDigits() {
        assertTrue(LoopbackRelayPath.isToken("abc123XYZ"))
        assertFalse(LoopbackRelayPath.isToken(""))
        assertFalse(LoopbackRelayPath.isToken("a.b"))
        assertFalse(LoopbackRelayPath.isToken("a".repeat(65)))
    }
}
