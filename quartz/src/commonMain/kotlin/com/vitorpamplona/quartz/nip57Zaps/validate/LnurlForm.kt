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
package com.vitorpamplona.quartz.nip57Zaps.validate

import com.vitorpamplona.quartz.lightning.Lud06
import com.vitorpamplona.quartz.nip19Bech32.toLnUrl

/**
 * Helpers for converting between the three forms an "lnurl" value can take:
 *   - lud16 (`user@domain`)
 *   - LNURL-pay URL (`https://domain/.well-known/lnurlp/user`)
 *   - bech32 LNURL (`lnurl1...`)
 *
 * NIP-57's `lnurl` tag is most commonly written as the bech32 form, but
 * Appendix F only says it SHOULD equal the recipient's lnurl — without
 * mandating a form. We canonicalize through the URL form so the comparison
 * works regardless of how either side spelled it.
 */
object LnurlForm {
    /**
     * Resolve any of the three forms to the canonical LNURL-pay URL. Returns
     * null if the input doesn't match a known form.
     */
    fun toUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        // Already a URL.
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        // Bech32 LNURL.
        if (trimmed.startsWith("lnurl", ignoreCase = true)) {
            return Lud06().toLnUrlp(trimmed)
        }

        // lud16 (`user@domain`).
        val parts = trimmed.split("@")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return "https://${parts[1]}/.well-known/lnurlp/${parts[0]}"
        }

        return null
    }

    /**
     * Encode an LNURL-pay URL as a bech32 LNURL string. Used to populate the
     * `lnurl` tag on outgoing zap requests in the conventional form.
     */
    fun urlToBech32(url: String): String = url.encodeToByteArray().toLnUrl()

    /**
     * True if both inputs resolve to the same LNURL-pay URL (case-insensitive
     * on scheme + host, case-sensitive on path). Returns false if either side
     * can't be resolved to a URL.
     */
    fun matches(
        a: String,
        b: String,
    ): Boolean {
        val ua = toUrl(a) ?: return false
        val ub = toUrl(b) ?: return false
        return normalizeUrl(ua) == normalizeUrl(ub)
    }

    /**
     * Canonicalize an LNURL-pay URL for comparison: trim trailing slashes and
     * lowercase the scheme + host while keeping the path case-sensitive (some
     * providers are case-sensitive on the username segment).
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd < 0) return trimmed
        val pathStart = trimmed.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return trimmed.lowercase()
        return trimmed.substring(0, pathStart).lowercase() + trimmed.substring(pathStart)
    }
}
