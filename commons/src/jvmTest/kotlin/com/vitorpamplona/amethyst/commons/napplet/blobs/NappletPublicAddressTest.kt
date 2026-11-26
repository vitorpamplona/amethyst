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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/** The byte-level check iOS runs refuses exactly what the JVM's InetAddress check does. */
class NappletPublicAddressTest {
    @Test
    fun bothChecksAgree() {
        val addresses =
            listOf(
                "0.0.0.0",
                "127.0.0.1",
                "127.8.9.10",
                "10.1.2.3",
                "172.15.0.1",
                "172.16.0.1",
                "172.31.255.255",
                "172.32.0.1",
                "192.168.0.1",
                "169.254.1.1",
                "100.63.0.1",
                "100.64.0.1",
                "100.127.255.255",
                "100.128.0.1",
                "192.0.0.1",
                "192.0.2.1",
                "198.17.0.1",
                "198.18.0.1",
                "198.19.0.1",
                "198.20.0.1",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "239.1.1.1",
                "240.0.0.1",
                "255.255.255.255",
                "1.1.1.1",
                "8.8.8.8",
                "93.184.216.34",
                "::",
                "::1",
                "fe80::1",
                "febf::1",
                "fec0::1",
                "ff02::1",
                "fc00::1",
                "fd12::1",
                "2001:db8::1",
                "2001:db9::1",
                "2606:4700:4700::1111",
                "::ffff:127.0.0.1",
                "::ffff:8.8.8.8",
                "64:ff9b::1",
            )
        addresses.forEach { text ->
            val address = InetAddress.getByName(text)
            assertEquals(NappletInetAddresses.isPublic(address), NappletPublicAddress.isPublic(address.address), text)
        }
    }
}
