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
package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.ETag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip92MediaAttachments
import com.vitorpamplona.quartz.encoders.Nip92MediaAttachments.Companion.IMETA
import com.vitorpamplona.quartz.encoders.PTag
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.ALT
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.BLUR_HASH
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.DIMENSION
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.FILE_SIZE
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.MAGNET_URI
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.TORRENT_INFOHASH
import com.vitorpamplona.quartz.events.FileHeaderEvent.Companion.URL
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.coroutines.cancellation.CancellationException

@Immutable
class PictureEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun mimeTypes() = tags.filter { it.size > 1 && it[0] == MIME_TYPE }

    fun hashes() = tags.filter { it.size > 1 && it[0] == HASH }

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == TITLE }?.get(1)

    fun url() = tags.firstOrNull { it.size > 1 && it[0] == URL }?.get(1)

    fun urls() = tags.filter { it.size > 1 && it[0] == URL }.map { it[1] }

    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == FileHeaderEvent.MIME_TYPE }?.get(1)

    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == FileHeaderEvent.HASH }?.get(1)

    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)

    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)

    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)?.let { Dimension.parse(it) }

    fun magnetURI() = tags.firstOrNull { it.size > 1 && it[0] == MAGNET_URI }?.get(1)

    fun torrentInfoHash() = tags.firstOrNull { it.size > 1 && it[0] == TORRENT_INFOHASH }?.get(1)

    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    fun hasUrl() = tags.any { it.size > 1 && it[0] == URL }

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
        const val ALT_DESCRIPTION = "Picture"

        private const val MIME_TYPE = "m"
        private const val HASH = "x"
        private const val TITLE = "title"

        private fun create(
            msg: String,
            tags: MutableList<Array<String>>,
            usersMentioned: Set<PTag> = emptySet(),
            addressesMentioned: Set<ATag> = emptySet(),
            eventsMentioned: Set<ETag> = emptySet(),
            nip94attachments: List<FileHeaderEvent>? = null,
            geohash: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            isDraft: Boolean,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (PictureEvent) -> Unit,
        ) {
            usersMentioned.forEach { tags.add(it.toPTagArray()) }
            addressesMentioned.forEach { tags.add(it.toQTagArray()) }
            eventsMentioned.forEach { tags.add(it.toQTagArray()) }

            findHashtags(msg).forEach {
                val lowercaseTag = it.lowercase()
                tags.add(arrayOf("t", it))
                if (it != lowercaseTag) {
                    tags.add(arrayOf("t", it.lowercase()))
                }
            }

            findURLs(msg).forEach { tags.add(arrayOf("r", it)) }

            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let { tags.add(arrayOf("zapraiser", "$it")) }
            geohash?.let { tags.addAll(geohashMipMap(it)) }
            nip94attachments?.let {
                it.forEach {
                    Nip92MediaAttachments().convertFromFileHeader(it)?.let {
                        tags.add(it)
                    }
                }
            }

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            } else {
                signer.sign(createdAt, KIND, tags.toTypedArray(), msg, onReady)
            }
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
    companion object {
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

            if (tagArray.size == 2 && tagArray[1].contains("url") && (tagArray[1].contains("blurhash") || tagArray[1].contains("size"))) {
                // hack to fix pablo's bug
                val keys = setOf("url", "m", "blurhash", "dim", "alt", "x", "size", "fallback", "annotate-user")
                var keyNextValue: String? = null
                val values = mutableListOf<String>()

                tagArray[1].split(" ").forEach {
                    if (it in keys) {
                        if (keyNextValue != null && values.isNotEmpty()) {
                            when (keyNextValue) {
                                "url" -> url = values.joinToString(" ")
                                "m" -> mimeType = values.joinToString(" ")
                                "blurhash" -> blurhash = values.joinToString(" ")
                                "dim" -> dim = Dimension.parse(values.joinToString(" "))
                                "alt" -> alt = values.joinToString(" ")
                                "x" -> hash = values.joinToString(" ")
                                "size" -> size = values.joinToString(" ").toLongOrNull()
                                "fallback" -> fallback.add(values.joinToString(" "))
                                "annotate-user" -> {
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
                        "url" -> url = values.joinToString(" ")
                        "m" -> mimeType = values.joinToString(" ")
                        "blurhash" -> blurhash = values.joinToString(" ")
                        "dim" -> dim = Dimension.parse(values.joinToString(" "))
                        "alt" -> alt = values.joinToString(" ")
                        "x" -> hash = values.joinToString(" ")
                        "size" -> size = values.joinToString(" ").toLongOrNull()
                        "fallback" -> fallback.add(values.joinToString(" "))
                        "annotate-user" -> {
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
                            "url" -> url = value
                            "m" -> mimeType = value
                            "blurhash" -> blurhash = value
                            "dim" -> dim = Dimension.parse(value)
                            "alt" -> alt = value
                            "x" -> hash = value
                            "size" -> size = value.toLongOrNull()
                            "fallback" -> fallback.add(value)
                            "annotate-user" -> {
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

class Dimension(
    val width: Int,
    val height: Int,
) {
    fun aspectRatio() = width.toFloat() / height.toFloat()

    override fun toString() = "${width}x$height"

    companion object {
        fun parse(dim: String): Dimension? {
            if (dim == "0x0") return null

            val parts = dim.split("x")
            if (parts.size != 2) return null

            return try {
                val width = parts[0].toInt()
                val height = parts[1].toInt()

                Dimension(width, height)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
    }
}

class UserAnnotation(
    val pubkey: HexKey,
    val x: Int,
    val y: Int,
) {
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
