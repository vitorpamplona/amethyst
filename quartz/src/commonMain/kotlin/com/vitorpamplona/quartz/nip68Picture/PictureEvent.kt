/*
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
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.hashtags
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip68Picture.tags.LocationTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag
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
    RootScope,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(content)
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var iMetas: List<PictureMeta>? = null

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun mimeType() = tags.firstNotNullOfOrNull(MimeTypeTag::parse)

    fun hash() = tags.firstNotNullOfOrNull(HashSha256Tag::parse)

    fun hashtags() = tags.hashtags()

    fun geohashes() = tags.geohashes()

    fun location() = tags.mapNotNull(LocationTag::parse)

    fun imetaTags() = iMetas ?: imetas().map { PictureMeta.parse(it) }.also { iMetas = it }

    /** NIP-68 names its `p` tags "tagged users", and an imeta `annotate-user` places one at a point in the image. */
    override fun links(): List<Link<*>> =
        links {
            each(tags, PTag::parse) { user(Relation.TAGGED, it, PTag.TAG_NAME) }
            imetaTags().forEach { image ->
                image.annotations.forEach { user(Relation.TAGGED, it.pubkey, IMetaTag.TAG_NAME, it.linkProps()) }
            }
            hashtags(tags)
            each(tags, GeoHashTag::parse) { tag(Relation.TAG, GeoHashTag.TAG_NAME, it) }
        }

    companion object {
        const val KIND = 20

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
            initializer()
        }
    }
}
