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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.sun.net.httpserver.HttpServer
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Blob fetches are built outside the app's HTTP factories, but still name the app. */
class NappletBlobHttpUserAgentTest {
    private val seen = ConcurrentLinkedQueue<String>()

    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                seen += exchange.requestHeaders.getFirst("User-Agent").orEmpty()
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            start()
        }
    private val url = "http://127.0.0.1:${server.address.port}/blob"

    @AfterTest
    fun tearDown() {
        server.stop(0)
        NappletBlobHttp.userAgent = null
    }

    private fun fetch(request: Request) {
        NappletBlobHttp
            .client(proxyPort = -1)
            .newCall(request)
            .execute()
            .close()
    }

    @Test
    fun sendsTheAppUserAgent() {
        NappletBlobHttp.userAgent = "Amethyst/1.2.3"

        fetch(Request.Builder().url(url).build())

        assertEquals(listOf("Amethyst/1.2.3"), seen.toList())
    }

    @Test
    fun keepsTheUserAgentARequestNames() {
        NappletBlobHttp.userAgent = "Amethyst/1.2.3"

        // The in-app browser's downloads carry the WebView's own agent.
        fetch(
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "WebView/1")
                .build(),
        )

        assertEquals(listOf("WebView/1"), seen.toList())
    }
}
