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
package com.vitorpamplona.quartz.nip13Pow.miner

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256

class PoWMiner(
    val buffer: MiningBuffer,
    val desiredPoW: Int,
) {
    val emptyBytesForDesiredPoW = desiredPoW / 8

    fun reachedDesiredPoW(byteArray: ByteArray) = PoWRankEvaluator.atLeastPowRank(sha256(byteArray), desiredPoW, emptyBytesForDesiredPoW)

    fun run() = runDigit(buffer.nonceStarts)

    private fun runDigit(index: Int): Boolean {
        for (testByte in VALID_BYTES) {
            // replaces the background base by the nonce integers
            buffer.bytes[index] = testByte

            if (index + 1 < buffer.nonceEnds) {
                if (runDigit(index + 1)) return true
            } else {
                if (reachedDesiredPoW(buffer.bytes)) return true
            }
        }
        return false
    }

    companion object {
        private const val STARTING_NONCE_SIZE = 5

        // make sure these chars are not escaped by the JSON stringifier
        private val VALID_CHARS: List<Char> =
            ('0'..'9') + ('a'..'z') + ('A'..'Z') + "-()[]{}$@!*=;:?,".toCharArray().toList()

        private val VALID_BYTES = VALID_CHARS.map { it.code.toByte() }

        private fun randomBase(size: Int): String = CharArray(size) { VALID_CHARS.random() }.concatToString()

        /**
         * The miner creates a stringified json template and changes the nonce directly in the UTF-8 ByteArray representation
         * to avoid having to recompute the json objects and stringify it.
         */
        fun <T : Event> run(
            template: EventTemplate<T>,
            pubKey: HexKey,
            desiredPoW: Int,
        ): EventTemplate<T> {
            var nextSize = STARTING_NONCE_SIZE

            do {
                val initialNonce = randomBase(nextSize)

                val bytes =
                    EventHasherSerializer
                        .fastMakeJsonForId(
                            pubKey = pubKey,
                            createdAt = template.createdAt,
                            kind = template.kind,
                            tags = template.tags + PoWTag.assemble(initialNonce, desiredPoW),
                            content = template.content,
                        )

                val startIndex = bytes.indexOf(initialNonce.encodeToByteArray())

                val buffer = MiningBuffer(bytes, startIndex, startIndex + nextSize)

                if (PoWMiner(buffer, desiredPoW).run()) {
                    return EventTemplate(
                        template.createdAt,
                        template.kind,
                        template.tags + PoWTag.assemble(buffer.nonce(), desiredPoW),
                        template.content,
                    )
                } else {
                    nextSize += STARTING_NONCE_SIZE
                }
            } while (nextSize < 50)

            throw RuntimeException("Could not find PoW")
        }
    }
}
