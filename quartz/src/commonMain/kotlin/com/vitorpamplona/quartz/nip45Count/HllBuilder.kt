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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.utils.Hex

/**
 * Accumulates a NIP-45 HyperLogLog register array from the pubkeys of the
 * events that match a COUNT filter, so a relay can answer with a mergeable
 * `hll` field instead of (or alongside) an exact count.
 *
 * Build one with [HyperLogLog.builderFor] (which derives the [offset] from the
 * filter), fold every matching event's pubkey in with [add], then read
 * [toCountResult] / [build]:
 *
 * ```
 * val hll = HyperLogLog.builderFor(filter) ?: return CountResult(exactCount)
 * store.query(filter) { event -> hll.add(event.pubKey) }
 * return hll.toCountResult()
 * ```
 *
 * Two relays counting the same corpus with the same filter produce register
 * arrays that merge (via [HyperLogLog.merge]) into a deduplicated estimate —
 * the whole point of NIP-45 HLL.
 */
class HllBuilder(
    val offset: Int,
) {
    private val registers = ByteArray(HyperLogLog.NUM_REGISTERS)

    /** Folds a 32-byte event pubkey into the registers. */
    fun add(pubKey: ByteArray): HllBuilder {
        HyperLogLog.addPubKey(registers, pubKey, offset)
        return this
    }

    /** Folds a 64-char hex event pubkey into the registers; no-op if malformed. */
    fun add(pubKeyHex: String): HllBuilder {
        val bytes =
            try {
                Hex.decode(pubKeyHex)
            } catch (_: Exception) {
                return this
            }
        return add(bytes)
    }

    /** A copy of the current 256-byte register array. */
    fun build(): ByteArray = registers.copyOf()

    /** The HyperLogLog cardinality estimate of the folded pubkeys. */
    fun estimate(): Int = HyperLogLog.estimate(registers).toInt()

    /** An approximate [CountResult] carrying both the estimate and the registers. */
    fun toCountResult(): CountResult = CountResult(estimate(), approximate = true, hll = build())

    companion object
}
