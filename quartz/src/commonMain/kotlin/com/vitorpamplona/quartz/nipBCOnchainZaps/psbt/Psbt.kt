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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * One key-value record inside a PSBT map. [keyType] is the BIP-174 keytype
 * compact-size; [keyData] is whatever follows it (empty for all the field
 * types NIP-BC uses).
 */
class PsbtRecord(
    val keyType: Int,
    val keyData: ByteArray,
    val value: ByteArray,
) {
    val hasEmptyKeyData: Boolean get() = keyData.isEmpty()
}

/**
 * An ordered PSBT key-value map (global, per-input, or per-output). Unknown
 * records are preserved verbatim so serialization round-trips.
 */
class PsbtMap(
    val records: MutableList<PsbtRecord> = mutableListOf(),
) {
    /** First value for [keyType] with empty key data, or null. */
    fun get(keyType: Int): ByteArray? = records.firstOrNull { it.keyType == keyType && it.hasEmptyKeyData }?.value

    /** Insert or replace the (empty-key-data) record for [keyType]. */
    fun put(
        keyType: Int,
        value: ByteArray,
    ) {
        val idx = records.indexOfFirst { it.keyType == keyType && it.hasEmptyKeyData }
        val record = PsbtRecord(keyType, ByteArray(0), value)
        if (idx >= 0) records[idx] = record else records.add(record)
    }

    /** Remove the (empty-key-data) record for [keyType], if present. */
    fun remove(keyType: Int) {
        records.removeAll { it.keyType == keyType && it.hasEmptyKeyData }
    }

    fun write(writer: BitcoinWriter) {
        for (record in records) {
            val key = BitcoinWriter()
            key.writeVarInt(record.keyType.toLong())
            key.writeBytes(record.keyData)
            writer.writeVarBytes(key.toByteArray())
            writer.writeVarBytes(record.value)
        }
        writer.writeByte(0x00) // map separator
    }

    companion object {
        fun read(reader: BitcoinReader): PsbtMap {
            val map = PsbtMap()
            while (true) {
                val keyLen = reader.readVarInt()
                if (keyLen == 0L) break // separator
                val keyBytes = reader.readBytes(keyLen.toInt())
                val keyReader = BitcoinReader(keyBytes)
                val keyType = keyReader.readVarInt().toInt()
                val keyData = keyReader.readBytes(keyReader.remaining)
                val value = reader.readVarBytes()
                map.records.add(PsbtRecord(keyType, keyData, value))
            }
            return map
        }
    }
}

/**
 * A Partially Signed Bitcoin Transaction ([BIP-174](https://github.com/bitcoin/bips/blob/master/bip-0174.mediawiki)).
 *
 * This is a deliberately small subset — enough to construct, sign, and
 * finalize the single-key-path P2TR spends NIP-BC needs. Unknown records are
 * preserved verbatim so the container round-trips even when fields aren't
 * modeled.
 *
 * This `psbt/` package is intentionally hand-rolled rather than delegated to a
 * Bitcoin library. That is a recorded architecture decision — see
 * `amethyst/plans/2026-05-14-onchain-zaps.md` ("Architecture decision:
 * hand-rolled Bitcoin consensus code"). It holds only while the scope stays at
 * single-key-path P2TR; expanding past that should revisit the decision.
 */
class Psbt(
    val global: PsbtMap,
    val inputs: MutableList<PsbtMap>,
    val outputs: MutableList<PsbtMap>,
) {
    /** The unsigned transaction from `PSBT_GLOBAL_UNSIGNED_TX`. */
    val unsignedTx: BitcoinTransaction by lazy {
        val raw =
            global.get(PSBT_GLOBAL_UNSIGNED_TX)
                ?: throw PsbtParseException("PSBT has no unsigned transaction")
        BitcoinTransaction.parse(raw)
    }

    fun serialize(): ByteArray {
        val w = BitcoinWriter()
        w.writeBytes(MAGIC)
        global.write(w)
        inputs.forEach { it.write(w) }
        outputs.forEach { it.write(w) }
        return w.toByteArray()
    }

    fun toHex(): String = serialize().toHexKey()

    companion object {
        val MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xFF.toByte())

        // Global keytypes.
        const val PSBT_GLOBAL_UNSIGNED_TX = 0x00
        const val PSBT_GLOBAL_VERSION = 0xFB

        // Per-input keytypes.
        const val PSBT_IN_NON_WITNESS_UTXO = 0x00
        const val PSBT_IN_WITNESS_UTXO = 0x01
        const val PSBT_IN_SIGHASH_TYPE = 0x03
        const val PSBT_IN_TAP_KEY_SIG = 0x13
        const val PSBT_IN_TAP_INTERNAL_KEY = 0x17

        // Per-output keytypes.
        const val PSBT_OUT_TAP_INTERNAL_KEY = 0x05

        fun parse(hex: String): Psbt = parse(hex.hexToByteArray())

        fun parse(bytes: ByteArray): Psbt {
            val reader = BitcoinReader(bytes)
            val magic = reader.readBytes(5)
            if (!magic.contentEquals(MAGIC)) {
                throw PsbtParseException("Not a PSBT: bad magic ${magic.toHexKey()}")
            }

            val global = PsbtMap.read(reader)
            val rawTx =
                global.get(PSBT_GLOBAL_UNSIGNED_TX)
                    ?: throw PsbtParseException("PSBT global map has no unsigned transaction")
            val tx = BitcoinTransaction.parse(rawTx)

            val inputs = ArrayList<PsbtMap>(tx.inputs.size)
            for (i in tx.inputs.indices) {
                inputs.add(PsbtMap.read(reader))
            }

            val outputs = ArrayList<PsbtMap>(tx.outputs.size)
            for (i in tx.outputs.indices) {
                outputs.add(PsbtMap.read(reader))
            }

            return Psbt(global, inputs, outputs)
        }

        /** Build an unsigned PSBT shell from a transaction with empty input/output maps. */
        fun fromUnsignedTx(tx: BitcoinTransaction): Psbt {
            require(!tx.hasWitness) { "unsigned tx must not carry witness data" }
            val global = PsbtMap()
            global.put(PSBT_GLOBAL_UNSIGNED_TX, tx.serializeForId())
            val inputs = MutableList(tx.inputs.size) { PsbtMap() }
            val outputs = MutableList(tx.outputs.size) { PsbtMap() }
            return Psbt(global, inputs, outputs)
        }
    }
}

// ---------------------------------------------------------------------------
// Typed accessors for the fields NIP-BC's key-path P2TR flow touches.
// ---------------------------------------------------------------------------

/** The witness UTXO (`PSBT_IN_WITNESS_UTXO`) being spent by input [index]. */
fun Psbt.inputWitnessUtxo(index: Int): TxOut? = inputs[index].get(Psbt.PSBT_IN_WITNESS_UTXO)?.let { TxOut.read(BitcoinReader(it)) }

fun Psbt.setInputWitnessUtxo(
    index: Int,
    output: TxOut,
) {
    val w = BitcoinWriter()
    output.write(w)
    inputs[index].put(Psbt.PSBT_IN_WITNESS_UTXO, w.toByteArray())
}

/** The 32-byte x-only taproot internal key for input [index]. */
fun Psbt.inputTapInternalKey(index: Int): ByteArray? = inputs[index].get(Psbt.PSBT_IN_TAP_INTERNAL_KEY)

fun Psbt.setInputTapInternalKey(
    index: Int,
    xOnlyPubKey: ByteArray,
) {
    require(xOnlyPubKey.size == 32) { "tap internal key must be 32 bytes" }
    inputs[index].put(Psbt.PSBT_IN_TAP_INTERNAL_KEY, xOnlyPubKey)
}

/** The 64- or 65-byte schnorr key-path signature for input [index]. */
fun Psbt.inputTapKeySig(index: Int): ByteArray? = inputs[index].get(Psbt.PSBT_IN_TAP_KEY_SIG)

fun Psbt.setInputTapKeySig(
    index: Int,
    signature: ByteArray,
) {
    require(signature.size == 64 || signature.size == 65) {
        "taproot key-path signature must be 64 or 65 bytes, got ${signature.size}"
    }
    inputs[index].put(Psbt.PSBT_IN_TAP_KEY_SIG, signature)
}

/** The optional BIP-174 sighash type for input [index] (4-byte LE), or null. */
fun Psbt.inputSighashType(index: Int): Int? = inputs[index].get(Psbt.PSBT_IN_SIGHASH_TYPE)?.let { BitcoinReader(it).readUInt32LE().toInt() }

fun Psbt.setInputSighashType(
    index: Int,
    sighashType: Int,
) {
    inputs[index].put(
        Psbt.PSBT_IN_SIGHASH_TYPE,
        BitcoinWriter().writeUInt32LE(sighashType.toLong()).toByteArray(),
    )
}

/** Optional taproot internal key on a change output. */
fun Psbt.setOutputTapInternalKey(
    index: Int,
    xOnlyPubKey: ByteArray,
) {
    require(xOnlyPubKey.size == 32) { "tap internal key must be 32 bytes" }
    outputs[index].put(Psbt.PSBT_OUT_TAP_INTERNAL_KEY, xOnlyPubKey)
}
