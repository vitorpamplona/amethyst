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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.amethyst.commons.originless.OriginlessGatewayFailoverInterceptor
import com.vitorpamplona.amethyst.commons.richtext.IpfsGatewayResolver
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class OriginlessGatewayFailoverInterceptorTest {
    private val cid = "QmcnXw2spQuvsegCQ56SWHxFtU8u41VHh7r4PRTxDgvMHX"
    private val primary = "https://originless.gupt.app"
    private val secondary = "https://originless.example"

    @After
    fun restore() {
        IpfsGatewayResolver.serverBasesProvider = { emptyList() }
    }

    @Test
    fun doesNotFailoverWhenOnlyOneServerIsConfigured() {
        val interceptor = OriginlessGatewayFailoverInterceptor { listOf(primary) }
        val chain = FakeChain("GET", "$primary/ipfs/$cid", codes = listOf(404))
        val response = interceptor.intercept(chain.asChain())
        assertEquals(404, response.code)
        assertEquals(1, chain.requests.size)
        response.close()
    }

    @Test
    fun skipsPostUploads() {
        val interceptor = OriginlessGatewayFailoverInterceptor { listOf(primary, secondary) }
        val chain = FakeChain("POST", "$primary/upload", codes = listOf(500))
        val response = interceptor.intercept(chain.asChain())
        assertEquals(500, response.code)
        assertEquals(1, chain.requests.size)
        response.close()
    }

    @Test
    fun retriesNextOriginlessNodeOn404() {
        val interceptor = OriginlessGatewayFailoverInterceptor { listOf(primary, secondary) }
        val chain =
            FakeChain(
                "GET",
                "$primary/ipfs/$cid",
                codes = listOf(404, 200),
            )
        val response = interceptor.intercept(chain.asChain())
        assertEquals(200, response.code)
        assertEquals(2, chain.requests.size)
        assertEquals("$primary/ipfs/$cid", chain.requests[0].url.toString())
        assertEquals("$secondary/ipfs/$cid", chain.requests[1].url.toString())
        response.close()
    }

    @Test
    fun retriesNextOriginlessNodeOn500() {
        val interceptor = OriginlessGatewayFailoverInterceptor { listOf(primary, secondary) }
        val chain =
            FakeChain(
                "GET",
                "$primary/ipfs/$cid",
                codes = listOf(500, 200),
            )
        val response = interceptor.intercept(chain.asChain())
        assertEquals(200, response.code)
        assertEquals(2, chain.requests.size)
        assertEquals("$secondary/ipfs/$cid", chain.requests[1].url.toString())
        response.close()
    }

    @Test
    fun retriesNextOriginlessNodeOnConnectionFailure() {
        val interceptor = OriginlessGatewayFailoverInterceptor { listOf(primary, secondary) }
        val chain =
            FakeChain(
                "GET",
                "$primary/ipfs/$cid",
                codes = listOf(200),
                failFirst = true,
            )
        val response = interceptor.intercept(chain.asChain())
        assertEquals(200, response.code)
        assertEquals(2, chain.requests.size)
        assertTrue(
            chain.requests[1]
                .url
                .toString()
                .startsWith(secondary),
        )
        response.close()
    }

    /**
     * Records every [Request] it is asked to proceed and answers each with the
     * next code from [codes]. [failFirst] throws [IOException] on the first proceed.
     */
    private class FakeChain(
        private val method: String,
        url: String,
        private val codes: List<Int>,
        private val failFirst: Boolean = false,
    ) {
        private val request =
            Request
                .Builder()
                .url(url)
                .method(method, if (method == "POST") ByteArray(0).toRequestBody(null) else null)
                .build()
        val requests = mutableListOf<Request>()
        private var lastBody: ClosingSource? = null

        fun asChain(): Interceptor.Chain =
            Proxy.newProxyInstance(
                Interceptor.Chain::class.java.classLoader,
                arrayOf(Interceptor.Chain::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "request" -> request
                    "proceed" -> {
                        // Mirror OkHttp's real invariant. RealCall refuses a new request while a
                        // previous response is still open, and a fake that hands back canned
                        // responses without this check will happily walk a path that crashes at
                        // runtime -- which is exactly what happened here.
                        val stillOpen = lastBody
                        if (stillOpen != null && !stillOpen.closed) {
                            throw IllegalStateException(
                                "cannot make a new request because the previous response is still open: please call response.close()",
                            )
                        }
                        val proceeded = args[0] as Request
                        requests.add(proceeded)
                        if (failFirst && requests.size == 1) {
                            throw IOException("unreachable")
                        }
                        val code = codes.getOrElse(requests.size - 1) { codes.last() }
                        val tracked = ClosingSource(Buffer().writeUtf8("body"))
                        lastBody = tracked
                        Response
                            .Builder()
                            .request(proceeded)
                            .protocol(Protocol.HTTP_1_1)
                            .code(code)
                            .message("msg")
                            .body(tracked.buffer().asResponseBody(null, 4))
                            .build()
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Interceptor.Chain
    }

    /** Okio source that records whether the consumer closed it. */
    private class ClosingSource(
        private val delegate: Source,
    ) : Source by delegate {
        var closed = false

        override fun close() {
            closed = true
            delegate.close()
        }
    }
}
