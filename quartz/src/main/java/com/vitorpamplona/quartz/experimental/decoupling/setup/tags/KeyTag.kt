/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.decoupling.setup.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class KeyTag(
    val pubkey: HexKey,
    val nonce: HexKey,
) {
    fun toTagArray() = assemble(pubkey, nonce)

    companion object {
        const val TAG_NAME = "n"

        fun isSameKey(
            tag1: Array<String>,
            tag2: Array<String>,
        ): Boolean {
            ensure(tag1.has(1)) { return false }
            ensure(tag2.has(1)) { return false }
            ensure(tag1[0] == tag2[0]) { return false }
            ensure(tag1[1] == tag2[1]) { return false }
            return true
        }

        @JvmStatic
        fun parse(tag: Array<String>): KeyTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            return KeyTag(tag[1], tag[2])
        }

        @JvmStatic
        fun assemble(
            key: HexKey,
            nonce: HexKey,
        ) = arrayOf(TAG_NAME, key, nonce)
    }
}
