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
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** Result of validating a NIP-XX BOLT12 zap event (kind 9736). */
@Immutable
sealed interface Bolt12ZapValidation {
    /**
     * The zap event is well-formed, its embedded intent is signed by the same key
     * and matches, and the payer proof is bound to that intent.
     *
     * @property recipient the zapped author (`p`).
     * @property payer the payer (`P`), or null for an anonymous zap.
     * @property amountMillisats the amount to count.
     * @property paymentHashHex the proof's `invoice_payment_hash`, hex-encoded —
     *   the key clients MUST deduplicate on before summing.
     * @property proofCryptoVerified true when the BOLT12 payer-proof signatures
     *   were fully verified; false when the proof is structurally valid and bound
     *   but its signatures could not yet be checked (a compressed proof — see
     *   [Bolt12ProofVerifier]). Callers decide whether to count or merely display
     *   the latter, and MUST label it as unverified.
     */
    @Immutable
    data class Valid(
        val recipient: HexKey,
        val payer: HexKey?,
        val amountMillisats: Long,
        val paymentHashHex: String,
        val zappedEventId: String?,
        val zappedAddress: String?,
        val zappedKind: Int?,
        val proofCryptoVerified: Boolean,
    ) : Bolt12ZapValidation {
        val isProfileZap: Boolean get() = zappedEventId == null && zappedAddress == null
    }

    /** The event failed validation and MUST NOT be counted. */
    @Immutable
    data class Invalid(
        val reason: Reason,
    ) : Bolt12ZapValidation

    enum class Reason {
        WRONG_KIND,
        BAD_EVENT_SIGNATURE,
        MISSING_DESCRIPTION,
        NOT_EXACTLY_ONE_DESCRIPTION,
        MISSING_RECIPIENT,
        NOT_EXACTLY_ONE_RECIPIENT,
        MISSING_AMOUNT,
        NON_POSITIVE_AMOUNT,
        MISSING_OR_INVALID_OFFER,
        MISSING_OR_INVALID_PROOF,
        MULTIPLE_EVENT_TARGETS,
        MULTIPLE_ADDRESS_TARGETS,
        BOTH_EVENT_AND_ADDRESS_TARGET,
        PAYER_TAG_MISMATCH,
        MISSING_OR_INVALID_INTENT,
        BAD_INTENT_SIGNATURE,
        INTENT_PUBKEY_MISMATCH,
        INVALID_ZAP_ID,
        INTENT_STRUCTURE_INVALID,
        CONTENT_MISMATCH,
        RECIPIENT_MISMATCH,
        AMOUNT_MISMATCH,
        OFFER_MISMATCH,
        TARGET_MISMATCH,
        UNPARSEABLE_OFFER,
        UNPARSEABLE_PROOF,
        PROOF_NOTE_MISMATCH,
        MISSING_INVOICE_AMOUNT,
        PROOF_AMOUNT_MISMATCH,
        OFFER_PROOF_MISMATCH,
        PROOF_MISSING_REQUIRED_FIELDS,
        PROOF_PREIMAGE_MISMATCH,
        PROOF_INVOICE_SIGNATURE_INVALID,
        PROOF_SIGNATURE_INVALID,
        PROOF_MALFORMED_KEY,
    }
}
