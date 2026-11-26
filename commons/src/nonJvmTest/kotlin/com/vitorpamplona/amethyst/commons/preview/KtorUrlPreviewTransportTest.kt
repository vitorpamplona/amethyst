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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [UrlPreview] on [KtorUrlPreviewTransport], against a mock server: no network. */
class KtorUrlPreviewTransportTest {
    private val page =
        "<html><head><meta property=\"og:title\" content=\"A page\">" +
            "<meta property=\"og:image\" content=\"https://example.com/a.png\"></head><body></body></html>"

    private fun transport(engine: MockEngine) = KtorUrlPreviewTransport { HttpClient(engine) }

    @Test
    fun readsAPage() =
        runTest {
            val engine = MockEngine { respond(page, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")) }

            val info = UrlPreview().getDocument("https://example.com/x", transport(engine))

            assertEquals("A page", info.title)
            assertEquals("https://example.com/a.png", info.image)
            assertEquals("text/html; charset=utf-8", info.mimeType)
        }

    @Test
    fun anImageIsItsOwnPreview() =
        runTest {
            val engine = MockEngine { respond(ByteArray(10), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/webp")) }

            val info = UrlPreview().getDocument("https://example.com/a", transport(engine))

            assertEquals("https://example.com/a", info.image)
            assertEquals("image/webp", info.mimeType)
        }

    @Test
    fun anErrorStatusFails() =
        runTest {
            val engine = MockEngine { respond(page, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/html")) }

            val e = assertFailsWith<IllegalArgumentException> { UrlPreview().getDocument("https://example.com/x", transport(engine)) }
            assertEquals("Website returned: 404", e.message)
        }

    @Volatile
    private var written = 0L

    @Test
    fun aBodyThatNeverEndsIsNotDownloaded() =
        runTest {
            // The head never closes and the body never ends: the read stops at the cap and leaving
            // the block ends the call, or this test would never finish. What the server gets to
            // write past the cap is Ktor's channel buffering between the engine and the reader
            // (about 2.5 MB on the mock engine, measured), not the reader asking for more.
            val chunk = ByteArray(16 * 1024) { 'x'.code.toByte() }
            val engine =
                MockEngine {
                    val body: ByteReadChannel =
                        backgroundScope
                            .writer {
                                channel.writeFully("<html><head><title>Endless</title>".encodeToByteArray())
                                while (true) {
                                    channel.writeFully(chunk)
                                    channel.flush()
                                    written += chunk.size
                                }
                            }.channel
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
                }

            val info = UrlPreview().getDocument("https://example.com/forever", transport(engine))

            assertEquals("Endless", info.title)
            assertTrue(written < 4L * MAX_PREVIEW_BYTES, "wrote $written bytes for a $MAX_PREVIEW_BYTES-byte cap")
        }
}
