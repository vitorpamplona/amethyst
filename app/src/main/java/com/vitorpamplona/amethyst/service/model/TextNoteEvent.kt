package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.ui.screen.loggedIn.findHashtags

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun subject() = tags.firstOrNull() { it.size > 1 && it[0] == "subject" }?.get(1)

    fun root() = tags.firstOrNull() { it.size > 3 && it[3] == "root" }?.get(1)

    companion object {
        const val kind = 1

        fun create(
            msg: String,
            replyTos: List<String>?,
            mentions: List<String>?,
            addresses: List<ATag>?,
            extraTags: List<String>?,
            zapReceiver: String?,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            replyingTo: String?,
            root: String?,
            directMentions: Set<HexKey>,
            geohash: String? = null,

            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): TextNoteEvent {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                if (it == replyingTo) {
                    tags.add(listOf("e", it, "", "reply"))
                } else if (it == root) {
                    tags.add(listOf("e", it, "", "root"))
                } else if (it in directMentions) {
                    tags.add(listOf("e", it, "", "mention"))
                } else {
                    tags.add(listOf("e", it))
                }
            }
            mentions?.forEach {
                if (it in directMentions) {
                    tags.add(listOf("p", it, "", "mention"))
                } else {
                    tags.add(listOf("p", it))
                }
            }
            addresses?.forEach {
                val aTag = it.toTag()
                if (aTag == replyingTo) {
                    tags.add(listOf("a", aTag, "", "reply"))
                } else if (aTag == root) {
                    tags.add(listOf("a", aTag, "", "root"))
                } else if (aTag in directMentions) {
                    tags.add(listOf("a", aTag, "", "mention"))
                } else {
                    tags.add(listOf("a", aTag))
                }
            }
            findHashtags(msg).forEach {
                tags.add(listOf("t", it))
                tags.add(listOf("t", it.lowercase()))
            }
            extraTags?.forEach {
                tags.add(listOf("t", it))
            }
            zapReceiver?.let {
                tags.add(listOf("zap", it))
            }
            findURLs(msg).forEach {
                tags.add(listOf("r", it))
            }
            if (markAsSensitive) {
                tags.add(listOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(listOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.add(listOf("g", it))
            }

            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = CryptoUtils.sign(id, privateKey)
            return TextNoteEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}

fun findURLs(text: String): List<String> {
    return UrlDetector(text, UrlDetectorOptions.Default).detect().map { it.originalUrl }
}
