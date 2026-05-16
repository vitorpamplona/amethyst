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
package com.vitorpamplona.quartz.nipBCOnchainZaps.psbt

import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Secp256k1Instance

/**
 * Independently verifies the key-path P2TR signatures inside a [Psbt].
 *
 * [PsbtFinalizer.isFullySigned] only checks that a `PSBT_IN_TAP_KEY_SIG`
 * record is *present*; it does not check the signature is *valid* nor that it
 * commits to *this* transaction. Before broadcasting a transaction that came
 * back from an external signer, the client MUST also confirm every signature
 * actually verifies — otherwise a buggy or malicious signer could substitute a
 * different transaction (with valid signatures over it) and redirect funds.
 *
 * Each signature is checked as a BIP-340 Schnorr signature over the BIP-341
 * sighash, against the output key derived from the input's
 * `PSBT_IN_TAP_INTERNAL_KEY`. Both anchors — the sighash and the tweak — are
 * validated against the BIP-341 wallet test vectors, so this check is not
 * circular with the signer.
 */
object PsbtSignatureVerifier {
    /**
     * True iff every input of [psbt] carries a key-path tap signature, every
     * input has the witness-UTXO data needed to compute its sighash, and every
     * signature is a valid BIP-340 signature over the BIP-341 sighash.
     */
    fun verifyAllKeyPathInputs(psbt: Psbt): Boolean {
        val tx = psbt.unsignedTx
        if (tx.inputs.isEmpty()) return false

        val spentOutputs =
            tx.inputs.indices.map { psbt.inputWitnessUtxo(it) ?: return false }

        for (index in tx.inputs.indices) {
            val internalKey = psbt.inputTapInternalKey(index) ?: return false
            val sig = psbt.inputTapKeySig(index) ?: return false

            // BIP-341: a 64-byte signature implies SIGHASH_DEFAULT; a 65-byte
            // signature carries the (non-default) sighash type as its last byte.
            val sighashType: Int
            val sig64: ByteArray
            when (sig.size) {
                64 -> {
                    sighashType = TaprootSigHash.SIGHASH_DEFAULT
                    sig64 = sig
                }

                65 -> {
                    sighashType = sig[64].toInt() and 0xFF
                    // A 65-byte signature must not encode SIGHASH_DEFAULT.
                    if (sighashType == TaprootSigHash.SIGHASH_DEFAULT) return false
                    sig64 = sig.copyOfRange(0, 64)
                }

                else -> {
                    return false
                }
            }

            val sigHash =
                runCatching { TaprootSigHash.compute(tx, index, spentOutputs, sighashType) }
                    .getOrElse { return false }
            val outputKey =
                runCatching { TaprootAddress.tweakOutputKey(internalKey) }
                    .getOrElse { return false }

            if (!Secp256k1Instance.verifySchnorr(sig64, sigHash, outputKey)) return false
        }
        return true
    }
}
