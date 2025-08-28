/**
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

import com.vitorpamplona.quartz.utils.RandomInstance
import java.util.Base64
import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class BloomFilterMurMur3(
    private val size: Int,
    private val rounds: Int,
    private val bits: BitSet = BitSet(size),
    private val commonSalt: Int = RandomInstance.int(),
) {
    private val hasher = MurmurHash3()
    private val lock = ReentrantReadWriteLock()

    fun add(
        value: ByteArray,
        salt: Int = commonSalt,
    ) {
        lock.write {
            repeat(rounds) {
                bits.set(hash(value, salt + it))
            }
        }
    }

    fun mightContain(
        value: ByteArray,
        salt: Int = commonSalt,
    ): Boolean {
        lock.read {
            repeat(rounds) {
                if (!bits.get(hash(value, salt + it))) return false
            }
            return true
        }
    }

    private fun hash(
        value: ByteArray,
        seed: Int,
    ) = hasher.hash(value, seed).mod(size)

    fun encode() = encode(this)

    fun printBits() = bits.printBits()

    companion object {
        fun encode(f: BloomFilterMurMur3): String {
            val bitSetB64 = Base64.getEncoder().encodeToString(f.bits.toByteArray())
            return "${f.size}:${f.rounds}:$bitSetB64:${f.commonSalt}"
        }

        fun decode(encodedStr: String): BloomFilterMurMur3 {
            val (sizeStr, roundsStr, filterB64, salt) = encodedStr.split(":")
            val bitSet = BitSet.valueOf(Base64.getDecoder().decode(filterB64))
            return BloomFilterMurMur3(sizeStr.toInt(), roundsStr.toInt(), bitSet, salt.toInt())
        }
    }
}
