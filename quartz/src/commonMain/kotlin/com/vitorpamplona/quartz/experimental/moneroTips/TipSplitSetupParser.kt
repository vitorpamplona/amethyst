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
package com.vitorpamplona.quartz.experimental.moneroTips

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class TipSplitSetupParser {
    companion object {
        const val TAG_NAME = "monero"

        fun isTagged(tags: Array<String>) = tags.has(1) && tags[0] == TAG_NAME

        fun parse(tags: Array<String>): TipSplitSetup? {
            ensure(tags.has(1)) { return null }
            ensure(tags[0] == TAG_NAME) { return null }

            val isAddress = tags[1].length == 95

            val weight: Double?
            val relay: String?

            if (isAddress) {
                relay = null
                weight = tags.getOrNull(2)?.toDoubleOrNull()
            } else {
                relay = tags.getOrNull(2)
                weight = tags.getOrNull(3)?.toDoubleOrNull()
            }

            return if (weight == null || weight > 0) {
                TipSplitSetup(
                    addressOrPubKeyHex = tags[1],
                    relay = relay,
                    weight = weight,
                    isAddress = isAddress,
                )
            } else {
                null
            }
        }
    }
}
