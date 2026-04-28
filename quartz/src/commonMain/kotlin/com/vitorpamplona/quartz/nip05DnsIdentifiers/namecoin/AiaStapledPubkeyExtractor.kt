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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Extracts a stapled `SubjectPublicKeyInfo` (SPKI) DER blob from cert metadata
 * produced by `ncgencert`'s AIA-stapling convention.
 *
 * # Background — why this exists
 *
 * `ncgencert` (Namecoin's TLS certificate generator) produces a 3-tier chain:
 *
 *   leaf  ←  Domain CA  ←  AIA Parent CA
 *
 * The AIA Parent CA is **not** sent during the TLS handshake — only `leaf` and
 * `Domain CA` are served. The TLSA record published in the Namecoin `tls`
 * field, however, is the SHA-256 of the AIA Parent CA's SPKI. To let
 * verifiers pin the chain without an extra round trip, ncgencert "staples"
 * the AIA Parent CA's pubkey into the Domain CA's metadata, in two
 * redundant locations on the same cert:
 *
 *   1. As a JSON blob inside the issuer-DN `serialNumber` (OID 2.5.4.5)
 *      attribute value, formatted exactly:
 *
 *          Namecoin TLS Certificate\n\nStapled: {"pubb64":"<URL-SAFE-BASE64>"}
 *
 *   2. As a `pubb64=<URL-SAFE-BASE64>` query parameter on the AIA `caIssuers`
 *      access-method URL (extension OID 1.3.6.1.5.5.7.1.1).
 *
 * The pubb64 value is a URL-safe base64 (RFC 4648 §5) encoding of the AIA
 * Parent CA's SPKI DER (~91 bytes for an EC P-256 key, ~294 bytes for
 * RSA-2048).
 *
 * Cryptographic soundness: the issuer-DN serialNumber attribute is part of
 * the data signed by the AIA Parent CA's private key when it issued the
 * Domain CA. Tampering with the staple would invalidate that signature and
 * the platform `X509TrustManager` would reject the chain before this
 * extractor ever ran. So even though the staple itself is not signed by a
 * trusted root, it cannot be forged independently of the AIA Parent CA.
 *
 * # API
 *
 * The extractor is platform-agnostic on purpose: callers (typically JVM)
 * pull the raw strings out of an `X509Certificate` using whatever DER /
 * X.500 helpers their platform offers, and feed them in here.
 *
 *   - [extractFromIssuerDn] takes the RFC 2253 / RFC 4514 form of the
 *     issuer DN (`CN=...,2.5.4.5=Namecoin TLS Certificate\n\nStapled: {...}`).
 *     It tolerates either the raw bytes form (with literal `\n`) or the
 *     escaped form `\0A` produced by `X500Principal.getName(RFC2253)`.
 *
 *   - [extractFromAiaUrl] takes one CA-Issuers URL and pulls the
 *     `pubb64=` query parameter. URLs without the parameter return null.
 *
 *   - [extractFromCert] is a convenience that runs both extractors and
 *     returns the first non-null result.
 *
 * Returns: the decoded SPKI DER bytes, or `null` if the input doesn't carry
 * the staple. Returns `null` rather than throwing on malformed base64 —
 * downstream callers should treat absence as "no staple present" and fall
 * through to the normal NoMatch path.
 */
object AiaStapledPubkeyExtractor {
    private const val STAPLED_KEY = "\"pubb64\""
    private const val PUBB64_QUERY_PARAM = "pubb64="

    /**
     * Pull a stapled SPKI from the issuer-DN serialNumber attribute.
     *
     * Accepts either:
     *   - the raw multi-line value (`Namecoin TLS Certificate\n\nStapled: {"pubb64":"..."}`)
     *   - the RFC 2253 escaped form, where literal newlines may appear as
     *     `\0A` and embedded quotes as `\"`. We unescape the bytes we care
     *     about (`\0A` → `\n`, `\22` → `"`, `\\` → `\`) before searching.
     *
     * @return decoded SPKI DER bytes, or `null` if no staple is present.
     */
    fun extractFromIssuerDn(rawDn: String?): ByteArray? {
        if (rawDn.isNullOrEmpty()) return null
        // First try the input as-is (handles raw + simple-escape forms).
        findPubb64InJson(unescapeRfc2253(rawDn))?.let { return it }
        // Then try decoding any RFC 2253 §2.4 #-hex-encoded attribute values.
        // X500Principal.getName(RFC2253) emits this form when an attribute
        // value contains characters that aren't safe in the printable form
        // (the ncgencert staple's `\n` and `{` chars trigger this).
        for (decoded in extractHexEncodedAttributeValues(rawDn)) {
            findPubb64InJson(decoded)?.let { return it }
        }
        return null
    }

    /**
     * Pull a stapled SPKI from one AIA `caIssuers` URL by reading the
     * `pubb64` query parameter. Returns null for URLs without the param,
     * for relative URLs, or for URLs whose `pubb64` value isn't decodable.
     */
    fun extractFromAiaUrl(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) return null
        val queryStart = url.indexOf('?').takeIf { it >= 0 } ?: return null
        val query = url.substring(queryStart + 1)
        // Walk &-separated parameters. Don't depend on `Url` parsers
        // (commonMain doesn't have a portable one).
        for (param in query.split('&')) {
            if (param.startsWith(PUBB64_QUERY_PARAM)) {
                val raw = param.substring(PUBB64_QUERY_PARAM.length)
                // Strip any trailing fragment if the URL had one.
                val clean = raw.substringBefore('#').substringBefore('&')
                return decodeUrlSafeBase64(clean)
            }
        }
        return null
    }

    /**
     * Convenience: try [extractFromIssuerDn] first, then walk the supplied
     * AIA URLs (in order) until one produces a non-null result.
     */
    fun extractFromCert(
        issuerDn: String?,
        aiaCaIssuersUrls: List<String>,
    ): ByteArray? {
        extractFromIssuerDn(issuerDn)?.let { return it }
        for (url in aiaCaIssuersUrls) {
            extractFromAiaUrl(url)?.let { return it }
        }
        return null
    }

    /**
     * Find the first `"pubb64":"<value>"` JSON pair in [text] and return the
     * decoded SPKI bytes. The `text` is searched verbatim — pass an already
     * unescaped string. Returns null if absent or undecodable.
     */
    private fun findPubb64InJson(text: String): ByteArray? {
        val keyIdx = text.indexOf(STAPLED_KEY)
        if (keyIdx < 0) return null
        // Skip past the closing quote of the key.
        var i = keyIdx + STAPLED_KEY.length
        // Allow optional whitespace + ':' + whitespace before the value.
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != ':') return null
        i++
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '"') return null
        i++
        val valueStart = i
        // Find the (unescaped) closing quote. The pubb64 alphabet doesn't
        // include `"` or `\`, so a plain scan is safe.
        val valueEnd = text.indexOf('"', valueStart)
        if (valueEnd < 0) return null
        val value = text.substring(valueStart, valueEnd)
        return decodeUrlSafeBase64(value)
    }

    /**
     * Best-effort RFC 2253 unescape. Only the escapes that actually appear
     * in ncgencert's stapled blob are handled:
     *
     *   - `\0A`  → newline
     *   - `\22`  → `"`
     *   - `\\`   → `\`
     *
     * Other RFC 2253 escapes (`\,` `\;` `\#` …) pass through as-is. We are
     * NOT trying to be a general DN parser — the goal is just to recover
     * the literal `"pubb64":"..."` substring. Any escape we miss will at
     * worst cause [findPubb64InJson] to return null, which is correct
     * fall-through behaviour.
     */
    private fun unescapeRfc2253(input: String): String {
        if (!input.contains('\\')) return input
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '\\' || i + 1 >= input.length) {
                out.append(c)
                i++
                continue
            }
            val next = input[i + 1]
            // Hex-escape: `\HH`. Two ASCII hex digits.
            if (next.isHexDigit() && i + 2 < input.length && input[i + 2].isHexDigit()) {
                val hex = input.substring(i + 1, i + 3)
                val code = hex.toInt(16)
                out.append(code.toChar())
                i += 3
                continue
            }
            // Single-char escape: `\,` `\"` `\\` etc.
            out.append(next)
            i += 2
        }
        return out.toString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /**
     * Find every RFC 2253 §2.4 hex-encoded attribute value (`#<hex bytes>`)
     * in [input] and return its decoded UTF-8 string form. The hex bytes are
     * a DER-encoded ASN.1 value; we strip the outer tag + length and decode
     * the rest as UTF-8 (which works for the printable/utf8 string types
     * X500Principal emits here). Values that don't decode cleanly are
     * skipped — callers should treat absence as "no staple".
     *
     * Each `#<hex>` run continues until a separator (`,`, `+`, end-of-string,
     * unescaped whitespace) per RFC 2253.
     */
    private fun extractHexEncodedAttributeValues(input: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '#') {
                val start = i + 1
                var j = start
                while (j < input.length) {
                    val ch = input[j]
                    if (!ch.isHexDigit()) break
                    j++
                }
                if (j > start && (j - start) % 2 == 0) {
                    val hex = input.substring(start, j)
                    decodeDerStringValue(hex)?.let { out.add(it) }
                }
                i = j
            } else {
                i++
            }
        }
        return out
    }

    /**
     * Take the hex form of a DER-encoded ASN.1 string value and return the
     * UTF-8 contents. We support short-form lengths (sufficient for any
     * reasonable attribute value); longer values fall back to a permissive
     * "strip first 2 bytes and decode" path that handles the common case.
     */
    private fun decodeDerStringValue(hex: String): String? {
        if (hex.length < 4 || hex.length % 2 != 0) return null
        val bytes = ByteArray(hex.length / 2)
        for (k in bytes.indices) {
            val v = hex.substring(k * 2, k * 2 + 2).toIntOrNull(16) ?: return null
            bytes[k] = v.toByte()
        }
        // bytes[0] = ASN.1 tag (PrintableString = 0x13, UTF8String = 0x0C, etc.)
        // bytes[1] = length (short form < 128) or `0x81 + len` for long form.
        if (bytes.size < 2) return null
        var contentStart: Int
        var contentLength: Int
        val lenByte = bytes[1].toInt() and 0xFF
        if (lenByte < 0x80) {
            contentStart = 2
            contentLength = lenByte
        } else {
            val lenOctets = lenByte and 0x7F
            if (lenOctets == 0 || bytes.size < 2 + lenOctets) return null
            var len = 0
            for (k in 0 until lenOctets) {
                len = (len shl 8) or (bytes[2 + k].toInt() and 0xFF)
            }
            contentStart = 2 + lenOctets
            contentLength = len
        }
        if (contentStart + contentLength > bytes.size) return null
        return runCatching {
            bytes.copyOfRange(contentStart, contentStart + contentLength).decodeToString()
        }.getOrNull()
    }

    /**
     * Decode a URL-safe base64 string, tolerating missing padding and the
     * standard alphabet (`+/`) as well as the URL-safe alphabet (`-_`).
     * Returns null for anything we can't decode.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeUrlSafeBase64(value: String): ByteArray? {
        if (value.isEmpty()) return null
        val normalised = value.replace('-', '+').replace('_', '/').filterNot { it.isWhitespace() }
        val padded =
            when (val mod = normalised.length % 4) {
                0 -> normalised
                else -> normalised + "=".repeat(4 - mod)
            }
        return runCatching { Base64.decode(padded) }.getOrNull()
    }
}
