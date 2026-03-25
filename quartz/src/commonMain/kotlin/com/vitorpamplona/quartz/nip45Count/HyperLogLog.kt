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
package com.vitorpamplona.quartz.nip45Count

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.math.ln
import kotlin.math.pow

/**
 * NIP-45 HyperLogLog implementation for probabilistic cardinality estimation.
 *
 * Uses 256 registers (8-bit precision, p=8) as specified by NIP-45.
 * Each register is a single byte storing the maximum number of leading
 * zero bits + 1 observed for events mapping to that register.
 *
 * HLL values are transmitted as 512-character hex strings (256 bytes).
 */
object HyperLogLog {
    const val NUM_REGISTERS = 256

    // Bias correction constant for m=256: α = 0.7213 / (1 + 1.079/m)
    private val ALPHA_M: Double = 0.7213 / (1.0 + 1.079 / NUM_REGISTERS)

    /**
     * Merges multiple HLL register arrays by taking the maximum value
     * for each register position across all inputs.
     *
     * @param hlls List of 256-byte register arrays from different relays
     * @return Merged 256-byte register array
     */
    fun merge(hlls: List<ByteArray>): ByteArray {
        val merged = ByteArray(NUM_REGISTERS)
        for (hll in hlls) {
            for (i in 0 until NUM_REGISTERS) {
                val value = hll[i].toInt() and 0xFF
                val current = merged[i].toInt() and 0xFF
                if (value > current) {
                    merged[i] = value.toByte()
                }
            }
        }
        return merged
    }

    /**
     * Estimates the cardinality from an HLL register array using the
     * standard HyperLogLog algorithm with small/large range corrections.
     *
     * @param registers 256-byte HLL register array
     * @return Estimated cardinality
     */
    fun estimate(registers: ByteArray): Long {
        var harmonicSum = 0.0
        var zeroRegisters = 0

        for (i in 0 until NUM_REGISTERS) {
            val value = registers[i].toInt() and 0xFF
            harmonicSum += 2.0.pow(-value.toDouble())
            if (value == 0) zeroRegisters++
        }

        val m = NUM_REGISTERS.toDouble()
        var estimate = ALPHA_M * m * m / harmonicSum

        // Small range correction: use linear counting when estimate is small
        // and there are empty registers
        if (estimate <= 2.5 * m && zeroRegisters > 0) {
            estimate = m * ln(m / zeroRegisters.toDouble())
        }

        return estimate.toLong()
    }

    /**
     * Decodes a 512-character hex string into a 256-byte HLL register array.
     *
     * @param hex 512-character hex string
     * @return 256-byte register array, or null if the hex string is invalid
     */
    fun decode(hex: String): ByteArray? {
        if (hex.length != NUM_REGISTERS * 2) return null
        return try {
            Hex.decode(hex)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encodes a 256-byte HLL register array into a 512-character hex string.
     *
     * @param registers 256-byte register array
     * @return 512-character hex string
     */
    fun encode(registers: ByteArray): String = Hex.encode(registers)

    /**
     * Computes the deterministic offset for a given filter, as specified
     * by NIP-45. The offset determines which byte of event pubkeys is
     * used as the register index.
     *
     * Algorithm:
     * 1. Extract the first item from the filter's first tag attribute
     * 2. Convert to a 64-character hex string:
     *    - If already a 64-char hex (event ID or pubkey): use directly
     *    - If an address (kind:pubkey:dtag): extract the pubkey
     *    - Otherwise: SHA-256 hash the value
     * 3. Take the hex character at position 32
     * 4. Parse as base-16 and add 8
     *
     * @param filter The filter to compute offset for
     * @return The offset (8-23), or null if the filter has no tag attributes
     */
    fun computeOffset(filter: Filter): Int? {
        val firstTagValue = extractFirstTagValue(filter) ?: return null
        val hex64 = toHex64(firstTagValue) ?: return null
        val charAtPos32 = hex64[32]
        val hexValue = charAtPos32.digitToIntOrNull(16) ?: return null
        return hexValue + 8
    }

    /**
     * Extracts the first value from the first tag attribute in the filter.
     */
    private fun extractFirstTagValue(filter: Filter): String? {
        val tags = filter.tags ?: return null
        for ((_, values) in tags) {
            if (values.isNotEmpty()) {
                return values[0]
            }
        }
        return null
    }

    /**
     * Converts a tag value to a 64-character hex string.
     *
     * - If it's already a 64-char hex string: return as-is
     * - If it's a Nostr address (kind:pubkey:dtag): extract pubkey
     * - Otherwise: SHA-256 hash and hex-encode
     */
    private fun toHex64(value: String): String? {
        // Already a 64-char hex (event ID or pubkey)
        if (value.length == 64 && Hex.isHex(value)) {
            return value
        }

        // Try to parse as address (kind:pubkey:dtag)
        val address = Address.parse(value)
        if (address != null) {
            return address.pubKeyHex
        }

        // Fall back to SHA-256
        val hash = sha256(value.encodeToByteArray())
        return Hex.encode(hash)
    }
}
