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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

/**
 * Thrown when a mint URL is refused before any request is issued. The message is
 * user-facing: it is surfaced verbatim in the "could not redeem" dialog.
 */
class MintUrlException(
    message: String,
) : RuntimeException(message)

/**
 * Gatekeeper for the `mintUrl` a [MintHttpClient] is about to talk to.
 *
 * A Cashu token carries its own mint URL (`token.mint`) and that URL is attacker
 * controlled: anybody can paste or post a token. Redeeming it makes the device
 * issue HTTP requests to whatever it says, which is an SSRF primitive against
 * anything the phone can reach (`http://127.0.0.1:<port>`, the home router at
 * `192.168.1.1`, `169.254.169.254` cloud metadata) plus an IP-address discloser
 * for whoever controls the URL.
 *
 * The rule:
 * - `https://` to a public host is allowed.
 * - `http://` is allowed **only** to a `.onion` host. Amethyst supports Tor and
 *   onion mints are legitimate; a blanket "https only" would break them. Onion
 *   names are self-authenticating and never resolve to a local address, so the
 *   private-range checks below don't apply to them.
 * - Any other scheme (`file:`, `ftp:`, `data:`, …) is refused.
 * - Loopback, RFC1918 private, link-local, CGNAT, unique-local, unspecified,
 *   multicast and broadcast addresses are refused, including via the usual
 *   spellings: IPv4-mapped/compatible IPv6 (`::ffff:127.0.0.1`), `inet_aton`
 *   decimal/octal/hex forms (`http://2130706433`, `http://0177.0.0.1`),
 *   `http://user@127.0.0.1`, and a trailing-dot hostname (`localhost.`).
 *
 * [userConfigured] switches the host checks off (the scheme check still runs).
 * Pass it only for a mint the user deliberately added — a mint already in their
 * NIP-60 wallet, or one they typed into the CLI. A self-hosted mint on the LAN
 * is a legitimate setup; the threat modelled here is a *pasted, untrusted* token
 * pointing at an internal address.
 *
 * NOTE: this is a pre-resolution check on the literal host, so it does not stop
 * DNS rebinding (a public name that resolves to 127.0.0.1). That is deliberately
 * out of scope — closing it needs a resolve-then-pin socket factory, not a URL
 * check.
 */
object CashuMintUrlValidator {
    /**
     * Validates [mintUrl] and returns the base URL to build request paths from
     * (trailing slashes stripped, matching the previous [MintHttpClient] behaviour).
     *
     * @throws MintUrlException with a user-facing reason when the URL is refused.
     */
    fun validatedBaseUrl(
        mintUrl: String,
        userConfigured: Boolean = false,
    ): String {
        val url = mintUrl.trim()
        if (url.isEmpty()) throw MintUrlException("The token does not name a mint.")

        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) throw MintUrlException("The mint address \"$url\" is not an https address.")

        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "https" && scheme != "http") {
            throw MintUrlException("The mint address uses the unsupported \"$scheme:\" scheme. Only https is allowed.")
        }

        val host = hostOf(url.substring(schemeEnd + 3))
        if (host.isEmpty()) throw MintUrlException("The mint address \"$url\" has no host.")

        val isOnion = host == "onion" || host.endsWith(".onion")

        if (scheme == "http" && !isOnion && !userConfigured) {
            throw MintUrlException("The mint address \"$url\" is not encrypted (http). Only https, or an .onion address over Tor, is allowed.")
        }

        if (!userConfigured && !isOnion && isPrivateHost(host)) {
            throw MintUrlException("The mint address \"$url\" points at a private or local address. Amethyst will not contact it.")
        }

        return url.trimEnd('/')
    }

    /**
     * Extracts the host from the part of the URL that follows `scheme://`:
     * drops the path/query/fragment, drops any `user:pass@` prefix (so
     * `http://mint.example.com@127.0.0.1/` is correctly read as `127.0.0.1`),
     * drops the port, unwraps a `[…]` IPv6 literal, and normalises case plus
     * the FQDN trailing dot.
     */
    private fun hostOf(afterScheme: String): String {
        var rest = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        // userinfo goes up to the LAST '@' — an attacker can embed one in the password.
        val at = rest.lastIndexOf('@')
        if (at >= 0) rest = rest.substring(at + 1)

        val host =
            if (rest.startsWith("[")) {
                rest.substringAfter('[').substringBefore(']')
            } else {
                rest.substringBefore(':')
            }

        return host.lowercase().trimEnd('.')
    }

    /**
     * True when [host] is a literal address inside a range we must never reach
     * from an untrusted token. Names that are not IP literals return false: the
     * check is pre-resolution (see the DNS-rebinding note on the object), except
     * for `localhost`, which is a name but always local.
     */
    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true

        if (host.contains(':')) {
            val v6 = parseIpv6(host) ?: return false
            return isPrivateIpv6(v6)
        }

        val v4 = parseIpv4(host) ?: return false
        return isPrivateIpv4(v4)
    }

    // ------------------------------------------------------------------
    // IPv4
    // ------------------------------------------------------------------

    /**
     * `inet_aton`-style parse: accepts 1..4 parts, each decimal, octal (`0…`) or
     * hex (`0x…`), because that is what a C resolver — and therefore a lot of the
     * stack below us — will happily accept. Returns the address as an unsigned
     * 32-bit value in a [Long], or null when [host] is not an IPv4 literal at all.
     */
    fun parseIpv4(host: String): Long? {
        if (host.isEmpty()) return null
        val parts = host.split('.')
        if (parts.size > 4) return null

        val values = ArrayList<Long>(parts.size)
        for (part in parts) {
            values.add(parseIpv4Part(part) ?: return null)
        }

        // The last part absorbs every byte the earlier parts didn't name:
        // "127.1" is 127.0.0.1, "2130706433" is 127.0.0.1.
        val n = values.size
        var result = 0L
        for (i in 0 until n - 1) {
            val v = values[i]
            if (v > 255L) return null
            result = result or (v shl (8 * (3 - i)))
        }
        val tailMax = (1L shl (8 * (4 - (n - 1)))) - 1L
        val tail = values[n - 1]
        if (tail > tailMax) return null
        return result or tail
    }

    private fun parseIpv4Part(part: String): Long? {
        if (part.isEmpty()) return null
        return when {
            part.startsWith("0x") || part.startsWith("0X") -> {
                val digits = part.substring(2)
                if (digits.isEmpty() || !digits.all { it.isHexDigit() }) return null
                digits.toLongOrNull(16)
            }
            part.length > 1 && part[0] == '0' -> {
                val digits = part.substring(1)
                if (!digits.all { it in '0'..'7' }) return null
                digits.toLongOrNull(8)
            }
            else -> {
                if (!part.all { it in '0'..'9' }) return null
                part.toLongOrNull()
            }
        }?.takeIf { it >= 0 && it <= 0xFFFFFFFFL }
    }

    private fun isPrivateIpv4(addr: Long): Boolean {
        val a = ((addr shr 24) and 0xFF).toInt()
        val b = ((addr shr 16) and 0xFF).toInt()
        return when {
            a == 0 -> true // 0.0.0.0/8 — "this network", includes 0.0.0.0
            a == 10 -> true // RFC1918
            a == 127 -> true // loopback
            a == 169 && b == 254 -> true // link-local
            a == 172 && b in 16..31 -> true // RFC1918
            a == 192 && b == 168 -> true // RFC1918
            a == 100 && b in 64..127 -> true // CGNAT, RFC6598
            a == 192 && b == 0 -> true // 192.0.0.0/24 IETF protocol assignments
            a in 224..255 -> true // multicast + reserved + 255.255.255.255
            else -> false
        }
    }

    // ------------------------------------------------------------------
    // IPv6
    // ------------------------------------------------------------------

    /** Parses an IPv6 literal (with optional `::` run and optional trailing IPv4) into 16 bytes. */
    fun parseIpv6(literal: String): ByteArray? {
        // A zone id (fe80::1%wlan0) doesn't change which range we're in.
        val text = literal.substringBefore('%')
        if (text.isEmpty()) return null

        val out = ByteArray(16)
        val doubleColon = text.indexOf("::")
        if (text.indexOf("::", doubleColon + 1) >= 0) return null // more than one "::"

        val headText = if (doubleColon >= 0) text.substring(0, doubleColon) else text
        val tailText = if (doubleColon >= 0) text.substring(doubleColon + 2) else ""

        val head = splitGroups(headText) ?: return null
        val tail = splitGroups(tailText) ?: return null

        // A trailing dotted-quad ("::ffff:127.0.0.1") counts as the last two groups.
        val headBytes = groupsToBytes(head) ?: return null
        val tailBytes = groupsToBytes(tail) ?: return null

        if (doubleColon < 0) {
            if (headBytes.size != 16) return null
            return headBytes
        }
        if (headBytes.size + tailBytes.size > 14) return null // "::" must stand for >= 1 group

        headBytes.copyInto(out, 0)
        tailBytes.copyInto(out, 16 - tailBytes.size)
        return out
    }

    private fun splitGroups(text: String): List<String>? {
        if (text.isEmpty()) return emptyList()
        val groups = text.split(':')
        if (groups.any { it.isEmpty() }) return null
        return groups
    }

    private fun groupsToBytes(groups: List<String>): ByteArray? {
        val bytes = ArrayList<Byte>(16)
        for ((index, group) in groups.withIndex()) {
            if (group.contains('.')) {
                // Only legal as the final element, and only in strict dotted-quad form.
                if (index != groups.size - 1) return null
                val quad = group.split('.')
                if (quad.size != 4) return null
                for (q in quad) {
                    if (q.isEmpty() || q.length > 3 || !q.all { it in '0'..'9' }) return null
                    val v = q.toInt()
                    if (v > 255) return null
                    bytes.add(v.toByte())
                }
            } else {
                if (group.length > 4 || !group.all { it.isHexDigit() }) return null
                val v = group.toInt(16)
                bytes.add(((v shr 8) and 0xFF).toByte())
                bytes.add((v and 0xFF).toByte())
            }
        }
        if (bytes.size > 16) return null
        return bytes.toByteArray()
    }

    private fun isPrivateIpv6(addr: ByteArray): Boolean {
        // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) carry an
        // IPv4 address; judge them by the IPv4 rules or ::ffff:127.0.0.1 walks in.
        val first10Zero = (0 until 10).all { addr[it].toInt() == 0 }
        if (first10Zero) {
            val ffff = (addr[10].toInt() and 0xFF) == 0xFF && (addr[11].toInt() and 0xFF) == 0xFF
            val compat = addr[10].toInt() == 0 && addr[11].toInt() == 0
            if (ffff || compat) {
                val v4 =
                    ((addr[12].toLong() and 0xFF) shl 24) or
                        ((addr[13].toLong() and 0xFF) shl 16) or
                        ((addr[14].toLong() and 0xFF) shl 8) or
                        (addr[15].toLong() and 0xFF)
                // ::0.0.0.0 / ::1 fall out of the IPv4 rules too (0/8 is blocked).
                if (compat && v4 <= 1L) return true
                return isPrivateIpv4(v4)
            }
        }

        val b0 = addr[0].toInt() and 0xFF
        val b1 = addr[1].toInt() and 0xFF

        // :: (unspecified) and ::1 (loopback)
        if (addr.all { it.toInt() == 0 }) return true
        if ((0..14).all { addr[it].toInt() == 0 } && addr[15].toInt() == 1) return true

        if (b0 == 0xFF) return true // ff00::/8 multicast
        if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true // fe80::/10 link-local
        if ((b0 and 0xFE) == 0xFC) return true // fc00::/7 unique-local

        return false
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
