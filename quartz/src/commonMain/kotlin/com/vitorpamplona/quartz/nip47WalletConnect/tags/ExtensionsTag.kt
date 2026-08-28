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
package com.vitorpamplona.quartz.nip47WalletConnect.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-47's `extensions` tag on the kind 13194 info event: the optional NWC
 * extension specs a wallet service supports, space-separated (eg. `02 03 04`).
 *
 * This is how a client learns it may use anything beyond the core command set
 * without guessing. It matters most for request fields a wallet might not
 * understand — sending one to a wallet that never advertised support risks a
 * refusal on a method that would otherwise have worked.
 */
class ExtensionsTag {
    companion object {
        const val TAG_NAME = "extensions"

        // The specs this client knows how to use, so a caller names a constant
        // rather than a bare string at each gate.
        const val TRANSACTION_HISTORY = "05"
        const val METADATA_CONVENTIONS = "06"

        fun parse(tag: Array<String>): List<String>? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag.drop(1)
        }

        fun assemble(extensions: List<String>) = arrayOf(TAG_NAME, *extensions.toTypedArray())
    }
}
