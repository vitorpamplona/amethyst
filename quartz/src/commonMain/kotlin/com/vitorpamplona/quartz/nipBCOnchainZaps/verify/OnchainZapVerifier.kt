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

import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * NIP-BC verifier — validates a [OnchainZapEvent] against the configured
 * chain backend and returns a [VerifiedOnchainZap] result.
 *
 * Implements the spec's verification rules:
 * 1. Parse the txid from the `i` tag.
 * 2. Fetch the transaction from the backend.
 * 3. Derive the recipient's expected Taproot scriptPubKey from the `p` tag.
 * 4. Sum the values of all outputs matching that scriptPubKey. Change outputs
 *    paying back to the sender's own derived script MUST NOT be counted.
 * 5. If verified amount is 0 → discard.
 * 6. Self-zaps (sender == recipient) → discard.
 * 7. Pending (unconfirmed) txs are returned as [VerifiedOnchainZap.Pending];
 *    callers SHOULD exclude them from aggregate totals.
 *
 * Deduplication by `(txid, target)` is the caller's responsibility — typically
 * done in `LocalCache`.
 */
class OnchainZapVerifier(
    private val backend: OnchainBackend,
) {
    suspend fun verify(event: OnchainZapEvent): VerifiedOnchainZap {
        val txid =
            event.txid()
                ?: return VerifiedOnchainZap.Rejected("", VerifiedOnchainZap.Rejected.Reason.MISSING_TXID)

        val recipientPubKey =
            event.recipient()
                ?: return VerifiedOnchainZap.Rejected(txid, VerifiedOnchainZap.Rejected.Reason.MISSING_RECIPIENT)

        // Anti-spoofing rule: self-zaps contribute nothing meaningful.
        if (event.pubKey.equals(recipientPubKey, ignoreCase = true)) {
            return VerifiedOnchainZap.Rejected(txid, VerifiedOnchainZap.Rejected.Reason.SELF_ZAP)
        }

        val tx =
            backend.getTx(txid)
                ?: return VerifiedOnchainZap.Rejected(txid, VerifiedOnchainZap.Rejected.Reason.TX_NOT_FOUND)

        val recipientScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(recipientPubKey).lowercase()
        val senderScriptHex = TaprootAddress.scriptPubKeyHexForRecipient(event.pubKey).lowercase()

        val verifiedSats = sumOutputsToRecipient(tx, recipientScriptHex, senderScriptHex)

        if (verifiedSats == 0L) {
            return VerifiedOnchainZap.Rejected(
                txid,
                VerifiedOnchainZap.Rejected.Reason.ZERO_VERIFIED_AMOUNT,
            )
        }

        return if (tx.confirmations > 0) {
            VerifiedOnchainZap.Confirmed(
                txid = txid,
                recipientPubKey = recipientPubKey,
                verifiedSats = verifiedSats,
                confirmations = tx.confirmations,
                blockHeight = tx.blockHeight,
                blockHashHex = tx.blockHashHex,
            )
        } else {
            VerifiedOnchainZap.Pending(
                txid = txid,
                recipientPubKey = recipientPubKey,
                verifiedSats = verifiedSats,
            )
        }
    }

    /**
     * Sum the value of outputs paying [recipientScriptHex]. Outputs paying
     * back to [senderScriptHex] are change and MUST NOT be counted.
     */
    private fun sumOutputsToRecipient(
        tx: BitcoinTx,
        recipientScriptHex: String,
        senderScriptHex: String,
    ): Long {
        var sum = 0L
        for (out in tx.outputs) {
            val script = out.scriptPubKeyHex.lowercase()
            if (script == recipientScriptHex && script != senderScriptHex) {
                sum += out.valueSats
            }
        }
        return sum
    }
}
