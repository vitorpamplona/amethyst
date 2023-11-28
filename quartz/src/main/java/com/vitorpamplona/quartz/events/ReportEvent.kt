package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
data class ReportedKey(val key: String, val reportType: ReportEvent.ReportType)

// NIP 56 event.
@Immutable
class ReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    private fun defaultReportType(): ReportType {
        // Works with old and new structures for report.
        var reportType = tags.filter { it.firstOrNull() == "report" }.mapNotNull { it.getOrNull(1) }.map { ReportType.valueOf(it.uppercase()) }.firstOrNull()
        if (reportType == null) {
            reportType = tags.mapNotNull { it.getOrNull(2) }.map { ReportType.valueOf(it.uppercase()) }.firstOrNull()
        }
        if (reportType == null) {
            reportType = ReportType.SPAM
        }
        return reportType
    }

    fun reportedPost() = tags
        .filter { it.size > 1 && it[0] == "e" }
        .map {
            ReportedKey(
                it[1],
                it.getOrNull(2)?.uppercase()?.let { it1 -> ReportType.valueOf(it1) } ?: defaultReportType()
            )
        }

    fun reportedAuthor() = tags
        .filter { it.size > 1 && it[0] == "p" }
        .map {
            ReportedKey(
                it[1],
                it.getOrNull(2)?.uppercase()?.let { it1 -> ReportType.valueOf(it1) } ?: defaultReportType()
            )
        }

    companion object {
        const val kind = 1984

        fun create(
            reportedPost: EventInterface,
            type: ReportType,
            signer: NostrSigner,
            content: String = "",
            createdAt: Long = TimeUtils.now(),
            onReady: (ReportEvent) -> Unit
        ) {
            val reportPostTag = arrayOf("e", reportedPost.id(), type.name.lowercase())
            val reportAuthorTag = arrayOf("p", reportedPost.pubKey(), type.name.lowercase())

            var tags: Array<Array<String>> = arrayOf(reportPostTag, reportAuthorTag)

            if (reportedPost is AddressableEvent) {
                tags = tags + listOf(arrayOf("a", reportedPost.address().toTag()))
            }

            signer.sign(createdAt, kind, tags, content, onReady)
        }

        fun create(
            reportedUser: String,
            type: ReportType,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ReportEvent) -> Unit
        ) {
            val content = ""

            val reportAuthorTag = arrayOf("p", reportedUser, type.name.lowercase())

            val tags: Array<Array<String>> = arrayOf(reportAuthorTag)
            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }

    enum class ReportType() {
        EXPLICIT, // Not used anymore.
        ILLEGAL,
        SPAM,
        IMPERSONATION,
        NUDITY,
        PROFANITY
    }
}
