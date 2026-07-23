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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.verify

import androidx.compose.runtime.Immutable

/** Outcome of cryptographically verifying a BOLT12 payer proof. */
@Immutable
sealed interface Bolt12ProofResult {
    /**
     * The proof cryptographically checks out.
     *
     * @property paymentHash the `invoice_payment_hash` — the dedup key for zaps
     *   sharing a target.
     * @property invoiceAmountMillisats the settled `invoice_amount`, if present.
     */
    @Immutable
    data class Valid(
        val paymentHash: ByteArray,
        val invoiceAmountMillisats: Long?,
    ) : Bolt12ProofResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Valid) return false
            return paymentHash.contentEquals(other.paymentHash) && invoiceAmountMillisats == other.invoiceAmountMillisats
        }

        override fun hashCode(): Int = 31 * paymentHash.contentHashCode() + (invoiceAmountMillisats?.hashCode() ?: 0)
    }

    /** The proof is present but fails a check and MUST NOT be counted. */
    @Immutable
    data class Invalid(
        val reason: Reason,
    ) : Bolt12ProofResult

    /**
     * The proof could not be verified with the currently-implemented checks (e.g.
     * a compressed proof needing the not-yet-validated merkle reconstruction).
     * Whether to surface it as unverified or drop it is the caller's policy.
     */
    @Immutable
    data class Unsupported(
        val reason: Reason,
    ) : Bolt12ProofResult

    enum class Reason {
        MISSING_REQUIRED_FIELDS,
        PREIMAGE_MISMATCH,
        INVOICE_SIGNATURE_INVALID,
        PROOF_SIGNATURE_INVALID,
        MALFORMED_KEY,
        COMPRESSED_PROOF_UNSUPPORTED,
    }
}
