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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [KtorNappletHttp] against Ktor's mock engine: the rules OkHttpNappletHttp keeps on the JVM. */
class KtorNappletHttpTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val asked = mutableListOf<String>()
    private var public = true

    private fun http(handler: MockRequestHandler): Pair<KtorNappletHttp, MutableList<Boolean>> {
        val routes = mutableListOf<Boolean>()
        val client =
            HttpClient(
                MockEngine { request ->
                    seen += request
                    handler(request)
                },
            )
        return KtorNappletHttp({ viaTor -> client.also { routes += viaTor } }) { host -> public.also { asked += host } } to routes
    }

    // The mock engine answers on its own dispatcher: on the test's virtual clock every call's timeout
    // would pass at once. The timeouts here run on the real one.
    private fun realTime(block: suspend CoroutineScope.() -> Unit) = runTest { withContext(Dispatchers.Default, block) }

    private fun request(
        url: String,
        maxBytes: Long = 10,
        viaTor: Boolean = false,
        followRedirects: Boolean = true,
        publicOnly: Boolean = false,
        timeoutMs: Long = 5_000,
    ) = NappletHttpRequest(url, viaTor, maxBytes, timeoutMs, followRedirects, publicOnly)

    @Test
    fun aBodyWithinTheCapComesBack() =
        realTime {
            val (http, routes) = http { respond("0123456789", HttpStatusCode.OK) }
            val response = http.get(request("https://blossom.example/a", viaTor = true))
            assertTrue(response.isSuccessful)
            assertContentEquals("0123456789".encodeToByteArray(), response.body)
            assertEquals(listOf(true), routes, "the request took the route it asked for")
        }

    @Test
    fun aDeclaredLengthOverTheCapIsRefusedUnread() =
        realTime {
            val (http, _) = http { respond("01234567890", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "11")) }
            val response = http.get(request("https://blossom.example/a"))
            assertTrue(response.tooLarge)
            assertNull(response.body)
            assertEquals(0, response.received)
        }

    @Test
    fun aBodyWithNoLengthIsStillCapped() =
        realTime {
            val (http, _) = http { respond(ByteReadChannel(ByteArray(50)), HttpStatusCode.OK) }
            val response = http.get(request("https://blossom.example/a"))
            assertTrue(response.tooLarge)
            assertNull(response.body)
            assertEquals(11, response.received, "read one byte past the cap, no more")
        }

    @Test
    fun aMissingFileIsA404WithNoBody() =
        realTime {
            val (http, _) = http { respond("gone", HttpStatusCode.NotFound) }
            val response = http.get(request("https://blossom.example/a"))
            assertEquals(404, response.status)
            assertFalse(response.isSuccessful)
            assertNull(response.body)
        }

    @Test
    fun noCookieGoesBackToTheServerThatSetIt() =
        realTime {
            val (http, _) = http { respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "session=secret")) }
            http.get(request("https://site.example/a", publicOnly = true))
            http.get(request("https://site.example/b", publicOnly = true))
            http.get(request("https://site.example/c"))
            assertEquals(3, seen.size)
            seen.forEach {
                assertNull(it.headers[HttpHeaders.Cookie])
                assertNull(it.headers[HttpHeaders.Authorization])
            }
        }

    @Test
    fun redirectsAreFollowedHereOrHandedBack() =
        realTime {
            val (http, _) =
                http { request ->
                    if (request.url.encodedPath == "/old") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/new"))
                    } else {
                        respond("moved", HttpStatusCode.OK)
                    }
                }
            assertContentEquals("moved".encodeToByteArray(), http.get(request("https://site.example/old")).body)
            assertEquals("https://site.example/new", seen.last().url.toString())

            val handedBack = http.get(request("https://site.example/old", followRedirects = false))
            assertEquals(302, handedBack.status)
            assertTrue(handedBack.isRedirect)
            assertEquals("/new", handedBack.location)
        }

    @Test
    fun aPublicOnlyFetchRefusesPrivateAddressesOnEveryHop() =
        realTime {
            val (http, _) =
                http { request ->
                    if (request.url.host == "site.example") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://192.168.1.1/admin"))
                    } else {
                        respond("router", HttpStatusCode.OK)
                    }
                }
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://127.0.0.1/", publicOnly = true)) }
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://[::1]/", publicOnly = true)) }
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://site.example/", publicOnly = true)) }
            assertEquals(listOf("site.example"), seen.map { it.url.host }, "the private hop never went out")

            public = false
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://lan.example/", publicOnly = true)) }
            assertEquals(listOf("site.example", "lan.example"), asked)
            // Not a fetch on a page's behalf: the address is not its to vet.
            assertEquals(200, http.get(request("https://192.168.1.1/admin")).status)
        }

    @Test
    fun overTorOnlyIpLiteralsAreChecked() =
        realTime {
            public = false
            val (http, _) = http { respond("ok", HttpStatusCode.OK) }
            assertEquals(200, http.get(request("https://name.example/", viaTor = true, publicOnly = true)).status)
            assertTrue(asked.isEmpty(), "the exit resolves names")
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://10.0.0.1/", viaTor = true, publicOnly = true)) }
        }

    @Test
    fun aPublicOnlyFetchIsNotRedirectedToPlainHttp() =
        realTime {
            val (http, _) = http { respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "http://site.example/")) }
            assertFailsWith<NappletHttpBlockedException> { http.get(request("https://site.example/", publicOnly = true)) }
        }

    @Test
    fun aStalledServerTimesOut() =
        realTime {
            val (http, _) =
                http {
                    delay(10_000)
                    respond("late", HttpStatusCode.OK)
                }
            assertFailsWith<NappletHttpTimeoutException> { http.get(request("https://slow.example/", timeoutMs = 1_000)) }
        }

    @Test
    fun ipLiteralsParse() {
        assertContentEquals(byteArrayOf(127, 0, 0, 1), KtorNappletHttp.ipLiteralBytes("127.0.0.1"))
        assertNull(KtorNappletHttp.ipLiteralBytes("example.com"))
        assertNull(KtorNappletHttp.ipLiteralBytes("1.2.3"))
        assertNull(KtorNappletHttp.ipLiteralBytes("256.1.1.1"))
        assertContentEquals(ByteArray(15) + byteArrayOf(1), KtorNappletHttp.ipLiteralBytes("::1"))
        assertContentEquals(ByteArray(10) + byteArrayOf(-1, -1, 10, 0, 0, 1), KtorNappletHttp.ipLiteralBytes("::ffff:10.0.0.1"))
        assertFalse(NappletPublicAddress.isPublic(KtorNappletHttp.ipLiteralBytes("::ffff:10.0.0.1")!!))
        assertTrue(NappletPublicAddress.isPublic(KtorNappletHttp.ipLiteralBytes("2606:4700:4700::1111")!!))
        assertNull(KtorNappletHttp.ipLiteralBytes("1:2:3"))
    }
}
