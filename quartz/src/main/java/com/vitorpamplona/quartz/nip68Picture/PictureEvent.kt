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
package com.vitorpamplona.quartz.nip68Picture

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashMipMap
import com.vitorpamplona.quartz.nip10Notes.ETag
import com.vitorpamplona.quartz.nip10Notes.PTag
import com.vitorpamplona.quartz.nip10Notes.findHashtags
import com.vitorpamplona.quartz.nip10Notes.findURLs
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip57Zaps.ZapSplitSetup
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments.Companion.IMETA
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PictureEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun mimeTypes() = tags.filter { it.size > 1 && it[0] == MIME_TYPE }

    fun hashes() = tags.filter { it.size > 1 && it[0] == HASH }

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == TITLE }?.get(1)

    private fun url() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.URL }?.get(1)

    private fun urls() = tags.filter { it.size > 1 && it[0] == PictureMeta.URL }.map { it[1] }

    private fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.MIME_TYPE }?.get(1)

    private fun hash() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.HASH }?.get(1)

    private fun size() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.FILE_SIZE }?.get(1)

    private fun alt() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.ALT }?.get(1)

    private fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.DIMENSION }?.get(1)?.let { Dimension.parse(it) }

    private fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == PictureMeta.BLUR_HASH }?.get(1)

    private fun hasUrl() = tags.any { it.size > 1 && it[0] == PictureMeta.URL }

    // hack to fix pablo's bug
    fun rootImage() =
        url()?.let {
            PictureMeta(
                url = it,
                mimeType = mimeType(),
                blurhash = blurhash(),
                alt = alt(),
                hash = hash(),
                dimension = dimensions(),
                size = size()?.toLongOrNull(),
                fallback = emptyList(),
                annotations = emptyList(),
            )
        }

    fun imetaTags() =
        tags
            .map { tagArray ->
                if (tagArray.size > 1 && tagArray[0] == IMETA) {
                    PictureMeta.parse(tagArray)
                } else {
                    null
                }
            }.plus(rootImage())
            .filterNotNull()

    companion object {
        const val KIND = 20
        const val ALT_DESCRIPTION = "List of pictures"

        private const val MIME_TYPE = "m"
        private const val HASH = "x"
        private const val TITLE = "title"

        fun create(
            url: String,
            msg: String? = null,
            title: String? = null,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: Long? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PictureEvent) -> Unit,
        ) {
            val image =
                PictureMeta(
                    url,
                    mimeType,
                    blurhash,
                    dimensions,
                    alt,
                    hash,
                    size,
                    emptyList(),
                    emptyList(),
                )

            create(listOf(image), msg, title, usersMentioned, addressesMentioned, eventsMentioned, geohash, zapReceiver, markAsSensitive, zapRaiserAmount, signer, createdAt, onReady)
        }

        fun create(
            images: List<PictureMeta>,
            msg: String? = null,
            title: String? = null,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PictureEvent) -> Unit,
        ) {
            val tags = mutableListOf(arrayOf<String>("alt", ALT_DESCRIPTION))

            images.forEach {
                tags.add(it.toIMetaArray())
            }

            title?.let { tags.add(arrayOf("title", it)) }

            images.distinctBy { it.hash }.forEach {
                if (it.hash != null) {
                    tags.add(arrayOf("x", it.hash))
                }
            }

            images.distinctBy { it.mimeType }.forEach {
                if (it.mimeType != null) {
                    tags.add(arrayOf("m", it.mimeType))
                }
            }

            usersMentioned.forEach { tags.add(it.toPTagArray()) }
            addressesMentioned.forEach { tags.add(it.toQTagArray()) }
            eventsMentioned.forEach { tags.add(it.toQTagArray()) }

            if (msg != null) {
                findHashtags(msg).forEach {
                    val lowercaseTag = it.lowercase()
                    tags.add(arrayOf("t", it))
                    if (it != lowercaseTag) {
                        tags.add(arrayOf("t", it.lowercase()))
                    }
                }

                findURLs(msg).forEach { tags.add(arrayOf("r", it)) }
            }

            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }

            signer.sign(createdAt, KIND, tags.toTypedArray(), msg ?: "", onReady)
        }
    }
}

class PictureMeta(
    val url: String,
    val mimeType: String?,
    val blurhash: String?,
    val dimension: Dimension?,
    val alt: String?,
    val hash: String?,
    val size: Long?,
    val fallback: List<String>,
    val annotations: List<UserAnnotation>,
) {
    fun toIMetaArray(): Array<String> =
        (
            listOfNotNull(
                "imeta",
                "$URL $url",
                mimeType?.let { "$MIME_TYPE $it" },
                alt?.let { "$ALT $it" },
                hash?.let { "$HASH $it" },
                size?.let { "$FILE_SIZE $it" },
                dimension?.let { "$DIMENSION $it" },
                blurhash?.let { "$BLUR_HASH $it" },
            ) +
                fallback.map { "$FALLBACK $it" } +
                annotations.map { "$ANNOTATIONS $it" }
        ).toTypedArray()

    companion object {
        const val URL = "url"
        const val MIME_TYPE = "m"
        const val FILE_SIZE = "size"
        const val DIMENSION = "dim"
        const val HASH = "x"
        const val BLUR_HASH = "blurhash"
        const val ALT = "alt"
        const val FALLBACK = "fallback"
        const val ANNOTATIONS = "annotate-user"

        fun parse(tagArray: Array<String>): PictureMeta? {
            var url: String? = null
            var mimeType: String? = null
            var blurhash: String? = null
            var dim: Dimension? = null
            var alt: String? = null
            var hash: String? = null
            var size: Long? = null
            val fallback = mutableListOf<String>()
            val annotations = mutableListOf<UserAnnotation>()

            if (tagArray.size == 2 &&
                tagArray[1].contains(URL) &&
                (
                    tagArray[1].contains(BLUR_HASH) ||
                        tagArray[1].contains(
                            FILE_SIZE,
                        )
                )
            ) {
                // hack to fix pablo's bug
                val keys = setOf(URL, MIME_TYPE, BLUR_HASH, DIMENSION, ALT, HASH, FILE_SIZE, FALLBACK, ANNOTATIONS)
                var keyNextValue: String? = null
                val values = mutableListOf<String>()

                tagArray[1].split(" ").forEach {
                    if (it in keys) {
                        if (keyNextValue != null && values.isNotEmpty()) {
                            when (keyNextValue) {
                                URL -> url = values.joinToString(" ")
                                MIME_TYPE -> mimeType = values.joinToString(" ")
                                BLUR_HASH -> blurhash = values.joinToString(" ")
                                DIMENSION -> dim = Dimension.parse(values.joinToString(" "))
                                ALT -> alt = values.joinToString(" ")
                                HASH -> hash = values.joinToString(" ")
                                FILE_SIZE -> size = values.joinToString(" ").toLongOrNull()
                                FALLBACK -> fallback.add(values.joinToString(" "))
                                ANNOTATIONS -> {
                                    UserAnnotation.parse(values.joinToString(" "))?.let {
                                        annotations.add(it)
                                    }
                                }
                            }
                            values.clear()
                        }
                        keyNextValue = it
                    } else {
                        values.add(it)
                    }
                }

                if (keyNextValue != null && values.isNotEmpty()) {
                    when (keyNextValue) {
                        URL -> url = values.joinToString(" ")
                        MIME_TYPE -> mimeType = values.joinToString(" ")
                        BLUR_HASH -> blurhash = values.joinToString(" ")
                        DIMENSION -> dim = Dimension.parse(values.joinToString(" "))
                        ALT -> alt = values.joinToString(" ")
                        HASH -> hash = values.joinToString(" ")
                        FILE_SIZE -> size = values.joinToString(" ").toLongOrNull()
                        FALLBACK -> fallback.add(values.joinToString(" "))
                        ANNOTATIONS -> {
                            UserAnnotation.parse(values.joinToString(" "))?.let {
                                annotations.add(it)
                            }
                        }
                    }
                    values.clear()
                    keyNextValue = null
                }
            } else {
                tagArray.forEach {
                    val parts = it.split(" ", limit = 2)
                    val key = parts[0]
                    val value = if (parts.size == 2) parts[1] else ""

                    if (value.isNotBlank()) {
                        when (key) {
                            URL -> url = value
                            MIME_TYPE -> mimeType = value
                            BLUR_HASH -> blurhash = value
                            DIMENSION -> dim = Dimension.parse(value)
                            ALT -> alt = value
                            HASH -> hash = value
                            FILE_SIZE -> size = value.toLongOrNull()
                            FALLBACK -> fallback.add(value)
                            ANNOTATIONS -> {
                                UserAnnotation.parse(value)?.let {
                                    annotations.add(it)
                                }
                            }
                        }
                    }
                }
            }

            return url?.let {
                PictureMeta(it, mimeType, blurhash, dim, alt, hash, size, fallback, annotations)
            }
        }
    }
}

class UserAnnotation(
    val pubkey: HexKey,
    val x: Int,
    val y: Int,
) {
    override fun toString() = "$pubkey:$x:$y"

    companion object {
        fun parse(value: String): UserAnnotation? {
            val ann = value.split(":")
            if (ann.size == 3) {
                val x = ann[1].toIntOrNull()
                val y = ann[2].toIntOrNull()
                if (x != null && y != null) {
                    return UserAnnotation(ann[0], x, y)
                }
            }

            return null
        }
    }
}
