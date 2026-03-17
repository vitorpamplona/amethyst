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

import kotlinx.serialization.Serializable

/**
 * Supported Namecoin address types.
 *
 * - **P2PKH**: Legacy addresses starting with 'N' (version byte 52).
 *   Most compatible, required for name operations.
 * - **P2SH_P2WPKH**: Wrapped SegWit addresses starting with '6' (version byte 13).
 *   Better fees than P2PKH, compatible with most wallets.
 * - **P2WPKH**: Native SegWit (bech32) addresses starting with 'nc1'.
 *   Best fees, but limited name operation support.
 */
@Serializable
enum class NmcAddressType(
    val displayName: String,
    val description: String,
) {
    P2PKH("Legacy (P2PKH)", "N... addresses — most compatible, required for name ops"),
    P2SH_P2WPKH("Wrapped SegWit (P2SH-P2WPKH)", "6... addresses — better fees, good compatibility"),
    P2WPKH("Native SegWit (P2WPKH)", "nc1... addresses — best fees, limited name op support"),
    ;

    /** Whether this address type supports Namecoin name operations. */
    val supportsNameOps: Boolean get() = this == P2PKH
}

/**
 * Address generation for all supported types.
 */
object NmcAddressGenerator {
    /** Generate an address of the given type from a compressed public key. */
    fun addressFromPubKey(
        compressedPubKey: ByteArray,
        type: NmcAddressType,
    ): String =
        when (type) {
            NmcAddressType.P2PKH -> NmcKeyManager.addressFromPubKey(compressedPubKey)
            NmcAddressType.P2SH_P2WPKH -> p2shP2wpkhAddress(compressedPubKey)
            NmcAddressType.P2WPKH -> bech32Address(compressedPubKey)
        }

    /** Generate an address of the given type from a private key. */
    fun addressFromPrivKey(
        privKey: ByteArray,
        type: NmcAddressType,
    ): String = addressFromPubKey(NmcKeyManager.compressedPubKey(privKey), type)

    /**
     * Build the scriptPubKey for a given address type and public key hash.
     */
    fun scriptPubKeyForType(
        pubKeyHash160: ByteArray,
        type: NmcAddressType,
    ): ByteArray =
        when (type) {
            NmcAddressType.P2PKH -> NmcTransactionBuilder.buildP2PKHScript(pubKeyHash160)
            NmcAddressType.P2SH_P2WPKH -> buildP2SHP2WPKHScript(pubKeyHash160)
            NmcAddressType.P2WPKH -> buildP2WPKHScript(pubKeyHash160)
        }

    // ── P2SH-P2WPKH (Wrapped SegWit) ──────────────────────────────────

    /**
     * P2SH-P2WPKH address: Hash160 of the witness program, wrapped in P2SH.
     * Witness program: OP_0 <20-byte-pubkey-hash>
     * P2SH wrapping: OP_HASH160 <Hash160(witness_program)> OP_EQUAL
     */
    private fun p2shP2wpkhAddress(compressedPubKey: ByteArray): String {
        val witnessProgram = witnessProgram(compressedPubKey)
        val scriptHash = NmcKeyManager.hash160(witnessProgram)
        return NmcKeyManager.base58CheckEncode(byteArrayOf(NmcKeyManager.NMC_SCRIPT_VERSION) + scriptHash)
    }

    /** Build the witness program: OP_0 <20-byte-hash160(pubkey)> */
    private fun witnessProgram(compressedPubKey: ByteArray): ByteArray {
        val pubKeyHash = NmcKeyManager.hash160(compressedPubKey)
        return byteArrayOf(0x00, 0x14) + pubKeyHash
    }

    /** Build P2SH-P2WPKH output script: OP_HASH160 <hash> OP_EQUAL */
    private fun buildP2SHP2WPKHScript(pubKeyHash160: ByteArray): ByteArray {
        val witnessProgram = byteArrayOf(0x00, 0x14) + pubKeyHash160
        val scriptHash = NmcKeyManager.hash160(witnessProgram)
        return byteArrayOf(
            0xa9.toByte(), // OP_HASH160
            0x14, // push 20 bytes
        ) + scriptHash +
            byteArrayOf(
                0x87.toByte(), // OP_EQUAL
            )
    }

    // ── P2WPKH (Native SegWit / Bech32) ───────────────────────────────

    /** Namecoin bech32 human-readable part. */
    private const val NMC_BECH32_HRP = "nc"

    /**
     * P2WPKH (native SegWit) address using bech32 encoding.
     * Format: nc1q<hash160>
     */
    private fun bech32Address(compressedPubKey: ByteArray): String {
        val pubKeyHash = NmcKeyManager.hash160(compressedPubKey)
        val converted =
            convertBits(pubKeyHash, 8, 5, true)
                ?: throw IllegalStateException("Bech32 bit conversion failed")
        // witness version 0 + converted data
        val data = byteArrayOf(0x00) + converted
        return bech32Encode(NMC_BECH32_HRP, data)
    }

    /** Build P2WPKH output script: OP_0 <20-byte-hash> */
    private fun buildP2WPKHScript(pubKeyHash160: ByteArray): ByteArray {
        require(pubKeyHash160.size == 20)
        return byteArrayOf(0x00, 0x14) + pubKeyHash160
    }

    // ── Bech32 Encoding ────────────────────────────────────────────────

    private const val BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    private fun bech32Polymod(values: IntArray): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = (chk and 0x1ffffff shl 5) xor v
            for (i in 0..4) {
                if ((top shr i) and 1 != 0) chk = chk xor gen[i]
            }
        }
        return chk
    }

    private fun bech32HrpExpand(hrp: String): IntArray {
        val result = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            result[i] = hrp[i].code shr 5
            result[i + hrp.length + 1] = hrp[i].code and 31
        }
        result[hrp.length] = 0
        return result
    }

    private fun bech32CreateChecksum(
        hrp: String,
        data: ByteArray,
    ): ByteArray {
        val values = bech32HrpExpand(hrp) + data.map { it.toInt() }.toIntArray() + intArrayOf(0, 0, 0, 0, 0, 0)
        val polymod = bech32Polymod(values) xor 1
        return ByteArray(6) { ((polymod shr (5 * (5 - it))) and 31).toByte() }
    }

    private fun bech32Encode(
        hrp: String,
        data: ByteArray,
    ): String {
        val checksum = bech32CreateChecksum(hrp, data)
        val combined = data + checksum
        val sb = StringBuilder(hrp.length + 1 + combined.size)
        sb.append(hrp)
        sb.append('1')
        for (b in combined) {
            sb.append(BECH32_CHARSET[b.toInt() and 31])
        }
        return sb.toString()
    }

    /**
     * Convert between bit widths (e.g., 8-bit to 5-bit for bech32).
     */
    private fun convertBits(
        data: ByteArray,
        fromBits: Int,
        toBits: Int,
        pad: Boolean,
    ): ByteArray? {
        var acc = 0
        var bits = 0
        val result = mutableListOf<Byte>()
        val maxv = (1 shl toBits) - 1
        for (b in data) {
            val value = b.toInt() and 0xff
            if (value shr fromBits != 0) return null
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) result.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }
        return result.toByteArray()
    }
}
