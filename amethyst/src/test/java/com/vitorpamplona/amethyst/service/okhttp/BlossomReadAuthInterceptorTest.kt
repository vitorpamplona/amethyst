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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class BlossomReadAuthInterceptorTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"

    // --- blossomHashOrNull ------------------------------------------------

    @Test
    fun hashParsedFromPlainBlob() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/media/$sha.png"))
    }

    @Test
    fun hashParsedFromThumbnailVariant() {
        // Buzz thumbnails use the dot form <hash>.thumb.jpg — the base is still the hash.
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/media/$sha.thumb.jpg"))
    }

    @Test
    fun hashParsedWithoutExtension() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/$sha"))
    }

    @Test
    fun hashLowercasedFromUppercaseSegment() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/media/${sha.uppercase()}.png"))
    }

    @Test
    fun nonBlobPathsReturnNull() {
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/media/avatar.png"))
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/media/nostr.build_$sha.jpg"))
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/media/${sha}_thumb.jpg"))
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/"))
    }

    // --- intercept behavior ----------------------------------------------

    @Test
    fun retriesWithAuthOn401() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401, 200))

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(200, response.code)
        assertEquals(2, chain.requests.size)
        assertNull("first attempt is anonymous", chain.requests[0].header("Authorization"))
        assertEquals("Nostr token", chain.requests[1].header("Authorization"))
        assertEquals(host to sha, provider.calls.single())
        response.close()
    }

    @Test
    fun thumbnailUrlAlsoRetries() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.thumb.jpg", codes = listOf(401, 200))

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(200, response.code)
        assertEquals(sha, provider.calls.single().second)
        response.close()
    }

    @Test
    fun successfulRequestNeverSigns() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://blossom.example.com/$sha.png", codes = listOf(200))

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(200, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue("public host must not be signed", provider.calls.isEmpty())
        response.close()
    }

    @Test
    fun keepsThe401WhenNoSignerAvailable() {
        val provider = RecordingProvider(header = null)
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401))

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertEquals(host to sha, provider.calls.single())
        response.close()
    }

    @Test
    fun nonBlobUrlNeverSignsEvenOn401() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://example.com/media/avatar.png", codes = listOf(401))

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.calls.isEmpty())
        response.close()
    }

    @Test
    fun requestWithExistingAuthPassesThrough() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401), preAuthHeader = "Nostr existing")

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.calls.isEmpty())
        response.close()
    }

    @Test
    fun nonGetRequestPassesThrough() {
        val provider = RecordingProvider(header = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401), method = "PUT")

        val response = BlossomReadAuthInterceptor(provider::header).intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.calls.isEmpty())
        response.close()
    }

    @Test
    fun learnsHostThenSignsSubsequentBlobsUpFront() {
        val provider = RecordingProvider(header = "Nostr token")
        val interceptor = BlossomReadAuthInterceptor(provider::header)
        val otherSha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

        // First blob learns the host via the 401 probe + signed retry.
        val first = fakeChain("https://$host/media/$sha.png", codes = listOf(401, 200))
        interceptor.intercept(first.asChain()).close()
        assertEquals(2, first.requests.size)

        // Second, different blob on the same host is signed on the first attempt —
        // no anonymous probe, so a single request.
        val second = fakeChain("https://$host/media/$otherSha.png", codes = listOf(200))
        val response = interceptor.intercept(second.asChain())

        assertEquals(200, response.code)
        assertEquals("no anonymous probe on a known-gated host", 1, second.requests.size)
        assertEquals("Nostr token", second.requests.single().header("Authorization"))
        response.close()
    }

    @Test
    fun learnedHostWithoutSignerDoesNotLoop() {
        val provider = RecordingProvider(header = null)
        val interceptor = BlossomReadAuthInterceptor(provider::header)

        val first = fakeChain("https://$host/media/$sha.png", codes = listOf(401))
        interceptor.intercept(first.asChain()).close()

        // Host is now known, but with no signer the preemptive path must fall
        // back to a single anonymous request rather than retrying endlessly.
        val second = fakeChain("https://$host/media/$sha.png", codes = listOf(401))
        val response = interceptor.intercept(second.asChain())

        assertEquals(401, response.code)
        assertEquals(1, second.requests.size)
        response.close()
    }

    private class RecordingProvider(
        private val header: String?,
    ) {
        val calls = mutableListOf<Pair<String, HexKey>>()

        fun header(
            host: String,
            sha256: HexKey,
        ): String? {
            calls.add(host to sha256)
            return header
        }
    }

    /**
     * Records every [Request] it is asked to proceed and answers each with the
     * next code from [codes], so `[401, 200]` models "anonymous fails,
     * authenticated succeeds".
     */
    private class FakeChain(
        private val request: Request,
        private val codes: List<Int>,
    ) {
        val requests = mutableListOf<Request>()

        fun asChain(): Interceptor.Chain =
            Proxy.newProxyInstance(
                Interceptor.Chain::class.java.classLoader,
                arrayOf(Interceptor.Chain::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "request" -> request
                    "proceed" -> {
                        val proceeded = args[0] as Request
                        requests.add(proceeded)
                        Response
                            .Builder()
                            .request(proceeded)
                            .protocol(Protocol.HTTP_1_1)
                            .code(codes[requests.size - 1])
                            .message("msg")
                            .body("".toResponseBody(null))
                            .build()
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Interceptor.Chain
    }

    private fun fakeChain(
        url: String,
        codes: List<Int>,
        method: String = "GET",
        preAuthHeader: String? = null,
    ): FakeChain {
        val request =
            Request
                .Builder()
                .url(url.toHttpUrl())
                .apply {
                    if (method == "GET") get() else method(method, "".toRequestBody())
                    preAuthHeader?.let { header("Authorization", it) }
                }.build()
        return FakeChain(request, codes)
    }
}
