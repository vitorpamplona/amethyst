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
package com.vitorpamplona.quartz.nip99Classifieds

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip10Notes.findHashtags
import com.vitorpamplona.quartz.nip10Notes.findURLs
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip57Zaps.ZapSplitSetup
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstOrNull { it.size > 1 && it[0] == "title" }?.get(1)

    fun image() = tags.firstOrNull { it.size > 1 && it[0] == "image" }?.get(1)

    fun condition() = tags.firstOrNull { it.size > 1 && it[0] == "condition" }?.get(1)

    fun images() = tags.filter { it.size > 1 && it[0] == "image" }.map { it[1] }

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == "summary" }?.get(1)

    fun price() =
        tags
            .firstOrNull { it.size > 1 && it[0] == "price" }
            ?.let { Price(it[1], it.getOrNull(2), it.getOrNull(3)) }

    fun location() = tags.firstOrNull { it.size > 1 && it[0] == "location" }?.get(1)

    fun isWellFormed(): Boolean {
        var hasImage = false
        var hasTitle = false
        var hasPrice = false

        tags.forEach {
            if (it.size > 1) {
                if (it[0] == "image") {
                    hasImage = true
                } else if (it[0] == "title") {
                    hasTitle = true
                } else if (it[0] == "price") {
                    hasPrice = true
                }
            }
        }

        return hasImage && hasPrice && hasTitle
    }

    fun publishedAt() =
        try {
            tags.firstOrNull { it.size > 1 && it[0] == "published_at" }?.get(1)?.toLongOrNull()
        } catch (_: Exception) {
            null
        }

    enum class CONDITION(
        val value: String,
    ) {
        NEW("new"),
        USED_LIKE_NEW("like new"),
        USED_GOOD("good"),
        USED_FAIR("fair"),
    }

    companion object {
        const val KIND = 30402
        private val imageExtensions = listOf("png", "jpg", "gif", "bmp", "jpeg", "webp", "svg", "avif")
        const val ALT = "Classifieds listing"

        fun create(
            dTag: String,
            title: String?,
            image: String?,
            summary: String?,
            message: String,
            price: Price?,
            location: String?,
            category: String?,
            condition: CONDITION?,
            publishedAt: Long? = TimeUtils.now(),
            replyTos: List<String>?,
            addresses: List<ATag>?,
            mentions: List<String>?,
            directMentions: Set<HexKey>,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            emojis: List<EmojiUrl>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            isDraft: Boolean,
            onReady: (ClassifiedsEvent) -> Unit,
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
                val removedParamsFromUrl =
                    if (it.contains("?")) {
                        it.split("?")[0].lowercase()
                    } else if (it.contains("#")) {
                        it.split("#")[0].lowercase()
                    } else {
                        it
                    }

                if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                    tags.add(arrayOf("image", it))
                }
                tags.add(arrayOf("r", it))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            imetas?.forEach { tags.add(Nip92MediaAttachments.createTag(it)) }
            emojis?.forEach { tags.add(it.toTagArray()) }
            tags.add(arrayOf("alt", ALT))

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), message, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), message, onReady)
            }
        }
    }
}
