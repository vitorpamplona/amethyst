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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The resource fetcher's URL and data rules read the same on the JVM (OkHttp) and iOS (Foundation). */
class NappletResourceUrlsTest {
    @Test
    fun onlyCredentialFreeHttpsUrlsPass() {
        assertEquals("https://example.com/a?b=c", safeHttpsResourceUrl("https://example.com/a?b=c"))
        assertEquals("https://example.com/", safeHttpsResourceUrl("https://EXAMPLE.com"))
        assertNull(safeHttpsResourceUrl("http://example.com/a"))
        assertNull(safeHttpsResourceUrl("https://user:secret@example.com/a"))
        assertNull(safeHttpsResourceUrl("https://user@example.com/a"))
        assertNull(safeHttpsResourceUrl("file:///etc/passwd"))
        assertNull(safeHttpsResourceUrl("not a url"))
        assertTrue(NappletResourceFetcher.isSafeHttpsResourceUrl("https://blossom.example/abc"))
    }

    @Test
    fun redirectsResolveAgainstTheirPage() {
        assertEquals("https://a.example/z", resolveResourceRedirect("https://a.example/x/y", "../z"))
        assertEquals("https://a.example/x/z", resolveResourceRedirect("https://a.example/x/y", "z"))
        assertEquals("https://b.example/q", resolveResourceRedirect("https://a.example/x", "https://b.example/q"))
        assertEquals("http://b.example/q", resolveResourceRedirect("https://a.example/x", "http://b.example/q"))
        // A downgrade resolves, and then fails the https check.
        assertNull(resolveResourceRedirect("https://a.example/x", "http://b.example/q")?.let(::safeHttpsResourceUrl))
    }

    @Test
    fun base64PayloadsDecodeWithOrWithoutPadding() {
        assertContentEquals("hi".encodeToByteArray(), decodeDataBase64("aGk="))
        assertContentEquals("hi".encodeToByteArray(), decodeDataBase64("aGk"))
        assertContentEquals(byteArrayOf(0, 1, 2, -1), decodeDataBase64("AAEC/w=="))
        assertFailsWith<IllegalArgumentException> { decodeDataBase64("aG\$k") }
    }
}
