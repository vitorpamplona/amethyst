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

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

class BitcoinTxIdTag {
    companion object {
        const val TAG_NAME = "i"
        const val PREFIX = "bitcoin:tx:"
        const val TXID_LENGTH = 64

        fun isTag(tag: Array<String>) =
            tag.has(1) &&
                tag[0] == TAG_NAME &&
                tag[1].startsWith(PREFIX) &&
                tag[1].length == PREFIX.length + TXID_LENGTH

        fun isTagged(
            tag: Array<String>,
            txid: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == assembleScope(txid)

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].startsWith(PREFIX)) { return null }

            val txid = tag[1].substring(PREFIX.length)
            ensure(txid.length == TXID_LENGTH) { return null }
            ensure(txid == txid.lowercase()) { return null }
            ensure(Hex.isHex(txid)) { return null }

            return txid
        }

        fun parseScope(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].startsWith(PREFIX)) { return null }
            return tag[1]
        }

        fun assembleScope(txid: String) = PREFIX + txid.lowercase()

        fun assemble(txid: String) = arrayOf(TAG_NAME, assembleScope(txid))
    }
}
