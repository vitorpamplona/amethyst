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
package com.vitorpamplona.quartz.nipBCOnchainZaps.taproot

import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32

/**
 * BIP-173 / BIP-350 segwit native address encoder/decoder.
 *
 * NIP-BC uses witness version 1 (taproot) with a 32-byte program, encoded as
 * bech32m with HRP `bc` (Bitcoin mainnet only).
 */
object SegwitAddress {
    /** Mainnet human-readable prefix. */
    const val HRP_MAINNET = "bc"

    /** Taproot witness version. */
    const val TAPROOT_WITNESS_VERSION = 1

    /**
     * Encode a witness program to a segwit address.
     *
     * @param hrp Human-readable prefix (`bc` for mainnet).
     * @param witnessVersion Witness version (0 for v0, 1 for taproot).
     * @param program Witness program bytes (32 bytes for v1 taproot).
     */
    fun encode(
        hrp: String,
        witnessVersion: Int,
        program: ByteArray,
    ): String {
        require(witnessVersion in 0..16) { "invalid witness version $witnessVersion" }
        require(program.size in 2..40) { "invalid witness program length ${program.size}" }
        if (witnessVersion == 0) {
            require(program.size == 20 || program.size == 32) {
                "witness v0 program must be 20 or 32 bytes (got ${program.size})"
            }
        }

        val data = ArrayList<Byte>(1 + program.size * 2)
        data.add(witnessVersion.toByte())
        data.addAll(Bech32.eight2five(program))

        val encoding =
            if (witnessVersion == 0) Bech32.Encoding.Bech32 else Bech32.Encoding.Bech32m

        return Bech32.encode(hrp, ArrayList(data), encoding)
    }

    /** Encode a taproot (witness v1) output key as a `bc1p...` address. */
    fun encodeP2TR(
        outputKey: ByteArray,
        hrp: String = HRP_MAINNET,
    ): String {
        require(outputKey.size == 32) {
            "taproot output key must be 32 bytes (got ${outputKey.size})"
        }
        return encode(hrp, TAPROOT_WITNESS_VERSION, outputKey)
    }

    /**
     * Decoded segwit address.
     *
     * @property hrp Human-readable prefix.
     * @property witnessVersion Witness version (0-16).
     * @property program Witness program bytes.
     */
    data class Decoded(
        val hrp: String,
        val witnessVersion: Int,
        val program: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return hrp == other.hrp &&
                witnessVersion == other.witnessVersion &&
                program.contentEquals(other.program)
        }

        override fun hashCode(): Int {
            var result = hrp.hashCode()
            result = 31 * result + witnessVersion
            result = 31 * result + program.contentHashCode()
            return result
        }
    }

    /** Decode a segwit address, validating HRP, version, encoding, and program length. */
    fun decode(address: String): Decoded {
        val (hrp, data, encoding) = Bech32.decode(address)
        require(data.isNotEmpty()) { "empty data" }

        val witnessVersion = data[0].toInt() and 0x1f
        require(witnessVersion in 0..16) { "invalid witness version $witnessVersion" }

        val expectedEncoding =
            if (witnessVersion == 0) Bech32.Encoding.Bech32 else Bech32.Encoding.Bech32m
        require(encoding == expectedEncoding) {
            "wrong checksum encoding for witness version $witnessVersion"
        }

        val program = Bech32.five2eight(data, 1)
        require(program.size in 2..40) { "invalid witness program length ${program.size}" }
        if (witnessVersion == 0) {
            require(program.size == 20 || program.size == 32) {
                "witness v0 program must be 20 or 32 bytes"
            }
        }

        return Decoded(hrp, witnessVersion, program)
    }
}
