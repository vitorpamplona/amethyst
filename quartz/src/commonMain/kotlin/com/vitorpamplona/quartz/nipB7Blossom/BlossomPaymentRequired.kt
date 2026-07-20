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
package com.vitorpamplona.quartz.nipB7Blossom

/**
 * BUD-07 payment challenge parsed from a `402 Payment Required` response. A paid
 * Blossom server answers upload/mirror/media requests with a 402 and one or both
 * of the payment headers; the client pays, then retries the same request with the
 * proof attached.
 *
 * - [cashu] — a NUT-24 Cashu token request string from the `X-Cashu` header. The
 *   client pays it (e.g. via a NIP-60 wallet) and retries with the settled token.
 * - [lightning] — a BOLT-11 invoice string from the `X-Lightning` header. The
 *   client pays it and retries; the preimage is the proof.
 * - [reason] — the optional human-readable `X-Reason` message.
 *
 * Kept transport-agnostic (no OkHttp) so it can live in `commonMain`: build it
 * from any per-name header lookup via [fromHeaders].
 */
data class BlossomPaymentRequired(
    val cashu: String? = null,
    val lightning: String? = null,
    val reason: String? = null,
) {
    /** True when the server offered at least one payment method we could attempt. */
    fun hasPaymentOption(): Boolean = !cashu.isNullOrBlank() || !lightning.isNullOrBlank()

    /**
     * [reason] rendered safe to show in a dialog.
     *
     * `X-Reason` is server-controlled text displayed next to a Pay button, so a
     * hostile server would otherwise use it to assert its own amount ("Pay 1
     * sat"), inject blank lines to push the real wording off screen, or run
     * control characters / bidi overrides through the label. This strips every
     * ISO control character (newlines, tabs, NUL) plus the Unicode bidi
     * overrides, collapses the resulting whitespace, and clamps the length.
     *
     * The caller must still present the result as *the server's* words — never
     * as Amethyst's own — because the content itself remains untrusted.
     */
    fun sanitizedReason(maxLength: Int = MAX_REASON_LENGTH): String? {
        val raw = reason ?: return null
        val cleaned =
            raw
                .map { if (it.isISOControl() || it in BIDI_OVERRIDES) ' ' else it }
                .joinToString("")
                .replace(WHITESPACE_RUN, " ")
                .trim()

        if (cleaned.isEmpty()) return null
        return if (cleaned.length > maxLength) cleaned.take(maxLength).trimEnd() + "…" else cleaned
    }

    companion object {
        /** Long enough for a real explanation, short enough that it can't crowd out our own text. */
        const val MAX_REASON_LENGTH = 200

        /** LRE/RLE/PDF/LRO/RLO and the isolate family — invisible, and they reorder what follows. */
        private val BIDI_OVERRIDES = charArrayOf('‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩', '‏', '‎')

        private val WHITESPACE_RUN = Regex("\\s+")

        /**
         * Reads the BUD-07 headers from a 402 response. [header] returns the value
         * for a header name (case-insensitive at the transport layer), or null.
         */
        inline fun fromHeaders(header: (String) -> String?): BlossomPaymentRequired =
            BlossomPaymentRequired(
                cashu = header(BlossomServerUrl.X_CASHU_HEADER)?.trim('"', ' ')?.ifBlank { null },
                lightning = header(BlossomServerUrl.X_LIGHTNING_HEADER)?.trim('"', ' ')?.ifBlank { null },
                reason = header(BlossomServerUrl.REASON_HEADER)?.ifBlank { null },
            )
    }
}

/**
 * The proof a client sends when retrying a request after settling a BUD-07 [BlossomPaymentRequired]:
 * a settled Cashu token (echoed in `X-Cashu`) or the preimage of the paid BOLT-11 invoice
 * (echoed in `X-Lightning`).
 */
data class BlossomPaymentProof(
    val cashu: String? = null,
    val lightningPreimage: String? = null,
) {
    /** The header name/value pairs to attach to the retried request. */
    fun headers(): List<Pair<String, String>> =
        buildList {
            cashu?.let { add(BlossomServerUrl.X_CASHU_HEADER to it) }
            lightningPreimage?.let { add(BlossomServerUrl.X_LIGHTNING_HEADER to it) }
        }
}
