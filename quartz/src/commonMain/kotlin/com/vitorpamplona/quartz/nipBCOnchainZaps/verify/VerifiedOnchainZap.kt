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
package com.vitorpamplona.quartz.nipBCOnchainZaps.verify

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Result of verifying a NIP-BC onchain zap event against the chain.
 *
 * Clients SHOULD display [verifiedSats], not the sender-claimed amount.
 */
@Immutable
sealed interface VerifiedOnchainZap {
    val txid: String

    /** The event is valid and the on-chain transaction pays the recipient. */
    @Immutable
    data class Confirmed(
        override val txid: String,
        val recipientPubKey: HexKey,
        val verifiedSats: Long,
        val confirmations: Int,
        val blockHeight: Long?,
        val blockHashHex: String?,
    ) : VerifiedOnchainZap

    /** The transaction exists but is not yet confirmed. */
    @Immutable
    data class Pending(
        override val txid: String,
        val recipientPubKey: HexKey,
        val verifiedSats: Long,
    ) : VerifiedOnchainZap

    /**
     * The event failed verification and SHOULD be discarded.
     *
     * @property reason Why the event was rejected; useful for debugging only —
     *                  do not surface to users.
     */
    @Immutable
    data class Rejected(
        override val txid: String,
        val reason: Reason,
    ) : VerifiedOnchainZap {
        enum class Reason {
            /** Sender equals recipient (self-zap). */
            SELF_ZAP,

            /** Transaction does not exist on the configured backend. */
            TX_NOT_FOUND,

            /** Transaction exists but pays the recipient zero satoshis. */
            ZERO_VERIFIED_AMOUNT,

            /** Event has no `i` tag or it's malformed. */
            MISSING_TXID,

            /** Event has no `p` tag identifying the recipient. */
            MISSING_RECIPIENT,
        }
    }
}
