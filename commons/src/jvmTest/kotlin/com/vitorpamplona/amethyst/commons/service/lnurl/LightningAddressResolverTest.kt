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
package com.vitorpamplona.amethyst.commons.service.lnurl

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** [LightningAddressResolver] (the `amy` path) refuses amounts outside the receiver's range, in sats. */
class LightningAddressResolverTest {
    private class ScriptedReplies(
        private val lnurlp: String,
    ) : Interceptor {
        val requested = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url.toString()
            requested += url
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(lnurlp.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun fetch(
        lnurlp: String,
        milliSats: Long,
    ): Pair<LightningAddressResolver.Result, List<String>> {
        val replies = ScriptedReplies(lnurlp)
        val resolver = LightningAddressResolver(OkHttpClient.Builder().addInterceptor(replies).build())
        return runBlocking { resolver.fetchInvoice("alice@example.com", milliSats) } to replies.requested
    }

    @Test
    fun anAmountBelowMinSendableIsRefusedInSats() {
        val (result, requested) = fetch("""{"callback":"https://example.com/callback","minSendable":5000}""", 3000L)

        assertEquals("alice@example.com only accepts payments of at least 5 sats", assertIs<LightningAddressResolver.Result.Error>(result).message)
        assertEquals(1, requested.size, "the callback must not be called")
    }

    @Test
    fun anAmountAboveMaxSendableIsRefusedInSats() {
        val (result, requested) = fetch("""{"callback":"https://example.com/callback","maxSendable":50000000}""", 100_000_000L)

        assertEquals("alice@example.com only accepts payments of up to 50000 sats", assertIs<LightningAddressResolver.Result.Error>(result).message)
        assertEquals(1, requested.size, "the callback must not be called")
    }
}
