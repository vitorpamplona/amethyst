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
package com.vitorpamplona.quartz.nip57Zaps.validate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LnurlEndpointCacheTest {
    @Test
    fun `put and get round-trip with normalization`() {
        LnurlEndpointCache.clear()
        val info = LnurlEndpointInfo(nostrPubkey = "a".repeat(64), allowsNostr = true)
        LnurlEndpointCache.put("https://Example.COM/.well-known/lnurlp/Vitor/", info)
        assertEquals(info, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/Vitor"))
    }

    @Test
    fun `path case is preserved`() {
        LnurlEndpointCache.clear()
        val info = LnurlEndpointInfo(nostrPubkey = "b".repeat(64), allowsNostr = true)
        LnurlEndpointCache.put("https://example.com/.well-known/lnurlp/Vitor", info)
        // Different path case is a different cache entry.
        assertEquals(null, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/vitor"))
    }

    @Test
    fun `eviction at MAX_ENTRIES boundary`() {
        LnurlEndpointCache.clear()
        for (i in 0 until 1001) {
            LnurlEndpointCache.put(
                "https://example.com/.well-known/lnurlp/user$i",
                LnurlEndpointInfo("c".repeat(64), true),
            )
        }
        // Cache should not exceed 1000 entries; the first inserted key should be evicted.
        assertEquals(null, LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/user0"))
        assertTrue(LnurlEndpointCache.get("https://example.com/.well-known/lnurlp/user1000") != null)
    }
}
