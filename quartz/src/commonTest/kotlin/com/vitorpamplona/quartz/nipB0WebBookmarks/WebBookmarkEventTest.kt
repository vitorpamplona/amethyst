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
package com.vitorpamplona.quartz.nipB0WebBookmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebBookmarkEventTest {
    private fun bookmark(dTag: String) = WebBookmarkEvent("0".repeat(64), "1".repeat(64), 1L, arrayOf(arrayOf("d", dTag)), "", "")

    @Test
    fun httpsDropsEverythingBeforeTheHostname() {
        assertEquals("alice.blog/post", WebBookmarkEvent.urlToDTag("https://alice.blog/post"))
        assertEquals("alice.blog/post", WebBookmarkEvent.urlToDTag("HTTPS://alice.blog/post"))
        assertEquals("alice.blog/post", WebBookmarkEvent.urlToDTag("https://user:pw@alice.blog/post"))
        assertEquals("alice.blog:8443/post?q=1#f", WebBookmarkEvent.urlToDTag("https://alice.blog:8443/post?q=1#f"))
    }

    @Test
    fun otherSchemesAreKept() {
        assertEquals("http://alice.i2p/post", WebBookmarkEvent.urlToDTag("http://alice.i2p/post"))
        assertEquals("ftp://files.example.com/a.txt", WebBookmarkEvent.urlToDTag("ftp://files.example.com/a.txt"))
        assertEquals("gemini://alice.space/post", WebBookmarkEvent.urlToDTag("gemini://alice.space/post"))
        assertEquals("magnet:?xt=urn:btih:abc", WebBookmarkEvent.urlToDTag("magnet:?xt=urn:btih:abc"))
    }

    @Test
    fun schemelessInputIsAlreadyInHttpsForm() {
        assertEquals("alice.blog/post", WebBookmarkEvent.urlToDTag("alice.blog/post"))
        assertEquals("localhost:8080/x", WebBookmarkEvent.urlToDTag("localhost:8080/x"))
    }

    @Test
    fun trailingSlashIsTrimmedAsBefore() {
        // NIP-B0 is silent on trailing slashes; keep the long-standing trim so existing d tags still match.
        assertEquals("alice.blog", WebBookmarkEvent.urlToDTag("https://alice.blog/"))
        assertEquals("http://alice.blog", WebBookmarkEvent.urlToDTag("http://alice.blog/"))
    }

    @Test
    fun urlRestoresHttpsOnlyWhenTheDTagHasNoScheme() {
        assertEquals("https://alice.blog/post", bookmark("alice.blog/post").url())
        assertEquals("https://alice.blog:8443/post", bookmark("alice.blog:8443/post").url())
        assertEquals("https://localhost:8080/x", bookmark("localhost:8080/x").url())
        assertEquals("http://alice.i2p/post", bookmark("http://alice.i2p/post").url())
        assertEquals("gemini://alice.space/post", bookmark("gemini://alice.space/post").url())
        assertEquals("magnet:?xt=urn:btih:abc", bookmark("magnet:?xt=urn:btih:abc").url())
        assertEquals("", bookmark("").url())
    }

    @Test
    fun roundTripsThroughTheDTag() {
        listOf(
            "https://alice.blog/post",
            "http://alice.i2p/post",
            "ftp://files.example.com/a.txt",
            "magnet:?xt=urn:btih:abc",
        ).forEach { url ->
            assertEquals(url, bookmark(WebBookmarkEvent.urlToDTag(url)).url())
        }
    }

    @Test
    fun detectsSchemes() {
        assertTrue(WebBookmarkEvent.hasScheme("http://a.b"))
        assertTrue(WebBookmarkEvent.hasScheme("mailto:alice@a.b"))
        assertFalse(WebBookmarkEvent.hasScheme("a.b/c"))
        assertFalse(WebBookmarkEvent.hasScheme("a.b:80/c"))
        assertFalse(WebBookmarkEvent.hasScheme("localhost:80"))
        assertFalse(WebBookmarkEvent.hasScheme("192.168.0.1:80/c"))
    }

    @Test
    fun buildKeepsTheOriginalPublishedAt() {
        val template = WebBookmarkEvent.build("http://alice.i2p/post", "t", "", createdAt = 200, firstPublishedAt = 100)
        assertEquals("http://alice.i2p/post", template.tags.first { it[0] == "d" }[1])
        assertEquals("100", template.tags.first { it[0] == "published_at" }[1])
    }
}
