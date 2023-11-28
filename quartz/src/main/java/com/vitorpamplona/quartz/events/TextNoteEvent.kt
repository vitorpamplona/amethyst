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
    tags: Array<Array<String>>,
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
            val tags = mutableListOf<Array<String>>()
            replyTos?.forEach {
                if (it == root) {
                    tags.add(arrayOf("e", it, "", "root"))
                } else if (it == replyingTo) {
                    tags.add(arrayOf("e", it, "", "reply"))
                } else if (it in directMentions) {
                    tags.add(arrayOf("e", it, "", "mention"))
                } else {
                    tags.add(arrayOf("e", it))
                }
            }
            mentions?.forEach {
                if (it in directMentions) {
                    tags.add(arrayOf("p", it, "", "mention"))
                } else {
                    tags.add(arrayOf("p", it))
                }
            }
            addresses?.forEach {
                val aTag = it.toTag()
                if (aTag == root) {
                    tags.add(arrayOf("a", aTag, "", "root"))
                } else if (aTag == replyingTo) {
                    tags.add(arrayOf("a", aTag, "", "reply"))
                } else if (aTag in directMentions) {
                    tags.add(arrayOf("a", aTag, "", "mention"))
                } else {
                    tags.add(arrayOf("a", aTag))
                }
            }
            findHashtags(msg).forEach {
                tags.add(arrayOf("t", it))
                tags.add(arrayOf("t", it.lowercase()))
            }
            extraTags?.forEach {
                tags.add(arrayOf("t", it))
            }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            findURLs(msg).forEach {
                tags.add(arrayOf("r", it))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(arrayOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.addAll(geohashMipMap(it))
            }

            signer.sign(createdAt, kind, tags.toTypedArray(), msg, onReady)
        }
    }
}

fun findURLs(text: String): List<String> {
    return UrlDetector(text, UrlDetectorOptions.Default).detect().map { it.originalUrl }
}
