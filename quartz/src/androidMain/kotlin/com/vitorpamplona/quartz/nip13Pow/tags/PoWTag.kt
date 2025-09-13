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
package com.vitorpamplona.quartz.nip13Pow.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.ensure
import com.vitorpamplona.quartz.utils.pointerSizeInBytes

class PoWTag(
    val nonce: String,
    val commitment: Int?,
) {
    fun countMemory(): Int = 2 * pointerSizeInBytes + nonce.bytesUsedInMemory() + (commitment?.bytesUsedInMemory() ?: 0)

    fun toTagArray() = assemble(nonce, commitment)

    companion object {
        const val TAG_NAME = "nonce"

        @JvmStatic
        fun hasTagWithContent(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        @JvmStatic
        fun parse(tag: Array<String>): PoWTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return PoWTag(tag[1], tag.getOrNull(2)?.toIntOrNull())
        }

        @JvmStatic
        fun parseCommitment(tag: Array<String>): Int? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag.getOrNull(2)?.toIntOrNull()
        }

        @JvmStatic
        fun assemble(
            nonce: String,
            commitment: Int?,
        ) = arrayOfNotNull(TAG_NAME, nonce, commitment.toString())
    }
}
