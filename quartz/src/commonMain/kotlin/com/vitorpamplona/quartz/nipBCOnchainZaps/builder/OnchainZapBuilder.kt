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
package com.vitorpamplona.quartz.nipBCOnchainZaps.builder

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.BitcoinTransaction
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.OutPoint
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.Psbt
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxIn
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.TxOut
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setInputWitnessUtxo
import com.vitorpamplona.quartz.nipBCOnchainZaps.psbt.setOutputTapInternalKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import kotlin.math.ceil

/**
 * Assembles the unsigned [Psbt] for a NIP-BC onchain zap.
 *
 * The sender's whole "wallet" is the single Taproot address derived from their
 * Nostr pubkey, so every input spends from — and any change returns to — that
 * one address. The recipient output pays the recipient's derived Taproot
 * address.
 *
 * Coin selection is a simple largest-first greedy fill: correct and
 * predictable, not privacy- or fee-optimal. The result is an unsigned PSBT
 * with `PSBT_IN_WITNESS_UTXO` and `PSBT_IN_TAP_INTERNAL_KEY` populated on
 * every input, ready for `NostrSigner.signPsbt`.
 */
object OnchainZapBuilder {
    /** P2TR outputs below this are unspendable dust and must not be created. */
    const val DUST_THRESHOLD_SATS = 330L

    /**
     * nSequence that opts the transaction into BIP-125 replace-by-fee, so a
     * zap stuck at a low fee rate can be bumped instead of being stuck forever.
     */
    const val RBF_SEQUENCE = 0xFFFFFFFDL

    // Virtual-size estimates for an all-P2TR-key-path transaction.
    private const val OVERHEAD_VBYTES = 10.5
    private const val P2TR_INPUT_VBYTES = 57.5
    private const val P2TR_OUTPUT_VBYTES = 43.0

    /**
     * @property psbt The unsigned PSBT, ready to sign.
     * @property selectedUtxos The UTXOs chosen as inputs.
     * @property recipientSats Amount paid to the recipient.
     * @property changeSats Amount returned to the sender (0 if no change output).
     * @property feeSats The miner fee.
     */
    data class Result(
        val psbt: Psbt,
        val selectedUtxos: List<Utxo>,
        val recipientSats: Long,
        val changeSats: Long,
        val feeSats: Long,
    )

    fun estimateVsize(
        inputCount: Int,
        outputCount: Int,
    ): Double = OVERHEAD_VBYTES + inputCount * P2TR_INPUT_VBYTES + outputCount * P2TR_OUTPUT_VBYTES

    fun estimateFee(
        inputCount: Int,
        outputCount: Int,
        feeRateSatPerVByte: Double,
    ): Long = ceil(estimateVsize(inputCount, outputCount) * feeRateSatPerVByte).toLong()

    /**
     * Build the unsigned onchain-zap PSBT.
     *
     * @param senderPubKey The sender's 32-byte x-only Nostr pubkey (hex).
     * @param recipientPubKey The recipient's 32-byte x-only Nostr pubkey (hex).
     * @param amountSats Amount to pay the recipient.
     * @param feeRateSatPerVByte Target fee rate.
     * @param availableUtxos UTXOs spendable from the sender's Taproot address.
     * @param allowUnconfirmed When false (the default), 0-confirmation UTXOs are
     *        excluded — chaining off an unconfirmed parent risks the whole zap
     *        being invalidated if that parent is dropped or replaced.
     * @throws InsufficientFundsException when the UTXOs can't cover amount + fee.
     */
    fun build(
        senderPubKey: HexKey,
        recipientPubKey: HexKey,
        amountSats: Long,
        feeRateSatPerVByte: Double,
        availableUtxos: List<Utxo>,
        allowUnconfirmed: Boolean = false,
    ): Result {
        require(amountSats > 0) { "amount must be positive" }
        require(amountSats >= DUST_THRESHOLD_SATS) { "amount is below the dust threshold" }
        require(feeRateSatPerVByte > 0) { "fee rate must be positive" }
        require(senderPubKey != recipientPubKey) { "cannot zap yourself" }

        val senderXOnly = senderPubKey.hexToByteArray()
        require(senderXOnly.size == 32) { "sender pubkey must be 32 bytes" }
        val senderScript = TaprootAddress.scriptPubKeyForRecipient(senderPubKey)
        val recipientScript = TaprootAddress.scriptPubKeyForRecipient(recipientPubKey)

        // Only spend confirmed UTXOs unless the caller explicitly opts in.
        val spendableUtxos =
            if (allowUnconfirmed) availableUtxos else availableUtxos.filter { it.confirmations > 0 }

        // Largest-first greedy selection.
        val sorted = spendableUtxos.sortedByDescending { it.valueSats }
        val selected = ArrayList<Utxo>()
        var selectedSum = 0L
        var cursor = 0

        while (true) {
            val feeWithChange = estimateFee(selected.size, 2, feeRateSatPerVByte)
            if (selected.isNotEmpty() && selectedSum >= amountSats + feeWithChange) break

            if (cursor >= sorted.size) {
                // Last chance: maybe it fits without a change output.
                val feeNoChange = estimateFee(selected.size, 1, feeRateSatPerVByte)
                if (selected.isNotEmpty() && selectedSum >= amountSats + feeNoChange) break
                throw InsufficientFundsException(
                    needed = amountSats + estimateFee(selected.size.coerceAtLeast(1), 2, feeRateSatPerVByte),
                    available = spendableUtxos.sumOf { it.valueSats },
                )
            }
            selected.add(sorted[cursor])
            selectedSum += sorted[cursor].valueSats
            cursor++
        }

        // Decide whether a change output is worth creating.
        val feeWithChange = estimateFee(selected.size, 2, feeRateSatPerVByte)
        val candidateChange = selectedSum - amountSats - feeWithChange

        val feeSats: Long
        val changeSats: Long
        if (candidateChange >= DUST_THRESHOLD_SATS) {
            feeSats = feeWithChange
            changeSats = candidateChange
        } else {
            // Drop the change output; the leftover (dust + would-be change) is
            // absorbed into the fee.
            val feeNoChange = estimateFee(selected.size, 1, feeRateSatPerVByte)
            val leftover = selectedSum - amountSats
            if (leftover < feeNoChange) {
                throw InsufficientFundsException(
                    needed = amountSats + feeNoChange,
                    available = spendableUtxos.sumOf { it.valueSats },
                )
            }
            feeSats = leftover
            changeSats = 0L
        }

        // Assemble the unsigned transaction.
        val inputs =
            selected.map { utxo ->
                TxIn(
                    outPoint = OutPoint(utxo.txid, utxo.vout.toLong()),
                    scriptSig = ByteArray(0),
                    sequence = RBF_SEQUENCE,
                )
            }
        val outputs = ArrayList<TxOut>(2)
        outputs.add(TxOut(amountSats, recipientScript))
        if (changeSats > 0) {
            outputs.add(TxOut(changeSats, senderScript))
        }

        val tx = BitcoinTransaction(version = 2L, inputs = inputs, outputs = outputs, lockTime = 0L)

        // Wrap into a PSBT and populate the signing metadata.
        val psbt = Psbt.fromUnsignedTx(tx)
        selected.forEachIndexed { i, utxo ->
            psbt.setInputWitnessUtxo(i, TxOut(utxo.valueSats, senderScript))
            psbt.setInputTapInternalKey(i, senderXOnly)
        }
        if (changeSats > 0) {
            psbt.setOutputTapInternalKey(1, senderXOnly)
        }

        return Result(
            psbt = psbt,
            selectedUtxos = selected,
            recipientSats = amountSats,
            changeSats = changeSats,
            feeSats = feeSats,
        )
    }
}

/**
 * Thrown when the available UTXOs cannot cover the requested amount plus fee.
 *
 * @property needed Total satoshis required (amount + estimated fee).
 * @property available Total satoshis available across all UTXOs.
 */
class InsufficientFundsException(
    val needed: Long,
    val available: Long,
) : RuntimeException("Insufficient funds: need $needed sats, have $available sats")
