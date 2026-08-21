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
            "https://ipfs.io/ipfs/$cid",
            IpfsGatewayResolver.toHttpUrl("ipfs://$cid", IpfsGatewayResolver.SECONDARY_GATEWAY),
        )
    }

    @Test
    fun getAllCandidateUrlsIncludesBothGateways() {
        val cid = "bafybeigdyrzt5sfp7udm7hu76uh7y26nf3efuylqabf3oclgtqy55fbzdi"
        val candidates = IpfsGatewayResolver.getAllCandidateUrls("ipfs://$cid")
        assertEquals(2, candidates.size)
        assertEquals("https://dweb.link/ipfs/$cid", candidates[0])
        assertEquals("https://ipfs.io/ipfs/$cid", candidates[1])
    }
}
