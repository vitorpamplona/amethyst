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
package com.vitorpamplona.amethyst.cli

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** `AMY_LOCAL_ONLY` must cover Blossom, not only relay sockets. */
class LocalOnlyBlossomTest {
    private val guarded = BlossomClient(LocalOnly.httpClientBuilder(guarded = true).build())

    @Test
    fun publicBlossomHostIsRefusedBeforeConnecting() {
        // An IP literal: the guard decides without a DNS lookup, and refuses before any socket opens.
        val error = assertFailsWith<IOException> { runBlocking { guarded.download("http://1.1.1.1/" + "a".repeat(64)) } }
        assertContains(error.message.orEmpty(), LocalOnly.ENV)
    }

    @Test
    fun loopbackBlossomServerIsReached() {
        val blob = byteArrayOf(1, 2, 3)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, blob.size.toLong())
            exchange.responseBody.use { it.write(blob) }
        }
        server.start()
        try {
            val bytes = runBlocking { guarded.download("http://127.0.0.1:${server.address.port}/" + "b".repeat(64)) }
            assertContentEquals(blob, bytes)
        } finally {
            server.stop(0)
        }
    }
}
