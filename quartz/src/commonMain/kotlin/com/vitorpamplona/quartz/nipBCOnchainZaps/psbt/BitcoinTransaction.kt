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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/** Double SHA-256, the Bitcoin hash. */
internal fun hash256(data: ByteArray): ByteArray = sha256(sha256(data))

/**
 * A reference to a specific output of a previous transaction.
 *
 * @property txid Transaction id in display byte order (64-char lowercase hex).
 * @property vout Output index within that transaction.
 */
@Immutable
data class OutPoint(
    val txid: String,
    val vout: Long,
) {
    fun write(writer: BitcoinWriter) {
        // On the wire the txid is stored in internal (reversed) byte order.
        writer.writeBytes(txid.hexToByteArray().reversedArray())
        writer.writeUInt32LE(vout)
    }

    companion object {
        fun read(reader: BitcoinReader): OutPoint {
            val txidInternal = reader.readBytes(32)
            val txid = txidInternal.reversedArray().toHexKey()
            val vout = reader.readUInt32LE()
            return OutPoint(txid, vout)
        }
    }
}

/**
 * A transaction input.
 *
 * @property outPoint The previous output being spent.
 * @property scriptSig The unlocking script. Empty for segwit inputs.
 * @property sequence nSequence value.
 * @property witness Witness stack items. Empty for a pre-signing or legacy input.
 */
@Immutable
data class TxIn(
    val outPoint: OutPoint,
    val scriptSig: ByteArray = ByteArray(0),
    val sequence: Long = 0xFFFFFFFFL,
    val witness: List<ByteArray> = emptyList(),
) {
    fun writeWithoutWitness(writer: BitcoinWriter) {
        outPoint.write(writer)
        writer.writeVarBytes(scriptSig)
        writer.writeUInt32LE(sequence)
    }

    fun writeWitness(writer: BitcoinWriter) {
        writer.writeVarInt(witness.size.toLong())
        witness.forEach { writer.writeVarBytes(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TxIn) return false
        return outPoint == other.outPoint &&
            scriptSig.contentEquals(other.scriptSig) &&
            sequence == other.sequence &&
            witness.size == other.witness.size &&
            witness.indices.all { witness[it].contentEquals(other.witness[it]) }
    }

    override fun hashCode(): Int {
        var result = outPoint.hashCode()
        result = 31 * result + scriptSig.contentHashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + witness.sumOf { it.contentHashCode() }
        return result
    }

    companion object {
        fun readWithoutWitness(reader: BitcoinReader): TxIn {
            val outPoint = OutPoint.read(reader)
            val scriptSig = reader.readVarBytes()
            val sequence = reader.readUInt32LE()
            return TxIn(outPoint, scriptSig, sequence)
        }
    }
}

/**
 * A transaction output.
 *
 * @property valueSats Output value in satoshis.
 * @property scriptPubKey The locking script.
 */
@Immutable
data class TxOut(
    val valueSats: Long,
    val scriptPubKey: ByteArray,
) {
    fun write(writer: BitcoinWriter) {
        writer.writeUInt64LE(valueSats)
        writer.writeVarBytes(scriptPubKey)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TxOut) return false
        return valueSats == other.valueSats && scriptPubKey.contentEquals(other.scriptPubKey)
    }

    override fun hashCode(): Int = 31 * valueSats.hashCode() + scriptPubKey.contentHashCode()

    companion object {
        fun read(reader: BitcoinReader): TxOut = TxOut(reader.readUInt64LE(), reader.readVarBytes())
    }
}

/**
 * A Bitcoin transaction.
 *
 * Supports both legacy and BIP-144 segwit serialization. [txid] is computed
 * over the legacy serialization (witness-stripped), per consensus rules.
 */
@Immutable
data class BitcoinTransaction(
    val version: Long,
    val inputs: List<TxIn>,
    val outputs: List<TxOut>,
    val lockTime: Long,
) {
    val hasWitness: Boolean get() = inputs.any { it.witness.isNotEmpty() }

    /** Legacy (witness-stripped) serialization — the bytes that [txid] hashes. */
    fun serializeForId(): ByteArray {
        val w = BitcoinWriter()
        w.writeUInt32LE(version)
        w.writeVarInt(inputs.size.toLong())
        inputs.forEach { it.writeWithoutWitness(w) }
        w.writeVarInt(outputs.size.toLong())
        outputs.forEach { it.write(w) }
        w.writeUInt32LE(lockTime)
        return w.toByteArray()
    }

    /** Full serialization. Uses BIP-144 segwit format when any input carries witness data. */
    fun serialize(): ByteArray {
        if (!hasWitness) return serializeForId()
        val w = BitcoinWriter()
        w.writeUInt32LE(version)
        w.writeByte(0x00) // segwit marker
        w.writeByte(0x01) // segwit flag
        w.writeVarInt(inputs.size.toLong())
        inputs.forEach { it.writeWithoutWitness(w) }
        w.writeVarInt(outputs.size.toLong())
        outputs.forEach { it.write(w) }
        inputs.forEach { it.writeWitness(w) }
        w.writeUInt32LE(lockTime)
        return w.toByteArray()
    }

    /** Transaction id in display byte order (reversed double-SHA256 of the legacy bytes). */
    fun txid(): String = hash256(serializeForId()).reversedArray().toHexKey()

    companion object {
        fun parse(rawHex: String): BitcoinTransaction = parse(rawHex.hexToByteArray())

        fun parse(bytes: ByteArray): BitcoinTransaction {
            val reader = BitcoinReader(bytes)
            val version = reader.readUInt32LE()

            // Detect the BIP-144 segwit marker+flag (0x00 0x01).
            var isSegwit = false
            val firstByte = reader.readByte()
            val inputCount: Long
            if (firstByte == 0x00) {
                val flag = reader.readByte()
                if (flag != 0x01) throw PsbtParseException("Invalid segwit flag $flag")
                isSegwit = true
                inputCount = reader.readVarInt()
            } else {
                // firstByte was actually the start of the input-count varint.
                inputCount =
                    when (firstByte) {
                        0xFD -> reader.readUInt16LE().toLong()
                        0xFE -> reader.readUInt32LE()
                        0xFF -> reader.readUInt64LE()
                        else -> firstByte.toLong()
                    }
            }

            val inputs = ArrayList<TxIn>(inputCount.toInt())
            for (i in 0 until inputCount) {
                inputs.add(TxIn.readWithoutWitness(reader))
            }

            val outputCount = reader.readVarInt()
            val outputs = ArrayList<TxOut>(outputCount.toInt())
            for (i in 0 until outputCount) {
                outputs.add(TxOut.read(reader))
            }

            if (isSegwit) {
                for (i in inputs.indices) {
                    val itemCount = reader.readVarInt()
                    val items = ArrayList<ByteArray>(itemCount.toInt())
                    for (j in 0 until itemCount) {
                        items.add(reader.readVarBytes())
                    }
                    inputs[i] = inputs[i].copy(witness = items)
                }
            }

            val lockTime = reader.readUInt32LE()
            return BitcoinTransaction(version, inputs, outputs, lockTime)
        }
    }
}
