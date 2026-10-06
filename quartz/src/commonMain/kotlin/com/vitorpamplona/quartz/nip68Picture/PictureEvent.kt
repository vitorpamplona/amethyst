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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip68Picture.tags.LocationTag
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
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent {
    // The place name and each image's own `alt` (the poster's accessibility description of
    // that picture) are human-written too, and are how a photo is often searched for.
    override fun indexableContent() = (listOfNotNull(title(), content) + location() + imageDescriptions()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(content)) return
        // Walks the tags and the (cached) imetas in place: this runs per event per search
        // keystroke, and location()/imageDescriptions() would build lists only to discard them.
        for (tag in tags) {
            val location = LocationTag.parse(tag) ?: continue
            if (!visitor.visit(location)) return
        }
        val metas = imetaTags()
        for (i in metas.indices) {
            val alt = metas[i].alt?.takeIf { it.isNotBlank() } ?: continue
            if (!visitor.visit(alt)) return
        }
    }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var citedNIP19Cache: List<Entity>? = null

    /** NIP-19 entities cited as `nostr:` URIs in the description, parsed once. */
    fun citedNIP19(): List<Entity> = citedNIP19Cache ?: findNostrUris(content).also { citedNIP19Cache = it }

    /** Pubkeys tagged on the images themselves (imeta `annotate-user`); no relay slot. */
    fun annotatedUsers(): List<HexKey> = imetaTags().flatMap { meta -> meta.annotations.mapNotNull { it.pubkey.takeIf { key -> key.isValid() } } }

    // NIP-68 `p` tags the people in the picture; imeta annotations place them on an image.
    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint) + citedNIP19().pubKeyHints()

    // linked*() run on every relay copy of the event, and each is two or three tag scans plus
    // concatenations; the event is immutable, so they are built once per instance.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedPubKeysCache: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedEventIdsCache: List<HexKey>? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var linkedAddressIdsCache: List<String>? = null

    override fun linkedPubKeys(): List<HexKey> = linkedPubKeysCache ?: (tags.mapNotNull(PTag::parseKey) + annotatedUsers() + citedNIP19().pubKeys()).also { linkedPubKeysCache = it }

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(QTag::parseEventAsHint) + citedNIP19().eventHints()

    override fun linkedEventIds(): List<HexKey> = linkedEventIdsCache ?: (tags.mapNotNull(QTag::parseEventId) + citedNIP19().eventIds()).also { linkedEventIdsCache = it }

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(QTag::parseAddressAsHint) + citedNIP19().addressHints()

    override fun linkedAddressIds(): List<String> = linkedAddressIdsCache ?: (tags.mapNotNull(QTag::parseValidAddress) + citedNIP19().addressIds()).also { linkedAddressIdsCache = it }

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

    /** Each image's `alt` text, in imeta order. */
    fun imageDescriptions(): List<String> = imetaTags().mapNotNull { meta -> meta.alt?.takeIf { it.isNotBlank() } }

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
