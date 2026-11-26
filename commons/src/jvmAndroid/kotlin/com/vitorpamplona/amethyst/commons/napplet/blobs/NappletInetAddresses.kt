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

/** [NappletPublicAddress] for the JVM's resolved addresses: the check the OkHttp clients run on every hop. */
object NappletInetAddresses {
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            // Shared address space (100.64/10) and reserved/non-routed ranges Java does not classify.
            if (first == 0 || first >= 224) return false
            if (first == 100 && second in 64..127) return false
            if (first == 192 && second == 0) return false
            if (first == 198 && second in 18..19) return false
            if (first == 198 && second == 51 && (bytes[2].toInt() and 0xff) == 100) return false
            if (first == 203 && second == 0 && (bytes[2].toInt() and 0xff) == 113) return false
        } else if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            if (first and 0xfe == 0xfc) return false // fc00::/7 unique-local
            if (
                first == 0x20 &&
                (bytes[1].toInt() and 0xff) == 0x01 &&
                (bytes[2].toInt() and 0xff) == 0x0d &&
                (bytes[3].toInt() and 0xff) == 0xb8
            ) {
                return false // 2001:db8::/32 documentation range
            }
        }
        return true
    }
}
