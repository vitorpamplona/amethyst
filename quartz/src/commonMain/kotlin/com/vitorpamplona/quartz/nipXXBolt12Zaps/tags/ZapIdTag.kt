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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `zap_id` tag of a zap intent (kind 9737): a random value with at least
 * 128 bits of entropy, encoded as lowercase hex. It only needs to be present and
 * well-formed; the anti-replay binding is enforced by the payer note referencing
 * the intent's event id, not by this value itself.
 */
class ZapIdTag {
    companion object {
        const val TAG_NAME = "zap_id"

        /** 128 bits of entropy = 16 bytes = 32 lowercase-hex characters. */
        const val MIN_HEX_LENGTH = 32

        fun isValid(zapId: String) = zapId.length >= MIN_HEX_LENGTH && Hex.isHex(zapId) && zapId == zapId.lowercase()

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && isValid(tag[1])

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isValid(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(zapId: String) = arrayOf(TAG_NAME, zapId)
    }
}
