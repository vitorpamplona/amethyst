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
package com.vitorpamplona.amethyst.commons.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpfsGatewayResolverTest {
    @Test
    fun isIpfsUriMatchesCorrectly() {
        assertTrue(IpfsGatewayResolver.isIpfsUri("ipfs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
        assertTrue(IpfsGatewayResolver.isIpfsUri("IPFS://bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
        assertTrue(IpfsGatewayResolver.isIpfsUri("IpFs://bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
        assertTrue(IpfsGatewayResolver.isIpfsUri("ipfs:bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
        assertFalse(IpfsGatewayResolver.isIpfsUri("https://dweb.link/ipfs/bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
        assertFalse(IpfsGatewayResolver.isIpfsUri("blossom:bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"))
    }

    @Test
    fun toHttpUrlWithDefaultGateway() {
        val cid = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
        assertEquals(
            "https://dweb.link/ipfs/$cid",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid"),
        )
        assertEquals(
            "https://dweb.link/ipfs/$cid/image.png",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid/image.png"),
        )
    }

    @Test
    fun toHttpUrlWithCustomGateway() {
        val cid = "QmXoypizjW3WknFiJnKLwHCnL72vedxjQkDDP1mXWo6uco"
        assertEquals(
            "http://localhost:3232/ipfs/$cid",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid", "http://localhost:3232"),
        )
        assertEquals(
            "http://localhost:3232/ipfs/$cid",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid", "http://localhost:3232/ipfs/"),
        )
    }

    @Test
    fun getAllCandidateUrlsPrefersCustomGatewayThenDefaults() {
        val cid = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
        val candidates = IpfsGatewayResolver.getAllCandidateUrls("ipfs://$cid", "http://localhost:3232/")
        assertEquals(3, candidates.size)
        assertEquals("http://localhost:3232/ipfs/$cid", candidates[0])
        assertEquals("https://dweb.link/ipfs/$cid", candidates[1])
        assertEquals("https://ipfs.io/ipfs/$cid", candidates[2])
    }

    @Test
    fun toHttpUrlHandlesMixedCaseAndRejectsMalformedInput() {
        val cid = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
        assertEquals(
            "https://dweb.link/ipfs/$cid/image.png?download=1#preview",
            IpfsGatewayResolver.toHttpUrl("IpFs://$cid/image.png?download=1#preview"),
        )
        assertEquals("ipfs://", IpfsGatewayResolver.toHttpUrl("ipfs://"))
        assertEquals("ipfs://$cid/../status", IpfsGatewayResolver.toHttpUrl("ipfs://$cid/../status"))
        assertEquals(
            "ipfs://$cid",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid", "ftp://gateway.example"),
        )
    }

    @Test
    fun normalizeGatewayUrlAcceptsOriginAndIpfsEndpoint() {
        assertEquals(
            "https://originless.gupt.app",
            IpfsGatewayResolver.normalizeGatewayUrl(" https://originless.gupt.app/ipfs/ "),
        )
        assertEquals(
            "http://127.0.0.1:3232",
            IpfsGatewayResolver.normalizeGatewayUrl("http://127.0.0.1:3232/"),
        )
        assertEquals(null, IpfsGatewayResolver.normalizeGatewayUrl("originless.gupt.app"))
    }
}
