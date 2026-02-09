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
package com.vitorpamplona.quartz.nip01Core.hints.bloom

import com.vitorpamplona.quartz.utils.BitSet
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.bitSetValueOf
import kotlin.io.encoding.Base64

/**
 * Info: BitSet is not threadsafe for data consistency, but this
 * application of the set doesn't require data consistency since
 * it only sets bits to true, instead of flipping them back and
 * forth between true and false.
 */
class BloomFilterMurMur3(
    private val size: Int,
    private val rounds: Int,
    private val bits: BitSet = BitSet(size),
    private val commonSalt: Int = RandomInstance.int(),
) {
    private val hasher = MurmurHash3()

    fun add(
        value: ByteArray,
        salt: Int = commonSalt,
    ) {
        repeat(rounds) { round ->
            bits.set(hash(value, salt + round))
        }
    }

    fun mightContain(
        value: ByteArray,
        salt: Int = commonSalt,
    ): Boolean {
        repeat(rounds) { round ->
            if (!bits.get(hash(value, salt + round))) {
                return false
            }
        }
        return true
    }

    private fun hash(
        value: ByteArray,
        seed: Int,
    ) = hasher.hash(value, seed).mod(size)

    fun encode() = encode(this)

    fun printBits() = bits.printBits()

    companion object {
        fun encode(f: BloomFilterMurMur3): String {
            val bitSetB64 = Base64.encode(f.bits.toByteArray())
            return "${f.size}:${f.rounds}:$bitSetB64:${f.commonSalt}"
        }

        fun decode(encodedStr: String): BloomFilterMurMur3 {
            val (sizeStr, roundsStr, filterB64, salt) = encodedStr.split(":")
            val bitSet = bitSetValueOf(Base64.decode(filterB64))
            return BloomFilterMurMur3(sizeStr.toInt(), roundsStr.toInt(), bitSet, salt.toInt())
        }
    }
}
