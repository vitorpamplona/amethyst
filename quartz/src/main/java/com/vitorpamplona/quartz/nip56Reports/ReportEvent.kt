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
package com.vitorpamplona.quartz.nip56Reports

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable data class ReportedKey(
    val key: String,
    val reportType: ReportEvent.ReportType,
)

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
    private fun defaultReportType(): ReportType {
        // Works with old and new structures for report.
        var reportType =
            tags
                .filter { it.firstOrNull() == "report" }
                .mapNotNull { it.getOrNull(1) }
                .map { ReportType.valueOf(it.uppercase()) }
                .firstOrNull()
        if (reportType == null) {
            reportType =
                tags.mapNotNull { it.getOrNull(2) }.map { ReportType.valueOf(it.uppercase()) }.firstOrNull()
        }
        if (reportType == null) {
            reportType = ReportType.SPAM
        }
        return reportType
    }

    fun reportedPost() =
        tags
            .filter { it.size > 1 && it[0] == "e" }
            .map {
                ReportedKey(
                    it[1],
                    it.getOrNull(2)?.uppercase()?.let { it1 -> ReportType.valueOf(it1) }
                        ?: defaultReportType(),
                )
            }

    fun reportedAuthor() =
        tags
            .filter { it.size > 1 && it[0] == "p" }
            .map {
                ReportedKey(
                    it[1],
                    it.getOrNull(2)?.uppercase()?.let { it1 -> ReportType.valueOf(it1) }
                        ?: defaultReportType(),
                )
            }

    companion object {
        const val KIND = 1984

        fun create(
            reportedPost: Event,
            type: ReportType,
            signer: NostrSigner,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            onReady: (ReportEvent) -> Unit,
        ) {
            val reportPostTag = arrayOf("e", reportedPost.id, type.name.lowercase())
            val reportAuthorTag = arrayOf("p", reportedPost.pubKey, type.name.lowercase())

            var tags: Array<Array<String>> = arrayOf(reportPostTag, reportAuthorTag)

            if (reportedPost is AddressableEvent) {
                tags += listOf(arrayOf("a", reportedPost.address().toTag()))
            }

            tags += listOf(arrayOf("alt", "Report for ${type.name}"))

            signer.sign(createdAt, KIND, tags, content, onReady)
        }

        fun create(
            reportedUser: String,
            type: ReportType,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReportEvent) -> Unit,
        ) {
            val content = ""

            val reportAuthorTag = arrayOf("p", reportedUser, type.name.lowercase())
            val alt = arrayOf("alt", "Report for ${type.name}")

            val tags: Array<Array<String>> = arrayOf(reportAuthorTag, alt)
            signer.sign(createdAt, KIND, tags, content, onReady)
        }
    }

    enum class ReportType {
        EXPLICIT, // Not used anymore.
        ILLEGAL,
        SPAM,
        IMPERSONATION,
        NUDITY,
        PROFANITY,
        MALWARE,
        MOD,
        OTHER,
    }
}
