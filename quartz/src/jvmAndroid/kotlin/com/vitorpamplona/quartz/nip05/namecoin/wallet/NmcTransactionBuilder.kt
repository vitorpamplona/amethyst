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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Hex
import fr.acinq.secp256k1.Secp256k1
import java.io.ByteArrayOutputStream

/**
 * Builds and signs raw Namecoin transactions.
 *
 * Namecoin transactions follow the Bitcoin transaction format exactly,
 * with the addition of name operation scripts in outputs. This builder
 * handles P2PKH inputs, standard outputs, and name-operation outputs.
 *
 * Uses ECDSA signing via the ACINQ secp256k1 library (same library
 * Amethyst already uses for Schnorr).
 */
class NmcTransactionBuilder {
    private val inputs = mutableListOf<TxInput>()
    private val outputs = mutableListOf<TxOutput>()

    data class TxInput(
        val prevTxHash: ByteArray, // 32 bytes, internal byte order (reversed from display)
        val prevIndex: Int,
        val scriptSig: ByteArray = byteArrayOf(),
        val sequence: Long = 0xFFFFFFFFL,
        /** Needed for signing: the scriptPubKey of the UTXO being spent */
        val prevScriptPubKey: ByteArray = byteArrayOf(),
        /** Needed for signing: the value of the UTXO being spent */
        val prevValue: Long = 0,
    )

    data class TxOutput(
        val value: Long, // in satoshis
        val scriptPubKey: ByteArray,
    )

    fun addInput(
        prevTxHash: String, // hex, display order (big-endian)
        prevIndex: Int,
        prevScriptPubKey: ByteArray,
        prevValue: Long,
        sequence: Long = 0xFFFFFFFFL,
    ): NmcTransactionBuilder {
        val hashBytes = Hex.decode(prevTxHash).reversedArray()
        inputs.add(
            TxInput(
                prevTxHash = hashBytes,
                prevIndex = prevIndex,
                prevScriptPubKey = prevScriptPubKey,
                prevValue = prevValue,
                sequence = sequence,
            ),
        )
        return this
    }

    /** Add a standard P2PKH output sending NMC to an address. */
    fun addP2PKHOutput(
        address: String,
        valueSatoshis: Long,
    ): NmcTransactionBuilder {
        val hash160 =
            NmcKeyManager.addressToHash160(address)
                ?: throw IllegalArgumentException("Invalid Namecoin address: $address")
        outputs.add(TxOutput(valueSatoshis, buildP2PKHScript(hash160)))
        return this
    }

    /** Add a raw script output (used for name operations). */
    fun addScriptOutput(
        scriptPubKey: ByteArray,
        valueSatoshis: Long,
    ): NmcTransactionBuilder {
        outputs.add(TxOutput(valueSatoshis, scriptPubKey))
        return this
    }

    /**
     * Sign all inputs and produce the raw transaction hex.
     *
     * Uses SIGHASH_ALL for all inputs. Each input is signed with
     * the corresponding private key.
     *
     * @param privKeys One private key per input
     * @return Raw signed transaction hex, ready for broadcast
     */
    fun sign(privKeys: List<ByteArray>): String {
        require(privKeys.size == inputs.size) { "Need one private key per input" }
        val secp = Secp256k1.get()

        // For each input, compute the signature
        val signedInputs =
            inputs.mapIndexed { idx, input ->
                // Compute sighash for this input
                val sighash = computeSighash(idx)
                // ECDSA sign (compact format)
                val compactSig = secp.sign(sighash, privKeys[idx])
                // Convert to DER format
                val derSig = secp.compact2der(compactSig)
                // Append SIGHASH_ALL byte
                val sigWithHashType = derSig + byteArrayOf(0x01)
                // Build scriptSig: <sig> <compressed_pubkey>
                val pubKey = NmcKeyManager.compressedPubKey(privKeys[idx])
                val scriptSig = pushData(sigWithHashType) + pushData(pubKey)
                input.copy(scriptSig = scriptSig)
            }

        return serializeTransaction(signedInputs, outputs).toHexKey()
    }

    /**
     * Compute SIGHASH_ALL for a specific input.
     */
    private fun computeSighash(inputIndex: Int): ByteArray {
        val stream = ByteArrayOutputStream()

        // Version
        writeInt32LE(stream, 1)

        // Inputs
        writeVarInt(stream, inputs.size.toLong())
        inputs.forEachIndexed { idx, input ->
            stream.write(input.prevTxHash)
            writeInt32LE(stream, input.prevIndex)
            if (idx == inputIndex) {
                // Current input gets the previous output's script
                writeVarInt(stream, input.prevScriptPubKey.size.toLong())
                stream.write(input.prevScriptPubKey)
            } else {
                // Other inputs get empty scripts
                writeVarInt(stream, 0)
            }
            writeInt32LE(stream, input.sequence.toInt())
        }

        // Outputs
        writeVarInt(stream, outputs.size.toLong())
        outputs.forEach { output ->
            writeInt64LE(stream, output.value)
            writeVarInt(stream, output.scriptPubKey.size.toLong())
            stream.write(output.scriptPubKey)
        }

        // Locktime
        writeInt32LE(stream, 0)
        // Sighash type (SIGHASH_ALL = 1)
        writeInt32LE(stream, 1)

        return NmcKeyManager.doubleSha256(stream.toByteArray())
    }

    private fun serializeTransaction(
        signedInputs: List<TxInput>,
        outputs: List<TxOutput>,
    ): ByteArray {
        val stream = ByteArrayOutputStream()

        // Version
        writeInt32LE(stream, 1)

        // Inputs
        writeVarInt(stream, signedInputs.size.toLong())
        signedInputs.forEach { input ->
            stream.write(input.prevTxHash)
            writeInt32LE(stream, input.prevIndex)
            writeVarInt(stream, input.scriptSig.size.toLong())
            stream.write(input.scriptSig)
            writeInt32LE(stream, input.sequence.toInt())
        }

        // Outputs
        writeVarInt(stream, outputs.size.toLong())
        outputs.forEach { output ->
            writeInt64LE(stream, output.value)
            writeVarInt(stream, output.scriptPubKey.size.toLong())
            stream.write(output.scriptPubKey)
        }

        // Locktime
        writeInt32LE(stream, 0)

        return stream.toByteArray()
    }

    companion object {
        // ── Script builders ────────────────────────────────────────────

        /** Build a standard P2PKH script: OP_DUP OP_HASH160 <20-byte-hash> OP_EQUALVERIFY OP_CHECKSIG */
        fun buildP2PKHScript(hash160: ByteArray): ByteArray {
            require(hash160.size == 20)
            return byteArrayOf(
                0x76, // OP_DUP
                0xa9.toByte(), // OP_HASH160
                0x14, // push 20 bytes
            ) + hash160 +
                byteArrayOf(
                    0x88.toByte(), // OP_EQUALVERIFY
                    0xac.toByte(), // OP_CHECKSIG
                )
        }

        /** Estimate transaction size for fee calculation. */
        fun estimateTxSize(
            numInputs: Int,
            numOutputs: Int,
            extraScriptBytes: Int = 0,
        ): Int {
            // Rough estimate: 10 (overhead) + 148 per input + 34 per output
            return 10 + (numInputs * 148) + (numOutputs * 34) + extraScriptBytes
        }

        // ── Encoding helpers ───────────────────────────────────────────

        fun pushData(data: ByteArray): ByteArray {
            val len = data.size
            return when {
                len < 0x4c -> {
                    byteArrayOf(len.toByte()) + data
                }

                len <= 0xff -> {
                    byteArrayOf(0x4c, len.toByte()) + data
                }

                len <= 0xffff -> {
                    byteArrayOf(0x4d, (len and 0xff).toByte(), ((len shr 8) and 0xff).toByte()) + data
                }

                else -> {
                    throw IllegalArgumentException("Data too large for pushData")
                }
            }
        }

        fun writeVarInt(
            stream: ByteArrayOutputStream,
            value: Long,
        ) {
            when {
                value < 0xfd -> {
                    stream.write(value.toInt())
                }

                value <= 0xffff -> {
                    stream.write(0xfd)
                    stream.write((value and 0xff).toInt())
                    stream.write(((value shr 8) and 0xff).toInt())
                }

                value <= 0xffffffffL -> {
                    stream.write(0xfe)
                    writeInt32LE(stream, value.toInt())
                }

                else -> {
                    stream.write(0xff)
                    writeInt64LE(stream, value)
                }
            }
        }

        fun writeInt32LE(
            stream: ByteArrayOutputStream,
            value: Int,
        ) {
            stream.write(value and 0xff)
            stream.write((value shr 8) and 0xff)
            stream.write((value shr 16) and 0xff)
            stream.write((value shr 24) and 0xff)
        }

        fun writeInt64LE(
            stream: ByteArrayOutputStream,
            value: Long,
        ) {
            for (i in 0..7) stream.write(((value shr (8 * i)) and 0xff).toInt())
        }
    }
}
