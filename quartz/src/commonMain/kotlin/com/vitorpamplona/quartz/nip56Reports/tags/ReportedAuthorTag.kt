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
package com.vitorpamplona.quartz.nip56Reports.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

@Immutable
class ReportedAuthorTag(
    val pubkey: HexKey,
    override val type: ReportType? = null,
) : BaseReportTag {
    fun toTagArray() = assemble(pubkey, type)

    companion object {
        const val TAG_NAME = "p"

        fun parse(
            tag: Array<String>,
            defaultReportType: ReportType? = null,
        ): ReportedAuthorTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val type =
                if (tag.size == 2) {
                    defaultReportType
                } else if (tag.size == 3) {
                    ReportType.parseOrNull(tag[2], tag) ?: defaultReportType
                } else {
                    ReportType.parseOrNull(tag[3], tag) ?: defaultReportType
                }

            return ReportedAuthorTag(tag[1], type)
        }

        fun assemble(
            pubkey: HexKey,
            type: ReportType? = null,
        ) = arrayOfNotNull(TAG_NAME, pubkey, type?.code)
    }
}
