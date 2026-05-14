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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Turns a fully signed key-path P2TR [Psbt] into a broadcastable transaction.
 *
 * For a key-path taproot spend the witness is just the single Schnorr
 * signature and the scriptSig stays empty, so finalization is purely
 * mechanical: move each input's `PSBT_IN_TAP_KEY_SIG` into the transaction's
 * witness stack.
 */
object PsbtFinalizer {
    /**
     * Build the final signed transaction from [psbt].
     *
     * @throws PsbtSigningException if any input is still missing its key-path
     *         signature.
     */
    fun finalize(psbt: Psbt): BitcoinTransaction {
        val tx = psbt.unsignedTx
        val signedInputs =
            tx.inputs.mapIndexed { index, input ->
                val sig =
                    psbt.inputTapKeySig(index)
                        ?: throw PsbtSigningException("input $index is not signed")
                input.copy(
                    scriptSig = ByteArray(0),
                    witness = listOf(sig),
                )
            }
        return BitcoinTransaction(tx.version, signedInputs, tx.outputs, tx.lockTime)
    }

    /** Convenience: [finalize] then serialize to a broadcast-ready lowercase hex string. */
    fun finalizeToHex(psbt: Psbt): String = finalize(psbt).serialize().toHexKey()

    /** True when every input of [psbt] carries a key-path signature. */
    fun isFullySigned(psbt: Psbt): Boolean =
        psbt.unsignedTx.inputs.indices
            .all { psbt.inputTapKeySig(it) != null }
}
