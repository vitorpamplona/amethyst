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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Proxy
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalBlossomCacheRedirectSafetyTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"
    private val origin = "https://blossom.example.com/$sha.jpg"
    private val bridged = "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fblossom.example.com"

    private object IdentityCipher : NostrCipher {
        override fun name() = "identity"

        override fun encrypt(bytesToEncrypt: ByteArray) = bytesToEncrypt

        override fun decrypt(bytesToDecrypt: ByteArray) = bytesToDecrypt

        override fun decryptOrNull(bytesToDecrypt: ByteArray) = bytesToDecrypt
    }

    /** Records every request passed to proceed; [refuse] makes matching ones fail like a closed port. */
    private fun chain(
        request: Request,
        sent: MutableList<Request>,
        refuse: (Request) -> Boolean = { false },
    ): Interceptor.Chain =
        Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> {
                    val proceeded = args[0] as Request
                    sent.add(proceeded)
                    if (refuse(proceeded)) throw ConnectException("Connection refused")
                    Response
                        .Builder()
                        .request(proceeded)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody(null))
                        .build()
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Interceptor.Chain

    @Test
    fun getIsBridged() {
        val sent = mutableListOf<Request>()
        LocalBlossomCacheRedirectInterceptor { true }.intercept(chain(Request.Builder().url(origin).build(), sent)).close()
        assertEquals(bridged, sent.single().url.toString())
    }

    @Test
    fun serverSpecificMethodsAreNotBridged() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val requests =
            listOf(
                Request
                    .Builder()
                    .url(origin)
                    .head()
                    .build(),
                Request
                    .Builder()
                    .url(origin)
                    .delete()
                    .header("Authorization", "Nostr abc")
                    .build(),
                Request
                    .Builder()
                    .url(origin)
                    .put("x".toRequestBody())
                    .build(),
            )
        requests.forEach { request ->
            val sent = mutableListOf<Request>()
            interceptor.intercept(chain(request, sent)).close()
            assertEquals(origin, sent.single().url.toString(), "${request.method} must reach its server")
        }
    }

    @Test
    fun decryptionKeyFollowsTheRewrite() {
        val keys = EncryptionKeyCache()
        val info = DecryptInformation(IdentityCipher, "image/jpeg")
        keys.add(origin, info)

        val sent = mutableListOf<Request>()
        LocalBlossomCacheRedirectInterceptor(keyCache = keys) { true }.intercept(chain(Request.Builder().url(origin).build(), sent)).close()

        // EncryptedBlobInterceptor runs after the rewrite and looks the key up by the URL it sees.
        assertSame(info, assertNotNull(keys.get(sent.single().url.toString())))
    }

    @Test
    fun deadCacheFallsBackToOriginAndIsReported() {
        var reports = 0
        val sent = mutableListOf<Request>()
        val response =
            LocalBlossomCacheRedirectInterceptor(onUnreachable = { reports++ }) { true }
                .intercept(chain(Request.Builder().url(origin).build(), sent) { it.url.host == "127.0.0.1" })

        assertEquals(200, response.code)
        assertEquals(listOf(bridged, origin), sent.map { it.url.toString() })
        assertEquals(1, reports)
        response.close()
    }

    @Test
    fun deadCacheIsReportedForResolvedBlossomUrls() {
        var reports = 0
        val sent = mutableListOf<Request>()
        val request = Request.Builder().url("http://127.0.0.1:24242/$sha.mp4?xs=https://blossom.example.com").build()

        assertFailsWith<ConnectException> {
            LocalBlossomCacheRedirectInterceptor(onUnreachable = { reports++ }) { true }
                .intercept(chain(request, sent) { true })
        }
        assertEquals(1, reports)
        assertTrue(sent.single().url.host == "127.0.0.1")
    }
}
