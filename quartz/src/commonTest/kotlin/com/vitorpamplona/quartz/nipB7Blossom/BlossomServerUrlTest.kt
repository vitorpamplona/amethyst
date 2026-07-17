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
package com.vitorpamplona.quartz.nipB7Blossom

import kotlin.test.Test
import kotlin.test.assertEquals

class BlossomServerUrlTest {
    private val sha256 = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
    private val pubkey = "a8f3721a0dc1b4d5c12f4cc7c54ae14071eb9c1b4f9b2cf0d4ab22c0e9f0c7e0"

    @Test
    fun buildsEndpointPaths() {
        assertEquals("https://cdn.example.com/upload", BlossomServerUrl.upload("https://cdn.example.com"))
        assertEquals("https://cdn.example.com/mirror", BlossomServerUrl.mirror("https://cdn.example.com"))
        assertEquals("https://cdn.example.com/media", BlossomServerUrl.media("https://cdn.example.com"))
        assertEquals("https://cdn.example.com/report", BlossomServerUrl.report("https://cdn.example.com"))
        assertEquals("https://cdn.example.com/list/$pubkey", BlossomServerUrl.list("https://cdn.example.com", pubkey))
    }

    @Test
    fun collapsesTrailingSlash() {
        assertEquals("https://cdn.example.com/upload", BlossomServerUrl.upload("https://cdn.example.com/"))
        assertEquals("https://cdn.example.com/mirror", BlossomServerUrl.mirror("https://cdn.example.com/"))
        assertEquals("https://cdn.example.com/list/$pubkey", BlossomServerUrl.list("https://cdn.example.com/", pubkey))
    }

    @Test
    fun buildsBlobUrlWithOptionalExtension() {
        assertEquals("https://cdn.example.com/$sha256", BlossomServerUrl.blob("https://cdn.example.com", sha256))
        assertEquals("https://cdn.example.com/$sha256.png", BlossomServerUrl.blob("https://cdn.example.com", sha256, "png"))
    }

    @Test
    fun extractsLowercaseBareDomainForServerScope() {
        assertEquals("cdn.example.com", BlossomServerUrl.domain("https://cdn.example.com"))
        assertEquals("cdn.example.com", BlossomServerUrl.domain("https://CDN.Example.com/"))
        assertEquals("cdn.example.com", BlossomServerUrl.domain("https://cdn.example.com:8443/upload"))
        assertEquals("blossom.band", BlossomServerUrl.domain("https://blossom.band"))
    }
}
