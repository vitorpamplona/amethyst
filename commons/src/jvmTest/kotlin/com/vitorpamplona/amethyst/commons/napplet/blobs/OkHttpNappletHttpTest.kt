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
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The JVM [NappletHttp] against a real server: the OkHttp code the blob store and fetcher always ran. */
class OkHttpNappletHttpTest {
    private val seenCookies = mutableListOf<String?>()

    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                seenCookies += exchange.requestHeaders.getFirst("Cookie")
                when (val path = exchange.requestURI.path) {
                    "/fits" -> exchange.reply(200, "0123456789".encodeToByteArray())
                    "/chunked-big" -> exchange.reply(200, ByteArray(50), chunked = true)
                    "/declared-big" -> exchange.reply(200, ByteArray(11))
                    "/redirect" -> {
                        exchange.responseHeaders.add("Location", "/fits")
                        exchange.reply(302, ByteArray(0))
                    }
                    "/slow" -> {
                        Thread.sleep(2_000)
                        exchange.reply(200, ByteArray(1))
                    }
                    else -> exchange.reply(404, "no $path".encodeToByteArray())
                }
            }
            start()
        }
    private val base = "http://127.0.0.1:${server.address.port}"

    private fun com.sun.net.httpserver.HttpExchange.reply(
        status: Int,
        body: ByteArray,
        chunked: Boolean = false,
    ) {
        sendResponseHeaders(
            status,
            if (chunked) {
                0
            } else if (body.isEmpty()) {
                -1
            } else {
                body.size.toLong()
            },
        )
        if (body.isNotEmpty()) responseBody.use { it.write(body) }
        close()
    }

    @AfterTest
    fun tearDown() = server.stop(0)

    private val http = OkHttpNappletHttp(OkHttpClient())

    private fun get(
        path: String,
        followRedirects: Boolean = true,
        publicOnly: Boolean = false,
        timeoutMs: Long = 10_000,
    ) = runBlocking { http.get(NappletHttpRequest("$base$path", viaTor = false, maxBytes = 10, timeoutMs = timeoutMs, followRedirects = followRedirects, publicOnly = publicOnly)) }

    @Test
    fun theCapHoldsDeclaredOrNot() {
        assertContentEquals("0123456789".encodeToByteArray(), get("/fits").body)
        assertTrue(get("/declared-big").tooLarge)
        val chunked = get("/chunked-big")
        assertTrue(chunked.tooLarge)
        assertNull(chunked.body)
        assertTrue(chunked.received > 10, "what came in is counted, kept or not")
    }

    @Test
    fun aMissIsA404() {
        val missing = get("/nothing")
        assertEquals(404, missing.status)
        assertNull(missing.body)
    }

    @Test
    fun redirectsFollowOrComeBack() {
        assertContentEquals("0123456789".encodeToByteArray(), get("/redirect").body)
        val handedBack = get("/redirect", followRedirects = false)
        assertTrue(handedBack.isRedirect)
        assertEquals("/fits", handedBack.location)
    }

    @Test
    fun aPublicOnlyFetchRefusesWhatResolvesToThisMachine() {
        // The hardened client checks what a name resolves to; localhost is loopback.
        val local = "http://localhost:${server.address.port}/fits"
        assertFailsWith<NappletHttpBlockedException> {
            runBlocking { http.get(NappletHttpRequest(local, viaTor = false, maxBytes = 10, timeoutMs = 10_000, followRedirects = false, publicOnly = true)) }
        }
        assertTrue(seenCookies.isEmpty(), "nothing went out")
    }

    @Test
    fun aStalledCallTimesOut() {
        assertFailsWith<NappletHttpTimeoutException> { get("/slow", followRedirects = false, timeoutMs = 300) }
    }

    @Test
    fun theLaunchsClientIsUsedAsItIs() {
        // A plain request keeps the client's own call timeout (a launch's blob client has 45s).
        val strict = OkHttpNappletHttp(OkHttpClient.Builder().callTimeout(300, TimeUnit.MILLISECONDS).build())
        assertFailsWith<NappletHttpTimeoutException> {
            runBlocking { strict.get(NappletHttpRequest("$base/slow", viaTor = false, maxBytes = 10, timeoutMs = 60_000)) }
        }
    }
}
