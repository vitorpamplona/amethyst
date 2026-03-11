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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.quartz.nipB7Blossom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlossomUriTest {
    private val sha256 = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
    private val authorPubkey = "a8f3721a0dc1b4d5c12f4cc7c54ae14071eb9c1b4f9b2cf0d4ab22c0e9f0c7e"

    @Test
    fun parsesMinimalUri() {
        val uri = "blossom:${sha256}.jpg"
        val result = BlossomUri.parse(uri)

        assertNotNull(result)
        assertEquals(sha256, result.sha256)
        assertEquals("jpg", result.extension)
        assertTrue(result.servers.isEmpty())
        assertTrue(result.authors.isEmpty())
        assertNull(result.size)
    }

    @Test
    fun parsesFullUri() {
        val uri = "blossom:${sha256}.mp4" +
            "?xs=https%3A%2F%2Fcdn.example.com" +
            "&xs=https%3A%2F%2Fbackup.example.com" +
            "&as=$authorPubkey" +
            "&sz=1048576"
        val result = BlossomUri.parse(uri)

        assertNotNull(result)
        assertEquals(sha256, result.sha256)
        assertEquals("mp4", result.extension)
        assertEquals(listOf("https://cdn.example.com", "https://backup.example.com"), result.servers)
        assertEquals(listOf(authorPubkey), result.authors)
        assertEquals(1048576L, result.size)
    }

    @Test
    fun parsesUriWithUnencodedServerUrls() {
        // xs values with unencoded colons/slashes should still parse
        val uri = "blossom:${sha256}.bin?xs=https://cdn.example.com&sz=512"
        val result = BlossomUri.parse(uri)

        assertNotNull(result)
        assertEquals(listOf("https://cdn.example.com"), result.servers)
        assertEquals(512L, result.size)
    }

    @Test
    fun defaultsExtensionToBinWhenMissing() {
        val uri = "blossom:$sha256"
        val result = BlossomUri.parse(uri)

        assertNotNull(result)
        assertEquals("bin", result.extension)
    }

    @Test
    fun isCaseInsensitiveForScheme() {
        val result = BlossomUri.parse("BLOSSOM:${sha256}.png")
        assertNotNull(result)
        assertEquals(sha256, result.sha256)
    }

    @Test
    fun normalisesUpperCaseSha256ToLowercase() {
        val result = BlossomUri.parse("blossom:${sha256.uppercase()}.jpg")
        assertNotNull(result)
        assertEquals(sha256, result.sha256)
    }

    @Test
    fun returnsNullForWrongScheme() {
        assertNull(BlossomUri.parse("magnet:?xt=urn:btih:$sha256"))
        assertNull(BlossomUri.parse("https://example.com/$sha256"))
    }

    @Test
    fun returnsNullForMalformedSha256() {
        assertNull(BlossomUri.parse("blossom:tooshort.jpg"))
        assertNull(BlossomUri.parse("blossom:gggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggggg.jpg"))
    }

    @Test
    fun roundTrip() {
        val original = BlossomUri(
            sha256 = sha256,
            extension = "jpg",
            servers = listOf("https://cdn.example.com", "https://backup.example.com"),
            authors = listOf(authorPubkey),
            size = 204800L,
        )
        val reparsed = BlossomUri.parse(original.toUriString())

        assertEquals(original, reparsed)
    }

    @Test
    fun handlesMultipleAuthors() {
        val author2 = "b8f3721a0dc1b4d5c12f4cc7c54ae14071eb9c1b4f9b2cf0d4ab22c0e9f0c7f"
        val uri = "blossom:${sha256}.jpg?as=$authorPubkey&as=$author2"
        val result = BlossomUri.parse(uri)

        assertNotNull(result)
        assertEquals(listOf(authorPubkey, author2), result.authors)
    }
}
