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
package com.vitorpamplona.quartz.nip56Reports

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip56Reports.tags.DefaultReportTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAddressTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAuthorTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedEventTag
import com.vitorpamplona.quartz.utils.TimeUtils

// NIP 56 event.
@Immutable
class ReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var defaultType: ReportType? = null

    private fun defaultReportTypes() = tags.mapNotNull(DefaultReportTag::parse)

    private fun defaultReportType(): ReportType {
        defaultType?.let { return it }

        // search for any type in any tag.
        val reportType =
            defaultReportTypes().firstOrNull()
                ?: tags.firstNotNullOfOrNull {
                    ReportedAuthorTag.parse(it)?.type
                        ?: ReportedEventTag.parse(it)?.type
                        ?: ReportedAddressTag.parse(it)?.type
                } ?: ReportType.SPAM

        defaultType = reportType
        return reportType
    }

    fun reportedPost() = tags.mapNotNull { ReportedEventTag.parse(it, defaultReportType()) }

    fun reportedAddresses() = tags.mapNotNull { ReportedAddressTag.parse(it, defaultReportType()) }

    fun reportedAuthor() = tags.mapNotNull { ReportedAuthorTag.parse(it, defaultReportType()) }

    companion object {
        const val KIND = 1984
        const val ALT_PREFIX = "Report for "

        fun build(
            reportedPost: Event,
            type: ReportType,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, comment, createdAt) {
            alt(ALT_PREFIX + type.code)
            event(reportedPost.id, type)
            user(reportedPost.pubKey, type)

            if (reportedPost is AddressableEvent) {
                address(reportedPost.address(), type)
            }
        }

        fun build(
            reportedUser: HexKey,
            type: ReportType,
            comment: String = "",
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate(KIND, comment, createdAt) {
            alt(ALT_PREFIX + type.code)
            user(reportedUser, type)
        }
    }
}
