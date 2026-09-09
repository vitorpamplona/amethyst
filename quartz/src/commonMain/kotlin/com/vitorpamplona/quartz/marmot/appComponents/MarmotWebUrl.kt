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
        val v4 = host.split('.').mapNotNull { it.toIntOrNull() }
        if (v4.size == 4 && v4.all { it in 0..255 }) return !isNonRoutableIpv4(v4)
        return true
    }

    private fun hostOf(normalized: String): String {
        val rest = normalized.substringAfter("://")
        val end = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        return splitHostPort(rest.substring(0, end)).first
    }

    private fun isNonRoutableIpv4(o: List<Int>): Boolean =
        o[0] == 0 ||
            o[0] == 127 ||
            o[0] == 10 ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 169 && o[1] == 254) ||
            (o[0] == 100 && o[1] in 64..127) ||
            o[0] >= 224

    private fun isNonRoutableIpv6(addr: String): Boolean {
        val a = addr.lowercase()
        if (a == "::1" || a == "::") return true
        // Unique-local (fc00::/7) and link-local (fe80::/10).
        return a.startsWith("fc") || a.startsWith("fd") || a.startsWith("fe8") ||
            a.startsWith("fe9") || a.startsWith("fea") || a.startsWith("feb")
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
