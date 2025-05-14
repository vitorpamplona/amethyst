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
package com.vitorpamplona.quartz.nip57Zaps.splits

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class ZapSplitSetupParser {
    companion object {
        @JvmStatic
        fun isTagged(tags: Array<String>) = tags.has(1) && tags[0] == BaseZapSplitSetup.TAG_NAME

        @JvmStatic
        fun parse(tags: Array<String>): BaseZapSplitSetup? {
            ensure(tags.has(1)) { return null }
            ensure(tags[0] == BaseZapSplitSetup.TAG_NAME) { return null }

            val isLnAddress = tags[1].contains("@") || tags[1].startsWith("LNURL", true)
            val weight = if (isLnAddress) 1.0 else (tags.getOrNull(3)?.toDoubleOrNull() ?: 0.0)

            return if (weight > 0) {
                if (isLnAddress) {
                    ZapSplitSetupLnAddress(tags[1], 1.0)
                } else {
                    ZapSplitSetup(
                        tags[1],
                        tags.getOrNull(2),
                        weight,
                    )
                }
            } else {
                null
            }
        }
    }
}
