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
 * Signs the key-path P2TR inputs of a [Psbt] with a single private key.
 *
 * This is the core of `NostrSigner.signPsbt` — pure protocol logic, no UI or
 * signer-app dependency. The signer:
 * 1. derives the caller's x-only public key,
 * 2. for each input whose `PSBT_IN_TAP_INTERNAL_KEY` matches that key and
 *    which is not already signed,
 * 3. computes the BIP-341 sighash over all inputs (so every input must carry
 *    a `PSBT_IN_WITNESS_UTXO`),
 * 4. applies the BIP-341 key-path tweak to the private key, and
 * 5. produces a BIP-340 Schnorr signature, stored as `PSBT_IN_TAP_KEY_SIG`.
 *
 * Inputs the key does not control are left untouched. Finalization (moving the
 * signature into the transaction witness) is [PsbtFinalizer]'s job.
 */
object PsbtSigner {
    /**
     * Sign every key-path input of [psbt] that [privKey] controls, in place.
     *
     * @return the number of inputs signed by this call.
     * @throws PsbtSigningException if a controllable input is missing the
     *         witness-UTXO data needed to compute its sighash.
     */
    fun signKeyPathInputs(
        psbt: Psbt,
        privKey: ByteArray,
    ): Int {
        require(privKey.size == 32) { "private key must be 32 bytes" }

        val ourXOnlyPubKey = Secp256k1Instance.compressedPubKeyFor(privKey).copyOfRange(1, 33)
        val tx = psbt.unsignedTx

        // Lazily materialized: every input's spent output, needed for the
        // all-inputs commitment in the BIP-341 sighash.
        var spentOutputs: List<TxOut>? = null

        var signed = 0
        for (index in tx.inputs.indices) {
            val internalKey = psbt.inputTapInternalKey(index) ?: continue
            if (!internalKey.contentEquals(ourXOnlyPubKey)) continue
            if (psbt.inputTapKeySig(index) != null) continue // already signed

            if (spentOutputs == null) {
                spentOutputs =
                    tx.inputs.indices.map { i ->
                        psbt.inputWitnessUtxo(i)
                            ?: throw PsbtSigningException(
                                "input $i has no witness UTXO; cannot compute sighash",
                            )
                    }
            }

            val sighashType = psbt.inputSighashType(index) ?: TaprootSigHash.SIGHASH_DEFAULT
            val sigHash = TaprootSigHash.compute(tx, index, spentOutputs, sighashType)

            val tweakedSecKey = TaprootAddress.tweakSecretKey(privKey)
            val signature64 = Secp256k1Instance.signSchnorr(sigHash, tweakedSecKey)

            // SIGHASH_DEFAULT → bare 64-byte signature. Any other type → append
            // the sighash byte for a 65-byte signature, per BIP-341.
            val signature =
                if (sighashType == TaprootSigHash.SIGHASH_DEFAULT) {
                    signature64
                } else {
                    signature64 + byteArrayOf(sighashType.toByte())
                }

            psbt.setInputTapKeySig(index, signature)
            signed++
        }
        return signed
    }
}

/** Thrown when a PSBT cannot be signed (e.g. missing witness-UTXO data). */
class PsbtSigningException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
