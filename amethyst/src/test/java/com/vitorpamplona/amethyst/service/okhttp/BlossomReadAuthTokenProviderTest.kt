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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlossomReadAuthTokenProviderTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"
    private val signer = NostrSignerInternal(KeyPair())

    @Test
    fun signsAndFormatsHeader() {
        val provider = BlossomReadAuthTokenProvider(signerProvider = { signer })
        val header = provider.authHeader(host, sha)
        assertTrue("expected a Nostr auth header, got $header", header!!.startsWith("Nostr "))
    }

    @Test
    fun returnsNullWhenNoSigner() {
        val provider = BlossomReadAuthTokenProvider(signerProvider = { null })
        assertNull(provider.authHeader(host, sha))
    }

    @Test
    fun cachesPerHostWithinTtl() {
        // signerProvider is consulted only on a cache miss, so its invocation
        // count is the number of times a fresh token was signed.
        var lookups = 0
        val provider =
            BlossomReadAuthTokenProvider(
                signerProvider = {
                    lookups++
                    signer
                },
                clock = { 0L },
            )

        val first = provider.authHeader(host, sha)
        val second = provider.authHeader(host, sha)

        assertEquals("second call must be served from cache", first, second)
        assertEquals("signer must run only once for the same host", 1, lookups)
    }

    @Test
    fun differentHostSignsSeparately() {
        val provider = BlossomReadAuthTokenProvider(signerProvider = { signer }, clock = { 0L })

        val a = provider.authHeader(host, sha)
        val b = provider.authHeader("other.example.com", sha)

        assertNotEquals(a, b)
    }

    @Test
    fun refreshesAfterExpiry() {
        var now = 0L
        val provider = BlossomReadAuthTokenProvider(signerProvider = { signer }, clock = { now })

        val first = provider.authHeader(host, sha)
        now += 60L * 60L * 1000L // one hour later — past the 55-min TTL
        val second = provider.authHeader(host, sha)

        assertNotEquals("expired token must be re-signed", first, second)
    }
}
