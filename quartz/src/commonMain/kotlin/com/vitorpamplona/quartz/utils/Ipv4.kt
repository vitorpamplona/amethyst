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
package com.vitorpamplona.quartz.utils

/**
 * Pure-Kotlin IPv4 literal parsing and range classification, the companion to [Ipv6].
 *
 * Exists because asking "is this host private?" with `url.contains("192.168.")` is wrong in
 * both directions: it misses `10.0.0.5` and `172.16.3.4` (so a LAN relay gets `wss://` and is
 * dialed through Tor) and it matches `192.168.evil.com`, a perfectly registrable domain (so a
 * hostile relay url can exempt itself from Tor). Parsing the host and testing the range is the
 * only form of the question that has a right answer.
 */
object Ipv4 {
    /** Parses a dotted quad in `[from, to)`, or null when the region is not one. */
    fun parse(
        text: String,
        from: Int,
        to: Int,
    ): ByteArray? {
        // Cheapest possible rejection of a DNS host: a literal always starts with a digit.
        if (from >= to || text[from] !in '0'..'9') return null
        val out = ByteArray(4)
        return if (parseInto(text, from, to, out, 0)) out else null
    }

    /**
     * Parses `a.b.c.d` in `[from, to)` into four bytes at [at]. Leading zeros are rejected —
     * they invite the octal reading that makes `010.1.1.1` ambiguous across resolvers.
     */
    fun parseInto(
        text: String,
        from: Int,
        to: Int,
        out: ByteArray,
        at: Int,
    ): Boolean {
        var i = from
        for (octet in 0 until 4) {
            if (octet > 0) {
                if (i >= to || text[i] != '.') return false
                i++
            }
            var value = 0
            var digits = 0
            while (i < to && text[i] in '0'..'9') {
                if (digits == 3) return false
                if (digits == 1 && value == 0) return false // leading zero
                value = value * 10 + (text[i] - '0')
                digits++
                i++
            }
            if (digits == 0 || value > 255) return false
            out[at + octet] = value.toByte()
        }
        return i == to
    }

    /** `127.0.0.0/8` — the whole loopback block, not just 127.0.0.1. */
    fun isLoopback(bytes: ByteArray): Boolean = octet(bytes, 0) == 127

    /** RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`. */
    fun isPrivate(bytes: ByteArray): Boolean {
        val first = octet(bytes, 0)
        val second = octet(bytes, 1)
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    /** `169.254.0.0/16` — link-local / APIPA, reachable only on the local segment. */
    fun isLinkLocal(bytes: ByteArray): Boolean = octet(bytes, 0) == 169 && octet(bytes, 1) == 254

    /** `0.0.0.0/8` — "this network"; never a routable relay. */
    fun isUnspecified(bytes: ByteArray): Boolean = octet(bytes, 0) == 0

    private fun octet(
        bytes: ByteArray,
        at: Int,
    ) = bytes[at].toInt() and 0xFF
}
