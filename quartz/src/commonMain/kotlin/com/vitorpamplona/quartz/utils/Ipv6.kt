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
 * Pure-Kotlin IPv6 literal parsing, RFC 5952 canonical formatting and address
 * classification. No `java.net`, so it works on every KMP target.
 *
 * Exists because relay identity is a *string*: [com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl]
 * is the key of the connection pool, the relay-list sets, the NIP-11 cache and every
 * per-relay stat map. RFC 4291 lets one address be written many ways
 * (`[201:0d0e:9ba5:8bbc:0000:0000:0000:0001]` and `[201:d0e:9ba5:8bbc::1]` are the same
 * host), and without folding them the app treats one relay as two — two sockets, two REQ
 * sets, two rows in the UI. The canonical form here matches what OkHttp renders, so the
 * key the app stores is the host it actually dials.
 */
object Ipv6 {
    /** Longest legal literal is 45 chars (`::ffff:` + dotted quad is shorter than 8 full groups). */
    private const val MAX_LITERAL = 45

    /**
     * Parses a bracket-less, zone-less IPv6 literal into its 16 bytes, or null when [address]
     * is not a valid literal. Accepts `::` compression and a trailing dotted quad
     * (`::ffff:192.168.1.1`).
     */
    fun parse(address: String): ByteArray? {
        val len = address.length
        if (len < 2 || len > MAX_LITERAL) return null

        val out = ByteArray(16)
        // Bytes written so far, counting from the left. When a `::` is present the bytes after
        // it are written contiguously here and shifted to the right end at the very end.
        var fill = 0
        var gapAt = -1
        var i = 0

        if (address[0] == ':') {
            if (address[1] != ':') return null
            gapAt = 0
            i = 2
            if (i == len) return out
        }

        while (true) {
            val groupStart = i
            var value = 0
            var digits = 0
            while (i < len) {
                val digit = hexDigit(address[i])
                if (digit < 0) break
                if (digits == 4) return null
                value = (value shl 4) or digit
                digits++
                i++
            }

            if (i < len && address[i] == '.') {
                // Trailing dotted quad: occupies the last four bytes, so nothing may follow it.
                if (fill > 12) return null
                if (!parseIpv4Into(address, groupStart, len, out, fill)) return null
                fill += 4
                i = len
                break
            }

            if (digits == 0) return null
            if (fill + 2 > 16) return null
            out[fill++] = (value ushr 8).toByte()
            out[fill++] = value.toByte()

            if (i == len) break
            if (address[i] != ':') return null
            i++
            if (i == len) return null // a single trailing ':' is not a valid literal
            if (address[i] == ':') {
                if (gapAt >= 0) return null // only one `::` allowed
                gapAt = fill
                i++
                if (i == len) break
            }
        }

        if (gapAt < 0) {
            if (fill != 16) return null
        } else {
            // `::` must stand for at least one omitted group.
            if (fill == 16) return null
            val tail = fill - gapAt
            for (k in tail - 1 downTo 0) {
                out[16 - tail + k] = out[gapAt + k]
                out[gapAt + k] = 0
            }
        }
        return out
    }

    /**
     * RFC 5952 text form: lowercase hex, no leading zeros, and the longest run of two or more
     * zero groups replaced by `::` (leftmost run wins a tie). IPv4-mapped addresses keep their
     * dotted tail. This is byte-for-byte what OkHttp prints for the same address.
     */
    fun format(bytes: ByteArray): String {
        require(bytes.size == 16) { "An IPv6 address is 16 bytes, got ${bytes.size}" }

        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < 16) {
            if (bytes[i] == ZERO && bytes[i + 1] == ZERO) {
                val runStart = i
                var j = i
                while (j < 16 && bytes[j] == ZERO && bytes[j + 1] == ZERO) j += 2
                if (j - runStart > bestLen) {
                    bestLen = j - runStart
                    bestStart = runStart
                }
                i = j
            } else {
                i += 2
            }
        }
        // A single zero group is written as `0`, never as `::`.
        if (bestLen < 4) {
            bestStart = -1
            bestLen = 0
        }

        val out = StringBuilder(39)
        // ::ffff:a.b.c.d — IPv4-mapped addresses read as IPv4 everywhere else, so keep them that way.
        if (bestStart == 0 && bestLen == 10 && bytes[10] == ALL_ONES && bytes[11] == ALL_ONES) {
            out.append("::ffff:")
            appendIpv4(out, bytes, 12)
            return out.toString()
        }

        i = 0
        while (i < 16) {
            if (i == bestStart) {
                out.append(':')
                i += bestLen
                if (i == 16) out.append(':')
            } else {
                if (i > 0) out.append(':')
                out.append(group(bytes, i).toString(16))
                i += 2
            }
        }
        return out.toString()
    }

    /**
     * Canonicalizes a bracket-less literal, preserving any `%zone` suffix verbatim (in URLs the
     * zone arrives percent-encoded, e.g. `fe80::1%25wlan0`). Returns null when [address] is not
     * a valid literal.
     */
    fun canonicalizeOrNull(address: String): String? {
        val zoneAt = address.indexOf('%')
        if (zoneAt < 0) return parse(address)?.let(::format)
        val bytes = parse(address.substring(0, zoneAt)) ?: return null
        return format(bytes) + address.substring(zoneAt)
    }

    /** True when [address] is a valid bracket-less literal that names more than one group. */
    fun isLiteral(address: String): Boolean = address.indexOf(':') >= 0 && parse(address) != null

    /** `::1` — the IPv6 loopback, twin of 127.0.0.1. */
    fun isLoopback(bytes: ByteArray): Boolean {
        for (i in 0 until 15) if (bytes[i] != ZERO) return false
        return bytes[15] == ONE
    }

    /** `fe80::/10` — link-local, only meaningful on the interface it came from. */
    fun isLinkLocal(bytes: ByteArray): Boolean = bytes[0] == FE.toByte() && (bytes[1].toInt() and 0xC0) == 0x80

    /** `fc00::/7` — unique local addresses, the IPv6 twin of 192.168.0.0/16. */
    fun isUniqueLocal(bytes: ByteArray): Boolean = (bytes[0].toInt() and 0xFE) == 0xFC

    /**
     * `0200::/7` — the range Yggdrasil derives node addresses (`0200::/8`) and subnets
     * (`0300::/8`) from. Formally deprecated NSAP space, so nothing else routes here: an
     * address in this range is reachable only through a running mesh interface, is already
     * end-to-end encrypted by the overlay, and can never hold a CA-issued certificate.
     */
    fun isOverlayMesh(bytes: ByteArray): Boolean = (bytes[0].toInt() and 0xFE) == 0x02

    private fun group(
        bytes: ByteArray,
        at: Int,
    ) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun appendIpv4(
        out: StringBuilder,
        bytes: ByteArray,
        from: Int,
    ) {
        for (k in 0 until 4) {
            if (k > 0) out.append('.')
            out.append(bytes[from + k].toInt() and 0xFF)
        }
    }

    /**
     * Parses `a.b.c.d` in `[from, to)` into four bytes at [at]. Leading zeros are rejected —
     * they invite the octal reading that makes `010.1.1.1` ambiguous across resolvers.
     */
    private fun parseIpv4Into(
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

    private fun hexDigit(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }

    private const val FE = 0xFE
    private const val ZERO = 0.toByte()
    private const val ONE = 1.toByte()
    private const val ALL_ONES = 0xFF.toByte()
}
