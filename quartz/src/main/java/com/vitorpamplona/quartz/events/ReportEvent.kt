package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
data class ReportedKey(val key: String, val reportType: ReportEvent.ReportType)

// NIP 56 event.
@Immutable
class ReportEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
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
            keyPair: KeyPair,
            content: String = "",
            createdAt: Long = TimeUtils.now()
        ): ReportEvent {
            val reportPostTag = listOf("e", reportedPost.id(), type.name.lowercase())
            val reportAuthorTag = listOf("p", reportedPost.pubKey(), type.name.lowercase())

            val pubKey = keyPair.pubKey.toHexKey()
            var tags: List<List<String>> = listOf(reportPostTag, reportAuthorTag)

            if (reportedPost is AddressableEvent) {
                tags = tags + listOf(listOf("a", reportedPost.address().toTag()))
            }

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return ReportEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(reportedUser: String, type: ReportType, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ReportEvent {
            val content = ""

            val reportAuthorTag = listOf("p", reportedUser, type.name.lowercase())

            val pubKey = keyPair.pubKey.toHexKey()
            val tags: List<List<String>> = listOf(reportAuthorTag)
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return ReportEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
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
