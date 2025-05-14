/**
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.BlurhashTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.FallbackTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ImageTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MagnetTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ServiceTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SizeTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.TorrentInfoHash
import com.vitorpamplona.quartz.nip94FileMetadata.tags.UrlTag
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
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
    // ---------------
    // current
    // --------------

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    /** old standard didnt use IMetas **/
    private fun url() = tags.firstNotNullOfOrNull(UrlTag::parse)

    private fun urls() = tags.mapNotNull(UrlTag::parse)

    private fun mimeType() = tags.firstNotNullOfOrNull(MimeTypeTag::parse)

    private fun hash() = tags.firstNotNullOfOrNull(HashSha256Tag::parse)

    private fun size() = tags.firstNotNullOfOrNull(SizeTag::parse)

    private fun dimensions() = tags.firstNotNullOfOrNull(DimensionTag::parse)

    private fun magnetURI() = tags.firstNotNullOfOrNull(MagnetTag::parse)

    private fun torrentInfoHash() = tags.firstNotNullOfOrNull(TorrentInfoHash::parse)

    private fun blurhash() = tags.firstNotNullOfOrNull(BlurhashTag::parse)

    private fun hasUrl() = tags.any(UrlTag::isTag)

    private fun isOneOf(mimeTypes: Set<String>) = tags.any(MimeTypeTag::isIn, mimeTypes)

    private fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    private fun images() = tags.mapNotNull(ImageTag::parse)

    private fun thumb() = tags.firstNotNullOfOrNull(ThumbTag::parse)

    private fun service() = tags.firstNotNullOfOrNull(ServiceTag::parse)

    private fun fallbacks() = tags.mapNotNull(FallbackTag::parse)

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
                size = size(),
                service = service(),
                fallback = fallbacks(),
                annotations = emptyList(),
            )
        }

    fun imetaTags() = imetas().map { PictureMeta.parse(it) }.plus(rootImage()).filterNotNull()

    companion object {
        const val KIND = 20
        const val ALT_DESCRIPTION = "List of pictures"

        fun build(
            image: PictureMeta,
            description: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PictureEvent>.() -> Unit = {},
        ) = build(description, createdAt) {
            pictureIMeta(image)
            initializer()
        }

        fun build(
            images: List<PictureMeta>,
            description: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PictureEvent>.() -> Unit = {},
        ) = build(description, createdAt) {
            pictureIMetas(images)
            initializer()
        }

        fun build(
            description: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PictureEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            alt(ClassifiedsEvent.ALT_DESCRIPTION)
            initializer()
        }
    }
}
