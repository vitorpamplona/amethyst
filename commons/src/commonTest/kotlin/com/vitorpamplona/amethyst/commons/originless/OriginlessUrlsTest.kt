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
package com.vitorpamplona.amethyst.commons.originless

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginlessUrlsTest {
    @Test
    fun normalizeBaseStripsTrailingSlashAndAddsHttps() {
        assertEquals("https://originless.gupt.app", OriginlessUrls.normalizeBase("https://originless.gupt.app/"))
        assertEquals("https://originless.example", OriginlessUrls.normalizeBase("originless.example"))
        assertEquals(OriginlessUrls.DEFAULT_SERVER, OriginlessUrls.normalizeBase("  "))
    }

    @Test
    fun uploadAndGatewayUrls() {
        val base = "https://originless.gupt.app"
        assertEquals("https://originless.gupt.app/upload", OriginlessUrls.uploadUrl(base))
        assertEquals("https://originless.gupt.app/ipfs/", OriginlessUrls.gatewayPrefix(base))
        assertEquals(
            "https://originless.gupt.app/ipfs/bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi",
            OriginlessUrls.gatewayUrl(base, "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"),
        )
    }

    @Test
    fun toIpfsUriStripsExistingScheme() {
        assertEquals("ipfs://abc", OriginlessUrls.toIpfsUri("abc"))
        assertEquals("ipfs://abc", OriginlessUrls.toIpfsUri("ipfs://abc"))
        assertEquals("ipfs://abc", OriginlessUrls.toIpfsUri("/abc"))
    }

    @Test
    fun parseUploadResponse() {
        val json =
            """{"status":"success","cid":"bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi","size":12,"type":"image/png","filename":"x.png","pinned":true}"""
        val parsed = JsonMapper.fromJson<OriginlessUploadResponse>(json)
        assertFalse(parsed.isError())
        assertEquals("bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi", parsed.requireCid())
        assertEquals(12L, parsed.size)
        assertEquals("image/png", parsed.type)
        assertTrue(parsed.pinned == true)
    }
}
