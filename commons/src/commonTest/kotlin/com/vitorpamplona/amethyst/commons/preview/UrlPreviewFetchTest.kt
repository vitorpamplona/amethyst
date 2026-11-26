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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [UrlPreview] over a fake [UrlPreviewTransport]: which Content-Types make a preview, and how much it reads. */
class UrlPreviewFetchTest {
    /** Serves [bytes] in reads of at most [chunk] bytes, counting in [pulled] how many were handed out. */
    private class FakeTransport(
        private val code: Int = 200,
        private val contentType: String?,
        private val bytes: (Long) -> Byte?,
        private val chunk: Int = 8192,
    ) : UrlPreviewTransport {
        var pulled = 0L

        override suspend fun <T> get(
            url: String,
            read: suspend (UrlPreviewResponse) -> T,
        ): T =
            read(
                UrlPreviewResponse(code, contentType) { buffer, offset, length ->
                    var n = 0
                    while (n < minOf(length, chunk)) {
                        val b = bytes(pulled) ?: break
                        buffer[offset + n] = b
                        n++
                        pulled++
                    }
                    if (n == 0) -1 else n
                },
            )
    }

    private fun serving(
        body: String,
        contentType: String? = "text/html; charset=utf-8",
        code: Int = 200,
    ): FakeTransport {
        val data = body.encodeToByteArray()
        return FakeTransport(code, contentType, { i -> if (i < data.size) data[i.toInt()] else null })
    }

    private val page =
        "<html><head><title>T</title><meta property=\"og:title\" content=\"A page\">" +
            "<meta property=\"og:image\" content=\"https://example.com/a.png\"></head><body></body></html>"

    private suspend fun fetch(
        transport: UrlPreviewTransport,
        url: String = "https://example.com/x",
    ) = UrlPreview().getDocument(url, transport)

    @Test
    fun htmlReadsTheOpenGraphTags() =
        runTest {
            val info = fetch(serving(page))
            assertEquals("A page", info.title)
            assertEquals("https://example.com/a.png", info.image)
            assertEquals("text/html; charset=utf-8", info.mimeType)
            assertTrue(info.fetchComplete())
        }

    @Test
    fun mediaTypesPreviewTheUrlItself() =
        runTest {
            for (type in listOf("image/png", "video/mp4", "audio/mpeg", "application/pdf", "IMAGE/JPEG")) {
                val transport = serving("binary", contentType = type)
                val info = fetch(transport, "https://example.com/file")
                assertEquals("https://example.com/file", info.image, type)
                assertEquals(type, info.mimeType)
                assertEquals(0L, transport.pulled, "$type read its body")
            }
        }

    @Test
    fun otherTypesFail() =
        runTest {
            assertFailsWith<IllegalArgumentException> { fetch(serving("{}", contentType = "application/json")) }
            assertFailsWith<IllegalArgumentException> { fetch(serving("x", contentType = "text/plain")) }
            assertFailsWith<IllegalArgumentException> { fetch(serving(page, contentType = null)) }
            assertFailsWith<IllegalArgumentException> { fetch(serving(page, contentType = "html")) }
        }

    @Test
    fun anErrorStatusFails() =
        runTest {
            val e = assertFailsWith<IllegalArgumentException> { fetch(serving(page, code = 404)) }
            assertEquals("Website returned: 404", e.message)
            assertFailsWith<IllegalArgumentException> { fetch(serving(page, code = 301)) }
        }

    @Test
    fun theHeaderCharsetDecodesThePage() =
        runTest {
            // "Café" in Latin-1: é is 0xE9, which is not valid UTF-8 on its own.
            val head = "<html><head><meta property=\"og:title\" content=\"Caf".encodeToByteArray() + byteArrayOf(0xE9.toByte()) + "\"></head>".encodeToByteArray()
            val transport = FakeTransport(200, "text/html; charset=\"ISO-8859-1\"", { i -> if (i < head.size) head[i.toInt()] else null })
            assertEquals("Café", fetch(transport).title)
        }

    @Test
    fun aBodyThatNeverEndsIsReadOnlyUpToTheCap() =
        runTest {
            // An attacker's host streams `text/html` forever, and no head ever closes.
            val start = "<html><head><title>Endless</title>".encodeToByteArray()
            val transport = FakeTransport(200, "text/html", { i -> if (i < start.size) start[i.toInt()] else 'x'.code.toByte() })

            val info = fetch(transport)

            assertEquals("Endless", info.title)
            assertEquals(MAX_PREVIEW_BYTES.toLong(), transport.pulled)
        }

    @Test
    fun readingStopsOnceTheHeadCloses() =
        runTest {
            val head = page.substringBefore("<body>")
            val data = (head + "<body>" + "x".repeat(1_000_000) + "</body></html>").encodeToByteArray()
            val transport = FakeTransport(200, "text/html", { i -> if (i < data.size) data[i.toInt()] else null })

            assertEquals("https://example.com/a.png", fetch(transport).image)
            assertTrue(transport.pulled < 32 * 1024, "read ${transport.pulled} bytes of a page whose head ends at ${head.length}")
        }

    @Test
    fun aHeadCloseSplitAcrossTwoReadsIsStillFound() =
        runTest {
            val head = page.substringBefore("<body>")
            val data = (head + "<body>" + "x".repeat(1_000_000)).encodeToByteArray()
            // Reads of 3 bytes split `</head` across reads.
            val transport = FakeTransport(200, "text/html", { i -> if (i < data.size) data[i.toInt()] else null }, chunk = 3)

            assertEquals("A page", fetch(transport).title)
            assertTrue(transport.pulled < head.length + 64, "read ${transport.pulled} bytes of a page whose head ends at ${head.length}")
        }

    @Test
    fun contentTypeParsing() {
        val type = PreviewMediaType.parse("Text/HTML; Charset='UTF-8'; q=1")
        assertEquals("text", type.type)
        assertEquals("html", type.subtype)
        assertEquals("UTF-8", type.charsetName())
        assertEquals("Text/HTML; Charset='UTF-8'; q=1", type.toString())

        assertNull(PreviewMediaType.parse("text/html").charsetName())
        assertNull(PreviewMediaType.parse("text/html; charset=no-such-charset").charsetName())
        assertEquals("text", PreviewMediaType.parse("text/html;").type)
        assertFailsWith<IllegalArgumentException> { PreviewMediaType.parse("text/html; charset") }
    }
}
