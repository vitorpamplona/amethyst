package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)
    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)
    fun condition() = tags.firstOrNull { it.size > 1 && it[0] == "condition" }?.get(1)
    fun images() = tags.filter { it.size > 1 && it[0] == "image" }.map { it[1] }
    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)
    fun price() = tags.firstOrNull { it.size > 1 && it[0] == "price" }?.let {
        Price(it[1], it.getOrNull(2), it.getOrNull(3))
    }
    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)

    fun publishedAt() = try {
        tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    enum class CONDITION(val value: String){
        NEW("new"),
        USED_LIKE_NEW("like new"),
        USED_GOOD("good"),
        USED_FAIR("fair"),
    }

    companion object {
        const val kind = 30402
        private val imageExtensions = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg")

        fun create(
            dTag: String,
            title: String?,
            image: String?,
            summary: String?,
            message: String,
            price: Price?,
            location: String?,
            category: String?,
            condition: ClassifiedsEvent.CONDITION?,
            publishedAt: Long? = TimeUtils.now(),
            replyTos: List<String>?,
            addresses: List<ATag>?,
            mentions: List<String>?,
            directMentions: Set<HexKey>,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ClassifiedsEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()

            replyTos?.forEach {
                if (it in directMentions) {
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
                if (aTag in directMentions) {
                    tags.add(arrayOf("a", aTag, "", "mention"))
                } else {
                    tags.add(arrayOf("a", aTag))
                }
            }

            tags.add(arrayOf("d", dTag))
            title?.let { tags.add(arrayOf("title", it)) }
            image?.let { tags.add(arrayOf("image", it)) }
            summary?.let { tags.add(arrayOf("summary", it)) }
            price?.let {
                if (it.frequency != null && it.currency != null) {
                    tags.add(arrayOf("price", it.amount, it.currency, it.frequency))
                } else if (it.currency != null) {
                    tags.add(arrayOf("price", it.amount, it.currency))
                } else {
                    tags.add(arrayOf("price", it.amount))
                }
            }
            category?.let { tags.add(arrayOf("t", it)) }
            location?.let { tags.add(arrayOf("location", it)) }
            publishedAt?.let { tags.add(arrayOf("publishedAt", it.toString())) }
            condition?.let { tags.add(arrayOf("condition", it.value)) }

            findHashtags(message).forEach {
                tags.add(arrayOf("t", it))
                tags.add(arrayOf("t", it.lowercase()))
            }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            findURLs(message).forEach {
                val removedParamsFromUrl = it.split("?")[0].lowercase()
                if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                    tags.add(arrayOf("image", it))
                }
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

            signer.sign(createdAt, kind, tags.toTypedArray(), message, onReady)
        }
    }
}

data class Price(val amount: String, val currency: String?, val frequency: String?)
