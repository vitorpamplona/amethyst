package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class TextNoteEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun root() = tags.firstOrNull() { it.size > 3 && it[3] == "root" }?.get(1)

    companion object {
        const val kind = 1

        fun create(
            msg: String,
            replyTos: List<String>?,
            mentions: List<String>?,
            addresses: List<ATag>?,
            extraTags: List<String>?,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            replyingTo: String?,
            root: String?,
            directMentions: Set<HexKey>,
            geohash: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (TextNoteEvent) -> Unit
        ) {
            val tags = mutableListOf<List<String>>()
            replyTos?.forEach {
                if (it == root) {
                    tags.add(listOf("e", it, "", "root"))
                } else if (it == replyingTo) {
                    tags.add(listOf("e", it, "", "reply"))
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
                if (aTag == root) {
                    tags.add(listOf("a", aTag, "", "root"))
                } else if (aTag == replyingTo) {
                    tags.add(listOf("a", aTag, "", "reply"))
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
            zapReceiver?.forEach {
                tags.add(listOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
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
                tags.addAll(geohashMipMap(it))
            }

            signer.sign(createdAt, kind, tags, msg, onReady)
        }
    }
}

fun findURLs(text: String): List<String> {
    return UrlDetector(text, UrlDetectorOptions.Default).detect().map { it.originalUrl }
}
