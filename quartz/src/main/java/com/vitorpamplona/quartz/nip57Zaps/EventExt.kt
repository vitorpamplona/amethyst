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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueAsLong
import com.vitorpamplona.quartz.nip01Core.core.hasTagWithContent
import com.vitorpamplona.quartz.nip01Core.core.mapTagged

fun Event.hasZapSplitSetup() = tags.hasTagWithContent("zap")

fun Event.zapSplitSetup(): List<ZapSplitSetup> =
    tags.mapTagged("zap") {
        val isLnAddress = it[0].contains("@") || it[0].startsWith("LNURL", true)
        val weight = if (isLnAddress) 1.0 else (it.getOrNull(3)?.toDoubleOrNull() ?: 0.0)

        if (weight > 0) {
            ZapSplitSetup(
                it[1],
                it.getOrNull(2),
                weight,
                isLnAddress,
            )
        } else {
            null
        }
    }

fun Event.zapraiserAmount() = tags.firstTagValueAsLong("zapraiser")
