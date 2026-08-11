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
package com.vitorpamplona.amethyst.napplet.gateways

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NappletResourceFetcherPolicyTest {
    @Test
    fun blocksPrivateSpecialAndLocalAddresses() {
        val blocked =
            listOf(
                "0.0.0.0",
                "10.0.0.1",
                "100.64.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "192.0.0.1",
                "192.0.2.1",
                "192.168.1.1",
                "198.18.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "::1",
                "2001:db8::1",
                "fc00::1",
                "fe80::1",
            )

        blocked.forEach {
            assertFalse(it, NappletResourceFetcher.isPublicAddress(InetAddress.getByName(it)))
        }
    }

    @Test
    fun permitsPublicAddresses() {
        assertTrue(NappletResourceFetcher.isPublicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(NappletResourceFetcher.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test
    fun acceptsOnlyCredentialFreeHttpsUrls() {
        assertTrue(NappletResourceFetcher.isSafeHttpsResourceUrl("https://example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("http://example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("https://user:secret@example.com/a"))
        assertFalse(NappletResourceFetcher.isSafeHttpsResourceUrl("file:///etc/passwd"))
    }
}
