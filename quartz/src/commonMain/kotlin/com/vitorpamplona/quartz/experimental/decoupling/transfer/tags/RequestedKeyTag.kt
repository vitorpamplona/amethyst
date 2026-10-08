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
package com.vitorpamplona.quartz.experimental.decoupling.transfer.tags

import com.vitorpamplona.quartz.experimental.decoupling.setup.tags.KeyTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * The encryption public key a kind 4454 request asks for: `["n", "<encryption-pubkey>"]`, the same
 * `n` tag name kind 10044 uses for its keys ([KeyTag]). PsstPsst profile: "When `n` is present, the
 * responder transfers that encryption key; otherwise it transfers its current key." Only the key
 * slot is read; a nonce slot, as [KeyTag] carries in 10044, is ignored here.
 */
class RequestedKeyTag {
    companion object {
        const val TAG_NAME = KeyTag.TAG_NAME

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            ensure(Hex.isHex64(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(encryptionPubKey: HexKey) = arrayOf(TAG_NAME, encryptionPubKey)
    }
}
