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

import com.vitorpamplona.amethyst.commons.richtext.IpfsGatewayResolver
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EncryptionKeyCacheTest {
    private val cid = "QmNdEr3bMJ9fudJZ4hmXy3R63v8ia7XZaNVACMQF42pkhi"
    private val cipher =
        AESGCM(
            ByteArray(32) { 1 },
            ByteArray(16) { 2 },
        )

    @After
    fun restore() {
        IpfsGatewayResolver.serverBasesProvider = { emptyList() }
    }

    @Test
    fun getIsExactAndDoesNotExpandIpfsAliases() {
        val cache = EncryptionKeyCache()
        cache.add("ipfs://$cid", cipher, "image/jpeg")
        assertSame(cipher, cache.get("ipfs://$cid")?.cipher)
        assertNull(cache.get("https://originless.gupt.app/ipfs/$cid"))
    }

    @Test
    fun addForMediaUrlRegistersIpfsAndGatewayAliases() {
        IpfsGatewayResolver.serverBasesProvider =
            { listOf("https://originless.gupt.app", "https://originless.example") }
        val cache = EncryptionKeyCache()
        cache.addForMediaUrl("ipfs://$cid", cipher, "image/jpeg")
        assertSame(cipher, cache.get("ipfs://$cid")?.cipher)
        assertSame(cipher, cache.get("https://originless.gupt.app/ipfs/$cid")?.cipher)
        assertSame(cipher, cache.get("https://originless.example/ipfs/$cid")?.cipher)
        assertEquals("image/jpeg", cache.get("https://originless.gupt.app/ipfs/$cid")?.mimeType)
    }
}
