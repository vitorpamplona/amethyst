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

/**
 * Whether an IP address is one a fetch nobody vouched for may reach: not this device, not the
 * user's network, not a range that isn't routed on the internet. The rules are the JVM's
 * (`InetAddress.isAnyLocalAddress`, `isLoopbackAddress`, `isLinkLocalAddress`, `isSiteLocalAddress`,
 * `isMulticastAddress`) plus the reserved ranges Java doesn't classify, on raw bytes, so a platform
 * with no `InetAddress` (iOS, which resolves with `getaddrinfo`) refuses exactly what the JVM does.
 */
object NappletPublicAddress {
    /** [address] is 4 (IPv4) or 16 (IPv6) bytes, network order. Anything else is refused. */
    fun isPublic(address: ByteArray): Boolean {
        // An IPv4-mapped IPv6 address is the IPv4 one: the JVM hands those back as IPv4 too.
        if (address.size == 16 && isV4Mapped(address)) return isPublic(address.copyOfRange(12, 16))
        return when (address.size) {
            4 -> isPublicV4(address.map { it.toInt() and 0xff })
            16 -> isPublicV6(address.map { it.toInt() and 0xff })
            else -> false
        }
    }

    private fun isV4Mapped(b: ByteArray): Boolean = (0 until 10).all { b[it] == 0.toByte() } && b[10] == 0xff.toByte() && b[11] == 0xff.toByte()

    private fun isPublicV4(b: List<Int>): Boolean {
        val (first, second, third) = b
        // Java's any-local (0.0.0.0) and multicast (224/4) fall in these.
        if (first == 0 || first >= 224) return false
        if (first == 127) return false // loopback
        if (first == 169 && second == 254) return false // link-local
        if (first == 10 || (first == 172 && second in 16..31) || (first == 192 && second == 168)) return false // site-local
        // Shared address space (100.64/10) and reserved/non-routed ranges Java does not classify.
        if (first == 100 && second in 64..127) return false
        if (first == 192 && second == 0) return false
        if (first == 198 && second in 18..19) return false
        if (first == 198 && second == 51 && third == 100) return false
        if (first == 203 && second == 0 && third == 113) return false
        return true
    }

    private fun isPublicV6(b: List<Int>): Boolean {
        if (b.all { it == 0 }) return false // any-local
        if ((0 until 15).all { b[it] == 0 } && b[15] == 1) return false // loopback
        if (b[0] == 0xfe && (b[1] and 0xc0) == 0x80) return false // link-local fe80::/10
        if (b[0] == 0xfe && (b[1] and 0xc0) == 0xc0) return false // site-local fec0::/10
        if (b[0] == 0xff) return false // multicast
        if (b[0] and 0xfe == 0xfc) return false // fc00::/7 unique-local
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false // 2001:db8::/32 documentation range
        return true
    }
}
