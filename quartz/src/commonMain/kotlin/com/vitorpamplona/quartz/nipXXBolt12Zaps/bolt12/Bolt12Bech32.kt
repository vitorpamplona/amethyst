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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32

/**
 * BOLT12 bech32 codec.
 *
 * BOLT12 reuses the bech32 character set and 8-to-5-bit conversion but differs
 * from BIP-173 in two ways this object handles:
 *
 *  1. **No checksum and no length limit.** Offers and proofs can be far longer
 *     than the 90-char BIP-173 cap and carry no trailing 6-char checksum, so we
 *     decode with [Bech32.decodeBytes] in `noChecksum` mode. (The bech32 data
 *     alphabet excludes `1`, so the single `1` separating the human-readable
 *     prefix from the data is unambiguous even for long strings.)
 *  2. **`+` continuations.** For transport, a long string may be split with `+`
 *     separators optionally surrounded by whitespace (`lno1abc+ def`). The
 *     canonical form removes every `+` and whitespace character — see
 *     [canonicalize]. The NIP stores only canonical offers/proofs, but callers
 *     should canonicalize any externally-sourced string before use.
 *
 * See https://github.com/lightning/bolts/blob/master/12-offer-encoding.md
 */
object Bolt12Bech32 {
    /** Human-readable prefix of a BOLT12 offer. */
    const val OFFER_HRP = "lno"

    /** Human-readable prefix of a BOLT12 payer proof (lightning/bolts#1346). */
    const val PAYER_PROOF_HRP = "lnp"

    /**
     * Removes BOLT12 `+` continuations and all whitespace and lowercases the
     * result, producing the canonical raw form the NIP stores and compares.
     */
    fun canonicalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            if (c == '+' || c == ' ' || c == '\t' || c == '\n' || c == '\r') continue
            sb.append(c)
        }
        return sb.toString().lowercase()
    }

    // O(1) bech32 data-alphabet membership, indexed by char code (ASCII only).
    private val IS_BECH32_CHAR =
        BooleanArray(128).also { table ->
            for (c in Bech32.ALPHABET) table[c.code] = true
        }

    private fun isBech32Char(c: Char): Boolean = c.code < 128 && IS_BECH32_CHAR[c.code]

    private fun hasHrp(
        canonical: String,
        hrp: String,
    ): Boolean {
        val prefix = "${hrp}1"
        if (canonical.length <= prefix.length) return false
        if (!canonical.startsWith(prefix)) return false
        // every data char must be in the bech32 alphabet
        for (i in prefix.length until canonical.length) {
            if (!isBech32Char(canonical[i])) return false
        }
        return true
    }

    /** True when [canonical] is a syntactically well-formed canonical `lno1...` offer. */
    fun isOffer(canonical: String) = hasHrp(canonical, OFFER_HRP)

    /** True when [canonical] is a syntactically well-formed canonical `lnp1...` payer proof. */
    fun isPayerProof(canonical: String) = hasHrp(canonical, PAYER_PROOF_HRP)

    /**
     * Decodes a BOLT12 bech32 string (offer or proof) into its raw TLV-stream
     * bytes, first canonicalizing it. Optionally asserts the human-readable
     * prefix. Throws [IllegalArgumentException] on malformed input or a prefix
     * mismatch.
     */
    fun decodeToBytes(
        raw: String,
        expectedHrp: String? = null,
    ): ByteArray {
        val (hrp, bytes, _) = Bech32.decodeBytes(canonicalize(raw), noChecksum = true)
        if (expectedHrp != null) {
            require(hrp == expectedHrp) { "Expected BOLT12 prefix $expectedHrp but obtained $hrp" }
        }
        return bytes
    }

    fun decodeToBytesOrNull(
        raw: String,
        expectedHrp: String? = null,
    ): ByteArray? =
        try {
            decodeToBytes(raw, expectedHrp)
        } catch (_: Exception) {
            null
        }

    /**
     * Encodes raw TLV-stream [bytes] as a canonical (no `+` continuation) BOLT12
     * bech32 string with the given [hrp]. Primarily an interop/testing helper —
     * production only ever decodes offers and proofs produced by wallets.
     */
    fun encode(
        hrp: String,
        bytes: ByteArray,
    ): String = Bech32.encodeBytes(hrp, bytes, Bech32.Encoding.Beck32WithoutChecksum)
}
