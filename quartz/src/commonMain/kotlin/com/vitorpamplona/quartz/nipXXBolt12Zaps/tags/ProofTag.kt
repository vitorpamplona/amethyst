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
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `proof` tag: the bech32-encoded BOLT12 `lnp` **payer proof** for the settled
 * payment. Only present on the zap event (kind 9736), never on the intent.
 *
 * The proof is decoded and cryptographically checked by the validator; this tag
 * class only guards the surface syntax (`lnp1...`).
 */
class ProofTag {
    companion object {
        const val TAG_NAME = "proof"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && Bolt12Bech32.isPayerProof(tag[1])

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(Bolt12Bech32.isPayerProof(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(payerProof: String) = arrayOf(TAG_NAME, payerProof)
    }
}
