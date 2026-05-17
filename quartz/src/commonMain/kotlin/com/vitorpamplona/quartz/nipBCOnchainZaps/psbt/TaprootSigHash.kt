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

import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * BIP-341 taproot signature hash (`SigMsg` + `TapSighash` tagged hash).
 *
 * Supports key-path spends (`ext_flag = 0`) with all six base sighash types,
 * with and without an annex. Script-path spends (`ext_flag = 1`) are out of
 * scope for NIP-BC, which only ever spends key-path P2TR outputs.
 *
 * Reference: <https://github.com/bitcoin/bips/blob/master/bip-0341.mediawiki>
 */
object TaprootSigHash {
    const val SIGHASH_DEFAULT = 0x00
    const val SIGHASH_ALL = 0x01
    const val SIGHASH_NONE = 0x02
    const val SIGHASH_SINGLE = 0x03
    const val SIGHASH_ANYONECANPAY = 0x80

    private val tapSighashTag: ByteArray by lazy { sha256("TapSighash".encodeToByteArray()) }

    /**
     * Compute the BIP-341 signature hash for a key-path P2TR spend.
     *
     * @param tx The transaction being signed.
     * @param inputIndex The index of the input whose signature is being produced.
     * @param spentOutputs The previous outputs being spent, one per input of [tx],
     *                     in the same order as `tx.inputs`.
     * @param hashType A BIP-341 sighash type byte (default `SIGHASH_DEFAULT`).
     * @param annex Optional annex bytes (including the `0x50` prefix), or null.
     */
    fun compute(
        tx: BitcoinTransaction,
        inputIndex: Int,
        spentOutputs: List<TxOut>,
        hashType: Int = SIGHASH_DEFAULT,
        annex: ByteArray? = null,
    ): ByteArray {
        require(spentOutputs.size == tx.inputs.size) {
            "spentOutputs (${spentOutputs.size}) must match inputs (${tx.inputs.size})"
        }
        require(inputIndex in tx.inputs.indices) { "inputIndex $inputIndex out of range" }

        val anyoneCanPay = (hashType and SIGHASH_ANYONECANPAY) != 0
        val outputType = hashType and 0x03
        require(
            hashType == SIGHASH_DEFAULT ||
                outputType == SIGHASH_ALL ||
                outputType == SIGHASH_NONE ||
                outputType == SIGHASH_SINGLE,
        ) { "invalid sighash type $hashType" }

        val ss = BitcoinWriter()

        // Sighash epoch.
        ss.writeByte(0x00)

        // Common signature message fields.
        ss.writeByte(hashType)
        ss.writeUInt32LE(tx.version)
        ss.writeUInt32LE(tx.lockTime)

        if (!anyoneCanPay) {
            ss.writeBytes(shaPrevouts(tx))
            ss.writeBytes(shaAmounts(spentOutputs))
            ss.writeBytes(shaScriptPubKeys(spentOutputs))
            ss.writeBytes(shaSequences(tx))
        }

        if (outputType != SIGHASH_NONE && outputType != SIGHASH_SINGLE) {
            ss.writeBytes(shaOutputs(tx))
        }

        // spend_type = ext_flag * 2 + annex_present. ext_flag = 0 for key-path.
        val annexPresent = if (annex != null) 1 else 0
        ss.writeByte(annexPresent)

        if (anyoneCanPay) {
            tx.inputs[inputIndex].outPoint.write(ss)
            ss.writeUInt64LE(spentOutputs[inputIndex].valueSats)
            ss.writeVarBytes(spentOutputs[inputIndex].scriptPubKey)
            ss.writeUInt32LE(tx.inputs[inputIndex].sequence)
        } else {
            ss.writeUInt32LE(inputIndex.toLong())
        }

        if (annex != null) {
            val a = BitcoinWriter()
            a.writeVarBytes(annex)
            ss.writeBytes(sha256(a.toByteArray()))
        }

        if (outputType == SIGHASH_SINGLE) {
            require(inputIndex < tx.outputs.size) {
                "SIGHASH_SINGLE with no matching output at index $inputIndex"
            }
            val o = BitcoinWriter()
            tx.outputs[inputIndex].write(o)
            ss.writeBytes(sha256(o.toByteArray()))
        }

        return taggedHash(tapSighashTag, ss.toByteArray())
    }

    /** BIP-340 tagged hash: `SHA256(SHA256(tag) || SHA256(tag) || msg)`. */
    private fun taggedHash(
        tagHash: ByteArray,
        msg: ByteArray,
    ): ByteArray {
        val buf = ByteArray(tagHash.size * 2 + msg.size)
        tagHash.copyInto(buf, 0)
        tagHash.copyInto(buf, tagHash.size)
        msg.copyInto(buf, tagHash.size * 2)
        return sha256(buf)
    }

    private fun shaPrevouts(tx: BitcoinTransaction): ByteArray {
        val w = BitcoinWriter()
        tx.inputs.forEach { it.outPoint.write(w) }
        return sha256(w.toByteArray())
    }

    private fun shaAmounts(spentOutputs: List<TxOut>): ByteArray {
        val w = BitcoinWriter()
        spentOutputs.forEach { w.writeUInt64LE(it.valueSats) }
        return sha256(w.toByteArray())
    }

    private fun shaScriptPubKeys(spentOutputs: List<TxOut>): ByteArray {
        val w = BitcoinWriter()
        spentOutputs.forEach { w.writeVarBytes(it.scriptPubKey) }
        return sha256(w.toByteArray())
    }

    private fun shaSequences(tx: BitcoinTransaction): ByteArray {
        val w = BitcoinWriter()
        tx.inputs.forEach { w.writeUInt32LE(it.sequence) }
        return sha256(w.toByteArray())
    }

    private fun shaOutputs(tx: BitcoinTransaction): ByteArray {
        val w = BitcoinWriter()
        tx.outputs.forEach { it.write(w) }
        return sha256(w.toByteArray())
    }
}
