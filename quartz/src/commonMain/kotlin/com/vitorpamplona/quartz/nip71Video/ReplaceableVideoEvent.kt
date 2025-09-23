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
package com.vitorpamplona.quartz.nip71Video

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip71Video.tags.DurationTag
import com.vitorpamplona.quartz.nip71Video.tags.SegmentTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag

@Immutable
abstract class ReplaceableVideoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    kind: Int,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, kind, tags, content, sig),
    PublishedAtProvider,
    VideoEvent,
    RootScope {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var iMetas: List<VideoMeta>? = null

    override fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    override fun publishedAt(): Long? {
        val publishedAt = tags.firstNotNullOfOrNull(PublishedAtTag::parse)

        if (publishedAt == null) return null

        // removes posts in the future.
        return if (publishedAt <= createdAt) {
            publishedAt
        } else {
            null
        }
    }

    override fun duration() = tags.firstNotNullOfOrNull(DurationTag::parse)

    override fun textTrack() = tags.mapNotNull(ETag::parse)

    override fun segments() = tags.mapNotNull(SegmentTag::parse)

    override fun participants() = tags.mapNotNull(PTag::parse)

    override fun hashtags() = tags.hashtags()

    override fun mimeType() = tags.firstNotNullOfOrNull(MimeTypeTag::parse)

    override fun hash() = tags.firstNotNullOfOrNull(HashSha256Tag::parse)

    override fun imetaTags() = iMetas ?: imetas().map { VideoMeta.parse(it) }.also { iMetas = it }
}
