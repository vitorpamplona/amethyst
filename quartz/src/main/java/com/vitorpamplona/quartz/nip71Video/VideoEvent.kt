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
package com.vitorpamplona.quartz.nip71Video

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip92IMeta.Nip92MediaAttachments.Companion.IMETA
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
abstract class VideoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig),
    RootScope {
    private fun url() = tags.firstOrNull { it.size > 1 && it[0] == URL }?.get(1)

    private fun urls() = tags.filter { it.size > 1 && it[0] == URL }.map { it[1] }

    private fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)

    private fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)

    private fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)

    private fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)?.let { Dimension.parse(it) }

    private fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    private fun image() = tags.filter { it.size > 1 && it[0] == IMAGE }.map { it[1] }

    private fun thumb() = tags.firstOrNull { it.size > 1 && it[0] == THUMB }?.get(1)

    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)

    fun title() = tags.firstOrNull { it.size > 1 && it[0] == TITLE }?.get(1)

    fun summary() = tags.firstOrNull { it.size > 1 && it[0] == SUMMARY }?.get(1)

    fun duration() = tags.firstOrNull { it.size > 1 && it[0] == DURATION }?.get(1)

    fun hasUrl() = tags.any { it.size > 1 && it[0] == URL }

    fun isOneOf(mimeTypes: Set<String>) = tags.any { it.size > 1 && it[0] == FileHeaderEvent.MIME_TYPE && mimeTypes.contains(it[1]) }

    // hack to fix pablo's bug
    fun rootVideo() =
        url()?.let {
            VideoMeta(
                url = it,
                mimeType = mimeType(),
                blurhash = blurhash(),
                alt = alt(),
                hash = hash(),
                dimension = dimensions(),
                size = size()?.toIntOrNull(),
                service = null,
                fallback = emptyList(),
                image = image(),
            )
        }

    fun imetaTags() =
        tags
            .map { tagArray ->
                if (tagArray.size > 1 && tagArray[0] == IMETA) {
                    VideoMeta.parse(tagArray)
                } else {
                    null
                }
            }.plus(rootVideo())
            .filterNotNull()

    companion object {
        private const val URL = "url"
        private const val MIME_TYPE = "m"
        private const val FILE_SIZE = "size"
        private const val DIMENSION = "dim"
        private const val HASH = "x"
        private const val BLUR_HASH = "blurhash"
        private const val ALT = "alt"
        private const val TITLE = "title"
        private const val SUMMARY = "summary"
        private const val DURATION = "duration"
        private const val IMAGE = "image"
        private const val THUMB = "thumb"

        fun <T : VideoEvent> create(
            kind: Int,
            dTag: String,
            url: String,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: Int? = null,
            duration: Int? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            sensitiveContent: Boolean? = null,
            service: String? = null,
            altDescription: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (T) -> Unit,
        ) {
            val video =
                VideoMeta(
                    url,
                    mimeType,
                    blurhash,
                    dimensions,
                    alt,
                    hash,
                    size,
                    service,
                    emptyList(),
                    emptyList(),
                )

            val tags = mutableListOf<Array<String>>()

            tags.add(arrayOf("d", dTag))
            tags.add(arrayOf(ALT, altDescription))
            if (sensitiveContent == true) {
                tags.add(ContentWarningSerializer.toTagArray())
            }
            duration?.let { tags.add(arrayOf(DURATION, "duration")) }

            tags.add(video.toIMetaArray())

            val content = alt ?: ""
            signer.sign<T>(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}

data class VideoMeta(
    val url: String,
    val mimeType: String?,
    val blurhash: String?,
    val dimension: Dimension?,
    val alt: String?,
    val hash: String?,
    val size: Int?,
    val service: String?,
    val fallback: List<String>,
    val image: List<String>,
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
                service?.let { "$SERVICE $it" },
            ) +
                fallback.map { "$FALLBACK $it" } +
                image.map { "$IMAGE $it" }

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
        const val IMAGE = "image"
        const val SERVICE = "service"

        fun parse(tagArray: Array<String>): VideoMeta? {
            var url: String? = null
            var mimeType: String? = null
            var blurhash: String? = null
            var dim: Dimension? = null
            var alt: String? = null
            var hash: String? = null
            var size: Int? = null
            var service: String? = null
            val fallback = mutableListOf<String>()
            val images = mutableListOf<String>()

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
                        FILE_SIZE -> size = value.toIntOrNull()
                        SERVICE -> service = value
                        FALLBACK -> fallback.add(value)
                        IMAGE -> images.add(value)
                    }
                }
            }

            return url?.let {
                VideoMeta(it, mimeType, blurhash, dim, alt, hash, size, service, fallback, images)
            }
        }
    }
}
