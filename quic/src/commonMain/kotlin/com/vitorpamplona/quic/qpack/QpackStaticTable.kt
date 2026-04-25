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
package com.vitorpamplona.quic.qpack

/**
 * QPACK static table per RFC 9204 Appendix A.
 *
 * 99 entries total. Indices are 0-based on the wire (a varint-prefixed
 * "indexed field line" with T=1 carries an index into this table).
 */
object QpackStaticTable {
    val entries: List<Pair<String, String>> =
        listOf(
            ":authority" to "",
            ":path" to "/",
            "age" to "0",
            "content-disposition" to "",
            "content-length" to "0",
            "cookie" to "",
            "date" to "",
            "etag" to "",
            "if-modified-since" to "",
            "if-none-match" to "",
            "last-modified" to "",
            "link" to "",
            "location" to "",
            "referer" to "",
            "set-cookie" to "",
            ":method" to "CONNECT",
            ":method" to "DELETE",
            ":method" to "GET",
            ":method" to "HEAD",
            ":method" to "OPTIONS",
            ":method" to "POST",
            ":method" to "PUT",
            ":scheme" to "http",
            ":scheme" to "https",
            ":status" to "103",
            ":status" to "200",
            ":status" to "304",
            ":status" to "404",
            ":status" to "503",
            "accept" to "*/*",
            "accept" to "application/dns-message",
            "accept-encoding" to "gzip, deflate, br",
            "accept-ranges" to "bytes",
            "access-control-allow-headers" to "cache-control",
            "access-control-allow-headers" to "content-type",
            "access-control-allow-origin" to "*",
            "cache-control" to "max-age=0",
            "cache-control" to "max-age=2592000",
            "cache-control" to "max-age=604800",
            "cache-control" to "no-cache",
            "cache-control" to "no-store",
            "cache-control" to "public, max-age=31536000",
            "content-encoding" to "br",
            "content-encoding" to "gzip",
            "content-type" to "application/dns-message",
            "content-type" to "application/javascript",
            "content-type" to "application/json",
            "content-type" to "application/x-www-form-urlencoded",
            "content-type" to "image/gif",
            "content-type" to "image/jpeg",
            "content-type" to "image/png",
            "content-type" to "text/css",
            "content-type" to "text/html; charset=utf-8",
            "content-type" to "text/plain",
            "content-type" to "text/plain;charset=utf-8",
            "range" to "bytes=0-",
            "strict-transport-security" to "max-age=31536000",
            "strict-transport-security" to "max-age=31536000; includesubdomains",
            "strict-transport-security" to "max-age=31536000; includesubdomains; preload",
            "vary" to "accept-encoding",
            "vary" to "origin",
            "x-content-type-options" to "nosniff",
            "x-xss-protection" to "1; mode=block",
            ":status" to "100",
            ":status" to "204",
            ":status" to "206",
            ":status" to "302",
            ":status" to "400",
            ":status" to "403",
            ":status" to "421",
            ":status" to "425",
            ":status" to "500",
            "accept-language" to "",
            "access-control-allow-credentials" to "FALSE",
            "access-control-allow-credentials" to "TRUE",
            "access-control-allow-headers" to "*",
            "access-control-allow-methods" to "get",
            "access-control-allow-methods" to "get, post, options",
            "access-control-allow-methods" to "options",
            "access-control-expose-headers" to "content-length",
            "access-control-request-headers" to "content-type",
            "access-control-request-method" to "get",
            "access-control-request-method" to "post",
            "alt-svc" to "clear",
            "authorization" to "",
            "content-security-policy" to "script-src 'none'; object-src 'none'; base-uri 'none'",
            "early-data" to "1",
            "expect-ct" to "",
            "forwarded" to "",
            "if-range" to "",
            "origin" to "",
            "purpose" to "prefetch",
            "server" to "",
            "timing-allow-origin" to "*",
            "upgrade-insecure-requests" to "1",
            "user-agent" to "",
            "x-forwarded-for" to "",
            "x-frame-options" to "deny",
            "x-frame-options" to "sameorigin",
        )

    /** Build a quick lookup of name → first index for encoder name-reference selection. */
    val nameToIndex: Map<String, Int> =
        entries.foldIndexed(mutableMapOf()) { idx, acc, (name, _) ->
            acc.putIfAbsent(name, idx)
            acc
        }

    /** Look up name+value → index, or null if no exact match. */
    val pairToIndex: Map<Pair<String, String>, Int> =
        entries.foldIndexed(mutableMapOf()) { idx, acc, pair ->
            acc.putIfAbsent(pair, idx)
            acc
        }
}
