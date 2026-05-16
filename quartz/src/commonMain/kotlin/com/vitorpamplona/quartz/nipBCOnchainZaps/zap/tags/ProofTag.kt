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
package com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

@Immutable
data class ProofTag(
    val rawTxHex: String,
    val merkleProofHex: String,
) {
    fun toTagArray() = assemble(rawTxHex, merkleProofHex)

    companion object {
        const val TAG_NAME = "proof"

        fun isTag(tag: Array<String>) =
            tag.has(2) &&
                tag[0] == TAG_NAME &&
                tag[1].isNotEmpty()

        fun parse(tag: Array<String>): ProofTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(Hex.isHex(tag[1])) { return null }
            ensure(Hex.isHex(tag[2])) { return null }

            return ProofTag(tag[1].lowercase(), tag[2].lowercase())
        }

        fun assemble(
            rawTxHex: String,
            merkleProofHex: String,
        ) = arrayOf(TAG_NAME, rawTxHex.lowercase(), merkleProofHex.lowercase())

        fun assemble(proof: ProofTag) = assemble(proof.rawTxHex, proof.merkleProofHex)
    }
}
