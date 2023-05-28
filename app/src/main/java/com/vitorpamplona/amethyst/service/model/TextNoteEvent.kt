package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.ui.screen.loggedIn.findHashtags
import nostr.postr.Utils
import java.util.Date

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

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
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000
        ): TextNoteEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
            }
            addresses?.forEach {
                tags.add(listOf("a", it.toTag()))
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

            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = Utils.sign(id, privateKey)
            return TextNoteEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig.toHexKey())
        }
    }
}

fun findURLs(text: String): List<String> {
    return UrlDetector(text, UrlDetectorOptions.Default).detect().map { it.originalUrl }
}
