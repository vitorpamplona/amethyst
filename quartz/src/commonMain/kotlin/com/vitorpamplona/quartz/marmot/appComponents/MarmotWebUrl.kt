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
package com.vitorpamplona.quartz.marmot.appComponents

/**
 * The WHATWG URL normalizer Marmot group state needs.
 *
 * This exists because normalization is part of the wire format, not a
 * convenience: `marmot.group.avatar-url.v1` stores the serialized form, and a
 * decoder "MUST reject state whose stored URL bytes differ from the
 * serializer's output". `marmot.group.encrypted-media.v2` says the same about
 * its blob-store base URLs, and names this same normalization. So this has to
 * agree with every other implementation byte for byte — too lax and we accept
 * state a peer rejects, too strict and we reject a group somebody else made.
 *
 * It is not a general URL library. It handles exactly the shapes the components
 * allow — an `https` (or, where the component permits it, `http`) URL with a
 * host, no userinfo and no fragment — and refuses everything else rather than
 * guessing.
 *
 * **Known limit: no IDNA.** A host with non-ASCII characters is refused instead
 * of punycoded. That costs nothing on the decode side, where it matters: a
 * conformant producer already stored the punycoded form (which is ASCII and
 * passes through untouched), and a stored raw-Unicode host is non-normalized
 * and must be rejected anyway. It only stops us from *accepting* a
 * Unicode-typed host from our own user, who can paste the punycode form.
 */
object MarmotWebUrl {
    const val MAX_BYTES = 2048

    private const val HTTPS_DEFAULT_PORT = "443"
    private const val HTTP_DEFAULT_PORT = "80"

    /**
     * Parse [raw] and return its WHATWG serialization.
     *
     * @throws IllegalArgumentException when the URL is not a valid group-avatar
     *   URL, or when normalizing it would need something this does not do.
     */
    fun normalize(
        raw: String,
        /**
         * Whether plain `http` is acceptable. Off by default because the
         * avatar component is https-only; the media policy permits both, and
         * that is a per-component rule rather than a global one.
         */
        allowHttp: Boolean = false,
        /** What to call this URL in an error, e.g. "avatar URL". */
        label: String = "URL",
    ): String {
        require(raw.isNotEmpty()) { "$label must not be empty" }
        require(raw.encodeToByteArray().size <= MAX_BYTES) { "$label exceeds $MAX_BYTES bytes" }

        val schemeEnd = raw.indexOf("://")
        require(schemeEnd > 0) { "$label must be an absolute URL" }
        val scheme = raw.substring(0, schemeEnd).lowercase()
        require(scheme == "https" || (allowHttp && scheme == "http")) {
            if (allowHttp) "$label scheme must be http or https" else "$label scheme must be https"
        }
        val defaultPort = if (scheme == "http") HTTP_DEFAULT_PORT else HTTPS_DEFAULT_PORT

        var rest = raw.substring(schemeEnd + 3)
        require(!rest.contains('#')) { "$label must not include a fragment" }

        // The authority runs to the first "/" or "?" — everything after is path
        // and query.
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        rest = rest.substring(authorityEnd)
        require(!authority.contains('@')) { "$label must not include credentials" }
        require(authority.isNotEmpty()) { "$label must include a host" }

        val (host, port) = splitHostPort(authority)
        require(host.isNotEmpty()) { "$label must include a host" }
        require(host.all { it.code < 0x80 }) {
            "$label host must be ASCII — encode an international host as punycode first"
        }

        val queryStart = rest.indexOf('?')
        val rawPath = if (queryStart < 0) rest else rest.substring(0, queryStart)
        val rawQuery = if (queryStart < 0) null else rest.substring(queryStart + 1)

        val out = StringBuilder(scheme).append("://")
        out.append(host.lowercase())
        if (port != null && port != defaultPort) out.append(':').append(port)
        out.append(normalizePath(rawPath))
        if (rawQuery != null) out.append('?').append(percentEncode(rawQuery, QUERY_KEEP))

        val normalized = out.toString()
        require(normalized.encodeToByteArray().size <= MAX_BYTES) { "$label exceeds $MAX_BYTES bytes" }
        return normalized
    }

    /** True when [normalize] accepts [raw] and returns it unchanged. */
    fun isNormalized(
        raw: String,
        allowHttp: Boolean = false,
    ): Boolean =
        try {
            normalize(raw, allowHttp) == raw
        } catch (_: IllegalArgumentException) {
            false
        }

    /**
     * Whether this client should be willing to FETCH [raw].
     *
     * Deliberately separate from [normalize]: a URL can be perfectly valid group
     * state and still be somewhere we refuse to go. Validity is the group's
     * business and is the same for every member; contact is ours alone, and the
     * spec is explicit that it "MUST NOT affect component or commit validity".
     *
     * The rule is the ordinary SSRF one — no loopback, no link-local, no private
     * range — so a group avatar cannot make a member probe its own network.
     */
    fun isSafeToContact(raw: String): Boolean {
        val host =
            try {
                hostOf(normalize(raw, allowHttp = true))
            } catch (_: IllegalArgumentException) {
                return false
            }
        if (host == "localhost" || host.endsWith(".localhost")) return false
        if (host.startsWith("[")) return !isNonRoutableIpv6(host.trim('[', ']'))
        // A trailing dot is the same host ("example.com." == "example.com"),
        // and would otherwise leave an empty last label below.
        val bare = host.removeSuffix(".")
        val packed = packIpv4(bare)
        if (packed != null) return !isNonRoutableIpv4(packed)
        // Not a name, and not an address shape we could evaluate. Refusing is
        // the only safe answer: something like `0x7f.1` is an address to the
        // resolver and a mystery to us, and "we could not tell" must not mean
        // "go ahead".
        if (looksNumeric(bare)) return false
        return true
    }

    /** True when the last label is numeric, which no registrable name may be. */
    private fun looksNumeric(host: String): Boolean {
        val last = host.substringAfterLast('.')
        if (last.isEmpty()) return false
        if (last.startsWith("0x") || last.startsWith("0X")) return true
        return last.all { it in '0'..'9' }
    }

    /**
     * Pack an IPv4 literal in ANY of the notations a resolver accepts into its
     * 32-bit value, or null when [host] is not one.
     *
     * `inet_aton` — which is what the platform resolver ultimately uses — does
     * not require four parts. `127.1` is 127.0.0.1, so is the bare integer
     * `2130706433`, and `0x7f.0.0.1` is too; a leading zero means octal. An
     * earlier version of this check only recognised four decimal parts, so
     * every one of those forms walked past the loopback guard and was fetched.
     *
     * With N parts the LAST part is not one byte but all the bytes the earlier
     * parts did not cover: `a.b` is a.(24 bits), `a.b.c` is a.b.(16 bits).
     */
    private fun packIpv4(host: String): Long? {
        val parts = host.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        val values = parts.map { parseIpv4Part(it) ?: return null }
        val lastWidth = 8 * (5 - parts.size)
        val last = values.last()
        if (lastWidth < 32 && last >= (1L shl lastWidth)) return null
        var packed = last
        // Every part but the last contributes exactly one byte, most
        // significant first.
        values.dropLast(1).forEachIndexed { i, v ->
            if (v > 255L) return null
            packed = packed or (v shl (8 * (3 - i)))
        }
        return packed
    }

    /** One `inet_aton` part: 0x-hex, leading-zero octal, or decimal. */
    private fun parseIpv4Part(part: String): Long? {
        if (part.isEmpty()) return null
        val value =
            when {
                part.startsWith("0x") || part.startsWith("0X") ->
                    part.substring(2).takeIf { it.isNotEmpty() }?.toLongOrNull(16)
                part.length > 1 && part[0] == '0' -> part.substring(1).toLongOrNull(8)
                else -> part.toLongOrNull(10)
            }
        return value?.takeIf { it in 0..0xFFFFFFFFL }
    }

    private fun hostOf(normalized: String): String {
        val rest = normalized.substringAfter("://")
        val end = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        return splitHostPort(rest.substring(0, end)).first
    }

    /** Takes the packed 32-bit address so every notation is judged the same. */
    private fun isNonRoutableIpv4(packed: Long): Boolean {
        val a = ((packed shr 24) and 0xFF).toInt()
        val b = ((packed shr 16) and 0xFF).toInt()
        return a == 0 ||
            a == 127 ||
            a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) ||
            a >= 224
    }

    /**
     * Decide an IPv6 literal on its BYTES, never on how it was spelled.
     *
     * Matching text was the bug: `::1` is one of many spellings of loopback,
     * and `0:0:0:0:0:0:0:1` — the same address, fully expanded — matched
     * nothing and read as routable. `::ffff:127.0.0.1` is worse still, because
     * it is IPv4 loopback wearing an IPv6 coat and shares no prefix with any
     * of the strings above. Both made a group avatar URL a way to have every
     * member fetch from their own machine.
     *
     * An address this cannot parse is refused rather than allowed: "we could
     * not tell" must not mean "go ahead", which is the same rule the IPv4 side
     * applies to shapes like `0x7f.1`.
     */
    private fun isNonRoutableIpv6(addr: String): Boolean {
        val bytes = parseIpv6(addr) ?: return true

        // An IPv4-mapped (::ffff:a.b.c.d) or IPv4-compatible (::a.b.c.d)
        // address is really that IPv4 address, so it gets the IPv4 rules.
        val v4Prefix = bytes.take(10).all { it.toInt() == 0 }
        if (v4Prefix) {
            val mapped = bytes[10].toInt() and 0xFF
            val mapped2 = bytes[11].toInt() and 0xFF
            if ((mapped == 0xFF && mapped2 == 0xFF) || (mapped == 0 && mapped2 == 0)) {
                val packed =
                    ((bytes[12].toLong() and 0xFF) shl 24) or
                        ((bytes[13].toLong() and 0xFF) shl 16) or
                        ((bytes[14].toLong() and 0xFF) shl 8) or
                        (bytes[15].toLong() and 0xFF)
                // `::` and `::1` land here too, and both are non-routable under
                // the IPv4 rules (0.0.0.0 and 0.0.0.1 are in 0.0.0.0/8).
                return isNonRoutableIpv4(packed)
            }
        }

        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return when {
            // Unique-local fc00::/7.
            first == 0xFC || first == 0xFD -> true
            // Link-local fe80::/10 — the top two bits of the second byte.
            first == 0xFE && (second and 0xC0) == 0x80 -> true
            // Multicast ff00::/8.
            first == 0xFF -> true
            else -> false
        }
    }

    /**
     * The 16 bytes of an IPv6 literal, or null when it is not one.
     *
     * Handles `::` compression once, a trailing embedded IPv4 dotted quad, and
     * a `%zone` suffix (dropped — a zone never makes an address more routable).
     */
    private fun parseIpv6(addr: String): ByteArray? {
        val text = addr.lowercase().substringBefore('%')
        if (text.isEmpty()) return null

        val doubleColon = text.indexOf("::")
        if (doubleColon != text.lastIndexOf("::")) return null

        val headText = if (doubleColon >= 0) text.substring(0, doubleColon) else text
        val tailText = if (doubleColon >= 0) text.substring(doubleColon + 2) else ""

        val head = mutableListOf<Int>()
        val tail = mutableListOf<Int>()

        // The embedded-IPv4 form is only legal as the last element, and it
        // contributes two groups rather than one.
        fun push(
            into: MutableList<Int>,
            piece: String,
            isLast: Boolean,
        ): Boolean {
            if (piece.contains('.')) {
                if (!isLast) return false
                val quad = packIpv4(piece) ?: return false
                if (piece.count { it == '.' } != 3) return false
                into.add(((quad shr 16) and 0xFFFF).toInt())
                into.add((quad and 0xFFFF).toInt())
                return true
            }
            if (piece.isEmpty() || piece.length > 4) return false
            val value = piece.toIntOrNull(16) ?: return false
            into.add(value)
            return true
        }

        if (headText.isNotEmpty()) {
            val pieces = headText.split(':')
            pieces.forEachIndexed { i, piece ->
                if (!push(head, piece, i == pieces.lastIndex && doubleColon < 0)) return null
            }
        }
        if (tailText.isNotEmpty()) {
            val pieces = tailText.split(':')
            pieces.forEachIndexed { i, piece ->
                if (!push(tail, piece, i == pieces.lastIndex)) return null
            }
        }

        val groups =
            when {
                doubleColon < 0 -> if (head.size == 8) head else return null
                head.size + tail.size > 7 -> return null
                else -> head + List(8 - head.size - tail.size) { 0 } + tail
            }
        if (groups.size != 8) return null

        val bytes = ByteArray(16)
        groups.forEachIndexed { i, group ->
            bytes[i * 2] = ((group shr 8) and 0xFF).toByte()
            bytes[i * 2 + 1] = (group and 0xFF).toByte()
        }
        return bytes
    }

    /** Splits `host:port`, keeping an IPv6 literal's brackets on the host. */
    private fun splitHostPort(authority: String): Pair<String, String?> {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            require(close > 0) { "avatar URL has an unterminated IPv6 host" }
            val host = authority.substring(0, close + 1)
            val tail = authority.substring(close + 1)
            if (tail.isEmpty()) return host to null
            require(tail.startsWith(":")) { "avatar URL has a malformed IPv6 authority" }
            return host to validPort(tail.substring(1))
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        return authority.substring(0, colon) to validPort(authority.substring(colon + 1))
    }

    private fun validPort(port: String): String {
        require(port.isNotEmpty() && port.all { it.isDigit() }) { "avatar URL has a malformed port" }
        val value = port.toIntOrNull()
        require(value != null && value in 1..65535) { "avatar URL port is out of range" }
        // WHATWG serializes the port as a decimal number, so "0443" is "443".
        return value.toString()
    }

    /**
     * Resolve dot segments and percent-encode what the path set demands. An
     * empty path serializes as `/`.
     */
    private fun normalizePath(rawPath: String): String {
        if (rawPath.isEmpty()) return "/"
        // WHATWG keeps the path as a segment LIST and serializes it as "/" +
        // segments joined by "/". A trailing slash is therefore a final EMPTY
        // segment, not a suffix — which is also why "/a/." ends in a slash: the
        // dot segment is dropped and an empty one takes its place.
        val segments = rawPath.removePrefix("/").split('/')
        val out = ArrayList<String>()
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.size - 1
            when {
                isDoubleDot(segment) -> {
                    if (out.isNotEmpty()) out.removeAt(out.size - 1)
                    if (isLast) out.add("")
                }

                isSingleDot(segment) -> if (isLast) out.add("")

                else -> out.add(percentEncode(segment, PATH_KEEP))
            }
        }
        return "/" + out.joinToString("/")
    }

    /** WHATWG counts `%2e` as a dot for segment resolution, case-insensitively. */
    private fun isSingleDot(segment: String) = segment == "." || segment.equals("%2e", ignoreCase = true)

    private fun isDoubleDot(segment: String): Boolean {
        val s = segment.lowercase()
        return s == ".." || s == ".%2e" || s == "%2e." || s == "%2e%2e"
    }

    /**
     * Percent-encode every byte outside [keep], leaving an existing `%XX`
     * sequence exactly as it was found.
     *
     * Preserving is not laziness: the reference serializer does not re-case or
     * decode what is already encoded (`%7e` stays `%7e`, `%7E` stays `%7E`), and
     * "canonicalising" either way would make our bytes differ from a peer's for
     * the same URL.
     */
    private fun percentEncode(
        value: String,
        keep: (Char) -> Boolean,
    ): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length && value[i + 1].isHex() && value[i + 2].isHex()) {
                out.append(value, i, i + 3)
                i += 3
                continue
            }
            if (keep(c)) {
                out.append(c)
            } else {
                for (b in c.toString().encodeToByteArray()) {
                    out.append('%').append(HEX[(b.toInt() shr 4) and 0xf]).append(HEX[b.toInt() and 0xf])
                }
            }
            i++
        }
        return out.toString()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val HEX = "0123456789ABCDEF"

    /**
     * WHATWG "path percent-encode set": the C0 set (below 0x20, above 0x7E)
     * plus space, `"`, `<`, `>`, backtick, `#`, `?`, `{`, `}`.
     */
    private val PATH_KEEP: (Char) -> Boolean = { c ->
        c.code in 0x20..0x7e && c != ' ' && c != '"' && c != '<' && c != '>' && c != '`' && c != '#' && c != '?' && c != '{' && c != '}'
    }

    /**
     * WHATWG "special-query percent-encode set": the C0 set plus space, `"`,
     * `#`, `<`, `>`, and — because `https` is a special scheme — `'`.
     */
    private val QUERY_KEEP: (Char) -> Boolean = { c ->
        c.code in 0x20..0x7e && c != ' ' && c != '"' && c != '#' && c != '<' && c != '>' && c != '\''
    }
}
